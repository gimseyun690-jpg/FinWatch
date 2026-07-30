import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { getUsdKrw } from '../api/fx'
import { getAlerts, setAlertStatus } from '../api/alerts'
import type { AppRouteContext } from '../app/context'
import type { FxRate, RealtimeFxRate } from '../types/fx'
import type { PriceAlert } from '../types/alert'
import { GlobalStockSearch } from './GlobalStockSearch'
import { DataStatusBadge, type DataStatus } from './DataStatusBadge'
import { Icon, type IconName } from './Icon'
import { PwaInstallButton } from './PwaInstallButton'
import { mergeRealtimeFxRate, validRealtimeRate } from '../utils/realtimeFx'

type Props = { context: AppRouteContext }

const SIDEBAR_KEY = 'finwatch.ui.sidebar-collapsed'

const primaryNavigation: Array<{ to: string; label: string; icon: IconName; group: '홈' | '시장' | '내 투자' }> = [
  { to: '/dashboard', label: '대시보드', icon: 'home', group: '홈' },
  { to: '/stocks', label: '종목 탐색', icon: 'search', group: '시장' },
  { to: '/news', label: '뉴스', icon: 'content', group: '시장' },
  { to: '/disclosures', label: '공시', icon: 'disclosure', group: '시장' },
  { to: '/watchlist', label: '관심종목', icon: 'star', group: '내 투자' },
  { to: '/portfolio', label: '포트폴리오', icon: 'portfolio', group: '내 투자' },
  { to: '/alerts', label: '가격 알림', icon: 'alert', group: '내 투자' },
]

function routeTitle(pathname: string) {
  if (pathname === '/dashboard') return '대시보드'
  if (pathname === '/stocks') return '종목 탐색'
  if (pathname.includes('/technical')) return '차트·기술분석'
  if (/^\/stocks\/[^/]+\/[^/]+\/news/.test(pathname)) return '종목 뉴스'
  if (/^\/stocks\/[^/]+\/[^/]+\/disclosures/.test(pathname)) return '종목 공시'
  if (pathname.includes('/briefing')) return 'AI 브리핑'
  if (/^\/stocks\//.test(pathname)) return '종목 개요'
  if (pathname === '/news') return '뉴스'
  if (pathname === '/disclosures') return '공시'
  if (pathname === '/watchlist') return '관심종목'
  if (pathname === '/portfolio') return '포트폴리오'
  if (pathname === '/alerts') return '가격 알림'
  if (pathname === '/admin/ai') return 'AI 사용량'
  if (pathname === '/admin/data') return '데이터 수집 상태'
  return 'FinWatch'
}

function CompactFxTicker({ realtimeRate }: { realtimeRate: RealtimeFxRate | null }) {
  const [rate, setRate] = useState<FxRate | null>(null)
  const [failed, setFailed] = useState(false)
  const realtimeRateRef = useRef(realtimeRate)

  useEffect(() => {
    realtimeRateRef.current = realtimeRate
    if (!validRealtimeRate(realtimeRate)) return
    setRate((current) => mergeRealtimeFxRate(current, realtimeRate))
    setFailed(false)
  }, [realtimeRate])

  useEffect(() => {
    let active = true
    let inFlight = false
    let controller: AbortController | null = null

    const refresh = () => {
      if (!active || inFlight || document.visibilityState !== 'visible' || !navigator.onLine) return
      inFlight = true
      controller = new AbortController()
      getUsdKrw(controller.signal).then((nextRate) => {
        if (!active) return
        setRate(mergeRealtimeFxRate(nextRate, realtimeRateRef.current))
        setFailed(false)
      }).catch((reason: unknown) => {
        if (!active || (reason instanceof DOMException && reason.name === 'AbortError')) return
        setFailed(true)
      }).finally(() => {
        inFlight = false
      })
    }

    refresh()
    const interval = window.setInterval(refresh, 30_000)
    const resume = () => refresh()
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    window.addEventListener('online', resume)
    document.addEventListener('visibilitychange', handleVisibility)
    return () => {
      active = false
      window.clearInterval(interval)
      window.removeEventListener('online', resume)
      document.removeEventListener('visibilitychange', handleVisibility)
      controller?.abort()
    }
  }, [])

  return (
    <span className="compact-fx" title={rate ? `${rate.source} · ${new Date(rate.asOf).toLocaleString('ko-KR')}` : failed ? '환율 사용 불가' : '환율 확인 중'}>
      <small>USD/KRW</small>
      <strong>{rate ? rate.rate.toLocaleString('ko-KR', { maximumFractionDigits: 2 }) : '—'}</strong>
      {rate?.changeRate != null && <em className={rate.changeRate >= 0 ? 'up' : 'down'}>{rate.changeRate > 0 ? '+' : ''}{rate.changeRate.toFixed(2)}%</em>}
      <DataStatusBadge className="compact-fx-badge" status={rate ? failed ? 'STALE' : compactFxStatus(rate) : failed ? 'UNAVAILABLE' : 'PARTIAL'} />
    </span>
  )
}

function compactFxStatus(rate: FxRate): DataStatus {
  if (rate.freshness === 'STALE') return 'STALE'
  if (rate.rateType === 'DEMO') return 'DEMO'
  if (rate.rateType === 'REFERENCE') return 'REFERENCE'
  if (rate.rateType === 'DELAYED' || rate.freshness === 'DELAYED') return 'DELAYED'
  return 'LIVE'
}

function NavigationLink({ to, label, icon, onClick }: { to: string; label: string; icon: IconName; onClick?: () => void }) {
  return (
    <NavLink
      to={to}
      onClick={onClick}
      className={({ isActive }) => isActive ? 'shell-nav-link active' : 'shell-nav-link'}
      aria-label={label}
      title={label}
    >
      <span aria-hidden="true"><Icon name={icon} /></span><b>{label}</b>
    </NavLink>
  )
}

export function AppShell({ context }: Props) {
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === 'true')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [online, setOnline] = useState(() => navigator.onLine)
  const frameRef = useRef<HTMLDivElement>(null)
  const bottomNavRef = useRef<HTMLElement>(null)
  const drawerRef = useRef<HTMLDivElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  const drawerTriggerRef = useRef<HTMLButtonElement>(null)
  const drawerLocationRef = useRef(`${location.pathname}${location.search}`)
  const title = routeTitle(location.pathname)

  const statusText = context.apiState === 'checking' ? 'API 확인 중' : context.apiState === 'connected' ? 'API 연결됨' : 'API 연결 끊김'
  const realtimeText = context.realtimeConnection === 'connected'
    ? `실시간 ${context.connectedProviders}/2`
    : context.realtimeConnection === 'reconnecting'
      ? '실시간 재연결'
      : context.realtimeConnection === 'connecting'
        ? '실시간 연결 중'
        : '실시간 끊김'

  const [activeAlerts, setActiveAlerts] = useState<PriceAlert[]>([])
  const triggeredAlertIdsRef = useRef<Set<number>>(new Set())

  useEffect(() => {
    if (context.session) {
      getAlerts()
        .then((items) => {
          setActiveAlerts(items.filter((item) => item.status === 'ACTIVE'))
        })
        .catch(() => {})
    }
  }, [context.session, context.adminRefreshKey])

  useEffect(() => {
    if (activeAlerts.length === 0) return

    activeAlerts.forEach((alert) => {
      if (triggeredAlertIdsRef.current.has(alert.id)) return

      const key = `${alert.market.toUpperCase()}:${alert.symbol.toUpperCase()}`
      const quote = context.liveQuotes[key]
      if (!quote || quote.price == null) return

      const isAbove = alert.condition === 'ABOVE' && quote.price >= alert.targetPrice
      const isBelow = alert.condition === 'BELOW' && quote.price <= alert.targetPrice

      if (isAbove || isBelow) {
        triggeredAlertIdsRef.current.add(alert.id)

        const directionText = alert.condition === 'ABOVE' ? '이상' : '이하'

        setAlertStatus(alert.id, 'DISABLED')
          .catch(() => {})

        const formattedTarget = new Intl.NumberFormat('ko-KR', {
          style: 'currency',
          currency: alert.currency,
          maximumFractionDigits: alert.currency === 'KRW' ? 0 : 2
        }).format(alert.targetPrice)

        const formattedCurrent = new Intl.NumberFormat('ko-KR', {
          style: 'currency',
          currency: alert.currency,
          maximumFractionDigits: alert.currency === 'KRW' ? 0 : 2
        }).format(quote.price)

        setTimeout(() => {
          window.alert(`[가격 도달 알림] ${alert.name} (${alert.symbol}) 종목이 설정하신 가격 ${formattedTarget} ${directionText}에 도달했습니다!\n(실시간 현재가: ${formattedCurrent})`)
        }, 100)
      }
    })
  }, [context.liveQuotes, activeAlerts, context])

  useEffect(() => {
    document.title = `${title} | FinWatch`
  }, [title])

  useEffect(() => {
    window.requestAnimationFrame(() => {
      const heading = document.querySelector<HTMLElement>('#main-content h1')
      if (!heading) return
      heading.tabIndex = -1
      heading.focus({ preventScroll: true })
    })
  }, [location.pathname])

  useEffect(() => {
    const nextLocation = `${location.pathname}${location.search}`
    if (drawerLocationRef.current === nextLocation) return
    drawerLocationRef.current = nextLocation
    setDrawerOpen(false)
  }, [location.pathname, location.search])

  useEffect(() => {
    const handleOnline = () => setOnline(true)
    const handleOffline = () => setOnline(false)
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [])

  useEffect(() => {
    if (!drawerOpen) return
    const frame = frameRef.current
    const bottomNav = bottomNavRef.current
    const trigger = drawerTriggerRef.current
    frame?.setAttribute('inert', '')
    bottomNav?.setAttribute('inert', '')
    document.body.classList.add('drawer-open')
    window.requestAnimationFrame(() => closeRef.current?.focus())
    return () => {
      frame?.removeAttribute('inert')
      bottomNav?.removeAttribute('inert')
      document.body.classList.remove('drawer-open')
      trigger?.focus()
    }
  }, [drawerOpen])

  function toggleSidebar() {
    setCollapsed((current) => {
      localStorage.setItem(SIDEBAR_KEY, String(!current))
      return !current
    })
  }

  function trapDrawerFocus(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      setDrawerOpen(false)
      return
    }
    if (event.key !== 'Tab') return
    const focusable = [...(drawerRef.current?.querySelectorAll<HTMLElement>('a[href], button:not([disabled])') ?? [])]
    if (focusable.length === 0) return
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div className="workspace-shell">
      <div className={`workspace-frame${collapsed ? ' sidebar-collapsed' : ''}`} ref={frameRef}>
        <aside className="desktop-sidebar" aria-label="데스크톱 주 메뉴">
          <NavLink to="/dashboard" className="sidebar-brand" aria-label="FinWatch 대시보드">
            <img src="/finwatch-icon-64.png" alt="" aria-hidden="true" /><strong>Fin<span>Watch</span></strong>
          </NavLink>
          <button type="button" className="sidebar-toggle" onClick={toggleSidebar} aria-label={collapsed ? '사이드바 펼치기' : '사이드바 접기'} title={collapsed ? '사이드바 펼치기' : '사이드바 접기'}>
            <Icon name={collapsed ? 'chevron-right' : 'chevron-left'} />
          </button>
          <nav className="sidebar-navigation" aria-label="서비스 메뉴">
            {['홈', '시장', '내 투자'].map((group) => (
              <div className="nav-group" key={group}>
                <small>{group}</small>
                {primaryNavigation.filter((item) => item.group === group).map((item) => <NavigationLink key={item.to} {...item} />)}
              </div>
            ))}
            {context.session.user.role === 'ADMIN' && (
              <div className="nav-group"><small>운영</small><NavigationLink to="/admin/ai" label="AI 사용량" icon="ai" /><NavigationLink to="/admin/data" label="데이터 수집 상태" icon="sync" /></div>
            )}
          </nav>
          <div className="sidebar-footer">
            <PwaInstallButton />
            <div className="sidebar-admin-toggle" style={{ padding: '4px 12px', margin: '4px 0', borderTop: '1px solid rgba(255,255,255,0.05)', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
              <label className="admin-toggle-switch" style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.8rem', color: '#94a3b8', cursor: 'pointer', userSelect: 'none' }}>
                <input
                  type="checkbox"
                  checked={context.showAdminDetails}
                  onChange={(e) => context.setShowAdminDetails(e.target.checked)}
                  style={{ cursor: 'pointer' }}
                />
                <span>상세모드</span>
              </label>
            </div>
            <div className="sidebar-user">{context.session.user.profileImageUrl ? <img src={context.session.user.profileImageUrl} alt="" referrerPolicy="no-referrer" /> : <span aria-hidden="true">{context.session.user.displayName.slice(0, 1).toUpperCase()}</span>}<div><b>{context.session.user.displayName}</b><small>{context.session.user.email ?? `${context.session.user.authProvider} 로그인`}</small></div></div>
            <button type="button" className="shell-logout" onClick={context.logout}>로그아웃</button>
          </div>
        </aside>

        <div className="shell-main-column">
          <header className="shell-topbar">
            <div className="mobile-brand"><img src="/finwatch-icon-64.png" alt="" aria-hidden="true" /><strong>FinWatch</strong></div>
            <div className="page-context"><small>현재 화면</small><strong>{title}</strong></div>
            <GlobalStockSearch selectedStock={context.selectedStock} onSelect={context.selectStock} />
            <div className="shell-statuses" role="status" aria-live="polite">
              <CompactFxTicker realtimeRate={context.realtimeFxRate} />
              {context.showAdminDetails && (
                <>
                  <span className={`api-status ${context.apiState}`} title={statusText}><span className="status-dot" aria-hidden="true" /><b>{statusText}</b></span>
                  <span className={`realtime-status ${context.realtimeConnection}`} title={realtimeText}><span className="status-dot" aria-hidden="true" /><b>{realtimeText}</b></span>
                </>
              )}
            </div>
          </header>
          {!online && (
            <div className="global-offline-banner" role="status">
              <Icon name="wifi-off" />
              <span><strong>오프라인 상태입니다.</strong> 저장된 화면은 계속 볼 수 있고, 연결되면 최신 데이터 갱신을 다시 시도합니다.</span>
              <button type="button" onClick={() => window.location.reload()}><Icon name="refresh" size={16} /> 다시 연결</button>
            </div>
          )}
          <main className="route-content" id="main-content"><Outlet context={context} /></main>
          <footer>기술적 신호와 AI 요약은 투자 권유가 아닌 참고 정보입니다.</footer>
        </div>
      </div>

      <nav className="mobile-bottom-nav" aria-label="모바일 주 메뉴" ref={bottomNavRef}>
        <NavigationLink to="/dashboard" label="홈" icon="home" /><NavigationLink to="/stocks" label="검색" icon="search" /><NavigationLink to="/watchlist" label="관심종목" icon="star" /><NavigationLink to="/news" label="뉴스" icon="content" />
        <button ref={drawerTriggerRef} type="button" aria-label="더보기 메뉴" aria-haspopup="dialog" aria-expanded={drawerOpen} onClick={() => setDrawerOpen(true)}><span aria-hidden="true"><Icon name="more" /></span><b>더보기</b></button>
      </nav>

      {drawerOpen && (
        <>
          <button type="button" className="drawer-backdrop" aria-label="더보기 메뉴 닫기" onClick={() => setDrawerOpen(false)} />
          <div className="mobile-more-drawer" ref={drawerRef} role="dialog" aria-modal="true" aria-labelledby="more-menu-title" onKeyDown={trapDrawerFocus}>
            <div className="drawer-heading"><div><small>FINWATCH</small><h2 id="more-menu-title">더보기</h2></div><button ref={closeRef} type="button" aria-label="더보기 메뉴 닫기" onClick={() => setDrawerOpen(false)}><Icon name="close" /></button></div>
            <nav aria-label="추가 서비스 메뉴">
              <NavigationLink to="/disclosures" label="공시" icon="disclosure" onClick={() => setDrawerOpen(false)} /><NavigationLink to="/portfolio" label="포트폴리오" icon="portfolio" onClick={() => setDrawerOpen(false)} /><NavigationLink to="/alerts" label="가격 알림" icon="alert" onClick={() => setDrawerOpen(false)} />
              {context.session.user.role === 'ADMIN' && <NavigationLink to="/admin/ai" label="AI 사용량" icon="ai" onClick={() => setDrawerOpen(false)} />}
              {context.session.user.role === 'ADMIN' && <NavigationLink to="/admin/data" label="데이터 수집 상태" icon="sync" onClick={() => setDrawerOpen(false)} />}
            </nav>
            <div className="drawer-account">
              <span>{context.session.user.displayName} · {context.session.user.role}</span>
              <strong>{context.session.user.email ?? `${context.session.user.authProvider} 로그인`}</strong>
              <label className="admin-toggle-switch mobile-toggle" style={{ display: 'flex', alignItems: 'center', gap: '8px', margin: '8px 0', fontSize: '0.85rem', color: '#94a3b8', cursor: 'pointer', userSelect: 'none' }}>
                <input
                  type="checkbox"
                  checked={context.showAdminDetails}
                  onChange={(e) => context.setShowAdminDetails(e.target.checked)}
                  style={{ cursor: 'pointer' }}
                />
                <span>상세모드</span>
              </label>
              <PwaInstallButton />
              <button type="button" className="shell-logout" onClick={context.logout}>로그아웃</button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
