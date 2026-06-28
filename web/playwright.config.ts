import { defineConfig, devices } from '@playwright/test'

/**
 * E2E config for the Angular backoffice. Runs against a DEPLOYED URL (no local
 * web server): CI passes the Vercel preview via BASE_URL; locally it defaults to
 * the production alias. The deployed app runs with auth-bypass (no Keycloak) and
 * the backend API is not publicly reachable, so these are SHELL/ROUTING smoke
 * tests — they assert the app boots, the chrome renders, navigation works, and
 * the SPA serves deep links, all of which hold under graceful API degradation.
 */
const BASE_URL = process.env.BASE_URL ?? 'https://cba-web-nine.vercel.app'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? 'html' : 'list',
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
})
