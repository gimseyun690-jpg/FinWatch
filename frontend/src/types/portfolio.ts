export type PortfolioHolding = {
  id: number
  symbol: string
  name: string
  market: string
  currency: string
  quantity: number
  averagePurchasePrice: number
  latestPrice: number | null
  priceAsOf: string | null
  priceSource: string | null
  purchaseAmount: number
  evaluationAmount: number | null
  profitLoss: number | null
  returnRate: number | null
  valuationStatus: 'VALUED' | 'PRICE_UNAVAILABLE'
  updatedAt: string
}

export type PortfolioCurrencySummary = {
  currency: string
  totalPurchaseAmount: number
  totalEvaluationAmount: number | null
  profitLoss: number | null
  returnRate: number | null
  valuationComplete: boolean
}

export type Portfolio = {
  currencySummaries: PortfolioCurrencySummary[]
  holdings: PortfolioHolding[]
}
