# Validation status

This file separates automated development checks from checks that require a physical Android phone. A successful APK build does not establish listening quality, Bluetooth compatibility, safe headphone loudness, or a measured latency target.

## Automated checks

Results from the supplied build (18 October 2026, Asia/Manila):

| Check | Command / method | Status |
| --- | --- | --- |
| Standalone audio-processing checks | `sh tests/audio/run.sh` | Passed: 512,022 sample-level assertions across generated signals at 16/48 kHz |
| Debug APK compilation and packaging | `./gradlew :app:assembleDebug` | Passed; Android 8+ / target Android 15 |
| Android static analysis | `./gradlew :app:lintDebug` | Passed with 0 errors and 2 reviewed warnings |
| Web UI interaction checks | `npm run test:ui` | 23 passed; 1 duplicate screenshot check intentionally skipped; 320 px and 375 px widths |
| Android integration test APK | `./gradlew :app:assembleDebugAndroidTest` | Passed compilation; 8 runtime tests were not executed because emulator startup was blocked |
| APK signature | Android SDK `apksigner verify --verbose` | Passed, APK Signature Scheme v2, one debug signer |
| APK contents and permissions | Android SDK `aapt dump badging` | Correct launch activity, minSdk 26, targetSdk 35; no INTERNET permission |
| Source archive | ZIP integrity, required-file checks, and build from extracted source | Passed; source, wrappers, and docs included; local keys/SDKs/caches excluded |

The two lint warnings are expected: the legacy-back opt-out attribute is ignored on Android versions before API 33, and JavaScript is enabled for the bundled local interface. The WebView blocks network loads and external navigation, disallows general file/content access, and renders user profile names and notes as text. Tests check text escaping. No lint checks were disabled.

Android runtime checks were blocked by the cloud test environment. Hardware virtualization (`/dev/kvm`) is unavailable. The Android 11 software-only emulator encountered a system StorageManager startup crash before app installation. A separate Android 15 image got further but did not complete boot within the test window (over 10 minutes from launch). No Android app-launch or native integration-test pass is claimed. The 8 compiled instrumentation tests cover actual SharedPreferences, backup validation, and WebView/bridge startup and can be run with `./gradlew :app:connectedDebugAndroidTest` on a working emulator or test phone. No physical phone or headphones are attached here.

The DSP checks cover finite bounded output, silence, frequency response, low-level noise attenuation, surroundings blend, live settings changes, mute, and invalid input bounds. The browser tests use a simulated bridge and cover onboarding, start/stop commands, modes/sliders, profiles, feedback, backup commands, data escaping, navigation, and responsive layouts. They do not execute Android microphone code. Automated signals cannot substitute for listening tests with real speech and noise.

## Physical Android acceptance checks

Use Android 8 or later, begin with low media volume, set the app output to 25%, and connect headphones. Record the phone model, Android version, headphone model, and connection type with the results. Do not mark these checks passed without running them.

| Scenario | Expected result | Status |
| --- | --- | --- |
| First install and startup | App opens without an account or network connection. | Not yet tested on a physical phone |
| Permission denial | Denying microphone access shows a useful status and does not start capture or crash. Granting permission later permits a new session. | Not yet tested |
| Basic wired/USB-C listening | Speech reaches headphones; controls and mode changes have an audible effect without unstable output. | Not yet tested |
| No headphones | Starting live playback without a supported headphone route is prevented or stopped with clear guidance. | Not yet tested |
| Headphone disconnection | Playback/capture stops promptly, and audio is not unexpectedly redirected to the speaker. | Not yet tested |
| Bluetooth | Supported route works if available; note latency, microphone selection, sound quality, and reconnection behavior. Wired/USB-C fallback remains available. | Not yet tested |
| Notification Stop | Tapping Stop ends listening and releases the microphone. | Not yet tested |
| Screen off and lock | Verify whether listening continues as intended on this device and can still be stopped. Record manufacturer power-management interruptions. | Not yet tested |
| App backgrounded | Navigation away from the activity behaves consistently with the foreground service and notification. | Not yet tested |
| App killed / force-stopped | Force stop releases microphone/playback and does not silently restart listening. Separately record swipe-away behavior. | Not yet tested |
| Incoming phone call / audio focus loss | Listening stops or suspends cleanly; restart is deliberate after the interruption. No unexpected loudspeaker output occurs. | Not yet tested |
| Volume and mode changes | Settings remain within allowed ranges and do not cause non-finite audio, abrupt unstable output, or UI/service disagreement. | Not yet tested |
| Profile persistence | Save a profile, close and reopen AURA, and confirm it can be recalled correctly. | Not yet tested |
| Keyboard and screen insets | Open profile/feedback forms; controls remain visible with the keyboard, gesture navigation, and system font scaling. | Not yet tested |
| Session feedback | End a session, save a rating/note, reopen the app, and confirm metadata survives without an audio recording. | Not yet tested |
| Export and restore | Export JSON, change settings, restore the export, and verify expected profiles/settings/history. | Not yet tested |
| Invalid import | Malformed JSON, wrong schema, out-of-range controls, and excessively large files are rejected safely without replacing valid data. | Not yet tested |
| Cancel document picker | Cancelling export/import returns to the app without losing data or crashing. | Not yet tested |
| Reset | Confirm reset clears the intended local data and does not delete unrelated files or existing exported backups. | Not yet tested |
| Offline use | With Wi-Fi and mobile data off, start/stop, controls, profiles, and local backup still work. Bluetooth can remain on when testing that route. | Not yet tested |
| Long session | Run a representative session and record battery use, temperature, dropouts, and interruption behavior. | Not yet tested |

## Measuring latency and quality

The app makes no guaranteed latency claim. Measure the delay on the actual phone and headphones with an external test setup if timing matters. Bluetooth measurements should be separate from wired/USB-C measurements.

Compare short speech samples in quiet, steady noise, and competing speech using low playback levels. Record settings and subjective clarity; more noise reduction is not always better. A small test sample does not establish medical benefit, reliable emergency-sound preservation, or safety for all users.
