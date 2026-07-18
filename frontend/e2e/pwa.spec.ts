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

test('production PWA shell keeps the Kakao login flow usable at 390px', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/health') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' })
      return
    }
    if (path === '/api/v1/auth/session') {
      await route.fulfill({ status: 401, contentType: 'application/json', body: '{"message":"로그인이 필요합니다."}' })
      return
    }
    if (path === '/api/v1/auth/kakao/status') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: '{"success":true,"data":{"enabled":true}}',
      })
      return
    }
    if (path === '/api/v1/auth/kakao/authorize') {
      await route.fulfill({ status: 204 })
      return
    }
    await route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })

  await page.goto('/login?returnTo=%2Fstocks%2FKRX%2F000660%2Ftechnical')
  await page.evaluate(() => navigator.serviceWorker.ready)
  await page.waitForFunction(() => navigator.serviceWorker.controller != null)

  const button = page.getByRole('button', { name: '카카오 로그인' })
  await expect(button).toBeEnabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(await page.evaluate(() => localStorage.getItem('finwatch.auth.session'))).toBeNull()

  const authorizeRequest = page.waitForRequest((request) => new URL(request.url()).pathname === '/api/v1/auth/kakao/authorize')
  await button.click()
  const authorizeUrl = new URL((await authorizeRequest).url())
  expect(authorizeUrl.searchParams.get('returnTo')).toBe('/stocks/KRX/000660/technical')
})
