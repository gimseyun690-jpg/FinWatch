import { useEffect, useMemo, useState } from 'react'
import { createHolding, deleteHolding, getPortfolio } from '../api/portfolio'
import { getStocks } from '../api/stocks'
import type { Portfolio } from '../types/portfolio'
import type { PortfolioHolding } from '../types/portfolio'
import type { LiveQuote } from '../types/realtime'
import type { StockSummary } from '../types/stock'

type Props = {
  liveQuotes: Record<string, LiveQuote>
}

export function PortfolioPanel({ liveQuotes }: Props) {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null)
  const [stocks, setStocks] = useState<StockSummary[]>([])
  const [symbol, setSymbol] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [averagePrice, setAveragePrice] = useState('')
  const [purchaseFxRate, setPurchaseFxRate] = useState('')
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const availableStocks = useMemo(() => {
    const held = new Set(portfolio?.holdings.map((holding) => holding.symbol) ?? [])
    return stocks.filter((stock) => !held.has(stock.symbol))
  }, [portfolio, stocks])
  const selectedStock = stocks.find((stock) => stock.symbol === symbol) ?? null
  const evaluatedPortfolio = useMemo(
    () => applyLiveQuotes(portfolio, liveQuotes),
    [liveQuotes, portfolio],
  )

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
        ...(selectedStock.currency === 'USD' && purchaseFxRate
          ? { averagePurchaseFxRate: Number(purchaseFxRate), purchaseFxBaseCurrency: 'USD', purchaseFxQuoteCurrency: 'KRW' }
          : {}),
      })
      await refresh()
      setAveragePrice('')
      setPurchaseFxRate('')
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
          {selectedStock?.currency === 'USD' && <input type="number" min="0.0000000001" step="any" value={purchaseFxRate} onChange={(event) => setPurchaseFxRate(event.target.value)} placeholder="매수 당시 USD/KRW (선택)" aria-label="매수 당시 USD KRW 환율" />}
          <button type="submit" disabled={saving || !selectedStock}>{saving ? '저장 중…' : '등록'}</button>
        </form>
      )}
      {error && <p className="portfolio-error" role="alert">{error}</p>}

      <div className="currency-summary-grid">
        {evaluatedPortfolio && (
          <section className="currency-summary base-currency-summary">
            <span>현재 환율 기준 총 평가액</span>
            <strong>{evaluatedPortfolio.conversionComplete && evaluatedPortfolio.baseCurrencyTotalEvaluationAmount != null
              ? formatMoney(evaluatedPortfolio.baseCurrencyTotalEvaluationAmount, 'KRW') : '환산 불완전'}</strong>
            <small className={(evaluatedPortfolio.baseCurrencyProfitLoss ?? 0) >= 0 ? 'profit' : 'down'}>
              {evaluatedPortfolio.profitLossComplete && evaluatedPortfolio.baseCurrencyProfitLoss != null
                ? `${signedMoney(evaluatedPortfolio.baseCurrencyProfitLoss, 'KRW')} · 원화 통합 손익`
                : '원화 손익 계산에는 매수 당시 환율이 필요합니다.'}
            </small>
            {evaluatedPortfolio.fxRates[0] && <em>USD/KRW {evaluatedPortfolio.fxRates[0].rate.toLocaleString('ko-KR')} · {evaluatedPortfolio.fxRates[0].source} · {evaluatedPortfolio.fxRates[0].freshness}</em>}
          </section>
        )}
        {evaluatedPortfolio?.currencySummaries.map((summary) => (
          <section className="currency-summary" key={summary.currency}>
            <span>{summary.currency} 평가액</span>
            <strong>{summary.totalEvaluationAmount == null ? '가격 없음' : formatMoney(summary.totalEvaluationAmount, summary.currency)}</strong>
            <small className={(summary.profitLoss ?? 0) >= 0 ? 'profit' : 'down'}>
              {summary.profitLoss == null ? '평가 불완전' : `${signedMoney(summary.profitLoss, summary.currency)} · ${signedRate(summary.returnRate)}`}
            </small>
          </section>
        ))}
        {evaluatedPortfolio && evaluatedPortfolio.currencySummaries.length === 0 && <p className="portfolio-empty">보유 종목을 등록하면 최신 가격 기준 평가가 표시됩니다.</p>}
      </div>

      <div className="holding-list">
        {evaluatedPortfolio?.holdings.map((holding) => {
          const quote = liveQuotes[holding.symbol]
          const streaming = quote?.sessionStatus === 'LIVE' && holding.priceAsOf === quote.asOf
          return (
          <div className={`holding-row ${streaming ? 'live' : ''}`} key={holding.id}>
            <div><strong>{holding.name}</strong><span>{holding.quantity}주 · 평균 {formatMoney(holding.averagePurchasePrice, holding.currency)}</span></div>
            <div>
              <strong>{holding.evaluationAmount == null ? '—' : formatMoney(holding.evaluationAmount, holding.currency)}</strong>
              <span className={(holding.profitLoss ?? 0) >= 0 ? 'profit' : 'down'}>{signedRate(holding.returnRate)}</span>
              {streaming && <small className="portfolio-live-label">LIVE · {holding.priceSource}</small>}
              {holding.convertedEvaluationAmount != null && holding.currency !== 'KRW' && <small>약 {formatMoney(holding.convertedEvaluationAmount, 'KRW')}</small>}
              {holding.fxEffectApproximation != null && <small>환율효과 근사 {signedMoney(holding.fxEffectApproximation, 'KRW')}</small>}
            </div>
            <button type="button" onClick={() => removeHolding(holding.id)} aria-label={`${holding.name} 보유 삭제`}>삭제</button>
          </div>
          )
        })}
      </div>
      <p className="portfolio-note">원통화 금액을 보존하면서 검증된 USD/KRW 환율로만 원화 환산합니다. 환율이나 매수 환율이 없으면 불완전한 합계를 전체 자산·손익처럼 표시하지 않습니다.</p>
    </article>
  )
}

function applyLiveQuotes(portfolio: Portfolio | null, liveQuotes: Record<string, LiveQuote>): Portfolio | null {
  if (portfolio == null) return null
  const fxRate = portfolio.fxRates.find((item) => item.pair === 'USD/KRW')?.rate ?? null
  const holdings = portfolio.holdings.map((holding) => {
    const live = applyLiveQuote(holding, liveQuotes[holding.symbol])
    const convertedEvaluationAmount = live.evaluationAmount == null ? null
      : live.currency === 'KRW' ? live.evaluationAmount
        : live.currency === 'USD' && fxRate != null ? Math.round(live.evaluationAmount * fxRate) : null
    const convertedPurchaseAmount = live.currency === 'KRW' ? live.purchaseAmount
      : live.currency === 'USD' && live.averagePurchaseFxRate != null ? Math.round(live.purchaseAmount * live.averagePurchaseFxRate) : null
    return {
      ...live,
      convertedEvaluationAmount,
      convertedPurchaseAmount,
      convertedProfitLoss: convertedEvaluationAmount == null || convertedPurchaseAmount == null ? null : convertedEvaluationAmount - convertedPurchaseAmount,
      fxEffectApproximation: live.currency === 'USD' && fxRate != null && live.averagePurchaseFxRate != null && live.evaluationAmount != null
        ? Math.round(live.evaluationAmount * (fxRate - live.averagePurchaseFxRate)) : null,
    }
  })
  const currencies = [...new Set(holdings.map((holding) => holding.currency))]
  const currencySummaries = currencies.map((currency) => {
    const items = holdings.filter((holding) => holding.currency === currency)
    const totalPurchaseAmount = items.reduce((sum, holding) => sum + holding.purchaseAmount, 0)
    const valuationComplete = items.every((holding) => holding.evaluationAmount != null)
    if (!valuationComplete) {
      return { currency, totalPurchaseAmount, totalEvaluationAmount: null, profitLoss: null, returnRate: null, valuationComplete }
    }
    const totalEvaluationAmount = items.reduce((sum, holding) => sum + (holding.evaluationAmount ?? 0), 0)
    const profitLoss = totalEvaluationAmount - totalPurchaseAmount
    const returnRate = totalPurchaseAmount === 0 ? null : profitLoss / totalPurchaseAmount * 100
    return { currency, totalPurchaseAmount, totalEvaluationAmount, profitLoss, returnRate, valuationComplete }
  })
  const conversionComplete = holdings.every((holding) => holding.convertedEvaluationAmount != null)
  const profitLossComplete = conversionComplete && holdings.every((holding) => holding.convertedPurchaseAmount != null)
  const baseCurrencyTotalEvaluationAmount = conversionComplete ? holdings.reduce((sum, holding) => sum + (holding.convertedEvaluationAmount ?? 0), 0) : null
  const baseCurrencyTotalPurchaseAmount = profitLossComplete ? holdings.reduce((sum, holding) => sum + (holding.convertedPurchaseAmount ?? 0), 0) : null
  return {
    ...portfolio,
    holdings,
    currencySummaries,
    conversionComplete,
    profitLossComplete,
    baseCurrencyTotalEvaluationAmount,
    baseCurrencyTotalPurchaseAmount,
    baseCurrencyProfitLoss: baseCurrencyTotalEvaluationAmount == null || baseCurrencyTotalPurchaseAmount == null ? null : baseCurrencyTotalEvaluationAmount - baseCurrencyTotalPurchaseAmount,
  }
}

function applyLiveQuote(holding: PortfolioHolding, quote?: LiveQuote): PortfolioHolding {
  if (quote == null || (holding.priceAsOf != null && new Date(quote.asOf) < new Date(holding.priceAsOf))) return holding
  const evaluationAmount = holding.quantity * quote.price
  const profitLoss = evaluationAmount - holding.purchaseAmount
  return {
    ...holding,
    latestPrice: quote.price,
    priceAsOf: quote.asOf,
    priceSource: quote.source,
    evaluationAmount,
    profitLoss,
    returnRate: holding.purchaseAmount === 0 ? null : profitLoss / holding.purchaseAmount * 100,
    valuationStatus: 'VALUED',
  }
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
