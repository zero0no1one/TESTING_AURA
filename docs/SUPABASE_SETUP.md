# Supabase Free setup for AURA 1.1

The APK is configured for `https://cwnfvwezyqmwviixaqxr.supabase.co` using the public publishable key supplied by the project owner. A publishable key is not a database password and does not grant access to another user's data. Row-level security (RLS) is the access boundary. Never add a secret key or `service_role` key to the app.

**The live database has not been provisioned or verified by this build environment.** Its network proxy blocks this Supabase project, and no Supabase management connector is available. The migration was tested on isolated PostgreSQL with simulated Supabase identities, including cross-user access attempts. Complete the setup below before trying cloud backup.

## 1. Install the database migration

Use a **dedicated AURA project on the Free plan**. The app's Delete cloud account action deletes the signed-in Supabase Auth identity and its AURA backup. Do not use a project where the same identities serve unrelated applications.

1. Sign into [your Supabase project's SQL Editor](https://supabase.com/dashboard/project/cwnfvwezyqmwviixaqxr/sql/new).
2. Open `supabase/migrations/202610180001_aura_backups.sql` from the source archive, or the downloadable `AURA-database.sql` alongside the APK.
3. Copy the entire SQL file into a new query and choose **Run**. Run it once. All statements are in a transaction; errors roll it back.
4. In Table Editor, confirm `public.aura_backups` exists and RLS is enabled. There are four owner-only policies, one for each data operation. Anonymous users have no table privileges.
5. If Supabase says a table or function already exists, stop and review the existing deployment. Do not drop existing tables or disable RLS to make the script pass.

The database keeps one backup per account, with at most 20 profiles, 200 sessions, and 512 KiB of JSON. The app also validates every restore before replacing local data. The database rejects malformed top-level structures; the app validates individual settings and session entries. No audio storage bucket is created.

## 2. Configure free email authentication

1. Under **Authentication → Sign In / Providers** (or **Providers**), enable Email with passwords and keep email confirmation enabled.
2. Under Auth email settings, configure a verified Site URL you control for confirmation redirects. For your own test account, confirmation can open that site in the browser; return to AURA and sign in manually afterward. AURA does not handle web callback links.
3. Under **Authentication → Email Templates → Reset Password**, include the recovery code explicitly, for example:

   ```html
   <h2>Reset your AURA password</h2>
   <p>Your recovery code is: <strong>{{ .Token }}</strong></p>
   <p>Open AURA → Account → Forgot password? and enter this code with a new password.</p>
   <p>If you did not request this, ignore this message.</p>
   ```

4. Keep the project's auth rate limits enabled. Set a strong password policy; the app requires 12–128 characters for new passwords and accepts existing shorter passwords when signing in.

Supabase's built-in email sender has strict limits and may only deliver to addresses belonging to the project team. It is suitable for limited owner testing, not an unrestricted public launch. For other testers, configure SMTP using an account/provider with an ongoing free allowance; verify current provider terms and sender requirements before adopting it. A personal Gmail SMTP account with an app password, if your account supports it, is a limited free testing alternative with daily limits. Configure SMTP credentials only in Supabase, never in this repository or APK. No trial or paid email subscription is required for offline use. Free SMTP limits may make a large public rollout impractical without additional resources.

If confirmation or recovery mail is not delivered, inspect Supabase Auth logs, sender configuration, and rate limits. Do not disable database security as a workaround. You can keep using all listening features without signing in.

## 3. Use the APK

1. Install AURA 1.1 over the previous APK, open **Account**, and choose **Create account**.
2. Confirm your email, return to AURA, and choose **Sign in**. The email/password dialogs are native Android views; credentials do not enter the WebView.
3. Use AURA normally. **No background upload or automatic synchronization occurs.**
4. Choose **Back up now** and read the confirmation. Settings, contexts, profile names, session details, and written notes will replace your account's previous cloud backup. No microphone audio is uploaded.
5. On another phone, install AURA, sign into the same account, and choose **Restore cloud backup**. Confirm replacement of that phone's local data. Listening is stopped before import.
6. Use **Delete cloud backup** to remove only the remote backup. **Delete cloud account** removes the Auth identity and its backup. Both leave local data intact.
7. **Sign out** clears local sign-in tokens; offline listening and local data remain. **Disconnect project** also removes the project's local configuration. It does not delete remote data or revoke other devices' sessions.

The last-backup time means the most recent successful upload from this phone. It is not a global sync timestamp. A backup from another phone can replace it; restore always retrieves the current account backup. Export a local JSON copy before replacing a backup if you need version history.

## 4. Validate your actual project

Create two test accounts A and B. With A, save a distinct profile and upload a backup. With B, Restore must report no backup until B uploads its own. Restore A again and confirm its profile returns. Delete B's account and confirm A still works. Test password recovery and reopening the app after sign-in, then test sign-out while offline. Confirm RLS remains enabled throughout. These live checks have not been performed in this environment.

## Free-plan and privacy limits

- Check current Free-plan storage, bandwidth, auth/email, and inactivity limits in the Supabase dashboard. Projects may pause; a paused project must be resumed by its owner. No pricing/limit figure is guaranteed by this source code.
- Listening is independent of Supabase and continues offline. Backup, authentication, recovery, and account deletion need internet and an active project.
- Tokens are encrypted using Android Keystore. The APK contains only a public project key. Passwords and recovery codes are not persisted by AURA.
- Cloud backups use HTTPS and per-user RLS. They are **not end-to-end encrypted**; the project owner/administrator can access stored metadata. Notes can be sensitive. The user controls whether to upload them.
- Reset AURA removes local listening data, not the cloud account or authentication configuration. Sign out/disconnect and delete cloud data separately when required. Uninstalling removes app data; exported files and cloud backups remain.
- Hosted `https://PROJECTREF.supabase.co` origins are supported. Custom domains and self-hosted Supabase require a reviewed source change to the origin validator.
