import type { AuthSession, LoginResponse } from '../types/auth'

const SESSION_KEY = 'finwatch.auth.session'
export const UNAUTHORIZED_EVENT = 'finwatch:unauthorized'

export function saveSession(login: LoginResponse): AuthSession {
  const session: AuthSession = {
    ...login,
    expiresAt: Date.now() + login.expiresIn * 1000,
  }
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  return session
}

export function readSession(): AuthSession | null {
  const stored = localStorage.getItem(SESSION_KEY)
  if (!stored) return null
  try {
    const session = JSON.parse(stored) as AuthSession
    if (!session.accessToken || session.expiresAt <= Date.now()) {
      clearSession()
      return null
    }
    return session
  } catch {
    clearSession()
    return null
  }
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
}

export function accessToken() {
  return readSession()?.accessToken ?? null
}
