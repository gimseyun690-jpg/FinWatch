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
  historyAvailable: true,
  historyPoints: 90,
  historyAsOf: now,
  historySource: 'DEMO',
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
  historyAvailable: true,
  historyPoints: 90,
  historyAsOf: now,
  historySource: 'DEMO',
  price: 212.42,
  change: 2.1,
  changeRate: 1.0,
  volume: 48120000,
  asOf: now,
  source: 'DEMO',
}

const disclosure = {
  id: 701,
  market: 'KRX',
  symbol: '000660',
  title: '신규시설투자등',
  publisher: '금융감독원 전자공시시스템',
  url: 'https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260715000123',
  publishedAt: now,
  source: 'OPENDART',
  disclosureType: 'B',
  aiAnalysisAllowed: false,
  fetchedAt: null,
}

const disclosureSummary = {
  analysisId: 801,
  newsId: disclosure.id,
  symbol: disclosure.symbol,
  summary: '생산 설비 투자를 확대하며 집행 일정과 자금 조달 계획을 함께 공시했습니다.',
  keyPoints: ['신규 생산 설비 투자', '단계별 투자 집행', '일정 변경 가능성 명시'],
  positiveFactors: ['생산 역량 확대 계획'],
  riskFactors: ['투자 일정과 집행 금액 변경 가능성'],
  mentionedCompanies: ['SK하이닉스'],
  evidenceSegments: ['S1', 'S2'],
  keywords: ['시설투자', '생산설비', '자금조달'],
  sentiment: 'NEUTRAL',
  modelName: 'gemini-fixture',
  promptVersion: 'news-analysis-live-v1',
  analysisScope: 'FULL_PROCESSED_TEXT',
  originalCharacters: 2400,
  processedCharacters: 1800,
  providerCallCount: 1,
  cacheHit: false,
  inputTokens: 620,
  outputTokens: 180,
  estimatedCost: 0.00031,
  costCurrency: 'USD',
  responseTimeMs: 780,
  generatedAt: now,
}

const metadataOnlyNews = {
  id: 601,
  symbol: '000660',
  title: 'HBM 생산 확대와 차세대 메모리 투자 계획',
  publisher: 'Fixture Economy',
  url: 'https://example.com/news/hbm-investment',
  publishedAt: now,
  summaryAvailable: false,
  source: 'NAVER_API_HUB',
  contentSource: 'METADATA_ONLY',
  rightsProfile: 'METADATA_ONLY',
  aiAnalysisAllowed: false,
  fetchedAt: null,
}

const metadataOnlyNewsSummary = {
  ...disclosureSummary,
  analysisId: 802,
  newsId: metadataOnlyNews.id,
  summary: '원문을 요청 시 수집해 HBM 생산 확대와 차세대 메모리 투자 계획을 요약했습니다.',
  keyPoints: ['HBM 생산 능력 확대', '차세대 메모리 투자 계획', '수요 변동성 확인 필요'],
  positiveFactors: ['고부가 메모리 생산 역량 확대'],
  riskFactors: ['시장 수요와 투자 일정 변동 가능성'],
  keywords: ['HBM', '차세대 메모리', '설비 투자'],
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
    market: stock.market,
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

const realtimeSenders = new WeakMap<Page, (event: unknown) => void>()
const watchlistReadCounts = new WeakMap<Page, number>()
const requestedAuthRoles = new WeakMap<Page, 'ADMIN' | 'USER'>()

async function mockApi(page: Page) {
  watchlistReadCounts.set(page, 0)
  requestedAuthRoles.set(page, 'ADMIN')
  let authenticated = false
  let currentUser = {
    id: 1,
    displayName: 'admin',
    email: 'admin@finwatch.local' as string | null,
    profileImageUrl: null,
    role: 'ADMIN',
    authProvider: 'LOCAL',
  }
  const watchlistItems = [{ id: 1, ...stock, dataAvailability: 'READY', addedAt: now }]
  await page.routeWebSocket('**/ws/quotes', (webSocket) => {
    realtimeSenders.set(page, (event) => webSocket.send(JSON.stringify(event)))
    webSocket.send(JSON.stringify({
      type: 'snapshot',
      data: {
        quotes: [{
          market: 'KRX',
          symbol: '000660',
          price: 2750000,
          change: 65900,
          changeRate: 2.46,
          volume: 4100000,
          currency: 'KRW',
          asOf: '2026-07-14T01:15:30Z',
          source: 'KIS_WS',
          sessionStatus: 'LIVE',
        }, {
          market: 'NASDAQ',
          symbol: '000660',
          price: 1.25,
          change: -0.1,
          changeRate: -7.41,
          volume: 12,
          currency: 'USD',
          asOf: '2026-07-14T01:15:31Z',
          source: 'COLLISION_FIXTURE',
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
    } else if (path === '/api/v1/auth/kakao/status') {
      body = response({ enabled: true })
    } else if (path === '/api/v1/auth/kakao/authorize') {
      const role = requestedAuthRoles.get(page) ?? 'ADMIN'
      currentUser = {
        id: 1,
        displayName: role === 'ADMIN' ? 'admin' : 'user',
        email: role === 'ADMIN' ? 'admin@finwatch.local' : 'user@finwatch.local',
        profileImageUrl: null,
        role,
        authProvider: 'KAKAO',
      }
      authenticated = true
      await route.fulfill({ status: 303, headers: { location: '/dashboard' } })
      return
    } else if (path === '/api/v1/auth/session') {
      if (!authenticated) {
        await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ success: false, code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.' }) })
        return
      }
      body = response({ authenticated: true, expiresAt: '2026-07-17T12:00:00Z', user: currentUser })
    } else if (path === '/api/v1/market/fx-rates/USD/KRW') {
      body = response({ baseCurrency: 'USD', quoteCurrency: 'KRW', rate: 1382.5, previousClose: 1380.6, change: 1.9, changeRate: 0.1376, rateType: 'DEMO', source: 'DEMO', providerSymbol: 'DEMO:USDKRW', asOf: now, fetchedAt: now, freshness: 'FRESH' })
    } else if (path === '/api/v1/auth/login') {
      const credentials = request.postDataJSON() as { email?: string }
      const role = credentials.email?.startsWith('user@') ? 'USER' : 'ADMIN'
      currentUser = {
        id: 1,
        displayName: role === 'ADMIN' ? 'admin' : 'user',
        email: credentials.email ?? 'admin@finwatch.local',
        profileImageUrl: null,
        role,
        authProvider: 'LOCAL',
      }
      authenticated = true
      body = response({
        authenticated: true,
        expiresAt: '2026-07-17T12:00:00Z',
        user: currentUser,
      })
    } else if (path === '/api/v1/auth/logout') {
      authenticated = false
      body = response(null)
    } else if (path === '/api/v1/watchlists' && request.method() === 'POST') {
      const requested = request.postDataJSON() as { market: string; symbol: string }
      const source = requested.symbol === 'AAPL' ? appleStock : canonicalStock
      const added = {
        id: watchlistItems.length + 1,
        symbol: source.symbol,
        name: source.name,
        market: source.market,
        currency: source.currency,
        price: source.price,
        change: source.change,
        changeRate: source.changeRate,
        asOf: source.asOf,
        source: source.source,
        dataAvailability: source.dataAvailability,
        addedAt: now,
      }
      watchlistItems.push(added)
      body = response(added)
    } else if (path === '/api/v1/watchlists') {
      watchlistReadCounts.set(page, (watchlistReadCounts.get(page) ?? 0) + 1)
      body = response(watchlistItems)
    } else if (path.startsWith('/api/v1/watchlists/') && request.method() === 'DELETE') {
      body = response(null)
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
    } else if (path.endsWith('/data-loads') && request.method() === 'POST') {
      body = response({
        jobId: 'fixture-data-load', market: path.includes('/NASDAQ/') ? 'NASDAQ' : 'KRX',
        symbol: path.includes('/AAPL/') ? 'AAPL' : '000660', status: 'READY', reused: false,
        startedAt: now, completedAt: now,
        resources: [{ resource: 'DAILY_PRICES', status: 'READY', provider: 'DEMO_DB', imported: 0, message: 'fixture', asOf: now }],
      })
    } else if (path === '/api/v1/stocks/000660/prices' || path === '/api/v1/stocks/KRX/000660/prices') {
      body = response({ symbol: stock.symbol, interval: url.searchParams.get('interval') ?? '1D', period: url.searchParams.get('period') ?? '3M', source: 'DEMO', items: prices })
    } else if (path === '/api/v1/stocks/NASDAQ/AAPL/prices') {
      body = response({ symbol: appleStock.symbol, interval: url.searchParams.get('interval') ?? '1D', period: url.searchParams.get('period') ?? '3M', source: 'DEMO', items: prices.map((item) => ({ ...item, open: item.open / 10000, high: item.high / 10000, low: item.low / 10000, close: item.close / 10000 })) })
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
    } else if (path === '/api/v1/stocks/KRX/000660/disclosures') {
      body = response([disclosure])
    } else if (path === '/api/v1/ai/disclosure-summaries' && request.method() === 'POST') {
      body = response(disclosureSummary)
    } else if (path === '/api/v1/stocks/KRX/000660/news') {
      body = response([metadataOnlyNews])
    } else if (path === '/api/v1/ai/news-summaries' && request.method() === 'POST') {
      body = response(metadataOnlyNewsSummary)
    } else if (path === '/api/v1/content-feed') {
      const pageNumber = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 20)
      const requestedKind = url.searchParams.get('kind') ?? 'ALL'
      const analysis = url.searchParams.get('analysis') ?? 'ALL'
      const baseItems = [
        {
          id: 900 + pageNumber * size,
          kind: requestedKind === 'DISCLOSURE' ? 'DISCLOSURE' : 'NEWS',
          market: url.searchParams.get('market') === 'NASDAQ' ? 'NASDAQ' : 'KRX',
          symbol: url.searchParams.get('symbol') ?? '000660',
          stockName: url.searchParams.get('market') === 'NASDAQ' ? 'Apple' : 'SK하이닉스',
          title: `${pageNumber + 1}페이지 시장 콘텐츠`,
          publisher: requestedKind === 'DISCLOSURE' ? 'Open DART' : 'Fixture News',
          source: requestedKind === 'DISCLOSURE' ? 'OPENDART' : 'DEMO',
          publishedAt: now,
          disclosureType: requestedKind === 'DISCLOSURE' ? 'B' : null,
          contentSource: requestedKind === 'DISCLOSURE' ? 'OFFICIAL_DISCLOSURE' : 'METADATA_ONLY',
          rightsProfile: requestedKind === 'DISCLOSURE' ? 'STORE_FOR_AI' : 'METADATA_ONLY',
          aiAnalysisAllowed: requestedKind === 'DISCLOSURE' || analysis === 'AI_ALLOWED' || analysis === 'AI_COMPLETED',
          aiAnalysisStatus: analysis === 'AI_COMPLETED' ? 'COMPLETED' : requestedKind === 'DISCLOSURE' || analysis === 'AI_ALLOWED' ? 'AVAILABLE' : 'UNAVAILABLE',
          summaryPreview: analysis === 'AI_COMPLETED' ? 'AI가 검증된 원문에서 추출한 핵심 요약입니다.' : null,
          url: 'https://example.com/original',
        },
      ]
      body = response({
        items: baseItems,
        page: pageNumber,
        size,
        totalElements: 45,
        totalPages: Math.ceil(45 / size),
        hasPrevious: pageNumber > 0,
        hasNext: pageNumber + 1 < Math.ceil(45 / size),
        sort: url.searchParams.get('sort') ?? 'publishedAt,desc',
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

async function login(page: Page, role: 'ADMIN' | 'USER' = 'ADMIN') {
  requestedAuthRoles.set(page, role)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '투자 정보 대시보드 로그인' })).toBeVisible()
  await page.getByRole('button', { name: '카카오 로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '개인 투자자용 메인 대시보드' })).toBeVisible()
}

test.beforeEach(async ({ page }) => {
  await mockApi(page)
})

test('desktop chart tools, indicator settings and drawings remain usable', async ({ page }) => {
  test.slow()
  await login(page)
  await page.goto('/stocks/KRX/000660/technical')

  await expect(page.locator('.realtime-status')).toContainText('실시간 2/2')
  await expect(page.locator('.stock-detail .eyebrow')).toContainText('KIS_WS')
  await expect(page.locator('.live-tick-badge')).toHaveText('TICK')
  await expect(page.locator('.price-chart-card .quote-row')).toContainText('₩2,750,000')

  const bollinger = page.getByRole('button', { name: '볼린저(20,2)' })
  await bollinger.click()
  await expect(bollinger).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('button', { name: 'MACD', exact: true }).click()
  await expect(page.getByRole('button', { name: 'MACD', exact: true })).toHaveAttribute('aria-pressed', 'true')
  const chartCanvas = page.locator('.interactive-chart-canvas')
  await chartCanvas.hover({ position: { x: 420, y: 160 } })
  await expect(page.locator('.chart-tooltip')).toContainText('MA20')
  await expect(page.locator('.chart-tooltip')).toContainText('MACD')
  await expect(page.locator('.chart-tooltip')).toContainText(/거래량 .*주/)

  await page.getByRole('button', { name: '주봉', exact: true }).click()
  await expect(page.getByRole('img', { name: /000660 3M 주봉 캔들 및 거래량\(주\) 차트/ })).toBeVisible()
  await page.getByRole('button', { name: '월봉', exact: true }).click()
  await expect(page.getByRole('img', { name: /000660 3M 월봉 캔들 및 거래량\(주\) 차트/ })).toBeVisible()
  await page.getByRole('button', { name: '일봉', exact: true }).click()

  await page.getByRole('button', { name: '추세선', exact: true }).click()
  const drawingLayer = page.locator('.drawing-layer')
  await drawingLayer.click({ position: { x: 180, y: 130 }, force: true })
  await drawingLayer.click({ position: { x: 430, y: 220 }, force: true })
  await expect(drawingLayer.locator('line.drawing-shape.trend')).toHaveCount(1)

  await page.getByText('키보드 그리기', { exact: true }).click()
  await page.getByRole('button', { name: '최근 추세선 추가' }).click()
  await expect(drawingLayer.locator('line.drawing-shape.trend')).toHaveCount(2)
  await page.getByRole('button', { name: '가격 위로' }).click()
  await expect(page.locator('.keyboard-drawing-feedback')).toContainText('위로 0.5% 이동')
  await expect(page.locator('.chart-a11y-summary')).toContainText(/거래량 .*주/)

  await page.getByRole('button', { name: '1분봉', exact: true }).click()
  await expect(page.getByRole('img', { name: /000660 실시간 1분봉 캔들 및 거래량\(주\) 차트/ })).toBeVisible()
  await expect(page.locator('.intraday-session-label')).toContainText('현재 서버 세션')
  await expect(page.locator('.chart-data-meta')).toContainText('WebSocket · 실시간')
  await expect(drawingLayer.locator('line.drawing-shape.trend')).toHaveCount(0)

  await page.reload()
  await expect(page.getByRole('button', { name: '볼린저(20,2)' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('button', { name: 'MACD', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

test('live quote updates the latest candle without recreating the chart', async ({ page }) => {
  await login(page)
  await page.goto('/stocks/KRX/000660/technical')

  const chart = page.locator('.interactive-chart-canvas')
  await expect(chart).toBeVisible()
  const instanceBeforeTick = await chart.getAttribute('data-chart-instance')
  expect(instanceBeforeTick).toBeTruthy()

  const sendRealtime = realtimeSenders.get(page)
  expect(sendRealtime).toBeDefined()
  sendRealtime?.({
    type: 'quote',
    data: {
      market: 'KRX',
      symbol: '000660',
      price: 2751000,
      change: 66900,
      changeRate: 2.5,
      volume: 4101200,
      currency: 'KRW',
      asOf: '2026-07-14T01:15:31Z',
      source: 'KIS_WS',
      sessionStatus: 'LIVE',
    },
  })

  await expect(page.locator('.price-chart-card .quote-row')).toContainText('₩2,751,000')
  await expect(chart).toHaveAttribute('data-chart-instance', instanceBeforeTick!)
})

test('dashboard live ticks keep the watchlist mounted without refetching it', async ({ page }) => {
  await login(page)

  const stockGrid = page.locator('.stock-grid')
  await expect(stockGrid).toBeVisible()
  const originalGrid = await stockGrid.elementHandle()
  expect(originalGrid).not.toBeNull()
  const readsBeforeTicks = watchlistReadCounts.get(page) ?? 0
  const sendRealtime = realtimeSenders.get(page)
  expect(sendRealtime).toBeDefined()

  for (let index = 1; index <= 3; index += 1) {
    sendRealtime?.({
      type: 'quote',
      data: {
        market: 'KRX',
        symbol: '000660',
        price: 2750000 + index * 1000,
        change: 65900 + index * 1000,
        changeRate: 2.46 + index * 0.01,
        volume: 4100000 + index * 100,
        currency: 'KRW',
        asOf: `2026-07-14T01:15:3${index}Z`,
        source: 'KIS_WS',
        sessionStatus: 'LIVE',
      },
    })
  }

  await expect(stockGrid).toContainText('₩2,753,000')
  expect(watchlistReadCounts.get(page) ?? 0).toBe(readsBeforeTicks)
  expect(await originalGrid!.evaluate((element) => (
    element.isConnected && element === document.querySelector('.stock-grid')
  ))).toBe(true)
})

test('realtime frames with the same symbol in another market never replace the selected instrument', async ({ page }) => {
  await login(page)

  const selectedCard = page.locator('.stock-card.selected')
  await expect(selectedCard).toContainText('₩2,750,000')

  const sendRealtime = realtimeSenders.get(page)
  expect(sendRealtime).toBeDefined()
  sendRealtime?.({
    type: 'quote',
    data: {
      market: 'NASDAQ',
      symbol: '000660',
      price: 0.5,
      change: -0.75,
      changeRate: -60,
      volume: 14,
      currency: 'USD',
      asOf: '2026-07-14T01:15:32Z',
      source: 'COLLISION_FIXTURE',
      sessionStatus: 'LIVE',
    },
  })

  await page.waitForTimeout(50)
  await expect(selectedCard).toContainText('₩2,750,000')
  await expect(selectedCard).not.toContainText('₩1')
})

test('mobile chart has no horizontal overflow and exposes touch-sized controls', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page)
  await page.goto('/stocks/KRX/000660/technical')

  const atrButton = page.getByRole('button', { name: 'ATR', exact: true })
  await atrButton.click()
  await expect(atrButton).toHaveAttribute('aria-pressed', 'true')
  const box = await atrButton.boundingBox()
  expect(box?.height ?? 0).toBeGreaterThanOrEqual(44)
  const hasOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
  expect(hasOverflow).toBe(false)
  await expect(page.getByText('기술적 신호와 AI 요약은 투자 권유가 아닌 참고 정보입니다.')).toBeVisible()
})

test('technical AI explanation exposes evidence and audit metadata', async ({ page }) => {
  await login(page)
  await page.goto('/stocks/KRX/000660/technical')

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
  await page.goto('/stocks/KRX/000660/briefing')

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
  await expect(page.getByRole('heading', { name: 'Apple 종목 개요' })).toBeVisible()
  await expect(page.getByRole('img', { name: /AAPL 3M 일봉 캔들 및 거래량\(주\) 차트/ })).toBeVisible()
  await page.getByRole('link', { name: '차트·기술분석' }).click()
  await expect(page.locator('.ai-technical-card')).toContainText('NASDAQ · AAPL')

  await page.goBack()
  await expect(page).toHaveURL(/\/stocks\/NASDAQ\/AAPL$/)
})

test('watchlist search adds a catalog stock beyond the four demo fixtures', async ({ page }) => {
  await login(page)

  await page.getByRole('button', { name: /관심종목 추가/ }).click()
  const search = page.getByPlaceholder('예: 삼성전자, NAVER, Apple, AAPL')
  await search.fill('AAPL')
  await expect(page.getByRole('button', { name: 'Apple 관심종목 추가' })).toBeVisible()
  await page.getByRole('button', { name: 'Apple 관심종목 추가' }).click()

  await expect(page).toHaveURL(/\/stocks\/NASDAQ\/AAPL$/)
  await page.goto('/watchlist')
  await expect(page.locator('.watchlist-title-row')).toContainText('2개 종목 · 개수 제한 없음')
  await expect(page.locator('.stock-card')).toHaveCount(2)
})

test('official disclosure can fetch its source and render a Gemini summary', async ({ page }) => {
  await login(page)
  await page.goto('/stocks/KRX/000660/disclosures')

  const summarizeButton = page.getByRole('button', { name: 'Gemini 공시 요약' })
  await summarizeButton.scrollIntoViewIfNeeded()
  await expect(summarizeButton).toBeVisible()
  await expect(page.locator('.disclosure-content-state')).toContainText('요약 시 원문 확보')
  await summarizeButton.click()

  const panel = page.locator('.disclosure-row.selected .disclosure-summary-panel')
  await expect(panel).toBeVisible()
  await expect(panel).toContainText('생산 설비 투자를 확대')
  await expect(panel).toContainText('투자 일정과 집행 금액 변경 가능성')
  await expect(panel.locator('.cache-badge')).toHaveText('CACHE MISS')
})

test('metadata-only news with an http source can fetch the original and render a Gemini summary', async ({ page }) => {
  await login(page)
  await page.goto('/stocks/KRX/000660/news')

  const newsPanel = page.locator('.ai-news-card')
  await expect(newsPanel.getByRole('heading', { name: 'AI 뉴스 분석' })).toBeVisible()
  await expect(newsPanel.getByText(metadataOnlyNews.title)).toBeVisible()
  await expect(newsPanel.getByText(/원문 링크 · 요청 시 원문 수집/).first()).toBeVisible()
  const sourceLink = newsPanel.getByRole('link', { name: /원문 보기/ }).first()
  await expect(sourceLink).toHaveAttribute('href', metadataOnlyNews.url)

  const requestPromise = page.waitForRequest((request) => (
    new URL(request.url()).pathname === '/api/v1/ai/news-summaries'
      && request.method() === 'POST'
  ))
  const summarizeButton = newsPanel.getByRole('button', { name: 'Gemini 뉴스 요약' })
  await expect(summarizeButton).toBeEnabled()
  await summarizeButton.click()
  const summaryRequest = await requestPromise
  expect(summaryRequest.postDataJSON()).toEqual({ newsId: metadataOnlyNews.id })

  await expect(newsPanel.locator('.summary-result')).toContainText('원문을 요청 시 수집해 HBM 생산 확대')
  await expect(newsPanel.getByRole('link', { name: /분석 원문 보기/ })).toHaveAttribute('href', metadataOnlyNews.url)
  await expect(newsPanel.locator('.cache-badge')).toHaveText(/CACHE MISS/)
})

test('route shell preserves active navigation and collapsed sidebar preference', async ({ page }) => {
  await login(page)

  const dashboardLink = page.getByRole('link', { name: '대시보드', exact: true })
  await expect(dashboardLink).toHaveAttribute('aria-current', 'page')
  await page.getByRole('button', { name: '사이드바 접기' }).click()
  await expect(page.locator('.workspace-frame')).toHaveClass(/sidebar-collapsed/)
  await page.reload()
  await expect(page.locator('.workspace-frame')).toHaveClass(/sidebar-collapsed/)
  await page.getByRole('button', { name: '사이드바 펼치기' }).click()

  await page.getByRole('link', { name: '뉴스', exact: true }).first().click()
  await expect(page).toHaveURL(/\/news$/)
  await expect(page.getByRole('heading', { name: 'AI 뉴스 분석' })).toBeVisible()
  await expect(page.getByRole('link', { name: '뉴스', exact: true }).first()).toHaveAttribute('aria-current', 'page')

  await page.getByRole('link', { name: '공시', exact: true }).first().click()
  await expect(page).toHaveURL(/\/disclosures$/)
  await expect(page.getByRole('heading', { name: '기업 공시' })).toBeVisible()
})

test('navigation uses the shared SVG icon set and exposes financial data status labels', async ({ page }) => {
  await login(page)

  const desktopLinks = page.locator('.desktop-sidebar .shell-nav-link')
  await expect(desktopLinks).toHaveCount(9)
  await expect(page.locator('.desktop-sidebar .shell-nav-link svg')).toHaveCount(9)
  await expect(page.locator('.stock-card .data-status-badge').first()).toContainText(/실시간|지연|갱신 지연|일부 준비/)

  await page.goto('/stocks/KRX/000660/technical')
  await expect(page.locator('.chart-data-meta .data-status-badge')).toBeVisible()
  await expect(page.locator('.chart-legend')).toContainText('MA(5)')
  await expect(page.locator('.chart-legend')).toContainText('RSI(14)')
  await expect(page.getByRole('toolbar', { name: '상세 차트 도구 모음' })).toBeVisible()
  await expect(page.getByRole('group', { name: '그리기 도구' })).toBeVisible()
})

test('news and disclosure routes are separated without the generic content feed', async ({ page }) => {
  await login(page)
  await page.goto('/news')
  await expect(page.locator('.ai-news-card')).toBeVisible()
  await expect(page.locator('.content-feed-page')).toHaveCount(0)
  await expect(page.locator('.disclosure-card')).toHaveCount(0)

  await page.goto('/disclosures')
  await expect(page.getByRole('heading', { name: '기업 공시' })).toBeVisible()
  await expect(page.locator('.ai-news-card')).toHaveCount(0)

  await page.goto('/content')
  await expect(page).toHaveURL(/\/news$/)
})

test('mobile bottom navigation and more drawer restore focus', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page)

  const more = page.getByRole('button', { name: '더보기 메뉴' })
  await more.click()
  await expect(page.getByRole('dialog', { name: '더보기' })).toBeVisible()
  await expect(page.getByRole('link', { name: '포트폴리오' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '더보기' })).toBeHidden()
  await expect(more).toBeFocused()
  expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false)
})

test('regular users cannot see or directly open administrator routes', async ({ page }) => {
  await login(page, 'USER')
  await expect(page.getByRole('heading', { name: '개인 투자자용 메인 대시보드' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'AI 사용량' })).toHaveCount(0)

  await page.goto('/admin/ai')
  await expect(page.getByRole('heading', { name: '관리자 권한이 필요합니다.' })).toBeVisible()
  await expect(page).toHaveURL(/\/forbidden$/)
})

test('legacy hash URLs and direct routes resolve to their canonical screens', async ({ page }) => {
  await login(page)
  await page.goto('/#news')
  await expect(page).toHaveURL(/\/news$/)
  await expect(page.getByRole('heading', { name: 'AI 뉴스 분석' })).toBeVisible()

  await page.goto('/not-a-finwatch-route')
  await expect(page.getByRole('heading', { name: '요청한 화면을 찾을 수 없습니다.' })).toBeVisible()
})

test('administrator data route runs external provider synchronization', async ({ page }) => {
  await login(page)
  await page.goto('/admin/data')
  await page.getByRole('button', { name: '외부 데이터 동기화' }).click()
  await expect(page.locator('.data-sync-summary')).toContainText('DEMO')
  await expect(page.locator('.data-sync-summary')).toContainText('시세')
  await expect(page.locator('.data-sync-summary')).toContainText('뉴스')
})

test('async state matrix distinguishes loading, empty, partial and stale data', async ({ context, page }) => {
  let releaseAdminRequests = () => {}
  const adminGate = new Promise<void>((resolve) => { releaseAdminRequests = resolve })

  await page.route('**/api/v1/admin/ai/metrics', async (route) => {
    await adminGate
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(response({
        from: now,
        to: now,
        requestCount: 4,
        successCount: 4,
        failedCount: 0,
        modelCallCount: 2,
        cacheHitCount: 2,
        cacheMissCount: 2,
        inputTokens: 320,
        outputTokens: 140,
        totalTokens: 460,
        estimatedCost: 0.0012,
        cacheHitRate: 50,
        savedEstimatedCost: 0.0011,
        averageResponseTimeMs: 112,
        costCurrency: 'USD',
        featureUsage: [],
      })),
    })
  })
  await page.route('**/api/v1/admin/ai/usage-logs*', async (route) => {
    await adminGate
    await route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'USAGE_LOG_UNAVAILABLE', message: 'usage log fixture unavailable' }),
    })
  })

  await login(page)
  await page.goto('/admin/ai')
  await expect(page.locator('.admin-loading')).toBeVisible()

  releaseAdminRequests()
  await expect(page.locator('.admin-title-meta .data-status-badge')).toHaveClass(/status-partial/)
  await expect(page.locator('.admin-metric-grid')).toBeVisible()

  await context.setOffline(true)
  await expect(page.locator('.admin-title-meta .data-status-badge')).toHaveClass(/status-stale/)
  await expect(page.locator('.admin-metric-grid')).toBeVisible()
  await context.setOffline(false)

  await page.route('**/api/v1/stocks/*/*/news', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(response([])),
    })
  })
  await page.goto('/news')
  await expect(page.getByText('저장된 종목 뉴스가 없습니다.')).toBeVisible()
})

test('captures reproducible UI polish evidence for desktop and 390px mobile', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await login(page)
  await expect(page.locator('.stock-grid')).toBeVisible()
  await page.screenshot({ path: '../docs/evidence/18-desktop-dashboard.png', fullPage: true })

  await page.goto('/stocks/KRX/000660/technical')
  await expect(page.getByRole('toolbar', { name: '상세 차트 도구 모음' })).toBeVisible()
  await page.screenshot({ path: '../docs/evidence/18-desktop-technical.png', fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/dashboard')
  await expect(page.locator('.mobile-bottom-nav')).toBeVisible()
  await page.screenshot({ path: '../docs/evidence/18-mobile-dashboard.png' })
})

test('captures AI cache miss, cache hit and provider failure evidence', async ({ page }) => {
  let requestCount = 0
  await page.route('**/api/v1/ai/technical-explanations', async (route) => {
    requestCount += 1

    if (requestCount === 3) {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'AI_PROVIDER_UNAVAILABLE',
          message: 'Gemini 공급자 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.',
        }),
      })
      return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(response({
        analysisId: 18,
        symbol: stock.symbol,
        market: stock.market,
        interval: '1D',
        latestRecordedAt: prices.at(-1)?.time ?? now,
        source: 'KIS_REST',
        freshness: 'FRESH',
        calculationVersion: 'technical-v2-wilder',
        promptVersion: 'technical-explanation-v1',
        inputHash: '18f3c0440b3c18f3c0440b3c18f3c0440b3c18f3c0440b3c18f3c0440b3c18f3',
        summarySignal: 'BUY',
        summary: '단기 추세는 상승 우세지만 RSI 과열 경계와 변동성 확대를 함께 확인해야 합니다.',
        trendExplanation: 'MA5가 MA20 위에 있어 단기 가격 흐름은 우상향 근거가 우세합니다.',
        momentumExplanation: 'RSI(14)는 68.4로 과열 기준에 가까우며 MACD는 양의 모멘텀을 유지합니다.',
        volatilityExplanation: 'ATR(14)은 가격의 1.57%로 단기 변동 폭이 확대될 수 있습니다.',
        volumeExplanation: '현재 거래량은 20일 평균과 함께 확인해야 추세 신뢰도를 판단할 수 있습니다.',
        supportingSignals: [{ text: '단기 이동평균 정배열', evidenceIds: ['I1'] }],
        conflictingSignals: [{ text: 'RSI 과열 경계 접근', evidenceIds: ['I2'] }],
        riskNotes: ['단일 시점 기술지표는 미래 수익을 보장하지 않습니다.'],
        dataLimitations: ['일봉 마감 데이터 기준이며 장중 변화는 반영되지 않을 수 있습니다.'],
        evidence: [
          { id: 'I1', indicator: 'MOVING_AVERAGE', values: { ma5: '2700000', ma20: '2660000' }, displayValue: 'MA5 2,700,000 · MA20 2,660,000' },
          { id: 'I2', indicator: 'RSI', values: { value: '68.4', period: '14' }, displayValue: 'RSI(14) 68.4' },
        ],
        modelName: 'gemini-2.0-flash',
        cacheHit: requestCount === 2,
        inputTokens: requestCount === 2 ? 0 : 420,
        outputTokens: requestCount === 2 ? 0 : 180,
        estimatedCost: requestCount === 2 ? 0 : 0.00018,
        costCurrency: 'USD',
        responseTimeMs: requestCount === 2 ? 24 : 780,
        generatedAt: now,
        disclaimer: 'AI 기술지표 해설은 투자 권유가 아닌 참고 정보입니다.',
      })),
    })
  })

  await login(page)
  await page.goto('/stocks/KRX/000660/technical')
  await page.addStyleTag({
    content: '.shell-topbar, .stock-route-tabs { position: static !important; }',
  })

  const aiCard = page.locator('.ai-technical-card')
  await page.getByRole('button', { name: 'AI 기술 해설 실행' }).click()
  await expect(aiCard.locator('.cache-badge')).toHaveText('CACHE MISS')
  await expect(aiCard).toContainText('gemini-2.0-flash')
  await aiCard.screenshot({ path: '../docs/evidence/18-ai-cache-miss.png' })

  await page.getByRole('button', { name: '같은 스냅샷 다시 해설' }).click()
  await expect(aiCard.locator('.cache-badge')).toHaveText('CACHE HIT')
  await expect(aiCard).toContainText('캐시 적중 결과')
  await aiCard.screenshot({ path: '../docs/evidence/18-ai-cache-hit.png' })

  await page.reload()
  await page.addStyleTag({
    content: '.shell-topbar, .stock-route-tabs { position: static !important; }',
  })
  await page.getByRole('button', { name: 'AI 기술 해설 실행' }).click()
  await expect(aiCard.getByRole('alert')).toContainText('Gemini 공급자 연결에 실패했습니다.')
  await expect(aiCard.getByRole('alert')).toContainText('[AI_PROVIDER_UNAVAILABLE]')
  await aiCard.screenshot({ path: '../docs/evidence/18-ai-provider-failure.png' })
})
