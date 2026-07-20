import { csrfToken } from '../auth/session'
import type { ApiResponse, KakaoLoginStatus, LoginResponse } from '../types/auth'

type ApiError = {
  message?: string
}

export async function getSession(signal?: AbortSignal): Promise<LoginResponse | null> {
  const response = await fetch('/api/v1/auth/session', {
    method: 'GET',
    credentials: 'same-origin',
    signal: signal ?? AbortSignal.timeout(10_000),
  })
  if (response.status === 401) return null
  const body = (await response.json()) as ApiResponse<LoginResponse> & ApiError
  if (!response.ok) throw new Error(body.message ?? '로그인 세션을 확인하지 못했습니다.')
  return body.data
}

export async function logout(): Promise<void> {
  const headers = new Headers()
  const token = csrfToken()
  if (token) headers.set('X-XSRF-TOKEN', token)
  const response = await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers,
    signal: AbortSignal.timeout(10_000),
  })
  if (!response.ok && response.status !== 401) {
    const body = (await response.json()) as ApiError
    throw new Error(body.message ?? '로그아웃에 실패했습니다.')
  }
}

export async function getKakaoLoginStatus(signal?: AbortSignal): Promise<KakaoLoginStatus> {
  const response = await fetch('/api/v1/auth/kakao/status', {
    credentials: 'same-origin',
    signal: signal ?? AbortSignal.timeout(5_000),
  })
  const body = (await response.json()) as ApiResponse<KakaoLoginStatus> & ApiError
  if (!response.ok) throw new Error(body.message ?? '카카오 로그인 설정을 확인하지 못했습니다.')
  return body.data
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const headers = new Headers()
  headers.set('Content-Type', 'application/json')
  const token = csrfToken()
  if (token) headers.set('X-XSRF-TOKEN', token)

  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers,
    body: JSON.stringify({ email, password }),
    signal: AbortSignal.timeout(10_000),
  })

  const body = (await response.json()) as ApiResponse<LoginResponse> & ApiError
  if (!response.ok) throw new Error(body.message ?? '로그인에 실패했습니다.')
  return body.data
}
