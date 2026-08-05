export type MarketSessionPhase = 'PRE_MARKET' | 'REGULAR' | 'AFTER_HOURS' | 'CLOSED'

export type MarketSessionInfo = {
  phase: MarketSessionPhase
  label: '프리마켓' | '정규장' | '애프터마켓' | '장 마감' | '애프터장' | '장전'
  streaming: boolean
}

const usMarkets = new Set(['NASDAQ', 'NYSE', 'AMEX', 'ARCA', 'US'])
const krxMarkets = new Set(['KRX', 'KOSPI', 'KOSDAQ', 'KONEX'])

function normalizedSession(status?: string | null) {
  return status?.trim().toUpperCase().replace(/[\s-]+/g, '_') ?? ''
}

export function isStreamingSession(status?: string | null) {
  const normalized = normalizedSession(status)
  return normalized === 'LIVE'
    || normalized === 'OPEN'
    || normalized === 'PRE_MARKET'
    || normalized === 'PREMARKET'
    || normalized === 'REGULAR'
    || normalized === 'MARKET_OPEN'
    || normalized === 'AFTER_MARKET'
    || normalized === 'AFTER_HOURS'
    || normalized === 'POST_MARKET'
    || normalized === 'POSTMARKET'
}

function resolveKrxSessionFromTime(date: Date): MarketSessionInfo {
  const kstFormatter = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    weekday: 'short',
    hour: 'numeric',
    minute: 'numeric',
    second: 'numeric',
    hour12: false,
  })
  const parts = Object.fromEntries(kstFormatter.formatToParts(date).map((p) => [p.type, p.value]))
  const weekday = parts.weekday
  const hour = Number.parseInt(parts.hour, 10) % 24
  const minute = Number.parseInt(parts.minute, 10)
  const second = Number.parseInt(parts.second, 10)

  if (weekday === 'Sat' || weekday === 'Sun') {
    return { phase: 'CLOSED', label: '장 마감', streaming: false }
  }

  const timeSeconds = (hour * 60 + minute) * 60 + second
  // 08:00 ~ 08:59:59 KST: PRE_MARKET (프리마켓)
  if (timeSeconds >= 8 * 3600 && timeSeconds < 9 * 3600) {
    return { phase: 'PRE_MARKET', label: '프리마켓', streaming: true }
  }
  // 09:00 ~ 15:30:00 KST: REGULAR (정규장)
  if (timeSeconds >= 9 * 3600 && timeSeconds <= (15 * 3600 + 30 * 60)) {
    return { phase: 'REGULAR', label: '정규장', streaming: true }
  }
  // 15:30:01 ~ 20:00:00 KST: AFTER_HOURS (애프터마켓)
  if (timeSeconds > (15 * 3600 + 30 * 60) && timeSeconds < 20 * 3600) {
    return { phase: 'AFTER_HOURS', label: '애프터마켓', streaming: true }
  }
  return { phase: 'CLOSED', label: '장 마감', streaming: false }
}

function resolveUsSessionFromTime(date: Date): MarketSessionInfo {
  const etFormatter = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York',
    weekday: 'short',
    hour: 'numeric',
    minute: 'numeric',
    second: 'numeric',
    hour12: false,
  })
  const parts = Object.fromEntries(etFormatter.formatToParts(date).map((p) => [p.type, p.value]))
  const weekday = parts.weekday
  const hour = Number.parseInt(parts.hour, 10) % 24
  const minute = Number.parseInt(parts.minute, 10)
  const second = Number.parseInt(parts.second, 10)

  if (weekday === 'Sat' || weekday === 'Sun') {
    return { phase: 'CLOSED', label: '장 마감', streaming: false }
  }

  const timeSeconds = (hour * 60 + minute) * 60 + second
  // 04:00 ~ 09:29:59 ET: PRE_MARKET (프리마켓)
  if (timeSeconds >= 4 * 3600 && timeSeconds < (9 * 3600 + 30 * 60)) {
    return { phase: 'PRE_MARKET', label: '프리마켓', streaming: true }
  }
  // 09:30 ~ 16:00:00 ET: REGULAR (정규장)
  if (timeSeconds >= (9 * 3600 + 30 * 60) && timeSeconds <= 16 * 3600) {
    return { phase: 'REGULAR', label: '정규장', streaming: true }
  }
  // 16:00:01 ~ 20:00:00 ET: AFTER_HOURS (애프터마켓)
  if (timeSeconds > 16 * 3600 && timeSeconds < 20 * 3600) {
    return { phase: 'AFTER_HOURS', label: '애프터마켓', streaming: true }
  }
  return { phase: 'CLOSED', label: '장 마감', streaming: false }
}

export function marketSessionInfo(
  market: string,
  status?: string | null,
  asOf?: string | null,
): MarketSessionInfo | null {
  const normalizedMarket = market.trim().toUpperCase()
  const isUs = usMarkets.has(normalizedMarket)
  const isKrx = krxMarkets.has(normalizedMarket)

  if (!isUs && !isKrx) return null

  let resolved: MarketSessionInfo | null = null
  const normalized = normalizedSession(status)

  if (normalized && normalized !== 'SNAPSHOT' && normalized !== 'UNKNOWN') {
    switch (normalized) {
      case 'PRE_MARKET':
      case 'PREMARKET':
        resolved = { phase: 'PRE_MARKET', label: '프리마켓', streaming: true }
        break
      case 'AFTER_MARKET':
      case 'AFTER_HOURS':
      case 'POST_MARKET':
      case 'POSTMARKET':
        resolved = { phase: 'AFTER_HOURS', label: '애프터마켓', streaming: true }
        break
      case 'CLOSED':
      case 'CLOSE':
      case 'LIVE':
      case 'OPEN':
      case 'REGULAR':
      case 'MARKET_OPEN':
        return null
      default:
        break
    }
  }

  if (!resolved) {
    if (!asOf) return null
    const date = new Date(asOf)
    if (Number.isNaN(date.getTime())) return null
    // Ensure quote timestamp is recent (within 24 hours) to prevent stale trade dates
    if (Math.abs(Date.now() - date.getTime()) > 24 * 60 * 60 * 1000) return null
    resolved = isKrx ? resolveKrxSessionFromTime(date) : resolveUsSessionFromTime(date)
  }

  // Only display session badges for extended hours (PRE_MARKET or AFTER_HOURS).
  // Hide badge completely during regular trading hours and when the market is closed.
  if (resolved.phase === 'REGULAR' || resolved.phase === 'CLOSED') {
    return null
  }

  return resolved
}

export function marketSessionClassName(phase: MarketSessionPhase) {
  return phase.toLowerCase().replace('_', '-')
}
