export type PortfolioBalanceStatus =
  | 'DIVERSIFIED'
  | 'MODERATE_CONCENTRATION'
  | 'HIGH_CONCENTRATION'
  | 'INCOMPLETE'

export type PortfolioEvaluationStatement = {
  text: string
  evidenceIds: string[]
}

export type PortfolioEvaluationEvidence = {
  id: string
  category: string
  values: Record<string, string>
  displayValue: string
}

export type PortfolioEvaluation = {
  evaluationId: number
  snapshotAt: string
  baseCurrency: string
  balanceStatus: PortfolioBalanceStatus
  headline: string
  summary: string
  diversification: PortfolioEvaluationStatement
  concentration: PortfolioEvaluationStatement
  currencyExposure: PortfolioEvaluationStatement
  performanceContext: PortfolioEvaluationStatement
  strengths: PortfolioEvaluationStatement[]
  riskFactors: PortfolioEvaluationStatement[]
  reviewPoints: PortfolioEvaluationStatement[]
  dataLimitations: string[]
  evidence: PortfolioEvaluationEvidence[]
  audit: {
    inputHash: string
    positionsHash: string
    promptVersion: string
    modelName: string
    cacheHit: boolean
    inputTokens: number
    outputTokens: number
    estimatedCost: number
    savedEstimatedCost: number
    costCurrency: string
    responseTimeMs: number
    generatedAt: string
  }
  disclaimer: string
}
