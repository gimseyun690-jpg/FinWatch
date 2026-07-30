import { authFetch } from './client'
import type { ApiResponse } from '../types/stock'
import type { PortfolioEvaluation } from '../types/portfolioEvaluation'

export async function evaluatePortfolio(signal?: AbortSignal) {
  const response = await authFetch('/api/v1/ai/portfolio-evaluations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({}),
    signal,
  })
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string; code?: string } | null
    const message = error?.message ?? '포트폴리오 AI 평가를 생성하지 못했습니다.'
    throw new Error(error?.code ? `${message} [${error.code}]` : message)
  }
  const body = await response.json() as ApiResponse<PortfolioEvaluation>
  return body.data
}
