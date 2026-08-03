import { expect, test } from '@playwright/test'
import type { LiveQuote } from '../src/types/realtime'
import { canApplyLiveQuote, preferredQuote, shouldPreferQuote } from '../src/utils/realtimeQuote'

function quote(overrides: Partial<LiveQuote> = {}): LiveQuote {
  return {
    market: 'KRX',
    symbol: '005930',
    price: 81_000,
    change: 1_000,
    changeRate: 1.25,
    volume: 1_000_000,
    currency: 'KRW',
    asOf: '2026-08-03T01:00:00Z',
    source: 'KIS_UNIFIED_WS',
    sessionStatus: 'LIVE',
    ...overrides,
  }
}

test.describe('realtime quote precedence', () => {
  test('keeps a streaming trade when a later REST snapshot arrives', () => {
    const streaming = quote()
    const laterSnapshot = quote({
      price: 80_500,
      asOf: '2026-08-03T01:00:10Z',
      source: 'KIS_UNIFIED_REST',
      sessionStatus: 'SNAPSHOT',
    })

    expect(preferredQuote(streaming, laterSnapshot)).toBe(streaming)
  })

  test('applies a streaming trade over a later fetched REST-backed view model', () => {
    const streaming = quote({ asOf: '2026-08-03T01:00:00Z' })

    expect(canApplyLiveQuote(streaming, {
      asOf: '2026-08-03T01:00:15Z',
      source: 'KIS_UNIFIED_REST',
    })).toBe(true)
  })

  test('orders quotes by market timestamp when both have the same transport authority', () => {
    const current = quote({ asOf: '2026-08-03T01:00:02Z', price: 81_100 })
    const older = quote({ asOf: '2026-08-03T01:00:01Z', price: 81_050 })
    const newer = quote({ asOf: '2026-08-03T01:00:03Z', price: 81_150 })

    expect(shouldPreferQuote(current, older)).toBe(false)
    expect(preferredQuote(current, newer)).toBe(newer)
  })

  test('allows a fresh REST snapshot to replace a stream that is clearly stale', () => {
    const staleStream = quote({ asOf: '2026-08-03T01:00:00Z', price: 81_000 })
    const freshSnapshot = quote({
      asOf: '2026-08-03T01:03:01Z',
      price: 81_500,
      source: 'KIS_UNIFIED_REST',
      sessionStatus: 'SNAPSHOT',
    })

    expect(preferredQuote(staleStream, freshSnapshot)).toBe(freshSnapshot)
  })

  test('does not apply an invalid streaming price', () => {
    expect(canApplyLiveQuote(quote({ price: Number.NaN }), {
      asOf: '2026-08-03T01:00:15Z',
      source: 'KIS_UNIFIED_REST',
    })).toBe(false)
  })
})
