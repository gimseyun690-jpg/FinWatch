import { expect, test } from '@playwright/test'

test('production service worker restores the shell offline without trusting a stored browser token', async ({ context, page }) => {
  await page.route('**/api/v1/**', (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"offline fixture"}' }))

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '투자 정보 대시보드 로그인' })).toBeVisible()
  await page.evaluate(() => navigator.serviceWorker.ready)
  await page.waitForFunction(() => navigator.serviceWorker.controller != null)
  expect(await page.evaluate(() => localStorage.getItem('finwatch.auth.session'))).toBeNull()

  const cachedPaths = await page.evaluate(async () => {
    const keys = await caches.keys()
    const requests = (await Promise.all(keys.map(async (key) => (await caches.open(key)).keys()))).flat()
    return requests.map((request) => new URL(request.url).pathname)
  })
  expect(cachedPaths.filter((path) => path.startsWith('/api/'))).toEqual([])
  expect(cachedPaths.some((path) => path.endsWith('.js'))).toBe(true)

  await context.setOffline(true)
  try {
    await page.goto('/stocks/KRX/000660/technical')
    await expect(page.getByRole('heading', { name: '투자 정보 대시보드 로그인' })).toBeVisible()
    await expect(page).toHaveURL(/\/login\?returnTo=/)
    expect(await page.evaluate(() => localStorage.getItem('finwatch.auth.session'))).toBeNull()
    await page.screenshot({ path: '../docs/evidence/18-pwa-offline.png', fullPage: true })
  } finally {
    await context.setOffline(false)
  }
})
