# UI and bridge contract checks

These Playwright tests load the bundled HTML/CSS/JavaScript using a loopback-only static server and an explicitly simulated `window.Aura` bridge. They check interaction, command payloads, state rendering, accessible labels, escaping, and 375/320 px layouts. They do **not** run Android's WebView, microphone permissions, service, DSP, file pickers, or audio routing and do not establish audio quality, latency, safety, or Android-device compatibility.

Use Node.js 20 or newer and a local Chromium installation:

```sh
npm ci
npm run test:ui
```

Linux defaults to `/usr/bin/chromium`. If Chromium is installed elsewhere, set `CHROMIUM_PATH` to its executable. No browser is downloaded automatically. A mobile review screenshot is written to `test-results/aura-listen.png`; failure traces and screenshots are also in that ignored directory.

Fixture persistence uses browser localStorage solely to check that the interface can reload native-supplied state. Real Android persistence is implemented in `AppStore` and requires its own validation. Native confirmations and file pickers are intentionally asserted as emitted commands only.
