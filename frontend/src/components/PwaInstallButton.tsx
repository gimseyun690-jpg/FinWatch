import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

type InstallPromptEvent = Event & {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform?: string }>
}

type InstallMethod = 'native' | 'ios'

type PwaInstallContextValue = {
  available: boolean
  installing: boolean
  method: InstallMethod | null
  mobile: boolean
  noticeOpen: boolean
  dismissNotice: () => void
  install: () => Promise<void>
  openNotice: () => void
}

const DISMISS_KEY = 'finwatch.pwa-install-dismissed-at'
const DISMISS_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1_000
const PwaInstallContext = createContext<PwaInstallContextValue | null>(null)

function standaloneMode() {
  const iosNavigator = navigator as Navigator & { standalone?: boolean }
  return window.matchMedia('(display-mode: standalone)').matches || iosNavigator.standalone === true
}

function iosDevice() {
  return /iphone|ipad|ipod/i.test(navigator.userAgent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

function recentlyDismissed() {
  try {
    const dismissedAt = Number(localStorage.getItem(DISMISS_KEY))
    return Number.isFinite(dismissedAt) && dismissedAt > 0 && Date.now() - dismissedAt < DISMISS_COOLDOWN_MS
  } catch {
    return false
  }
}

function rememberDismissal() {
  try {
    localStorage.setItem(DISMISS_KEY, String(Date.now()))
  } catch {
    // Private browsing can reject storage writes; the prompt still remains dismissible.
  }
}

function forgetDismissal() {
  try {
    localStorage.removeItem(DISMISS_KEY)
  } catch {
    // Storage availability does not affect installation.
  }
}

export function PwaInstallProvider({ children }: { children: ReactNode }) {
  const [installPrompt, setInstallPrompt] = useState<InstallPromptEvent | null>(null)
  const [installed, setInstalled] = useState(standaloneMode)
  const [ios, setIos] = useState(() => iosDevice())
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 900px)').matches)
  const [noticeOpen, setNoticeOpen] = useState(false)
  const [installing, setInstalling] = useState(false)

  useEffect(() => {
    const mobileQuery = window.matchMedia('(max-width: 900px)')
    const standaloneQuery = window.matchMedia('(display-mode: standalone)')
    const capturePrompt = (event: Event) => {
      event.preventDefault()
      setInstallPrompt(event as InstallPromptEvent)
    }
    const handleInstalled = () => {
      setInstalled(true)
      setInstallPrompt(null)
      setNoticeOpen(false)
      forgetDismissal()
    }
    const handleMobileChange = (event: MediaQueryListEvent) => setMobile(event.matches)
    const handleStandaloneChange = (event: MediaQueryListEvent) => {
      setInstalled(event.matches || (navigator as Navigator & { standalone?: boolean }).standalone === true)
    }

    setIos(iosDevice())
    setMobile(mobileQuery.matches)
    setInstalled(standaloneMode())
    window.addEventListener('beforeinstallprompt', capturePrompt)
    window.addEventListener('appinstalled', handleInstalled)
    mobileQuery.addEventListener('change', handleMobileChange)
    standaloneQuery.addEventListener('change', handleStandaloneChange)
    return () => {
      window.removeEventListener('beforeinstallprompt', capturePrompt)
      window.removeEventListener('appinstalled', handleInstalled)
      mobileQuery.removeEventListener('change', handleMobileChange)
      standaloneQuery.removeEventListener('change', handleStandaloneChange)
    }
  }, [])

  const method: InstallMethod | null = installed ? null : installPrompt ? 'native' : ios ? 'ios' : null

  useEffect(() => {
    if (!mobile || method == null || recentlyDismissed()) return
    const timer = window.setTimeout(() => setNoticeOpen(true), 650)
    return () => window.clearTimeout(timer)
  }, [method, mobile])

  const dismissNotice = useCallback(() => {
    setNoticeOpen(false)
    rememberDismissal()
  }, [])

  const openNotice = useCallback(() => setNoticeOpen(true), [])

  const install = useCallback(async () => {
    if (installPrompt == null || installing) return
    setInstalling(true)
    try {
      await installPrompt.prompt()
      const choice = await installPrompt.userChoice
      setInstallPrompt(null)
      setNoticeOpen(false)
      if (choice.outcome === 'dismissed') rememberDismissal()
    } catch {
      // A deferred prompt is one-shot. Hide it safely if the browser rejects it.
      setInstallPrompt(null)
      setNoticeOpen(false)
    } finally {
      setInstalling(false)
    }
  }, [installPrompt, installing])

  const value = useMemo<PwaInstallContextValue>(() => ({
    available: method != null,
    installing,
    method,
    mobile,
    noticeOpen: noticeOpen && mobile && method != null,
    dismissNotice,
    install,
    openNotice,
  }), [dismissNotice, install, installing, method, mobile, noticeOpen, openNotice])

  return <PwaInstallContext.Provider value={value}>{children}</PwaInstallContext.Provider>
}

function usePwaInstall() {
  const value = useContext(PwaInstallContext)
  if (value == null) throw new Error('PwaInstallProvider is required')
  return value
}

export function PwaInstallButton({ onBeforeOpen }: { onBeforeOpen?: () => void } = {}) {
  const { available, install, installing, method, mobile, openNotice } = usePwaInstall()
  if (!available) return null
  const activate = () => {
    onBeforeOpen?.()
    if (!mobile && method === 'native') {
      void install()
      return
    }
    openNotice()
  }
  return <button type="button" className="pwa-install-button" onClick={activate} disabled={installing}>앱 설치</button>
}

export function PwaInstallNotice() {
  const { dismissNotice, install, installing, method, noticeOpen } = usePwaInstall()
  if (!noticeOpen || method == null) return null

  const ios = method === 'ios'
  return (
    <aside className="pwa-install-notice" role="region" aria-labelledby="pwa-install-title" aria-describedby="pwa-install-description">
      <img src="/finwatch-icon-64.png" alt="" aria-hidden="true" />
      <div className="pwa-install-copy">
        <small>FINWATCH APP</small>
        <h2 id="pwa-install-title">홈 화면에 FinWatch 설치</h2>
        <p id="pwa-install-description">
          {ios
            ? <>Safari의 <strong>공유</strong> 버튼을 누른 뒤 <strong>홈 화면에 추가</strong>를 선택하세요.</>
            : '앱처럼 빠르게 열고 전체 화면으로 편하게 이용할 수 있어요.'}
        </p>
      </div>
      <button type="button" className="pwa-install-close" aria-label="설치 안내 닫기" onClick={dismissNotice}>×</button>
      <div className="pwa-install-actions">
        <button type="button" className="pwa-install-later" onClick={dismissNotice}>나중에</button>
        {ios
          ? <button type="button" className="pwa-install-primary" onClick={dismissNotice}>확인</button>
          : <button type="button" className="pwa-install-primary" onClick={() => void install()} disabled={installing}>{installing ? '설치 창 여는 중…' : '홈 화면에 설치'}</button>}
      </div>
    </aside>
  )
}
