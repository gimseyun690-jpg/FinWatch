import { expect, test, type Page } from '@playwright/test'

const now = '2026-07-13T06:00:00Z'
const stock = {
  symbol: '000660',
  name: 'SK하이닉스',
  market: 'KRX',
  currency: 'KRW',
  price: 2723000,
  change: 38200,
  changeRate: 1.42,
  volume: 3870000,
  asOf: now,
  source: 'DEMO',
}

const prices = Array.from({ length: 90 }, (_, index) => {
  const close = 2380000 + index * 3800 + Math.round(Math.sin(index / 4) * 18000)
  return {
    time: new Date(Date.UTC(2026, 3, 15 + index)).toISOString(),
    open: close - 5000,
    high: close + 16000,
    low: close - 14000,
    close,
    volume: 2500000 + (index % 11) * 127000,
  }
})

const intradayCandles = Array.from({ length: 30 }, (_, index) => {
  const close = 2720000 + index * 1000 + Math.round(Math.sin(index / 3) * 8000)
  return {
    symbol: stock.symbol,
    time: new Date(Date.UTC(2026, 6, 14, 0, index)).toISOString(),
    open: close - 2000,
    high: close + 5000,
    low: close - 4000,
    close,
    volume: 1200 + index * 30,
    currency: stock.currency,
    source: 'KIS_WS',
  }
})

function response(data: unknown) {
  return { success: true, data, message: 'fixture', timestamp: now }
}

async function mockApi(page: Page) {
  await page.routeWebSocket('**/ws/quotes', (webSocket) => {
    webSocket.send(JSON.stringify({
      type: 'snapshot',
      data: {
        quotes: [{
          symbol: '000660',
          price: 2750000,
          change: 65900,
          changeRate: 2.46,
          volume: 4100000,
          currency: 'KRW',
          asOf: '2026-07-14T01:15:30Z',
          source: 'KIS_WS',
          sessionStatus: 'LIVE',
        }],
        providers: [
          { provider: 'KIS', state: 'CONNECTED', message: '1개 종목 체결 구독 중', updatedAt: now },
          { provider: 'FINNHUB', state: 'CONNECTED', message: '2개 종목 trade 구독 중', updatedAt: now },
        ],
      },
    }))
    webSocket.send(JSON.stringify({
      type: 'candles',
      data: { candles: intradayCandles },
    }))
  })
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    let body: unknown

    if (path === '/api/v1/health') {
      body = { status: 'UP', timestamp: now }
    } else if (path === '/api/v1/auth/login') {
      body = response({
        accessToken: 'fixture.jwt.token',
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: { id: 1, email: 'admin@finwatch.local', role: 'ADMIN' },
      })
    } else if (path === '/api/v1/watchlists') {
      body = response([{ id: 1, ...stock, addedAt: now }])
    } else if (path === '/api/v1/stocks') {
      body = response([stock])
    } else if (path === '/api/v1/stocks/000660') {
      body = response(stock)
    } else if (path === '/api/v1/stocks/000660/prices') {
      body = response({ symbol: stock.symbol, interval: '1D', period: url.searchParams.get('period') ?? '3M', items: prices })
    } else if (path === '/api/v1/stocks/000660/intraday') {
      body = response({ symbol: stock.symbol, interval: '1m', period: 'SESSION', items: intradayCandles })
    } else if (path === '/api/v1/stocks/000660/technical') {
      body = response({
        symbol: stock.symbol,
        calculatedAt: now,
        calculationVersion: 'technical-v2-wilder',
        summarySignal: 'BUY',
        movingAverages: { ma5: 2700000, ma20: 2660000, ma60: 2520000, signal: 'BUY' },
        rsi: { period: 14, method: 'WILDER', value: 68.4, signal: 'NEUTRAL' },
        macd: { value: 18320, signalLine: 14210, histogram: 4110, signal: 'BUY' },
        bollingerBands: { period: 20, deviationMultiplier: 2, upper: 2750000, middle: 2660000, lower: 2570000, bandwidthPercent: 6.76 },
        atr: { period: 14, value: 42850, percent: 1.57 },
        volumeMa20: 3198500,
        events: [{ time: prices[75].time, type: 'MA_GOLDEN_CROSS', signal: 'BUY' }],
        disclaimer: '기술적 신호는 투자 권유가 아닌 참고 정보입니다.',
      })
    } else if (path.endsWith('/news')) {
      body = response([])
    } else if (path === '/api/v1/admin/ai/metrics') {
      body = response({
        from: now, to: now, requestCount: 0, modelCallCount: 0, cacheHitCount: 0, cacheMissCount: 0,
        inputTokens: 0, outputTokens: 0, totalTokens: 0, estimatedCost: 0, cacheHitRate: 0,
        savedEstimatedCost: 0, averageResponseTimeMs: 0, costCurrency: 'USD', featureUsage: [],
      })
    } else if (path === '/api/v1/admin/ai/usage-logs') {
      body = response({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
    } else if (path === '/api/v1/admin/data/sync') {
      body = response({ mode: 'DEMO', startedAt: now, finishedAt: now, pricesImported: 0, newsImported: 0, stocks: [] })
    } else if (path === '/api/v1/portfolios') {
      body = response({
        currencySummaries: [{
          currency: 'KRW', totalPurchaseAmount: 2500000, totalEvaluationAmount: 2723000,
          profitLoss: 223000, returnRate: 8.92, valuationComplete: true,
        }],
        holdings: [{
          id: 1, symbol: '000660', name: 'SK하이닉스', market: 'KRX', currency: 'KRW',
          quantity: 1, averagePurchasePrice: 2500000, latestPrice: 2723000, priceAsOf: now,
          priceSource: 'DEMO', purchaseAmount: 2500000, evaluationAmount: 2723000,
          profitLoss: 223000, returnRate: 8.92, valuationStatus: 'VALUED', updatedAt: now,
        }],
      })
    } else if (path === '/api/v1/alerts') {
      body = response([{
        id: 1, symbol: '000660', name: 'SK하이닉스', market: 'KRX', condition: 'ABOVE',
        targetPrice: 2740000, currency: 'KRW', status: 'ACTIVE', latestPrice: 2723000,
        priceAsOf: now, evaluationStatus: 'WAITING', triggeredAt: null, createdAt: now,
      }])
    } else {
      body = response(null)
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}

async function login(page: Page) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '투자 정보 대시보드 로그인' })).toBeVisible()
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '개인 투자자용 메인 대시보드' })).toBeVisible()
  await expect(page.getByRole('img', { name: /000660 3M 일봉 캔들 및 거래량 차트/ })).toBeVisible()
}

test.beforeEach(async ({ page }) => {
  await mockApi(page)
})

test('desktop chart tools, indicator settings and drawings remain usable', async ({ page }) => {
  await login(page)

  await expect(page.locator('.realtime-status')).toContainText('실시간 2/2')
  await expect(page.locator('.live-quote-label')).toContainText('KIS_WS · 실시간')
  await expect(page.locator('.live-tick-badge')).toHaveText('TICK')
  await expect(page.locator('.price-chart-card .quote-row')).toContainText('₩2,750,000')
  await expect(page.locator('.portfolio-card')).toContainText('₩2,750,000')
  await expect(page.locator('.portfolio-card')).toContainText('LIVE · KIS_WS')
  await expect(page.locator('.alerts-card')).toContainText('현재 ₩2,750,000 · 조건 충족')
  await expect(page.locator('.alerts-card')).toContainText('LIVE · KIS_WS')

  const bollinger = page.getByRole('button', { name: '볼린저(20,2)' })
  await bollinger.click()
  await expect(bollinger).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('button', { name: 'MACD', exact: true }).click()
  await expect(page.getByRole('button', { name: 'MACD', exact: true })).toHaveAttribute('aria-pressed', 'true')
  const chartCanvas = page.locator('.interactive-chart-canvas')
  await chartCanvas.hover({ position: { x: 420, y: 160 } })
  await expect(page.locator('.chart-tooltip')).toContainText('MA20')
  await expect(page.locator('.chart-tooltip')).toContainText('MACD')

  await page.getByRole('button', { name: '추세선', exact: true }).click()
  const drawingLayer = page.locator('.drawing-layer')
  await drawingLayer.click({ position: { x: 180, y: 130 }, force: true })
  await drawingLayer.click({ position: { x: 430, y: 220 }, force: true })
  await expect(drawingLayer.locator('line.drawing-shape.trend')).toHaveCount(1)

  await page.getByRole('button', { name: '1분봉', exact: true }).click()
  await expect(page.getByRole('img', { name: /000660 실시간 1분봉 캔들 및 거래량 차트/ })).toBeVisible()
  await expect(page.locator('.intraday-session-label')).toContainText('현재 서버 세션')
  await expect(page.locator('.chart-legend strong')).toHaveText('LIVE')
  await expect(drawingLayer.locator('line.drawing-shape.trend')).toHaveCount(0)

  await page.getByRole('button', { name: '외부 데이터 동기화' }).click()
  await expect(page.locator('.data-sync-status')).toContainText('DEMO · 시세 0건 · 뉴스 0건 반영')

  await page.reload()
  await expect(page.getByRole('button', { name: '볼린저(20,2)' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('button', { name: 'MACD', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

test('mobile chart has no horizontal overflow and exposes touch-sized controls', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page)

  const atrButton = page.getByRole('button', { name: 'ATR', exact: true })
  await atrButton.click()
  await expect(atrButton).toHaveAttribute('aria-pressed', 'true')
  const box = await atrButton.boundingBox()
  expect(box?.height ?? 0).toBeGreaterThanOrEqual(36)
  const hasOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
  expect(hasOverflow).toBe(false)
  await expect(page.getByText('기술적 신호와 AI 요약은 투자 권유가 아닌 참고 정보입니다.')).toBeVisible()
})
