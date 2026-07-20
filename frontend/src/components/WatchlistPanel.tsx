import { useEffect, useRef, useState } from 'react'
import { useOutletContext } from 'react-router'
import type { AppRouteContext } from '../app/context'
import { searchStocks } from '../api/stocks'
import { addWatchlist, getWatchlist, removeWatchlist } from '../api/watchlists'
import type { StockCatalogItem, StockRef } from '../types/stock'
import type { WatchlistItem } from '../types/watchlist'
import type { LiveQuote } from '../types/realtime'
import { DataStatusBadge, type DataStatus } from './DataStatusBadge'
import { realtimeInstrumentKey } from '../utils/realtimeInstrument'

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

function stockKey(stock: StockRef) {
  return `${stock.market.toUpperCase()}:${stock.symbol.toUpperCase()}`
}

const availabilityLabel: Record<StockCatalogItem['dataAvailability'], string> = {
  READY: '시세 준비됨',
  PARTIAL: '일부 데이터',
  METADATA_ONLY: '등록 후 데이터 준비',
  UNAVAILABLE: '상세 미지원',
}

export function WatchlistPanel({
  selectedStock,
  editorOpen,
  onSelect,
  onEditorOpenChange,
  onItemsChange,
  liveQuotes,
}: Props) {
  const context = useOutletContext<AppRouteContext | null>()
  const showAdminDetails = context?.showAdminDetails ?? true
  const [items, setItems] = useState<WatchlistItem[]>([])
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<StockCatalogItem[]>([])
  const [loading, setLoading] = useState(true)
  const [searching, setSearching] = useState(false)
  const [mutatingKey, setMutatingKey] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [searchError, setSearchError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [confirmRemoveKey, setConfirmRemoveKey] = useState('')
  const onItemsChangeRef = useRef(onItemsChange)

  useEffect(() => {
    onItemsChangeRef.current = onItemsChange
  }, [onItemsChange])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setErrorMessage('')
    getWatchlist(controller.signal)
      .then((watchlist) => {
        setItems(watchlist)
        onItemsChangeRef.current(watchlist)
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setErrorMessage(error instanceof Error ? error.message : '관심종목을 불러오지 못했습니다.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [loadAttempt])

  useEffect(() => {
    const trimmed = query.trim()
    if (!editorOpen || !trimmed) {
      setResults([])
      setSearching(false)
      setSearchError('')
      return
    }
    const controller = new AbortController()
    const timeout = window.setTimeout(() => {
      setSearching(true)
      setSearchError('')
      searchStocks(trimmed, controller.signal, 0, 20)
        .then((page) => setResults(page.items))
        .catch((error: unknown) => {
          if (error instanceof DOMException && error.name === 'AbortError') return
          setResults([])
          setSearchError(error instanceof Error ? error.message : '종목 검색에 실패했습니다.')
        })
        .finally(() => {
          if (!controller.signal.aborted) setSearching(false)
        })
    }, 250)
    return () => {
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [editorOpen, query])

  function updateItems(next: WatchlistItem[]) {
    setItems(next)
    onItemsChange(next)
  }

  async function addSelectedStock(stock: StockCatalogItem) {
    const key = stockKey(stock)
    if (items.some((item) => stockKey(item) === key)) return
    setMutatingKey(key)
    setErrorMessage('')
    try {
      const added = await addWatchlist({ market: stock.market, symbol: stock.symbol })
      const next = [...items, added]
      updateItems(next)
      setFeedback(`${added.name}을(를) 관심종목에 추가했습니다.`)
    } catch (error: unknown) {
      setErrorMessage(error instanceof Error ? error.message : '관심종목 등록에 실패했습니다.')
    } finally {
      setMutatingKey('')
    }
  }

  async function removeSelectedStock(stock: WatchlistItem) {
    const key = stockKey(stock)
    setMutatingKey(key)
    setErrorMessage('')
    try {
      await removeWatchlist({ market: stock.market, symbol: stock.symbol })
      const next = items.filter((item) => stockKey(item) !== key)
      updateItems(next)
      setConfirmRemoveKey('')
      setFeedback(`${stock.name}을(를) 관심종목에서 삭제했습니다.`)
      if (stockKey(selectedStock) === key && next.length > 0) {
        onSelect({ market: next[0].market, symbol: next[0].symbol })
      }
    } catch (error: unknown) {
      setErrorMessage(error instanceof Error ? error.message : '관심종목 삭제에 실패했습니다.')
    } finally {
      setMutatingKey('')
    }
  }

  return (
    <section className="watchlist-section" id="watchlist" aria-labelledby="watchlist-title">
      <div className="watchlist-title-row">
        <div><p className="eyebrow">MY WATCHLIST</p><h2 id="watchlist-title">관심종목</h2></div>
        <span>{items.length}개 종목 · 개수 제한 없음</span>
      </div>

      {editorOpen && (
        <div className="card watchlist-editor">
          <div className="watchlist-editor-heading">
            <div><strong>관심종목 검색 추가</strong><span>국내·미국 전체 종목 마스터에서 종목명이나 심볼을 검색하세요.</span></div>
            <button type="button" className="editor-cancel" onClick={() => onEditorOpenChange(false)}>닫기</button>
          </div>
          <label className="watchlist-search">
            <span className="sr-only">관심종목 검색</span>
            <input
              type="search"
              value={query}
              placeholder="예: 삼성전자, NAVER, Apple, AAPL"
              autoComplete="off"
              onChange={(event) => setQuery(event.target.value)}
              autoFocus
            />
            {searching && <i className="search-spinner" aria-hidden="true" />}
          </label>
          <div className="watchlist-search-results" aria-live="polite">
            {!query.trim() ? (
              <p>원하는 종목을 검색하면 최대 20개 결과가 표시됩니다.</p>
            ) : searchError ? (
              <p className="watchlist-error" role="alert">{searchError}</p>
            ) : !searching && results.length === 0 ? (
              <p>검색 결과가 없습니다. 종목명 또는 심볼을 확인해 주세요.</p>
            ) : (
              results.map((stock) => {
                const key = stockKey(stock)
                const registered = items.some((item) => stockKey(item) === key)
                const mutating = mutatingKey === key
                const disabledReason = registered
                  ? '이미 관심종목에 등록되어 있습니다.'
                  : mutating
                    ? '관심종목을 등록하는 중입니다.'
                    : !stock.active
                      ? '현재 비활성 종목이라 등록할 수 없습니다.'
                      : !stock.tradable
                        ? '현재 거래 지원 대상이 아니라 등록할 수 없습니다.'
                        : undefined
                return (
                  <article className="watchlist-search-result" key={key}>
                    <span className="search-symbol"><strong>{stock.symbol}</strong><small>{stock.market}</small></span>
                    <span className="search-company"><strong>{stock.name}</strong><small>{stock.englishName ?? stock.exchange}</small></span>
                    {showAdminDetails && <DataStatusBadge status={stock.dataAvailability} detail={availabilityLabel[stock.dataAvailability]} />}
                    <button
                      type="button"
                      onClick={() => addSelectedStock(stock)}
                      disabled={registered || mutating || !stock.active || !stock.tradable}
                      aria-label={`${stock.name} 관심종목 추가`}
                      title={disabledReason}
                    >
                      {mutating ? '추가 중…' : registered ? '등록됨' : '추가'}
                    </button>
                  </article>
                )
              })
            )}
          </div>
          <p className="watchlist-editor-note">등록 즉시 현재가·일봉·뉴스·공시 준비와 실시간 구독 갱신을 요청합니다.</p>
        </div>
      )}

      <p className="sr-only" role="status" aria-live="polite">{feedback}</p>
      {errorMessage && items.length > 0 && <p className="watchlist-error" role="alert">{errorMessage} 기존 관심종목은 그대로 표시합니다.</p>}
      {loading ? (
        <div className="stock-grid watchlist-skeleton-grid" role="status" aria-busy="true" aria-label="관심종목을 불러오는 중">
          {[0, 1, 2, 3].map((item) => <span className="card watchlist-card-skeleton" key={item} aria-hidden="true" />)}
        </div>
      ) : errorMessage && items.length === 0 ? (
        <div className="card watchlist-empty watchlist-load-error" role="alert">
          <strong>관심종목을 불러오지 못했습니다.</strong>
          <span>네트워크 연결을 확인한 뒤 다시 시도해 주세요. 빈 목록으로 처리하지 않았습니다.</span>
          <button type="button" onClick={() => setLoadAttempt((value) => value + 1)}>다시 시도</button>
        </div>
      ) : items.length === 0 ? (
        <div className="card watchlist-empty">
          <strong>아직 등록된 관심종목이 없습니다.</strong>
          <span>상단의 관심종목 추가 버튼으로 첫 종목을 등록해보세요.</span>
        </div>
      ) : (
        <div className="stock-grid" aria-label="관심종목 목록">
          {items.map((stock) => {
            const candidateQuote = liveQuotes[realtimeInstrumentKey(stock.market, stock.symbol)]
            const liveQuote = candidateQuote != null && (stock.asOf == null || new Date(candidateQuote.asOf) >= new Date(stock.asOf))
              ? candidateQuote
              : undefined
            const streaming = liveQuote?.sessionStatus === 'LIVE'
            const price = liveQuote?.price ?? stock.price
            const changeRate = liveQuote?.changeRate ?? stock.changeRate
            const key = stockKey(stock)
            const dataStatus: DataStatus = streaming
              ? 'LIVE'
              : liveQuote
                ? 'DELAYED'
                : stock.dataAvailability === 'READY'
                  ? stock.source?.toUpperCase() === 'DEMO' ? 'DEMO' : 'REFERENCE'
                  : stock.dataAvailability === 'METADATA_ONLY'
                    ? 'METADATA_ONLY'
                    : stock.dataAvailability === 'UNAVAILABLE'
                      ? 'UNAVAILABLE'
                      : 'PARTIAL'
            const quoteSource = liveQuote?.source ?? stock.source ?? (stock.dataAvailability === 'READY' ? '저장 시세' : '동기화 대기')
            const quoteAsOf = liveQuote?.asOf ?? stock.asOf
            return (
              <article className={`card stock-card ${stockKey(selectedStock) === key ? 'selected' : ''} ${streaming ? 'live' : ''}`} key={stock.id}>
                <button className="stock-card-main" type="button" onClick={() => onSelect({ market: stock.market, symbol: stock.symbol })}>
                  <span className="card-heading"><span>{stock.name}</span><small>{stock.market}</small></span>
                  <strong>{price == null ? '데이터 준비 중' : formatMoney(price, stock.currency)}</strong>
                  {changeRate == null ? (
                    <span className="watchlist-data-state">현재가 수집 대기</span>
                  ) : (
                    <span className={changeRate >= 0 ? 'up' : 'down'}>
                      {changeRate >= 0 ? '+' : ''}{changeRate.toFixed(2)}%
                    </span>
                  )}
                  {showAdminDetails && (
                    <span className="quote-trust-row">
                      <DataStatusBadge status={dataStatus} detail={quoteSource} />
                      {quoteAsOf && <small>기준 {new Date(quoteAsOf).toLocaleTimeString('ko-KR')}</small>}
                    </span>
                  )}
                </button>
                <button
                  className={`remove-watchlist${confirmRemoveKey === key ? ' confirm' : ''}`}
                  type="button"
                  onClick={() => {
                    if (confirmRemoveKey === key) {
                      void removeSelectedStock(stock)
                    } else {
                      setConfirmRemoveKey(key)
                      setFeedback(`${stock.name} 삭제를 확인하려면 삭제 확인 버튼을 한 번 더 누르세요.`)
                    }
                  }}
                  disabled={mutatingKey === key}
                  aria-label={confirmRemoveKey === key ? `${stock.name} 관심종목 삭제 확인` : `${stock.name} 관심종목 삭제`}
                  title={mutatingKey === key ? '관심종목에서 삭제하는 중입니다.' : undefined}
                >
                  {mutatingKey === key ? '삭제 중…' : confirmRemoveKey === key ? '삭제 확인' : '삭제'}
                </button>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
