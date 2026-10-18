const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests/ui',
  outputDir: './test-results',
  fullyParallel: true,
  workers: 2,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    headless: true,
    launchOptions: { executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium' },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    { name: 'mobile-375', use: { viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true } },
    { name: 'narrow-320', use: { viewport: { width: 320, height: 740 }, isMobile: true, hasTouch: true } }
  ],
  webServer: {
    command: 'node tests/ui/server.cjs',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: false,
    timeout: 10000
  }
});
