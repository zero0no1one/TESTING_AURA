const { test, expect } = require('@playwright/test');
const { installBridge, care, session } = require('./bridge.cjs');

const navigate = (page, name) => page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name, exact: true }).click();
const commands = page => page.evaluate(() => window.__bridgeCalls);

test('onboarding explains limitations and completes through the bridge', async ({ page }) => {
  await installBridge(page, { onboardingDone: false });
  await page.goto('/');
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText('Headphones first');
  await expect(dialog).toContainText('It does not isolate speakers or detect alarms.');
  await expect(page.locator('.app-shell')).toHaveAttribute('inert', '');
  await dialog.getByRole('button', { name: 'Get comfortable' }).click();
  await expect(dialog).toBeHidden();
  expect(await commands(page)).toContainEqual({ type: 'onboardingDone', payload: {} });
  await page.reload();
  await expect(dialog).toBeHidden();
});

test('headphone absence disables start until a new native state arrives', async ({ page }) => {
  await installBridge(page, { device: { headphones: false, micPermission: true } });
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Connect headphones to start' })).toBeDisabled();
  expect(await commands(page)).toEqual([]);
  await page.evaluate(() => window.__setFixture({ device: { headphones: true, route: 'USB headphones (fixture)' } }));
  await expect(page.getByRole('button', { name: 'Start listening', exact: true })).toBeEnabled();
});

test('start and stop commands render the returned engine state', async ({ page }) => {
  await installBridge(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Start listening', exact: true }).click();
  await expect(page.locator('#status-text')).toHaveText('Listening · on this phone');
  await expect(page.locator('#session-clock')).toHaveText('00:12');
  await expect(page.locator('#processing-value')).toHaveText('0.4 ms');
  await expect(page.getByRole('meter', { name: 'Microphone signal level' })).toHaveAttribute('aria-valuetext', '-30 dBFS');
  const stop = page.getByRole('button', { name: 'Stop listening' });
  await expect(stop).toBeEnabled();
  await stop.click();
  await expect(page.locator('#status-text')).toHaveText('Ready when you are');
  expect((await commands(page)).map(command => command.type)).toEqual(['settings', 'start', 'stop']);
});

test('controls persist via bridge and mode changes preserve listening volume', async ({ page }) => {
  await installBridge(page);
  await page.goto('/');
  const volume = page.getByRole('slider', { name: 'Listening volume' });
  await volume.fill('19');
  await expect.poll(async () => (await commands(page)).some(command => command.type === 'settings' && command.payload.volume === 19)).toBe(true);
  await page.getByRole('button', { name: 'Focus More speech clarity' }).click();
  await expect(page.getByRole('slider', { name: 'Speech clarity' })).toHaveValue('65');
  await expect(volume).toHaveValue('19');
  await page.reload();
  await expect(volume).toHaveValue('19');
  await expect(page.locator('[data-mode="focus"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('slider', { name: 'Speech clarity' })).toHaveAttribute('aria-valuetext', '65 percent');
});

test('profile validation, save, load and confirmed deletion use bridge IDs', async ({ page }) => {
  await installBridge(page);
  await page.goto('/');
  await navigate(page, 'Profiles');
  await page.getByRole('button', { name: 'Save as a profile' }).click();
  let dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Save profile', exact: true }).click();
  await expect(dialog).toContainText('Give your profile a name first.');
  await page.getByRole('textbox', { name: 'Profile name' }).fill('Quiet conversation');
  await dialog.getByRole('button', { name: 'Save profile', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Quiet conversation', exact: true })).toBeVisible();
  await navigate(page, 'Listen');
  await page.getByRole('button', { name: 'Focus More speech clarity' }).click();
  await navigate(page, 'Profiles');
  await page.getByRole('button', { name: 'Use this profile' }).click();
  await expect(page.locator('#mode-summary')).toHaveText('Care mode');
  await expect(page.getByRole('slider', { name: 'Speech clarity' })).toHaveValue('55');
  await navigate(page, 'Profiles');
  await page.getByRole('button', { name: 'Delete profile Quiet conversation' }).click();
  await dialog.getByRole('button', { name: 'Keep as is' }).click();
  await expect(page.getByRole('heading', { name: 'Quiet conversation', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Delete profile Quiet conversation' }).click();
  await dialog.getByRole('button', { name: 'Delete profile', exact: true }).click();
  await expect(page.locator('#profiles-empty')).toBeVisible();
  expect(await commands(page)).toEqual(expect.arrayContaining([
    { type: 'saveProfile', payload: { name: 'Quiet conversation' } },
    { type: 'loadProfile', payload: { id: 'profile-1' } },
    { type: 'deleteProfile', payload: { id: 'profile-1' } }
  ]));
});

test('session reflection requires a rating and adjustment remains opt-in', async ({ page }) => {
  await installBridge(page, { sessions: [session()] });
  await page.goto('/');
  await navigate(page, 'Sessions');
  await expect(page.locator('#session-count')).toHaveText('1');
  await expect(page.locator('#session-total')).toHaveText('1 min');
  await page.getByRole('button', { name: 'Add reflection' }).click();
  await page.getByRole('button', { name: 'Save reflection' }).click();
  await expect(page.getByRole('dialog')).toContainText('Choose Clear, Okay, or Difficult');
  await page.getByRole('radio', { name: 'Difficult', exact: true }).check();
  await page.getByRole('textbox', { name: 'A note for next time (optional)' }).fill('Try sitting closer next time.');
  await page.getByRole('button', { name: 'Save reflection' }).click();
  await expect(page.locator('#session-rated')).toHaveText('1');
  expect((await commands(page)).some(command => command.type === 'applySuggestion')).toBe(false);
  await page.getByRole('button', { name: 'Apply suggestion to current settings' }).click();
  await expect(page.getByRole('button', { name: 'Apply suggestion to current settings' })).toHaveCount(0);
  await navigate(page, 'Listen');
  await expect(page.getByRole('slider', { name: 'Speech clarity' })).toHaveValue('65');
  await expect(page.getByRole('slider', { name: 'Noise reduction' })).toHaveValue('30');
  await expect(page.getByRole('slider', { name: 'Listening volume' })).toHaveValue('25');
  await page.reload();
  await navigate(page, 'Sessions');
  await expect(page.getByText('Suggestion applied to your current settings.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Apply suggestion to current settings' })).toHaveCount(0);
});

test('backup, permission and destructive actions emit commands for native handling', async ({ page }) => {
  await installBridge(page, { sessions: [session()] });
  await page.goto('/');
  await navigate(page, 'Profiles');
  await page.getByRole('button', { name: 'Export backup' }).click();
  await page.getByRole('button', { name: 'Import backup' }).click();
  await navigate(page, 'Sessions');
  await page.getByRole('button', { name: 'Clear history' }).click();
  await navigate(page, 'Guide');
  await page.getByRole('button', { name: 'Check permissions' }).click();
  await page.getByRole('button', { name: 'Open app settings' }).click();
  await page.getByRole('button', { name: 'Reset AURA on this phone' }).click();
  expect((await commands(page)).map(command => command.type)).toEqual(['export', 'import', 'clearHistory', 'permissions', 'appSettings', 'reset']);
});

test('Guide navigation, accessible control labels and Android back contract work', async ({ page }) => {
  await installBridge(page);
  await page.goto('/');
  for (const label of ['Speech clarity', 'Noise reduction', 'Surroundings blend', 'Listening volume']) {
    await expect(page.getByRole('slider', { name: label })).toHaveCount(1);
  }
  await navigate(page, 'Guide');
  await expect(page.locator('[data-tab="guide"]')).toHaveAttribute('aria-current', 'page');
  await expect(page.getByRole('heading', { name: 'Make yourself comfortable.' })).toBeVisible();
  await page.getByText('What does AURA actually do?', { exact: true }).click();
  await expect(page.getByText('AURA does not use cloud AI', { exact: false })).toBeVisible();
  expect(await page.evaluate(() => window.auraBack())).toBe(true);
  await expect(page.locator('#page-listen')).toBeVisible();
  expect(await page.evaluate(() => window.auraBack())).toBe(false);
  await page.getByRole('button', { name: 'Save profile', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(await page.evaluate(() => window.auraBack())).toBe(true);
  await expect(page.getByRole('dialog')).toBeHidden();
});

test('profile names and session notes are rendered as text, never HTML', async ({ page }) => {
  const payload = '<img src=x onerror=alert(1)>';
  let dialogs = 0;
  page.on('dialog', dialog => { dialogs++; dialog.dismiss(); });
  await installBridge(page, { profiles: [{ id: 'p1', name: payload, settings: care }], sessions: [session({ note: payload, rating: 'clear' })] });
  await page.goto('/');
  await navigate(page, 'Profiles');
  await expect(page.locator('#profiles-list h3')).toHaveText(payload);
  await expect(page.locator('#profiles-list img')).toHaveCount(0);
  await navigate(page, 'Sessions');
  await expect(page.locator('.session-note')).toHaveText(payload);
  await expect(page.locator('#sessions-list img')).toHaveCount(0);
  expect(dialogs).toBe(0);
});

test('each mobile page fits the viewport even with long user text', async ({ page }) => {
  await installBridge(page, { profiles: [{ id: 'p1', name: 'A'.repeat(40), settings: care }], sessions: [session({ note: 'B'.repeat(300), rating: 'okay' })] });
  await page.goto('/');
  for (const tab of ['Listen', 'Profiles', 'Sessions', 'Guide']) {
    await navigate(page, tab);
    const sizes = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, viewport: document.documentElement.clientWidth }));
    expect(sizes.scroll, tab + ' horizontal overflow').toBeLessThanOrEqual(sizes.viewport);
  }
});

test('without a native bridge the interface makes its preview limitation clear', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('#notice')).toContainText('Install the native Android APK');
  await expect(page.getByRole('button', { name: 'Start listening', exact: true })).toBeDisabled();
  await expect(page.getByRole('slider', { name: 'Speech clarity' })).toBeDisabled();
  await navigate(page, 'Guide');
  await expect(page.locator('#device-android')).toHaveText('Native app required');
});

test('capture the mobile listening interface for visual review', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-375', 'One review screenshot is sufficient.');
  await installBridge(page);
  await page.goto('/');
  await expect(page.locator('#status-text')).toHaveText('Ready when you are');
  await page.screenshot({ path: 'test-results/aura-listen.png', fullPage: true });
});

test('context prepares a useful preset while preserving volume', async ({ page }) => {
  await installBridge(page, { settings: { ...care, volume: 13 } });
  await page.goto('/');
  await page.getByRole('combobox', { name: 'Listening context', exact: true }).selectOption('consultation');
  await expect(page.getByRole('slider', { name: 'Speech clarity' })).toHaveValue('70');
  await expect(page.getByRole('slider', { name: 'Listening volume' })).toHaveValue('13');
  expect(await commands(page)).toContainEqual({ type: 'settings', payload: { mode: 'care', clarity: 70, reduction: 45, awareness: 40, volume: 13, context: 'consultation', profileName: '' } });
  await page.reload();
  await expect(page.getByRole('combobox', { name: 'Listening context', exact: true })).toHaveValue('consultation');
});

test('a completed live session offers an optional immediate reflection', async ({ page }) => {
  await installBridge(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Start listening', exact: true }).click();
  await expect(page.locator('#status-text')).toContainText('Listening');
  await page.evaluate(() => window.__setFixture({ engine: { status: 'idle' }, sessions: [{ id: 'new-session', mode: 'care', startedAt: Date.now(), durationSeconds: 20, rating: null, note: '', settings: { mode: 'care', clarity: 70, reduction: 45, awareness: 40, volume: 25, context: 'consultation' } }] }));
  await expect(page.getByRole('dialog')).toContainText('How did listening feel?');
  await page.getByRole('button', { name: 'Not now', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await page.reload();
  await expect(page.getByRole('dialog')).toBeHidden();
  await navigate(page, 'Sessions');
  await expect(page.getByText('Medical consultation · Settings saved at session start')).toBeVisible();
});

test('cloud actions use native commands and passwords never enter the web page', async ({ page }) => {
  await installBridge(page, { cloud: { configured: true, signedIn: false, project: 'https://project.supabase.co', status: 'Ready' } });
  await page.goto('/');
  await navigate(page, 'Account');
  for (const name of ['Sign in', 'Create account', 'Forgot password?']) await page.getByRole('button', { name, exact: true }).click();
  expect((await commands(page)).map(item => item.type)).toEqual(['cloudSignIn', 'cloudSignUp', 'cloudResetPassword']);
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
  await page.evaluate(() => window.__setFixture({ cloud: { configured: true, signedIn: true, email: '<img src=x onerror=alert(1)>', project: 'https://project.supabase.co', status: 'Signed in' } }));
  await expect(page.locator('#cloud-email')).toHaveText('<img src=x onerror=alert(1)>');
  await expect(page.locator('#cloud-email img')).toHaveCount(0);
  for (const name of ['Back up now', 'Restore cloud backup', 'Delete cloud backup', 'Delete cloud account', 'Sign out']) await page.getByRole('button', { name, exact: true }).click();
  expect((await commands(page)).slice(3).map(item => item.type)).toEqual(['cloudBackup', 'cloudRestore', 'cloudDeleteBackup', 'cloudDeleteAccount', 'cloudSignOut']);
  await page.evaluate(() => window.__setFixture({ cloud: { configured: true, signedIn: true, busy: true } }));
  await expect(page.getByRole('button', { name: 'Back up now', exact: true })).toBeDisabled();
  const sizes = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, viewport: document.documentElement.clientWidth }));
  expect(sizes.scroll).toBeLessThanOrEqual(sizes.viewport);
});

test('unconfigured cloud cannot sign in and remains optional', async ({ page }) => {
  await installBridge(page, { cloud: { configured: false, signedIn: false } });
  await page.goto('/');
  await navigate(page, 'Account');
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeDisabled();
  await page.getByText('Project setup & connection', { exact: true }).click();
  await page.getByRole('button', { name: 'Configure project', exact: true }).click();
  expect(await commands(page)).toContainEqual({ type: 'cloudConfigure', payload: {} });
  await navigate(page, 'Listen');
  await expect(page.getByRole('button', { name: 'Start listening', exact: true })).toBeEnabled();
});

test('feedback suggestions restore session context without raising volume', async ({ page }) => {
  const initial = { ...care, context: 'consultation', clarity: 70, reduction: 45, volume: 35 };
  await installBridge(page, { settings: { ...care, context: 'commute', volume: 10 }, sessions: [session({ rating: 'difficult', settings: initial })] });
  await page.goto('/');
  await navigate(page, 'Sessions');
  await expect(page.locator('.suggestion')).toContainText('Medical consultation: try clarity 80%, reduction 40%');
  await page.getByRole('button', { name: 'Apply suggestion to current settings' }).click();
  await navigate(page, 'Listen');
  await expect(page.getByRole('combobox', { name: 'Listening context', exact: true })).toHaveValue('consultation');
  await expect(page.getByRole('slider', { name: 'Speech clarity' })).toHaveValue('80');
  await expect(page.getByRole('slider', { name: 'Listening volume' })).toHaveValue('10');
});

test('research features are clearly distinguished from available controls', async ({ page }) => {
  await installBridge(page);
  await page.goto('/');
  await navigate(page, 'Guide');
  await expect(page.locator('.capability-panel')).toContainText('Voice Focus');
  await expect(page.locator('.capability-panel')).toContainText('Not yet available');
  await expect(page.locator('.capability-panel')).toContainText('cannot recognize or guarantee preservation of alarms');
});
