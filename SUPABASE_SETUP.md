# PaketKu Tracker — Supabase setup

## Project configuration

The Android project is configured with the supplied project URL normalized to the project root:

- Project URL: `https://ghlwwlklkxcghregifyv.supabase.co`
- API key: Supabase publishable key (public client key; never use a `service_role`/secret key in an APK)

The `/rest/v1/` suffix must not be included in the base URL because the app adds `/auth/v1/` and `/rest/v1/` itself.

## 1. Create the private parcels table

1. Open Supabase Dashboard for project `ghlwwlklkxcghregifyv`.
2. Open **SQL Editor** → **New query**.
3. Paste the complete contents of `SUPABASE_SETUP.sql` and run it.
4. Confirm `public.parcels` exists in Table Editor.

The table uses Row Level Security. Each authenticated account can read and change only rows whose `user_id` matches `auth.uid()`.

## 2. Enable email authentication

In Supabase Dashboard, open **Authentication → Providers → Email** and ensure Email provider is enabled. If email confirmation is enabled, a new user must confirm the email before login. For production, keep email confirmation enabled.

## 3. Use the Android app

1. Build and install the Android app.
2. Tap **Login** in the top bar.
3. Register with email and a password of at least 6 characters, or log in.
4. Tap **Sync** on the home screen (or **Sinkronkan** in the account dialog).

Local records are kept on the device. Sync uploads local records and then downloads cloud records for the signed-in user. The current first integration is manual/on-login sync; it does not yet sync deletes, refresh expired sessions automatically, or resolve simultaneous edits across devices. Re-login if an access token expires.

## Security

The publishable key is intended for use in client apps and is not an admin credential. Never embed `service_role`, secret keys, or database passwords in the APK. RLS policies are essential; do not disable them.
