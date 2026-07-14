import { useCallback, useEffect, useState } from 'react'
import { StockDetail } from './components/StockDetail'
import { AiNewsSummary } from './components/AiNewsSummary'
import { AdminAiDashboard } from './components/AdminAiDashboard'
import { LoginPage } from './components/LoginPage'
import { WatchlistPanel } from './components/WatchlistPanel'
import { PortfolioPanel } from './components/PortfolioPanel'
import { AlertsPanel } from './components/AlertsPanel'
import { PwaInstallButton } from './components/PwaInstallButton'
import { useRealtimeQuotes } from './hooks/useRealtimeQuotes'
import { clearSession, readSession, saveSession, UNAUTHORIZED_EVENT } from './auth/session'
import type { AuthSession, LoginResponse } from './types/auth'
import type { WatchlistItem } from './types/watchlist'
import './App.css'

type ApiState = 'checking' | 'connected' | 'offline'

type HealthResponse = {
  status: string
  timestamp: string
}

function App() {
  const [apiState, setApiState] = useState<ApiState>('checking')
  const [selectedSymbol, setSelectedSymbol] = useState('000660')
  const [adminRefreshKey, setAdminRefreshKey] = useState(0)
  const [session, setSession] = useState<AuthSession | null>(() => readSession())
  const [watchlistEditorOpen, setWatchlistEditorOpen] = useState(false)
  const [watchlistCount, setWatchlistCount] = useState<number | null>(null)
  const { quotes: liveQuotes, connection: realtimeConnection, connectedProviders } = useRealtimeQuotes(session != null)

  useEffect(() => {
    const controller = new AbortController()
    const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(5_000)])
    fetch('/api/v1/health', { signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('API health check failed')
        return response.json() as Promise<HealthResponse>
      })
      .then((health) => setApiState(health.status === 'UP' ? 'connected' : 'offline'))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setApiState('offline')
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const logoutExpiredSession = () => setSession(null)
    window.addEventListener(UNAUTHORIZED_EVENT, logoutExpiredSession)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, logoutExpiredSession)
  }, [])

  const statusText = {
    checking: 'API 확인 중',
    connected: 'API 연결됨',
    offline: '데모 모드',
  }[apiState]

  const realtimeText = realtimeConnection === 'connected'
    ? `실시간 ${connectedProviders}/2`
    : realtimeConnection === 'reconnecting'
      ? '실시간 재연결'
      : realtimeConnection === 'connecting'
        ? '실시간 연결 중'
        : '실시간 끊김'

  const selectStock = useCallback((symbol: string) => {
    setSelectedSymbol(symbol)
    window.requestAnimationFrame(() => document.querySelector('#stock-detail')?.scrollIntoView({ behavior: 'smooth' }))
  }, [])

  function handleLogin(response: LoginResponse) {
    setSession(saveSession(response))
  }

  function logout() {
    clearSession()
    setSession(null)
  }

  const handleWatchlistItems = useCallback((items: WatchlistItem[]) => {
    setWatchlistCount(items.length)
  }, [])

  if (session == null) {
    return <LoginPage apiState={apiState} onLogin={handleLogin} />
  }

  return (
    <div className="app-shell" id="top">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="FinWatch 홈">
          <span className="brand-mark" aria-hidden="true">F</span>
          <span><strong>FinWatch</strong><small>PWA 투자 정보 모니터링</small></span>
        </a>
        <nav aria-label="주요 메뉴">
          <a className="active" href="#dashboard">대시보드</a>
          <a href="#watchlist">관심종목</a>
          <a href="#news">AI 뉴스</a>
          {session.user.role === 'ADMIN' && <a href="#admin">관리자</a>}
        </nav>
        <div className="topbar-actions">
          <PwaInstallButton />
          <span className={`api-status ${apiState}`}><span className="status-dot" aria-hidden="true" />{statusText}</span>
          <span className={`realtime-status ${realtimeConnection}`}>
            <span className="status-dot" aria-hidden="true" />{realtimeText}
          </span>
          <div className="user-session">
            <span><strong>{session.user.role}</strong><small>{session.user.email}</small></span>
            <button type="button" onClick={logout}>로그아웃</button>
          </div>
        </div>
      </header>

      <main id="dashboard">
        <section className="hero-copy">
          <div>
            <p className="eyebrow">PERSONAL INVESTMENT DASHBOARD</p>
            <h1>개인 투자자용 메인 대시보드</h1>
            <p>관심종목과 포트폴리오, AI 뉴스 요약을 한눈에 확인하세요.</p>
          </div>
          <button type="button" onClick={() => setWatchlistEditorOpen(true)}>+ 관심종목 추가</button>
        </section>

        <WatchlistPanel
          selectedSymbol={selectedSymbol}
          editorOpen={watchlistEditorOpen}
          onSelect={selectStock}
          onEditorOpenChange={setWatchlistEditorOpen}
          onItemsChange={handleWatchlistItems}
          liveQuotes={liveQuotes}
        />

        {watchlistCount !== 0 && <StockDetail symbol={selectedSymbol} liveQuote={liveQuotes[selectedSymbol]} />}

        <AiNewsSummary symbol="000660" onUsageRecorded={() => setAdminRefreshKey((key) => key + 1)} />

        {session.user.role === 'ADMIN' && <AdminAiDashboard refreshKey={adminRefreshKey} />}

        <section className="dashboard-grid">
          <PortfolioPanel liveQuotes={liveQuotes} />

          <div className="side-stack">
            <AlertsPanel liveQuotes={liveQuotes} />
            <article className="card">
              <div className="section-heading compact"><h2>비용 최적화 흐름</h2><span className="muted">Redis</span></div>
              <p>뉴스 ID·본문 해시·프롬프트 버전으로 캐시 키를 만들고, 같은 본문만 재사용하며 수정된 본문은 새 분석으로 생성합니다.</p>
              <div className="cache-note">요약 요청 → 캐시 확인 → 모델 호출 또는 즉시 반환 → 운영 로그 저장</div>
            </article>
          </div>
        </section>
      </main>

      <footer>기술적 신호와 AI 요약은 투자 권유가 아닌 참고 정보입니다.</footer>
    </div>
  )
}

export default App
