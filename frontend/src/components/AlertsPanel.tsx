import { useCallback, useEffect, useState } from 'react'
import { createAlert, deleteAlert, getAlerts, setAlertStatus } from '../api/alerts'
import { getStocks } from '../api/stocks'
import { DataStatusBadge, type DataStatus } from './DataStatusBadge'
import type { PriceAlert } from '../types/alert'
import type { LiveQuote } from '../types/realtime'
import type { StockSummary } from '../types/stock'
import { realtimeInstrumentKey } from '../utils/realtimeInstrument'

type Props = {
  liveQuotes: Record<string, LiveQuote>
}

export function AlertsPanel({ liveQuotes }: Props) {
  const [alerts, setAlerts] = useState<PriceAlert[] | null>(null)
  const [stocks, setStocks] = useState<StockSummary[] | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [symbol, setSymbol] = useState('')
  const [condition, setCondition] = useState<'ABOVE' | 'BELOW'>('ABOVE')
  const [targetPrice, setTargetPrice] = useState('')
  const [alertsLoading, setAlertsLoading] = useState(true)
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [updatingId, setUpdatingId] = useState<number | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [deleteConfirmationId, setDeleteConfirmationId] = useState<number | null>(null)
  const [alertsLoadError, setAlertsLoadError] = useState('')
  const [catalogLoadError, setCatalogLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [statusMessage, setStatusMessage] = useState('')
  const [offline, setOffline] = useState(() => typeof navigator !== 'undefined' && !navigator.onLine)
  const selectedStock = (stocks ?? []).find((stock) => stock.symbol === symbol) ?? stocks?.[0] ?? null

  const loadAlerts = useCallback(async (signal?: AbortSignal) => {
    setAlertsLoading(true)
    setAlertsLoadError('')
    try {
      const items = await getAlerts(signal)
      if (signal?.aborted) return false
      setAlerts(items)
      return true
    } catch (reason: unknown) {
      if (isAbortError(reason)) return false
      setAlertsLoadError(errorMessage(reason, '가격 알림을 불러오지 못했습니다.'))
      return false
    } finally {
      if (!signal?.aborted) setAlertsLoading(false)
    }
  }, [])

  const loadCatalog = useCallback(async (signal?: AbortSignal) => {
    setCatalogLoading(true)
    setCatalogLoadError('')
    try {
      const stockItems = await getStocks(signal)
      if (signal?.aborted) return false
      setStocks(stockItems)
      setSymbol((current) => current || stockItems[0]?.symbol || '')
      return true
    } catch (reason: unknown) {
      if (isAbortError(reason)) return false
      setCatalogLoadError(errorMessage(reason, '알림 종목 목록을 불러오지 못했습니다.'))
      return false
    } finally {
      if (!signal?.aborted) setCatalogLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void loadAlerts(controller.signal)
    void loadCatalog(controller.signal)
    return () => controller.abort()
  }, [loadAlerts, loadCatalog])

  useEffect(() => {
    const updateConnection = () => setOffline(!navigator.onLine)
    window.addEventListener('online', updateConnection)
    window.addEventListener('offline', updateConnection)
    return () => {
      window.removeEventListener('online', updateConnection)
      window.removeEventListener('offline', updateConnection)
    }
  }, [])

  async function retryAll() {
    setActionError('')
    setStatusMessage(offline ? '네트워크 연결을 확인한 뒤 다시 시도해 주세요.' : '가격 알림과 종목 목록을 다시 요청하고 있습니다.')
    if (offline) return
    const [alertsReady, catalogReady] = await Promise.all([loadAlerts(), loadCatalog()])
    setStatusMessage(alertsReady && catalogReady
      ? '가격 알림과 종목 목록을 최신 상태로 갱신했습니다.'
      : '일부 데이터를 갱신하지 못했습니다. 기존 성공 데이터는 그대로 표시합니다.')
  }

  async function add(event: React.FormEvent) {
    event.preventDefault()
    if (saving || updatingId != null || deletingId != null) return
    if (offline) {
      setActionError('오프라인에서는 가격 알림을 저장할 수 없습니다.')
      return
    }
    const parsedTargetPrice = Number(targetPrice)
    if (!selectedStock || !Number.isFinite(parsedTargetPrice) || parsedTargetPrice <= 0) {
      setActionError('종목과 0보다 큰 목표 가격을 입력해 주세요.')
      return
    }

    setSaving(true)
    setActionError('')
    setStatusMessage('')
    try {
      await createAlert({ symbol: selectedStock.symbol, condition, targetPrice: parsedTargetPrice, currency: selectedStock.currency })
      setTargetPrice('')
      setEditorOpen(false)
      const refreshed = await loadAlerts()
      setStatusMessage(refreshed
        ? `${selectedStock.name} 가격 알림을 저장했습니다.`
        : `${selectedStock.name} 알림 저장은 완료됐지만 목록 갱신에 실패했습니다. 다시 시도해 주세요.`)
    } catch (reason: unknown) {
      setActionError(errorMessage(reason, '가격 알림을 만들지 못했습니다.'))
    } finally {
      setSaving(false)
    }
  }

  async function toggle(alert: PriceAlert, displayedStatus: PriceAlert['status'] = alert.status) {
    if (saving || updatingId != null || deletingId != null) return
    if (offline) {
      setActionError('오프라인에서는 알림 상태를 변경할 수 없습니다.')
      return
    }
    setUpdatingId(alert.id)
    setActionError('')
    setStatusMessage('')
    const nextStatus = displayedStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    try {
      await setAlertStatus(alert.id, nextStatus)
      const refreshed = await loadAlerts()
      setStatusMessage(refreshed
        ? `${alert.name} 알림을 ${nextStatus === 'ACTIVE' ? '켰습니다.' : '껐습니다.'}`
        : `알림 상태 변경은 완료됐지만 목록 갱신에 실패했습니다. 현재 표시는 이전 성공 데이터입니다.`)
    } catch (reason: unknown) {
      setActionError(errorMessage(reason, '알림 상태를 바꾸지 못했습니다.'))
    } finally {
      setUpdatingId(null)
    }
  }

  async function remove(alert: PriceAlert) {
    if (saving || updatingId != null || deletingId != null) return
    if (offline) {
      setActionError('오프라인에서는 가격 알림을 삭제할 수 없습니다.')
      return
    }
    setDeletingId(alert.id)
    setActionError('')
    setStatusMessage('')
    try {
      await deleteAlert(alert.id)
      setDeleteConfirmationId(null)
      const refreshed = await loadAlerts()
      setStatusMessage(refreshed
        ? `${alert.name} 가격 알림을 삭제했습니다.`
        : `${alert.name} 알림 삭제는 완료됐지만 목록 갱신에 실패했습니다. 현재 표시는 이전 성공 데이터입니다.`)
    } catch (reason: unknown) {
      setActionError(errorMessage(reason, '가격 알림을 삭제하지 못했습니다.'))
    } finally {
      setDeletingId(null)
    }
  }

  const hasAlertsData = alerts != null
  const hasCatalogData = stocks != null
  const hasRefreshFailure = (hasAlertsData && Boolean(alertsLoadError)) || (hasCatalogData && Boolean(catalogLoadError))
  const isPartial = (hasAlertsData && Boolean(catalogLoadError)) || (hasCatalogData && Boolean(alertsLoadError))
  const editorAvailable = alerts != null && stocks != null && stocks.length > 0
  const busy = saving || updatingId != null || deletingId != null

  return (
    <article className="card alerts-card" id="alerts" aria-busy={alertsLoading || catalogLoading || busy}>
      <div className="section-heading compact">
        <div><p className="eyebrow">PRICE ALERTS · API</p><h2>가격 조건 알림</h2></div>
        <button
          type="button"
          className="alert-add-button"
          onClick={() => setEditorOpen((value) => !value)}
          disabled={!editorAvailable || busy}
          title={!editorAvailable ? '가격 알림과 종목 목록이 준비된 뒤 사용할 수 있습니다.' : undefined}
        >{editorOpen ? '닫기' : '+ 추가'}</button>
      </div>

      <div className="panel-feedback" aria-live="polite" aria-atomic="true">
        {statusMessage && <p className="panel-status-message" role="status">{statusMessage}</p>}
      </div>
      {actionError && <p className="alert-error" role="alert">{actionError}</p>}

      {offline && hasAlertsData && (
        <section className="panel-state panel-state-offline" role="status">
          <DataStatusBadge status="STALE" detail="오프라인" />
          <strong>{hasAlertsData ? '마지막으로 확인된 가격 알림을 표시합니다.' : '가격 알림을 불러오려면 네트워크 연결이 필요합니다.'}</strong>
          <span>오프라인에서는 저장, 상태 변경, 삭제를 할 수 없습니다.</span>
        </section>
      )}

      {hasRefreshFailure && !offline && (
        <section className="panel-state panel-state-warning" role="status">
          <DataStatusBadge status={isPartial ? 'PARTIAL' : 'STALE'} />
          <strong>{isPartial ? '일부 데이터만 준비되었습니다.' : '갱신에 실패해 이전 성공 데이터를 표시합니다.'}</strong>
          {alertsLoadError && <span>가격 알림: {alertsLoadError}</span>}
          {catalogLoadError && <span>종목 목록: {catalogLoadError}</span>}
          <button type="button" onClick={() => void retryAll()}>다시 시도</button>
        </section>
      )}

      {alerts == null && alertsLoading && !offline && (
        <section className="panel-state panel-state-loading" role="status">
          <strong>가격 알림을 불러오는 중입니다.</strong>
          <span>저장된 조건과 최근 평가 기준을 확인하고 있습니다.</span>
        </section>
      )}

      {alerts == null && (offline || !alertsLoading) && (
        <section className={`panel-state ${offline ? 'panel-state-offline' : 'panel-state-error'}`} role={offline ? 'status' : 'alert'}>
          <DataStatusBadge status="UNAVAILABLE" />
          <strong>{offline ? '오프라인이라 가격 알림을 확인할 수 없습니다.' : '가격 알림 조회에 실패했습니다.'}</strong>
          <span>{alertsLoadError || (offline ? '현재 기기에 저장된 가격 알림이 없습니다.' : '잠시 후 다시 시도해 주세요.')}</span>
          {catalogLoadError && <span>종목 목록: {catalogLoadError}</span>}
          <button type="button" onClick={() => void retryAll()} disabled={offline}>다시 시도</button>
        </section>
      )}

      {editorOpen && (
        <form className="alert-form" onSubmit={add} aria-busy={saving}>
          {stocks != null && stocks.length > 0 ? (
            <>
              <select value={symbol} onChange={(event) => setSymbol(event.target.value)} aria-label="알림 종목" disabled={saving}>
                {stocks.map((stock) => <option key={`${stock.market}:${stock.symbol}`} value={stock.symbol}>{stock.name} · {stock.market} · {stock.currency} · {stock.source}</option>)}
              </select>
              <select value={condition} onChange={(event) => setCondition(event.target.value as 'ABOVE' | 'BELOW')} aria-label="알림 조건" disabled={saving}>
                <option value="ABOVE">이상</option><option value="BELOW">이하</option>
              </select>
              <input type="number" min="0.0001" step="any" value={targetPrice} onChange={(event) => setTargetPrice(event.target.value)} placeholder="목표 가격" aria-label="목표 가격" required disabled={saving} />
              <button type="submit" disabled={saving || !selectedStock}>{saving ? '저장 중…' : '저장'}</button>
            </>
          ) : (
            <p className="alert-note">{catalogLoadError ? '종목 목록을 불러오지 못해 지금은 알림을 만들 수 없습니다.' : '알림을 만들 수 있는 종목이 없습니다.'}</p>
          )}
        </form>
      )}

      {alerts != null && alerts.length === 0 && (
        <section className="panel-state panel-state-empty">
          <strong>저장된 가격 알림이 없습니다.</strong>
          <span>목표 가격 이상 또는 이하 조건을 추가하면 여기에서 평가 상태를 확인할 수 있습니다.</span>
          {editorAvailable && <button type="button" onClick={() => setEditorOpen(true)}>첫 가격 알림 만들기</button>}
        </section>
      )}

      {alerts != null && alerts.length > 0 && (
        <div className="alert-list">
          {alerts.map((alert) => {
            const candidateQuote = liveQuotes[realtimeInstrumentKey(alert.market, alert.symbol)]
            const quote = candidateQuote != null && (
              alert.priceAsOf == null || new Date(candidateQuote.asOf) >= new Date(alert.priceAsOf)
            ) ? candidateQuote : undefined
            const latestPrice = quote?.price ?? alert.latestPrice
            const conditionMet = alert.status === 'TRIGGERED' || alert.evaluationStatus === 'CONDITION_MET' || (
              alert.status === 'ACTIVE'
              && latestPrice != null
              && (alert.condition === 'ABOVE' ? latestPrice >= alert.targetPrice : latestPrice <= alert.targetPrice)
            )
            const effectiveStatus = conditionMet ? 'TRIGGERED' : alert.status
            const live = quote?.sessionStatus === 'LIVE'
            const confirmingDelete = deleteConfirmationId === alert.id
            const rowBusy = updatingId === alert.id || deletingId === alert.id
            return (
              <div className={`alert-row ${live ? 'live' : ''}`} key={alert.id} aria-busy={rowBusy}>
                <div className={`alert-state ${effectiveStatus.toLowerCase()}`} aria-hidden="true" />
                <div>
                  <strong>{alert.name} {alert.condition === 'ABOVE' ? '≥' : '≤'} {formatMoney(alert.targetPrice, alert.currency)}</strong>
                  <span>현재 {latestPrice == null ? '가격 확인 불가' : formatMoney(latestPrice, alert.currency)} · {statusLabel(effectiveStatus)}</span>
                  <small>
                    <DataStatusBadge status={alertDataStatus(alert, quote)} />
                    {evaluationStatusLabel(alert.evaluationStatus)} · {quote?.source ?? 'API 저장 평가값(출처 미제공)'} · {formatAsOf(quote?.asOf ?? alert.priceAsOf)}
                  </small>
                  {alert.triggeredAt && <small>조건 충족 {formatAsOf(alert.triggeredAt)}</small>}
                </div>
                <button type="button" onClick={() => void toggle(alert, effectiveStatus)} disabled={busy}>
                  {updatingId === alert.id ? '변경 중…' : effectiveStatus === 'ACTIVE' ? '끄기' : '다시 켜기'}
                </button>
                <div className="alert-delete-actions">
                  {confirmingDelete ? (
                    <>
                      <button type="button" onClick={() => void remove(alert)} disabled={busy} aria-label={`${alert.name} 알림 삭제 확인`}>
                        {deletingId === alert.id ? '삭제 중…' : '삭제 확인'}
                      </button>
                      <button type="button" onClick={() => setDeleteConfirmationId(null)} disabled={deletingId != null}>취소</button>
                    </>
                  ) : (
                    <button
                      type="button"
                      onClick={() => {
                        setDeleteConfirmationId(alert.id)
                        setStatusMessage(`${alert.name} 알림을 삭제하려면 ‘삭제 확인’을 누르세요.`)
                      }}
                      disabled={busy}
                      aria-label={`${alert.name} 알림 삭제`}
                    >삭제</button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
      <p className="alert-note">조건 충족 상태만 표시하며 모바일 푸시·이메일 전송은 MVP 범위에서 제외합니다. 가격과 기준 시각을 확인할 수 없으면 조건 충족으로 간주하지 않습니다.</p>
    </article>
  )
}

function alertDataStatus(alert: PriceAlert, quote?: LiveQuote): DataStatus {
  if (quote?.source.toUpperCase().includes('DEMO')) return 'DEMO'
  if (quote?.sessionStatus === 'LIVE') return 'LIVE'
  if (alert.evaluationStatus === 'PRICE_UNAVAILABLE' || (quote?.price ?? alert.latestPrice) == null) return 'UNAVAILABLE'
  if (alert.evaluationStatus === 'NOT_EVALUATED') return 'PARTIAL'
  if (quote != null) return 'DELAYED'
  return 'REFERENCE'
}

function evaluationStatusLabel(status: PriceAlert['evaluationStatus']) {
  if (status === 'CONDITION_MET') return '조건 충족 평가'
  if (status === 'WAITING') return '조건 대기 평가'
  if (status === 'PRICE_UNAVAILABLE') return '가격 확인 불가'
  return '아직 평가되지 않음'
}

function statusLabel(status: PriceAlert['status']) {
  if (status === 'TRIGGERED') return '조건 충족'
  if (status === 'DISABLED') return '꺼짐'
  return '대기 중'
}

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency, maximumFractionDigits: currency === 'KRW' ? 0 : 2 }).format(value)
}

function formatAsOf(value: string | null) {
  if (!value) return '기준 시각 없음'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return `기준 ${value}`
  return `기준 ${new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(date)}`
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}

function isAbortError(reason: unknown) {
  return reason instanceof DOMException && reason.name === 'AbortError'
}
