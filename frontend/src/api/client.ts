import { csrfToken, UNAUTHORIZED_EVENT } from '../auth/session'

export async function authFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  const headers = new Headers(init.headers)
  const method = (init.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const token = csrfToken()
    if (token) headers.set('X-XSRF-TOKEN', token)
  }
  const timeoutSignal = AbortSignal.timeout(20_000)
  const signal = init.signal ? AbortSignal.any([init.signal, timeoutSignal]) : timeoutSignal

  const response = await fetch(input, { ...init, headers, signal, credentials: 'same-origin' })
  if (response.status === 401) {
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }
  return response
}
