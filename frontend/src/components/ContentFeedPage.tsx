import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ContentFeedApiError, getContentFeed } from '../api/content'
import type {
  ContentAnalysisFilter,
  ContentFeedItem,
  ContentFeedPage as ContentPage,
  ContentFeedQuery,
  ContentKind,
  ContentPeriod,
  ContentSort,
} from '../types/content'
import type { StockRef } from '../types/stock'
import { DataStatusBadge } from './DataStatusBadge'

type Props = {
  fixedStock?: StockRef
  compact?: boolean
}

type ParsedQuery = { query: ContentFeedQuery; error: string | null }

const kinds: ContentKind[] = ['ALL', 'NEWS', 'DISCLOSURE']
const analyses: ContentAnalysisFilter[] = ['ALL', 'METADATA_ONLY', 'AI_ALLOWED', 'AI_COMPLETED']
const periods: ContentPeriod[] = ['24H', '7D', '1M', '3M', 'CUSTOM']
const markets = ['ALL', 'KRX', 'NASDAQ', 'NYSE'] as const
const sizes = [10, 20, 50]
const sources = ['ALL', 'OPENDART', 'NAVER_API_HUB', 'FINNHUB', 'SEC_EDGAR', 'DEMO']
const sourcePattern = /^[A-Z0-9_-]{1,40}$/
const symbolPattern = /^[A-Z0-9][A-Z0-9.-]{0,19}$/

const kindLabels: Record<ContentKind, string> = { ALL: '전체', NEWS: '뉴스', DISCLOSURE: '공시' }
const analysisLabels: Record<ContentAnalysisFilter, string> = {
  ALL: '모든 분석 상태',
  METADATA_ONLY: '원문 링크만',
  AI_ALLOWED: 'AI 분석 가능',
  AI_COMPLETED: 'AI 분석 완료',
}
const periodLabels: Record<ContentPeriod, string> = { '24H': '24시간', '7D': '7일', '1M': '1개월', '3M': '3개월', CUSTOM: '직접 선택' }

function enumParam<T extends string>(params: URLSearchParams, name: string, allowed: readonly T[], fallback: T) {
  const raw = params.get(name)
  if (raw == null || raw === '') return { value: fallback, error: null }
  const normalized = raw.toUpperCase() as T
  return allowed.includes(normalized)
    ? { value: normalized, error: null }
    : { value: fallback, error: `${name} 값 '${raw}'은(는) 지원하지 않습니다.` }
}

function parseContentQuery(params: URLSearchParams, fixedStock?: StockRef): ParsedQuery {
  const problems: string[] = []
  const kind = enumParam(params, 'kind', kinds, 'ALL')
  const analysis = enumParam(params, 'analysis', analyses, 'ALL')
  const period = enumParam(params, 'period', periods, '7D')
  const market = fixedStock
    ? { value: fixedStock.market.toUpperCase(), error: null }
    : enumParam(params, 'market', markets, 'ALL')
  for (const parsed of [kind, analysis, period, market]) if (parsed.error) problems.push(parsed.error)

  const symbol = (fixedStock?.symbol ?? params.get('symbol') ?? '').trim().toUpperCase()
  if (symbol && market.value === 'ALL') problems.push('종목 심볼을 사용하려면 시장을 먼저 선택하세요.')
  if (symbol && !symbolPattern.test(symbol)) problems.push('종목 심볼 형식이 올바르지 않습니다.')

  const q = (params.get('q') ?? '').trim().replace(/\s+/g, ' ')
  if (q && (q.length < 2 || q.length > 100)) problems.push('검색어는 2자 이상 100자 이하로 입력하세요.')

  const source = (params.get('source') ?? 'ALL').trim().toUpperCase()
  if (source !== 'ALL' && !sourcePattern.test(source)) problems.push('출처 형식이 올바르지 않습니다.')

  const rawPage = params.get('page') ?? '0'
  const page = Number(rawPage)
  if (!/^\d+$/.test(rawPage) || !Number.isSafeInteger(page)) problems.push('페이지는 0 이상의 정수여야 합니다.')

  const rawSize = params.get('size') ?? '20'
  const size = Number(rawSize)
  if (!sizes.includes(size)) problems.push('페이지 크기는 10, 20, 50 중 하나여야 합니다.')

  const sort = params.get('sort') ?? 'publishedAt,desc'
  if (sort !== 'publishedAt,desc' && sort !== 'publishedAt,asc') problems.push('정렬은 최신순 또는 오래된순만 지원합니다.')

  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  if (period.value === 'CUSTOM') {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to)) problems.push('직접 선택 기간에는 시작일과 종료일이 필요합니다.')
    if (from && to && from > to) problems.push('시작일은 종료일보다 늦을 수 없습니다.')
  } else if (from || to) {
    problems.push('시작일과 종료일은 직접 선택 기간에서만 사용할 수 있습니다.')
  }

  return {
    query: {
      kind: kind.value,
      market: market.value,
      symbol: symbol || undefined,
      q: q || undefined,
      period: period.value,
      from: from || undefined,
      to: to || undefined,
      source,
      analysis: analysis.value,
      page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
      size: sizes.includes(size) ? size : 20,
      sort: (sort === 'publishedAt,asc' ? sort : 'publishedAt,desc') as ContentSort,
    },
    error: problems[0] ?? null,
  }
}

function safeExternalUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
}

function analysisState(item: ContentFeedItem, url: string | null) {
  if (item.aiAnalysisStatus === 'COMPLETED') return 'AI 분석 완료'
  if (item.aiAnalysisAllowed) return 'AI 분석 가능'
  if (item.kind === 'NEWS' && url) return 'AI 요약 가능'
  return 'AI 분석 불가'
}

function analysisStateClass(item: ContentFeedItem, url: string | null) {
  if (item.aiAnalysisStatus === 'COMPLETED') return 'completed'
  if (item.aiAnalysisAllowed || (item.kind === 'NEWS' && url)) return 'available'
  return 'unavailable'
}

const rightsLabels: Record<ContentFeedItem['rightsProfile'], string> = {
  METADATA_ONLY: '원문 링크만',
  TRANSIENT_AI: '일시 분석 허용',
  STORE_FOR_AI: 'AI 분석 저장 허용',
  STORE_AND_DISPLAY: '본문 표시 허용',
}

function rightsState(item: ContentFeedItem, url: string | null) {
  if (item.kind === 'NEWS' && item.rightsProfile === 'METADATA_ONLY' && url) return '요청 시 원문 수집'
  return rightsLabels[item.rightsProfile]
}

function dateTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function FeedItem({ item }: { item: ContentFeedItem }) {
  const url = safeExternalUrl(item.url)
  return (
    <article className="content-feed-item">
      <div className="content-badges">
        <span className={`content-kind ${item.kind.toLowerCase()}`}>{item.kind === 'NEWS' ? '뉴스' : '공시'}</span>
        {item.disclosureType && <span>유형 {item.disclosureType}</span>}
        {item.contentSource === 'OFFICIAL_DISCLOSURE' && <span className="official-source">공식 원문</span>}
        <span className={`rights-state ${item.rightsProfile.toLowerCase()}`}>{rightsState(item, url)}</span>
        <span className={`analysis-state ${analysisStateClass(item, url)}`}>{analysisState(item, url)}</span>
        {item.source === 'DEMO' && <DataStatusBadge status="DEMO" />}
      </div>
      <div className="content-item-main">
        <div>
          <h2>{url ? <a href={url} target="_blank" rel="noopener noreferrer">{item.title}</a> : item.title}</h2>
          <p><strong>{item.stockName}</strong> · {item.market} {item.symbol}</p>
        </div>
        <time dateTime={item.publishedAt}>{dateTime(item.publishedAt)}</time>
      </div>
      <p className="content-source">{item.publisher || '언론사 정보 없음'}</p>
      {item.summaryPreview && <p className="content-summary"><span>AI 핵심</span>{item.summaryPreview}</p>}
      {!url && <p className="content-invalid-link">안전하게 열 수 있는 원문 주소가 없습니다.</p>}
    </article>
  )
}

export function ContentFeedPage({ fixedStock, compact = false }: Props) {
  const [searchParams, setSearchParams] = useSearchParams()
  const parsed = useMemo(() => parseContentQuery(searchParams, fixedStock), [searchParams, fixedStock])
  const [pageData, setPageData] = useState<ContentPage | null>(null)
  const [pageDataKey, setPageDataKey] = useState('')
  const [loading, setLoading] = useState(false)
  const [requestError, setRequestError] = useState<{ message: string; code?: string } | null>(null)
  const [retryKey, setRetryKey] = useState(0)
  const [online, setOnline] = useState(navigator.onLine)
  const [searchDraft, setSearchDraft] = useState(parsed.query.q ?? '')
  const headingRef = useRef<HTMLHeadingElement>(null)
  const lastAnnouncedPage = useRef<number | null>(null)

  useEffect(() => setSearchDraft(parsed.query.q ?? ''), [parsed.query.q])
  const queryKey = JSON.stringify(parsed.query)

  useEffect(() => {
    const connected = () => setOnline(true)
    const disconnected = () => setOnline(false)
    window.addEventListener('online', connected)
    window.addEventListener('offline', disconnected)
    return () => {
      window.removeEventListener('online', connected)
      window.removeEventListener('offline', disconnected)
    }
  }, [])

  useEffect(() => {
    if (parsed.error) {
      setLoading(false)
      return
    }
    const controller = new AbortController()
    setLoading(true)
    setRequestError(null)
    getContentFeed(parsed.query, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setPageData(result)
        setPageDataKey(queryKey)
        if (lastAnnouncedPage.current != null && lastAnnouncedPage.current !== result.page) headingRef.current?.focus()
        lastAnnouncedPage.current = result.page
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setRequestError({
          message: error instanceof Error ? error.message : '콘텐츠 목록을 불러오지 못했습니다.',
          code: error instanceof ContentFeedApiError ? error.code ?? undefined : undefined,
        })
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [parsed.error, parsed.query, queryKey, retryKey])

  function update(values: Record<string, string | number | null>, resetPage = true) {
    const next = new URLSearchParams(searchParams)
    for (const [name, value] of Object.entries(values)) {
      if (value == null || value === '') next.delete(name)
      else next.set(name, String(value))
    }
    if (resetPage) next.set('page', '0')
    setSearchParams(next)
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault()
    update({ q: searchDraft.trim() || null })
  }

  function clearInvalidQuery() {
    const next = new URLSearchParams()
    if (fixedStock == null) next.set('market', 'ALL')
    next.set('kind', 'ALL')
    next.set('period', '7D')
    next.set('analysis', 'ALL')
    next.set('page', '0')
    next.set('size', '20')
    next.set('sort', 'publishedAt,desc')
    setSearchParams(next, { replace: true })
  }

  const visibleData = pageDataKey === queryKey ? pageData : null
  const totalStart = visibleData && visibleData.totalElements > 0 ? visibleData.page * visibleData.size + 1 : 0
  const totalEnd = visibleData ? Math.min((visibleData.page + 1) * visibleData.size, visibleData.totalElements) : 0
  const visiblePages = visibleData ? [...new Set([0, visibleData.page - 1, visibleData.page, visibleData.page + 1, visibleData.totalPages - 1])].filter((page) => page >= 0 && page < visibleData.totalPages) : []

  if (compact) {
    return (
      <section className="dashboard-content-preview card" aria-labelledby="recent-content-title">
        <div className="section-heading"><div><p className="eyebrow">LATEST CONTENT</p><h2 id="recent-content-title">최근 뉴스·공시</h2></div><Link to="/content">전체 보기</Link></div>
        {loading && !visibleData ? <div className="feed-skeleton compact" aria-label="최근 콘텐츠 불러오는 중"><span /><span /></div> : visibleData?.items.length ? (
          <><ul>{visibleData.items.slice(0, 5).map((item) => <li key={item.id}><span>{item.kind === 'NEWS' ? '뉴스' : '공시'}</span><strong>{item.title}</strong><time>{dateTime(item.publishedAt)}</time></li>)}</ul>{requestError && <p className="feed-refresh-state error">기존 결과를 유지합니다. 갱신 실패: {requestError.message}</p>}</>
        ) : requestError ? <p className="feed-error">{requestError.message}</p> : (
          <p>선택한 기간의 최근 콘텐츠가 없습니다.</p>
        )}
      </section>
    )
  }

  return (
    <section className="content-feed-page" aria-labelledby="content-feed-title">
      <div className="route-page-heading">
        <div><p className="eyebrow">MARKET INTELLIGENCE</p>{fixedStock
          ? <h2 id="content-feed-title" ref={headingRef} tabIndex={-1}>{fixedStock.market} {fixedStock.symbol} 뉴스·공시 목록</h2>
          : <h1 id="content-feed-title" ref={headingRef} tabIndex={-1}>뉴스·공시 통합 목록</h1>}<p>권리 상태와 AI 분석 가능 여부를 구분해 필요한 콘텐츠를 빠르게 찾습니다.</p></div>
      </div>

      <div className="content-primary-tabs" role="tablist" aria-label="콘텐츠 종류">
        {kinds.map((kind) => <button type="button" role="tab" key={kind} aria-selected={parsed.query.kind === kind} onClick={() => update({ kind })}>{kindLabels[kind]}</button>)}
        <button type="button" role="tab" aria-selected={parsed.query.analysis === 'AI_ALLOWED'} onClick={() => update({ analysis: 'AI_ALLOWED' })}>AI 분석 가능</button>
        <button type="button" role="tab" aria-selected={parsed.query.analysis === 'AI_COMPLETED'} onClick={() => update({ analysis: 'AI_COMPLETED' })}>AI 분석 완료</button>
      </div>

      <form className="content-filters card" onSubmit={submitSearch}>
        <label className="content-search"><span>제목·발행처 검색</span><div><input type="search" value={searchDraft} maxLength={100} placeholder="2자 이상 입력" onChange={(event) => setSearchDraft(event.target.value)} /><button type="submit">검색</button></div></label>
        {!fixedStock && <label><span>시장</span><select value={parsed.query.market} onChange={(event) => update({ market: event.target.value, symbol: null })}>{markets.map((market) => <option key={market}>{market}</option>)}</select></label>}
        {!fixedStock && <label><span>종목 심볼</span><input value={parsed.query.symbol ?? ''} disabled={parsed.query.market === 'ALL'} placeholder={parsed.query.market === 'ALL' ? '시장 선택 필요' : '예: 005930'} onChange={(event) => update({ symbol: event.target.value.toUpperCase() })} /></label>}
        <label><span>기간</span><select value={parsed.query.period} onChange={(event) => update({ period: event.target.value, from: null, to: null })}>{periods.map((period) => <option value={period} key={period}>{periodLabels[period]}</option>)}</select></label>
        {parsed.query.period === 'CUSTOM' && <label><span>시작일</span><input type="date" value={parsed.query.from ?? ''} onChange={(event) => update({ from: event.target.value })} /></label>}
        {parsed.query.period === 'CUSTOM' && <label><span>종료일</span><input type="date" value={parsed.query.to ?? ''} onChange={(event) => update({ to: event.target.value })} /></label>}
        <label><span>출처</span><select value={parsed.query.source} onChange={(event) => update({ source: event.target.value })}>{[...new Set([...sources, parsed.query.source])].map((source) => <option key={source}>{source}</option>)}</select></label>
        <label><span>분석 상태</span><select value={parsed.query.analysis} onChange={(event) => update({ analysis: event.target.value })}>{analyses.map((analysis) => <option value={analysis} key={analysis}>{analysisLabels[analysis]}</option>)}</select></label>
        <label><span>정렬</span><select value={parsed.query.sort} onChange={(event) => update({ sort: event.target.value })}><option value="publishedAt,desc">최신순</option><option value="publishedAt,asc">오래된순</option></select></label>
      </form>

      {parsed.error && <div className="feed-state error" role="alert"><strong>필터를 확인해 주세요.</strong><p>{parsed.error}</p><button type="button" onClick={clearInvalidQuery}>기본 필터로 되돌리기</button></div>}
      {!parsed.error && !online && !visibleData && <div className="feed-state offline" role="status"><strong>오프라인 상태입니다.</strong><p>인터넷에 다시 연결한 뒤 목록을 새로고침하세요.</p><button type="button" onClick={() => setRetryKey((key) => key + 1)}>다시 시도</button></div>}
      {!parsed.error && online && loading && !visibleData && <div className="feed-skeleton" aria-label="콘텐츠 목록 불러오는 중">{[1, 2, 3].map((item) => <span key={item} />)}</div>}
      {!parsed.error && online && requestError && !visibleData && <div className="feed-state error" role="alert"><strong>목록을 불러오지 못했습니다.</strong><p>{requestError.message}{requestError.code ? ` · ${requestError.code}` : ''}</p><button type="button" onClick={() => setRetryKey((key) => key + 1)}>다시 시도</button></div>}

      {!parsed.error && visibleData && (
        <>
          <div className="content-results-heading"><p><strong>{visibleData.totalElements.toLocaleString('ko-KR')}건</strong> · {totalStart.toLocaleString('ko-KR')}–{totalEnd.toLocaleString('ko-KR')}</p><label>페이지당 <select value={visibleData.size} onChange={(event) => update({ size: event.target.value })}>{sizes.map((size) => <option key={size}>{size}</option>)}</select></label></div>
          {(loading || requestError) && <div className={`feed-refresh-state${requestError ? ' error' : ''}`} role="status">{requestError ? `기존 결과를 표시 중입니다. 새로고침 실패: ${requestError.message}` : '새 조건으로 목록을 갱신하는 중입니다.'}</div>}
          {visibleData.items.length === 0 ? <div className="feed-state empty"><strong>조건에 맞는 콘텐츠가 없습니다.</strong><p>기간이나 분석 상태를 넓혀 다시 검색해 보세요.</p></div> : <div className="content-feed-list">{visibleData.items.map((item) => <FeedItem item={item} key={item.id} />)}</div>}
          <nav className="content-pagination" aria-label="콘텐츠 페이지">
            <button type="button" disabled={!visibleData.hasPrevious || loading} onClick={() => update({ page: visibleData.page - 1 }, false)}>이전</button>
            {visiblePages.map((page, index) => <span key={page}>{index > 0 && page - visiblePages[index - 1] > 1 && <i aria-hidden="true">…</i>}<button type="button" aria-current={page === visibleData.page ? 'page' : undefined} disabled={loading} aria-label={`${page + 1}페이지`} onClick={() => update({ page }, false)}>{page + 1}</button></span>)}
            <button type="button" disabled={!visibleData.hasNext || loading} onClick={() => update({ page: visibleData.page + 1 }, false)}>다음</button>
          </nav>
          <p className="sr-only" role="status" aria-live="polite">{loading ? '콘텐츠 목록 갱신 중' : `${visibleData.totalElements}건 중 ${totalStart}에서 ${totalEnd}까지 표시`}</p>
        </>
      )}
    </section>
  )
}
