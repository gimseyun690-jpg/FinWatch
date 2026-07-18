import type { ApiResponse } from './stock'

export type { ApiResponse }

export type AiFeatureUsage = {
  feature: string
  requestCount: number
  successCount: number
  failedCount: number
  modelCallCount: number
  cacheHitCount: number
  totalTokens: number
  estimatedCost: number
  savedEstimatedCost: number
}

export type AiMetrics = {
  from: string
  to: string
  requestCount: number
  successCount: number
  failedCount: number
  modelCallCount: number
  cacheHitCount: number
  cacheMissCount: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  estimatedCost: number
  cacheHitRate: number
  savedEstimatedCost: number
  averageResponseTimeMs: number
  costCurrency: string
  featureUsage: AiFeatureUsage[]
}

export type AiUsageLog = {
  id: number
  requestId: string
  featureType: string
  targetType: string
  targetId: number
  modelName: string
  inputTokens: number
  outputTokens: number
  totalTokens: number
  estimatedCost: number
  savedEstimatedCost: number
  cacheHit: boolean
  responseTimeMs: number
  promptVersion: string
  status: string
  errorCode?: string | null
  createdAt: string
}

export type AiUsageLogPage = {
  items: AiUsageLog[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type DataSyncResult = {
  mode: 'DEMO' | 'LIVE'
  startedAt: string
  finishedAt: string
  pricesImported: number
  newsImported: number
  stocks: Array<{
    symbol: string
    market: string
    marketPrices: { provider: string; status: 'SUCCESS' | 'SKIPPED' | 'FALLBACK'; imported: number; message: string }
    news: { provider: string; status: 'SUCCESS' | 'SKIPPED' | 'FALLBACK'; imported: number; message: string }
  }>
}
