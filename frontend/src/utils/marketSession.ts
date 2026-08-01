export type MarketSessionPhase = 'PRE_MARKET' | 'REGULAR' | 'AFTER_HOURS' | 'CLOSED'

export type MarketSessionInfo = {
  phase: MarketSessionPhase
  label: '프리마켓' | '정규장' | '애프터마켓' | '장 마감'
  streaming: boolean
}

const usMarkets = new Set(['NASDAQ', 'NYSE', 'AMEX', 'ARCA', 'US'])

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

export function marketSessionInfo(market: string, status?: string | null): MarketSessionInfo | null {
  if (!usMarkets.has(market.trim().toUpperCase())) return null
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
