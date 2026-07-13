export type PriceAlert = {
  id: number
  symbol: string
  name: string
  market: string
  condition: 'ABOVE' | 'BELOW'
  targetPrice: number
  currency: string
  status: 'ACTIVE' | 'TRIGGERED' | 'DISABLED'
  latestPrice: number | null
  priceAsOf: string | null
  evaluationStatus: 'WAITING' | 'CONDITION_MET' | 'NOT_EVALUATED' | 'PRICE_UNAVAILABLE'
  triggeredAt: string | null
  createdAt: string
}
