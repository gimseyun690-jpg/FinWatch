import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import {
  Navigate,
  NavLink,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useOutletContext,
  useParams,
  useSearchParams,
} from 'react-router'
import type { ApiState, AppRouteContext } from './app/context'
import { clearLegacyTokenSession, UNAUTHORIZED_EVENT } from './auth/session'
import { AdminAiDashboard } from './components/AdminAiDashboard'
import { AiNewsSummary } from './components/AiNewsSummary'
import { AiPortfolioEvaluation } from './components/AiPortfolioEvaluation'
import { AiTechnicalExplanation } from './components/AiTechnicalExplanation'
import { AlertsPanel } from './components/AlertsPanel'
import { AppShell } from './components/AppShell'
import { DailyChangeBriefing } from './components/DailyChangeBriefing'
import { DisclosurePanel } from './components/DisclosurePanel'
import { FxRatePanel } from './components/FxRatePanel'
import { LoginPage } from './components/LoginPage'
import { PortfolioPanel } from './components/PortfolioPanel'
import { WatchlistPanel } from './components/WatchlistPanel'
import { syncExternalData } from './api/admin'
import { getSession, logout as logoutSession } from './api/auth'
import { useRealtimeQuotes } from './hooks/useRealtimeQuotes'
import type { AuthSession } from './types/auth'
import type { DataSyncResult } from './types/admin'
import type { StockRef } from './types/stock'
import type { WatchlistItem } from './types/watchlist'
import { realtimeInstrumentKey } from './utils/realtimeInstrument'
import './App.css'

type HealthResponse = { status: string; timestamp: string }

const defaultStock: StockRef = { market: 'KRX', symbol: '000660' }
const allowedMarkets = new Set(['KRX', 'NASDAQ', 'NYSE'])
const symbolPattern = /^[A-Z0-9][A-Z0-9.-]{0,19}$/
const ignoreWatchlistItemsChange = () => undefined
const StockDetail = lazy(() => import('./components/StockDetail').then((module) => ({ default: module.StockDetail })))

function validReturnTo(value: string | null) {
  return value
    && value.startsWith('/')
    && !value.startsWith('//')
    && !value.startsWith('/login')
    && !value.includes('\\')
    && !Array.from(value).some((character) => character.charCodeAt(0) < 32)
    ? value
    : '/dashboard'
}

function stockFromPath(pathname: string): StockRef | null {
  const match = pathname.match(/^\/stocks\/([^/]+)\/([^/]+)(?:\/(?:technical|content|news|disclosures|briefing))?\/?$/)
  if (!match) return null
  try {
    const market = decodeURIComponent(match[1]).toUpperCase()
    const symbol = decodeURIComponent(match[2]).toUpperCase()
    return allowedMarkets.has(market) && symbolPattern.test(symbol) ? { market, symbol } : null
  } catch {
    return null
  }
}

function App() {
  const location = useLocation()
  const navigate = useNavigate()
  const [apiState, setApiState] = useState<ApiState>('checking')
  const [selectedStock, setSelectedStock] = useState<StockRef>(() => stockFromPath(window.location.pathname) ?? defaultStock)
  const [adminRefreshKey, setAdminRefreshKey] = useState(0)
  const [session, setSession] = useState<AuthSession | null | undefined>(undefined)
  const { quotes: liveQuotes, intradayCandles, fxRate: realtimeFxRate, connection: realtimeConnection, connectedProviders } = useRealtimeQuotes(Boolean(session), selectedStock)
  const [showAdminDetails, setShowAdminDetailsState] = useState(() => localStorage.getItem('finwatch.ui.admin-details') === 'true')
  const setShowAdminDetails = useCallback((show: boolean) => {
    localStorage.setItem('finwatch.ui.admin-details', String(show))
    setShowAdminDetailsState(show)
  }, [])

  useEffect(() => {
    clearLegacyTokenSession()
    const controller = new AbortController()
    const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)])
    getSession(signal)
      .then((restored) => setSession(restored))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setSession(null)
      })
    return () => controller.abort()
  }, [])

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
    const routeStock = stockFromPath(location.pathname)
    if (routeStock) setSelectedStock(routeStock)
  }, [location.pathname])

  useEffect(() => {
    const legacyRoutes: Record<string, string> = {
      '#dashboard': '/dashboard',
      '#watchlist': '/watchlist',
      '#news': '/news',
      '#admin': '/admin/ai',
    }
    const replacement = legacyRoutes[location.hash]
    if (replacement) navigate(replacement, { replace: true })
  }, [location.hash, navigate])

  useEffect(() => {
    const logoutExpiredSession = () => setSession(null)
    window.addEventListener(UNAUTHORIZED_EVENT, logoutExpiredSession)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, logoutExpiredSession)
  }, [])

  const selectStock = useCallback((stock: StockRef) => {
    const next = { market: stock.market.toUpperCase(), symbol: stock.symbol.toUpperCase(), stockId: stock.stockId }
    setSelectedStock(next)
    const suffix = location.pathname.match(/^\/stocks\/[^/]+\/[^/]+\/(technical|news|disclosures|briefing)\/?$/)?.[1]
    navigate(`/stocks/${encodeURIComponent(next.market)}/${encodeURIComponent(next.symbol)}${suffix ? `/${suffix}` : ''}`)
  }, [location.pathname, navigate])

  const logout = useCallback(() => {
    const returnTo = location.pathname + location.search
    void logoutSession().finally(() => {
      setSession(null)
      navigate(`/login?returnTo=${encodeURIComponent(returnTo)}`, { replace: true })
    })
  }, [location.pathname, location.search, navigate])

  const recordAiUsage = useCallback(() => {
    setAdminRefreshKey((key) => key + 1)
  }, [])

  const context = useMemo<AppRouteContext | null>(() => session == null ? null : ({
    apiState,
    session,
    selectedStock,
    liveQuotes,
    intradayCandles,
    realtimeFxRate,
    realtimeConnection,
    connectedProviders,
    adminRefreshKey,
    selectStock,
    recordAiUsage,
    logout,
    showAdminDetails,
    setShowAdminDetails,
  }), [apiState, session, selectedStock, liveQuotes, intradayCandles, realtimeFxRate, realtimeConnection, connectedProviders, adminRefreshKey, selectStock, recordAiUsage, logout, showAdminDetails, setShowAdminDetails])

  if (session === undefined) {
    return <main className="session-bootstrap" role="status"><span className="search-spinner" aria-hidden="true" /><strong>보안 세션을 확인하는 중입니다.</strong></main>
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginRoute apiState={apiState} session={session} />} />
      <Route path="/privacy" element={<PrivacyPage />} />
      <Route element={<AuthGuard session={session} />}>
        <Route element={context ? <AppShell context={context} /> : <Outlet />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="stocks" element={<StockSearchPage />} />
          <Route path="stocks/:market/:symbol" element={<StockRouteLayout />}>
            <Route index element={<Navigate to="technical" replace />} />
            <Route path="technical" element={<StockTechnicalPage />} />
            <Route path="content" element={<StockLegacyContentRedirect />} />
            <Route path="news" element={<StockNewsPage />} />
            <Route path="disclosures" element={<StockDisclosuresPage />} />
            <Route path="briefing" element={<StockBriefingPage />} />
          </Route>
          <Route path="content" element={<Navigate to="/news" replace />} />
          <Route path="news" element={<NewsPage />} />
          <Route path="disclosures" element={<DisclosuresPage />} />
          <Route path="watchlist" element={<WatchlistPage />} />
          <Route path="portfolio" element={<PortfolioPage />} />
          <Route path="alerts" element={<AlertsPage />} />
          <Route element={<AdminGuard session={session} />}>
            <Route path="admin/ai" element={<AdminAiPage />} />
            <Route path="admin/data" element={<AdminDataPage />} />
          </Route>
          <Route path="forbidden" element={<ForbiddenPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to={session ? '/dashboard' : '/login'} replace />} />
    </Routes>
  )
}

function LoginRoute({ apiState, session }: { apiState: ApiState; session: AuthSession | null }) {
  const [searchParams] = useSearchParams()
  const returnTo = validReturnTo(searchParams.get('returnTo'))
  if (session) return <Navigate to={returnTo} replace />
  return <LoginPage apiState={apiState} returnTo={returnTo} oauthError={searchParams.get('error')} />
}

function AuthGuard({ session }: { session: AuthSession | null }) {
  const location = useLocation()
  if (session) return <Outlet />
  const returnTo = encodeURIComponent(location.pathname + location.search)
  return <Navigate to={`/login?returnTo=${returnTo}`} replace />
}

function AdminGuard({ session }: { session: AuthSession | null }) {
  const context = useAppContext()
  return session?.user.role === 'ADMIN' ? <Outlet context={context} /> : <Navigate to="/forbidden" replace />
}

function useAppContext() {
  return useOutletContext<AppRouteContext>()
}

function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: React.ReactNode }) {
  return <div className="route-page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>{action}</div>
}

function DashboardPage() {
  const context = useAppContext()
  const [editorOpen, setEditorOpen] = useState(false)
  const [watchlistCount, setWatchlistCount] = useState<number | null>(null)
  const updateWatchlistCount = useCallback((items: WatchlistItem[]) => {
    setWatchlistCount(items.length)
  }, [])
  return (
    <>
      <PageHeading eyebrow="PERSONAL INVESTMENT DASHBOARD" title="개인 투자자용 메인 대시보드" description="관심종목, 포트폴리오와 가격 알림을 한눈에 확인하세요." action={<button type="button" onClick={() => setEditorOpen(true)}>+ 관심종목 추가{watchlistCount == null ? '' : ` (${watchlistCount})`}</button>} />
      <FxRatePanel />
      <WatchlistPanel selectedStock={context.selectedStock} editorOpen={editorOpen} onSelect={context.selectStock} onEditorOpenChange={setEditorOpen} onItemsChange={updateWatchlistCount} liveQuotes={context.liveQuotes} />
      <div className="dashboard-grid"><PortfolioPanel liveQuotes={context.liveQuotes} /><div className="side-stack"><AlertsPanel liveQuotes={context.liveQuotes} />{context.showAdminDetails && <article className="card"><div className="section-heading compact"><h2>비용 최적화 흐름</h2><span className="muted">Redis</span></div><p>AI 결과를 입력 해시와 프롬프트 버전으로 구분해 재사용하고 호출 비용을 기록합니다.</p><div className="cache-note">요약 요청 → 캐시 확인 → 모델 호출 또는 즉시 반환 → 운영 로그 저장</div></article>}</div></div>
    </>
  )
}

function StockSearchPage() {
  const context = useAppContext()
  const [editorOpen, setEditorOpen] = useState(true)
  return (
    <>
      <PageHeading eyebrow="STOCK DISCOVERY" title="종목 탐색" description="상단 검색이나 관심종목 검색에서 국내·미국 종목을 찾아 상세 화면을 여세요." />
      <WatchlistPanel selectedStock={context.selectedStock} editorOpen={editorOpen} onSelect={context.selectStock} onEditorOpenChange={setEditorOpen} onItemsChange={ignoreWatchlistItemsChange} liveQuotes={context.liveQuotes} />
    </>
  )
}

function StockRouteLayout() {
  const context = useAppContext()
  const params = useParams()
  const market = (params.market ?? '').toUpperCase()
  const symbol = (params.symbol ?? '').toUpperCase()
  const stock = useMemo<StockRef>(() => ({ market, symbol }), [market, symbol])
  if (!allowedMarkets.has(market) || !symbolPattern.test(symbol)) return <InvalidStockPage />
  const base = `/stocks/${encodeURIComponent(market)}/${encodeURIComponent(symbol)}`
  const stockContext = { ...context, selectedStock: stock }
  return (
    <section className="stock-route-page">
      <PageHeading eyebrow="STOCK WORKSPACE" title={`${market} · ${symbol}`} description="동일한 종목 문맥을 유지하며 기술분석, 뉴스, 공시와 브리핑을 확인합니다." />
      <nav className="stock-route-tabs" aria-label={`${symbol} 상세 메뉴`}>
        <NavLink to={`${base}/technical`}>차트·기술분석</NavLink><NavLink to={`${base}/news`}>뉴스</NavLink><NavLink to={`${base}/disclosures`}>공시</NavLink><NavLink to={`${base}/briefing`}>AI 브리핑</NavLink>
      </nav>
      <Outlet context={stockContext} />
    </section>
  )
}

function StockTechnicalPage() {
  const context = useAppContext()
  const realtimeKey = realtimeInstrumentKey(context.selectedStock.market, context.selectedStock.symbol)
  return <><Suspense fallback={<StockDetailLoading />}><StockDetail stockRef={context.selectedStock} liveQuote={context.liveQuotes[realtimeKey]} liveCandles={context.intradayCandles[realtimeKey]} /></Suspense><AiTechnicalExplanation market={context.selectedStock.market} symbol={context.selectedStock.symbol} onUsageRecorded={context.recordAiUsage} /></>
}

function StockDetailLoading() {
  return <section className="card detail-loading detail-skeleton" role="status" aria-live="polite"><span className="skeleton-line" aria-hidden="true" /><strong>차트 모듈을 불러오는 중입니다.</strong></section>
}

function StockLegacyContentRedirect() {
  const context = useAppContext()
  return <Navigate to={`/stocks/${encodeURIComponent(context.selectedStock.market)}/${encodeURIComponent(context.selectedStock.symbol)}/news`} replace />
}

function StockNewsPage() {
  const context = useAppContext()
  return <AiNewsSummary market={context.selectedStock.market} symbol={context.selectedStock.symbol} onUsageRecorded={context.recordAiUsage} />
}

function StockDisclosuresPage() {
  const context = useAppContext()
  return <DisclosurePanel stock={context.selectedStock} onUsageRecorded={context.recordAiUsage} />
}

function StockBriefingPage() {
  const context = useAppContext()
  return <DailyChangeBriefing market={context.selectedStock.market} symbol={context.selectedStock.symbol} onUsageRecorded={context.recordAiUsage} />
}

function NewsPage() {
  const context = useAppContext()
  return <AiNewsSummary onUsageRecorded={context.recordAiUsage} watchlistMode={true} />
}

function DisclosuresPage() {
  const context = useAppContext()
  return <DisclosurePanel onUsageRecorded={context.recordAiUsage} watchlistMode={true} />
}

function WatchlistPage() {
  const context = useAppContext()
  const [editorOpen, setEditorOpen] = useState(false)
  return <><PageHeading eyebrow="MY WATCHLIST" title="관심종목 관리" description="종목 수 제한 없이 검색해 추가하고 실시간 가격을 확인하세요." action={<button type="button" onClick={() => setEditorOpen(true)}>+ 종목 추가</button>} /><WatchlistPanel selectedStock={context.selectedStock} editorOpen={editorOpen} onSelect={context.selectStock} onEditorOpenChange={setEditorOpen} onItemsChange={ignoreWatchlistItemsChange} liveQuotes={context.liveQuotes} /></>
}

function PortfolioPage() {
  const context = useAppContext()
  const [portfolioRevision, setPortfolioRevision] = useState(0)
  const [portfolioHoldingCount, setPortfolioHoldingCount] = useState<number | null>(null)
  return <><PageHeading eyebrow="PORTFOLIO" title="포트폴리오" description="보유 수량과 매입 단가를 기준통화로 환산하고 자산 구성의 균형을 점검합니다." /><PortfolioPanel liveQuotes={context.liveQuotes} onPortfolioChanged={() => setPortfolioRevision((value) => value + 1)} onPortfolioLoaded={setPortfolioHoldingCount} /><AiPortfolioEvaluation portfolioRevision={portfolioRevision} holdingCount={portfolioHoldingCount} onUsageRecorded={context.recordAiUsage} /></>
}

function AlertsPage() {
  const context = useAppContext()
  return <><PageHeading eyebrow="PRICE ALERTS" title="가격 알림" description="관심 가격 조건을 저장하고 실시간 시세 기준 충족 여부를 확인하세요." /><AlertsPanel liveQuotes={context.liveQuotes} /></>
}

function AdminAiPage() {
  const context = useAppContext()
  return <><PageHeading eyebrow="AI OPERATIONS" title="AI 사용량·비용" description="모델 호출, 토큰, 캐시 적중률과 예상 절감액을 로그로 확인합니다." /><AdminAiDashboard refreshKey={context.adminRefreshKey} /></>
}

function AdminDataPage() {
  const [syncing, setSyncing] = useState(false)
  const [result, setResult] = useState<DataSyncResult | null>(null)
  const [error, setError] = useState('')
  async function sync() {
    setSyncing(true)
    setError('')
    try { setResult(await syncExternalData()) } catch (caught: unknown) { setError(caught instanceof Error ? caught.message : '데이터 동기화에 실패했습니다.') } finally { setSyncing(false) }
  }
  return <><PageHeading eyebrow="DATA OPERATIONS" title="데이터 수집 상태" description="KIS·NAVER API HUB·Finnhub·Open DART 공급자 동기화를 실행하고 결과를 확인합니다." action={<button type="button" onClick={() => void sync()} disabled={syncing}>{syncing ? '동기화 중…' : '외부 데이터 동기화'}</button>} /><section className="card data-operations-card">{error && <p className="feed-error" role="alert">{error}</p>}{!result && !error && <p>동기화를 실행하면 공급자별 반영 결과가 여기에 표시됩니다.</p>}{result && <><div className="data-sync-summary"><span>모드 <strong>{result.mode}</strong></span><span>시세 <strong>{result.pricesImported}건</strong></span><span>뉴스 <strong>{result.newsImported}건</strong></span></div><ul>{result.stocks.map((stock) => <li key={`${stock.market}:${stock.symbol}`}><strong>{stock.market} {stock.symbol}</strong><span>시세 {stock.marketPrices.status} · {stock.marketPrices.provider}</span><span>뉴스 {stock.news.status} · {stock.news.provider}</span></li>)}</ul></>}</section></>
}

function InvalidStockPage() {
  return <section className="route-state-page"><p className="eyebrow">INVALID STOCK</p><h1>종목 주소를 확인해 주세요.</h1><p>지원 시장은 KRX, NASDAQ, NYSE이며 심볼 형식이 올바른지 확인해야 합니다.</p><NavLink to="/stocks">종목 탐색으로 이동</NavLink></section>
}

function ForbiddenPage() {
  return <section className="route-state-page"><p className="eyebrow">403 FORBIDDEN</p><h1>관리자 권한이 필요합니다.</h1><p>일반 사용자는 AI 운영·데이터 수집 관리 화면에 접근할 수 없습니다.</p><NavLink to="/dashboard">대시보드로 이동</NavLink></section>
}

function PrivacyPage() {
  return <main className="privacy-page"><section className="card"><p className="eyebrow">PRIVACY</p><h1>FinWatch 개인정보 처리 안내</h1><p>카카오 로그인에서는 계정 식별자(sub)를 필수로 사용하고, 동의한 경우에만 닉네임과 프로필 이미지를 사용합니다. 이메일은 계정 자동 병합이나 관리자 권한 부여에 사용하지 않습니다.</p><h2>이용 목적과 보관</h2><p>수집 정보는 로그인, 사용자별 관심종목·포트폴리오·알림 분리에만 사용합니다. 카카오 Access Token과 Refresh Token은 로그인 완료 후 저장하지 않으며 브라우저에는 HttpOnly FinWatch 세션 쿠키만 남습니다.</p><h2>삭제</h2><p>계정 삭제 시 관심종목, 포트폴리오, 알림과 소셜 identity를 즉시 삭제하고 사용자 계정을 익명화합니다. AI 운영 로그는 사용자 직접 식별자를 제거한 뒤 통계 목적으로만 유지할 수 있습니다.</p><p className="privacy-updated">시행일: 2026년 7월 17일</p><NavLink to="/login">로그인으로 돌아가기</NavLink></section></main>
}

function NotFoundPage() {
  return <section className="route-state-page"><p className="eyebrow">404 NOT FOUND</p><h1>요청한 화면을 찾을 수 없습니다.</h1><p>주소가 변경됐거나 존재하지 않는 FinWatch 경로입니다.</p><NavLink to="/dashboard">안전하게 대시보드로 이동</NavLink></section>
}

export default App
