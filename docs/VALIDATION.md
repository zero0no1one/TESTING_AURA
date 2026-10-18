# Validation status

This file separates automated development checks from checks that require a physical Android phone. A successful APK build does not establish listening quality, Bluetooth compatibility, safe headphone loudness, or a measured latency target.

## Automated checks

Results from AURA 1.2.0 (18 October 2026, Asia/Manila):

| Check | Command / method | Status |
| --- | --- | --- |
| Standalone audio-processing checks | `sh tests/audio/run.sh` | Passed: 640,036 DSP assertions plus 15,608 resampler/category assertions at 16/48 kHz |
| Debug APK compilation and packaging | `./gradlew :app:assembleDebug` | Passed; Android 8+ / target Android 15 |
| Android static analysis | `./gradlew :app:lintDebug` | Passed with 0 errors and 2 reviewed warnings |
| Web UI interaction checks | `npm run test:ui` | 41 passed; 1 duplicate screenshot check intentionally skipped; 320 px and 375 px widths |
| JVM persistence/configuration checks | `./gradlew :app:testDebugUnitTest` | 12 passed (10 Robolectric storage/upgrade tests, 2 origin/key checks) |
| Database ownership/constraint checks | `python3 tests/cloud/test_database.py` | 31 operations passed in 1.1; unchanged migration, not rerun for 1.2; Auth identities simulated |
| Live Supabase end-to-end | Auth/recovery/backup/restore/deletion | Not run: project blocked by environment proxy; owner must install migration and test |
| Codex Security plugin scan | Required capability preflight | Blocked: supporting skill resources/runtime unavailable; no scan pass claimed |
| Android integration test APK | `./gradlew :app:assembleDebugAndroidTest` | Passed compilation; 8 runtime tests were not executed because emulator startup was blocked |
| APK signature | Android SDK `apksigner verify --verbose` | Passed, APK Signature Scheme v2, same debug certificate as the delivered 1.0 APK |
| APK contents and permissions | Android SDK `aapt dump badging` | Correct launch activity, version code 3, minSdk 26, targetSdk 35; INTERNET added for optional native Supabase only |
| Embedded model | `tests/models/test_model.py` in pinned Python test environment | Passed SHA-256/vocabulary/shapes and desktop smoke: Silence 0.801, Speech 0.918; no category-accuracy benchmark |
| Native library / ZIP alignment | `readelf -lW` and SDK `zipalign -c -P 16 4` | Passed 16 KiB alignment check for packaged 64-bit LiteRT libraries and APK; physical 16 KiB Android device untested |
| Source archive | ZIP integrity, required-file checks, and build from extracted source | Passed; source, wrappers, and docs included; local keys/SDKs/caches excluded |

The two lint warnings are expected: the legacy-back opt-out attribute is ignored on Android versions before API 33, and JavaScript is enabled for the bundled local interface. The WebView blocks network loads and external navigation, disallows general file/content access, and renders user profile names and notes as text. Tests check text escaping. No lint checks were disabled.

Android runtime checks were blocked by the cloud test environment. Hardware virtualization (`/dev/kvm`) is unavailable. The Android 11 software-only emulator encountered a system StorageManager startup crash before app installation. A separate Android 15 image got further but did not complete boot within the test window (over 10 minutes from launch). No Android app-launch or native integration-test pass is claimed. The 8 compiled instrumentation tests cover actual SharedPreferences, backup validation, and WebView/bridge startup and can be run with `./gradlew :app:connectedDebugAndroidTest` on a working emulator or test phone. No physical phone or headphones are attached here.

The DSP checks cover finite bounded output, silence, frequency response, low-level noise attenuation, surroundings blend, live settings changes, mute, and invalid input bounds. The browser tests use a simulated bridge and cover onboarding, start/stop commands, contexts, modes/sliders, profiles, post-session feedback, snapshot-based recommendations, cloud action commands, data escaping, navigation, and responsive layouts. The PostgreSQL suite tests the actual migration with mock Auth identities, including cross-user denial and account-deletion isolation. JVM tests use Robolectric for Android storage APIs; they do not execute Android Keystore or live Supabase networking. They do not execute Android microphone code. Automated signals cannot substitute for listening tests with real speech and noise.

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

## Additional 1.1 acceptance checks

- Install over the previously delivered APK without uninstalling; confirm old profiles/history survive and new context defaults to Everyday conversation.
- Test native email/password/recovery dialogs with the keyboard open and large system text. Confirm screenshot blocking for credential dialogs on the actual phone.
- Complete the two-account Supabase checks in [SUPABASE_SETUP.md](SUPABASE_SETUP.md). Test valid sign-in with an existing password shorter than 12 characters, rejected/revoked token handling, app restart with encrypted tokens, offline sign-out, and reconnection.
- Verify a failed cloud restore leaves current data intact. Confirm live listening stops before an accepted restore.
- Run the Codex Security repository scan after its complete plugin is available. See [SECURITY_STATUS.md](SECURITY_STATUS.md); ordinary testing is not equivalent to that scan.
- Attach the missing demo screens for exact UI comparison. Only the supplied waveform emblem and document terminology have been matched.

## Additional 1.2 acceptance checks

- Physical wired/USB-C vocal audibility and the reported vibrating sound remain **unverified**. No phone/headphones are attached here. Measure input/output dBFS, selected route, media volume and underruns using [Audio troubleshooting](AUDIO_TROUBLESHOOTING.md).
- Verify Phone microphone and an actual headset microphone separately. Missing/changed input routes should stop with a useful message. Direct comparison should disable optional Android noise suppression while retaining volume/gain/limiter.
- Compare direct and filtered listening at low volume; raise boost gradually only for quiet speech. Check 0 dB and +6/+12 dB, immediate mute, switching the notch/monitor, and saved profile recall.
- The synthetic DSP regression confirms that switching direct comparison off after a silent interval does not replay stale hum-notch energy. It does not certify click-free audio on every phone.
- Test classifier loading, categories off/on, unavailable status, live scores, stop/restart, CPU use and longer sessions. Compare underruns with categories off. Classification is pre-DSP and does not control playback.
- The model smoke test covers one official speech sample and silence only. Vehicles, air/wind, sirens, simultaneous sounds, far-field speech and physical phone performance have **not** been benchmarked. Do not infer category accuracy or reliable emergency recognition from the smoke test.
- Source review found and corrected stale notch state when toggling direct comparison. This was an ordinary correctness review, not a Codex Security scan.
