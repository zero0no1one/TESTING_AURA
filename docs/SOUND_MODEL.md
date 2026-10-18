# Offline sound model

AURA 1.2 bundles the official quantized YAMNet audio classifier with metadata from [tensorflow/tflite-support](https://github.com/tensorflow/tflite-support/tree/27fa045189ceaddd61ab4897c186c18f05cedd0e) (see the exact commit below). No paid API, runtime download, cloud inference or account is used. It runs through Google LiteRT 1.4.0 on a single background worker, separate from audio playback.

## Reproducible origin

- Repository: `https://github.com/tensorflow/tflite-support.git`
- Commit: `27fa045189ceaddd61ab4897c186c18f05cedd0e`
- Model: `tensorflow_lite_support/cc/test/testdata/task/audio/yamnet_audio_classifier_with_metadata.tflite`
- Model SHA-256: `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`
- Labels: model-associated ZIP member `yamnet_label_list.txt`, copied unchanged to `yamnet-labels.txt`; SHA-256 `8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051`.
- Test sample: same directory, `speech.wav`; SHA-256 `219f55927c62af71fa7dc29581b647ade375286498dd0e342cc07c72cc8edc04`.
- License: repository Apache License 2.0, preserved in `docs/licenses/TFLITE_SUPPORT_LICENSE.txt` and APK `assets/models/LICENSE.txt`. LiteRT/TensorFlow is Apache-2.0 with bundled third-party notices.
- Runtime validates the embedded model hash and input/output dimensions before inference. It never loads remote or user-supplied weights.

The model accepts float PCM `[15600]`: 16 kHz mono, 0.975 seconds, normalized from signed 16-bit capture. Output is `[1,521]`. 48 kHz capture is converted using a 63-tap low-pass FIR before 3:1 downsampling; 16 kHz capture is normalized directly. Audio is classified before AURA's DSP/boost. Android capture preprocessing can still affect it.

Two fixed windows bound pending capture memory; busy inference drops windows instead of queueing audio or delaying playback. Inference failure shows Unavailable while ordinary listening continues. Old results are hidden after five seconds, or immediately when stopped/disabled. CPU contention and additional battery use remain possible, so categories can be disabled independently.

## Display groups

Each group shows the **maximum** score of the following model labels; it is not an estimated source volume, a calibrated probability, or measured accuracy. Indices are zero-based in the bundled 521-label vocabulary.

| Display | Included indices | Meaning / limits |
| --- | --- | --- |
| Vocals | 0–34, 65 | Speech, singing, laughter and other vocalizations, including synthetic speech/singing. Not a chosen speaker or voice identity. |
| Vehicles | 294–338, 342–347 | Transport, horns and engines. Engine labels can also activate for non-vehicle machinery. Dental drill/lawnmower/chainsaw are excluded. |
| Air / wind | 277–279, 406–407 | Wind, rustling leaves, microphone wind noise, fan and air conditioning. Not all airflow or all hiss. |
| Sirens | 317–319, 390–391 | Emergency-vehicle, generic and civil-defense sirens. Not every alarm or danger. |

Groups overlap intentionally: an ambulance siren may score in Vehicles and Sirens. Scores need not total 1. Low scores for all four can mean another sound, silence, or a missed event. None of these scores changes audio gain, suppresses a source, triggers an emergency alert, or guarantees that a siren remains audible.

## Validation and free development

A desktop smoke test executes the exact embedded model against silence and the official speech test WAV. This verifies loading, shapes, vocabulary alignment and basic speech response only. It does **not** measure vehicle/wind/siren accuracy, mixed-sound performance, language coverage, or performance on the user's Android microphone. Acoustic benchmarks and physical-device checks remain outstanding.

With Python 3.12 on a supported desktop:

```sh
python3 -m venv .model-test-env
.model-test-env/bin/pip install -r tests/models/requirements.txt
.model-test-env/bin/python tests/models/test_model.py
```

Windows uses `.model-test-env\Scripts\python.exe`. This Python environment is optional test tooling; Android builds use Gradle and the bundled model directly. No Python dependency is needed on the phone.
