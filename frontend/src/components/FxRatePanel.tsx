import { useEffect, useMemo, useRef, useState } from 'react'
import { useOutletContext } from 'react-router'
import type { AppRouteContext } from '../app/context'
import { getUsdKrw, getUsdKrwHistory } from '../api/fx'
import type { FxHistory, FxRate } from '../types/fx'
import { DataStatusBadge, type DataStatus } from './DataStatusBadge'
import { mergeRealtimeFxRate, validRealtimeRate } from '../utils/realtimeFx'

const periods: FxHistory['period'][] = ['1W', '1M', '3M', '1Y']

export function FxRatePanel() {
  const context = useOutletContext<AppRouteContext | null>()
  const showAdminDetails = context?.showAdminDetails ?? true
  const realtimeRate = context?.realtimeFxRate ?? null
  const [rate, setRate] = useState<FxRate | null>(null)
  const [history, setHistory] = useState<FxHistory | null>(null)
  const [period, setPeriod] = useState<FxHistory['period']>('1M')
  const [open, setOpen] = useState(false)
  const [rateError, setRateError] = useState('')
  const [historyError, setHistoryError] = useState('')
  const [rateLoading, setRateLoading] = useState(true)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [rateAttempt, setRateAttempt] = useState(0)
  const [historyAttempt, setHistoryAttempt] = useState(0)
  const realtimeRateRef = useRef(realtimeRate)

  useEffect(() => {
    realtimeRateRef.current = realtimeRate
    if (!validRealtimeRate(realtimeRate)) return
    setRate((current) => mergeRealtimeFxRate(current, realtimeRate))
    setRateError('')
  }, [realtimeRate])

  useEffect(() => {
    let active = true
    let inFlight = false
    let controller: AbortController | null = null

    const refresh = () => {
      if (!active || inFlight || document.visibilityState !== 'visible' || !navigator.onLine) return
      inFlight = true
      controller = new AbortController()
      setRateLoading(true)
      getUsdKrw(controller.signal)
        .then((nextRate) => {
          if (!active) return
          setRate(mergeRealtimeFxRate(nextRate, realtimeRateRef.current))
          setRateError('')
        })
        .catch((reason: unknown) => {
          if (!active || (reason instanceof DOMException && reason.name === 'AbortError')) return
          setRateError(reason instanceof Error ? reason.message : '환율을 불러오지 못했습니다.')
        })
        .finally(() => {
          inFlight = false
          if (active) setRateLoading(false)
        })
    }

    refresh()
    const interval = window.setInterval(refresh, 30_000)
    const resume = () => refresh()
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    window.addEventListener('online', resume)
    document.addEventListener('visibilitychange', handleVisibility)
    return () => {
      active = false
      window.clearInterval(interval)
      window.removeEventListener('online', resume)
      document.removeEventListener('visibilitychange', handleVisibility)
      controller?.abort()
    }
  }, [rateAttempt])

  useEffect(() => {
    if (!open) return
    const controller = new AbortController()
    setHistoryLoading(true)
    setHistoryError('')
    getUsdKrwHistory(period, controller.signal)
      .then((nextHistory) => {
        setHistory(nextHistory)
        setHistoryError('')
      })
      .catch((reason: unknown) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return
        setHistoryError(reason instanceof Error ? reason.message : '환율 이력을 불러오지 못했습니다.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false)
      })
    return () => controller.abort()
  }, [historyAttempt, open, period])

  const periodHistory = history?.period === period ? history : null
  const points = useMemo(() => chartPoints(periodHistory?.items.map((item) => item.close) ?? []), [periodHistory])
  if (!rate && rateError) return (
    <div className="fx-strip unavailable" role="alert">
      <span>USD/KRW 환율을 불러오지 못했습니다. 원통화 평가는 계속 제공됩니다.</span>
      <button type="button" className="button-ghost" onClick={() => setRateAttempt((value) => value + 1)}>다시 시도</button>
    </div>
  )
  if (!rate) return <div className="fx-strip fx-strip-loading" role="status" aria-busy="true"><span className="skeleton-line" />USD/KRW 환율 확인 중…</div>

  const status = fxStatus(rate, Boolean(rateError))

  return (
    <section className="fx-panel" aria-label="USD KRW 환율" aria-busy={rateLoading}>
      <button type="button" className="fx-strip" onClick={() => setOpen((value) => !value)} aria-expanded={open} aria-controls="fx-history-detail">
        <span><b>USD/KRW</b><small>1 USD당 KRW</small></span>
        <strong>{rate.rate.toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
        <span className={(rate.changeRate ?? 0) >= 0 ? 'profit' : 'down'}>{signed(rate.changeRate)}</span>
        <span className="fx-status-wrapper">
          {showAdminDetails && <DataStatusBadge status={status} />}
        </span>
        {showAdminDetails ? (
          <small style={{ justifySelf: 'end', textAlign: 'right' }}>{rate.source} · 기준 {new Date(rate.asOf).toLocaleString('ko-KR')} · 갱신 {new Date(rate.fetchedAt).toLocaleTimeString('ko-KR')}</small>
        ) : (
          <small style={{ justifySelf: 'end', textAlign: 'right' }}>고시 기준: {new Date(rate.asOf).toLocaleDateString('ko-KR')}</small>
        )}
        <b aria-hidden="true">{open ? '접기' : '차트 보기'}</b>
      </button>
      {open && (
        <div className="fx-detail card" id="fx-history-detail" aria-busy={historyLoading}>
          <div className="fx-periods">
            {periods.map((value) => <button type="button" key={value} aria-pressed={period === value} onClick={() => setPeriod(value)}>{value}</button>)}
          </div>
          {historyLoading && periodHistory && <p className="feed-refresh-state" role="status">기존 차트를 유지하며 {period} 데이터를 갱신하고 있습니다.</p>}
          {historyLoading && !periodHistory ? (
            <div className="fx-chart-skeleton" role="status">{period} 환율 이력을 불러오는 중입니다.</div>
          ) : points ? (
            <svg viewBox="0 0 600 120" role="img" aria-label={`${period} USD KRW 종가 추이`}>
              <polyline points={points} fill="none" stroke="currentColor" strokeWidth="3" vectorEffect="non-scaling-stroke" />
            </svg>
          ) : <p>선택한 기간의 저장된 환율 이력이 부족합니다.</p>}
          {historyError && (
            <p className="fx-history-error" role="alert">
              {period} 이력 갱신에 실패했습니다. 다른 기간의 값을 현재 기간으로 표시하지 않습니다.
              <button type="button" className="button-ghost" onClick={() => setHistoryAttempt((value) => value + 1)}>다시 시도</button>
            </p>
          )}
          {rateError && <p className="fx-history-error" role="alert">현재 환율 갱신에 실패해 마지막 성공값을 유지하고 있습니다.</p>}
          <p>원통화 가격은 유지되며 환산값에만 이 환율을 적용합니다.</p>
        </div>
      )}
    </section>
  )
}

function fxStatus(rate: FxRate, refreshFailed: boolean): DataStatus {
  if (refreshFailed || rate.freshness === 'STALE') return 'STALE'
  if (rate.rateType === 'DEMO') return 'DEMO'
  if (rate.rateType === 'DELAYED' || rate.freshness === 'DELAYED') return 'DELAYED'
  return 'LIVE'
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
