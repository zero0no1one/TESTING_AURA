# AURA

AURA is a free, offline Android listening app inspired by the supplied Project AURA proposal, concept note, presentation deck, and navy/pale-blue visual reference. It sends the phone microphone through basic speech-oriented audio processing to connected headphones. It is intended for personal experimentation and development, with clear limits on what the processing can do.

The user-selected first version includes real microphone processing, listening modes, local profiles, session feedback, and a mobile interface. It does not require an account, subscription, paid API, cloud backend, or trial.

## Get it onto your Android phone

1. Use the provided `dist/AURA-debug.apk`, or build the APK using the instructions below.
2. Follow [Android installation and first use](docs/ANDROID_INSTALL.md) to transfer it to your phone, install it, and grant microphone access.
3. Connect wired or USB-C headphones, or compatible Bluetooth headphones. Start with the phone's media volume low and AURA's output at 25%.

Android 8.0 or later is required. Find your version under **Settings → About phone → Android version**; on some phones it is under **Software information**. Wired or USB-C headphones are the recommended first test because Bluetooth routing and delay vary by device.

The debug APK is signed for personal installation and testing. It is not a Play Store release. Nothing is published automatically by this project.

## What is included

- A native Android microphone and playback service, with an ongoing notification and a Stop action.
- Real-time speech-band filtering, adjustable basic noise reduction, output gain, compression, and a digital peak cap.
- Care, Focus, and Commute presets, with adjustable listening settings.
- An awareness control that mixes some less-processed microphone sound into the output.
- Up to 20 profiles and 200 sessions of local feedback; optional rule-based setting suggestions, plus JSON backup and restore through the Android document picker.
- A bundled, mobile-first WebView interface. The interface and audio processing run on the phone without a network connection.

This version does not isolate a chosen person, recognize alarms, or guarantee that environmental sounds remain audible. It does not implement trained AI models or claim a measured latency target. See [feature scope and limitations](docs/FEATURES.md).

## Build for free

The simplest route is the free [Android Studio](https://developer.android.com/studio) on a Windows, macOS, or Linux computer. Internet access is needed to download Android Studio, the Android SDK, Gradle, and build dependencies; the installed app works offline.

1. Install Android Studio and open this repository as a project.
2. In **Tools → SDK Manager**, install **Android SDK Platform 35**, **Android SDK Build-Tools 35.0.0**, and **Android SDK Platform-Tools**. Accept the SDK licenses when prompted.
3. Let Gradle sync finish. Select a compatible Gradle JDK in **Settings → Build, Execution, Deployment → Build Tools → Gradle**. JDK 17 is the project baseline; a newer compatible JDK such as 21 also works.
4. From the project terminal, run:

   ```sh
   ./gradlew :app:assembleDebug :app:lintDebug
   ```

   On Windows, use:

   ```bat
   gradlew.bat :app:assembleDebug :app:lintDebug
   ```

5. Find the installable APK at `app/build/outputs/apk/debug/app-debug.apk`.

For a command-line setup, install JDK 17 or a compatible newer version, the official Android command-line tools, SDK platform 35, build-tools 35.0.0, and platform-tools. Set `ANDROID_HOME` to the installed SDK, or create an untracked `local.properties` file with `sdk.dir=/absolute/path/to/your/android-sdk`. Run the Gradle command above from the repository root. Do not put SDK paths or signing passwords into tracked configuration.

The build uses Android Gradle Plugin 8.7.3 and the Gradle 8.9 wrapper. Android SDK packages may require accepting their free licenses; no paid developer account is needed for sideloading.

## Run and test

With USB debugging enabled and an authorized phone connected:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open **AURA** from the phone's app launcher. Android Studio's **Run** action is another option. An emulator can check navigation and permissions, but it cannot establish real phone/headphone latency or listening quality.

Run the standalone signal-processing tests with a JDK installed:

```sh
sh tests/audio/run.sh
```

For the actual Android storage and WebView integration checks, use an emulator or a test device with USB debugging:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The storage tests use a separate test-only preference namespace. They do not establish headphone audio quality or latency.

Optional interface checks need Node.js 20+ and Chromium. They use a simulated native bridge, not a real microphone:

```sh
npm ci
npm run test:ui
```

See [UI test setup](tests/ui/README.md) to set the Chromium path. The app itself does not need Node.js. After building, `python3 scripts/package.py` produces the source archive, APK, guides, and SHA-256 checksums in `dist/`.

See [validation status and device checks](docs/VALIDATION.md) for what has actually been tested and what still needs a physical phone. Opening the bundled HTML in a desktop browser only previews the interface; Android audio features require the APK.

## Project layout

- `app/src/main/java/` — native Android bridge, audio service, and signal processing.
- `app/src/main/assets/` — bundled HTML, CSS, and JavaScript interface.
- `app/src/main/res/` — app resources and icons.
- `tests/audio/` — standalone Java signal-processing checks.
- `app/src/androidTest/` — Android storage and WebView integration tests.
- `tests/ui/` — browser-based interface and bridge contract tests.
- `docs/` — installation, scope, privacy, and validation notes.
- `dist/` — packaged deliverables, when produced by the build.

## Keeping your own release build

For longer-term use, create your own release signing key through Android Studio's **Build → Generate Signed Bundle / APK → APK** wizard. Create and safely back up the keystore, alias, and passwords outside this repository, select the release build, and generate a signed APK. Use the same signing key for subsequent updates. The wizard and APK sideloading are free; a Play Store developer account is optional and is not required here.

An APK signed with a different key cannot replace the existing app in place. Export your local data first, uninstall the old build if necessary, install the newly signed build, then restore the backup. Do not share private signing keys or commit credentials.

AURA saves no microphone recordings. Profile settings and session feedback stay on the device unless you explicitly export them. See [privacy and local data](docs/PRIVACY.md).
