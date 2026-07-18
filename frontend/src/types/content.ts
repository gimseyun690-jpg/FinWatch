export type ContentKind = 'ALL' | 'NEWS' | 'DISCLOSURE'
export type ContentAnalysisFilter = 'ALL' | 'METADATA_ONLY' | 'AI_ALLOWED' | 'AI_COMPLETED'
export type ContentPeriod = '24H' | '7D' | '1M' | '3M' | 'CUSTOM'
export type ContentSort = 'publishedAt,desc' | 'publishedAt,asc'

export type ContentFeedItem = {
  id: number
  kind: 'NEWS' | 'DISCLOSURE'
  market: string
  symbol: string
  stockName: string
  title: string
  publisher: string | null
  source: string
  publishedAt: string
  disclosureType: string | null
  contentSource: 'PROVIDER_SUMMARY' | 'ALLOWLIST_ARTICLE' | 'ON_DEMAND_ARTICLE' | 'OFFICIAL_DISCLOSURE' | 'METADATA_ONLY'
  rightsProfile: 'METADATA_ONLY' | 'TRANSIENT_AI' | 'STORE_FOR_AI' | 'STORE_AND_DISPLAY'
  aiAnalysisAllowed: boolean
  aiAnalysisStatus: 'AVAILABLE' | 'COMPLETED' | 'UNAVAILABLE'
  summaryPreview: string | null
  url: string
}

export type ContentFeedPage = {
  items: ContentFeedItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasPrevious: boolean
  hasNext: boolean
  sort: ContentSort
}

export type ContentFeedQuery = {
  kind: ContentKind
  market: string
  symbol?: string
  q?: string
  period: ContentPeriod
  from?: string
  to?: string
  source: string
  analysis: ContentAnalysisFilter
  page: number
  size: number
  sort: ContentSort
}
