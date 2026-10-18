(() => {
  "use strict";
  // Android 8 devices can ship an older WebView; keep basic DOM updates compatible.
  if (!Element.prototype.replaceChildren) {
    Element.prototype.replaceChildren = function (...nodes) {
      while (this.firstChild) this.removeChild(this.firstChild);
      nodes.forEach((node) =>
        this.appendChild(
          typeof node === "string" ? document.createTextNode(node) : node,
        ),
      );
    };
  }
  const $ = (id) => document.getElementById(id);
  const native = Boolean(
    window.Aura && typeof window.Aura.state === "function",
  );
  const presets = {
    care: {
      mode: "care",
      clarity: 55,
      reduction: 35,
      awareness: 35,
      volume: 25,
    },
    focus: {
      mode: "focus",
      clarity: 65,
      reduction: 50,
      awareness: 25,
      volume: 25,
    },
    commute: {
      mode: "commute",
      clarity: 35,
      reduction: 20,
      awareness: 70,
      volume: 25,
    },
  };
  const descriptions = {
    care: "A softer starting point for everyday conversations.",
    focus: "More emphasis on speech frequencies, with less surrounding sound.",
    commute:
      "A lighter touch, with more surrounding microphone sound mixed in.",
  };
  const contexts = {
    general: [
      "Everyday conversation",
      "A balanced starting point for everyday speech.",
      "care",
      55,
      35,
      35,
    ],
    consultation: [
      "Medical consultation",
      "Prioritize speech clarity. Place the phone near the clinician and confirm important instructions.",
      "care",
      70,
      45,
      40,
    ],
    pharmacy: [
      "At the pharmacy",
      "A speech-focused starting point for a busy counter. Ask for written medication instructions too.",
      "care",
      65,
      45,
      40,
    ],
    caregiving: [
      "With a caregiver",
      "Keep speech prominent while retaining some surrounding sound.",
      "care",
      60,
      35,
      45,
    ],
    family: [
      "Family & friends",
      "A gentle mix for shared conversations. All voices are processed together.",
      "care",
      55,
      30,
      45,
    ],
    study: [
      "Classroom & work",
      "More speech clarity for a speaker nearby. This does not isolate a distant voice.",
      "focus",
      65,
      50,
      30,
    ],
    commute: [
      "Public transport",
      "Keep more surroundings audible. Sound estimates cannot protect warnings; do not rely on AURA near traffic.",
      "commute",
      35,
      20,
      70,
    ],
  };
  const contextName = (value) => (contexts[value] || contexts.general)[0];
  const modeName = (mode) =>
    ({ care: "Care", focus: "Focus", commute: "Commute" })[mode] || "Custom";
  const defaults = {
    engine: { status: "idle" },
    settings: { ...presets.care },
    profiles: [],
    sessions: [],
    device: {},
    onboardingDone: !native,
    notice: "",
  };
  let state = defaults;
  let activeTab = "listen";
  let settingsUntil = 0;
  let settingsTimer;
  let toastTimer;
  let lastNotice = "";
  let lastNoticeSeq = -1;
  let profilesSignature = "";
  let sessionsSignature = "";
  let returnFocus;
  let modalKey = "";
  let shownOnboarding = false;
  let busy = false;
  let pendingStart = false;
  let initialSessionsSeen = false;
  let knownSessionIds = new Set();
  let reflectAfterStop = false;
  const appliedSuggestions = new Set();
  const sliderIds = [
    "clarity",
    "reduction",
    "awareness",
    "volume",
    "micGainDb",
  ];
  const toggleIds = ["monitor", "deviceSuppression", "soundCategories"];

  function icon(name) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", "icon");
    svg.setAttribute("aria-hidden", "true");
    const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
    use.setAttribute("href", "#i-" + name);
    svg.append(use);
    return svg;
  }
  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function text(id, value) {
    const node = $(id);
    const next = String(value == null ? "" : value);
    if (node.textContent !== next) node.textContent = next;
  }
  function clamp(value) {
    return Math.max(0, Math.min(100, Math.round(Number(value) || 0)));
  }
  function cleanSettings(settings) {
    const result = {
      context: "general",
      profileName: "",
      ...presets.care,
      micGainDb: 0,
      monitor: false,
      deviceSuppression: false,
      microphone: "phone",
      humHz: 0,
      soundCategories: true,
      ...settings,
    };
    sliderIds.forEach(
      (key) =>
        (result[key] = Math.min(
          key === "micGainDb" ? 24 : 100,
          clamp(result[key]),
        )),
    );
    toggleIds.forEach((key) => (result[key] = result[key] === true));
    result.microphone = result.microphone === "headset" ? "headset" : "phone";
    result.humHz = [50, 60].includes(Number(result.humHz))
      ? Number(result.humHz)
      : 0;
    if (!presets[result.mode]) result.mode = "care";
    if (!contexts[result.context]) result.context = "general";
    return result;
  }
  function showToast(message) {
    text("toast", message);
    $("toast").hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      $("toast").hidden = true;
    }, 4200);
  }
  function command(type, payload = {}) {
    if (!native) {
      showToast("Install the native AURA APK to use this feature.");
      return false;
    }
    try {
      window.Aura.command(JSON.stringify({ type, payload }));
      setTimeout(refresh, 100);
      return true;
    } catch (error) {
      showToast("AURA could not complete that action. Please try again.");
      return false;
    }
  }
  function navigate(tab, moveFocus = false) {
    if (!["listen", "profiles", "sessions", "guide", "account"].includes(tab))
      return;
    activeTab = tab;
    document.querySelectorAll(".page").forEach((page) => {
      page.hidden = page.id !== "page-" + tab;
    });
    document.querySelectorAll("[data-tab]").forEach((button) => {
      const selected = button.dataset.tab === tab;
      button.classList.toggle("active", selected);
      if (selected) button.setAttribute("aria-current", "page");
      else button.removeAttribute("aria-current");
    });
    window.scrollTo(0, 0);
    if (moveFocus) {
      const heading = $(tab + "-heading");
      heading.setAttribute("tabindex", "-1");
      heading.focus({ preventScroll: true });
    }
  }
  function formatDuration(seconds, clock = false) {
    seconds = Math.max(0, Math.floor(Number(seconds) || 0));
    if (clock) {
      const hours = Math.floor(seconds / 3600);
      return (
        (hours ? String(hours).padStart(2, "0") + ":" : "") +
        String(Math.floor(seconds / 60) % 60).padStart(2, "0") +
        ":" +
        String(seconds % 60).padStart(2, "0")
      );
    }
    if (seconds < 60) return seconds + " sec";
    if (seconds < 3600)
      return (
        Math.floor(seconds / 60) +
        " min" +
        (seconds % 60 ? " " + (seconds % 60) + " sec" : "")
      );
    return (
      Math.floor(seconds / 3600) +
      " hr " +
      Math.floor((seconds % 3600) / 60) +
      " min"
    );
  }
  function formatDate(value) {
    const date = new Date(value);
    if (isNaN(date.getTime())) return "Date unavailable";
    try {
      return date.toLocaleString(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "numeric",
        minute: "2-digit",
      });
    } catch (_) {
      return date.toLocaleString();
    }
  }
  function sliderVisual(id, value) {
    const slider = $(id);
    const boost = id === "micGainDb";
    slider.style.setProperty(
      "--value",
      (boost ? (value / 24) * 100 : value) + "%",
    );
    slider.setAttribute(
      "aria-valuetext",
      value + (boost ? " decibels" : " percent"),
    );
    text(id + "-value", boost ? "+" + value + " dB" : value + "%");
  }
  function renderSettings() {
    const settings = state.settings;
    sliderIds.forEach((id) => {
      if (document.activeElement !== $(id)) $(id).value = settings[id];
      sliderVisual(id, Number($(id).value));
    });
    document.querySelectorAll("[data-mode]").forEach((button) => {
      const selected = settings.mode === button.dataset.mode;
      button.classList.toggle("selected", selected);
      button.setAttribute("aria-pressed", String(selected));
    });
    text("mode-summary", modeName(settings.mode) + " mode");
    text("mode-description", descriptions[settings.mode]);
    text("profile-mode", modeName(settings.mode) + " mode");
    $("listening-context").value = settings.context || "general";
    text(
      "context-description",
      (contexts[settings.context] || contexts.general)[1],
    );
    toggleIds.forEach((id) => {
      $(id).checked = settings[id];
    });
    $("microphone").value = settings.microphone;
    $("humHz").value = String(settings.humHz);
    $("deviceSuppression").disabled = !native || settings.monitor;
    $("humHz").disabled = !native || settings.monitor;
    text("active-profile", settings.profileName || "Personal settings");
    text(
      "profile-summary",
      "Clarity " +
        settings.clarity +
        " · Reduction " +
        settings.reduction +
        " · Surroundings " +
        settings.awareness +
        " · Volume " +
        settings.volume,
    );
  }
  function renderEngine() {
    const engine = state.engine || {};
    const device = state.device || {};
    const listening = engine.status === "listening";
    const starting = engine.status === "starting";
    const stopping = engine.status === "stopping";
    const failed = engine.status === "error";
    const status = listening
      ? "Listening · on this phone"
      : starting
        ? "Starting microphone…"
        : stopping
          ? "Finishing your session…"
          : failed
            ? "Listening paused"
            : "Ready when you are";
    text("status-text", status);
    $("status-pill").classList.toggle("live", listening);
    $("status-pill").classList.toggle("error", failed);
    text(
      "session-clock",
      formatDuration(listening ? engine.elapsedSeconds : 0, true),
    );
    text(
      "listening-title",
      listening
        ? "Here for the conversation."
        : starting
          ? "Finding your listening space."
          : "A little more connection.",
    );
    text(
      "listening-description",
      listening
        ? "Live microphone sound, shaped by you. Adjust your settings gently as you listen."
        : starting
          ? "Setting up your microphone and headphone audio."
          : failed && engine.message
            ? engine.message
            : "Bring everyday conversations into focus. Connect your headphones to begin.",
    );
    const toggle = $("listen-toggle");
    toggle.classList.toggle("listening", listening || starting);
    toggle.disabled =
      !native ||
      stopping ||
      (pendingStart && !listening && !starting) ||
      (!listening && !starting && !device.headphones);
    toggle
      .querySelector("use")
      .setAttribute(
        "href",
        listening || starting
          ? "#i-stop"
          : !device.headphones
            ? "#i-headphones"
            : "#i-play",
      );
    toggle.querySelector("span").textContent = stopping
      ? "Finishing session…"
      : listening || starting
        ? "Stop listening"
        : native && !device.headphones
          ? "Connect headphones to start"
          : "Start listening";
    const route = native
      ? device.headphones
        ? device.route || engine.route || "Headphones connected"
        : "Headphones needed · wired, USB-C, or Bluetooth"
      : "Audio is available in the Android APK";
    $("route-status").querySelector("span").textContent = route;
    $("route-status").classList.toggle("connected", Boolean(device.headphones));
    $("live-meters").hidden = !listening;
    const db = Number(engine.inputDbfs);
    const hasLevel = engine.inputDbfs != null && Number.isFinite(db);
    const level =
      listening && hasLevel ? Math.max(0, Math.min(1, (db + 65) / 60)) : 0;
    $("input-meter").value = hasLevel ? Math.max(-80, Math.min(0, db)) : -80;
    $("input-meter").setAttribute(
      "aria-valuetext",
      hasLevel ? Math.round(db) + " dBFS" : "No microphone reading",
    );
    const processMs = Number(engine.processingMs);
    text(
      "processing-value",
      Number.isFinite(processMs) && engine.processingMs != null
        ? processMs.toFixed(1) + " ms"
        : "—",
    );
    const outputDb = Number(engine.outputDbfs);
    const readableLevel = (value) =>
      value != null && Number.isFinite(Number(value))
        ? Math.round(Number(value)) + " dBFS"
        : "—";
    text(
      "audio-input-route",
      listening ? engine.inputRoute || "Not reported" : "—",
    );
    text(
      "audio-source",
      listening ? engine.captureSource || "Not reported" : "—",
    );
    text(
      "audio-levels",
      listening
        ? readableLevel(engine.inputDbfs) +
            " / " +
            readableLevel(engine.outputDbfs)
        : "—",
    );
    text(
      "audio-media",
      listening && engine.mediaMaximum
        ? engine.mediaVolume + " / " + engine.mediaMaximum
        : "—",
    );
    text(
      "audio-underruns",
      listening && engine.underruns != null ? engine.underruns : "—",
    );
    text(
      "audio-advice",
      !listening
        ? "Start listening to inspect the microphone and playback path."
        : engine.mediaVolume === 0
          ? "Phone media volume is muted. Raise it gently with your phone’s volume buttons."
          : hasLevel && db < -60
            ? "The microphone signal is very quiet. Speak near the selected microphone or try the other input before raising boost."
            : Number(engine.underruns) > 0
              ? "Playback missed buffers. Turn off sound categories and compare; close other audio apps."
              : engine.outputDbfs != null && outputDb < -50
                ? "Output is quiet. Try direct microphone comparison and increase boost gradually."
                : "For buzzing, compare direct microphone mode first. These digital levels do not measure headphone loudness.",
    );
    const categories = engine.categories || {};
    const fresh =
      listening &&
      state.settings.soundCategories &&
      categories.status === "ready" &&
      Number(categories.ageMs) <= 5000;
    text(
      "sound-status",
      !state.settings.soundCategories
        ? "Sound categories are off."
        : !listening
          ? "Start listening for estimates."
          : categories.status === "ready" && !fresh
            ? "Waiting for a fresh estimate…"
            : categories.message || "Preparing offline sound estimates…",
    );
    ["vocals", "vehicles", "air", "sirens"].forEach((key) => {
      const score =
        fresh && categories.scores ? Number(categories.scores[key]) : NaN;
      const valid = Number.isFinite(score) && score >= 0 && score <= 1;
      $("score-" + key).value = valid ? score : 0;
      $("score-" + key).setAttribute(
        "aria-valuetext",
        valid ? "Model score " + score.toFixed(2) : "No current estimate",
      );
      text("value-" + key, valid ? score.toFixed(2) : "—");
    });
    const weights = [0.3, 0.7, 1, 0.7, 0.3];
    document.querySelectorAll(".wave-bars i").forEach((bar, index) => {
      // Every live bar is derived from the current input RMS reading. There is no simulated animation.
      bar.style.height = listening
        ? 7 + level * weights[index] * 56 + "px"
        : "";
      bar.style.opacity = listening ? String(0.4 + level * 0.6) : "";
    });
    $("device-android").textContent = native
      ? (device.androidVersion
          ? "Android " + device.androidVersion
          : "Android") + (device.sdk ? " · API " + device.sdk : "")
      : "Native app required";
    text(
      "device-route",
      native ? device.route || "No headphone route" : "Unavailable in browser",
    );
    text(
      "device-mic",
      native
        ? device.micPermission
          ? "Allowed"
          : "Not yet allowed"
        : "Unavailable in browser",
    );
    let ns = listening
      ? engine.noiseSuppressor
      : "Checked when listening starts";
    if (typeof ns === "boolean")
      ns = ns ? "Available and enabled" : "Not active · device dependent";
    text("device-ns", ns || "Checked when listening starts");
    const notice = !native
      ? "Interface preview · Install the native Android APK for real microphone processing and local saved settings."
      : (failed ? engine.message : "") || state.notice;
    $("notice").hidden = !notice;
    text("notice", notice || "");
    if (
      state.notice &&
      (state.notice !== lastNotice ||
        (state.noticeSeq != null && state.noticeSeq !== lastNoticeSeq))
    ) {
      if (lastNoticeSeq !== -1) showToast(state.notice);
      lastNotice = state.notice;
      lastNoticeSeq = state.noticeSeq == null ? 0 : state.noticeSeq;
    }
  }
  function renderProfiles() {
    const profiles = Array.isArray(state.profiles) ? state.profiles : [];
    const signature = JSON.stringify(profiles);
    if (signature === profilesSignature) return;
    profilesSignature = signature;
    $("profiles-list").replaceChildren();
    $("profiles-empty").hidden = profiles.length > 0;
    text(
      "profile-count",
      profiles.length + (profiles.length === 1 ? " profile" : " profiles"),
    );
    profiles.forEach((profile) => {
      const settings = cleanSettings(profile.settings);
      const card = el("article", "panel profile-card");
      const top = el("div", "profile-card-top");
      top.append(el("h3", "", profile.name));
      const remove = el("button", "icon-button");
      remove.setAttribute("aria-label", "Delete profile " + profile.name);
      remove.append(icon("trash"));
      remove.addEventListener("click", () =>
        confirm(
          "Delete this profile?",
          "“" +
            profile.name +
            "” will be removed from this phone. Past sessions will stay in your journal.",
          "Delete profile",
          () => command("deleteProfile", { id: profile.id }),
          true,
        ),
      );
      top.append(remove);
      card.append(
        top,
        el(
          "p",
          "profile-mode-label",
          contextName(settings.context) +
            " · " +
            modeName(settings.mode) +
            " mode",
        ),
      );
      const values = el("div", "profile-values");
      [
        ["Clarity", "clarity"],
        ["Reduction", "reduction"],
        ["Surroundings", "awareness"],
        ["Volume", "volume"],
      ].forEach(([label, key]) => {
        const value = el("div", "", label);
        value.append(el("b", "", settings[key] + "%"));
        values.append(value);
      });
      card.append(values);
      const use = el("button", "button secondary", "Use this profile");
      use.append(icon("arrow"));
      use.addEventListener("click", () => {
        if (command("loadProfile", { id: profile.id })) {
          settingsUntil = 0;
          state.settings = settings;
          renderSettings();
          navigate("listen", true);
          showToast("Loaded “" + profile.name + "”.");
        }
      });
      card.append(use);
      $("profiles-list").append(card);
    });
  }
  function renderSessions() {
    const sessions = Array.isArray(state.sessions) ? state.sessions : [];
    const signature =
      JSON.stringify(sessions) + Array.from(appliedSuggestions).join(",");
    if (signature === sessionsSignature) return;
    sessionsSignature = signature;
    $("sessions-list").replaceChildren();
    $("sessions-empty").hidden = sessions.length > 0;
    $("clear-history").hidden = sessions.length === 0;
    text("session-count", sessions.length);
    const total = sessions.reduce(
      (sum, session) => sum + (Number(session.durationSeconds) || 0),
      0,
    );
    text(
      "session-total",
      total < 3600
        ? Math.floor(total / 60) + " min"
        : (total / 3600).toFixed(1) + " hr",
    );
    text("session-rated", sessions.filter((session) => session.rating).length);
    sessions
      .slice()
      .sort(
        (a, b) =>
          new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime(),
      )
      .forEach((session) => {
        const card = el("article", "panel session-card");
        const top = el("div", "session-card-top");
        const heading = el("div");
        heading.append(el("h3", "", modeName(session.mode) + " listening"));
        const time = el("time", "", formatDate(session.startedAt));
        const date = new Date(session.startedAt);
        if (!isNaN(date.getTime())) time.dateTime = date.toISOString();
        heading.append(time);
        top.append(
          heading,
          el(
            "span",
            "session-duration",
            formatDuration(session.durationSeconds),
          ),
        );
        card.append(top);
        if (session.settings)
          card.append(
            el(
              "p",
              "session-context",
              contextName(session.settings.context) +
                (session.settings.profileName
                  ? " · " + session.settings.profileName
                  : "") +
                " · Settings saved at session start",
            ),
          );
        const feedback = el("div", "session-feedback");
        feedback.append(
          el(
            "p",
            "",
            {
              clear: "Felt clear and comfortable",
              okay: "Some clarity, room to adjust",
              difficult: "Listening felt difficult",
            }[session.rating] || "How did that feel?",
          ),
        );
        const reflect = el(
          "button",
          "text-button",
          session.rating ? "Edit reflection" : "Add reflection",
        );
        reflect.addEventListener("click", () => feedbackDialog(session));
        feedback.append(reflect);
        card.append(feedback);
        if (session.note) card.append(el("p", "session-note", session.note));
        if (["clear", "okay", "difficult"].includes(session.rating)) {
          const suggestion = el("div", "suggestion");
          const applied =
            Boolean(session.suggestionApplied) ||
            appliedSuggestions.has(session.id);
          const base = cleanSettings(session.settings || state.settings);
          const nextClarity = clamp(
            base.clarity +
              (session.rating === "difficult"
                ? 10
                : session.rating === "okay"
                  ? 5
                  : 0),
          );
          const nextReduction = clamp(
            base.reduction - (session.rating === "difficult" ? 5 : 0),
          );
          const description =
            contextName(base.context) +
            ": try clarity " +
            nextClarity +
            "%, reduction " +
            nextReduction +
            "%, and surroundings " +
            base.awareness +
            "%. Uses " +
            (session.settings
              ? "this session’s starting settings"
              : "your current settings (older session)") +
            ". Volume will not increase. This is a rule-based suggestion, not AI learning.";
          suggestion.append(
            el(
              "p",
              "",
              applied
                ? "Suggestion applied to your current settings. You can still adjust every slider in Listen."
                : description,
            ),
          );
          if (!applied) {
            const apply = el(
              "button",
              "text-button",
              "Apply suggestion to current settings",
            );
            apply.addEventListener("click", () => {
              clearTimeout(settingsTimer);
              settingsUntil = 0;
              if (command("applySuggestion", { id: session.id })) {
                appliedSuggestions.add(session.id);
                renderSessions();
                showToast("Suggestion applied. Review your sliders in Listen.");
              }
            });
            suggestion.append(apply);
          }
          card.append(suggestion);
        }
        $("sessions-list").append(card);
      });
  }
  function renderCloud() {
    const cloud = state.cloud || {};
    text(
      "cloud-heading",
      cloud.signedIn ? "Your private cloud backup" : "Optional Supabase backup",
    );
    text(
      "cloud-status",
      cloud.busy
        ? "Working with your Supabase project…"
        : cloud.status ||
            "Connect your Supabase Free project to keep an optional backup.",
    );
    text("cloud-project", cloud.project || "No project connected");
    text(
      "cloud-email",
      cloud.signedIn
        ? cloud.email
        : "No audio is uploaded. Sign-in is optional.",
    );
    text(
      "cloud-last-backup",
      cloud.lastBackup
        ? "Last backup from this phone: " + formatDate(cloud.lastBackup)
        : "",
    );
    $("cloud-auth-actions").hidden = Boolean(cloud.signedIn);
    $("cloud-signed-in").hidden = !cloud.signedIn;
    document
      .querySelectorAll("#page-account [data-command]")
      .forEach((button) => {
        const needsProject = !["cloudConfigure", "cloudDisconnect"].includes(
          button.dataset.command,
        );
        button.disabled =
          !native || Boolean(cloud.busy) || (needsProject && !cloud.configured);
      });
  }

  function renderJourney() {
    const listening = ["listening", "starting", "stopping"].includes(
      state.engine.status,
    );
    $("journey-before").classList.toggle("active", !listening);
    $("journey-during").classList.toggle("active", listening);
    if (state.engine.status === "listening") reflectAfterStop = true;
    const sessions = state.sessions || [];
    const fresh = initialSessionsSeen
      ? sessions.find(
          (session) => !knownSessionIds.has(session.id) && !session.rating,
        )
      : null;
    if (fresh && reflectAfterStop && !listening && !modalKey) {
      reflectAfterStop = false;
      feedbackDialog(fresh);
    }
    if (!initialSessionsSeen || !modalKey || !reflectAfterStop) {
      knownSessionIds = new Set(sessions.map((session) => session.id));
      initialSessionsSeen = true;
    }
  }
  function refresh() {
    if (busy) return;
    busy = true;
    try {
      if (native) {
        const raw = window.Aura.state();
        const next = typeof raw === "string" ? JSON.parse(raw) : raw;
        if (next && typeof next === "object") {
          if (state.onboardingDone && next.onboardingDone === false) {
            shownOnboarding = false;
            settingsUntil = 0;
            appliedSuggestions.clear();
            closeDialog();
            navigate("listen");
          }
          const settings =
            Date.now() < settingsUntil
              ? state.settings
              : cleanSettings(next.settings);
          state = { ...defaults, ...next, settings };
        }
      }
      renderEngine();
      renderSettings();
      renderProfiles();
      renderSessions();
      renderCloud();
      renderJourney();
      if (native && !state.onboardingDone && !shownOnboarding && !modalKey) {
        shownOnboarding = true;
        welcomeDialog();
      }
    } catch (_) {
      $("notice").hidden = false;
      text(
        "notice",
        "AURA is reconnecting to its audio service. If this continues, close and reopen the app.",
      );
    } finally {
      busy = false;
    }
  }
  function closeDialog() {
    if ($("modal-overlay").hidden) return;
    $("modal-overlay").hidden = true;
    document.querySelector(".app-shell").removeAttribute("aria-hidden");
    document.querySelector(".bottom-nav").removeAttribute("aria-hidden");
    document.querySelector(".app-shell").removeAttribute("inert");
    document.querySelector(".bottom-nav").removeAttribute("inert");
    document.body.style.overflow = "";
    modalKey = "";
    if (returnFocus && returnFocus.isConnected)
      returnFocus.focus({ preventScroll: true });
  }
  function openDialog(key, title, eyebrow = "AURA") {
    returnFocus = document.activeElement;
    modalKey = key;
    text("modal-title", title);
    text("modal-eyebrow", eyebrow);
    $("modal-content").replaceChildren();
    $("modal-actions").replaceChildren();
    $("modal-overlay").hidden = false;
    document.querySelector(".modal").focus();
    document.querySelector(".app-shell").setAttribute("aria-hidden", "true");
    document.querySelector(".bottom-nav").setAttribute("aria-hidden", "true");
    document.querySelector(".app-shell").setAttribute("inert", "");
    document.querySelector(".bottom-nav").setAttribute("inert", "");
    document.body.style.overflow = "hidden";
    setTimeout(() => document.querySelector(".modal").focus(), 0);
  }
  function action(label, callback, style = "primary") {
    const button = el("button", "button " + style, label);
    button.addEventListener("click", callback);
    $("modal-actions").append(button);
    return button;
  }
  function confirm(title, message, label, callback, danger = false) {
    openDialog("confirm", title, danger ? "YOUR LOCAL DATA" : "YOUR SETTINGS");
    $("modal-content").append(el("p", "", message));
    action("Keep as is", closeDialog, "secondary");
    action(
      label,
      () => {
        closeDialog();
        callback();
      },
      danger ? "secondary danger" : "primary",
    );
  }
  function welcomeDialog() {
    openDialog(
      "welcome",
      "A little more clarity.\nA little more connection.",
      "WELCOME TO AURA",
    );
    $("modal-content").append(
      el(
        "p",
        "",
        "Your personal listening space, entirely on your phone. Let’s start comfortably.",
      ),
    );
    [
      [
        "headphones",
        "Headphones first",
        "Connect wired, USB-C, or Bluetooth headphones. Wired audio usually has less delay.",
      ],
      [
        "sun",
        "Start gently",
        "Lower your phone volume. Begin with Care mode and adjust one control at a time.",
      ],
      [
        "shield",
        "Private, by design",
        "No recordings or cloud audio processing. Accounts are optional, for metadata backup only.",
      ],
    ].forEach(([symbol, title, description]) => {
      const row = el("div", "welcome-item");
      const content = el("div");
      content.append(el("h3", "", title), el("p", "", description));
      row.append(icon(symbol), content);
      $("modal-content").append(row);
    });
    $("modal-content").append(
      el(
        "p",
        "fine-print",
        "An experimental listening aid, not a medical device. It does not isolate speakers or protect warning sounds. Never rely on it for safety.",
      ),
    );
    action("Get comfortable", () => {
      command("onboardingDone");
      closeDialog();
    });
  }
  function profileDialog() {
    if (!native) {
      showToast("Install the native AURA APK to save a profile.");
      return;
    }
    if (state.profiles.length >= 20) {
      showToast(
        "You have 20 profiles. Delete one to make room for a new setup.",
      );
      return;
    }
    openDialog("profile", "Remember this feeling.", "SAVE A PROFILE");
    $("modal-content").append(
      el(
        "p",
        "",
        "Save your current mode, clarity, reduction, surroundings blend, and volume.",
      ),
    );
    const label = el("label", "", "Profile name");
    label.htmlFor = "profile-name";
    const input = el("input");
    input.id = "profile-name";
    input.type = "text";
    input.maxLength = 40;
    input.placeholder = "e.g. Coffee with a friend";
    input.autocomplete = "off";
    const validation = el("p", "validation-message");
    validation.id = "profile-validation";
    validation.setAttribute("role", "status");
    input.setAttribute("aria-describedby", validation.id);
    $("modal-content").append(label, input, validation);
    action("Cancel", closeDialog, "secondary");
    const save = () => {
      const name = input.value.trim();
      if (!name) {
        validation.textContent = "Give your profile a name first.";
        input.focus();
        return;
      }
      clearTimeout(settingsTimer);
      command("settings", state.settings);
      if (command("saveProfile", { name })) {
        closeDialog();
        showToast("Profile saved on this phone.");
      }
    };
    action("Save profile", save);
    input.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        save();
      }
    });
    setTimeout(() => input.focus(), 80);
  }
  function feedbackDialog(session) {
    openDialog("feedback", "How did listening feel?", "A MOMENT TO REFLECT");
    $("modal-content").append(
      el(
        "p",
        "",
        "Your feedback is saved on this phone. Optional cloud backups include it only when you choose Back up now. Suggested adjustments are yours to choose.",
      ),
    );
    const group = el("div", "rating-choices");
    group.setAttribute("role", "radiogroup");
    group.setAttribute("aria-label", "Session clarity");
    [
      ["clear", "Clear", "☺"],
      ["okay", "Okay", "◡"],
      ["difficult", "Difficult", "⌁"],
    ].forEach(([value, label, face]) => {
      const choice = el("label", "rating-choice");
      const radio = el("input");
      radio.type = "radio";
      radio.name = "session-rating";
      radio.value = value;
      radio.checked = session.rating === value;
      choice.classList.toggle("chosen", radio.checked);
      radio.addEventListener("change", () =>
        group
          .querySelectorAll(".rating-choice")
          .forEach((item) =>
            item.classList.toggle(
              "chosen",
              item.querySelector("input").checked,
            ),
          ),
      );
      const faceNode = el("span", "rating-face", face);
      faceNode.setAttribute("aria-hidden", "true");
      choice.append(radio, faceNode, el("span", "", label));
      group.append(choice);
    });
    const label = el("label", "", "A note for next time (optional)");
    label.htmlFor = "session-note";
    const note = el("textarea");
    note.id = "session-note";
    note.maxLength = 300;
    note.rows = 3;
    note.value = session.note || "";
    note.placeholder = "What helped? What would you change?";
    const validation = el("p", "validation-message");
    validation.setAttribute("role", "status");
    $("modal-content").append(group, label, note, validation);
    action("Not now", closeDialog, "secondary");
    action("Save reflection", () => {
      const checked = group.querySelector("input:checked");
      if (!checked) {
        validation.textContent =
          "Choose Clear, Okay, or Difficult to save your reflection.";
        return;
      }
      if (
        command("feedback", {
          id: session.id,
          rating: checked.value,
          note: note.value.trim(),
        })
      ) {
        appliedSuggestions.delete(session.id);
        closeDialog();
        showToast("Reflection saved. Thank you for checking in.");
      }
    });
  }

  document
    .querySelectorAll("[data-tab]")
    .forEach((button) =>
      button.addEventListener("click", () => navigate(button.dataset.tab)),
    );
  document
    .querySelectorAll("[data-navigate]")
    .forEach((button) =>
      button.addEventListener("click", () =>
        navigate(button.dataset.navigate, true),
      ),
    );
  document.querySelector(".brand").addEventListener("click", (event) => {
    event.preventDefault();
    navigate("listen");
  });
  document.querySelectorAll("[data-command]").forEach((button) =>
    button.addEventListener("click", () => {
      const type = button.dataset.command;
      command(type);
    }),
  );
  $("journey-before").addEventListener("click", () =>
    $("listening-context").focus(),
  );
  $("journey-during").addEventListener("click", () =>
    $("listen-toggle").focus(),
  );
  $("listening-context").disabled = !native;
  $("listening-context").addEventListener("change", (event) => {
    const key = event.target.value;
    const selected = contexts[key];
    if (!selected) return;
    clearTimeout(settingsTimer);
    state.settings = {
      ...state.settings,
      mode: selected[2],
      clarity: selected[3],
      reduction: selected[4],
      awareness: selected[5],
      volume: state.settings.volume,
      context: key,
      profileName: "",
    };
    settingsUntil = Date.now() + 1000;
    renderSettings();
    command("settings", state.settings);
  });
  $("listen-toggle").addEventListener("click", () => {
    const listening = ["listening", "starting"].includes(state.engine.status);
    clearTimeout(settingsTimer);
    if (!listening) command("settings", state.settings);
    command(listening ? "stop" : "start");
    if (!listening) {
      pendingStart = true;
      setTimeout(() => {
        pendingStart = false;
        refresh();
      }, 1000);
    } else showToast("Stopping listening…");
    refresh();
  });
  document.querySelectorAll("[data-mode]").forEach((button) =>
    button.addEventListener("click", () => {
      clearTimeout(settingsTimer);
      state.settings = {
        ...state.settings,
        ...presets[button.dataset.mode],
        volume: state.settings.volume,
        context: state.settings.context || "general",
        profileName: "",
      };
      settingsUntil = Date.now() + 1000;
      renderSettings();
      command("settings", state.settings);
    }),
  );
  sliderIds.forEach((id) => {
    const slider = $(id);
    slider.disabled = !native;
    slider.addEventListener("input", () => {
      state.settings[id] = Math.min(
        id === "micGainDb" ? 24 : 100,
        clamp(slider.value),
      );
      state.settings.profileName = "";
      settingsUntil = Date.now() + 1100;
      sliderVisual(id, state.settings[id]);
      clearTimeout(settingsTimer);
      settingsTimer = setTimeout(
        () => command("settings", state.settings),
        120,
      );
    });
    slider.addEventListener("change", () => {
      clearTimeout(settingsTimer);
      settingsUntil = Date.now() + 800;
      command("settings", state.settings);
      renderSettings();
    });
  });
  document.querySelectorAll("[data-mode]").forEach((button) => {
    button.disabled = !native;
  });
  [...toggleIds, "microphone", "humHz"].forEach((id) => {
    $(id).disabled = !native;
    $(id).addEventListener("change", () => {
      clearTimeout(settingsTimer);
      state.settings[id] = toggleIds.includes(id)
        ? $(id).checked
        : id === "humHz"
          ? Number($(id).value)
          : $(id).value;
      state.settings.profileName = "";
      settingsUntil = Date.now() + 800;
      command("settings", state.settings);
      renderSettings();
      renderEngine();
    });
  });
  $("new-profile").addEventListener("click", profileDialog);
  $("save-profile-shortcut").addEventListener("click", profileDialog);
  $("clear-history").addEventListener("click", () => command("clearHistory"));
  $("reset-app").addEventListener("click", () => {
    clearTimeout(settingsTimer);
    settingsUntil = 0;
    command("reset");
  });
  $("modal-close").addEventListener("click", closeDialog);
  $("modal-overlay").addEventListener("click", (event) => {
    if (event.target === $("modal-overlay")) closeDialog();
  });
  document.addEventListener("keydown", (event) => {
    if (!modalKey) return;
    if (event.key === "Escape") {
      event.preventDefault();
      closeDialog();
      return;
    }
    if (event.key !== "Tab") return;
    const nodes = Array.from(
      document
        .querySelector(".modal")
        .querySelectorAll(
          'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), a[href], [tabindex="0"]',
        ),
    );
    const first = nodes[0];
    const last = nodes[nodes.length - 1];
    if (
      event.shiftKey &&
      (document.activeElement === first ||
        document.activeElement === document.querySelector(".modal"))
    ) {
      event.preventDefault();
      last.focus();
    } else if (
      !event.shiftKey &&
      (document.activeElement === last ||
        !document.querySelector(".modal").contains(document.activeElement))
    ) {
      event.preventDefault();
      first.focus();
    }
  });
  window.addEventListener("auraRefresh", refresh);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) refresh();
  });
  // The native Activity calls this before using Android's normal back action.
  window.auraBack = () => {
    if (modalKey) {
      closeDialog();
      return true;
    }
    if (activeTab !== "listen") {
      navigate("listen", true);
      return true;
    }
    return false;
  };
  refresh();
  setInterval(refresh, 700);
})();
