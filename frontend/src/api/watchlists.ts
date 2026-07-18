import type { ApiResponse } from '../types/stock'
import type { WatchlistItem } from '../types/watchlist'
import { authFetch } from './client'

type ApiError = {
  message?: string
}

async function readData<T>(response: Response): Promise<T> {
  const body = (await response.json()) as ApiResponse<T> & ApiError
  if (!response.ok) {
    throw new Error(body.message ?? `API request failed: ${response.status}`)
  }
  return body.data
}

export async function getWatchlist(signal?: AbortSignal) {
  return readData<WatchlistItem[]>(await authFetch('/api/v1/watchlists', { signal }))
}

export async function addWatchlist(stock: { market: string; symbol: string }) {
  return readData<WatchlistItem>(await authFetch('/api/v1/watchlists', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(stock),
  }))
}

export async function removeWatchlist(stock: { market: string; symbol: string }) {
  await readData<null>(await authFetch(`/api/v1/watchlists/${encodeURIComponent(stock.market)}/${encodeURIComponent(stock.symbol)}`, {
    method: 'DELETE',
  }))
}
