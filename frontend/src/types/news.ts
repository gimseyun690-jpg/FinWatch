import type { ApiResponse } from './stock'

export type { ApiResponse }

export type NewsArticle = {
  id: number
  symbol: string
  title: string
  publisher: string
  url: string
  publishedAt: string
  summaryAvailable: boolean
  source: string
  contentSource: 'PROVIDER_SUMMARY' | 'ALLOWLIST_ARTICLE' | 'ON_DEMAND_ARTICLE' | 'OFFICIAL_DISCLOSURE' | 'METADATA_ONLY'
  rightsProfile: 'METADATA_ONLY' | 'TRANSIENT_AI' | 'STORE_FOR_AI' | 'STORE_AND_DISPLAY'
  aiAnalysisAllowed: boolean
  fetchedAt: string | null
}

export type AiSummary = {
  analysisId: number
  newsId: number
  symbol: string
  summary: string
  keyPoints: string[]
  positiveFactors: string[]
  riskFactors: string[]
  mentionedCompanies: string[]
  evidenceSegments: string[]
  keywords: string[]
  sentiment: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
  modelName: string
  promptVersion: string
  analysisScope: 'FULL_PROCESSED_TEXT' | 'PARTIAL_PROCESSED_TEXT' | 'LEGACY'
  originalCharacters: number
  processedCharacters: number
  providerCallCount: number
  cacheHit: boolean
  inputTokens: number
  outputTokens: number
  estimatedCost: number
  costCurrency: string
  responseTimeMs: number
  generatedAt: string
}
