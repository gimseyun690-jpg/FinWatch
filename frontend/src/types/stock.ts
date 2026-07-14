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
  indicators?: {
    ma5: number | null
    ma20: number | null
    ma60: number | null
    volumeMa20: number | null
    bollingerUpper: number | null
    bollingerMiddle: number | null
    bollingerLower: number | null
    rsi: number | null
    macd: number | null
    macdSignal: number | null
    macdHistogram: number | null
    atr: number | null
  }
}

export type PriceHistory = {
  symbol: string
  interval: string
  period: string
  items: PricePoint[]
}

export type PricePeriod = '1M' | '3M' | '6M' | '1Y' | 'ALL'

export type PriceInterval = '1D' | '1m'

export type TechnicalAnalysis = {
  symbol: string
  calculatedAt: string
  calculationVersion?: string
  summarySignal: Signal
  movingAverages: {
    ma5: number
    ma20: number
    ma60: number
    signal: Signal
  }
  rsi: {
    period: number
    method?: 'WILDER'
    value: number
    signal: Signal
  }
  macd: {
    value: number
    signalLine: number
    histogram: number
    signal: Signal
  }
  bollingerBands?: {
    period: number
    deviationMultiplier: number
    upper: number
    middle: number
    lower: number
    bandwidthPercent?: number
  }
  atr?: {
    period: number
    value: number
    percent?: number
  }
  volumeMa20?: number
  events?: Array<{
    time: string
    type: 'MA_GOLDEN_CROSS' | 'MA_DEAD_CROSS' | 'MACD_BULLISH_CROSS' | 'MACD_BEARISH_CROSS' | 'RSI_OVERSOLD_ENTER' | 'RSI_OVERSOLD_EXIT' | 'RSI_OVERBOUGHT_ENTER' | 'RSI_OVERBOUGHT_EXIT'
    signal: Signal
  }>
  disclaimer: string
}

export type Signal = 'BUY' | 'NEUTRAL' | 'SELL'
