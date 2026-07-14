import { useEffect, useState } from 'react'
import { createAlert, deleteAlert, getAlerts, setAlertStatus } from '../api/alerts'
import { getStocks } from '../api/stocks'
import type { PriceAlert } from '../types/alert'
import type { LiveQuote } from '../types/realtime'
import type { StockSummary } from '../types/stock'

type Props = {
  liveQuotes: Record<string, LiveQuote>
}

export function AlertsPanel({ liveQuotes }: Props) {
  const [alerts, setAlerts] = useState<PriceAlert[]>([])
  const [stocks, setStocks] = useState<StockSummary[]>([])
  const [editorOpen, setEditorOpen] = useState(false)
  const [symbol, setSymbol] = useState('000660')
  const [condition, setCondition] = useState<'ABOVE' | 'BELOW'>('ABOVE')
  const [targetPrice, setTargetPrice] = useState('')
  const [error, setError] = useState('')
  const selectedStock = stocks.find((stock) => stock.symbol === symbol) ?? stocks[0] ?? null

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([getAlerts(controller.signal), getStocks(controller.signal)])
      .then(([items, stockItems]) => {
        setAlerts(items)
        setStocks(stockItems)
        if (stockItems.length > 0) setSymbol(stockItems[0].symbol)
      })
      .catch((reason: unknown) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return
        setError(reason instanceof Error ? reason.message : '가격 알림을 불러오지 못했습니다.')
      })
    return () => controller.abort()
  }, [])

  async function refresh() { setAlerts(await getAlerts()) }

  async function add(event: React.FormEvent) {
    event.preventDefault()
    if (!selectedStock) return
    setError('')
    try {
      await createAlert({ symbol: selectedStock.symbol, condition, targetPrice: Number(targetPrice), currency: selectedStock.currency })
      await refresh()
      setTargetPrice('')
      setEditorOpen(false)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : '가격 알림을 만들지 못했습니다.')
    }
  }

  async function toggle(alert: PriceAlert, displayedStatus: PriceAlert['status'] = alert.status) {
    setError('')
    try {
      await setAlertStatus(alert.id, displayedStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')
      await refresh()
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : '알림 상태를 바꾸지 못했습니다.')
    }
  }

  async function remove(alertId: number) {
    await deleteAlert(alertId)
    await refresh()
  }

  return (
    <article className="card alerts-card" id="alerts">
      <div className="section-heading compact">
        <div><p className="eyebrow">PRICE ALERTS · API</p><h2>가격 조건 알림</h2></div>
        <button type="button" className="alert-add-button" onClick={() => setEditorOpen((value) => !value)}>{editorOpen ? '닫기' : '+ 추가'}</button>
      </div>
      {editorOpen && (
        <form className="alert-form" onSubmit={add}>
          <select value={symbol} onChange={(event) => setSymbol(event.target.value)} aria-label="알림 종목">
            {stocks.map((stock) => <option key={`${stock.market}:${stock.symbol}`} value={stock.symbol}>{stock.name}</option>)}
          </select>
          <select value={condition} onChange={(event) => setCondition(event.target.value as 'ABOVE' | 'BELOW')} aria-label="알림 조건">
            <option value="ABOVE">이상</option><option value="BELOW">이하</option>
          </select>
          <input type="number" min="0.0001" step="any" value={targetPrice} onChange={(event) => setTargetPrice(event.target.value)} placeholder="목표 가격" aria-label="목표 가격" required />
          <button type="submit">저장</button>
        </form>
      )}
      {error && <p className="alert-error" role="alert">{error}</p>}
      <div className="alert-list">
        {alerts.map((alert) => {
          const candidateQuote = liveQuotes[alert.symbol]
          const quote = candidateQuote != null && (
            alert.priceAsOf == null || new Date(candidateQuote.asOf) >= new Date(alert.priceAsOf)
          ) ? candidateQuote : undefined
          const latestPrice = quote?.price ?? alert.latestPrice
          const conditionMet = alert.status === 'TRIGGERED' || (
            alert.status === 'ACTIVE'
            && latestPrice != null
            && (alert.condition === 'ABOVE' ? latestPrice >= alert.targetPrice : latestPrice <= alert.targetPrice)
          )
          const effectiveStatus = conditionMet ? 'TRIGGERED' : alert.status
          return (
          <div className={`alert-row ${quote?.sessionStatus === 'LIVE' ? 'live' : ''}`} key={alert.id}>
            <div className={`alert-state ${effectiveStatus.toLowerCase()}`} aria-hidden="true" />
            <div>
              <strong>{alert.name} {alert.condition === 'ABOVE' ? '≥' : '≤'} {formatMoney(alert.targetPrice, alert.currency)}</strong>
              <span>현재 {latestPrice == null ? '가격 없음' : formatMoney(latestPrice, alert.currency)} · {statusLabel(effectiveStatus)}</span>
              {quote?.sessionStatus === 'LIVE' && <small className="alert-live-label">LIVE · {quote.source}</small>}
            </div>
            <button type="button" onClick={() => toggle(alert, effectiveStatus)}>{effectiveStatus === 'ACTIVE' ? '끄기' : '다시 켜기'}</button>
            <button type="button" onClick={() => remove(alert.id)} aria-label={`${alert.name} 알림 삭제`}>삭제</button>
          </div>
          )
        })}
      </div>
      <p className="alert-note">조건 충족 상태만 표시하며 모바일 푸시·이메일 전송은 MVP 범위에서 제외합니다.</p>
    </article>
  )
}

function statusLabel(status: PriceAlert['status']) {
  if (status === 'TRIGGERED') return '조건 충족'
  if (status === 'DISABLED') return '꺼짐'
  return '대기 중'
}

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency, maximumFractionDigits: currency === 'KRW' ? 0 : 2 }).format(value)
}
