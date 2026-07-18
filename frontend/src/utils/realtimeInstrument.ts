const UNKNOWN_MARKET = 'UNKNOWN'

function normalize(value: string | null | undefined) {
  return value?.trim().toUpperCase() ?? ''
}

/**
 * Realtime state must always be addressed by market and symbol together. A payload
 * without a market is isolated under UNKNOWN instead of being aliased to a selected
 * market, which prevents legacy frames from contaminating another instrument.
 */
export function realtimeInstrumentKey(market: string | null | undefined, symbol: string) {
  return `${normalize(market) || UNKNOWN_MARKET}:${normalize(symbol)}`
}
