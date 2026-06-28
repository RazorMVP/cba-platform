import { test, expect } from '@playwright/test'

/**
 * Deployed-shell smoke tests. The backend API is unreachable from the Vercel
 * deployment, so these assert the boot/chrome/routing layer (which degrades
 * gracefully), not data-dependent flows.
 */
test.describe('CBA backoffice — deployed shell smoke', () => {
  test('home boots and redirects to the dashboard with the Nubbank shell', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/Nubbank/i)
    await expect(page).toHaveURL(/\/operations\/dashboard/)
    await expect(page.getByRole('complementary')).toBeVisible() // sidebar
  })

  test('sidebar exposes the main navigation groups', async ({ page }) => {
    await page.goto('/')
    const nav = page.getByRole('navigation')
    for (const group of ['Operations', 'Products', 'Cards', 'Accounting', 'Admin']) {
      await expect(nav.getByRole('button', { name: new RegExp(group) })).toBeVisible()
    }
  })

  test('topbar global search is present and editable', async ({ page }) => {
    await page.goto('/')
    const search = page.getByRole('textbox', { name: /search/i })
    await expect(search).toBeVisible()
    await search.fill('jane')
    await expect(search).toHaveValue('jane')
  })

  test('SPA deep-linking renders the shell on a feature route', async ({ page }) => {
    await page.goto('/operations/customers')
    await expect(page.getByRole('complementary')).toBeVisible() // sidebar persists
    await expect(page.getByRole('main')).toBeVisible()          // content area renders
  })

  test('expanding a collapsed sidebar group reveals its routes', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: /^Cards/ }).click()
    await expect(page.locator('a[href*="/cards"]').first()).toBeVisible()
  })
})
