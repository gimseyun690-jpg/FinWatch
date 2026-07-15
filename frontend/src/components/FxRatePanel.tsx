import { useEffect, useMemo, useState } from 'react'
import { getUsdKrw, getUsdKrwHistory } from '../api/fx'
import type { FxHistory, FxRate } from '../types/fx'

const periods: FxHistory['period'][] = ['1W', '1M', '3M', '1Y']

export function FxRatePanel() {
  const [rate, setRate] = useState<FxRate | null>(null)
  const [history, setHistory] = useState<FxHistory | null>(null)
  const [period, setPeriod] = useState<FxHistory['period']>('1M')
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getUsdKrw(controller.signal).then(setRate).catch((reason: unknown) => {
      if (reason instanceof DOMException && reason.name === 'AbortError') return
      setError(reason instanceof Error ? reason.message : '환율을 불러오지 못했습니다.')
    })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!open) return
    const controller = new AbortController()
    getUsdKrwHistory(period, controller.signal).then(setHistory).catch((reason: unknown) => {
      if (reason instanceof DOMException && reason.name === 'AbortError') return
      setError(reason instanceof Error ? reason.message : '환율 이력을 불러오지 못했습니다.')
    })
    return () => controller.abort()
  }, [open, period])

  const points = useMemo(() => chartPoints(history?.items.map((item) => item.close) ?? []), [history])
  if (!rate && error) return <div className="fx-strip unavailable">USD/KRW 환율 사용 불가 · 원통화 평가는 계속 제공됩니다.</div>
  if (!rate) return <div className="fx-strip">USD/KRW 환율 확인 중…</div>

  return (
    <section className="fx-panel" aria-label="USD KRW 환율">
      <button type="button" className="fx-strip" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
        <span><b>USD/KRW</b><small>1 USD당 KRW</small></span>
        <strong>{rate.rate.toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
        <span className={(rate.changeRate ?? 0) >= 0 ? 'profit' : 'down'}>{signed(rate.changeRate)}</span>
        <span className={`fx-freshness ${rate.freshness.toLowerCase()}`}>{rate.rateType} · {rate.freshness}</span>
        <small>{rate.source} · {new Date(rate.asOf).toLocaleString('ko-KR')}</small>
        <b aria-hidden="true">{open ? '접기' : '차트 보기'}</b>
      </button>
      {open && (
        <div className="fx-detail card">
          <div className="fx-periods">
            {periods.map((value) => <button type="button" key={value} aria-pressed={period === value} onClick={() => setPeriod(value)}>{value}</button>)}
          </div>
          {points ? (
            <svg viewBox="0 0 600 120" role="img" aria-label={`${period} USD KRW 종가 추이`}>
              <polyline points={points} fill="none" stroke="currentColor" strokeWidth="3" vectorEffect="non-scaling-stroke" />
            </svg>
          ) : <p>저장된 환율 이력이 부족합니다.</p>}
          <p>원통화 가격은 유지되며 환산값에만 이 환율을 적용합니다. {error}</p>
        </div>
      )}
    </section>
  )
}

function signed(value: number | null) {
  if (value == null) return '등락 정보 없음'
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
}

function chartPoints(values: number[]) {
  if (values.length < 2) return null
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  return values.map((value, index) => `${index / (values.length - 1) * 600},${110 - (value - min) / range * 100}`).join(' ')
}
