import type { ApiResponse } from '../types/stock'
import type { DataLoadJob, Disclosure } from '../types/disclosure'
import { authFetch } from './client'

async function readData<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string; code?: string } | null
    const detail = body?.message ?? `API request failed: ${response.status}`
    throw new Error(body?.code ? `${detail} [${body.code}]` : detail)
  }
  return ((await response.json()) as ApiResponse<T>).data
}

function stockPath(market: string, symbol: string) {
  return `/api/v1/stocks/${encodeURIComponent(market)}/${encodeURIComponent(symbol)}`
}

export async function getDisclosures(market: string, symbol: string, signal?: AbortSignal) {
  const response = await authFetch(`${stockPath(market, symbol)}/disclosures`, { signal })
  return readData<Disclosure[]>(response)
}

export async function startDisclosureLoad(market: string, symbol: string, signal?: AbortSignal) {
  const response = await authFetch(`${stockPath(market, symbol)}/data-loads`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ resources: ['DISCLOSURES'] }),
    signal,
  })
  return readData<DataLoadJob>(response)
}

export async function getDataLoadJob(market: string, symbol: string, jobId: string, signal?: AbortSignal) {
  const response = await authFetch(
    `${stockPath(market, symbol)}/data-loads/${encodeURIComponent(jobId)}`,
    { signal },
  )
  return readData<DataLoadJob>(response)
}
