import type { ApiResponse, PriceHistory, PricePeriod, StockSummary, TechnicalAnalysis } from '../types/stock'
import { authFetch } from './client'

async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await authFetch(path, { signal })
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
  }
  const body = (await response.json()) as ApiResponse<T>
  return body.data
}

export function getStock(symbol: string, signal?: AbortSignal) {
  return get<StockSummary>(`/api/v1/stocks/${encodeURIComponent(symbol)}`, signal)
}

export function getStocks(signal?: AbortSignal) {
  return get<StockSummary[]>('/api/v1/stocks', signal)
}

export function getStockPrices(symbol: string, period: PricePeriod = '3M', signal?: AbortSignal) {
  return get<PriceHistory>(
    `/api/v1/stocks/${encodeURIComponent(symbol)}/prices?period=${period}&interval=1D`,
    signal,
  )
}

export function getTechnicalAnalysis(symbol: string, signal?: AbortSignal) {
  return get<TechnicalAnalysis>(
    `/api/v1/stocks/${encodeURIComponent(symbol)}/technical`,
    signal,
  )
}
