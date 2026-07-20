import { useCallback, useEffect, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router'
import type { AppRouteContext } from '../app/context'
import { createHolding, deleteHolding, getPortfolio } from '../api/portfolio'
import { getStocks } from '../api/stocks'
import { DataStatusBadge, type DataStatus } from './DataStatusBadge'
import type { Portfolio, PortfolioHolding } from '../types/portfolio'
import type { LiveQuote } from '../types/realtime'
import type { StockSummary } from '../types/stock'
import { realtimeInstrumentKey } from '../utils/realtimeInstrument'

type Props = {
  liveQuotes: Record<string, LiveQuote>
}

export function PortfolioPanel({ liveQuotes }: Props) {
  const context = useOutletContext<AppRouteContext | null>()
  const showAdminDetails = context?.showAdminDetails ?? true
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null)
  const [stocks, setStocks] = useState<StockSummary[] | null>(null)
  const [symbol, setSymbol] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [averagePrice, setAveragePrice] = useState('')
  const [purchaseFxRate, setPurchaseFxRate] = useState('')
  const [editing, setEditing] = useState(false)
  const [purchaseCurrency, setPurchaseCurrency] = useState<'USD' | 'KRW'>('USD')

  useEffect(() => {
    setPurchaseCurrency('USD')
    setPurchaseFxRate('')
  }, [symbol])
  const [portfolioLoading, setPortfolioLoading] = useState(true)
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [deleteConfirmationId, setDeleteConfirmationId] = useState<number | null>(null)
  const [portfolioLoadError, setPortfolioLoadError] = useState('')
  const [catalogLoadError, setCatalogLoadError] = useState('')
  const [actionError, setActionError] = useState('')
  const [statusMessage, setStatusMessage] = useState('')
  const [offline, setOffline] = useState(() => typeof navigator !== 'undefined' && !navigator.onLine)

  const availableStocks = useMemo(() => {
    const held = new Set(portfolio?.holdings.map((holding) => holding.symbol) ?? [])
    return (stocks ?? []).filter((stock) => !held.has(stock.symbol))
  }, [portfolio, stocks])
  const selectedStock = (stocks ?? []).find((stock) => stock.symbol === symbol) ?? null
  const evaluatedPortfolio = useMemo(
    () => applyLiveQuotes(portfolio, liveQuotes),
    [liveQuotes, portfolio],
  )

  const loadPortfolio = useCallback(async (signal?: AbortSignal) => {
    setPortfolioLoading(true)
    setPortfolioLoadError('')
    try {
      const loadedPortfolio = await getPortfolio(signal)
      if (signal?.aborted) return false
      setPortfolio(loadedPortfolio)
      return true
    } catch (reason: unknown) {
      if (isAbortError(reason)) return false
      setPortfolioLoadError(errorMessage(reason, '포트폴리오를 불러오지 못했습니다.'))
      return false
    } finally {
      if (!signal?.aborted) setPortfolioLoading(false)
    }
  }, [])

  const loadCatalog = useCallback(async (signal?: AbortSignal) => {
    setCatalogLoading(true)
    setCatalogLoadError('')
    try {
      const loadedStocks = await getStocks(signal)
      if (signal?.aborted) return false
      setStocks(loadedStocks)
      return true
    } catch (reason: unknown) {
      if (isAbortError(reason)) return false
      setCatalogLoadError(errorMessage(reason, '등록 가능한 종목 목록을 불러오지 못했습니다.'))
      return false
    } finally {
      if (!signal?.aborted) setCatalogLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void loadPortfolio(controller.signal)
    void loadCatalog(controller.signal)
    return () => controller.abort()
  }, [loadCatalog, loadPortfolio])

  useEffect(() => {
    const updateConnection = () => setOffline(!navigator.onLine)
    window.addEventListener('online', updateConnection)
    window.addEventListener('offline', updateConnection)
    return () => {
      window.removeEventListener('online', updateConnection)
      window.removeEventListener('offline', updateConnection)
    }
  }, [])

  useEffect(() => {
    if (!symbol && availableStocks.length > 0) setSymbol(availableStocks[0].symbol)
    if (symbol && !availableStocks.some((stock) => stock.symbol === symbol)) {
      setSymbol(availableStocks[0]?.symbol ?? '')
    }
  }, [availableStocks, symbol])

  async function retryAll() {
    setActionError('')
    setStatusMessage(offline ? '네트워크 연결을 확인한 뒤 다시 시도해 주세요.' : '최신 데이터를 다시 요청하고 있습니다.')
    if (offline) return
    const [portfolioReady, catalogReady] = await Promise.all([loadPortfolio(), loadCatalog()])
    setStatusMessage(portfolioReady && catalogReady
      ? '포트폴리오와 종목 목록을 최신 상태로 갱신했습니다.'
      : '일부 데이터를 갱신하지 못했습니다. 기존 성공 데이터는 그대로 표시합니다.')
  }

  async function addHolding(event: React.FormEvent) {
    event.preventDefault()
    if (saving || deletingId != null) return
    if (offline) {
      setActionError('오프라인에서는 보유 종목을 등록할 수 없습니다.')
      return
    }
    const parsedQuantity = Number(quantity)
    let parsedAveragePrice = Number(averagePrice)
    const parsedFxRate = purchaseFxRate ? Number(purchaseFxRate) : null

    if (selectedStock?.currency === 'USD' && purchaseCurrency === 'KRW') {
      if (parsedFxRate == null || parsedFxRate <= 0) {
        setActionError('원화(KRW) 매수 시에는 매수 당시 환율 입력이 필수입니다.')
        return
      }
      // 원화 매수가를 환율로 나누어 달러 매수가로 환산
      parsedAveragePrice = parsedAveragePrice / parsedFxRate
    }

    if (!selectedStock || !Number.isFinite(parsedQuantity) || parsedQuantity <= 0 || !Number.isFinite(parsedAveragePrice) || parsedAveragePrice < 0) {
      setActionError('종목, 수량, 평균 매수가를 올바르게 입력해 주세요.')
      return
    }
    if (parsedFxRate != null && (!Number.isFinite(parsedFxRate) || parsedFxRate <= 0)) {
      setActionError('매수 당시 환율은 0보다 큰 숫자로 입력해 주세요.')
      return
    }

    setSaving(true)
    setActionError('')
    setStatusMessage('')
    try {
      await createHolding({
        symbol: selectedStock.symbol,
        quantity: parsedQuantity,
        averagePurchasePrice: parsedAveragePrice,
        currency: selectedStock.currency,
        ...(selectedStock.currency === 'USD' && parsedFxRate != null
          ? { averagePurchaseFxRate: parsedFxRate, purchaseFxBaseCurrency: 'USD', purchaseFxQuoteCurrency: 'KRW' }
          : {}),
      })
      setAveragePrice('')
      setPurchaseFxRate('')
      setPurchaseCurrency('USD')
      setEditing(false)
      const refreshed = await loadPortfolio()
      setStatusMessage(refreshed
        ? `${selectedStock.name} 보유 내역을 등록했습니다.`
        : `${selectedStock.name} 등록은 완료됐지만 목록 갱신에 실패했습니다. 다시 시도해 주세요.`)
    } catch (reason: unknown) {
      setActionError(errorMessage(reason, '보유 종목을 등록하지 못했습니다.'))
    } finally {
      setSaving(false)
    }
  }

  async function removeHolding(holding: PortfolioHolding) {
    if (deletingId != null || saving) return
    if (offline) {
      setActionError('오프라인에서는 보유 종목을 삭제할 수 없습니다.')
      return
    }
    setDeletingId(holding.id)
    setActionError('')
    setStatusMessage('')
    try {
      await deleteHolding(holding.id)
      setDeleteConfirmationId(null)
      const refreshed = await loadPortfolio()
      setStatusMessage(refreshed
        ? `${holding.name} 보유 내역을 삭제했습니다.`
        : `${holding.name} 삭제는 완료됐지만 목록 갱신에 실패했습니다. 현재 표시는 이전 성공 데이터입니다.`)
    } catch (reason: unknown) {
      setActionError(errorMessage(reason, '보유 종목을 삭제하지 못했습니다.'))
    } finally {
      setDeletingId(null)
    }
  }

  const hasPortfolioData = evaluatedPortfolio != null
  const hasCatalogData = stocks != null
  const hasRefreshFailure = (hasPortfolioData && Boolean(portfolioLoadError)) || (hasCatalogData && Boolean(catalogLoadError))
  const isPartial = (hasPortfolioData && Boolean(catalogLoadError)) || (hasCatalogData && Boolean(portfolioLoadError))
  const portfolioIsEmpty = evaluatedPortfolio?.holdings.length === 0
  const editorAvailable = evaluatedPortfolio != null && stocks != null && stocks.length > 0

  return (
    <article className="card portfolio-card" id="portfolio" aria-busy={portfolioLoading || catalogLoading || saving || deletingId != null}>
      <div className="section-heading">
        <div><p className="eyebrow">PORTFOLIO</p><h2>통화별 포트폴리오 평가</h2></div>
        <button
          type="button"
          className="portfolio-edit-button"
          onClick={() => setEditing((value) => !value)}
          disabled={!editorAvailable || saving || deletingId != null}
          title={!editorAvailable ? '포트폴리오와 종목 목록이 준비된 뒤 사용할 수 있습니다.' : undefined}
        >
          {editing ? '닫기' : '+ 보유 종목'}
        </button>
      </div>

      <div className="panel-feedback" aria-live="polite" aria-atomic="true">
        {statusMessage && <p className="panel-status-message" role="status">{statusMessage}</p>}
      </div>
      {actionError && <p className="portfolio-error" role="alert">{actionError}</p>}

      {offline && hasPortfolioData && (
        <section className="panel-state panel-state-offline" role="status">
          <DataStatusBadge status="STALE" detail="오프라인" />
          <strong>{hasPortfolioData ? '마지막으로 확인된 포트폴리오를 표시합니다.' : '포트폴리오를 불러오려면 네트워크 연결이 필요합니다.'}</strong>
          <span>연결이 복구되면 다시 시도해 최신 평가 기준을 확인해 주세요.</span>
        </section>
      )}

      {hasRefreshFailure && !offline && (
        <section className="panel-state panel-state-warning" role="status">
          <DataStatusBadge status={isPartial ? 'PARTIAL' : 'STALE'} />
          <strong>{isPartial ? '일부 데이터만 준비되었습니다.' : '갱신에 실패해 이전 성공 데이터를 표시합니다.'}</strong>
          {portfolioLoadError && <span>포트폴리오: {portfolioLoadError}</span>}
          {catalogLoadError && <span>종목 목록: {catalogLoadError}</span>}
          <button type="button" onClick={() => void retryAll()}>다시 시도</button>
        </section>
      )}

      {portfolio == null && portfolioLoading && !offline && (
        <section className="panel-state panel-state-loading" role="status">
          <strong>포트폴리오를 불러오는 중입니다.</strong>
          <span>보유 수량과 가격 기준을 확인하고 있습니다.</span>
        </section>
      )}

      {portfolio == null && (offline || !portfolioLoading) && (
        <section className={`panel-state ${offline ? 'panel-state-offline' : 'panel-state-error'}`} role={offline ? 'status' : 'alert'}>
          <DataStatusBadge status="UNAVAILABLE" />
          <strong>{offline ? '오프라인이라 포트폴리오를 확인할 수 없습니다.' : '포트폴리오 조회에 실패했습니다.'}</strong>
          <span>{portfolioLoadError || (offline ? '현재 기기에 저장된 포트폴리오가 없습니다.' : '잠시 후 다시 시도해 주세요.')}</span>
          {catalogLoadError && <span>종목 목록: {catalogLoadError}</span>}
          <button type="button" onClick={() => void retryAll()} disabled={offline}>다시 시도</button>
        </section>
      )}

      {editing && (
        <form className="portfolio-form" onSubmit={addHolding} aria-busy={saving}>
          {availableStocks.length > 0 ? (
            <>
              <select value={symbol} onChange={(event) => setSymbol(event.target.value)} aria-label="보유 종목" disabled={saving}>
                {availableStocks.map((stock) => (
                  <option key={`${stock.market}:${stock.symbol}`} value={stock.symbol}>{stock.name} · {stock.market} · {stock.currency} · {stock.source}</option>
                ))}
              </select>
              <input type="number" min="0.000001" step="any" value={quantity} onChange={(event) => setQuantity(event.target.value)} placeholder="수량" aria-label="보유 수량" required disabled={saving} />
              {selectedStock?.currency === 'USD' && (
                <div className="purchase-currency-toggle" style={{ display: 'flex', gap: '8px', margin: '4px 0 10px', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>매수 당시 통화:</span>
                  <button
                    type="button"
                    onClick={() => {
                      setPurchaseCurrency('USD')
                      setPurchaseFxRate('')
                    }}
                    style={{
                      padding: '4px 10px',
                      fontSize: '0.75rem',
                      borderRadius: '6px',
                      border: '1px solid var(--border)',
                      background: purchaseCurrency === 'USD' ? 'var(--cyan)' : 'rgba(255,255,255,0.02)',
                      color: purchaseCurrency === 'USD' ? '#0f172a' : 'var(--text-muted)',
                      fontWeight: purchaseCurrency === 'USD' ? '800' : 'normal',
                      cursor: 'pointer',
                      transition: 'all 0.2s'
                    }}
                  >
                    USD ($)
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setPurchaseCurrency('KRW')
                    }}
                    style={{
                      padding: '4px 10px',
                      fontSize: '0.75rem',
                      borderRadius: '6px',
                      border: '1px solid var(--border)',
                      background: purchaseCurrency === 'KRW' ? 'var(--cyan)' : 'rgba(255,255,255,0.02)',
                      color: purchaseCurrency === 'KRW' ? '#0f172a' : 'var(--text-muted)',
                      fontWeight: purchaseCurrency === 'KRW' ? '800' : 'normal',
                      cursor: 'pointer',
                      transition: 'all 0.2s'
                    }}
                  >
                    KRW (₩)
                  </button>
                </div>
              )}
              <input type="number" min="0" step="any" value={averagePrice} onChange={(event) => setAveragePrice(event.target.value)} placeholder={selectedStock?.currency === 'USD' ? `평균 매수가 (${purchaseCurrency})` : '평균 매수가'} aria-label="평균 매수가" required disabled={saving} />
              {selectedStock?.currency === 'USD' && (
                <input
                  type="number"
                  min="0.0000000001"
                  step="any"
                  value={purchaseFxRate}
                  onChange={(event) => setPurchaseFxRate(event.target.value)}
                  placeholder={purchaseCurrency === 'KRW' ? "매수 당시 USD/KRW (필수)" : "매수 당시 USD/KRW (선택)"}
                  aria-label="매수 당시 USD KRW 환율"
                  required={purchaseCurrency === 'KRW'}
                  disabled={saving}
                />
              )}
              <button type="submit" disabled={saving || !selectedStock}>{saving ? '저장 중…' : '등록'}</button>
            </>
          ) : (
            <p className="portfolio-empty">{catalogLoadError ? '종목 목록을 불러오지 못해 지금은 등록할 수 없습니다.' : '등록 가능한 종목이 없습니다.'}</p>
          )}
        </form>
      )}

      {evaluatedPortfolio && portfolioIsEmpty && (
        <section className="panel-state panel-state-empty">
          <strong>아직 등록한 보유 종목이 없습니다.</strong>
          <span>보유 종목을 등록하면 가격 기준, 평가액, 손익을 통화별로 확인할 수 있습니다.</span>
          {editorAvailable && <button type="button" onClick={() => setEditing(true)}>첫 보유 종목 등록</button>}
        </section>
      )}

      {evaluatedPortfolio && !portfolioIsEmpty && (
        <>
          <div className="currency-summary-grid">
            <section className="currency-summary base-currency-summary">
              <span>현재 환율 기준 총 평가액</span>
              <strong>{evaluatedPortfolio.conversionComplete && evaluatedPortfolio.baseCurrencyTotalEvaluationAmount != null
                ? formatMoney(evaluatedPortfolio.baseCurrencyTotalEvaluationAmount, 'KRW') : '환산 불완전'}</strong>
              <small className={profitClass(evaluatedPortfolio.baseCurrencyProfitLoss)}>
                {evaluatedPortfolio.profitLossComplete && evaluatedPortfolio.baseCurrencyProfitLoss != null
                  ? `${signedMoney(evaluatedPortfolio.baseCurrencyProfitLoss, 'KRW')} · 원화 통합 손익`
                  : '원화 손익 계산에는 매수 당시 환율이 필요합니다.'}
              </small>
              {showAdminDetails && evaluatedPortfolio.fxRates[0] && (
                <em>
                  <DataStatusBadge status={fxDataStatus(evaluatedPortfolio.fxRates[0])} />
                  USD/KRW {evaluatedPortfolio.fxRates[0].rate.toLocaleString('ko-KR')} · {evaluatedPortfolio.fxRates[0].source} · {formatAsOf(evaluatedPortfolio.fxRates[0].asOf)}
                </em>
              )}
            </section>
            {evaluatedPortfolio.currencySummaries.map((summary) => (
              <section className="currency-summary" key={summary.currency}>
                <span>{summary.currency} 평가액</span>
                <strong>{summary.totalEvaluationAmount == null ? '평가 불가' : formatMoney(summary.totalEvaluationAmount, summary.currency)}</strong>
                <small className={profitClass(summary.profitLoss)}>
                  {summary.profitLoss == null ? '일부 종목의 가격이 없어 평가가 불완전합니다.' : `${signedMoney(summary.profitLoss, summary.currency)} · ${signedRate(summary.returnRate)}`}
                </small>
              </section>
            ))}
          </div>

          <div className="holding-list">
            {evaluatedPortfolio.holdings.map((holding) => {
              const quote = liveQuotes[realtimeInstrumentKey(holding.market, holding.symbol)]
              const streaming = quote?.sessionStatus === 'LIVE' && holding.priceAsOf === quote.asOf
              const confirmingDelete = deleteConfirmationId === holding.id
              return (
                <div className={`holding-row ${streaming ? 'live' : ''}`} key={holding.id}>
                  <div>
                    <strong>{holding.name}</strong>
                    <span>{holding.market} · {holding.symbol} · {holding.quantity}주 · 평균 {formatMoney(holding.averagePurchasePrice, holding.currency)}</span>
                  </div>
                  <div>
                    <strong>{holding.evaluationAmount == null ? '평가 불가' : formatMoney(holding.evaluationAmount, holding.currency)}</strong>
                    <span className={profitClass(holding.profitLoss)}>{signedRate(holding.returnRate)}</span>
                    {showAdminDetails && (
                      <small>
                        <DataStatusBadge status={holdingDataStatus(holding, streaming)} />
                        {valuationStatusLabel(holding.valuationStatus)} · {holding.priceSource ?? '출처 없음'} · {formatAsOf(holding.priceAsOf)}
                      </small>
                    )}
                    {holding.convertedEvaluationAmount != null && holding.currency !== 'KRW' && <small>약 {formatMoney(holding.convertedEvaluationAmount, 'KRW')}</small>}
                    {showAdminDetails && holding.fxEffectApproximation != null && <small>환율효과 근사 {signedMoney(holding.fxEffectApproximation, 'KRW')}</small>}
                  </div>
                  <div className="holding-actions">
                    {confirmingDelete ? (
                      <>
                        <button type="button" onClick={() => void removeHolding(holding)} disabled={deletingId != null || saving} aria-label={`${holding.name} 보유 삭제 확인`}>
                          {deletingId === holding.id ? '삭제 중…' : '삭제 확인'}
                        </button>
                        <button type="button" onClick={() => setDeleteConfirmationId(null)} disabled={deletingId != null}>취소</button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={() => {
                          setDeleteConfirmationId(holding.id)
                          setStatusMessage(`${holding.name} 보유 내역을 삭제하려면 ‘삭제 확인’을 누르세요.`)
                        }}
                        disabled={deletingId != null || saving}
                        aria-label={`${holding.name} 보유 삭제`}
                      >삭제</button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </>
      )}
      <p className="portfolio-note">원통화 금액을 보존하면서 검증된 USD/KRW 환율로만 원화 환산합니다. 환율이나 매수 환율이 없으면 불완전한 합계를 전체 자산·손익처럼 표시하지 않습니다.</p>
    </article>
  )
}

function applyLiveQuotes(portfolio: Portfolio | null, liveQuotes: Record<string, LiveQuote>): Portfolio | null {
  if (portfolio == null) return null
  const fxRate = portfolio.fxRates.find((item) => item.pair === 'USD/KRW')?.rate ?? null
  const holdings = portfolio.holdings.map((holding) => {
    const live = applyLiveQuote(holding, liveQuotes[realtimeInstrumentKey(holding.market, holding.symbol)])
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
  const hasHoldings = holdings.length > 0
  const conversionComplete = hasHoldings && holdings.every((holding) => holding.convertedEvaluationAmount != null)
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
  if (quote == null || !Number.isFinite(quote.price) || (holding.priceAsOf != null && new Date(quote.asOf) < new Date(holding.priceAsOf))) return holding
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

function fxDataStatus(rate: Portfolio['fxRates'][number]): DataStatus {
  if (rate.rateType.toUpperCase() === 'DEMO' || rate.source.toUpperCase() === 'DEMO') return 'DEMO'
  if (rate.freshness.toUpperCase() !== 'FRESH') return 'STALE'
  return 'REFERENCE'
}

function holdingDataStatus(holding: PortfolioHolding, streaming: boolean): DataStatus {
  if (streaming) return 'LIVE'
  if (holding.valuationStatus === 'PRICE_UNAVAILABLE' || holding.latestPrice == null) return 'UNAVAILABLE'
  if (holding.priceSource?.toUpperCase().includes('DEMO')) return 'DEMO'
  return 'REFERENCE'
}

function valuationStatusLabel(status: PortfolioHolding['valuationStatus']) {
  return status === 'VALUED' ? '평가 완료' : '가격 확인 불가'
}

function profitClass(value: number | null) {
  if (value == null) return undefined
  return value >= 0 ? 'profit' : 'down'
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
