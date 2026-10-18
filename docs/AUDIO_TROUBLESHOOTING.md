# Quiet vocals or vibrating sound

AURA 1.2 fixes excessive quiet-signal attenuation being impossible to compensate for, adds an explicit capture route, disables optional Android noise suppression by default, and increases playback buffering. The microphone boost starts at 0 dB so an update does not unexpectedly raise headphone loudness. The actual cause of the reported buzzing has not been reproduced on the user's phone.

1. Connect wired/USB-C headphones; start with low Android **media volume**, app **Listening volume 25%**, and **Phone microphone**.
2. Turn **Direct microphone comparison** on. Speak near the phone. This bypasses app filters/notch and disables AURA's optional Android noise suppressor; boost, compressor and limiter remain. Android may still apply device-specific capture processing.
3. Open **Live audio diagnostics**. A moving input level when you speak confirms captured signal, not speech quality. Near −96 dBFS is near silence; if speech stays below −60 dBFS, move closer or check the input. Select **Wired / USB headset microphone** if you intend to use its microphone; changing input stops listening, so tap Start again. Earphones without a mic require Phone microphone.
4. If speech is simply quiet, increase **Microphone boost** gradually: +6 dB, then +12 dB if needed. Boost also raises noise. Do not continue increasing volume if sound is distorted or uncomfortable. Check that phone media volume is not zero.
5. Compare direct mode off/on. Buzz only with direct mode off points toward filtering/settings. Keep **Use Android noise suppression** off first; stronger suppression can damage speech. Buzz in both modes can involve input hardware, the adapter, output, capture processing or dropouts; it is not proof of a specific cause.
6. If **Playback buffer underruns** increases, turn **Show sound categories** off and compare; close other audio apps. Inference is separate from playback but competes for CPU/power. Reconnect the cable/USB-C adapter and try another headset if available.
7. For a steady electrical hum only, try **50 Hz** or **60 Hz notch** with direct comparison off. The filter attenuates a narrow band, not voices/vehicles as whole categories; it will not fix all vibrating sounds.
8. If unresolved, share the selected input route, capture source, input/output dBFS while speaking, phone media volume and underrun count. No conversation recording is needed.

## Why inverted 400 Hz is not category cancellation

Two equal-amplitude 400 Hz sine waves exactly 180° apart sum to zero **where and when those waveforms coincide**: `A sin(2π400t) + (−A sin(2π400t)) = 0`. A time shift, unequal level, reflection or different position leaves a residual. Inverting the entire microphone signal digitally flips both voice and noise. Adding that inverse to the same signal cancels both; playing only the inverse does not remove the original sound at your ears.

Real speech, traffic, wind and sirens occupy overlapping, changing frequency ranges. Recognizing a sound requires more than one frequency. Headphone ANC uses dedicated nearby microphones, very low latency, acoustic-path calibration and adaptive filters. A delayed phone-microphone signal cannot promise equivalent acoustic cancellation.

AURA uses conventional digital filters and a separate offline classifier. Its category display does not produce isolated waveforms or individual category volume controls. Source separation needs a suitable validated model and real-device evaluation; it is a separate development task.
