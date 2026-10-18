# AURA 1.2 for Android

[Download AURA 1.2 APK](https://github.com/zero0no1one/TESTING_AURA/raw/refs/heads/aura-download/AURA-1.2.apk) · [Complete source ZIP](https://github.com/zero0no1one/TESTING_AURA/raw/refs/heads/aura-download/AURA-source.zip) · [Browse source](https://github.com/zero0no1one/TESTING_AURA/tree/aura-upgrade)

A local-first listening prototype with a Before–During–After workflow, seven listening contexts, Personal Acoustic Profiles, session-specific feedback, and optional Supabase Free accounts and metadata backup. Microphone audio stays on the phone.

## New audio controls and offline categories

- User-controlled microphone boost (0–24 dB), explicit phone/headset input selection, and direct microphone comparison.
- Optional 50/60 Hz hum filter; Android noise suppression is off by default.
- Input/output levels, phone media volume and playback-underrun diagnostics.
- A bundled offline YAMNet model for likely vocals, vehicles, air/wind and sirens. Scores are not volume controls or accuracy percentages.

The APK is about 24.4 MB because it now includes the model and native inference libraries. No model download, paid service, or account is needed for listening/classification. This update addresses software causes of low speech audibility; the reported vibrating sound has not been verified on the user's phone.

After updating: use **Phone microphone**, enable **Direct microphone comparison**, start at low media volume/app volume 25%, and speak near the phone. If speech is quiet, raise **Microphone boost** gently to +6 dB, then +12 dB only if needed. Stop if distorted or uncomfortable. [Detailed audio troubleshooting and phase-cancellation explanation](https://github.com/zero0no1one/TESTING_AURA/blob/aura-upgrade/docs/AUDIO_TROUBLESHOOTING.md).

## Install or update

1. Use Android 8.0 or newer. Open the APK download link in Chrome.
2. Open Files → Downloads → AURA-1.2.apk. Allow installation from that source if Android asks.
3. This APK uses the same application ID and signer as the previously delivered AURA 1.0/1.1 APKs; install it as an update. Export a local backup first.
4. Connect headphones, allow microphone permission, and begin at low volume. Listening does not require an account.

[Full installation guide](INSTALL_ANDROID.md) · [Build instructions](BUILD_AND_RUN.md)

## Supabase setup required

The supplied public project connection is included. Before cloud backup can work, the project owner must run [AURA-database.sql](AURA-database.sql) and configure email authentication/recovery as described in [SUPABASE_SETUP.md](SUPABASE_SETUP.md). The environment could not reach the live project; live authentication and backup remain unverified.

Cloud backups contain settings, profiles, context, session details, and written notes. Upload and restore are explicit replacement actions, not automatic synchronization. Accounts are optional; no audio is uploaded. Row-level security restricts each user to their own backup. Never use a secret or service-role key in the app.

## Validation and limitations

- Build, signing, lint (0 errors, 2 reviewed warnings), 12 JVM tests, 41 browser UI tests, 640,036 DSP assertions and 15,608 resampler/category assertions passed.
- The migration passed 31 PostgreSQL policy/constraint/lifecycle operations with simulated Auth identities.
- The source archive builds independently. The debug-signed APK is for direct installation/testing.
- Physical Android audio, native authentication/Keystore, and live Supabase end-to-end checks remain unverified.
- The requested Codex Security scan was blocked by missing plugin resources/runtime. [Security status](SECURITY_STATUS.md) records controls and evidence; no scan pass is claimed.
- Offline YAMNet estimates vocals, vehicles, air/wind and sirens. Its desktop smoke test passed for one official speech sample and silence; category accuracy on real phones/noisy mixtures is not established. No sound category is independently isolated or muted. Selected-speaker isolation and reliable warning preservation remain unavailable.
- The supplied logo guided the visual design. Exact matching to unseen Drive demo screens remains unverified.

[Detailed validation](VALIDATION.md) · [Proposal alignment](https://github.com/zero0no1one/TESTING_AURA/blob/aura-upgrade/docs/PROPOSAL_ALIGNMENT.md) · [SHA-256 checksums](SHA256SUMS)

Source revision: `46089d7` on `aura-upgrade`. [Model provenance and limits](https://github.com/zero0no1one/TESTING_AURA/blob/aura-upgrade/docs/SOUND_MODEL.md).
