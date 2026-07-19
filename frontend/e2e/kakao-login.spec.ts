import { expect, test, type Page } from '@playwright/test'

async function mockLoggedOutKakaoApi(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/health') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' })
      return
    }
    if (url.pathname === '/api/v1/auth/session') {
      await route.fulfill({ status: 401, contentType: 'application/json', body: '{"message":"로그인이 필요합니다."}' })
      return
    }
    if (url.pathname === '/api/v1/auth/kakao/status') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: '{"success":true,"data":{"enabled":true}}',
      })
      return
    }
    if (url.pathname === '/api/v1/auth/kakao/authorize') {
      await route.fulfill({ status: 204 })
      return
    }
    await route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
}

test('official Kakao button starts the full-page login flow on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockLoggedOutKakaoApi(page)

  await page.goto('/login?returnTo=%2Fnews')

  const button = page.getByRole('button', { name: '카카오 로그인' })
  const image = button.locator('img')
  await expect(page.getByRole('textbox')).toHaveCount(0)
  await expect(page.getByRole('button')).toHaveCount(1)
  await expect(button).toBeEnabled()
  await expect(image).toBeVisible()
  await expect(image).toHaveAttribute('src', '/kakao-login-large-wide.png')
  await expect(image).toHaveAttribute('width', '600')
  await expect(image).toHaveAttribute('height', '90')

  const authorizeRequest = page.waitForRequest((request) => new URL(request.url()).pathname === '/api/v1/auth/kakao/authorize')
  await button.click()
  const authorizeUrl = new URL((await authorizeRequest).url())
  expect(authorizeUrl.searchParams.get('returnTo')).toBe('/news')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('desktop login explains cancellation, expiry and provider outage without storing a token', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('finwatch.auth.session', 'legacy-browser-token'))
  await mockLoggedOutKakaoApi(page)

  const cases = [
    ['kakao_cancelled', '카카오 로그인이 취소되었습니다. 원할 때 다시 시도할 수 있습니다.'],
    ['kakao_request_invalid', '카카오 로그인 요청이 만료되었거나 이미 사용되었습니다. 다시 시작해 주세요.'],
    ['kakao_unavailable', '카카오 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'],
  ] as const

  for (const [errorCode, message] of cases) {
    await page.goto(`/login?error=${errorCode}`)
    await expect(page.getByRole('alert')).toHaveText(message)
    await expect(page.getByRole('button', { name: '카카오 로그인' })).toBeEnabled()
    expect(await page.evaluate(() => localStorage.getItem('finwatch.auth.session'))).toBeNull()
  }
})
