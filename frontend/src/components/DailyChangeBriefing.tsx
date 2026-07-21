import { useEffect, useRef, useState } from 'react'
import { useOutletContext } from 'react-router'
import type { AppRouteContext } from '../app/context'
import { createDailyBriefing, getLatestDailyBriefing } from '../api/dailyBriefing'
import type { BriefingStatement, DailyBriefing } from '../types/dailyBriefing'
import { Icon } from './Icon'

type Props = { market: string; symbol: string; onUsageRecorded?: () => void }

export function DailyChangeBriefing({ market, symbol, onUsageRecorded }: Props) {
  const context = useOutletContext<AppRouteContext | null>()
  const showAdminDetails = context?.showAdminDetails ?? true
  const stockKey = `${market.toUpperCase()}:${symbol.toUpperCase()}`
  const [briefing, setBriefing] = useState<DailyBriefing | null>(null)
  const [initialLoading, setInitialLoading] = useState(true)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const requestRef = useRef<AbortController | null>(null)
  const currentStockKeyRef = useRef(stockKey)
  const evidenceDetailsRef = useRef<HTMLDetailsElement | null>(null)
  const evidenceRefs = useRef(new Map<string, HTMLElement>())

  useEffect(() => {
    currentStockKeyRef.current = stockKey
    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    setBriefing(null); setError(''); setInitialLoading(true); setLoading(false)
    void getLatestDailyBriefing(market, symbol, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted && currentStockKeyRef.current === stockKey) setBriefing(value)
      })
      .catch((caught: unknown) => {
        if (controller.signal.aborted || currentStockKeyRef.current !== stockKey) return
        setError(caught instanceof Error ? caught.message : '저장된 브리핑을 불러오지 못했습니다.')
      })
      .finally(() => {
        if (!controller.signal.aborted && currentStockKeyRef.current === stockKey) setInitialLoading(false)
      })
    return () => controller.abort()
  }, [market, stockKey, symbol])

  async function generate() {
    requestRef.current?.abort()
    const controller = new AbortController(); requestRef.current = controller
    const requestedStockKey = stockKey
    setInitialLoading(false); setLoading(true); setError('')
    try {
      const result = await createDailyBriefing(market, symbol, controller.signal)
      if (!controller.signal.aborted && currentStockKeyRef.current === requestedStockKey) { setBriefing(result); onUsageRecorded?.() }
    } catch (caught: unknown) {
      if (controller.signal.aborted || currentStockKeyRef.current !== requestedStockKey) return
      setError(caught instanceof Error ? caught.message : '일일 변화 브리핑을 생성하지 못했습니다.')
    } finally { if (requestRef.current === controller) { requestRef.current = null; setLoading(false) } }
  }

  function revealEvidence(id: string) {
    if (!briefing?.evidence.some((item) => item.id === id)) return
    revealEvidenceTarget(evidenceDetailsRef.current, evidenceRefs.current.get(id))
  }

  const availableEvidenceIds = new Set(briefing?.evidence.map((item) => item.id) ?? [])

  return (
    <article className="card daily-briefing-card" id="daily-briefing">
      <div className="section-heading compact daily-briefing-heading">
        <div>
          {showAdminDetails && <p className="eyebrow">EVIDENCE-BASED DAILY CHANGE</p>}
          <h2>오늘의 변화 브리핑</h2>
        </div>
        {showAdminDetails && briefing && (
          <span
            className={`cache-badge ${briefing.audit.cacheHit ? 'hit' : 'miss'}`}
            aria-label={briefing.audit.cacheHit ? 'CACHE HIT, 캐시 적중 결과' : 'CACHE MISS, 신규 생성 결과'}
          >
            {briefing.audit.cacheHit ? 'CACHE HIT' : 'CACHE MISS'}
          </span>
        )}
      </div>

      {initialLoading && !briefing ? <div className="daily-briefing-empty" role="status" aria-busy="true">
        <strong>저장된 브리핑을 확인하는 중입니다.</strong>
        <p>{market} · {symbol}의 마지막 성공 결과가 있는지 불러오고 있습니다.</p>
      </div> : !briefing ? <div className="daily-briefing-empty">
        <strong>{market} · {symbol}</strong>
        <p>서버가 가격과 기술지표 변화를 계산한 뒤, AI가 T/N/D/Q 근거 ID만 연결해 설명합니다.</p>
        {error && <p className="request-error" role="alert">{error}</p>}
        <button type="button" onClick={() => void generate()} disabled={loading}>{loading ? '변화 근거 계산 중…' : '오늘의 변화 생성'}</button>
      </div> : <div className="daily-briefing-result" aria-live="polite">
        <div className="briefing-date-row"><strong className={`relation-${briefing.relation.toLowerCase()}`}>{relationIcon(briefing.relation)} {relationLabel(briefing.relation)}</strong></div>
        <section className="briefing-headline">
          <div className="briefing-headline-meta">
            <p className="eyebrow">AI 종합 판단</p>
            <EvidenceChips
              ids={briefing.headlineEvidenceIds}
              availableEvidenceIds={availableEvidenceIds}
              onSelect={revealEvidence}
              label="결론 근거"
            />
          </div>
          <h3>{briefing.headline}</h3>
          <p>{briefing.changeSummary}</p>
          <EvidenceChips
            ids={briefing.changeSummaryEvidenceIds}
            availableEvidenceIds={availableEvidenceIds}
            onSelect={revealEvidence}
            label="변화 요약 근거"
          />
        </section>

        <div className="viewpoint-matrix">
          {briefing.viewpoints.map((item) => <div key={item.viewpoint} className={`viewpoint status-${item.status.toLowerCase()}`}>
            <span>{viewpointLabel(item.viewpoint)}</span><strong>{statusLabel(item.status)}</strong><p>{item.headline}</p><small>{changeTypeLabel(item.changeType)}</small>
            <EvidenceChips ids={item.evidenceIds} availableEvidenceIds={availableEvidenceIds} onSelect={revealEvidence} label={`${viewpointLabel(item.viewpoint)} 근거`} />
          </div>)}
        </div>

        <div className="briefing-statement-grid">
          <StatementList title="🟢 새롭게 강해진 흐름" values={briefing.newStrengths} tone="positive" availableEvidenceIds={availableEvidenceIds} onEvidenceSelect={revealEvidence} />
          <StatementList title="🔴 새 위험·주의" values={briefing.newRisks} tone="risk" availableEvidenceIds={availableEvidenceIds} onEvidenceSelect={revealEvidence} />
          <StatementList title="🔵 합치된 지표" values={briefing.alignedViews} tone="positive" availableEvidenceIds={availableEvidenceIds} onEvidenceSelect={revealEvidence} />
          <StatementList title="🟡 혼조된 지표" values={briefing.conflictingViews} tone="risk" availableEvidenceIds={availableEvidenceIds} onEvidenceSelect={revealEvidence} />
        </div>

        <details ref={evidenceDetailsRef} className="briefing-evidence"><summary>검증 근거 {briefing.evidence.length}개 보기</summary><div>
          {briefing.evidence.map((item) => {
            const sourceUrl = safeExternalUrl(item.sourceRef.url)
            return (
              <p
                key={item.id}
                ref={(node) => registerEvidenceRef(evidenceRefs.current, item.id, node)}
                id={`briefing-evidence-${toDomId(item.id)}`}
                tabIndex={-1}
                data-evidence-id={item.id}
              >
                <strong>{item.id} · {domainLabel(item.domain)}</strong>
                <span>{item.displayValue}</span>
                {sourceUrl && <a href={sourceUrl} target="_blank" rel="noopener noreferrer">원문 <Icon name="external" size={12} /></a>}
              </p>
            )
          })}
        </div></details>

        {briefing.dataLimitations.length > 0 && <div className="briefing-limitations">{briefing.dataLimitations.map((item) => <p key={item}>한계 · {item}</p>)}</div>}
        <p className="analysis-disclaimer">{briefing.disclaimer}</p>
        {showAdminDetails && (
          <>
            <div className="usage-strip">
              <div><strong>{briefing.audit.inputTokens + briefing.audit.outputTokens}</strong><span>이번 요청 토큰</span></div>
              <div><strong>${briefing.audit.estimatedCost.toFixed(6)}</strong><span>이번 요청 비용</span></div>
              <div><strong>${briefing.audit.savedEstimatedCost.toFixed(6)}</strong><span>캐시 절감 추정</span></div>
            </div>
            <details className="briefing-audit">
              <summary>AI 감사 정보</summary>
              <p>{briefing.audit.modelName} · {briefing.audit.promptVersion}</p>
              <p>{briefing.audit.calculationVersion} · {briefing.audit.briefingInputVersion}</p>
              <p>근거 {briefing.audit.evidenceCount}개 · 제외 {briefing.audit.excludedContentCount}건 · {briefing.audit.responseTimeMs}ms</p>
              <p>데이터 기준 {new Date(briefing.audit.latestRecordedAt).toLocaleString('ko-KR')}</p>
              <p>생성 {new Date(briefing.audit.generatedAt).toLocaleString('ko-KR')} · {briefing.audit.cacheHit ? '캐시 적중 결과' : '신규 생성 결과'}</p>
            </details>
          </>
        )}
        {error && <p className="request-error" role="alert">{error}</p>}
        <button type="button" className="secondary-button" onClick={() => void generate()} disabled={loading}>{loading ? '새 스냅샷 확인 중…' : '현재 근거로 다시 생성'}</button>
      </div>}
    </article>
  )
}

function StatementList({
  title,
  values,
  tone,
  availableEvidenceIds,
  onEvidenceSelect,
}: {
  title: string
  values: BriefingStatement[]
  tone: string
  availableEvidenceIds: ReadonlySet<string>
  onEvidenceSelect: (id: string) => void
}) {
  return (
    <section className={tone}>
      <h3>{title}</h3>
      {values.length === 0 ? <p>해당 항목이 없습니다.</p> : <ul>{values.map((item) => (
        <li key={`${item.text}-${item.evidenceIds.join('-')}`}>
          <span>{item.text}</span>
          <EvidenceChips ids={item.evidenceIds} availableEvidenceIds={availableEvidenceIds} onSelect={onEvidenceSelect} label={`${title} 근거`} />
        </li>
      ))}</ul>}
    </section>
  )
}

function EvidenceChips({
  ids,
  availableEvidenceIds,
  onSelect,
  label,
}: {
  ids: string[]
  availableEvidenceIds: ReadonlySet<string>
  onSelect: (id: string) => void
  label: string
}) {
  const validIds = [...new Set(ids)].filter((id) => availableEvidenceIds.has(id))
  if (validIds.length === 0) return null
  return (
    <div className="evidence-chip-list" aria-label={label}>
      {validIds.map((id) => <button key={id} type="button" className="evidence-chip" onClick={() => onSelect(id)}>{id}</button>)}
    </div>
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

function safeExternalUrl(value: string | undefined) {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}

function toDomId(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]/g, '-')
}
function relationLabel(value: DailyBriefing['relation']) { return ({ ALIGNED: '지표 합치', CONFLICTING: '지표 혼조', PARTIAL: '부분 근거', INSUFFICIENT: '근거 부족' })[value] }
function relationIcon(value: DailyBriefing['relation']) { return ({ ALIGNED: '✅', CONFLICTING: '⚡', PARTIAL: '🔶', INSUFFICIENT: '❓' })[value] ?? '' }
function viewpointLabel(value: string) { return ({ TREND: '추세', MOMENTUM: '모멘텀', OVERHEAT: '과열', VOLATILITY: '변동성', VOLUME: '거래량', NEWS: '뉴스', DISCLOSURE: '공시' } as Record<string, string>)[value] ?? value }
function statusLabel(value: string) { return ({ POSITIVE: '긍정', CAUTION: '주의', NEUTRAL: '중립', CONFIRMING: '동반', DIVERGING: '미동반', MIXED: '혼재', INSUFFICIENT: '없음' } as Record<string, string>)[value] ?? value }
function domainLabel(value: string) { return ({ TECHNICAL: '기술', NEWS: '뉴스', DISCLOSURE: '공시', QUALITY: '품질' } as Record<string, string>)[value] ?? value }
function changeTypeLabel(value: string) { return ({ INSUFFICIENT: '분석 대상 없음', NEW: '신규', UNCHANGED: '유지', REVERSED: '전환', STRENGTHENED: '강화', WEAKENED: '약화' } as Record<string, string>)[value] ?? value }
