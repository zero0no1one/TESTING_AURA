# Privacy and optional cloud data

AURA processes microphone sound on your Android phone and plays the result through headphones. It does not record microphone audio to a file, upload conversations, contact an inference API, or require an account. The app uses internet permission only for optional native Supabase authentication and metadata backup operations. It has no advertising SDK, analytics service, or cloud audio inference. The WebView cannot load network content.

## Permissions

- **Internet:** used for sign-up, sign-in, token refresh during cloud actions, password recovery, explicit backup/restore, and deletion. Offline listening works without it.
- **Microphone:** required for live listening. If you deny access, you can still browse the interface and settings, but microphone listening cannot start.
- **Modify audio settings:** lets the app configure its own audio session and request the intended headphone route.
- **Foreground service:** lets Android represent active listening with a persistent service notification. Stop listening from the app or notification.
- **Notifications, where requested by Android:** make the active service and its Stop action easier to access. Notification behavior differs across Android versions.
- **Documents selected by you:** Android's document picker grants access to the specific backup file you choose. The app does not need broad access to all phone storage.

Android may show a microphone privacy indicator while listening. Stopping a session releases microphone and playback resources. Device-specific interruption and background behavior should be checked using the [validation checklist](VALIDATION.md).

## Stored locally

AURA stores preferences, up to 20 saved profiles, and up to 200 recent session records on the phone. The Android manifest disables automatic app-data backup; explicit JSON export and optional Supabase backup are the supported backup paths. Session records contain time, duration, initial context/settings/profile label, ratings, and optional written notes; they do not contain microphone audio. Avoid entering sensitive conversation details into notes if you do not want those details included in a later backup.

The app's private storage benefits from the normal Android device protections. This is not a promise of an independently audited encrypted vault. Use your phone's screen lock and regular security updates.

## Export and import

A JSON export includes saved app data such as preferences, profiles, and session feedback. It is a readable file. It is not a microphone recording or an encrypted archive.

You choose where to save it using Android's document picker. A local folder keeps it on the phone; a cloud provider you select may upload it according to that provider's settings. This file-picker export is separate from AURA's optional Supabase account backup.

Only restore backups you trust. Invalid or incompatible files are rejected before import. Import accepts AURA version 1 JSON backups of at most 1 MB and replaces settings, profiles, and session history rather than merging them. Export your current data before restoring another backup if you need to keep both versions.

## Delete data

Use the app's reset option to remove the local settings and history managed by the app. Android's **Settings → Apps → AURA → Storage → Clear storage** or uninstalling AURA removes internal app data as well. Delete exported backup files separately from the locations you saved them to. Local reset does not delete a cloud backup/account or sign you out; use the Account controls separately. Clear storage or uninstalling also removes local sign-in configuration and encrypted tokens.

Build tools may download dependencies during development. That development traffic is separate from the installed app, whose listening features work offline.

## Supabase account and backup

The configured project is `https://cwnfvwezyqmwviixaqxr.supabase.co`. You may disconnect or replace the project under Account. The APK contains a public publishable key, never an administrator/service-role key.

Account creation sends email/password to Supabase over HTTPS. The password is not persisted by AURA or exposed to WebView JavaScript. Supabase manages password authentication. Access/refresh tokens are encrypted locally using a non-exportable Android Keystore key; cloud state exposed to the UI contains account email/status, not tokens. Password/recovery dialogs block screenshots using Android's secure-window flag. This does not make a rooted or otherwise compromised phone trustworthy.

Nothing is backed up automatically. When you confirm **Back up now**, settings, contexts, saved profiles, session details, and written notes replace your account's previous backup. Audio samples and recordings are never uploaded. A cloud restore must be confirmed and validated before replacing local data. Metadata is protected by HTTPS and owner-only RLS, but is not end-to-end encrypted: the project administrator can access it. Avoid including sensitive medical/conversation details in notes that you intend to upload.

**Delete cloud backup** removes the database row; **Delete cloud account** removes the signed-in Auth identity and its associated backup in the dedicated AURA project. Copies on phones, manually exported files, and provider-retained logs/backups are separate. Supabase's applicable retention practices also apply. Local sign-out clears this phone's tokens and makes a best-effort request to revoke that session; when offline, server revocation cannot be guaranteed. Existing access tokens elsewhere may remain valid until their normal expiry.

Free-plan availability, email delivery, and project inactivity can affect account actions. See [setup and limits](SUPABASE_SETUP.md). A completed security audit is not claimed; see [security status](SECURITY_STATUS.md).

## Local sound classification

The bundled YAMNet model uses two reusable PCM windows of about 0.975 seconds each plus temporary inference buffers in memory. Windows are overwritten as listening continues; the worker clears completed PCM windows, and shutdown clears the reusable buffers and releases the model. JVM/native memory release is not a forensic erasure guarantee. Neither PCM, model-input windows nor category predictions are written to files, stored in session history, included in backups, or sent to Supabase. You can turn sound categories off while continuing to listen. No model download happens at runtime.
