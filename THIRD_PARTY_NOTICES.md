# Third-party tools

AURA 1.2 bundles Google LiteRT 1.4.0 (`com.google.ai.edge.litert:litert` and its API dependency, Apache-2.0) and an official TensorFlow YAMNet audio-classifier model. See [pinned provenance and licenses](docs/SOUND_MODEL.md). Optional cloud account and metadata-backup features use the Supabase hosted Auth and Postgres REST services; offline audio does not depend on them. It uses the Android platform and the phone's installed Android System WebView. The application source is under the MIT license in `LICENSE`.

The source bundle includes the Gradle wrapper scripts and wrapper JAR (Gradle 8.9), licensed under Apache License 2.0: https://github.com/gradle/gradle/blob/v8.9.0/LICENSE. The wrapper downloads the checksum-pinned Gradle distribution for builds. Android build tools and SDKs are installed separately under their respective licenses; the SDK is not redistributed in the source archive.

Optional test tools, downloaded only for development:

- Playwright Test 1.56.1: Apache-2.0, https://github.com/microsoft/playwright
- AndroidX Test Runner 1.6.2 and its test dependencies: Apache-2.0, https://android.googlesource.com/platform/frameworks/testing/
- Robolectric 4.14.1 (JVM Android simulation): MIT, https://github.com/robolectric/robolectric
- PostgreSQL 17 (isolated database tests): PostgreSQL License, https://www.postgresql.org/about/licence/
- JUnit 4.13.2: EPL-1.0, https://github.com/junit-team/junit4

These tools are free to use. The YAMNet weights are included. No proprietary audio SDK, paid API, subscription, or trial is included. The uploaded project PDFs and the original reference image are not redistributed as app assets.

LiteRT/TensorFlow includes third-party native components with their own notices; the unmodified AAR `LICENSE` (including its third-party notices) is copied to `docs/licenses/LITERT_LICENSE.txt` and APK `assets/models/LITERT_LICENSE.txt`. The TensorFlow support Apache-2.0 license is included in `docs/licenses/TFLITE_SUPPORT_LICENSE.txt` and in the APK model assets. Test `speech.wav` comes from the same pinned official repository.
