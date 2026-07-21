import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { getKakaoLoginStatus, login } from '../api/auth'

type Props = {
  apiState: 'checking' | 'connected' | 'offline'
  returnTo: string
  oauthError: string | null
}

const oauthErrors: Record<string, string> = {
  kakao_cancelled: '카카오 로그인이 취소되었습니다. 원할 때 다시 시도할 수 있습니다.',
  kakao_request_invalid: '카카오 로그인 요청이 만료되었거나 이미 사용되었습니다. 다시 시작해 주세요.',
  kakao_unavailable: '카카오 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
  kakao_failed: '카카오 로그인 정보를 안전하게 검증하지 못했습니다. 다시 시도해 주세요.',
  account_disabled: '사용할 수 없는 FinWatch 계정입니다.',
  session_unavailable: '로그인 세션 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.',
}

export function LoginPage({ apiState, returnTo, oauthError }: Props) {
  const [kakaoEnabled, setKakaoEnabled] = useState<boolean | null>(null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [localError, setLocalError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [showAdminLogin, setShowAdminLogin] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    getKakaoLoginStatus(controller.signal)
      .then((status) => setKakaoEnabled(status.enabled))
      .catch(() => setKakaoEnabled(false))
    return () => controller.abort()
  }, [])

  function startKakaoLogin() {
    if (!kakaoEnabled || apiState !== 'connected') return
    window.location.assign(`/api/v1/auth/kakao/authorize?returnTo=${encodeURIComponent(returnTo)}`)
  }

  async function handleLocalLogin(e: FormEvent) {
    e.preventDefault()
    setLocalError('')
    setSubmitting(true)
    try {
      await login(email, password)
      window.location.assign(returnTo)
    } catch (reason: unknown) {
      setLocalError(reason instanceof Error ? reason.message : '로그인 중 오류가 발생했습니다.')
      setSubmitting(false)
    }
  }

  const displayedError = oauthError ? oauthErrors[oauthError] ?? '소셜 로그인에 실패했습니다.' : ''
  const kakaoButtonLabel = kakaoEnabled === null
    ? '카카오 로그인 확인 중'
    : kakaoEnabled
      ? '카카오 로그인'
      : '카카오 로그인 설정 필요'

  return (
    <main className="login-shell">
      <section className="login-card card">
        <div className="login-brand">
          <img className="login-brand-logo" src="/finwatch-logo-dark-white.svg?v=2" alt="FinWatch" />
          <p className="login-brand-subtext">투자를 더 스마트하게, 정보를 한눈에</p>
        </div>

        <div className="login-heading">
          <h1>FinWatch 시작하기</h1>
          <p>안전한 소셜 인증으로 간편하게 대시보드를 시작하세요.</p>
        </div>

        <div className="kakao-login-section">
          {displayedError && <p className="login-error" id="login-error" role="alert">{displayedError}</p>}
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

          {!showAdminLogin ? (
            <button
              type="button"
              onClick={() => setShowAdminLogin(true)}
              style={{
                display: 'block',
                margin: '24px auto 0',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: '6px',
                padding: '8px 16px',
                color: '#94a3b8',
                backgroundColor: 'rgba(255,255,255,0.02)',
                fontSize: '0.8rem',
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
            >
              관리자 계정으로 로그인
            </button>
          ) : (
            <>
              <div className="local-login-divider" style={{ display: 'flex', alignItems: 'center', margin: '24px 0 16px', color: '#94a3b8', fontSize: '0.8rem', gap: '8px' }}>
                <span style={{ flex: 1, height: '1px', backgroundColor: 'rgba(255,255,255,0.08)' }}></span>
                <span>관리자 로그인</span>
                <span style={{ flex: 1, height: '1px', backgroundColor: 'rgba(255,255,255,0.08)' }}></span>
              </div>
              <form onSubmit={handleLocalLogin} style={{ display: 'flex', flexDirection: 'column', gap: '12px', width: '100%', maxWidth: '320px', margin: '0 auto' }}>
                {localError && <p className="login-error" role="alert" style={{ fontSize: '0.8rem', color: '#ff6f7d', margin: 0, textAlign: 'center' }}>{localError}</p>}
                <input
                  type="email"
                  placeholder="이메일 (admin@finwatch.local)"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  style={{ padding: '10px 14px', borderRadius: '6px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: 'rgba(0,0,0,0.2)', color: '#fff', fontSize: '0.9rem' }}
                />
                <input
                  type="password"
                  placeholder="비밀번호"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  style={{ padding: '10px 14px', borderRadius: '6px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: 'rgba(0,0,0,0.2)', color: '#fff', fontSize: '0.9rem' }}
                />
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    type="submit"
                    disabled={submitting}
                    style={{ flex: 1, padding: '10px', borderRadius: '6px', border: 'none', backgroundColor: '#29d4c9', color: '#0f172a', fontWeight: 'bold', fontSize: '0.9rem', cursor: 'pointer', transition: 'background-color 0.2s' }}
                  >
                    {submitting ? '로그인 중...' : '로그인'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowAdminLogin(false)}
                    style={{ padding: '10px 14px', borderRadius: '6px', border: '1px solid rgba(255,255,255,0.1)', backgroundColor: 'transparent', color: '#94a3b8', fontSize: '0.9rem', cursor: 'pointer' }}
                  >
                    취소
                  </button>
                </div>
              </form>
            </>
          )}
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
