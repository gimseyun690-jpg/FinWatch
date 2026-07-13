import type { AiMetrics, AiUsageLogPage, ApiResponse } from '../types/admin'
import { authFetch } from './client'

async function readData<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
  }
  const body = (await response.json()) as ApiResponse<T>
  return body.data
}

export async function getAiMetrics(signal?: AbortSignal) {
  const response = await authFetch('/api/v1/admin/ai/metrics', { signal })
  return readData<AiMetrics>(response)
}

export async function getAiUsageLogs(signal?: AbortSignal) {
  const params = new URLSearchParams({
    page: '0',
    size: '10',
    sort: 'createdAt',
    direction: 'desc',
  })
  const response = await authFetch(`/api/v1/admin/ai/usage-logs?${params}`, { signal })
  return readData<AiUsageLogPage>(response)
}
