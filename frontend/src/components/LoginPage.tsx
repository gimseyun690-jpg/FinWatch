import { useEffect, useState, type FormEvent } from 'react'
import { getKakaoLoginStatus, login } from '../api/auth'
import type { LoginResponse } from '../types/auth'

type Props = {
  apiState: 'checking' | 'connected' | 'offline'
  returnTo: string
  oauthError: string | null
  onLogin: (response: LoginResponse) => void
}

const demoAccounts = [
  { label: '일반 사용자', email: 'user@finwatch.local', password: 'FinWatch123!', role: 'USER' },
  { label: '관리자', email: 'admin@finwatch.local', password: 'FinWatchAdmin123!', role: 'ADMIN' },
] as const

const oauthErrors: Record<string, string> = {
  kakao_cancelled: '카카오 로그인이 취소되었습니다. 원할 때 다시 시도할 수 있습니다.',
  kakao_request_invalid: '카카오 로그인 요청이 만료되었거나 이미 사용되었습니다. 다시 시작해 주세요.',
  kakao_unavailable: '카카오 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
  kakao_failed: '카카오 로그인 정보를 안전하게 검증하지 못했습니다. 다시 시도해 주세요.',
  account_disabled: '사용할 수 없는 FinWatch 계정입니다.',
  session_unavailable: '로그인 세션 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.',
}

export function LoginPage({ apiState, returnTo, oauthError, onLogin }: Props) {
  const [email, setEmail] = useState('admin@finwatch.local')
  const [password, setPassword] = useState('FinWatchAdmin123!')
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [kakaoEnabled, setKakaoEnabled] = useState<boolean | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    getKakaoLoginStatus(controller.signal)
      .then((status) => setKakaoEnabled(status.enabled))
      .catch(() => setKakaoEnabled(false))
    return () => controller.abort()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setErrorMessage('')
    try {
      onLogin(await login(email, password))
    } catch (error: unknown) {
      setErrorMessage(error instanceof Error ? error.message : '로그인에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  function fillDemoAccount(account: typeof demoAccounts[number]) {
    setEmail(account.email)
    setPassword(account.password)
    setErrorMessage('')
  }

  function startKakaoLogin() {
    if (!kakaoEnabled || apiState !== 'connected') return
    window.location.assign(`/api/v1/auth/kakao/authorize?returnTo=${encodeURIComponent(returnTo)}`)
  }

  const displayedError = errorMessage || (oauthError ? oauthErrors[oauthError] ?? '소셜 로그인에 실패했습니다.' : '')
  const kakaoButtonLabel = kakaoEnabled === null
    ? '카카오 로그인 확인 중'
    : kakaoEnabled
      ? '카카오 로그인'
      : '카카오 로그인 설정 필요'

  return (
    <main className="login-shell">
      <section className="login-card card">
        <div className="login-brand">
          <img className="login-brand-logo" src="/finwatch-logo-dark.png" alt="FinWatch — 투자를 더 스마트하게, 정보를 한눈에" />
        </div>

        <div className="login-heading">
          <p className="eyebrow">SECURE ACCESS</p>
          <h1>투자 정보 대시보드 로그인</h1>
          <p>HttpOnly 보안 세션으로 사용자 기능과 관리자 AI 운영 지표를 안전하게 분리합니다.</p>
        </div>

        <div className="kakao-login-section">
          <button
            type="button"
            className="kakao-login-button"
            onClick={startKakaoLogin}
            disabled={apiState !== 'connected' || kakaoEnabled !== true}
            aria-label={kakaoButtonLabel}
            aria-describedby="kakao-login-help"
          >
            <img
              src="/kakao-login-large-wide.png"
              alt=""
              width="600"
              height="90"
            />
          </button>
          <p id="kakao-login-help">카카오 비밀번호와 토큰은 FinWatch 브라우저 저장소에 저장되지 않습니다.</p>
        </div>

        <div className="login-divider login-method-divider"><span>또는 데모로 둘러보기</span></div>

        <form onSubmit={submit} aria-describedby={`login-api-help${displayedError ? ' login-error' : ''}`}>
          <label htmlFor="login-email">
            <span>이메일</span>
            <input id="login-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" aria-invalid={Boolean(displayedError)} aria-describedby={displayedError ? 'login-error' : undefined} required />
          </label>
          <label htmlFor="login-password">
            <span>비밀번호</span>
            <input id="login-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" aria-invalid={Boolean(displayedError)} aria-describedby={displayedError ? 'login-error' : undefined} required />
          </label>
          {displayedError && <p className="login-error" id="login-error" role="alert">{displayedError}</p>}
          <p className="login-api-help" id="login-api-help">
            {apiState === 'connected' ? 'API 연결이 확인되어 로그인할 수 있습니다.' : apiState === 'checking' ? 'API 연결을 확인한 뒤 로그인 버튼이 활성화됩니다.' : 'API 서버 연결이 필요합니다. 서버를 실행한 뒤 다시 확인해 주세요.'}
          </p>
          <button type="submit" disabled={submitting || apiState !== 'connected'} title={apiState === 'connected' ? undefined : 'API 연결 확인 후 로그인할 수 있습니다.'}>
            {submitting ? '인증 중…' : apiState === 'offline' ? 'API 서버 연결 필요' : apiState === 'checking' ? '연결 확인 중…' : '로그인'}
          </button>
        </form>

        <div className="demo-account-section">
          <div className="login-divider"><span>시연 계정 선택</span></div>
          <div className="demo-account-grid">
            {demoAccounts.map((account) => (
              <button type="button" key={account.role} onClick={() => fillDemoAccount(account)}>
                <span>{account.label}</span><strong>{account.role}</strong><small>{account.email}</small>
              </button>
            ))}
          </div>
        </div>

        <div className={`login-api-status ${apiState}`} role="status" aria-live="polite">
          <span className="status-dot" />
          {apiState === 'connected' ? 'FinWatch API 연결됨' : apiState === 'checking' ? 'API 연결 확인 중' : 'API 서버에 연결할 수 없음'}
        </div>
      </section>
      <p className="login-disclaimer">FinWatch의 분석 정보는 투자 권유가 아닌 참고 정보입니다. <a href="/privacy">개인정보 처리 안내</a></p>
    </main>
  )
}
