import { useEffect, useRef, useState } from 'react'
import { createDailyBriefing, getLatestDailyBriefing } from '../api/dailyBriefing'
import type { BriefingStatement, DailyBriefing } from '../types/dailyBriefing'

type Props = { market: string; symbol: string; onUsageRecorded?: () => void }

export function DailyChangeBriefing({ market, symbol, onUsageRecorded }: Props) {
  const [briefing, setBriefing] = useState<DailyBriefing | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const requestRef = useRef<AbortController | null>(null)

  useEffect(() => {
    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    setBriefing(null); setError('')
    void getLatestDailyBriefing(market, symbol, controller.signal)
      .then((value) => { if (!controller.signal.aborted) setBriefing(value) })
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') return
        if (!controller.signal.aborted) setError(caught instanceof Error ? caught.message : '저장된 브리핑을 불러오지 못했습니다.')
      })
    return () => controller.abort()
  }, [market, symbol])

  async function generate() {
    requestRef.current?.abort()
    const controller = new AbortController(); requestRef.current = controller
    setLoading(true); setError('')
    try {
      const result = await createDailyBriefing(market, symbol, controller.signal)
      if (!controller.signal.aborted) { setBriefing(result); onUsageRecorded?.() }
    } catch (caught: unknown) {
      if (caught instanceof DOMException && caught.name === 'AbortError') return
      setError(caught instanceof Error ? caught.message : '일일 변화 브리핑을 생성하지 못했습니다.')
    } finally { if (requestRef.current === controller) { requestRef.current = null; setLoading(false) } }
  }

  return (
    <article className="card daily-briefing-card" id="daily-briefing">
      <div className="section-heading compact daily-briefing-heading">
        <div><p className="eyebrow">EVIDENCE-BASED DAILY CHANGE</p><h2>오늘의 변화 브리핑</h2><p>완성된 두 일봉과 새 뉴스·공시만 비교합니다.</p></div>
        {briefing && <span className={`cache-badge ${briefing.audit.cacheHit ? 'hit' : 'miss'}`}>{briefing.audit.cacheHit ? 'CACHE HIT' : 'SAVED RESULT'}</span>}
      </div>

      {!briefing ? <div className="daily-briefing-empty">
        <strong>{market} · {symbol}</strong>
        <p>서버가 가격과 기술지표 변화를 계산한 뒤, AI가 T/N/D/Q 근거 ID만 연결해 설명합니다.</p>
        {error && <p className="request-error" role="alert">{error}</p>}
        <button type="button" onClick={() => void generate()} disabled={loading}>{loading ? '변화 근거 계산 중…' : '오늘의 변화 생성'}</button>
      </div> : <div className="daily-briefing-result" aria-live="polite">
        <div className="briefing-date-row"><span>{briefing.previousTradingDate ?? '기준 없음'} → {briefing.currentTradingDate}</span><strong className={`relation-${briefing.relation.toLowerCase()}`}>{relationLabel(briefing.relation)}</strong></div>
        {briefing.staleBriefing && <p className="briefing-warning">현재 일봉과 다른 저장 결과입니다. 다시 생성해 최신 근거를 확인하세요.</p>}
        <section className="briefing-headline"><small>{briefing.headlineEvidenceIds.join(' · ')}</small><h3>{briefing.headline}</h3><p>{briefing.changeSummary}</p></section>

        <div className="viewpoint-matrix">
          {briefing.viewpoints.map((item) => <div key={item.viewpoint} className={`viewpoint status-${item.status.toLowerCase()}`}>
            <span>{viewpointLabel(item.viewpoint)}</span><strong>{statusLabel(item.status)}</strong><p>{item.headline}</p><small>{item.changeType} · {item.evidenceIds.join(', ') || '근거 부족'}</small>
          </div>)}
        </div>

        <div className="briefing-statement-grid">
          <StatementList title="새롭게 강해진 흐름" values={briefing.newStrengths} tone="positive" />
          <StatementList title="새 위험·주의" values={briefing.newRisks} tone="risk" />
          <StatementList title="관점 일치" values={briefing.alignedViews} tone="positive" />
          <StatementList title="관점 충돌" values={briefing.conflictingViews} tone="risk" />
        </div>

        <details className="briefing-evidence"><summary>검증 근거 {briefing.evidence.length}개 보기</summary><div>
          {briefing.evidence.map((item) => <p key={item.id}><strong>{item.id} · {domainLabel(item.domain)}</strong><span>{item.displayValue}</span>{item.sourceRef.url && <a href={item.sourceRef.url} target="_blank" rel="noreferrer">원문 ↗</a>}</p>)}
        </div></details>

        {briefing.dataLimitations.length > 0 && <div className="briefing-limitations">{briefing.dataLimitations.map((item) => <p key={item}>한계 · {item}</p>)}</div>}
        <div className="usage-strip">
          <div><strong>{briefing.audit.inputTokens + briefing.audit.outputTokens}</strong><span>이번 요청 토큰</span></div>
          <div><strong>${briefing.audit.estimatedCost.toFixed(6)}</strong><span>이번 요청 비용</span></div>
          <div><strong>${briefing.audit.savedEstimatedCost.toFixed(6)}</strong><span>캐시 절감 추정</span></div>
        </div>
        <details className="briefing-audit"><summary>AI 감사 정보</summary><p>{briefing.audit.modelName} · {briefing.audit.promptVersion}</p><p>{briefing.audit.calculationVersion} · {briefing.audit.briefingInputVersion}</p><p>근거 {briefing.audit.evidenceCount}개 · 제외 {briefing.audit.excludedContentCount}건 · {briefing.audit.responseTimeMs}ms</p><p>기준 {new Date(briefing.audit.latestRecordedAt).toLocaleString('ko-KR')}</p></details>
        <p className="analysis-disclaimer">{briefing.disclaimer}</p>
        {error && <p className="request-error" role="alert">{error}</p>}
        <button type="button" className="secondary-button" onClick={() => void generate()} disabled={loading}>{loading ? '새 스냅샷 확인 중…' : '현재 근거로 다시 생성'}</button>
      </div>}
    </article>
  )
}

function StatementList({ title, values, tone }: { title: string; values: BriefingStatement[]; tone: string }) {
  return <section className={tone}><h3>{title}</h3>{values.length === 0 ? <p>해당 항목이 없습니다.</p> : <ul>{values.map((item) => <li key={`${item.text}-${item.evidenceIds.join('-')}`}><span>{item.text}</span><small>{item.evidenceIds.join(', ')}</small></li>)}</ul>}</section>
}
function relationLabel(value: DailyBriefing['relation']) { return ({ ALIGNED: '관점 일치', CONFLICTING: '관점 충돌', PARTIAL: '부분 근거', INSUFFICIENT: '근거 부족' })[value] }
function viewpointLabel(value: string) { return ({ TREND: '추세', MOMENTUM: '모멘텀', OVERHEAT: '과열', VOLATILITY: '변동성', VOLUME: '거래량', NEWS: '뉴스', DISCLOSURE: '공시' } as Record<string, string>)[value] ?? value }
function statusLabel(value: string) { return ({ POSITIVE: '긍정', CAUTION: '주의', NEUTRAL: '중립', CONFIRMING: '확인', DIVERGING: '괴리', MIXED: '혼재', INSUFFICIENT: '부족' } as Record<string, string>)[value] ?? value }
function domainLabel(value: string) { return ({ TECHNICAL: '기술', NEWS: '뉴스', DISCLOSURE: '공시', QUALITY: '품질' } as Record<string, string>)[value] ?? value }
