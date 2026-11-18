# PaketKu Tracker — Android

Native Android app built with Kotlin and Jetpack Compose.

## Included
- Local parcel list saved on device
- Add/remove shipment records and search
- BinderByte tracking integration (requires a BinderByte API key in app settings)
- Periodic tracking worker
- Supabase email registration/login
- Manual cloud sync for authenticated users
- Row-level security SQL in `SUPABASE_SETUP.sql`

## Supabase
See `SUPABASE_SETUP.md`. The app contains the supplied Supabase project URL and publishable key in `app/build.gradle.kts`. The URL is the project root (without `/rest/v1/`). Run the SQL before trying cloud sync.

## Build
Open this folder in Android Studio (Giraffe or newer), allow Gradle sync, then choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**. Install the generated debug APK from `app/build/outputs/apk/debug/`.

A Gradle wrapper is not included in this source archive. Android Studio can import the Gradle project and use its configured Gradle distribution.

## Current limitations
- Cloud sync runs after successful login/registration and when the user taps Sync; it is not continuous background cloud sync.
- Cloud deletes, automatic token refresh, and conflict resolution are not implemented yet.
- BinderByte tracking requires a separate BinderByte API key and may consume paid request quota.
- Build and runtime validation still need to be performed in Android Studio with the target Android SDK.
