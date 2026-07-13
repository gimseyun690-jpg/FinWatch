import { useState, type FormEvent } from 'react'
import { login } from '../api/auth'
import type { LoginResponse } from '../types/auth'

type Props = {
  apiState: 'checking' | 'connected' | 'offline'
  onLogin: (response: LoginResponse) => void
}

const demoAccounts = [
  { label: '일반 사용자', email: 'user@finwatch.local', password: 'FinWatch123!', role: 'USER' },
  { label: '관리자', email: 'admin@finwatch.local', password: 'FinWatchAdmin123!', role: 'ADMIN' },
] as const

export function LoginPage({ apiState, onLogin }: Props) {
  const [email, setEmail] = useState('admin@finwatch.local')
  const [password, setPassword] = useState('FinWatchAdmin123!')
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

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

  return (
    <main className="login-shell">
      <section className="login-card card">
        <div className="login-brand">
          <span className="brand-mark" aria-hidden="true">F</span>
          <div><strong>FinWatch</strong><span>AI COST OPTIMIZED INVESTMENT MONITORING</span></div>
        </div>

        <div className="login-heading">
          <p className="eyebrow">SECURE ACCESS</p>
          <h1>투자 정보 대시보드 로그인</h1>
          <p>JWT 인증으로 사용자 기능과 관리자 AI 운영 지표를 안전하게 분리합니다.</p>
        </div>

        <form onSubmit={submit}>
          <label>
            <span>이메일</span>
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" required />
          </label>
          <label>
            <span>비밀번호</span>
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required />
          </label>
          {errorMessage && <p className="login-error" role="alert">{errorMessage}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? '인증 중…' : apiState === 'offline' ? 'API 서버 연결 필요' : '로그인'}
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

        <div className={`login-api-status ${apiState}`}>
          <span className="status-dot" />
          {apiState === 'connected' ? 'FinWatch API 연결됨' : apiState === 'checking' ? 'API 연결 확인 중' : 'API 서버에 연결할 수 없음'}
        </div>
      </section>
      <p className="login-disclaimer">FinWatch의 분석 정보는 투자 권유가 아닌 참고 정보입니다.</p>
    </main>
  )
}
