export type TechnicalEvidence = {
  id: string
  indicator: string
  values: Record<string, string>
  displayValue: string
}

export type TechnicalSignalExplanation = {
  text: string
  evidenceIds: string[]
}

export type TechnicalExplanation = {
  analysisId: number
  symbol: string
  market: string
  interval: '1D'
  latestRecordedAt: string
  source: string
  freshness: 'FRESH' | 'STALE' | 'DEMO'
  calculationVersion: string
  promptVersion: string
  inputHash: string
  summarySignal: 'BUY' | 'NEUTRAL' | 'SELL'
  summary: string
  trendExplanation: string
  momentumExplanation: string
  volatilityExplanation: string
  volumeExplanation: string
  supportingSignals: TechnicalSignalExplanation[]
  conflictingSignals: TechnicalSignalExplanation[]
  riskNotes: string[]
  dataLimitations: string[]
  evidence: TechnicalEvidence[]
  modelName: string
  cacheHit: boolean
  inputTokens: number
  outputTokens: number
  estimatedCost: number
  costCurrency: 'USD'
  responseTimeMs: number
  generatedAt: string
  disclaimer: string
}
