import { useEffect, useRef, useState } from 'react'
import { useOutletContext } from 'react-router'
import type { AppRouteContext } from '../app/context'
import { getStockNews, summarizeNews } from '../api/news'
import { getWatchlist } from '../api/watchlists'
import type { AiSummary, NewsArticle } from '../types/news'
import { DataStatusBadge } from './DataStatusBadge'
import { Icon } from './Icon'

type Props = {
  market?: string
  symbol?: string
  onUsageRecorded?: () => void
  watchlistMode?: boolean
}

export function AiNewsSummary({ market = '', symbol = '', onUsageRecorded, watchlistMode = false }: Props) {
  const context = useOutletContext<AppRouteContext | null>()
  const showAdminDetails = context?.showAdminDetails ?? true
  const stockKey = `${market.toUpperCase()}:${symbol.toUpperCase()}`
  const [news, setNews] = useState<NewsArticle[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [summary, setSummary] = useState<AiSummary | null>(null)
  const [loadingNews, setLoadingNews] = useState(true)
  const [summarizing, setSummarizing] = useState(false)
  const [newsRequestVersion, setNewsRequestVersion] = useState(0)
  const [newsError, setNewsError] = useState('')
  const [summaryError, setSummaryError] = useState('')
  const currentStockKeyRef = useRef(stockKey)
  const newsRequestRef = useRef<AbortController | null>(null)
  const summaryRequestRef = useRef<AbortController | null>(null)
  const newsRequestIdRef = useRef(0)
  const selectedArticle = news.find((article) => article.id === selectedId) ?? null
  const selectedOriginalUrl = safeExternalUrl(selectedArticle?.url)
  const summaryRequestAvailable = selectedArticle != null
    && (selectedArticle.aiAnalysisAllowed || selectedOriginalUrl != null)

  const [symbolNameMap, setSymbolNameMap] = useState<Record<string, string>>({})

  useEffect(() => {
    getWatchlist()
      .then((items) => {
        const map: Record<string, string> = {}
        items.forEach((item) => {
          map[item.symbol] = item.name
        })
        setSymbolNameMap(map)
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    currentStockKeyRef.current = stockKey
    newsRequestRef.current?.abort()
    summaryRequestRef.current?.abort()
    const controller = new AbortController()
    newsRequestRef.current = controller
    const requestId = ++newsRequestIdRef.current
    setLoadingNews(true)
    setNews([])
    setSelectedId(null)
    setSummary(null)
    setSummarizing(false)
    setNewsError('')
    setSummaryError('')

    if (watchlistMode) {
      getWatchlist(controller.signal)
        .then(async (watchlistItems) => {
          const results = await Promise.all(
            watchlistItems.map(async (item) => {
              try {
                const list = await getStockNews(item.market, item.symbol, controller.signal)
                return list.map(newsItem => ({
                  ...newsItem,
                  stockName: item.name
                }))
              } catch {
                return []
              }
            })
          )
          const merged = results.flat().sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime())
          if (!isCurrentRequest(controller, newsRequestRef.current) || newsRequestIdRef.current !== requestId) return
          setNews(merged)
          setSelectedId(merged[0]?.id ?? null)
        })
        .catch(() => {
          if (controller.signal.aborted || newsRequestIdRef.current !== requestId) return
          setNewsError('관심종목 뉴스를 불러오지 못했습니다.')
        })
        .finally(() => {
          if (isCurrentRequest(controller, newsRequestRef.current) && newsRequestIdRef.current === requestId) {
            newsRequestRef.current = null
            setLoadingNews(false)
          }
        })
    } else {
      getStockNews(market, symbol, controller.signal)
        .then((items) => {
          if (!isCurrentRequest(controller, newsRequestRef.current) || currentStockKeyRef.current !== stockKey || newsRequestIdRef.current !== requestId) return
          setNews(items)
          setSelectedId(items[0]?.id ?? null)
        })
        .catch(() => {
          if (controller.signal.aborted || currentStockKeyRef.current !== stockKey || newsRequestIdRef.current !== requestId) return
          setNewsError('이 종목의 뉴스를 불러오지 못했습니다. 다른 종목이나 데모 뉴스로 대체하지 않습니다.')
        })
        .finally(() => {
          if (isCurrentRequest(controller, newsRequestRef.current) && currentStockKeyRef.current === stockKey && newsRequestIdRef.current === requestId) {
            newsRequestRef.current = null
            setLoadingNews(false)
          }
        })
    }

    return () => {
      controller.abort()
      summaryRequestRef.current?.abort()
    }
  }, [market, newsRequestVersion, stockKey, symbol, watchlistMode])

  function selectNews(newsId: number) {
    summaryRequestRef.current?.abort()
    summaryRequestRef.current = null
    setSummarizing(false)
    setSelectedId(newsId)
    setSummary(null)
    setSummaryError('')
  }

  async function requestSummary() {
    if (selectedId == null || selectedArticle == null) return
    if (!selectedArticle.aiAnalysisAllowed && safeExternalUrl(selectedArticle.url) == null) {
      setSummaryError('안전한 http(s) 원문 주소가 없어 원문 수집과 AI 요약을 시작할 수 없습니다.')
      return
    }

    summaryRequestRef.current?.abort()
    const controller = new AbortController()
    summaryRequestRef.current = controller
    const requestedStockKey = stockKey
    const requestedNewsId = selectedId
    setSummarizing(true)
    setSummaryError('')
    try {
      const nextSummary = await summarizeNews(requestedNewsId, controller.signal)
      if (!isCurrentRequest(controller, summaryRequestRef.current) || currentStockKeyRef.current !== requestedStockKey) return
      if (nextSummary.newsId !== requestedNewsId) {
        throw new Error('요청한 뉴스와 다른 분석 결과가 반환되었습니다.')
      }
      setSummary(nextSummary)
      onUsageRecorded?.()
    } catch (caught: unknown) {
      if (controller.signal.aborted || currentStockKeyRef.current !== requestedStockKey) return
      const detail = caught instanceof Error ? ` ${caught.message}` : ''
      setSummaryError(
        summary == null
          ? `원문 수집 또는 AI 요약에 실패했습니다. 원문 사이트의 접근 제한이나 본문 부족 여부를 확인해 주세요.${detail}`
          : `새 원문 수집·AI 요약 요청에 실패해 마지막 성공 결과를 유지하고 있습니다.${detail}`,
      )
    } finally {
      if (isCurrentRequest(controller, summaryRequestRef.current)) {
        summaryRequestRef.current = null
        setSummarizing(false)
      }
    }
  }

  return (
    <article className="card ai-news-card" id="news">
      <div className="section-heading compact">
        <div>
          {showAdminDetails && <p className="eyebrow">AI COST OPTIMIZATION</p>}
          <h2>AI 뉴스 요약</h2>
        </div>
        {showAdminDetails && summary && (
          <span className={`cache-badge ${summary.cacheHit ? 'hit' : 'miss'}`}>
            {summary.cacheHit ? '캐시 적중 · CACHE HIT' : '신규 생성 · CACHE MISS'}
          </span>
        )}
      </div>

      <div className="ai-news-layout">
        <div className="news-picker" aria-label="뉴스 목록" aria-busy={loadingNews}>
          {loadingNews && news.length === 0 && <p role="status">뉴스를 불러오는 중입니다.</p>}
          {newsError && (
            <div className="request-error" role="alert">
              <p>{newsError}</p>
              <button type="button" className="secondary-button" onClick={() => setNewsRequestVersion((version) => version + 1)}>
                뉴스 다시 불러오기
              </button>
            </div>
          )}
          {!loadingNews && !newsError && news.length === 0 && <p>저장된 종목 뉴스가 없습니다.</p>}
          {news.map((article) => {
            const articleStockName = (article as any).stockName || symbolNameMap[article.symbol]
            return (
              <button
                type="button"
                className={selectedId === article.id ? 'selected' : ''}
                key={article.id}
                onClick={() => selectNews(article.id)}
              >
                <strong>
                  {watchlistMode && articleStockName && <span style={{ marginRight: '6px', color: '#29d4c9', fontWeight: 'bold' }}>{articleStockName}</span>}{article.title}
                </strong>
                <span>{visiblePublisher(article)} · {new Date(article.publishedAt).toLocaleDateString('ko-KR')}</span>
                <span className={`news-policy-label ${canRequestArticleSummary(article) ? 'allowed' : 'metadata-only'}`}>
                  {articleAccessLabel(article)}
                </span>
              </button>
            )
          })}
        </div>

        <div className="summary-panel">
          {!summary ? (
            <div className="summary-empty">
              <span aria-hidden="true">AI</span>
              <p>{selectedArticle == null
                ? '분석할 뉴스를 선택할 수 없습니다.'
                : selectedArticle.aiAnalysisAllowed
                  ? '허용된 본문만 분석하며, 응답에 포함된 근거 범위와 운영 정보를 함께 표시합니다.'
                  : selectedOriginalUrl
                    ? '저장된 본문은 없지만, 요청 시 백엔드가 원문 링크에서 본문을 수집해 Gemini 요약을 시도합니다. 출처 사이트의 접근 정책에 따라 실패할 수 있습니다.'
                    : '안전한 http(s) 원문 주소가 없어 원문 수집과 AI 요약을 실행할 수 없습니다.'}</p>
              {selectedArticle && (
                <div className="news-source-disclosure">
                  <span>{visiblePublisher(selectedArticle)} · {articleAccessLabel(selectedArticle)}</span>
                  {selectedOriginalUrl
                    ? <a href={selectedOriginalUrl} target="_blank" rel="noopener noreferrer">원문 보기 <Icon name="external" size={12} /></a>
                    : <span>안전한 원문 주소 확인 불가</span>}
                </div>
              )}
              {summaryError && <p className="request-error" role="alert">{summaryError}</p>}
              <button
                type="button"
                onClick={requestSummary}
                disabled={selectedId == null || summarizing || !summaryRequestAvailable}
              >
                {summarizing ? '원문 수집·분석 중…' : selectedArticle == null ? '뉴스 없음' : summaryRequestAvailable ? 'Gemini 뉴스 요약' : '원문 주소 확인 필요'}
              </button>
            </div>
          ) : (
            <SummaryResult
              summary={summary}
              article={selectedArticle}
              errorMessage={summaryError}
              summarizing={summarizing}
              onRetry={requestSummary}
              showAdminDetails={showAdminDetails}
            />
          )}
        </div>
      </div>
    </article>
  )
}

function SummaryResult({
  summary,
  article,
  errorMessage,
  summarizing,
  onRetry,
  showAdminDetails,
}: {
  summary: AiSummary
  article: NewsArticle | null
  errorMessage: string
  summarizing: boolean
  onRetry: () => void
  showAdminDetails: boolean
}) {
  const evidenceIds = [...new Set(summary.evidenceSegments.map((id) => id.trim()).filter(Boolean))]
  const sourceUrl = safeExternalUrl(article?.url)
  const evidenceMatchesArticle = article != null && summary.newsId === article.id
  const hasTraceableEvidence = evidenceIds.length > 0 && sourceUrl != null && evidenceMatchesArticle
  const isPartial = summary.analysisScope !== 'FULL_PROCESSED_TEXT' || !hasTraceableEvidence

  const sentimentColor = summary.sentiment === 'POSITIVE' ? 'var(--green)' : summary.sentiment === 'NEGATIVE' ? 'var(--red)' : 'var(--cyan)'
  const sentimentIcon = summary.sentiment === 'POSITIVE' ? '↑' : summary.sentiment === 'NEGATIVE' ? '↓' : '→'

  return (
    <div className="summary-result" aria-live="polite" aria-busy={summarizing}>

      {/* Sentiment Header */}
      <div className="summary-sentiment-header" style={{ borderLeftColor: sentimentColor }}>
        <div className="sentiment-icon" style={{ background: sentimentColor }}>{sentimentIcon}</div>
        <div>
          <span className="sentiment-label" style={{ color: sentimentColor }}>{sentimentLabel(summary.sentiment)}</span>
          {article && <span className="sentiment-source">{visiblePublisher(article)} · {new Date(article.publishedAt).toLocaleDateString('ko-KR')}</span>}
        </div>
        {sourceUrl && <a className="summary-source-link" href={sourceUrl} target="_blank" rel="noopener noreferrer">원문 보기 <Icon name="external" size={11} /></a>}
      </div>

      {/* Headline */}
      <section className="summary-headline-section" aria-labelledby={`news-conclusion-${summary.analysisId}`}>
        <p className="eyebrow">AI 한 줄 결론</p>
        <blockquote id={`news-conclusion-${summary.analysisId}`} className="summary-headline-quote">{summary.summary}</blockquote>
        {summary.keyPoints.length > 0 && (
          <div className="summary-keypoints">
            <p className="summary-keypoints-label">핵심 포인트</p>
            <ul>{summary.keyPoints.map((point, index) => <li key={`${index}-${point}`}><span className="keypoint-bullet">·</span>{point}</li>)}</ul>
          </div>
        )}
      </section>

      {/* Factor Cards */}
      <div className="factor-grid">
        <section className="factor-card positive">
          <h3>🟢 긍정 요인</h3>
          {summary.positiveFactors.length > 0
            ? <ul>{summary.positiveFactors.map((factor, index) => <li key={`${index}-${factor}`}>{factor}</li>)}</ul>
            : <p>분석 범위 내 유의미한 긍정 요인이 확인되지 않았습니다.</p>}
        </section>
        <section className="factor-card risk">
          <h3>🔴 위험 요인</h3>
          {summary.riskFactors.length > 0
            ? <ul>{summary.riskFactors.map((factor, index) => <li key={`${index}-${factor}`}>{factor}</li>)}</ul>
            : <p>분석 범위 내 유의미한 위험 요인이 확인되지 않았습니다.</p>}
        </section>
      </div>

      {/* Keywords */}
      {summary.keywords.length > 0 && (
        <div className="summary-keywords-section">
          <p className="summary-keywords-label">핵심 키워드</p>
          <div className="keyword-row">{summary.keywords.map((keyword, index) => <span key={`${index}-${keyword}`}>#{keyword}</span>)}</div>
        </div>
      )}

      {/* Evidence */}
      <section className="summary-evidence-section" aria-labelledby={`news-evidence-${summary.analysisId}`}>
        <div className="summary-evidence-header">
          <span className="eyebrow" id={`news-evidence-${summary.analysisId}`}>분석 근거</span>
          {showAdminDetails && <DataStatusBadge status={isPartial ? 'PARTIAL' : 'READY'} detail={hasTraceableEvidence ? '원문 근거 확인 가능' : '근거 확인 불가'} />}
        </div>
        {showAdminDetails && !hasTraceableEvidence && <p className="request-error" role="status">PARTIAL · 검증 가능한 근거 없음 — 참고용으로만 표시합니다.</p>}
        <div className="evidence-row">
          {showAdminDetails && <span>{isPartial ? 'PARTIAL · 일부 분석' : '전체 본문 분석'}</span>}
          {hasTraceableEvidence ? (
            <span className="evidence-chip-list" aria-label="원문에서 확인할 뉴스 근거">
              {evidenceIds.map((id) => (
                <a key={id} className="evidence-chip" href={sourceUrl} target="_blank" rel="noopener noreferrer">
                  {id} 원문 확인 <Icon name="external" size={12} />
                </a>
              ))}
            </span>
          ) : <span>근거 확인 불가</span>}
          {showAdminDetails && <span>본문 {summary.processedCharacters.toLocaleString()} / {summary.originalCharacters.toLocaleString()}자</span>}
        </div>
        {summary.mentionedCompanies.length > 0 && <p className="company-row">언급 기업 · {summary.mentionedCompanies.join(', ')}</p>}
      </section>

      <p className="analysis-disclaimer">⚠️ AI 분석은 제공된 자료의 정보 요약이며 투자 권유가 아닙니다. 투자 판단 전 원문과 공식 공시를 직접 확인하세요.</p>

      {showAdminDetails && (
        <section aria-labelledby={`news-operation-${summary.analysisId}`}>
          <h3 id={`news-operation-${summary.analysisId}`}>생성·운영 정보</h3>
          <div className="summary-meta">
            <span>생성 {formatDateTime(summary.generatedAt)}</span>
            <span>프롬프트 {summary.promptVersion}</span>
            <span>{summary.cacheHit ? '캐시 적중 (HIT)' : '신규 생성 (MISS)'}</span>
          </div>
          <div className="usage-strip">
            <div><strong>{summary.inputTokens + summary.outputTokens}</strong><span>사용 토큰</span></div>
            <div><strong>{formatCost(summary.estimatedCost, summary.costCurrency)}</strong><span>설정 단가 기반 추정 비용</span></div>
            <div><strong>{summary.responseTimeMs}ms</strong><span>응답 시간</span></div>
          </div>
        </section>
      )}

      {errorMessage && <p className="request-error" role="alert">{errorMessage}</p>}
      <button type="button" className="secondary-button" onClick={onRetry} disabled={summarizing}>
        {summarizing ? '새 분석 요청 중…' : errorMessage ? 'AI 분석 다시 시도' : '같은 뉴스 다시 분석'}
      </button>
    </div>
  )
}

function contentSourceLabel(contentSource: NewsArticle['contentSource']) {
  switch (contentSource) {
    case 'OFFICIAL_DISCLOSURE': return '공식 공시 기반'
    case 'ALLOWLIST_ARTICLE': return '허용 출처 본문'
    case 'ON_DEMAND_ARTICLE': return '요청 수집 원문'
    case 'PROVIDER_SUMMARY': return '제공 요약 기반'
    default: return '원문 링크'
  }
}

function canRequestArticleSummary(article: NewsArticle) {
  return article.aiAnalysisAllowed || safeExternalUrl(article.url) != null
}

function articleAccessLabel(article: NewsArticle) {
  const source = contentSourceLabel(article.contentSource)
  return !article.aiAnalysisAllowed && safeExternalUrl(article.url) != null
    ? `${source} · 요청 시 원문 수집`
    : source
}

function visiblePublisher(article: NewsArticle) {
  const publisher = article.publisher?.trim()
  if (publisher && !/^(naver news|naver_api_hub|naver-api-hub|finnhub)$/i.test(publisher)) return publisher
  const url = safeExternalUrl(article.url)
  if (!url) return '언론사 정보 없음'
  return new URL(url).hostname.replace(/^www\./i, '')
}

function sentimentLabel(sentiment: AiSummary['sentiment']) {
  if (sentiment === 'POSITIVE') return '긍정 이슈'
  if (sentiment === 'NEGATIVE') return '부정 이슈'
  return '중립 이슈'
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '시각 정보 없음'
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatCost(value: number, currency: string) {
  if (currency === 'USD') return `$${value.toFixed(6)}`
  return `${value.toFixed(6)} ${currency}`
}

function safeExternalUrl(value: string | null | undefined) {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}

function isCurrentRequest(controller: AbortController, current: AbortController | null) {
  return !controller.signal.aborted && current === controller
}
