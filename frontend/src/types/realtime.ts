export type MarketSessionStatus =
  | 'DAY_MARKET'
  | 'PRE_MARKET'
  | 'REGULAR'
  | 'AFTER_HOURS'
  | 'CLOSED'
  | 'UNKNOWN'
  | 'LIVE'
  | 'SNAPSHOT'

export type LiveQuote = {
  market: string
  symbol: string
  price: number
  change: number
  changeRate: number
  volume: number
  currency: string
  asOf: string
  source: string
  sessionStatus: MarketSessionStatus
}

export type RealtimeProviderStatus = {
  provider: string
  state: 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED' | 'DEGRADED' | 'DISABLED' | 'ERROR' | string
  message: string
  updatedAt: string
}

export type RealtimeConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'disconnected'

export type RealtimeSnapshot = {
  quotes: LiveQuote[]
  providers: RealtimeProviderStatus[]
}

export type IntradayCandle = {
  market: string
  symbol: string
  time: string
  open: number
  high: number
  low: number
  close: number
  volume: number
  currency: string
  source: string
  sessionStatus?: MarketSessionStatus
}

export type IntradayCandleSnapshot = {
  candles: IntradayCandle[]
}
