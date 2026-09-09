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
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul', weekday: 'short', hour: 'numeric', minute: 'numeric', second: 'numeric', hour12: false,
  })
  const parts = Object.fromEntries(formatter.formatToParts(date).map((part) => [part.type, part.value]))
  const hour = Number.parseInt(parts.hour, 10) % 24
  const seconds = (hour * 60 + Number.parseInt(parts.minute, 10)) * 60 + Number.parseInt(parts.second, 10)
  if (parts.weekday === 'Sat' || parts.weekday === 'Sun') return { phase: 'CLOSED', label: '장 마감', streaming: false }
  if (seconds >= 8 * 3600 && seconds < 9 * 3600) return { phase: 'PRE_MARKET', label: '프리마켓', streaming: true }
  if (seconds >= 9 * 3600 && seconds <= 15 * 3600 + 30 * 60) return { phase: 'REGULAR', label: '정규장', streaming: true }
  if (seconds > 15 * 3600 + 30 * 60 && seconds < 20 * 3600) return { phase: 'AFTER_HOURS', label: '애프터마켓', streaming: true }
  return { phase: 'CLOSED', label: '장 마감', streaming: false }
}

function resolveUsSessionFromTime(date: Date): MarketSessionInfo {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York', weekday: 'short', hour: 'numeric', minute: 'numeric', second: 'numeric', hour12: false,
  })
  const parts = Object.fromEntries(formatter.formatToParts(date).map((part) => [part.type, part.value]))
  const hour = Number.parseInt(parts.hour, 10) % 24
  const seconds = (hour * 60 + Number.parseInt(parts.minute, 10)) * 60 + Number.parseInt(parts.second, 10)
  if (parts.weekday === 'Sat' || parts.weekday === 'Sun') return { phase: 'CLOSED', label: '장 마감', streaming: false }
  if (seconds >= 4 * 3600 && seconds < 9 * 3600 + 30 * 60) return { phase: 'PRE_MARKET', label: '프리마켓', streaming: true }
  if (seconds >= 9 * 3600 + 30 * 60 && seconds <= 16 * 3600) return { phase: 'REGULAR', label: '정규장', streaming: true }
  if (seconds > 16 * 3600 && seconds < 20 * 3600) return { phase: 'AFTER_HOURS', label: '애프터마켓', streaming: true }
  return { phase: 'CLOSED', label: '장 마감', streaming: false }
}

export function marketSessionInfo(
  market: string,
  status?: string | null,
  _asOf?: string | null,
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

  if (!resolved) resolved = isKrx ? resolveKrxSessionFromTime(new Date()) : resolveUsSessionFromTime(new Date())
  if (resolved.phase === 'REGULAR' || resolved.phase === 'CLOSED') return null
  return resolved
}

export function marketSessionClassName(phase: MarketSessionPhase) {
  return phase.toLowerCase().replace('_', '-')
}
