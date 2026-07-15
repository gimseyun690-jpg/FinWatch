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

const canonicalStock = {
  stockId: 1,
  ...stock,
  exchange: 'KRX',
  englishName: 'SK hynix',
  instrumentType: 'STOCK',
  active: true,
  tradable: true,
  status: 'LISTED',
  dataAvailability: 'READY',
  catalogSource: 'KIS_MASTER',
  catalogUpdatedAt: now,
}

const appleStock = {
  stockId: 4,
  symbol: 'AAPL',
  name: 'Apple',
  market: 'NASDAQ',
  exchange: 'NASDAQ',
  englishName: 'Apple Inc.',
  instrumentType: 'STOCK',
  currency: 'USD',
  active: true,
  tradable: true,
  status: 'LISTED',
  dataAvailability: 'READY',
  catalogSource: 'FINNHUB_SYMBOLS',
  catalogUpdatedAt: now,
  price: 212.42,
  change: 2.1,
  changeRate: 1.0,
  volume: 48120000,
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
    } else if (path === '/api/v1/market/fx-rates/USD/KRW') {
      body = response({ baseCurrency: 'USD', quoteCurrency: 'KRW', rate: 1382.5, previousClose: 1380.6, change: 1.9, changeRate: 0.1376, rateType: 'DEMO', source: 'DEMO', providerSymbol: 'DEMO:USDKRW', asOf: now, fetchedAt: now, freshness: 'FRESH' })
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
    } else if (path === '/api/v1/stocks/search') {
      const query = url.searchParams.get('q')?.toUpperCase() ?? ''
      const searchItems = query.includes('AAPL') || query.includes('APPLE')
        ? [{
            stockId: appleStock.stockId,
            market: appleStock.market,
            exchange: appleStock.exchange,
            symbol: appleStock.symbol,
            name: appleStock.name,
            englishName: appleStock.englishName,
            instrumentType: appleStock.instrumentType,
            currency: appleStock.currency,
            active: true,
            tradable: true,
            status: 'LISTED',
            dataAvailability: 'READY',
            source: 'FINNHUB_SYMBOLS',
          }]
        : []
      body = response({ items: searchItems, page: 0, size: 10, totalElements: searchItems.length, totalPages: searchItems.length ? 1 : 0, catalogAsOf: now })
    } else if (path === '/api/v1/stocks/000660') {
      body = response(stock)
    } else if (path === '/api/v1/stocks/KRX/000660') {
      body = response(canonicalStock)
    } else if (path === '/api/v1/stocks/NASDAQ/AAPL') {
      body = response(appleStock)
    } else if (path === '/api/v1/stocks/000660/prices' || path === '/api/v1/stocks/KRX/000660/prices') {
      body = response({ symbol: stock.symbol, interval: '1D', period: url.searchParams.get('period') ?? '3M', items: prices })
    } else if (path === '/api/v1/stocks/NASDAQ/AAPL/prices') {
      body = response({ symbol: appleStock.symbol, interval: '1D', period: url.searchParams.get('period') ?? '3M', items: prices.map((item) => ({ ...item, open: item.open / 10000, high: item.high / 10000, low: item.low / 10000, close: item.close / 10000 })) })
    } else if (path === '/api/v1/stocks/000660/intraday' || path === '/api/v1/stocks/KRX/000660/intraday') {
      body = response({ symbol: stock.symbol, interval: '1m', period: 'SESSION', items: intradayCandles })
    } else if (path === '/api/v1/stocks/000660/technical' || path === '/api/v1/stocks/KRX/000660/technical' || path === '/api/v1/stocks/NASDAQ/AAPL/technical') {
      const responseSymbol = path.includes('/AAPL/') ? 'AAPL' : stock.symbol
      body = response({
        symbol: responseSymbol,
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
    } else if (path.endsWith('/news') || path.endsWith('/disclosures')) {
      body = response([])
    } else if (path === '/api/v1/admin/ai/metrics') {
      body = response({
        from: now, to: now, requestCount: 0, successCount: 0, failedCount: 0, modelCallCount: 0, cacheHitCount: 0, cacheMissCount: 0,
        inputTokens: 0, outputTokens: 0, totalTokens: 0, estimatedCost: 0, cacheHitRate: 0,
        savedEstimatedCost: 0, averageResponseTimeMs: 0, costCurrency: 'USD', featureUsage: [],
      })
    } else if (path === '/api/v1/admin/ai/usage-logs') {
      body = response({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
    } else if (path === '/api/v1/ai/technical-explanations') {
      body = response({
        analysisId: 1,
        symbol: stock.symbol,
        market: stock.market,
        interval: '1D',
        latestRecordedAt: prices.at(-1)?.time ?? now,
        source: 'DEMO',
        freshness: 'DEMO',
        calculationVersion: 'technical-v2-wilder',
        promptVersion: 'technical-explanation-v1',
        inputHash: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        summarySignal: 'BUY',
        summary: '추세와 모멘텀은 상승 우세지만 변동성 위험을 함께 확인해야 합니다.',
        trendExplanation: '단기 이동평균이 중장기 이동평균 위에 있습니다.',
        momentumExplanation: 'RSI는 과매수 경계에 가까우며 MACD는 양수입니다.',
        volatilityExplanation: 'ATR 기준 변동 폭이 존재합니다.',
        volumeExplanation: '현재 거래량을 20일 평균과 함께 확인해야 합니다.',
        supportingSignals: [{ text: '이동평균 정배열', evidenceIds: ['I1'] }],
        conflictingSignals: [{ text: 'RSI 과열 경계', evidenceIds: ['I2'] }],
        riskNotes: ['단일 시점 기술지표만으로 미래 수익을 보장할 수 없습니다.'],
        dataLimitations: ['DEMO 데이터이므로 실제 투자 판단에 사용할 수 없습니다.'],
        evidence: [
          { id: 'I1', indicator: 'MOVING_AVERAGE', values: { ma5: '2700000', ma20: '2660000' }, displayValue: 'MA5 2,700,000 · MA20 2,660,000' },
          { id: 'I2', indicator: 'RSI', values: { value: '68.4', period: '14' }, displayValue: 'RSI(14) 68.4' },
        ],
        modelName: 'mock-technical-v1',
        cacheHit: false,
        inputTokens: 420,
        outputTokens: 180,
        estimatedCost: 0,
        costCurrency: 'USD',
        responseTimeMs: 18,
        generatedAt: now,
        disclaimer: '기술지표 해설은 투자 권유가 아닌 참고 정보입니다.',
      })
    } else if (path === '/api/v1/ai/daily-change-briefings' && request.method() === 'POST') {
      body = response({
        briefingId: 1, symbol: stock.symbol, market: stock.market,
        currentTradingDate: '2026-07-13', previousTradingDate: '2026-07-12', baselineStatus: 'AVAILABLE',
        relation: 'CONFLICTING', headline: '상승 흐름과 과열 주의 신호가 함께 나타났습니다.',
        headlineEvidenceIds: ['T1', 'T3'], changeSummary: '종가와 모멘텀이 개선됐지만 RSI 경계를 함께 확인해야 합니다.',
        changeSummaryEvidenceIds: ['T1', 'T3', 'T4'],
        viewpoints: [
          { viewpoint: 'TREND', status: 'POSITIVE', changeType: 'STRENGTHENED', headline: '가격과 이동평균 관계', evidenceIds: ['T1', 'T2'] },
          { viewpoint: 'MOMENTUM', status: 'POSITIVE', changeType: 'STRENGTHENED', headline: 'RSI와 MACD 변화', evidenceIds: ['T3', 'T4'] },
          { viewpoint: 'OVERHEAT', status: 'CAUTION', changeType: 'NEW', headline: 'RSI 과열 경계', evidenceIds: ['T3'] },
          { viewpoint: 'VOLATILITY', status: 'NEUTRAL', changeType: 'UNCHANGED', headline: 'ATR 변동성 변화', evidenceIds: ['T5'] },
          { viewpoint: 'VOLUME', status: 'CONFIRMING', changeType: 'STRENGTHENED', headline: '거래량 확인 여부', evidenceIds: ['T1', 'T6'] },
          { viewpoint: 'NEWS', status: 'INSUFFICIENT', changeType: 'INSUFFICIENT', headline: '검증된 신규 근거 없음', evidenceIds: [] },
          { viewpoint: 'DISCLOSURE', status: 'INSUFFICIENT', changeType: 'INSUFFICIENT', headline: '검증된 신규 근거 없음', evidenceIds: [] },
        ],
        newStrengths: [{ text: '전일보다 종가 흐름이 높아졌습니다.', evidenceIds: ['T1'] }],
        newRisks: [{ text: '과열 경계를 함께 확인해야 합니다.', evidenceIds: ['T3'] }],
        unchangedContext: [], alignedViews: [], conflictingViews: [{ text: '긍정과 주의가 함께 존재합니다.', evidenceIds: ['T1', 'T3'] }],
        dataLimitations: ['DEMO 데이터이므로 실제 투자 판단에 사용할 수 없습니다.'],
        evidence: [
          { id: 'T1', domain: 'TECHNICAL', kind: 'PRICE_CHANGE', currentValue: '2723000', previousValue: '2700000', delta: '23000', displayValue: '종가 2,700,000 → 2,723,000', sourceRef: { type: 'CHART_INDICATOR', target: 'PRICE' } },
          { id: 'T3', domain: 'TECHNICAL', kind: 'RSI_CHANGE', currentValue: '68.4', previousValue: '65.2', delta: '3.2', displayValue: 'RSI14 65.2 → 68.4', sourceRef: { type: 'CHART_INDICATOR', target: 'RSI' } },
          { id: 'Q1', domain: 'QUALITY', kind: 'DATA_QUALITY', currentValue: null, previousValue: null, delta: null, displayValue: '가격 출처 DEMO', sourceRef: { type: 'DATA_QUALITY', source: 'DEMO' } },
        ],
        audit: { sources: { TECHNICAL: ['CHART_INDICATOR'], QUALITY: ['DEMO'] }, latestRecordedAt: now, calculationVersion: 'technical-v2-wilder', briefingInputVersion: 'daily-briefing-input-v1', promptVersion: 'daily-change-briefing-v1', modelName: 'mock-daily-v1', evidenceCount: 3, excludedContentCount: 0, cacheHit: false, inputTokens: 420, outputTokens: 180, estimatedCost: 0, savedEstimatedCost: 0, costCurrency: 'USD', responseTimeMs: 21, generatedAt: now },
        staleBriefing: false, disclaimer: 'AI 브리핑은 투자 권유가 아닌 정보 정리 결과입니다.',
      })
    } else if (path === '/api/v1/admin/data/sync') {
      body = response({ mode: 'DEMO', startedAt: now, finishedAt: now, pricesImported: 0, newsImported: 0, stocks: [] })
    } else if (path === '/api/v1/portfolios') {
      body = response({
        baseCurrency: 'KRW', baseCurrencyTotalPurchaseAmount: 2500000, baseCurrencyTotalEvaluationAmount: 2723000,
        baseCurrencyProfitLoss: 223000, baseCurrencyReturnRate: 8.92, conversionComplete: true, profitLossComplete: true,
        fxRates: [{ pair: 'USD/KRW', rate: 1382.5, rateType: 'DEMO', source: 'DEMO', asOf: now, freshness: 'FRESH' }],
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

test('technical AI explanation exposes evidence and audit metadata', async ({ page }) => {
  await login(page)

  await page.getByRole('button', { name: 'AI 기술 해설 실행' }).click()
  await expect(page.locator('.ai-technical-card .cache-badge')).toHaveText('CACHE MISS')
  await expect(page.locator('.technical-ai-summary')).toContainText('추세와 모멘텀은 상승 우세')
  await expect(page.locator('.technical-signal-grid')).toContainText('I1')
  await expect(page.locator('.technical-limitations')).toContainText('DEMO 데이터')

  await page.getByText('서버 계산 근거 2개 보기').click()
  await expect(page.locator('.technical-evidence-drawer')).toContainText('I1 · 이동평균')
  await expect(page.locator('.analysis-disclaimer').last()).toContainText('technical-explanation-v1')
})

test('daily change briefing exposes viewpoint conflicts, evidence and audit metadata', async ({ page }) => {
  await login(page)

  await page.getByRole('button', { name: '오늘의 변화 생성' }).click()
  await expect(page.locator('.daily-briefing-card')).toContainText('상승 흐름과 과열 주의 신호')
  await expect(page.locator('.daily-briefing-card')).toContainText('관점 충돌')
  await expect(page.locator('.viewpoint-matrix')).toContainText('주의')
  await page.getByText('검증 근거 3개 보기').click()
  await expect(page.locator('.briefing-evidence')).toContainText('T1 · 기술')
  await page.getByText('AI 감사 정보').click()
  await expect(page.locator('.briefing-audit')).toContainText('daily-change-briefing-v1')
})

test('global search selects a canonical market and restores every detail context', async ({ page }) => {
  await login(page)

  const search = page.getByPlaceholder('종목명 또는 심볼 검색')
  await search.fill('AAPL')
  await expect(page.getByRole('option', { name: /AAPL/ })).toBeVisible()
  await search.press('Enter')

  await expect(page).toHaveURL(/\/stocks\/NASDAQ\/AAPL$/)
  await expect(page.getByRole('heading', { name: 'Apple 기술적 분석' })).toBeVisible()
  await expect(page.getByRole('img', { name: /AAPL 3M 일봉 캔들 및 거래량 차트/ })).toBeVisible()
  await expect(page.locator('.ai-technical-card')).toContainText('NASDAQ · AAPL')

  await page.goBack()
  await expect(page.getByRole('heading', { name: 'SK하이닉스 기술적 분석' })).toBeVisible()
})
