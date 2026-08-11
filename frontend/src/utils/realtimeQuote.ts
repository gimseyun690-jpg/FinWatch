import type { LiveQuote, MarketSessionStatus } from '../types/realtime'

const STREAM_SNAPSHOT_SKEW_MS = 2 * 60 * 1_000

export type QuoteReference = {
  asOf?: string | null
  source?: string | null
  sessionStatus?: MarketSessionStatus | string | null
}

function timestamp(value?: string | null) {
  if (!value) return Number.NaN
  return Date.parse(value)
}

function isStreamingSource(value?: QuoteReference | null) {
  if (value == null) return false
  const source = value.source?.trim().toUpperCase() ?? ''
  const session = value.sessionStatus?.trim().toUpperCase().replace(/[\s-]+/g, '_') ?? ''
  return source === 'WS'
    || source.includes('_WS')
    || source.includes('WEBSOCKET')
    || session === 'LIVE'
    || session === 'OPEN'
    || session === 'REGULAR'
    || session === 'MARKET_OPEN'
    || session === 'US_DAYTIME'
    || session === 'PRE_MARKET'
    || session === 'PREMARKET'
    || session === 'AFTER_MARKET'
    || session === 'AFTER_HOURS'
    || session === 'POST_MARKET'
    || session === 'POSTMARKET'
}

/**
 * Streaming trades are authoritative over REST snapshots. REST responses can be
 * fetched after a trade while still carrying an older market observation, so a
 * plain timestamp comparison can make the UI jump back to the snapshot price.
 */
export function shouldPreferQuote(current: QuoteReference | null | undefined, incoming: QuoteReference) {
  if (current == null) return true

  const currentStreaming = isStreamingSource(current)
  const incomingStreaming = isStreamingSource(incoming)
  const currentTimestamp = timestamp(current.asOf)
  const incomingTimestamp = timestamp(incoming.asOf)
  if (currentStreaming !== incomingStreaming) {
    if (Number.isFinite(currentTimestamp) && Number.isFinite(incomingTimestamp)) {
      return incomingStreaming
        ? incomingTimestamp + STREAM_SNAPSHOT_SKEW_MS >= currentTimestamp
        : incomingTimestamp > currentTimestamp + STREAM_SNAPSHOT_SKEW_MS
    }
    return incomingStreaming
  }

  if (Number.isFinite(currentTimestamp) && Number.isFinite(incomingTimestamp)) {
    return incomingTimestamp >= currentTimestamp
  }
  if (Number.isFinite(incomingTimestamp)) return true
  if (Number.isFinite(currentTimestamp)) return false
  return true
}

export function preferredQuote(current: LiveQuote | undefined, incoming: LiveQuote): LiveQuote {
  return shouldPreferQuote(current, incoming) ? incoming : (current ?? incoming)
}

export function canApplyLiveQuote(
  quote: LiveQuote | undefined,
  reference?: QuoteReference | null,
): quote is LiveQuote {
  return quote != null
    && Number.isFinite(quote.price)
    && shouldPreferQuote(reference, quote)
}
