export type ApiResponse<T> = {
  success: boolean
  data: T
  message: string
  timestamp: string
}

export type StockSummary = {
  symbol: string
  name: string
  market: string
  currency: string
  price: number
  change: number
  changeRate: number
  volume: number
  asOf: string
  source: string
}

export type PricePoint = {
  time: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export type PriceHistory = {
  symbol: string
  interval: string
  period: string
  items: PricePoint[]
}

export type PricePeriod = '1M' | '3M' | '6M' | '1Y' | 'ALL'

export type TechnicalAnalysis = {
  symbol: string
  calculatedAt: string
  summarySignal: Signal
  movingAverages: {
    ma5: number
    ma20: number
    ma60: number
    signal: Signal
  }
  rsi: {
    period: number
    value: number
    signal: Signal
  }
  macd: {
    value: number
    signalLine: number
    histogram: number
    signal: Signal
  }
  disclaimer: string
}

export type Signal = 'BUY' | 'NEUTRAL' | 'SELL'
