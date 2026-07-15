import type {
  ApiResponse,
  CanonicalStockDetail,
  PriceHistory,
  PricePeriod,
  StockRef,
  StockSearchPage,
  StockSummary,
  TechnicalAnalysis,
} from '../types/stock'
import { authFetch } from './client'

async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await authFetch(path, { signal })
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
  }
  const body = (await response.json()) as ApiResponse<T>
  return body.data
}

function canonicalPath(stock: StockRef) {
  return `/api/v1/stocks/${encodeURIComponent(stock.market)}/${encodeURIComponent(stock.symbol)}`
}

export function getStock(stock: StockRef, signal?: AbortSignal) {
  return get<CanonicalStockDetail>(canonicalPath(stock), signal)
}

export function getStocks(signal?: AbortSignal) {
  return get<StockSummary[]>('/api/v1/stocks', signal)
}

export function getStockPrices(stock: StockRef, period: PricePeriod = '3M', signal?: AbortSignal) {
  return get<PriceHistory>(
    `${canonicalPath(stock)}/prices?period=${period}&interval=1D`,
    signal,
  )
}

export function getStockIntraday(stock: StockRef, signal?: AbortSignal) {
  return get<PriceHistory>(
    `${canonicalPath(stock)}/intraday?limit=390`,
    signal,
  )
}

export function getTechnicalAnalysis(stock: StockRef, signal?: AbortSignal) {
  return get<TechnicalAnalysis>(
    `${canonicalPath(stock)}/technical`,
    signal,
  )
}

export function searchStocks(query: string, signal?: AbortSignal, page = 0, size = 10) {
  const params = new URLSearchParams({ q: query, page: String(page), size: String(size) })
  return get<StockSearchPage>(`/api/v1/stocks/search?${params.toString()}`, signal)
}
