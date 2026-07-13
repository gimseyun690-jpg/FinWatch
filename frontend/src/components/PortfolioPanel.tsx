import { useEffect, useMemo, useState } from 'react'
import { createHolding, deleteHolding, getPortfolio } from '../api/portfolio'
import { getStocks } from '../api/stocks'
import type { Portfolio } from '../types/portfolio'
import type { StockSummary } from '../types/stock'

export function PortfolioPanel() {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null)
  const [stocks, setStocks] = useState<StockSummary[]>([])
  const [symbol, setSymbol] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [averagePrice, setAveragePrice] = useState('')
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const availableStocks = useMemo(() => {
    const held = new Set(portfolio?.holdings.map((holding) => holding.symbol) ?? [])
    return stocks.filter((stock) => !held.has(stock.symbol))
  }, [portfolio, stocks])
  const selectedStock = stocks.find((stock) => stock.symbol === symbol) ?? null

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([getPortfolio(controller.signal), getStocks(controller.signal)])
      .then(([loadedPortfolio, loadedStocks]) => {
        setPortfolio(loadedPortfolio)
        setStocks(loadedStocks)
      })
      .catch((reason: unknown) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return
        setError(reason instanceof Error ? reason.message : '포트폴리오를 불러오지 못했습니다.')
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!symbol && availableStocks.length > 0) setSymbol(availableStocks[0].symbol)
    if (symbol && !availableStocks.some((stock) => stock.symbol === symbol)) {
      setSymbol(availableStocks[0]?.symbol ?? '')
    }
  }, [availableStocks, symbol])

  async function refresh() {
    setPortfolio(await getPortfolio())
  }

  async function addHolding(event: React.FormEvent) {
    event.preventDefault()
    if (!selectedStock) return
    setSaving(true)
    setError('')
    try {
      await createHolding({
        symbol: selectedStock.symbol,
        quantity: Number(quantity),
        averagePurchasePrice: Number(averagePrice),
        currency: selectedStock.currency,
      })
      await refresh()
      setAveragePrice('')
      setEditing(false)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : '보유 종목을 등록하지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }

  async function removeHolding(id: number) {
    setError('')
    try {
      await deleteHolding(id)
      await refresh()
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : '보유 종목을 삭제하지 못했습니다.')
    }
  }

  return (
    <article className="card portfolio-card" id="portfolio">
      <div className="section-heading">
        <div><p className="eyebrow">PORTFOLIO · API</p><h2>통화별 포트폴리오 평가</h2></div>
        <button type="button" className="portfolio-edit-button" onClick={() => setEditing((value) => !value)}>
          {editing ? '닫기' : '+ 보유 종목'}
        </button>
      </div>

      {editing && (
        <form className="portfolio-form" onSubmit={addHolding}>
          <select value={symbol} onChange={(event) => setSymbol(event.target.value)} aria-label="보유 종목">
            {availableStocks.map((stock) => (
              <option key={`${stock.market}:${stock.symbol}`} value={stock.symbol}>{stock.name} · {stock.currency}</option>
            ))}
          </select>
          <input type="number" min="0.000001" step="any" value={quantity} onChange={(event) => setQuantity(event.target.value)} placeholder="수량" aria-label="보유 수량" required />
          <input type="number" min="0" step="any" value={averagePrice} onChange={(event) => setAveragePrice(event.target.value)} placeholder="평균 매수가" aria-label="평균 매수가" required />
          <button type="submit" disabled={saving || !selectedStock}>{saving ? '저장 중…' : '등록'}</button>
        </form>
      )}
      {error && <p className="portfolio-error" role="alert">{error}</p>}

      <div className="currency-summary-grid">
        {portfolio?.currencySummaries.map((summary) => (
          <section className="currency-summary" key={summary.currency}>
            <span>{summary.currency} 평가액</span>
            <strong>{summary.totalEvaluationAmount == null ? '가격 없음' : formatMoney(summary.totalEvaluationAmount, summary.currency)}</strong>
            <small className={(summary.profitLoss ?? 0) >= 0 ? 'profit' : 'down'}>
              {summary.profitLoss == null ? '평가 불완전' : `${signedMoney(summary.profitLoss, summary.currency)} · ${signedRate(summary.returnRate)}`}
            </small>
          </section>
        ))}
        {portfolio && portfolio.currencySummaries.length === 0 && <p className="portfolio-empty">보유 종목을 등록하면 최신 가격 기준 평가가 표시됩니다.</p>}
      </div>

      <div className="holding-list">
        {portfolio?.holdings.map((holding) => (
          <div className="holding-row" key={holding.id}>
            <div><strong>{holding.name}</strong><span>{holding.quantity}주 · 평균 {formatMoney(holding.averagePurchasePrice, holding.currency)}</span></div>
            <div><strong>{holding.evaluationAmount == null ? '—' : formatMoney(holding.evaluationAmount, holding.currency)}</strong><span className={(holding.profitLoss ?? 0) >= 0 ? 'profit' : 'down'}>{signedRate(holding.returnRate)}</span></div>
            <button type="button" onClick={() => removeHolding(holding.id)} aria-label={`${holding.name} 보유 삭제`}>삭제</button>
          </div>
        ))}
      </div>
      <p className="portfolio-note">KRW와 USD는 환율 없이 합산하지 않으며 각 시장의 최신 저장 가격으로 평가합니다.</p>
    </article>
  )
}

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency, maximumFractionDigits: currency === 'KRW' ? 0 : 2 }).format(value)
}

function signedMoney(value: number, currency: string) {
  const prefix = value > 0 ? '+' : ''
  return `${prefix}${formatMoney(value, currency)}`
}

function signedRate(value: number | null) {
  if (value == null) return '수익률 계산 불가'
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
}
