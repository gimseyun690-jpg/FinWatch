import type { ApiResponse, LoginResponse } from '../types/auth'

type ApiError = {
  message?: string
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  let response: Response
  try {
    response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
      signal: AbortSignal.timeout(10_000),
    })
  } catch (error: unknown) {
    if (error instanceof DOMException && error.name === 'TimeoutError') {
      throw new Error('API 서버 응답 시간이 초과되었습니다.')
    }
    throw error
  }
  const body = (await response.json()) as ApiResponse<LoginResponse> & ApiError
  if (!response.ok) {
    throw new Error(body.message ?? '로그인에 실패했습니다.')
  }
  return body.data
}
