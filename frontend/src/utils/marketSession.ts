export type MarketSessionPhase = 'PRE_MARKET' | 'REGULAR' | 'AFTER_HOURS' | 'US_DAYTIME' | 'CLOSED'

export type MarketSessionInfo = {
  phase: MarketSessionPhase
  label: '데이마켓' | '프리마켓' | '정규장' | '애프터마켓' | '장 마감'
  streaming: boolean
}

const usMarkets = new Set(['NASDAQ', 'NYSE', 'AMEX', 'ARCA', 'US'])

function normalizedSession(status?: string | null) {
  return status?.trim().toUpperCase().replace(/[\s-]+/g, '_') ?? ''
}

/**
 * The broker's US daytime/pre/regular/after sessions are presented in KST.
 * This is deliberately based on current time rather than the last trade's
 * session, so an old after-hours tick cannot label the current daytime market.
 */
function koreaUsTradingSession(now = new Date()): MarketSessionInfo | null {
  try {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: 'Asia/Seoul',
      weekday: 'short',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(now)
    const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
    const weekday = values.weekday
    const minutes = Number(values.hour) * 60 + Number(values.minute)

    if (weekday === 'Sat' || weekday === 'Sun') {
      return { phase: 'CLOSED', label: '장 마감', streaming: false }
    }
    if (minutes >= 22 * 60 + 30 || minutes < 5 * 60) {
      return { phase: 'REGULAR', label: '정규장', streaming: true }
    }
    if (minutes < 8 * 60 + 50) {
      return { phase: 'AFTER_HOURS', label: '애프터마켓', streaming: true }
    }
    if (minutes >= 9 * 60 && minutes < 16 * 60 + 50) {
      return { phase: 'US_DAYTIME', label: '데이마켓', streaming: true }
    }
    if (minutes >= 17 * 60 && minutes < 22 * 60 + 30) {
      return { phase: 'PRE_MARKET', label: '프리마켓', streaming: true }
    }
    return { phase: 'CLOSED', label: '장 마감', streaming: false }
  } catch {
    return null
  }
}

export function isStreamingSession(status?: string | null) {
  const normalized = normalizedSession(status)
  return normalized === 'LIVE'
    || normalized === 'OPEN'
    || normalized === 'PRE_MARKET'
    || normalized === 'PREMARKET'
    || normalized === 'REGULAR'
    || normalized === 'MARKET_OPEN'
    || normalized === 'US_DAYTIME'
    || normalized === 'AFTER_MARKET'
    || normalized === 'AFTER_HOURS'
    || normalized === 'POST_MARKET'
    || normalized === 'POSTMARKET'
}

export function marketSessionInfo(market: string, status?: string | null): MarketSessionInfo | null {
  if (!usMarkets.has(market.trim().toUpperCase())) return null
  const currentSession = koreaUsTradingSession()
  if (currentSession) return currentSession
  const normalized = normalizedSession(status)
  if (!normalized || normalized === 'SNAPSHOT') return null

  switch (normalized) {
    case 'CLOSED':
    case 'CLOSE':
      return { phase: 'CLOSED', label: '장 마감', streaming: false }
    case 'PRE_MARKET':
    case 'PREMARKET':
      return { phase: 'PRE_MARKET', label: '프리마켓', streaming: true }
    case 'AFTER_MARKET':
    case 'AFTER_HOURS':
    case 'POST_MARKET':
    case 'POSTMARKET':
      return { phase: 'AFTER_HOURS', label: '애프터마켓', streaming: true }
    case 'US_DAYTIME':
      return { phase: 'US_DAYTIME', label: '데이마켓', streaming: true }
    case 'LIVE':
    case 'OPEN':
    case 'REGULAR':
    case 'MARKET_OPEN':
      return { phase: 'REGULAR', label: '정규장', streaming: true }
    default:
      return null
  }
}

export function marketSessionClassName(phase: MarketSessionPhase) {
  return phase.toLowerCase().replace('_', '-')
}
