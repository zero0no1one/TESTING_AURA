# AURA for Android

An offline listening app with speech-band enhancement, noise reduction, listening modes, profiles, session feedback, and local backup. No account, subscription, paid API, or internet connection is required by the app.

## Download

- [Download the Android APK](https://github.com/zero0no1one/TESTING_AURA/raw/refs/heads/aura-download/AURA-debug.apk)
- [Download the complete source code](https://github.com/zero0no1one/TESTING_AURA/raw/refs/heads/aura-download/AURA-source.zip)

If this repository is private, sign into GitHub with an account that has access. If a link opens a file preview, use the download button on that page.

## Install on your phone

1. Use Android 8.0 or newer. Open the APK download link in Chrome.
2. Open your phone's Files app, go to Downloads, and tap AURA-debug.apk.
3. If Android asks, allow installation from that source. You can disable that permission after installation.
4. Open AURA, connect headphones, allow microphone access, and start at low volume.

See [the Android installation guide](INSTALL_ANDROID.md) and [build instructions](BUILD_AND_RUN.md).

## Status and limitations

This is a debug-signed build for direct installation and testing. Build, signing, DSP, and browser UI checks passed. Native Android runtime and physical-phone audio behavior remain unverified; see [validation details](VALIDATION.md).

Audio processing runs offline using conventional DSP. It does not isolate a selected speaker or classify alarms. Bluetooth can introduce substantial delay. This app is not a medical device or a replacement for situational awareness.

The source archive excludes signing keys. Builds signed with a different key require uninstalling the old app first; export your local data before uninstalling. Maintain your own signing key for future updates.

[SHA-256 checksums](SHA256SUMS) are provided for all download artifacts.
