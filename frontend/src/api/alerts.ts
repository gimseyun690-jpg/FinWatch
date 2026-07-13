import { authFetch } from './client'
import type { PriceAlert } from '../types/alert'
import type { ApiResponse } from '../types/stock'

async function readData<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(error?.message ?? `API request failed: ${response.status}`)
  }
  return ((await response.json()) as ApiResponse<T>).data
}

export async function getAlerts(signal?: AbortSignal) {
  return readData<PriceAlert[]>(await authFetch('/api/v1/alerts', { signal }))
}

export async function createAlert(input: {
  symbol: string
  condition: 'ABOVE' | 'BELOW'
  targetPrice: number
  currency: string
}) {
  return readData<PriceAlert>(await authFetch('/api/v1/alerts', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
  }))
}

export async function setAlertStatus(alertId: number, status: 'ACTIVE' | 'DISABLED') {
  return readData<PriceAlert>(await authFetch(`/api/v1/alerts/${alertId}`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status }),
  }))
}

export async function deleteAlert(alertId: number) {
  return readData<null>(await authFetch(`/api/v1/alerts/${alertId}`, { method: 'DELETE' }))
}
