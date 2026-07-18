import { authFetch } from './client'
import type { ApiResponse } from '../types/stock'
import type { ContentFeedPage, ContentFeedQuery } from '../types/content'

export class ContentFeedApiError extends Error {
  readonly code: string | null
  readonly status: number

  constructor(
    message: string,
    code: string | null,
    status: number,
  ) {
    super(message)
    this.code = code
    this.status = status
  }
}

export async function getContentFeed(query: ContentFeedQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({
    kind: query.kind,
    market: query.market,
    period: query.period,
    source: query.source,
    analysis: query.analysis,
    page: String(query.page),
    size: String(query.size),
    sort: query.sort,
  })
  if (query.symbol) params.set('symbol', query.symbol)
  if (query.q) params.set('q', query.q)
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)

  const response = await authFetch(`/api/v1/content-feed?${params}`, { signal })
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { code?: string; message?: string } | null
    throw new ContentFeedApiError(
      error?.message ?? `콘텐츠 목록을 불러오지 못했습니다. (${response.status})`,
      error?.code ?? null,
      response.status,
    )
  }
  return ((await response.json()) as ApiResponse<ContentFeedPage>).data
}
