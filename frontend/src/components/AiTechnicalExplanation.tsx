import { useEffect, useRef, useState } from 'react'
import { explainTechnical } from '../api/technicalExplanation'
import type { TechnicalExplanation, TechnicalSignalExplanation } from '../types/technicalExplanation'

type Props = {
  market: string
  symbol: string
  onUsageRecorded?: () => void
}

export function AiTechnicalExplanation({ market, symbol, onUsageRecorded }: Props) {
  const [explanation, setExplanation] = useState<TechnicalExplanation | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const requestRef = useRef<AbortController | null>(null)

  useEffect(() => {
    requestRef.current?.abort()
    setExplanation(null)
    setLoading(false)
    setError('')
  }, [market, symbol])

  useEffect(() => () => requestRef.current?.abort(), [])

  async function requestExplanation() {
    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    setLoading(true)
    setError('')
    try {
      const result = await explainTechnical(market, symbol, controller.signal)
      if (controller.signal.aborted) return
      setExplanation(result)
      onUsageRecorded?.()
    } catch (caught: unknown) {
      if (caught instanceof DOMException && caught.name === 'AbortError') return
      setError(caught instanceof Error ? caught.message : 'AI 기술지표 해설을 생성하지 못했습니다.')
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null
        setLoading(false)
      }
    }
  }

  return (
    <article className="card ai-technical-card" id="ai-technical">
      <div className="section-heading compact">
        <div>
          <p className="eyebrow">EVIDENCE-BASED TECHNICAL AI</p>
          <h2>AI 기술 분석 해설</h2>
        </div>
        {explanation && (
          <span className={`cache-badge ${explanation.cacheHit ? 'hit' : 'miss'}`}>
            {explanation.cacheHit ? 'CACHE HIT' : 'CACHE MISS'}
          </span>
        )}
      </div>

      {!explanation ? (
        <div className="technical-ai-empty">
          <div><strong>{market} · {symbol}</strong><span>서버가 계산한 일봉 지표만 Gemini에 전달합니다.</span></div>
          <p>AI는 지표 값을 다시 계산하거나 종목을 추천하지 않고, 추세·모멘텀·변동성·거래량의 근거와 충돌을 설명합니다.</p>
          {error && <p className="request-error" role="alert">{error}</p>}
          <button type="button" onClick={() => void requestExplanation()} disabled={loading}>
            {loading ? '근거 해설 생성 중…' : 'AI 기술 해설 실행'}
          </button>
        </div>
      ) : (
        <div className="technical-ai-result" aria-live="polite">
          <div className="technical-ai-audit">
            <span>{explanation.symbol} · {explanation.interval}</span>
            <span>{explanation.source} · {explanation.freshness}</span>
            <span>{new Date(explanation.latestRecordedAt).toLocaleString('ko-KR')}</span>
            <span>{explanation.modelName}</span>
          </div>
          <div className={`technical-ai-summary signal-${explanation.summarySignal.toLowerCase()}`}>
            <span>서버 종합 신호 · {signalLabel(explanation.summarySignal)}</span>
            <strong>{explanation.summary}</strong>
          </div>

          <div className="technical-explanation-grid">
            <Explanation title="추세" text={explanation.trendExplanation} />
            <Explanation title="모멘텀" text={explanation.momentumExplanation} />
            <Explanation title="변동성" text={explanation.volatilityExplanation} />
            <Explanation title="거래량" text={explanation.volumeExplanation} />
          </div>

          <div className="technical-signal-grid">
            <SignalList title="지지 근거" items={explanation.supportingSignals} className="supporting" />
            <SignalList title="충돌·주의 신호" items={explanation.conflictingSignals} className="conflicting" />
          </div>

          <details className="technical-evidence-drawer">
            <summary>서버 계산 근거 {explanation.evidence.length}개 보기</summary>
            <div>
              {explanation.evidence.map((item) => (
                <p key={item.id}><strong>{item.id} · {indicatorLabel(item.indicator)}</strong><span>{item.displayValue}</span></p>
              ))}
            </div>
          </details>

          {(explanation.riskNotes.length > 0 || explanation.dataLimitations.length > 0) && (
            <div className="technical-limitations">
              {[...explanation.riskNotes, ...explanation.dataLimitations].map((item) => <p key={item}>주의 · {item}</p>)}
            </div>
          )}

          <div className="usage-strip">
            <div><strong>{explanation.inputTokens + explanation.outputTokens}</strong><span>사용 토큰</span></div>
            <div><strong>${explanation.estimatedCost.toFixed(6)}</strong><span>이번 요청 비용</span></div>
            <div><strong>{explanation.responseTimeMs}ms</strong><span>응답 시간</span></div>
          </div>
          <p className="analysis-disclaimer">
            {explanation.disclaimer} · {explanation.calculationVersion} · {explanation.promptVersion} · 입력 {explanation.inputHash.slice(0, 12)}…
          </p>
          <button type="button" className="secondary-button" onClick={() => void requestExplanation()} disabled={loading}>
            {loading ? '확인 중…' : '같은 스냅샷 다시 해설'}
          </button>
        </div>
      )}
    </article>
  )
}

function Explanation({ title, text }: { title: string; text: string }) {
  return <section><span>{title}</span><p>{text}</p></section>
}

function SignalList({ title, items, className }: { title: string; items: TechnicalSignalExplanation[]; className: string }) {
  return (
    <section className={className}>
      <h3>{title}</h3>
      {items.length === 0 ? <p>명시된 항목이 없습니다.</p> : (
        <ul>{items.map((item) => <li key={`${item.text}-${item.evidenceIds.join('-')}`}><span>{item.text}</span><small>{item.evidenceIds.join(', ')}</small></li>)}</ul>
      )}
    </section>
  )
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
