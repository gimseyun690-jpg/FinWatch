import { accessToken, clearSession, UNAUTHORIZED_EVENT } from '../auth/session'

export async function authFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  const headers = new Headers(init.headers)
  const token = accessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const timeoutSignal = AbortSignal.timeout(10_000)
  const signal = init.signal ? AbortSignal.any([init.signal, timeoutSignal]) : timeoutSignal

  const response = await fetch(input, { ...init, headers, signal })
  if (response.status === 401) {
    clearSession()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }
  return response
}
