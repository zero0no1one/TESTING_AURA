# Security controls and review status

## Codex Security request: blocked

The `codex-security:security-scan` skill was loaded. Its required `references/scan-prologue.md`, capability preflight, `core-scan.md`, and report tooling were unavailable through the skill provider and local environment. No Codex Security runtime tools were exposed. The skill requires: **“Start source review and launch scan workers only after preflight returns `ready`.”** That prerequisite could not be met.

Accordingly, no Codex Security scan ran or passed, and no canonical Codex Security findings/report was generated. This document records implementation controls and ordinary development checks; it is not a substitute scan report or a security certification. Restore the complete plugin/runtime and rerun its repository scan before treating that request as fulfilled.

Skill source: `skill://Plugin_1e648473be9c8191a91ac3947151af55/security-scan/SKILL.md`.

## Implemented boundaries

- The bundled WebView blocks network loading, external navigation, general file/content access, mixed content, and JavaScript network requests. User text is inserted as text, not HTML. Native Android dialogs handle email, password, and recovery-code input; tokens never enter the JavaScript state.
- Native HTTPS accepts only hosted `https://<20-character-project-ref>.supabase.co` origins. Redirects are disabled; system certificate/hostname validation remains enabled. The app does not disable TLS checks or trust all certificates.
- The APK contains a public publishable key only. Secret/service-role keys are rejected by configuration validation. User operations use Supabase access tokens; server-side RLS is authoritative.
- Access/refresh tokens are encrypted with AES-GCM and an Android Keystore key. Passwords and recovery codes are not persisted. Authentication dialogs request secure windows. Android app backup/data transfer is excluded.
- All cloud requests run on a serialized worker, with timeouts, bounded response sizes, and no retries that silently repeat destructive actions. Invalid/revoked refresh sessions return the user to sign-in. Errors do not echo tokens or raw server responses.
- Database RLS enforces account ownership for SELECT, INSERT, UPDATE, DELETE. Anonymous clients have no table privileges. User IDs cannot be reassigned by a client. A primary key limits each account to one snapshot; constraints limit size and array counts.
- Account deletion is a narrowly scoped authenticated RPC: it accepts no target ID, uses `auth.uid()`, has an empty search path, and deletes only that identity in the dedicated AURA project. Related AURA data cascades. This function is not suitable for a shared identity project without additional product review.
- Upload, restore, delete, and project-change behavior is explicit. Microphone samples are never passed to the cloud client. Imports are fully validated before replacing local data. Restoring stops listening first.
- Local and remote deletion are separate. Cloud data is not end-to-end encrypted, and administrator access/provider retention remain relevant.

## Evidence and open checks

- Isolated PostgreSQL test: 31 successful policy/constraint/account-lifecycle operations, including rejected cross-account inserts/updates, no cross-account reads/deletes, denied anonymous requests, limit enforcement, server timestamps, and self-only account deletion. Supabase Auth identity functions were simulated; this does not establish deployed policies.
- JVM tests: public-key/origin validation, old-backup compatibility, import atomicity, persistence limits, and session-context recommendations without volume increases. Android APIs are simulated by Robolectric, not a physical device.
- Browser tests: user-text escaping, bridge-only cloud actions, disabled actions during requests, context controls, post-session feedback, and narrow-screen layouts. These do not execute native networking or Android Keystore.
- Ordinary code review found and fixed existing-short-password sign-in rejection, a stale onboarding privacy sentence, and invalid-refresh-session recovery. This was not run as a Codex Security scan.
- Live Supabase authentication/recovery/backup/account deletion remains unverified because the environment proxy rejects the project and no management connector is available. The owner must install the migration and perform the two-account acceptance checks in `SUPABASE_SETUP.md`.
- Physical-phone audio routing, native dialog behavior, Keystore persistence, and device lifecycle checks remain unverified. The debug APK is intended for testing, not an assurance of production security.
