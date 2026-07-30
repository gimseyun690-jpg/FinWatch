import type { FxRate, RealtimeFxRate } from '../types/fx'

const MAX_LIVE_AGE_MS = 2 * 60 * 1000
const MAX_FUTURE_SKEW_MS = 5 * 60 * 1000

export function mergeRealtimeFxRate(
  reference: FxRate | null,
  realtime: RealtimeFxRate | null,
  now = Date.now(),
): FxRate | null {
  if (!validRealtimeRate(realtime, now)) return reference
  if (reference?.rateType === 'LIVE' && timestamp(reference.asOf) > timestamp(realtime.asOf)) {
    return reference
  }

  const previousClose = reference?.previousClose ?? null
  const change = previousClose == null ? null : realtime.rate - previousClose
  const changeRate = previousClose == null || previousClose === 0
    ? null
    : change! / previousClose * 100
  return {
    baseCurrency: realtime.baseCurrency,
    quoteCurrency: realtime.quoteCurrency,
    rate: realtime.rate,
    previousClose,
    change,
    changeRate,
    rateType: 'LIVE',
    source: realtime.source,
    providerSymbol: realtime.providerSymbol,
    asOf: realtime.asOf,
    fetchedAt: realtime.fetchedAt,
    freshness: 'FRESH',
  }
}

export function validRealtimeRate(
  value: RealtimeFxRate | null,
  now = Date.now(),
): value is RealtimeFxRate {
  if (value == null
      || value.baseCurrency !== 'USD'
      || value.quoteCurrency !== 'KRW'
      || value.rateType !== 'LIVE'
      || !Number.isFinite(value.rate)
      || value.rate <= 0) {
    return false
  }
  const asOf = timestamp(value.asOf)
  const fetchedAt = timestamp(value.fetchedAt)
  return Number.isFinite(asOf)
    && Number.isFinite(fetchedAt)
    && asOf <= now + MAX_FUTURE_SKEW_MS
    && asOf >= now - MAX_LIVE_AGE_MS
}

function timestamp(value: string) {
  return new Date(value).getTime()
}
