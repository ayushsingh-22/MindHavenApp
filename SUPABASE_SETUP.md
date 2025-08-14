# Supabase Configuration Setup Guide

## Step 1: Create a Supabase Project

1. Go to [supabase.com](https://supabase.com) and create a new account or sign in
2. Create a new project
3. Wait for the project to be fully initialized

## Step 2: Get Your Project Credentials

1. Go to your project dashboard
2. Navigate to Settings > API
3. Copy the following values:
   - **Project URL**: `https://your-project-ref.supabase.co`
   - **Anon (public) key**: `your-anon-key`

## Step 3: Update the Constants File

Replace the placeholder values in `app/src/main/java/com/example/mindhaven/data/SupabaseConstants.kt`:

```kotlin
const val SUPABASE_URL = "https://your-project-ref.supabase.co"
const val SUPABASE_ANON_KEY = "your-anon-key"
```

## Step 4: Configure Google OAuth

### 4.1 Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Google+ API

### 4.2 Create OAuth Credentials

1. Go to APIs & Credentials > Credentials
2. Click "Create Credentials" > "OAuth 2.0 Client IDs"
3. Choose "Web application" for type
4. Add your Supabase project URL to "Authorized JavaScript origins"
5. Add your Supabase callback URL to "Authorized redirect URIs":
   `https://your-project-ref.supabase.co/auth/v1/callback`

### 4.3 Create Android Credentials (for native Google Sign-In)

1. Create another OAuth 2.0 Client ID
2. Choose "Android" for application type
3. Provide your package name: `com.example.mindhaven`
4. Get your SHA-1 fingerprint:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
5. Add the SHA-1 fingerprint to the credentials

### 4.4 Update Google Sign-In Helper

Replace the client ID in `app/src/main/java/com/example/mindhaven/utils/GoogleSignInHelper.kt`:

```kotlin
private val webClientId = "your-google-oauth-client-id.apps.googleusercontent.com"
```

## Step 5: Configure Supabase Authentication

1. In your Supabase dashboard, go to Authentication > Providers
2. Enable Google provider
3. Add your Google OAuth client ID and secret
4. Add your redirect URLs:
   - `mindhaven://auth` (for mobile deeplink)
   - Your app's domain if you have one

## Step 6: Configure Authentication Settings

1. Go to Authentication > Settings
2. Set your Site URL (can be your app's domain or a placeholder)
3. Add additional redirect URLs if needed:
   - `mindhaven://auth`

## Step 7: Enable Row Level Security (Optional but Recommended)

1. Go to Database > Tables
2. Create your application tables
3. Enable RLS policies as needed

## Step 8: Test Your Integration

1. Build and run your app
2. Test email sign-up and sign-in
3. Test Google authentication
4. Check that users appear in your Supabase dashboard under Authentication > Users

## Troubleshooting

### Common Issues:

1. **"Invalid OAuth redirect URI"**: Make sure your redirect URLs are properly configured in both Google Cloud Console and Supabase
2. **"Email not confirmed"**: Check your email settings in Supabase and make sure email confirmation is properly configured
3. **Google Sign-In fails**: Verify your SHA-1 fingerprint is correct and the package name matches
4. **Deeplinks not working**: Make sure the intent filter is properly configured in AndroidManifest.xml

### Debug Steps:

1. Check Android Logcat for error messages
2. Verify network connectivity
3. Check Supabase logs in the dashboard
4. Verify all credentials are correctly entered

## Security Notes

- Never expose your `service_role` key in client applications
- Only use the `anon` key in your mobile app
- Set up proper RLS policies for your tables
- Consider implementing email verification for production apps

## Production Checklist

- [ ] Replace debug SHA-1 with production SHA-1 fingerprint
- [ ] Update redirect URLs for production domain
- [ ] Set proper RLS policies
- [ ] Configure proper email templates
- [ ] Set up proper error handling
- [ ] Test all authentication flows thoroughly
