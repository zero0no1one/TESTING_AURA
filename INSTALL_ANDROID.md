# Install and use AURA on Android

## 1. Check your phone

Open **Settings → About phone → Android version**. Some manufacturers put the version inside **Software information**. AURA requires Android 8.0 or later. The APK is a standard Android app; an iPhone cannot install it.

You do not need to know your phone model before trying it. You will need headphones and permission to use the microphone. Wired or USB-C headphones usually provide the most predictable listening path. Bluetooth headphones may work, but latency, microphone selection, and audio quality depend on the phone and headset. Android can report some Bluetooth speakers using the same route category as headphones, so connect actual headphones rather than a speaker. Bluetooth headsets that expose only a call-audio/SCO route and USB devices not reported as USB headsets are not supported in this version.

## 2. Get the APK

Use `dist/AURA-debug.apk` from the delivered files. If it is not included, build the project using the [README](../README.md); the resulting file is `app/build/outputs/apk/debug/app-debug.apk`.

Transfer the APK with either of these free methods:

- **USB cable:** connect the phone to your computer, choose **File transfer** on the phone, and copy the APK into its **Download** folder. On macOS, use a compatible Android file-transfer tool or the `adb install` method below.
- **Your own storage:** upload the APK to a storage service you already use, then download it on your phone. That service may require an account and an internet connection; AURA itself does not. Keep the file private if you prefer.

Developers can use USB debugging and run `adb install -r app/build/outputs/apk/debug/app-debug.apk` instead.

## 3. Install

1. On the phone, open **Files**, **My Files**, or **Downloads** and tap the APK.
2. If Android asks, open **Settings** and allow **Install unknown apps** for the specific app opening the APK, such as Files or your browser. This is the normal permission for installing your own APK outside a store.
3. Return to the APK and tap **Install**, then **Open**.
4. After installation, turn that install permission off again if you no longer need it.

The provided debug build is intended for personal testing. Android may display additional prompts for an app installed outside a store. Only install a file you built yourself or obtained from the intended project source.

If Android reports **App not installed**, check that the phone is on Android 8 or later, has free space, and does not already have an AURA build signed with a different key. Export existing data before uninstalling an old build; uninstalling removes the app's local data.

## 4. First listening session

1. Connect your headphones before starting a session.
2. Lower the phone's **media volume**, then open AURA.
3. Choose **Care**, **Focus**, or **Commute**. You can adjust the controls after the session starts.
4. Start listening and allow **Microphone** access when Android prompts. If notification permission is offered, allow it to make the service's Stop control easy to find.
5. Start with AURA's output at **25%**, then increase gradually only as needed. Keep the phone microphone away from the headphone drivers.
6. Have someone speak at a comfortable distance. Compare modes and adjust speech clarity, noise reduction, and awareness to preference.
7. Stop from the app or its ongoing notification when finished. You can save feedback about the session and a profile for later.

AURA prefers the built-in phone microphone and sends the processed sound to headphones. It stops on audio-focus loss, a detected route change, or headphone disconnection; it does not automatically resume. Check the connection and tap Start again. Android routing changes can occur before the app is notified, so the route checks are not a guarantee against every brief routing transition.

AURA listens through its microphone input and sends the processed sound to headphones. It cannot access or enhance the audio from every other app, and it does not act as a system-wide equalizer.

A digital output cap limits the app's sample amplitude. It cannot measure the sound pressure produced by your particular headphones. AURA is not a fitted hearing aid and does not guarantee safe acoustic volume or reliable detection of hazards.

## 5. Profiles, feedback, and backups

From **Listen**, choose **Save profile**, or open **Profiles → Save as a profile**, to keep a combination of settings you like. You can save up to 20 profiles, with names of up to 40 characters. Profiles and session feedback remain on the phone. Session history contains metadata, ratings, and optional notes, not audio recordings; it is limited to the most recent 200 sessions.

In **Sessions**, rate a completed session **Clear**, **Okay**, or **Difficult** and optionally add a note of up to 300 characters. You can choose to apply a simple settings suggestion; it does not train an AI model or increase output volume automatically.

Use **Profiles → Export backup** to save JSON using Android's document picker. Choose a local folder for an entirely offline backup. Choosing a cloud storage provider uses that provider's separate account and network service. **Profiles → Import backup** lets you select a compatible AURA version 1 JSON backup of up to 1 MB. Import replaces current settings, profiles, and session history; it does not merge them. Read the confirmation and export current data first if you want to keep it.

Use **Sessions → Clear history** to remove session details only, or **Guide → Reset AURA on this phone** to clear settings, profiles, and history. Uninstalling also removes internal app data. Exported backup files are separate and must be deleted from their saved location if you no longer want them.

## Common issues

| Symptom | What to try |
| --- | --- |
| Microphone permission was denied | Open **Settings → Apps → AURA → Permissions → Microphone**, allow microphone use, return to AURA, and start again. |
| No sound | Confirm headphones are connected, raise media volume slightly, and check that a session is running. Stop and restart after changing the audio route. |
| Feedback, ringing, or discomfort | Stop playback, reduce volume, and move the phone microphone away from headphones. Use a wired or USB-C route for the next low-volume test. |
| Bluetooth sound is delayed or poor | Try wired or USB-C headphones. Bluetooth cannot be assumed to provide low-latency live monitoring. |
| Listening stops after headphones disconnect | Reconnect headphones, check the route, and start a new session deliberately. |
| Listening is interrupted by a call or another audio app | Finish the call or stop the other app, return to AURA, check the route, and restart. |
| Listening stops when the screen locks | Reopen AURA and check phone battery restrictions. Manufacturer power management varies; verify screen-off behavior on your device before relying on it. |
| Backup is rejected | Use an unedited backup exported by a compatible AURA version. Do not import unrelated JSON files. |

The on-screen **Processing** time measures computation for an audio block, not the complete microphone-to-ear delay.

Commute mode and the surroundings/awareness mix are processing preferences, not an alarm-recognition system. Do not use live listening where delayed or changed sound would make an activity unsafe, including driving.

## Upgrade to AURA 1.1 and optional Supabase

Export a local JSON backup before upgrading. The supplied 1.1 APK retains the application ID and debug signer from the previously delivered APK, so it can update that build in place without uninstalling. The APK version is now 1.1.0 (version code 2). If your existing APK came from another signing key, follow the signing-mismatch instructions above.

Listen now includes a Before–During–After journey and listening contexts. Profiles store context with your preferences; new sessions remember starting settings for later feedback. Previous version 1 backups remain importable.

Account offers optional Supabase sign-in and manual metadata backup. The public project connection is preconfigured, but the owner must finish [Supabase database and email setup](SUPABASE_SETUP.md). Listening stays local and does not require sign-in. Microphone audio is never uploaded. Cloud backup is not automatic sync; read the replacement confirmation before uploading or restoring.
