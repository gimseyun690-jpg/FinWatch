const SESSION_KEY = 'finwatch.auth.session'
export const UNAUTHORIZED_EVENT = 'finwatch:unauthorized'

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
}

export function clearLegacyTokenSession() {
  clearSession()
}

export function csrfToken() {
  const prefix = 'XSRF-TOKEN='
  const entry = document.cookie.split(';').map((value) => value.trim()).find((value) => value.startsWith(prefix))
  return entry ? decodeURIComponent(entry.slice(prefix.length)) : null
}
