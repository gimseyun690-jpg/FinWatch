export function isUnifiedDomesticSource(source: string | null | undefined) {
  return (source ?? '').trim().toUpperCase().includes('KIS_UNIFIED')
}

export function marketSourceLabel(source: string | null | undefined) {
  const original = (source ?? '').trim()
  const normalized = original.toUpperCase()

  if (normalized.includes('KIS_UNIFIED')) return 'KIS · KRX+NXT 통합'
  if (normalized.includes('KIS_NXT')) return 'KIS · NXT'
  if (normalized.includes('KIS_KRX') || normalized === 'KIS') return 'KIS · KRX'
  if (normalized.includes('KIS_OVERSEAS')) return 'KIS 해외 · 실제 데이터'
  if (normalized === 'FINNHUB' || normalized.startsWith('FINNHUB_')) return 'Finnhub · 실제 데이터'
  if (normalized === 'DEMO') return 'DEMO · 샘플 데이터'
  if (normalized === 'LIVE') return 'WebSocket · 실시간'
  if (normalized === 'MIXED') return '혼합 출처'
  return original || '출처 확인 중'
}
