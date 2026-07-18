import type {
  ApiResponse,
  CanonicalStockDetail,
  PriceHistory,
  PriceInterval,
  PricePeriod,
  StockRef,
  StockSearchPage,
  StockSummary,
  TechnicalAnalysis,
} from '../types/stock'
import { authFetch } from './client'
import type { DataLoadJob, DataLoadResourceResult } from '../types/disclosure'

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

export function getStockPrices(
  stock: StockRef,
  period: PricePeriod = '3M',
  interval: Exclude<PriceInterval, '1m'> = '1D',
  signal?: AbortSignal,
) {
  return get<PriceHistory>(
    `${canonicalPath(stock)}/prices?period=${period}&interval=${interval}`,
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

export async function startStockDataLoad(
  stock: StockRef,
  resources: DataLoadResourceResult['resource'][] = ['QUOTE', 'DAILY_PRICES'],
  signal?: AbortSignal,
) {
  const response = await authFetch(`${canonicalPath(stock)}/data-loads`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ resources }),
    signal,
  })
  if (!response.ok) throw new Error(`Data load request failed: ${response.status}`)
  return ((await response.json()) as ApiResponse<DataLoadJob>).data
}

export function getStockDataLoadJob(stock: StockRef, jobId: string, signal?: AbortSignal) {
  return get<DataLoadJob>(
    `${canonicalPath(stock)}/data-loads/${encodeURIComponent(jobId)}`,
    signal,
  )
}
