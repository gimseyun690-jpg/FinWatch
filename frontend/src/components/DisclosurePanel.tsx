import { useCallback, useEffect, useRef, useState } from 'react'
import { getDataLoadJob, getDisclosures, startDisclosureLoad, summarizeDisclosure } from '../api/disclosures'
import type { Disclosure } from '../types/disclosure'
import type { AiSummary } from '../types/news'
import type { StockRef } from '../types/stock'
import { DataStatusBadge } from './DataStatusBadge'
import { Icon } from './Icon'

type Props = {
  stock: StockRef
  onUsageRecorded?: () => void
}

const POLL_INTERVAL_MS = 700
const MAX_POLL_ATTEMPTS = 30

export function DisclosurePanel({ stock, onUsageRecorded }: Props) {
  const stockKey = `${stock.market.toUpperCase()}:${stock.symbol.toUpperCase()}`
  const [items, setItems] = useState<Disclosure[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [summarizingId, setSummarizingId] = useState<number | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [summary, setSummary] = useState<AiSummary | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const currentStockKeyRef = useRef(stockKey)
  const listRequestRef = useRef<AbortController | null>(null)
  const actionRequestRef = useRef<AbortController | null>(null)
  const summaryRef = useRef<HTMLElement | null>(null)
  const evidenceDetailsRef = useRef<HTMLDetailsElement | null>(null)
  const evidenceRefs = useRef(new Map<string, HTMLElement>())

  const load = useCallback(async (signal?: AbortSignal, requestedStockKey = stockKey) => {
    const disclosures = await getDisclosures(stock.market, stock.symbol, signal)
    if (!signal?.aborted && currentStockKeyRef.current === requestedStockKey) setItems(disclosures)
  }, [stock.market, stock.symbol, stockKey])

  useEffect(() => {
    currentStockKeyRef.current = stockKey
    listRequestRef.current?.abort()
    actionRequestRef.current?.abort()
    const controller = new AbortController()
    listRequestRef.current = controller
    setLoading(true)
    setItems([])
    setSelectedId(null)
    setSummary(null)
    setSummarizingId(null)
    setMessage('')
    setError('')
    load(controller.signal, stockKey)
      .catch((reason: unknown) => {
        if (controller.signal.aborted || currentStockKeyRef.current !== stockKey) return
        setError(errorMessage(reason, '공시 목록을 불러오지 못했습니다.'))
      })
      .finally(() => {
        if (isCurrentRequest(controller, listRequestRef.current) && currentStockKeyRef.current === stockKey) {
          listRequestRef.current = null
          setLoading(false)
        }
      })
    return () => {
      controller.abort()
      actionRequestRef.current?.abort()
    }
  }, [load, stockKey])

  useEffect(() => {
    if (!summary) return
    summaryRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [summary])

  async function refresh() {
    actionRequestRef.current?.abort()
    setSummarizingId(null)
    const controller = new AbortController()
    actionRequestRef.current = controller
    const requestedStockKey = stockKey
    setRefreshing(true)
    setMessage('')
    setError('')
    try {
      let job = await startDisclosureLoad(stock.market, stock.symbol, controller.signal)
      for (let attempt = 0; job.status === 'SYNCING' && attempt < MAX_POLL_ATTEMPTS; attempt += 1) {
        await delay(POLL_INTERVAL_MS, controller.signal)
        job = await getDataLoadJob(stock.market, stock.symbol, job.jobId, controller.signal)
      }
      if (job.status === 'SYNCING') {
        throw new Error('수집 작업이 계속 진행 중입니다. 잠시 후 다시 확인해 주세요.')
      }
      const disclosureResult = job.resources.find((result) => result.resource === 'DISCLOSURES')
      if (disclosureResult?.status !== 'READY') {
        throw new Error(disclosureResult?.message ?? '공시 수집에 실패했습니다.')
      }
      await load(controller.signal, requestedStockKey)
      if (!isCurrentRequest(controller, actionRequestRef.current) || currentStockKeyRef.current !== requestedStockKey) return
      setMessage(`${providerLabel(disclosureResult.provider)}에서 공시 ${disclosureResult.imported.toLocaleString()}건을 반영했습니다.`)
    } catch (reason: unknown) {
      if (controller.signal.aborted || currentStockKeyRef.current !== requestedStockKey) return
      setError(errorMessage(reason, '공시 수집에 실패했습니다.'))
    } finally {
      if (isCurrentRequest(controller, actionRequestRef.current)) {
        actionRequestRef.current = null
        setRefreshing(false)
      }
    }
  }

  async function requestSummary(item: Disclosure) {
    actionRequestRef.current?.abort()
    setRefreshing(false)
    const controller = new AbortController()
    actionRequestRef.current = controller
    const requestedStockKey = stockKey
    setSummarizingId(item.id)
    setSelectedId(item.id)
    if (selectedId !== item.id) setSummary(null)
    setMessage('')
    setError('')
    try {
      const result = await summarizeDisclosure(item.id, controller.signal)
      if (!isCurrentRequest(controller, actionRequestRef.current) || currentStockKeyRef.current !== requestedStockKey) return
      if (result.newsId !== item.id) throw new Error('요청한 공시와 다른 분석 결과가 반환되었습니다.')
      setSummary(result)
      setMessage(result.cacheHit
        ? '저장된 공시 분석을 Redis 캐시에서 불러왔습니다.'
        : '공식 원문을 Gemini로 분석하고 결과를 저장했습니다.')
      onUsageRecorded?.()
      try {
        await load(controller.signal, requestedStockKey)
      } catch (loadReason: unknown) {
        if (controller.signal.aborted || currentStockKeyRef.current !== requestedStockKey) return
        setMessage(`${result.cacheHit ? '캐시 분석' : '신규 분석'}은 표시했지만 공시 목록 상태 갱신은 실패했습니다. ${errorMessage(loadReason, '')}`.trim())
      }
    } catch (reason: unknown) {
      if (controller.signal.aborted || currentStockKeyRef.current !== requestedStockKey) return
      setError(summary && selectedId === item.id
        ? `새 공시 요약에 실패해 마지막 성공 결과를 유지합니다. ${errorMessage(reason, 'AI 공시 요약에 실패했습니다.')}`
        : errorMessage(reason, 'AI 공시 요약에 실패했습니다.'))
    } finally {
      if (isCurrentRequest(controller, actionRequestRef.current)) {
        actionRequestRef.current = null
        setSummarizingId(null)
      }
    }
  }

  function revealEvidence(id: string) {
    if (!summary?.evidenceSegments.includes(id)) return
    revealEvidenceTarget(evidenceDetailsRef.current, evidenceRefs.current.get(id))
  }

  return (
    <article className="card disclosure-card" id="disclosures">
      <div className="section-heading compact disclosure-heading">
        <div>
          <p className="eyebrow">OFFICIAL DISCLOSURES</p>
          <h2>기업 공시</h2>
          <p>{stock.market === 'KRX' ? '금융감독원 Open DART' : '미국 SEC EDGAR'}의 공식 제출 자료입니다.</p>
        </div>
        <button type="button" className="disclosure-refresh" onClick={refresh} disabled={refreshing}>
          {refreshing ? '공시 수집 중…' : '공시 새로고침'}
        </button>
      </div>

      {message && <p className="disclosure-status" role="status">{message}</p>}
      {error && <p className="disclosure-status error" role="alert">{error}</p>}
      {loading ? (
        <div className="disclosure-empty">공시 목록을 불러오는 중입니다.</div>
      ) : items.length === 0 ? (
        <div className="disclosure-empty">
          <strong>저장된 공시가 없습니다.</strong>
          <span>LIVE 모드에서 새로고침하면 최근 1년 공시를 공식 공급자에서 가져옵니다.</span>
        </div>
      ) : (
        <div className="disclosure-list">
          {items.map((item) => {
            const originalUrl = safeExternalUrl(item.url)
            const traceableEvidenceIds = originalUrl && summary?.newsId === item.id
              ? new Set(summary.evidenceSegments.map((id) => id.trim()).filter(Boolean))
              : new Set<string>()
            return (
              <div key={item.id} className={`disclosure-row ${selectedId === item.id ? 'selected' : ''}`}>
              <div className="disclosure-row-meta">
                <span className="disclosure-provider">{providerLabel(item.source)}</span>
                {item.disclosureType && <span className="disclosure-type">{item.disclosureType}</span>}
                <span className={`disclosure-content-state ${item.aiAnalysisAllowed ? 'ready' : ''}`}>
                  {item.aiAnalysisAllowed ? '원문 확보됨' : '요약 시 원문 확보'}
                </span>
              </div>
              <strong>{item.title}</strong>
              <span>{item.publisher} · {formatDate(item.publishedAt)}</span>
              <div className="disclosure-actions">
                <button
                  type="button"
                  onClick={() => requestSummary(item)}
                  disabled={summarizingId !== null}
                >
                  {summarizingId === item.id
                    ? '원문 수집·분석 중…'
                    : summary && selectedId === item.id
                      ? '같은 공시 다시 분석'
                      : 'Gemini 공시 요약'}
                </button>
                {originalUrl
                  ? <a href={originalUrl} target="_blank" rel="noopener noreferrer">원문 보기 <Icon name="external" size={12} /></a>
                  : <span className="disclosure-link-unavailable">원문 주소 확인 필요</span>}
              </div>
              {summary && selectedId === item.id && (
                <section ref={summaryRef} className="disclosure-summary-panel" aria-live="polite">
                  <div className="disclosure-summary-heading">
                    <div>
                      <span>{sentimentLabel(summary.sentiment)}</span>
                      <h3>{item.title}</h3>
                    </div>
                    <span
                      className={`cache-badge ${summary.cacheHit ? 'hit' : 'miss'}`}
                      aria-label={summary.cacheHit ? 'CACHE HIT, 캐시 적중 결과' : 'CACHE MISS, 신규 생성 결과'}
                    >
                      {summary.cacheHit ? 'CACHE HIT' : 'CACHE MISS'}
                    </span>
                  </div>
                  <p className="disclosure-summary-text">{summary.summary}</p>
                  {summary.keyPoints.length > 0 && (
                    <ul className="disclosure-key-points">
                      {summary.keyPoints.map((point) => <li key={point}>{point}</li>)}
                    </ul>
                  )}
                  <div className="factor-grid">
                    <section className="factor-card positive">
                      <h3>주요 내용·긍정 요인</h3>
                      {summary.positiveFactors.length > 0
                        ? <ul>{summary.positiveFactors.map((factor) => <li key={factor}>{factor}</li>)}</ul>
                        : <p>명시된 긍정 요인이 없습니다.</p>}
                    </section>
                    <section className="factor-card risk">
                      <h3>위험·확인사항</h3>
                      {summary.riskFactors.length > 0
                        ? <ul>{summary.riskFactors.map((factor) => <li key={factor}>{factor}</li>)}</ul>
                        : <p>명시된 위험 요인이 없습니다.</p>}
                    </section>
                  </div>
                  <div className="evidence-row">
                    <DataStatusBadge
                      status={traceableEvidenceIds.size > 0 ? 'READY' : 'PARTIAL'}
                      detail={traceableEvidenceIds.size > 0 ? '공식 원문에서 근거 확인 가능' : '근거 확인 불가'}
                    />
                    <span>{analysisScopeLabel(summary.analysisScope)}</span>
                    {traceableEvidenceIds.size > 0
                      ? <EvidenceChips ids={summary.evidenceSegments} availableEvidenceIds={traceableEvidenceIds} onSelect={revealEvidence} />
                      : <span>안전한 공식 원문과 연결된 근거 확인 불가</span>}
                    <span>{summary.processedCharacters.toLocaleString()} / {summary.originalCharacters.toLocaleString()}자</span>
                  </div>
                  {traceableEvidenceIds.size > 0 && (
                    <details ref={evidenceDetailsRef} className="disclosure-evidence-drawer">
                      <summary>공식 원문 근거 구간 {traceableEvidenceIds.size}개 확인</summary>
                      <div>
                        {[...traceableEvidenceIds].map((id) => (
                          <p
                            key={id}
                            ref={(node) => registerEvidenceRef(evidenceRefs.current, id, node)}
                            id={`disclosure-evidence-${toDomId(id)}`}
                            tabIndex={-1}
                            data-evidence-id={id}
                          >
                            <strong>{id}</strong>
                            <span>AI 응답이 참조한 공식 제출 원문의 근거 구간</span>
                            {originalUrl && <a href={originalUrl} target="_blank" rel="noopener noreferrer">공식 원문에서 확인 <Icon name="external" size={12} /></a>}
                          </p>
                        ))}
                      </div>
                    </details>
                  )}
                  <p className="analysis-disclaimer disclosure-summary-disclaimer">
                    AI 요약은 공식 원문 확인을 보조하는 참고 정보이며, 중요한 판단 전 반드시 원문을 확인하세요.
                  </p>
                  <div className="usage-strip">
                    <div><strong>{summary.inputTokens + summary.outputTokens}</strong><span>사용 토큰</span></div>
                    <div><strong>${summary.estimatedCost.toFixed(6)}</strong><span>이번 요청 비용</span></div>
                    <div><strong>{summary.responseTimeMs}ms</strong><span>응답 시간</span></div>
                  </div>
                  <p className="disclosure-summary-audit">
                    생성 {new Date(summary.generatedAt).toLocaleString('ko-KR')} · {summary.cacheHit ? '캐시 적중 결과' : '신규 생성 결과'} · {summary.modelName} · {summary.promptVersion} · 공급자 호출 {summary.providerCallCount}회
                  </p>
                </section>
              )}
            </div>
            )
          })}
        </div>
      )}
      <p className="disclosure-note">공시는 투자 판단을 위한 원문 확인 자료이며, FinWatch의 투자 권유가 아닙니다.</p>
    </article>
  )
}

function sentimentLabel(sentiment: AiSummary['sentiment']) {
  if (sentiment === 'POSITIVE') return '긍정 요인 포함'
  if (sentiment === 'NEGATIVE') return '위험 요인 중심'
  return '중립·혼합 공시'
}

function EvidenceChips({
  ids,
  availableEvidenceIds,
  onSelect,
}: {
  ids: string[]
  availableEvidenceIds: ReadonlySet<string>
  onSelect: (id: string) => void
}) {
  const validIds = [...new Set(ids.map((id) => id.trim()).filter(Boolean))].filter((id) => availableEvidenceIds.has(id))
  if (validIds.length === 0) return null
  return (
    <span className="evidence-chip-list" aria-label="연결된 공식 원문 근거">
      {validIds.map((id) => <button key={id} type="button" className="evidence-chip" onClick={() => onSelect(id)}>{id}</button>)}
    </span>
  )
}

function registerEvidenceRef(refs: Map<string, HTMLElement>, id: string, node: HTMLElement | null) {
  if (node) refs.set(id, node)
  else refs.delete(id)
}

function revealEvidenceTarget(details: HTMLDetailsElement | null, target: HTMLElement | undefined) {
  if (!details || !target) return
  details.open = true
  window.requestAnimationFrame(() => {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    target.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'center' })
    target.focus({ preventScroll: true })
    if (!reduceMotion) {
      target.animate(
        [{ backgroundColor: 'rgba(41, 212, 201, 0.24)' }, { backgroundColor: 'rgba(41, 212, 201, 0)' }],
        { duration: 1_100, easing: 'ease-out' },
      )
    }
  })
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

function analysisScopeLabel(scope: AiSummary['analysisScope']) {
  if (scope === 'PARTIAL_PROCESSED_TEXT') return '일부 원문 분석'
  if (scope === 'FULL_PROCESSED_TEXT') return '전처리 원문 전체 분석'
  return '레거시 분석 범위'
}

function toDomId(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]/g, '-')
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value))
}

function providerLabel(provider: string | null) {
  if (provider === 'OPENDART') return 'Open DART'
  if (provider === 'SEC_EDGAR') return 'SEC EDGAR'
  if (provider === 'DATA_LOAD_CACHE') return '기존 데이터'
  return provider ?? '공식 공시 공급자'
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}

function delay(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(resolve, milliseconds)
    signal.addEventListener('abort', () => {
      window.clearTimeout(timeout)
      reject(new DOMException('Aborted', 'AbortError'))
    }, { once: true })
  })
}

function isCurrentRequest(controller: AbortController, current: AbortController | null) {
  return !controller.signal.aborted && current === controller
}
