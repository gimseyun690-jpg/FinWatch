export type BriefingViewpoint = {
  viewpoint: string
  status: string
  changeType: string
  headline: string
  evidenceIds: string[]
}

export type BriefingEvidence = {
  id: string
  domain: 'TECHNICAL' | 'NEWS' | 'DISCLOSURE' | 'QUALITY'
  kind: string
  currentValue: string | null
  previousValue: string | null
  delta: string | null
  displayValue: string
  sourceRef: Record<string, string>
}

export type BriefingStatement = { text: string; evidenceIds: string[] }

export type DailyBriefing = {
  briefingId: number
  symbol: string
  market: string
  currentTradingDate: string
  previousTradingDate: string | null
  baselineStatus: string
  relation: 'ALIGNED' | 'CONFLICTING' | 'PARTIAL' | 'INSUFFICIENT'
  headline: string
  headlineEvidenceIds: string[]
  changeSummary: string
  changeSummaryEvidenceIds: string[]
  viewpoints: BriefingViewpoint[]
  newStrengths: BriefingStatement[]
  newRisks: BriefingStatement[]
  unchangedContext: BriefingStatement[]
  alignedViews: BriefingStatement[]
  conflictingViews: BriefingStatement[]
  dataLimitations: string[]
  evidence: BriefingEvidence[]
  audit: {
    sources: Record<string, string[]>
    latestRecordedAt: string
    calculationVersion: string
    briefingInputVersion: string
    promptVersion: string
    modelName: string
    evidenceCount: number
    excludedContentCount: number
    cacheHit: boolean
    inputTokens: number
    outputTokens: number
    estimatedCost: number
    savedEstimatedCost: number
    costCurrency: 'USD'
    responseTimeMs: number
    generatedAt: string
  }
  staleBriefing: boolean
  disclaimer: string
}
