# AURA 1.1 for Android

[Download AURA 1.1 APK](https://github.com/zero0no1one/TESTING_AURA/raw/refs/heads/aura-download/AURA-1.1.apk) · [Complete source ZIP](https://github.com/zero0no1one/TESTING_AURA/raw/refs/heads/aura-download/AURA-source.zip) · [Browse source](https://github.com/zero0no1one/TESTING_AURA/tree/aura-upgrade)

A local-first listening prototype with a Before–During–After workflow, seven listening contexts, Personal Acoustic Profiles, session-specific feedback, and optional Supabase Free accounts and metadata backup. Microphone audio stays on the phone.

## Install or update

1. Use Android 8.0 or newer. Open the APK download link in Chrome.
2. Open Files → Downloads → AURA-1.1.apk. Allow installation from that source if Android asks.
3. This APK uses the same application ID and signer as the previously delivered AURA 1.0 APK; install it as an update. Export a local backup first.
4. Connect headphones, allow microphone permission, and begin at low volume. Listening does not require an account.

[Full installation guide](INSTALL_ANDROID.md) · [Build instructions](BUILD_AND_RUN.md)

## Supabase setup required

The supplied public project connection is included. Before cloud backup can work, the project owner must run [AURA-database.sql](AURA-database.sql) and configure email authentication/recovery as described in [SUPABASE_SETUP.md](SUPABASE_SETUP.md). The environment could not reach the live project; live authentication and backup remain unverified.

Cloud backups contain settings, profiles, context, session details, and written notes. Upload and restore are explicit replacement actions, not automatic synchronization. Accounts are optional; no audio is uploaded. Row-level security restricts each user to their own backup. Never use a secret or service-role key in the app.

## Validation and limitations

- Build, signing, lint (0 errors, 2 reviewed warnings), 11 JVM tests, 35 browser UI tests, and 512,022 DSP assertions passed.
- The migration passed 31 PostgreSQL policy/constraint/lifecycle operations with simulated Auth identities.
- The source archive builds independently. The debug-signed APK is for direct installation/testing.
- Physical Android audio, native authentication/Keystore, and live Supabase end-to-end checks remain unverified.
- The requested Codex Security scan was blocked by missing plugin resources/runtime. [Security status](SECURITY_STATUS.md) records controls and evidence; no scan pass is claimed.
- Selected-speaker isolation, semantic sound classification, and reliable alarm/siren preservation remain research objectives. The app clearly labels them as unavailable.
- The supplied logo guided the visual design. Exact matching to unseen Drive demo screens remains unverified.

[Detailed validation](VALIDATION.md) · [Proposal alignment](https://github.com/zero0no1one/TESTING_AURA/blob/aura-upgrade/docs/PROPOSAL_ALIGNMENT.md) · [SHA-256 checksums](SHA256SUMS)
