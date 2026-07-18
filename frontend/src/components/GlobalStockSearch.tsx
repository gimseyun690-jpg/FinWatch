import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { searchStocks } from '../api/stocks'
import type { StockCatalogItem, StockRef } from '../types/stock'
import { DataStatusBadge } from './DataStatusBadge'
import { Icon } from './Icon'

type Props = {
  selectedStock: StockRef
  onSelect: (stock: StockCatalogItem) => void
}

export function GlobalStockSearch({ selectedStock, onSelect }: Props) {
  const [query, setQuery] = useState('')
  const [items, setItems] = useState<StockCatalogItem[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const [error, setError] = useState('')
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const trimmed = query.trim()
    if (!trimmed) {
      setItems([])
      setLoading(false)
      setError('')
      setActiveIndex(-1)
      return
    }

    const controller = new AbortController()
    const timeout = window.setTimeout(() => {
      setLoading(true)
      setError('')
      searchStocks(trimmed, controller.signal)
        .then((result) => {
          if (controller.signal.aborted) return
          setItems(result.items)
          setActiveIndex(result.items.length > 0 ? 0 : -1)
          setOpen(true)
        })
        .catch((caught: unknown) => {
          if (caught instanceof DOMException && caught.name === 'AbortError') return
          setItems([])
          setActiveIndex(-1)
          setError(caught instanceof Error ? caught.message : '종목 검색에 실패했습니다.')
          setOpen(true)
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false)
        })
    }, 250)

    return () => {
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [query])

  useEffect(() => {
    const close = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    window.addEventListener('pointerdown', close)
    return () => window.removeEventListener('pointerdown', close)
  }, [])

  function choose(item: StockCatalogItem) {
    onSelect(item)
    setQuery('')
    setItems([])
    setOpen(false)
    setActiveIndex(-1)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      setOpen(false)
      return
    }
    if (event.key === 'ArrowDown' && items.length > 0) {
      event.preventDefault()
      setOpen(true)
      setActiveIndex((index) => (index + 1) % items.length)
      return
    }
    if (event.key === 'ArrowUp' && items.length > 0) {
      event.preventDefault()
      setOpen(true)
      setActiveIndex((index) => (index <= 0 ? items.length - 1 : index - 1))
      return
    }
    if (event.key === 'Enter' && open && activeIndex >= 0) {
      event.preventDefault()
      choose(items[activeIndex])
    }
  }

  const status = loading
    ? '검색 중'
    : error
      ? '검색 오류'
      : query.trim() && open
        ? `${items.length}개 검색 결과`
        : ''

  return (
    <div className="global-stock-search" ref={rootRef}>
      <label htmlFor="global-stock-query">종목 검색</label>
      <div className="global-search-input-row">
        <span aria-hidden="true"><Icon name="search" /></span>
        <input
          id="global-stock-query"
          type="search"
          value={query}
          placeholder="종목명 또는 심볼 검색"
          autoComplete="off"
          role="combobox"
          aria-autocomplete="list"
          aria-expanded={open && Boolean(query.trim())}
          aria-controls="global-stock-results"
          aria-activedescendant={activeIndex >= 0 ? `stock-search-option-${items[activeIndex]?.stockId}` : undefined}
          onChange={(event) => {
            setQuery(event.target.value)
            setOpen(Boolean(event.target.value.trim()))
          }}
          onFocus={() => query.trim() && setOpen(true)}
          onKeyDown={handleKeyDown}
        />
        {loading && <i className="search-spinner" aria-hidden="true" />}
      </div>

      <span className="sr-only" role="status" aria-live="polite">{status}</span>
      {open && query.trim() && (
        <div className="global-search-popover">
          {error ? (
            <p className="global-search-message" role="alert">검색을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
          ) : !loading && items.length === 0 ? (
            <p className="global-search-message">검색 결과가 없습니다. KRX·미국 종목명 또는 심볼을 확인해 주세요.</p>
          ) : (
            <ul id="global-stock-results" role="listbox" aria-label="종목 검색 결과">
              {items.map((item, index) => {
                const selected = item.market === selectedStock.market && item.symbol === selectedStock.symbol
                return (
                  <li key={`${item.market}:${item.symbol}`}>
                    <button
                      id={`stock-search-option-${item.stockId}`}
                      type="button"
                      role="option"
                      aria-selected={selected}
                      className={index === activeIndex ? 'active' : ''}
                      onMouseEnter={() => setActiveIndex(index)}
                      onClick={() => choose(item)}
                    >
                      <span className="search-symbol"><strong>{item.symbol}</strong><small>{item.market}</small></span>
                      <span className="search-company"><strong>{item.name}</strong><small>{item.englishName ?? item.exchange}</small></span>
                      <DataStatusBadge status={item.dataAvailability} />
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
          <p className="global-search-scope">전체 종목 마스터 검색 · 상세 선택 시 현재가와 관련 데이터 준비를 시작합니다.</p>
        </div>
      )}
    </div>
  )
}
