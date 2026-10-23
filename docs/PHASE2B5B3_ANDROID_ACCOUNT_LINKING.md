# Phase 2B.5B.3 — MindHaven Android: secure MantraAI account linking

Android only. Consumes the backend contract verified live in Phase 2B.5B.2
(`EmotionSense-AI/docs/PHASE2B5B2_ACCOUNT_LINKING.md`, `backend/src/routes/accountLinkRoutes.js`). No backend, frontend,
schema or configuration change. No credentials, tokens or real account identifiers appear in this document.

## Files

| File | Change |
|---|---|
| `data/remote/ApiModels.kt` | `LinkStartRequest/Response`, `LinkCompleteRequest/Response` (redacted `toString`), link error codes |
| `data/remote/BackendApi.kt` | `linkStart`, `linkComplete` on the existing OkHttp client; strict response validation |
| `data/auth/FirebaseAuthGateway.kt` | `idTokenWithAuthTime` (token + `auth_time`), `reauthenticateWithGoogle`, `reauthenticateWithPassword`, `UserMismatch` |
| `data/auth/BackendSessionManager.kt` | `adoptLinkedSession` (installs the link token like an exchange token); `api` made `internal` |
| `data/auth/AccountLinker.kt` | **new**: linking state machine, in-memory challenge, freshness, recovery |
| `data/auth/AuthRepository.kt` | `AuthGate.LinkRequired`, `AuthOutcome.LinkRequired`, `linkState`, link / re-auth / cancel operations |
| `ui/theme/navigation/AuthNavigation.kt` | `LINK_ROUTE = "linkAccount"`; `LinkRequired` → linking screen |
| `ui/theme/navigation/Navigation.kt` | linking destination |
| `ui/theme/screens/AccountLinkScreen.kt` | **new**: Compose screen + pure `linkScreenModel` |
| `viewmodel/AuthViewModel.kt` | `linkState`, `linkAccount`, `reauthenticateWith*`, `cancelLinking` (one job at a time) |
| tests | `AccountLinkingTest`, `LinkApiTest`, `AccountLinkScreenModelTest`, `AccountLinkSecurityTest`; additions to `AuthNavigationTest`, `AuthViewModelTest`; 4 existing tests updated (409 no longer signs out) |

## Authentication states

```
SignedOut ──sign-in──▶ (exchange) ──200──▶ Authenticated ──▶ main
                           │ 409 ACCOUNT_LINK_REQUIRED
                           ▼
                     LinkRequired  (Firebase kept, NO API token, only the linking screen)
                       │  linkAccount(password, consent)          │ cancel / use another account
                       ▼                                          ▼
           start ─▶ complete ─200─▶ Authenticated ─▶ main     SignedOut ─▶ welcome
```

Restart: the persisted Firebase session → `Verifying` (launch screen) → exchange again → `LinkRequired` if still 409.
Navigation is decided only by `authNavigationCommand(gate, route)`: idempotent, every command clears the back stack, so
Back can never return to the password screen after success, nor into the app from the linking screen.

`LinkState` (UI progress, no secrets): `Idle`, `Working(Starting|Completing|Recovering|Reauthenticating)`,
`NeedsReauth(Google|Password|SignInAgain, problem?)`, `Reauthenticated`, `Failed(problem, retryAfter?)`, `Linked`.

## API contract used

| Call | Body (exactly) | Success |
|---|---|---|
| `POST /api/auth/firebase/link/start` | `idToken`, `password`, `consent: true` | 201 `{success, challenge (43 base64url), expiresAt (epoch ms), expiresIn ≤ 300}` |
| `POST /api/auth/firebase/link/complete` | `idToken`, `challenge`, `consent: true` | 200 `{success, token, tokenType: "Bearer", expiresAt (ms), expiresIn: 300, linked: true, user{id,…}}` |

No `Authorization` header on either call. No user id, Firebase UID, project, e-mail or verification flag is sent.
Responses that do not match exactly are treated as `BadResponse`. Debug builds use the existing `API_BASE_URL`
(`http://10.0.2.2:8080` by default, production hosts refused by Gradle); release builds are unchanged.

Discrepancy check: the task text shows `expiresAt` as a string; the backend sends **epoch milliseconds (number)**, as the
backend document says. The Android models follow the source code.

## Error handling

| Backend answer | Step | Android behaviour |
|---|---|---|
| `LINK_PROOF_FAILED` 401 | start | "That password is incorrect." Stay restricted. |
| `CONSENT_REQUIRED` / `VALIDATION_FAILED` 400 | both | Not reachable from the UI (consent checked locally); generic message. |
| `INVALID_FIREBASE_TOKEN` 401 | both | One forced Firebase-token refresh + one retry (same challenge: rejected before use); second time → sign out. |
| `REAUTHENTICATION_REQUIRED` 401 | both | Provider-specific re-authentication; at complete the challenge is kept and completed after re-auth if still valid. |
| `LINK_CHALLENGE_INVALID` 400 | complete | Exchange first; 200 → signed in; 409 → "request expired, try again". Never replayed. |
| `IDENTITY_ALREADY_LINKED` 409 | both | Exchange → signed in. |
| `EMAIL_NOT_VERIFIED`, `UNSUPPORTED_SIGN_IN_METHOD`, `IDENTITY_DISABLED` 403 | both | Sign out with the existing safe message. |
| `RATE_LIMITED` 429 | both | Message with Retry-After (minutes when long). No automatic retry. |
| `IDENTITY_UNAVAILABLE` 503 / 5xx | start | "Temporarily unavailable." |
| 404 (flag off) | both | "Account linking isn't available yet." Stay restricted; Cancel available. |
| Timeout / offline / transport / 5xx / unparsable 2xx | complete | **Ambiguous**: drop the challenge, call the exchange once. 200 → signed in; 409 → "Linking didn't finish"; still offline → "couldn't confirm, try again". The next attempt starts fresh (a committed link answers `IDENTITY_ALREADY_LINKED` → exchange). No loops. |

## Secrets and tokens

* **Web password**: in Compose `remember` (not `rememberSaveable`), cleared the instant Link is pressed, passed through the
  ViewModel/repository to one `link/start` request; never stored, logged or put in a route. (JVM strings cannot be zeroed;
  the reference is dropped immediately.)
* **Challenge**: only in `AccountLinker.pending` (memory), bound to Firebase uid + session epoch, local deadline =
  receipt + `expiresIn` − 15 s; dropped on success, expiry, any ambiguous completion, cancel, logout, account switch.
* **Firebase ID token**: fetched per request with `getIdToken(false)`; forced refresh only after `INVALID_FIREBASE_TOKEN`
  and once after re-authentication. Never stored.
* **API JWT**: from `link/complete`, installed by `BackendSessionManager.adoptLinkedSession` (same in-memory cache,
  device-clock expiry and 30 s-early refresh timer as exchange tokens; later refreshes use the exchange, which now resolves
  the linked identity). Refused if the epoch or Firebase user changed.
* Process death: nothing is persisted; the app restarts through Verifying → exchange → LinkRequired.
* The linking screen shows no e-mail, Firebase UID or user id.

## Firebase freshness and re-authentication

The backend requires the Firebase `auth_time` to be ≤ 600 s old at both steps (`ACCOUNT_LINK_MAX_AUTH_AGE_SECONDS`). A
token refresh does **not** change `auth_time`. Android reads `auth_time` (`GetTokenResult.authTimestamp`) and asks for
re-authentication when it is older than 480 s, before sending anything:

* Google users: "Confirm with Google" → Google Sign-In intent → `FirebaseUser.reauthenticate(GoogleAuthProvider credential)`.
  A different Google account fails with `ERROR_USER_MISMATCH` ("different account").
* E-mail/password users: "MindHaven password" field → `reauthenticate(EmailAuthProvider credential)`; never stored.
* Other providers: "Sign in again" (controlled sign-out).

The server stays authoritative (`REAUTHENTICATION_REQUIRED` triggers the same flow). After a successful re-authentication
the local age check is skipped for 5 minutes so a wrong device clock cannot cause a loop.

## Test matrix (JVM unit tests, fakes only)

| Requirement | Test |
|---|---|
| Exchange 200 → Home; 409 → LinkRequired; no API session while restricted | `AccountLinkingTest`, `DefaultAuthRepositoryTest`, `AuthViewModelTest` |
| Wrong password stays restricted; consent unchecked / empty password → no request | `AccountLinkingTest`, `AccountLinkScreenModelTest` |
| Start/complete serialisation, duplicate taps | `AccountLinkingTest` (overlap), `AuthViewModelTest` (triple tap) |
| Token refresh once on invalid token; auth_time staleness → real re-auth (Google, password, sign-in-again) | `AccountLinkingTest` |
| Challenge in memory only, expiry before/after receipt, kept across re-auth | `AccountLinkingTest`, `AccountLinkSecurityTest` |
| Success → canonical session, `/me`, navigation once, Back cannot return | `AccountLinkingTest`, `AuthNavigationTest` |
| Timeout/5xx/invalid challenge → exchange recovery, no replay, no loop | `AccountLinkingTest` |
| Cancel, logout mid-flight, account switch mid-flight, process recreation | `AccountLinkingTest` |
| Feature disabled (404), 429, 503, offline | `AccountLinkingTest`, `LinkApiTest` |
| Exact wire contract, strict parsing, no Authorization header, cleartext refused | `LinkApiTest` |
| No sensitive logging / persistence / route arguments / identity fields | `AccountLinkSecurityTest`, `SecurityConfigTest`, redacted `toString` |

## Manual test steps (Android Studio, Phase 2B.5B.4 — requires approval first)

1. Backend (local, Development DB): start it with `ACCOUNT_LINKING_ENABLED=true` and `FIREBASE_EXCHANGE_ENABLED=true`
   set **in the shell only** (never in `.env`), port 8080.
2. Create a MantraAI web account through the local frontend with an e-mail you control.
3. Emulator: install the debug build from Android Studio (`API_BASE_URL` = `http://10.0.2.2:8080`).
4. Sign in with Google (same e-mail) or with a verified Firebase e-mail/password account with that e-mail.
   Expect the **Link your MantraAI account** screen (not Home, not an error toast).
5. Leave consent unchecked → Link disabled. Wrong web password → "incorrect", still on the screen.
6. Correct password + consent → Home. Profile shows the same account as the web app (`/api/identity/me`).
7. Kill and relaunch the app → straight to Home (exchange now resolves the link).
8. Second scenario: cancel on the linking screen → welcome; nothing changed on the web account.
9. Stale sign-in: restore a session older than 10 minutes, try to link → "confirm it's you" → re-authenticate → link.
10. Backend with the flag unset → "Account linking isn't available yet".
11. Afterwards remove the test data with the guarded cleanup procedure.

## Known limitations

* Not yet run on a device/emulator: Compose rendering, the Google re-authentication intent and real Firebase
  `reauthenticate` behaviour are verified only through fakes and compilation.
* Google re-authentication may complete silently when the Google client still has the account selected (the user still
  has to tap "Confirm with Google"; Firebase records a new `auth_time`).
* Typed passwords are not kept across a configuration change (by design: `remember`, not saved state).
* System Back on the linking screen leaves the app (the back stack is empty); Firebase stays signed in and the next
  launch returns to the linking screen.
* Kotlin/JVM strings cannot be wiped from memory.
* Existing lint errors in `MeditationScreen.kt` / `SleepScreen.kt` (`ContextCastToActivity`) are unrelated to this phase.
