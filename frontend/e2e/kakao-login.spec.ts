import { expect, test } from '@playwright/test'

test('official Kakao button starts the full-page login flow on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
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

  await page.goto('/login?returnTo=%2Fnews')

  const button = page.getByRole('button', { name: '카카오 로그인' })
  const image = button.locator('img')
  await expect(button).toBeEnabled()
  await expect(image).toBeVisible()
  await expect(image).toHaveAttribute('src', '/kakao-login-large-wide.png')
  await expect(image).toHaveAttribute('width', '600')
  await expect(image).toHaveAttribute('height', '90')

  const authorizeRequest = page.waitForRequest((request) => new URL(request.url()).pathname === '/api/v1/auth/kakao/authorize')
  await button.click()
  const authorizeUrl = new URL((await authorizeRequest).url())
  expect(authorizeUrl.searchParams.get('returnTo')).toBe('/news')
})
