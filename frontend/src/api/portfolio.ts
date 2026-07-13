import { authFetch } from './client'
import type { Portfolio, PortfolioHolding } from '../types/portfolio'
import type { ApiResponse } from '../types/stock'

async function readData<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string; code?: string } | null
    throw new Error(error?.message ?? `API request failed: ${response.status}`)
  }
  return ((await response.json()) as ApiResponse<T>).data
}

export async function getPortfolio(signal?: AbortSignal) {
  return readData<Portfolio>(await authFetch('/api/v1/portfolios', { signal }))
}

export async function createHolding(input: {
  symbol: string
  quantity: number
  averagePurchasePrice: number
  currency: string
}) {
  return readData<PortfolioHolding>(await authFetch('/api/v1/portfolios/holdings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  }))
}

export async function deleteHolding(holdingId: number) {
  return readData<null>(await authFetch(`/api/v1/portfolios/holdings/${holdingId}`, { method: 'DELETE' }))
}
