import type { AiSummary, ApiResponse, NewsArticle } from '../types/news'
import { authFetch } from './client'

async function readData<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorBody = await response.json().catch(() => null) as { message?: string; code?: string } | null
    const detail = errorBody?.message ?? `API request failed: ${response.status}`
    throw new Error(errorBody?.code ? `${detail} [${errorBody.code}]` : detail)
  }
  const body = (await response.json()) as ApiResponse<T>
  return body.data
}

export async function getStockNews(market: string, symbol: string, signal?: AbortSignal) {
  const response = await authFetch(`/api/v1/stocks/${encodeURIComponent(market)}/${encodeURIComponent(symbol)}/news`, { signal })
  return readData<NewsArticle[]>(response)
}

export async function summarizeNews(newsId: number, signal?: AbortSignal) {
  const response = await authFetch('/api/v1/ai/news-summaries', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ newsId }),
    signal,
  })
  return readData<AiSummary>(response)
}
