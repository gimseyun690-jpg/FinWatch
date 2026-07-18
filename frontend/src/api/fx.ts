import { authFetch } from './client'
import type { FxHistory, FxRate } from '../types/fx'
import type { ApiResponse } from '../types/stock'

async function data<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(error?.message ?? `API request failed: ${response.status}`)
  }
  return ((await response.json()) as ApiResponse<T>).data
}

export async function getUsdKrw(signal?: AbortSignal) {
  return data<FxRate>(await authFetch('/api/v1/market/fx-rates/USD/KRW', { signal }))
}

export async function getUsdKrwHistory(period: FxHistory['period'], signal?: AbortSignal) {
  return data<FxHistory>(await authFetch(`/api/v1/market/fx-rates/USD/KRW/history?period=${period}&interval=1D`, { signal }))
}
