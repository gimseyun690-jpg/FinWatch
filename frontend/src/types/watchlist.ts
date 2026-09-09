export type WatchlistItem = {
  id: number
  symbol: string
  name: string
  market: string
  currency: string
  price: number | null
  change: number | null
  changeRate: number | null
  asOf: string | null
  source: string | null
  dataAvailability: 'READY' | 'PARTIAL' | 'METADATA_ONLY' | 'UNAVAILABLE'
  addedAt: string
  sessionStatus?: string | null
}
