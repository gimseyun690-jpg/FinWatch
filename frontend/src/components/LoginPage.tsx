import { useEffect, useState } from 'react'
import { getKakaoLoginStatus } from '../api/auth'

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
          <img className="login-brand-logo" src="/finwatch-logo-dark.png" alt="FinWatch — 투자를 더 스마트하게, 정보를 한눈에" />
        </div>

        <div className="login-heading">
          <p className="eyebrow">SECURE ACCESS</p>
          <h1>투자 정보 대시보드 로그인</h1>
          <p>카카오 계정으로 간편하게 로그인하고 HttpOnly 보안 세션으로 서비스를 이용하세요.</p>
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
