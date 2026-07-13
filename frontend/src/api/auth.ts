import type { ApiResponse, LoginResponse } from '../types/auth'

type ApiError = {
  message?: string
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  const body = (await response.json()) as ApiResponse<LoginResponse> & ApiError
  if (!response.ok) {
    throw new Error(body.message ?? '로그인에 실패했습니다.')
  }
  return body.data
}
