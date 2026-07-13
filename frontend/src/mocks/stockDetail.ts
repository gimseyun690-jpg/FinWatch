import type { PriceHistory, StockSummary, TechnicalAnalysis } from '../types/stock'

export const demoStock: StockSummary = {
  symbol: '000660',
  name: 'SK하이닉스',
  market: 'KRX',
  currency: 'KRW',
  price: 2723000,
  change: 38200,
  changeRate: 1.42,
  volume: 3870000,
  asOf: '2026-07-13T06:00:00Z',
  source: 'DEMO',
}

const start = Date.UTC(2026, 3, 15)
const prices = Array.from({ length: 90 }, (_, index) => {
  const close = index === 89
    ? 2723000
    : Math.round((2380000 + index * 3400 + Math.sin(index / 4) * 26000) / 100) * 100
  return {
    time: new Date(start + index * 86_400_000).toISOString(),
    open: close - Math.round(Math.sin(index * 1.7) * 120),
    high: close + 18000,
    low: close - 16000,
    close,
    volume: 2500000 + (index % 11) * 127000,
  }
})

export const demoPriceHistory: PriceHistory = {
  symbol: '000660',
  interval: '1D',
  period: '3M',
  items: prices,
}

export const demoTechnical: TechnicalAnalysis = {
  symbol: '000660',
  calculatedAt: '2026-07-13T06:00:00Z',
  summarySignal: 'BUY',
  movingAverages: {
    ma5: 2689400,
    ma20: 2648200,
    ma60: 2523100,
    signal: 'BUY',
  },
  rsi: { period: 14, value: 68.4, signal: 'NEUTRAL' },
  macd: { value: 18320.5, signalLine: 14210.1, histogram: 4110.4, signal: 'BUY' },
  disclaimer: '기술적 신호는 투자 권유가 아닌 참고 정보입니다.',
}

