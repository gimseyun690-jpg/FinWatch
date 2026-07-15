import { useEffect, useState } from 'react'
import { getStockNews, summarizeNews } from '../api/news'
import type { AiSummary, NewsArticle } from '../types/news'

type Props = {
  market: string
  symbol: string
  onUsageRecorded?: () => void
}

const demoNews: NewsArticle[] = [
  {
    id: 1,
    symbol: '000660',
    title: 'SK하이닉스, HBM 수요 증가로 실적 전망 상향',
    publisher: 'FinWatch Demo News',
    url: 'https://example.com/news/demo',
    publishedAt: '2026-07-13T01:00:00Z',
    summaryAvailable: true,
    source: 'DEMO',
    contentSource: 'PROVIDER_SUMMARY',
    rightsProfile: 'STORE_FOR_AI',
    aiAnalysisAllowed: true,
    fetchedAt: '2026-07-13T01:00:00Z',
  },
]

const demoSummary: AiSummary = {
  analysisId: 1,
  newsId: 1,
  symbol: '000660',
  summary: 'HBM 수요 증가와 메모리 업황 회복이 실적 개선의 주요 동력으로 언급됐습니다. 단기 변동성과 공급 경쟁은 함께 확인해야 합니다.',
  keyPoints: ['글로벌 AI 서버 투자가 HBM 수요를 견인', '고부가 제품 비중 확대', '단기 변동성과 공급 경쟁에 주의'],
  positiveFactors: ['HBM 수요 증가', '메모리 업황 회복 기대'],
  riskFactors: ['단기 주가 변동성', '공급 경쟁 심화 가능성'],
  mentionedCompanies: ['SK하이닉스'],
  evidenceSegments: ['S1'],
  keywords: ['HBM', '메모리', '실적'],
  sentiment: 'POSITIVE',
  modelName: 'finwatch-demo-analyzer-v2',
  promptVersion: 'news-analysis-v2',
  analysisScope: 'FULL_PROCESSED_TEXT',
  originalCharacters: 258,
  processedCharacters: 223,
  providerCallCount: 1,
  cacheHit: false,
  inputTokens: 94,
  outputTokens: 54,
  estimatedCost: 0.000031,
  costCurrency: 'USD',
  responseTimeMs: 42,
  generatedAt: '2026-07-13T03:00:00Z',
}

export function AiNewsSummary({ market, symbol, onUsageRecorded }: Props) {
  const [news, setNews] = useState<NewsArticle[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [summary, setSummary] = useState<AiSummary | null>(null)
  const [loadingNews, setLoadingNews] = useState(true)
  const [summarizing, setSummarizing] = useState(false)
  const [demoRequestCount, setDemoRequestCount] = useState(0)
  const [demoMode, setDemoMode] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const selectedArticle = news.find((article) => article.id === selectedId) ?? null

  useEffect(() => {
    const controller = new AbortController()
    setLoadingNews(true)
    setSummary(null)
    setErrorMessage('')
    setDemoRequestCount(0)
    getStockNews(market, symbol, controller.signal)
      .then((items) => {
        setDemoMode(false)
        setNews(items)
        setSelectedId(items[0]?.id ?? null)
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (market === 'KRX' && symbol === '000660') {
          setDemoMode(true)
          setNews(demoNews)
          setSelectedId(demoNews[0].id)
        } else {
          setDemoMode(false)
          setNews([])
          setSelectedId(null)
          setErrorMessage('이 종목의 뉴스를 불러오지 못했습니다. 다른 종목의 데모 뉴스로 대체하지 않습니다.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingNews(false)
      })
    return () => controller.abort()
  }, [market, symbol])

  function selectNews(newsId: number) {
    setSelectedId(newsId)
    setSummary(null)
    setErrorMessage('')
  }

  async function requestSummary() {
    if (selectedId == null) return
    if (!selectedArticle?.aiAnalysisAllowed) {
      setErrorMessage('이 출처는 원문 링크만 제공하므로 AI 본문 분석을 실행할 수 없습니다.')
      return
    }
    setSummarizing(true)
    setErrorMessage('')
    try {
      if (demoMode) throw new Error('demo mode')
      setSummary(await summarizeNews(selectedId))
      onUsageRecorded?.()
    } catch (error: unknown) {
      if (demoMode) {
        const cacheHit = demoRequestCount > 0
        setSummary({
          ...demoSummary,
          newsId: selectedId,
          cacheHit,
          inputTokens: cacheHit ? 0 : demoSummary.inputTokens,
          outputTokens: cacheHit ? 0 : demoSummary.outputTokens,
          estimatedCost: cacheHit ? 0 : demoSummary.estimatedCost,
          responseTimeMs: cacheHit ? 4 : demoSummary.responseTimeMs,
        })
        setDemoRequestCount((count) => count + 1)
        onUsageRecorded?.()
      } else {
        const detail = error instanceof Error ? ` (${error.message})` : ''
        setErrorMessage(`AI 분석 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.${detail}`)
      }
    } finally {
      setSummarizing(false)
    }
  }

  return (
    <article className="card ai-news-card" id="news">
      <div className="section-heading compact">
        <div>
          <p className="eyebrow">AI COST OPTIMIZATION</p>
          <h2>근거 기반 AI 뉴스 분석 {demoMode && <small>· DEMO</small>}</h2>
        </div>
        {summary && (
          <span className={`cache-badge ${summary.cacheHit ? 'hit' : 'miss'}`}>
            {summary.cacheHit ? 'CACHE HIT' : 'CACHE MISS'}
          </span>
        )}
      </div>

      <div className="ai-news-layout">
        <div className="news-picker" aria-label="뉴스 목록">
          {loadingNews && <p>뉴스를 불러오는 중입니다.</p>}
          {!loadingNews && news.length === 0 && <p>저장된 종목 뉴스가 없습니다.</p>}
          {!loadingNews && news.map((article) => (
            <button
              type="button"
              className={selectedId === article.id ? 'selected' : ''}
              key={article.id}
              onClick={() => selectNews(article.id)}
            >
              <strong>{article.title}</strong>
              <span>{article.publisher} · {new Date(article.publishedAt).toLocaleDateString('ko-KR')}</span>
              <span className={`news-policy-label ${article.aiAnalysisAllowed ? 'allowed' : 'metadata-only'}`}>
                {contentSourceLabel(article.contentSource)}
              </span>
            </button>
          ))}
        </div>

        <div className="summary-panel">
          {!summary ? (
            <div className="summary-empty">
              <span aria-hidden="true">AI</span>
              <p>{selectedArticle == null
                ? '이 종목에 저장된 뉴스가 없어 AI 분석을 실행할 수 없습니다.'
                : selectedArticle.aiAnalysisAllowed
                  ? '허용된 본문을 구간별로 분석하고 근거 범위와 비용을 함께 기록합니다.'
                  : '이 출처는 메타데이터와 원문 링크만 제공하며 본문은 AI에 전달하지 않습니다.'}</p>
              {selectedArticle && (
                <div className="news-source-disclosure">
                  <span>{selectedArticle.source} · {contentSourceLabel(selectedArticle.contentSource)}</span>
                  <a href={selectedArticle.url} target="_blank" rel="noreferrer">원문 보기</a>
                </div>
              )}
              {errorMessage && <p className="request-error" role="alert">{errorMessage}</p>}
              <button
                type="button"
                onClick={requestSummary}
                disabled={selectedId == null || summarizing || !selectedArticle?.aiAnalysisAllowed}
              >
                {summarizing ? '분석 생성 중…' : selectedArticle == null ? '뉴스 없음' : selectedArticle.aiAnalysisAllowed ? 'AI 분석 실행' : '원문 분석 불가'}
              </button>
            </div>
          ) : (
            <div className="summary-result" aria-live="polite">
              <div className="summary-meta">
                <span>{sentimentLabel(summary.sentiment)}</span>
                <span>{summary.modelName} · {summary.providerCallCount}회 호출</span>
              </div>
              <p>{summary.summary}</p>
              <ul>{summary.keyPoints.map((point) => <li key={point}>{point}</li>)}</ul>

              <div className="factor-grid">
                <section className="factor-card positive">
                  <h3>긍정 요인</h3>
                  {summary.positiveFactors.length > 0
                    ? <ul>{summary.positiveFactors.map((factor) => <li key={factor}>{factor}</li>)}</ul>
                    : <p>명시된 긍정 요인이 없습니다.</p>}
                </section>
                <section className="factor-card risk">
                  <h3>위험 요인</h3>
                  {summary.riskFactors.length > 0
                    ? <ul>{summary.riskFactors.map((factor) => <li key={factor}>{factor}</li>)}</ul>
                    : <p>명시된 위험 요인이 없습니다.</p>}
                </section>
              </div>

              <div className="evidence-row">
                <span>{summary.analysisScope === 'PARTIAL_PROCESSED_TEXT' ? '일부 본문 분석' : '전처리 본문 전체 분석'}</span>
                <span>근거 {summary.evidenceSegments.join(', ') || '없음'}</span>
                <span>{summary.processedCharacters.toLocaleString()} / {summary.originalCharacters.toLocaleString()}자</span>
              </div>
              {summary.mentionedCompanies.length > 0 && (
                <p className="company-row">언급 기업 · {summary.mentionedCompanies.join(', ')}</p>
              )}
              <div className="keyword-row">{summary.keywords.map((keyword) => <span key={keyword}>#{keyword}</span>)}</div>
              <div className="usage-strip">
                <div><strong>{summary.inputTokens + summary.outputTokens}</strong><span>사용 토큰</span></div>
                <div><strong>${summary.estimatedCost.toFixed(6)}</strong><span>이번 요청 비용</span></div>
                <div><strong>{summary.responseTimeMs}ms</strong><span>응답 시간</span></div>
              </div>
              <p className="analysis-disclaimer">AI 분석은 정보 요약이며 투자 권유가 아닙니다. 원문과 공시를 함께 확인하세요.</p>
              <button type="button" className="secondary-button" onClick={requestSummary} disabled={summarizing}>
                {summarizing ? '확인 중…' : '같은 뉴스 다시 분석'}
              </button>
            </div>
          )}
        </div>
      </div>
    </article>
  )
}

function contentSourceLabel(contentSource: NewsArticle['contentSource']) {
  switch (contentSource) {
    case 'OFFICIAL_DISCLOSURE': return '공식 공시 기반'
    case 'ALLOWLIST_ARTICLE': return '허용 출처 본문'
    case 'PROVIDER_SUMMARY': return '제공 요약 기반'
    default: return '원문 링크 전용'
  }
}

function sentimentLabel(sentiment: AiSummary['sentiment']) {
  if (sentiment === 'POSITIVE') return '긍정 이슈'
  if (sentiment === 'NEGATIVE') return '부정 이슈'
  return '중립 이슈'
}
