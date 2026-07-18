export type FxRate = {
  baseCurrency: string
  quoteCurrency: string
  rate: number
  previousClose: number | null
  change: number | null
  changeRate: number | null
  rateType: 'LIVE' | 'DELAYED' | 'REFERENCE' | 'DEMO'
  source: string
  providerSymbol: string | null
  asOf: string
  fetchedAt: string
  freshness: 'FRESH' | 'DELAYED' | 'STALE'
}

export type FxHistory = {
  baseCurrency: string
  quoteCurrency: string
  period: '1W' | '1M' | '3M' | '1Y'
  interval: '1D'
  rateType: string
  items: Array<{
    time: string
    open: number
    high: number
    low: number
    close: number
    source: string
  }>
}
