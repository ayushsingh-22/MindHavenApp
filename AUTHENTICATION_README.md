# MindHaven - Supabase Google Authentication Integration

This project has been integrated with Supabase for authentication, including Google OAuth support. Here's what has been implemented:

## Features Implemented

### 1. Supabase Authentication Setup

- **Supabase Client Configuration**: Configured with PKCE flow for secure authentication
- **Dependency Injection**: Used Hilt for dependency injection of Supabase client
- **Authentication Repository**: Centralized auth operations handling
- **Authentication State Management**: Real-time auth state tracking

### 2. Google Authentication

- **OAuth Flow**: Web-based Google OAuth integration via Supabase
- **Native Google Sign-In**: Android-specific Google Sign-In implementation
- **Dual Authentication Methods**: Support for both OAuth and native flows
- **Deeplink Handling**: Proper deeplink configuration for OAuth callbacks

### 3. Email Authentication

- **Sign Up**: Email/password user registration
- **Sign In**: Email/password user authentication
- **Password Reset**: Forgot password functionality
- **Form Validation**: Client-side validation with user feedback

### 4. UI Components

- **Registration Screen**: Updated with Supabase integration
- **Email Login Screen**: Complete authentication flow
- **Sign Up Screen**: User registration with validation
- **Loading States**: Progress indicators during auth operations
- **Error Handling**: User-friendly error messages via Snackbar

### 5. Navigation Updates

- **Route Management**: Updated navigation with new auth screens
- **Auth State Routing**: Automatic navigation based on authentication status
- **Deeplink Support**: Proper intent filters for OAuth callbacks

## Files Modified/Created

### Core Authentication

- `data/SupabaseConstants.kt` - Configuration constants
- `data/SupabaseModule.kt` - Dependency injection module
- `data/repository/AuthRepository.kt` - Authentication operations
- `viewmodel/AuthViewModel.kt` - UI state management
- `utils/GoogleSignInHelper.kt` - Google Sign-In utilities

### UI Components

- `ui/theme/screens/userRegistration/registerationScreen.kt` - Updated with auth
- `ui/theme/screens/userRegistration/emailLogin.kt` - Complete login screen
- `ui/theme/screens/userRegistration/SignUpScreen.kt` - New signup screen
- `ui/theme/components/AuthWrapper.kt` - Authentication wrapper component

### Configuration

- `MainActivity.kt` - Added deeplink handling
- `AndroidManifest.xml` - Added permissions and intent filters
- `MindHavenApplication.kt` - Hilt application class
- `build.gradle.kts` - Updated dependencies
- `gradle/libs.versions.toml` - Added Supabase dependencies

## Dependencies Added

```kotlin
// Supabase
implementation(platform("io.github.jan-tennert.supabase:bom:3.0.5"))
implementation("io.github.jan-tennert.supabase:auth-kt")
implementation("io.ktor:ktor-client-android:3.0.7")

// Google Play Services (already present)
implementation("com.google.android.gms:play-services-auth:21.4.0")

// Serialization Plugin
id("org.jetbrains.kotlin.plugin.serialization")
```

## Configuration Required

### 1. Supabase Project Setup

- Create a Supabase project at [supabase.com](https://supabase.com)
- Get your project URL and anon key
- Update `SupabaseConstants.kt` with your credentials

### 2. Google Cloud Console Setup

- Create OAuth 2.0 credentials (Web application)
- Create OAuth 2.0 credentials (Android application)
- Configure authorized origins and redirect URIs
- Update `GoogleSignInHelper.kt` with your client ID

### 3. Supabase Authentication Configuration

- Enable Google provider in Supabase dashboard
- Add Google OAuth credentials to Supabase
- Configure redirect URLs: `mindhaven://auth`

## Authentication Flow

### Google OAuth Flow

1. User taps "Continue with Google"
2. Native Google Sign-In launches (if available)
3. On success, ID token is sent to Supabase
4. Supabase validates and creates/updates user session
5. User is redirected to main app

### Email Flow

1. User enters email/password
2. Credentials sent to Supabase
3. Supabase validates and returns session
4. User is authenticated and redirected

### Deeplink Flow (OAuth fallback)

1. User taps "Continue with Google"
2. Browser opens with Google OAuth
3. After authentication, redirects to `mindhaven://auth`
4. App handles deeplink and extracts session
5. User is authenticated

## Security Features

- **PKCE Flow**: Secure OAuth implementation
- **Token Management**: Automatic token refresh
- **Deeplink Validation**: Secure deeplink handling
- **Input Validation**: Client-side form validation
- **Error Handling**: Secure error messages

## Testing

Before testing, ensure:

1. Supabase project is properly configured
2. Google OAuth credentials are set up
3. Android SHA-1 fingerprint is registered
4. All configuration constants are updated

## Production Considerations

1. **Replace Debug SHA-1**: Update with production certificate fingerprint
2. **Environment Variables**: Move sensitive data to build configs
3. **Error Monitoring**: Implement crash reporting
4. **Security Policies**: Set up Supabase RLS policies
5. **Email Templates**: Customize Supabase email templates

## Troubleshooting

Common issues and solutions are documented in `SUPABASE_SETUP.md`.

For detailed setup instructions, see the setup guide included in the project.
