import type { AiSummary, ApiResponse, NewsArticle } from '../types/news'
import { authFetch } from './client'
import { getStockDataLoadJob, startStockDataLoad } from './stocks'

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

// Existing articles remain usable if a provider is temporarily unavailable.
// Empty or stale lists get one bounded, news-only refresh before we report them.
export async function getStockNewsWithRefresh(market: string, symbol: string, signal?: AbortSignal) {
  const existing = await getStockNews(market, symbol, signal)
  const newest = Math.max(0, ...existing.map((item) => new Date(item.publishedAt).getTime() || 0))
  if (existing.length > 0 && newest > Date.now() - 6 * 60 * 60 * 1000) return existing

  try {
    const stock = { market, symbol }
    let job = await startStockDataLoad(stock, ['NEWS'], signal)
    for (let attempt = 0; job.status === 'SYNCING' && attempt < 20; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 500))
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      job = await getStockDataLoadJob(stock, job.jobId, signal)
    }
    if (job.status === 'SYNCING') throw new Error('뉴스 공급자 응답이 지연되고 있습니다.')
    const result = job.resources.find((item) => item.resource === 'NEWS')
    if (result?.status === 'FAILED') throw new Error(result.message || '뉴스 공급자 동기화에 실패했습니다.')
    const refreshed = await getStockNews(market, symbol, signal)
    return refreshed.length > 0 ? refreshed : existing
  } catch (error) {
    if (signal?.aborted) throw error
    if (existing.length > 0) return existing
    throw error
  }
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
