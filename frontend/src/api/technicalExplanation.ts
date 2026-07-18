import { authFetch } from './client'
import type { ApiResponse } from '../types/stock'
import type { TechnicalExplanation } from '../types/technicalExplanation'

export async function explainTechnical(market: string, symbol: string, signal?: AbortSignal) {
  const response = await authFetch('/api/v1/ai/technical-explanations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      market,
      symbol,
      interval: '1D',
    }),
    signal,
  })
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string; code?: string } | null
    throw new Error(error?.code ? `${error.message ?? 'AI 기술지표 해설 실패'} [${error.code}]` : `API request failed: ${response.status}`)
  }
  const body = await response.json() as ApiResponse<TechnicalExplanation>
  return body.data
}
