// An intentionally simulated state/command bridge for UI contract tests only.
// These tests never capture audio, run AudioService, or validate Android routing.
const care = { mode: 'care', clarity: 55, reduction: 35, awareness: 35, volume: 25 };

async function installBridge(page, overrides = {}) {
  await page.addInitScript(({ overrides, care }) => {
    const key = 'aura-ui-test-state';
    const stored = localStorage.getItem(key);
    const fixture = stored ? JSON.parse(stored) : {
      engine: { status: 'idle' },
      settings: { ...care }, profiles: [], sessions: [],
      device: { headphones: true, route: 'USB headphones (fixture)', micPermission: true, androidVersion: '14', sdk: 34 },
      onboardingDone: true, notice: '', noticeSeq: 0, ...overrides
    };
    const persist = () => localStorage.setItem(key, JSON.stringify(fixture));
    window.__bridgeCalls = [];
    window.__fixture = fixture;
    window.__setFixture = patch => {
      Object.assign(fixture, patch);
      persist();
      window.dispatchEvent(new Event('auraRefresh'));
    };
    window.Aura = {
      state: () => JSON.stringify(fixture),
      command: raw => {
        const command = JSON.parse(raw);
        window.__bridgeCalls.push(command);
        const { type, payload } = command;
        switch (type) {
          case 'onboardingDone': fixture.onboardingDone = true; break;
          case 'settings': fixture.settings = { ...payload }; break;
          case 'start':
            if (fixture.device.headphones) fixture.engine = { status: 'listening', elapsedSeconds: 12, inputDbfs: -30, processingMs: 0.4, noiseSuppressor: 'Fixture only' };
            break;
          case 'stop': fixture.engine = { status: 'idle' }; break;
          case 'saveProfile':
            fixture.profiles.push({ id: 'profile-' + (fixture.profiles.length + 1), name: payload.name, settings: { ...fixture.settings, profileName: payload.name } });
            break;
          case 'loadProfile': fixture.settings = { ...fixture.profiles.find(item => item.id === payload.id).settings }; break;
          case 'deleteProfile': fixture.profiles = fixture.profiles.filter(item => item.id !== payload.id); break;
          case 'feedback': Object.assign(fixture.sessions.find(item => item.id === payload.id), { rating: payload.rating, note: payload.note }); break;
          case 'applySuggestion': {
            const session = fixture.sessions.find(item => item.id === payload.id);
            if (session.suggestionApplied) break;
            const volume = fixture.settings.volume;
            if (session.settings) fixture.settings = { ...session.settings, volume: Math.min(volume, session.settings.volume), profileName: '' };
            fixture.settings.clarity = Math.min(100, fixture.settings.clarity + (session.rating === 'difficult' ? 10 : session.rating === 'okay' ? 5 : 0));
            if (session.rating === 'difficult') fixture.settings.reduction = Math.max(0, fixture.settings.reduction - 5);
            session.suggestionApplied = true;
            break;
          }
          // File pickers, permissions and destructive confirmations belong to Android.
          // UI tests assert that the appropriate command is emitted, without simulating success.
        }
        persist();
      }
    };
    persist();
  }, { overrides, care });
}

const session = overrides => ({ id: 'session-1', mode: 'care', startedAt: 1780000000000, durationSeconds: 82, rating: null, note: '', suggestionApplied: false, ...overrides });
module.exports = { installBridge, care, session };
