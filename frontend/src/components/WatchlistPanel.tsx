import { useEffect, useMemo, useState } from 'react'
import { getStocks } from '../api/stocks'
import { addWatchlist, getWatchlist, removeWatchlist } from '../api/watchlists'
import type { StockRef, StockSummary } from '../types/stock'
import type { WatchlistItem } from '../types/watchlist'
import type { LiveQuote } from '../types/realtime'

type Props = {
  selectedStock: StockRef
  editorOpen: boolean
  onSelect: (stock: StockRef) => void
  onEditorOpenChange: (open: boolean) => void
  onItemsChange: (items: WatchlistItem[]) => void
  liveQuotes: Record<string, LiveQuote>
}

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency,
    maximumFractionDigits: currency === 'KRW' ? 0 : 2,
  }).format(value)
}

export function WatchlistPanel({
  selectedStock,
  editorOpen,
  onSelect,
  onEditorOpenChange,
  onItemsChange,
  liveQuotes,
}: Props) {
  const [items, setItems] = useState<WatchlistItem[]>([])
  const [stocks, setStocks] = useState<StockSummary[]>([])
  const [candidate, setCandidate] = useState('')
  const [loading, setLoading] = useState(true)
  const [mutatingSymbol, setMutatingSymbol] = useState('')
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    Promise.all([getWatchlist(controller.signal), getStocks(controller.signal)])
      .then(([watchlist, stockItems]) => {
        setItems(watchlist)
        setStocks(stockItems)
        onItemsChange(watchlist)
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setErrorMessage(error instanceof Error ? error.message : '관심종목을 불러오지 못했습니다.')
      })
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [onItemsChange, onSelect])

  const availableStocks = useMemo(
    () => stocks.filter((stock) => !items.some((item) => item.symbol === stock.symbol)),
    [items, stocks],
  )

  useEffect(() => {
    if (!availableStocks.some((stock) => stock.symbol === candidate)) {
      setCandidate(availableStocks[0]?.symbol ?? '')
    }
  }, [availableStocks, candidate])

  function updateItems(next: WatchlistItem[]) {
    setItems(next)
    onItemsChange(next)
  }

  async function addSelectedStock() {
    if (!candidate) return
    setMutatingSymbol(candidate)
    setErrorMessage('')
    try {
      const added = await addWatchlist(candidate)
      const next = [...items, added]
      updateItems(next)
      onSelect({ market: added.market, symbol: added.symbol })
      if (next.length >= stocks.length) onEditorOpenChange(false)
    } catch (error: unknown) {
      setErrorMessage(error instanceof Error ? error.message : '관심종목 등록에 실패했습니다.')
    } finally {
      setMutatingSymbol('')
    }
  }

  async function removeSelectedStock(symbol: string) {
    setMutatingSymbol(symbol)
    setErrorMessage('')
    try {
      await removeWatchlist(symbol)
      const next = items.filter((item) => item.symbol !== symbol)
      updateItems(next)
      if (selectedStock.symbol === symbol && next.length > 0) {
        onSelect({ market: next[0].market, symbol: next[0].symbol })
      }
    } catch (error: unknown) {
      setErrorMessage(error instanceof Error ? error.message : '관심종목 삭제에 실패했습니다.')
    } finally {
      setMutatingSymbol('')
    }
  }

  return (
    <section className="watchlist-section" id="watchlist" aria-labelledby="watchlist-title">
      <div className="watchlist-title-row">
        <div><p className="eyebrow">MY WATCHLIST</p><h2 id="watchlist-title">관심종목</h2></div>
        <span>{items.length}개 종목</span>
      </div>

      {editorOpen && (
        <div className="card watchlist-editor">
          <div><strong>관심종목 추가</strong><span>등록할 종목을 선택하세요.</span></div>
          <select value={candidate} onChange={(event) => setCandidate(event.target.value)} disabled={availableStocks.length === 0}>
            {availableStocks.length === 0 && <option value="">추가할 수 있는 종목이 없습니다</option>}
            {availableStocks.map((stock) => <option value={stock.symbol} key={stock.symbol}>{stock.name} · {stock.symbol}</option>)}
          </select>
          <button type="button" onClick={addSelectedStock} disabled={!candidate || Boolean(mutatingSymbol)}>추가</button>
          <button type="button" className="editor-cancel" onClick={() => onEditorOpenChange(false)}>닫기</button>
        </div>
      )}

      {errorMessage && <p className="watchlist-error" role="alert">{errorMessage}</p>}
      {loading ? (
        <div className="card watchlist-empty">관심종목을 불러오는 중입니다.</div>
      ) : items.length === 0 ? (
        <div className="card watchlist-empty">
          <strong>아직 등록된 관심종목이 없습니다.</strong>
          <span>상단의 관심종목 추가 버튼으로 첫 종목을 등록해보세요.</span>
        </div>
      ) : (
        <div className="stock-grid" aria-label="관심종목 목록">
          {items.map((stock) => {
            const candidateQuote = liveQuotes[stock.symbol]
            const liveQuote = candidateQuote != null && new Date(candidateQuote.asOf) >= new Date(stock.asOf)
              ? candidateQuote
              : undefined
            const streaming = liveQuote?.sessionStatus === 'LIVE'
            const price = liveQuote?.price ?? stock.price
            const changeRate = liveQuote?.changeRate ?? stock.changeRate
            return (
            <article className={`card stock-card ${selectedStock.market === stock.market && selectedStock.symbol === stock.symbol ? 'selected' : ''} ${streaming ? 'live' : ''}`} key={stock.id}>
              <button className="stock-card-main" type="button" onClick={() => onSelect({ market: stock.market, symbol: stock.symbol })}>
                <span className="card-heading"><span>{stock.name}</span><small>{stock.market}</small></span>
                <strong>{formatMoney(price, stock.currency)}</strong>
                <span className={changeRate >= 0 ? 'up' : 'down'}>
                  {changeRate >= 0 ? '+' : ''}{changeRate.toFixed(2)}%
                </span>
                {liveQuote && (
                  <span className={`live-quote-label ${streaming ? '' : 'snapshot'}`}>
                    <i />{liveQuote.source} · {streaming ? '실시간' : '최근 시세'}
                  </span>
                )}
              </button>
              <button
                className="remove-watchlist"
                type="button"
                onClick={() => removeSelectedStock(stock.symbol)}
                disabled={mutatingSymbol === stock.symbol}
                aria-label={`${stock.name} 관심종목 삭제`}
              >
                {mutatingSymbol === stock.symbol ? '…' : '삭제'}
              </button>
            </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
