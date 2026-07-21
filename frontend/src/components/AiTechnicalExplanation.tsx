import { useEffect, useRef, useState } from 'react'
import { useOutletContext } from 'react-router'
import type { AppRouteContext } from '../app/context'
import { explainTechnical } from '../api/technicalExplanation'
import type { TechnicalExplanation, TechnicalEvidence, TechnicalSignalExplanation } from '../types/technicalExplanation'
import { DataStatusBadge } from './DataStatusBadge'
import { resolveDataStatus } from './dataStatus'

type Props = {
  market: string
  symbol: string
  onUsageRecorded?: () => void
}

export function AiTechnicalExplanation({ market, symbol, onUsageRecorded }: Props) {
  const context = useOutletContext<AppRouteContext | null>()
  const showAdminDetails = context?.showAdminDetails ?? true
  const stockKey = `${market.toUpperCase()}:${symbol.toUpperCase()}`
  const [explanation, setExplanation] = useState<TechnicalExplanation | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const requestRef = useRef<AbortController | null>(null)
  const currentStockKeyRef = useRef(stockKey)
  const evidenceDetailsRef = useRef<HTMLDetailsElement | null>(null)
  const evidenceRefs = useRef(new Map<string, HTMLElement>())

  useEffect(() => {
    currentStockKeyRef.current = stockKey
    requestRef.current?.abort()
    setExplanation(null)
    setLoading(false)
    setError('')
  }, [stockKey])

  useEffect(() => () => requestRef.current?.abort(), [])

  async function requestExplanation() {
    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    const requestedStockKey = stockKey
    setLoading(true)
    setError('')
    try {
      const result = await explainTechnical(market, symbol, controller.signal)
      if (controller.signal.aborted || currentStockKeyRef.current !== requestedStockKey) return
      setExplanation(result)
      onUsageRecorded?.()
    } catch (caught: unknown) {
      if (controller.signal.aborted || currentStockKeyRef.current !== requestedStockKey) return
      const detail = caught instanceof Error ? caught.message : 'AI 기술지표 해설을 생성하지 못했습니다.'
      setError(explanation
        ? `새 해설 요청에 실패해 마지막 성공 결과를 유지합니다. ${detail}`
        : detail)
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null
        setLoading(false)
      }
    }
  }

  function revealEvidence(id: string) {
    if (!explanation?.evidence.some((item) => item.id === id)) return
    revealEvidenceTarget(evidenceDetailsRef.current, evidenceRefs.current.get(id))
  }

  const availableEvidenceIds = new Set(explanation?.evidence.map((item) => item.id) ?? [])

  return (
    <article className="card ai-technical-card" id="ai-technical">
      <div className="section-heading compact">
        <div>
          {showAdminDetails && <p className="eyebrow">EVIDENCE-BASED TECHNICAL AI</p>}
          <h2>AI 기술 분석 해설</h2>
        </div>
        {showAdminDetails && explanation && (
          <span
            className={`cache-badge ${explanation.cacheHit ? 'hit' : 'miss'}`}
            aria-label={explanation.cacheHit ? 'CACHE HIT, 캐시 적중 결과' : 'CACHE MISS, 신규 생성 결과'}
          >
            {explanation.cacheHit ? 'CACHE HIT' : 'CACHE MISS'}
          </span>
        )}
      </div>

      {!explanation ? (
        <div className="technical-ai-empty">
          <div><strong>{market} · {symbol}</strong><span>서버가 계산한 일봉 지표만 AI 모델에 전달합니다.</span></div>
          <p>AI는 지표 값을 다시 계산하거나 종목을 추천하지 않고, 추세·모멘텀·변동성·거래량의 근거와 충돌을 설명합니다.</p>
          {error && <p className="request-error" role="alert">{error}</p>}
          <button type="button" onClick={() => void requestExplanation()} disabled={loading}>
            {loading ? '근거 해설 생성 중…' : 'AI 기술 해설 실행'}
          </button>
        </div>
      ) : (
        <div className="technical-ai-result" aria-live="polite">
          {/* Signal Strength Gauge */}
          <SignalGauge signal={explanation.summarySignal} summary={explanation.summary} />

          {/* Enhanced Explanation Cards */}
          <div className="technical-explanation-grid">
            <TrendCard text={explanation.trendExplanation} evidence={explanation.evidence} />
            <MomentumCard text={explanation.momentumExplanation} evidence={explanation.evidence} />
            <VolatilityCard text={explanation.volatilityExplanation} evidence={explanation.evidence} />
            <VolumeCard text={explanation.volumeExplanation} evidence={explanation.evidence} />
          </div>

          <div className="technical-signal-grid">
            <SignalList
              title="지지 근거"
              items={explanation.supportingSignals}
              className="supporting"
              availableEvidenceIds={availableEvidenceIds}
              onEvidenceSelect={revealEvidence}
            />
            <SignalList
              title="충돌·주의 신호"
              items={explanation.conflictingSignals}
              className="conflicting"
              availableEvidenceIds={availableEvidenceIds}
              onEvidenceSelect={revealEvidence}
            />
          </div>

          <details ref={evidenceDetailsRef} className="technical-evidence-drawer">
            <summary>서버 계산 근거 {explanation.evidence.length}개 보기</summary>
            <div>
              {explanation.evidence.map((item) => (
                <p
                  key={item.id}
                  ref={(node) => registerEvidenceRef(evidenceRefs.current, item.id, node)}
                  id={`technical-evidence-${toDomId(item.id)}`}
                  tabIndex={-1}
                  data-evidence-id={item.id}
                >
                  <strong>{item.id} · {indicatorLabel(item.indicator)}</strong><span>{item.displayValue}</span>
                </p>
              ))}
            </div>
          </details>

          {(explanation.riskNotes.length > 0 || explanation.dataLimitations.length > 0) && (
            <div className="technical-limitations">
              {[...explanation.riskNotes, ...explanation.dataLimitations].map((item) => <p key={item}>주의 · {item}</p>)}
            </div>
          )}

          <p className="analysis-disclaimer">
            {explanation.disclaimer}
            {showAdminDetails && ` · ${explanation.calculationVersion} · ${explanation.promptVersion} · 입력 ${explanation.inputHash.slice(0, 12)}…`}
          </p>
          {showAdminDetails && (
            <>
              <div className="technical-ai-audit analysis-generation-meta">
                <span>{explanation.symbol} · {explanation.interval}</span>
                <DataStatusBadge status={resolveDataStatus(explanation.freshness)} detail={explanation.source} />
                <span>데이터 기준 {new Date(explanation.latestRecordedAt).toLocaleString('ko-KR')}</span>
                <span>생성 {new Date(explanation.generatedAt).toLocaleString('ko-KR')}</span>
                <span>{explanation.modelName}</span>
                <span>{explanation.cacheHit ? '캐시 적중 결과' : '신규 생성 결과'}</span>
              </div>
              <div className="usage-strip">
                <div><strong>{explanation.inputTokens + explanation.outputTokens}</strong><span>사용 토큰</span></div>
                <div><strong>${explanation.estimatedCost.toFixed(6)}</strong><span>이번 요청 비용</span></div>
                <div><strong>{explanation.responseTimeMs}ms</strong><span>응답 시간</span></div>
              </div>
            </>
          )}
          {error && <p className="request-error" role="alert">{error}</p>}
          <button type="button" className="secondary-button" onClick={() => void requestExplanation()} disabled={loading}>
            {loading ? '확인 중…' : error ? 'AI 기술 해설 다시 시도' : '같은 스냅샷 다시 해설'}
          </button>
        </div>
      )}
    </article>
  )
}

/* ── Helpers ── */

function getEv(evidence: TechnicalEvidence[], indicator: string, key: string): number | null {
  const item = evidence.find((e) => e.indicator === indicator)
  if (!item) return null
  const val = item.values[key]
  if (val == null) return null
  const n = parseFloat(val)
  return isNaN(n) ? null : n
}

/* ── Signal Strength Gauge ── */

function SignalGauge({ signal, summary }: { signal: TechnicalExplanation['summarySignal']; summary: string }) {
  const position = signal === 'BUY' ? 20 : signal === 'SELL' ? 80 : 50
  return (
    <div className={`signal-gauge-container signal-${signal.toLowerCase()}`}>
      <div className="signal-gauge-header">
        <span className="signal-gauge-label">종합 기술 신호</span>
        <span className={`signal-gauge-value signal-${signal.toLowerCase()}`}>{signalLabel(signal)}</span>
      </div>
      <div className="signal-gauge-track" aria-label={`신호 강도: ${signalLabel(signal)}`}>
        <div className="signal-gauge-zone zone-buy">매수</div>
        <div className="signal-gauge-zone zone-neutral">중립</div>
        <div className="signal-gauge-zone zone-sell">매도</div>
        <div className="signal-gauge-marker" style={{ left: `${position}%` }} />
      </div>
      <p className="signal-gauge-summary">{summary}</p>
    </div>
  )
}

/* ── Enhanced Cards ── */

function TrendCard({ text, evidence }: { text: string; evidence: TechnicalEvidence[] }) {
  const price = getEv(evidence, 'MOVING_AVERAGE', 'price')
  const ma5 = getEv(evidence, 'MOVING_AVERAGE', 'ma5')
  const ma20 = getEv(evidence, 'MOVING_AVERAGE', 'ma20')
  const ma60 = getEv(evidence, 'MOVING_AVERAGE', 'ma60')

  return (
    <section>
      <span>추세</span>
      {price != null && (
        <div className="indicator-visual ma-position">
          {[
            { label: 'MA5', value: ma5 },
            { label: 'MA20', value: ma20 },
            { label: 'MA60', value: ma60 },
          ].map(({ label, value }) => {
            if (value == null) return null
            const above = price >= value
            return (
              <div key={label} className={`ma-dot ${above ? 'above' : 'below'}`}>
                <span className="ma-dot-indicator" />
                <span className="ma-dot-label">{label}</span>
                <span className="ma-dot-status">{above ? '지지' : '저항'}</span>
              </div>
            )
          })}
        </div>
      )}
      <p>{text}</p>
    </section>
  )
}

function MomentumCard({ text, evidence }: { text: string; evidence: TechnicalEvidence[] }) {
  const rsiValue = getEv(evidence, 'RSI', 'value')
  const histogram = getEv(evidence, 'MACD', 'histogram')

  return (
    <section>
      <span>모멘텀</span>
      <div className="indicator-visual momentum-visuals">
        {rsiValue != null && (
          <div className="rsi-gauge-container">
            <div className="rsi-gauge-label-row">
              <span>RSI</span>
              <strong className={rsiValue < 30 ? 'oversold' : rsiValue > 70 ? 'overbought' : ''}>{rsiValue.toFixed(1)}</strong>
            </div>
            <div className="rsi-gauge-track">
              <div className="rsi-zone rsi-oversold" />
              <div className="rsi-zone rsi-neutral" />
              <div className="rsi-zone rsi-overbought" />
              <div className="rsi-gauge-marker" style={{ left: `${Math.min(100, Math.max(0, rsiValue))}%` }} />
            </div>
            <div className="rsi-gauge-labels">
              <span>과매도</span><span>중립</span><span>과매수</span>
            </div>
          </div>
        )}
        {histogram != null && (
          <div className={`macd-hist-badge ${histogram > 0 ? 'positive' : 'negative'}`}>
            MACD Hist {histogram > 0 ? '▲' : '▼'} {Math.abs(histogram).toFixed(0)}
          </div>
        )}
      </div>
      <p>{text}</p>
    </section>
  )
}

function VolatilityCard({ text, evidence }: { text: string; evidence: TechnicalEvidence[] }) {
  const atrPercent = getEv(evidence, 'ATR', 'percent')
  const level = atrPercent == null ? null : atrPercent < 5 ? 'low' : atrPercent < 10 ? 'medium' : 'high'
  const levelLabel = level === 'low' ? '낮음' : level === 'medium' ? '보통' : level === 'high' ? '높음' : null

  return (
    <section>
      <span>변동성</span>
      {atrPercent != null && level && (
        <div className="indicator-visual">
          <div className={`atr-badge atr-${level}`}>
            <span className="atr-badge-label">ATR14</span>
            <strong>{atrPercent.toFixed(1)}%</strong>
            <span className="atr-badge-level">{levelLabel}</span>
          </div>
        </div>
      )}
      <p>{text}</p>
    </section>
  )
}

function VolumeCard({ text, evidence }: { text: string; evidence: TechnicalEvidence[] }) {
  const ratio = getEv(evidence, 'VOLUME', 'ratio')

  return (
    <section>
      <span>거래량</span>
      {ratio != null && (
        <div className="indicator-visual">
          <div className="volume-ratio-container">
            <div className="volume-ratio-label-row">
              <span>평균 대비</span>
              <strong className={ratio > 1.5 ? 'high-volume' : ratio < 0.5 ? 'low-volume' : ''}>{ratio.toFixed(2)}배</strong>
            </div>
            <div className="volume-ratio-track">
              <div
                className={`volume-ratio-fill ${ratio > 1.5 ? 'high' : ratio < 0.7 ? 'low' : 'normal'}`}
                style={{ width: `${Math.min(100, (ratio / 2) * 100)}%` }}
              />
              <div className="volume-ratio-baseline" />
            </div>
          </div>
        </div>
      )}
      <p>{text}</p>
    </section>
  )
}

/* ── Signal List (unchanged logic) ── */

function SignalList({
  title,
  items,
  className,
  availableEvidenceIds,
  onEvidenceSelect,
}: {
  title: string
  items: TechnicalSignalExplanation[]
  className: string
  availableEvidenceIds: ReadonlySet<string>
  onEvidenceSelect: (id: string) => void
}) {
  return (
    <section className={className}>
      <h3>{title}</h3>
      {items.length === 0 ? <p>명시된 항목이 없습니다.</p> : (
        <ul>{items.map((item) => (
          <li key={`${item.text}-${item.evidenceIds.join('-')}`}>
            <span>{item.text}</span>
            <EvidenceChips
              ids={item.evidenceIds}
              availableEvidenceIds={availableEvidenceIds}
              onSelect={onEvidenceSelect}
            />
          </li>
        ))}</ul>
      )}
    </section>
  )
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
  const validIds = [...new Set(ids)].filter((id) => availableEvidenceIds.has(id))
  if (validIds.length === 0) return null
  return (
    <small className="evidence-chip-list" aria-label="연결된 근거">
      {validIds.map((id) => (
        <button key={id} type="button" className="evidence-chip" onClick={() => onSelect(id)}>
          {id}
        </button>
      ))}
    </small>
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
        [
          { backgroundColor: 'rgba(41, 212, 201, 0.24)' },
          { backgroundColor: 'rgba(41, 212, 201, 0)' },
        ],
        { duration: 1_100, easing: 'ease-out' },
      )
    }
  })
}

function toDomId(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]/g, '-')
}

function signalLabel(signal: TechnicalExplanation['summarySignal']) {
  if (signal === 'BUY') return '상승 우세'
  if (signal === 'SELL') return '하락 우세'
  return '혼조·중립'
}

function indicatorLabel(indicator: string) {
  return ({
    MOVING_AVERAGE: '이동평균', RSI: 'RSI', MACD: 'MACD', BOLLINGER_BANDS: '볼린저 밴드',
    ATR: 'ATR', VOLUME: '거래량', TECHNICAL_EVENT: '교차 이벤트', DATA_QUALITY: '데이터 품질',
  } as Record<string, string>)[indicator] ?? indicator
}
