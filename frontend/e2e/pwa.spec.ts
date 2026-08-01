import { expect, test } from '@playwright/test'

async function dispatchInstallPrompt(page: import('@playwright/test').Page, outcome: 'accepted' | 'dismissed' = 'accepted') {
  await page.evaluate((choice) => {
    const state = { promptCalls: 0, defaultPrevented: false }
    const event = new Event('beforeinstallprompt', { cancelable: true })
    Object.defineProperties(event, {
      prompt: {
        value: async () => {
          state.promptCalls += 1
        },
      },
      userChoice: {
        value: Promise.resolve({ outcome: choice, platform: 'web' }),
      },
    })
    ;(window as typeof window & { __pwaPromptMock?: typeof state }).__pwaPromptMock = state
    window.dispatchEvent(event)
    state.defaultPrevented = event.defaultPrevented
  }, outcome)
}

test('production service worker restores the shell offline without trusting a stored browser token', async ({ context, page }) => {
  await page.route('**/api/v1/**', (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"offline fixture"}' }))

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'FinWatch 시작하기' })).toBeVisible()
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
    await expect(page.getByRole('heading', { name: 'FinWatch 시작하기' })).toBeVisible()
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

test('mobile Android install notice opens the deferred browser prompt once', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/v1/**', (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{}' }))
  await page.goto('/login')

  await dispatchInstallPrompt(page)

  const notice = page.getByRole('region', { name: '홈 화면에 FinWatch 설치' })
  await expect(notice).toBeVisible()
  await expect(notice).toContainText('앱처럼 빠르게 열고 전체 화면으로 편하게 이용할 수 있어요.')
  await notice.getByRole('button', { name: '홈 화면에 설치' }).click()

  await expect(notice).toHaveCount(0)
  expect(await page.evaluate(() => (window as typeof window & { __pwaPromptMock?: { promptCalls: number; defaultPrevented: boolean } }).__pwaPromptMock)).toEqual({
    promptCalls: 1,
    defaultPrevented: true,
  })
})

test('mobile iOS shows Safari home-screen instructions and remembers dismissal', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'userAgent', {
      configurable: true,
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1',
    })
    Object.defineProperty(navigator, 'standalone', { configurable: true, value: false })
  })
  await page.route('**/api/v1/**', (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{}' }))
  await page.goto('/login')

  const notice = page.getByRole('region', { name: '홈 화면에 FinWatch 설치' })
  await expect(notice).toBeVisible()
  await expect(notice).toContainText('공유')
  await expect(notice).toContainText('홈 화면에 추가')
  await notice.getByRole('button', { name: '나중에' }).click()
  await expect(notice).toHaveCount(0)

  await page.reload()
  await page.waitForTimeout(800)
  await expect(page.getByRole('region', { name: '홈 화면에 FinWatch 설치' })).toHaveCount(0)
})

test('installed standalone mode never shows the install notice', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => {
    const nativeMatchMedia = window.matchMedia.bind(window)
    window.matchMedia = ((query: string) => {
      if (query !== '(display-mode: standalone)') return nativeMatchMedia(query)
      return {
        matches: true,
        media: query,
        onchange: null,
        addListener() {},
        removeListener() {},
        addEventListener() {},
        removeEventListener() {},
        dispatchEvent: () => true,
      } as MediaQueryList
    }) as typeof window.matchMedia
  })
  await page.route('**/api/v1/**', (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{}' }))
  await page.goto('/login')
  await dispatchInstallPrompt(page)
  await page.waitForTimeout(800)

  await expect(page.getByRole('region', { name: '홈 화면에 FinWatch 설치' })).toHaveCount(0)
})
