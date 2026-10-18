# Purpose and feature scope

AURA's source documents describe assistive listening: making speech easier to follow in noisy environments while keeping some awareness of surroundings. They propose Voice Focus, Care/Focus/Commute modes, personal preferences, session feedback, and local processing. The provided image supplies the navy and pale-blue visual direction.

The implementation is a free, local-first native Android prototype, upgraded with optional Supabase metadata backup at the user's request. Any document suggestions involving commercial subscriptions, paid services, research claims, or future AI capabilities are product context, not instructions to buy services or claim those features have been achieved.

## What the app displays and uses

- Current listening state and audio-route information.
- Microphone permission and headphone availability.
- The selected listening mode and current audio settings.
- Saved Personal Acoustic Profiles, selected listening contexts, and preferences.
- Optional cloud account status, manual backup status, and data-deletion controls.
- Session metadata, ratings, and optional feedback notes.
- Local backup, restore, reset, privacy, and installation guidance.

The output gain is attenuation-only: this version is not a high-gain hearing amplifier. Optional native Supabase authentication and database requests use INTERNET permission, HTTPS, a public publishable key, and a signed-in user token. There is no cloud audio inference or analytics endpoint. All live audio processing runs locally, and the bundled WebView still blocks network access.

## Implemented first-version approach

| Area | Behavior | Practical limit |
| --- | --- | --- |
| Speech enhancement | Speech-oriented filtering and adjustable EQ emphasize useful frequency ranges. | It can affect all voices and other sounds in those bands; it cannot identify the intended speaker. |
| Noise reduction | A local adaptive expander reduces some quieter background sound; Android device noise suppression is used when available and reduction is enabled. | Loud, changing noise and competing speech remain difficult. Strong settings may soften speech or introduce artifacts. |
| Listening modes | Care, Focus, and Commute provide convenient control presets. | Presets are not medical hearing profiles or scientifically calibrated environments. |
| Awareness | A controllable surroundings mix keeps some microphone sound from before the app's speech filtering and expansion. | Android's device noise suppressor may already have processed this sound. The mix does not recognize sirens, alarms, vehicles, or emergency events and cannot guarantee audibility. |
| Output dynamics | Attenuation-only gain, compression, and a digital sample peak cap help manage output levels. | Headphone loudness depends on system volume and hardware. A sample cap is not a hearing-safety guarantee. |
| Profiles | Users save and recall up to 20 listening profiles containing context and sound preferences. | The app does not perform an audiogram, diagnose hearing loss, or train a personalized AI model. |
| Feedback | Each new session stores its initial context and settings. An optional reflection opens after listening. Accepted rule-based suggestions use that session's starting settings and never raise current volume. | Feedback can be included in a user-requested cloud backup. It does not train a model or automatically change settings. |
| Background operation | Native microphone processing uses a foreground service and an ongoing notification. Audio-focus loss, headphone disconnection, and route changes stop listening; restart is deliberate. | Device policies and manufacturer power management may interrupt it. Route checks cannot guarantee that Android will never make a brief transition before notifying the app. |
| Backup | AURA settings and feedback can be exported/imported as version 1 JSON using the Android document picker. Import validates at most 1 MB before replacing local data. | Import replaces, rather than merges, settings, profiles, and history. File exports are ordinary readable files. Optional Supabase backup is explicit replacement, not automatic sync or end-to-end encryption. |

## Deliberately unclaimed capabilities

- **Selected-speaker AI isolation:** no voice enrollment, speaker identity model, or separation model is included.
- **Reliable emergency-sound preservation:** awareness is a mix setting, not sound classification.
- **A guaranteed latency such as under 100 ms:** latency must be measured on real device/headphone combinations, particularly with Bluetooth. The displayed Processing time is DSP computation time for one block, not end-to-end latency.
- **Clinical effectiveness:** the implementation has no medical certification and is not a replacement for professional hearing care or a fitted hearing aid.
- **Recordings or live transcription:** no microphone recordings or speech-to-text service are included.
- **System-wide processing:** the app monitors its own microphone stream; it cannot transparently rewrite all Android app audio.

## Free paths for future development

No paid alternative is needed to use the first version. Future research can evaluate open-source noise-reduction models, such as RNNoise, and on-device inference runtimes. Each specific model and dataset still needs license review, device-performance testing, integration work, and listening evaluation. A free model is not automatically suitable for live hearing assistance or selected-speaker isolation.

Optional future features such as local transcription can also use offline models, at the cost of larger downloads, more memory, battery use, and device compatibility work. Any later feature that sends audio off-device should require an explicit product and privacy decision rather than silently replacing the current offline behavior.

## Upgrade workflow

Before: choose a context (everyday, medical consultation, pharmacy, caregiving, family/friends, classroom/work, or public transport) and a saved acoustic profile. Context changes choose a visible preset while preserving the current output volume. During: local audio capture, controls, and real input-level indicators remain available. After: the user can rate the session, skip, or revisit it later.

Supabase accounts, recovery, backup, restore, and account deletion require internet and the migration/email setup in [SUPABASE_SETUP.md](SUPABASE_SETUP.md). Raw audio is never part of a backup. The supplied project has not been verified live from this environment.
