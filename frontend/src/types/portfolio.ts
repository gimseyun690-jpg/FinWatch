export type PortfolioHolding = {
  id: number
  symbol: string
  name: string
  market: string
  currency: string
  quantity: number
  averagePurchasePrice: number
  averagePurchaseFxRate: number | null
  purchaseFxBaseCurrency: string | null
  purchaseFxQuoteCurrency: string | null
  latestPrice: number | null
  priceAsOf: string | null
  priceSource: string | null
  purchaseAmount: number
  evaluationAmount: number | null
  profitLoss: number | null
  returnRate: number | null
  convertedEvaluationAmount: number | null
  convertedPurchaseAmount: number | null
  convertedProfitLoss: number | null
  fxEffectApproximation: number | null
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
  baseCurrency: 'KRW'
  baseCurrencyTotalEvaluationAmount: number | null
  baseCurrencyTotalPurchaseAmount: number | null
  baseCurrencyProfitLoss: number | null
  conversionComplete: boolean
  profitLossComplete: boolean
  fxRates: Array<{
    pair: 'USD/KRW'
    rate: number
    asOf: string
    source: string
    rateType: string
    freshness: string
  }>
  currencySummaries: PortfolioCurrencySummary[]
  holdings: PortfolioHolding[]
}
