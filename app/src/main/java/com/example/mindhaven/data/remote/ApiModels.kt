package com.example.mindhaven.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire models for the MantraAI backend (Express). Source of truth:
 * backend/src/routes/identityRoutes.js, backend/src/identity (the .js files) and docs/PHASE2B3_FIREBASE_IDENTITY.md.
 *
 * Every field the client does not need is simply ignored (ignoreUnknownKeys). Token-bearing types override
 * toString() so a token can never leak through string interpolation, logging or an exception message.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
    coerceInputValues = true
    explicitNulls = false
}

/** POST /api/auth/firebase/exchange body. ONLY the Firebase ID token is ever sent. */
@Serializable
data class ExchangeRequest(val idToken: String) {
    override fun toString(): String = "ExchangeRequest(idToken=<redacted>)"
}

/** Safe canonical profile returned by the exchange. [id] is Neon users.id (never the Firebase UID). */
@Serializable
data class ApiUser(
    val id: String,
    val email: String? = null,
    val fullName: String? = null,
    val avatarUrl: String? = null
)

/** 200 response of the exchange route. */
@Serializable
data class ExchangeResponse(
    val success: Boolean = false,
    val token: String = "",
    val tokenType: String = "",
    /** Epoch milliseconds (server clock). */
    val expiresAt: Long = 0,
    /** Seconds; the backend issues 300. */
    val expiresIn: Long = 0,
    val provisioned: Boolean = false,
    val user: ApiUser? = null
) {
    override fun toString(): String = "ExchangeResponse(success=$success, token=<redacted>, expiresIn=$expiresIn, provisioned=$provisioned)"
}

/** Error envelope used by every backend error: {"success":false,"error":"...","code":"...","requestId":"..."}. */
@Serializable
data class ErrorBody(
    val success: Boolean? = null,
    val error: String? = null,
    val code: String? = null,
    val requestId: String? = null
)

@Serializable
data class LinkedIdentity(
    val provider: String = "",
    val signInMethod: String? = null,
    val emailVerified: Boolean = false,
    val linkMethod: String? = null,
    val linkedAt: String? = null,
    val lastLoginAt: String? = null,
    val disabled: Boolean = false
)

/** GET /api/identity/me -> user. Never contains password hashes, provider subjects or tokens. */
@Serializable
data class IdentityProfile(
    val id: String,
    val email: String? = null,
    val fullName: String? = null,
    val avatarUrl: String? = null,
    val createdAt: String? = null,
    val emailVerified: Boolean = false,
    val hasPassword: Boolean = false,
    val identities: List<LinkedIdentity> = emptyList()
)

@Serializable
data class IdentityMeResponse(val success: Boolean = false, val user: IdentityProfile? = null)

/**
 * POST /api/auth/firebase/link/start body (Phase 2B.5B.2, backend/src/routes/accountLinkRoutes.js). The backend schema is
 * STRICT: exactly these three fields. No user id, Firebase UID, project, e-mail or verification flag is ever sent.
 */
@Serializable
data class LinkStartRequest(val idToken: String, val password: String, val consent: Boolean) {
    override fun toString(): String = "LinkStartRequest(idToken=<redacted>, password=<redacted>, consent=$consent)"
}

/** POST /api/auth/firebase/link/complete body. Exactly these three fields. */
@Serializable
data class LinkCompleteRequest(val idToken: String, val challenge: String, val consent: Boolean) {
    override fun toString(): String = "LinkCompleteRequest(idToken=<redacted>, challenge=<redacted>, consent=$consent)"
}

/** 201 response of link/start. [challenge] is a one-time secret (43 base64url chars), valid for [expiresIn] seconds. */
@Serializable
data class LinkStartResponse(
    val success: Boolean = false,
    val challenge: String = "",
    /** Epoch milliseconds (server clock). */
    val expiresAt: Long = 0,
    /** Seconds; the backend issues at most 300. */
    val expiresIn: Long = 0
) {
    override fun toString(): String = "LinkStartResponse(success=$success, challenge=<redacted>, expiresIn=$expiresIn)"
}

/** 200 response of link/complete: the normal Phase 2A API token for the ORIGINAL canonical users.id. */
@Serializable
data class LinkCompleteResponse(
    val success: Boolean = false,
    val token: String = "",
    val tokenType: String = "",
    /** Epoch milliseconds (server clock). */
    val expiresAt: Long = 0,
    /** Seconds; the backend issues 300. */
    val expiresIn: Long = 0,
    val linked: Boolean = false,
    val user: ApiUser? = null
) {
    override fun toString(): String = "LinkCompleteResponse(success=$success, token=<redacted>, expiresIn=$expiresIn, linked=$linked)"
}

/** Error codes defined by the backend contract. */
object ApiCodes {
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val INVALID_FIREBASE_TOKEN = "INVALID_FIREBASE_TOKEN"
    const val EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED"
    const val UNSUPPORTED_SIGN_IN_METHOD = "UNSUPPORTED_SIGN_IN_METHOD"
    const val IDENTITY_DISABLED = "IDENTITY_DISABLED"
    const val ACCOUNT_LINK_REQUIRED = "ACCOUNT_LINK_REQUIRED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val IDENTITY_UNAVAILABLE = "IDENTITY_UNAVAILABLE"
    /** Phase 2A requireAuth: missing/invalid/expired API token. */
    const val UNAUTHENTICATED = "UNAUTHENTICATED"
    const val USER_MISMATCH = "USER_MISMATCH"
    const val USER_NOT_FOUND = "USER_NOT_FOUND"

    // Account linking (Phase 2B.5B.2)
    const val CONSENT_REQUIRED = "CONSENT_REQUIRED"
    const val LINK_CHALLENGE_INVALID = "LINK_CHALLENGE_INVALID"
    const val REAUTHENTICATION_REQUIRED = "REAUTHENTICATION_REQUIRED"
    const val LINK_PROOF_FAILED = "LINK_PROOF_FAILED"
    const val IDENTITY_ALREADY_LINKED = "IDENTITY_ALREADY_LINKED"
}
