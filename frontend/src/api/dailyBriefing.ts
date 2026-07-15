import { authFetch } from './client'
import type { ApiResponse } from '../types/stock'
import type { DailyBriefing } from '../types/dailyBriefing'

async function parse(response: Response) {
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { code?: string; message?: string } | null
    throw new Error(error?.code ? `${error.message ?? '브리핑 요청 실패'} [${error.code}]` : `API request failed: ${response.status}`)
  }
  return (await response.json() as ApiResponse<DailyBriefing>).data
}

export async function createDailyBriefing(market: string, symbol: string, signal?: AbortSignal) {
  return parse(await authFetch('/api/v1/ai/daily-change-briefings', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
    body: JSON.stringify({ market, symbol, promptVersion: 'daily-change-briefing-v1' }),
  }))
}

export async function getLatestDailyBriefing(market: string, symbol: string, signal?: AbortSignal) {
  const response = await authFetch(`/api/v1/stocks/${encodeURIComponent(symbol)}/daily-change-briefings/latest?market=${encodeURIComponent(market)}`, { signal })
  if (response.status === 404) return null
  return parse(response)
}
