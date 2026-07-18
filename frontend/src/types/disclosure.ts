export type Disclosure = {
  id: number
  market: string
  symbol: string
  title: string
  publisher: string
  url: string
  publishedAt: string
  source: 'OPENDART' | 'SEC_EDGAR' | string
  disclosureType: string | null
  aiAnalysisAllowed: boolean
  fetchedAt: string | null
}

export type DataLoadResourceResult = {
  resource: 'QUOTE' | 'DAILY_PRICES' | 'NEWS' | 'DISCLOSURES'
  status: 'PENDING' | 'READY' | 'FAILED' | 'UNSUPPORTED'
  provider: string | null
  imported: number
  message: string
  asOf: string | null
}

export type DataLoadJob = {
  jobId: string
  market: string
  symbol: string
  status: 'SYNCING' | 'READY' | 'PARTIAL' | 'FAILED'
  reused: boolean
  startedAt: string
  finishedAt: string | null
  resources: DataLoadResourceResult[]
}
