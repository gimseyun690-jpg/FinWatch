import { useEffect, useRef, useState } from 'react'
import { useOutletContext } from 'react-router'
import type { AppRouteContext } from '../app/context'
import { evaluatePortfolio } from '../api/portfolioEvaluation'
import type {
  PortfolioBalanceStatus,
  PortfolioEvaluation,
  PortfolioEvaluationEvidence,
  PortfolioEvaluationStatement,
} from '../types/portfolioEvaluation'

type Props = {
  portfolioRevision?: number
  holdingCount?: number | null
  onUsageRecorded?: () => void
}

const sectionCards: Array<{
  key: 'diversification' | 'concentration' | 'currencyExposure' | 'performanceContext'
  label: string
  eyebrow: string
}> = [
  { key: 'diversification', label: '분산 구조', eyebrow: 'DIVERSIFICATION' },
  { key: 'concentration', label: '집중도', eyebrow: 'CONCENTRATION' },
  { key: 'currencyExposure', label: '통화 노출', eyebrow: 'CURRENCY' },
  { key: 'performanceContext', label: '손익 맥락', eyebrow: 'PERFORMANCE' },
]

export function AiPortfolioEvaluation({ portfolioRevision = 0, holdingCount = null, onUsageRecorded }: Props) {
  const context = useOutletContext<AppRouteContext | null>()
  const showAdminDetails = context?.showAdminDetails ?? true
  const [evaluation, setEvaluation] = useState<PortfolioEvaluation | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const requestRef = useRef<AbortController | null>(null)
  const evidenceDetailsRef = useRef<HTMLDetailsElement | null>(null)
  const evidenceRefs = useRef(new Map<string, HTMLElement>())
  const portfolioRevisionRef = useRef(portfolioRevision)

  useEffect(() => () => requestRef.current?.abort(), [])
  useEffect(() => {
    if (portfolioRevisionRef.current === portfolioRevision) return
    portfolioRevisionRef.current = portfolioRevision
    requestRef.current?.abort()
    requestRef.current = null
    setEvaluation(null)
    setLoading(false)
    setError('')
    setNotice('보유 구성이 변경되어 이전 평가를 지웠습니다. 현재 구성으로 다시 평가해 주세요.')
  }, [portfolioRevision])

  async function requestEvaluation() {
    if (holdingCount == null || holdingCount === 0) return
    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    setLoading(true)
    setError('')
    setNotice('')
    try {
      const result = await evaluatePortfolio(controller.signal)
      if (controller.signal.aborted) return
      setEvaluation(result)
      onUsageRecorded?.()
    } catch (caught: unknown) {
      if (controller.signal.aborted) return
      setError(caught instanceof Error ? caught.message : '포트폴리오 AI 평가를 생성하지 못했습니다.')
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null
        setLoading(false)
      }
    }
  }

  function revealEvidence(id: string) {
    const target = evidenceRefs.current.get(id)
    const details = evidenceDetailsRef.current
    if (!target || !details) return
    details.open = true
    window.requestAnimationFrame(() => {
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      target.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'center' })
      target.focus({ preventScroll: true })
      if (!reduceMotion) {
        target.animate(
          [
            { backgroundColor: 'rgba(41, 212, 201, 0.22)' },
            { backgroundColor: 'rgba(41, 212, 201, 0)' },
          ],
          { duration: 900, easing: 'ease-out' },
        )
      }
    })
  }

  return (
    <article className="card portfolio-ai-card" id="portfolio-ai" aria-busy={loading}>
      <div className="section-heading">
        <div>
          {showAdminDetails && <p className="eyebrow">PORTFOLIO COMPOSITION AI</p>}
          <h2>포트폴리오 AI 평가</h2>
        </div>
        {showAdminDetails && evaluation && (
          <span className={`cache-badge ${evaluation.audit.cacheHit ? 'hit' : 'miss'}`}>
            {evaluation.audit.cacheHit ? 'CACHE HIT' : 'CACHE MISS'}
          </span>
        )}
      </div>

      {!evaluation ? (
        <section className="portfolio-ai-empty">
          <div>
            <strong>{holdingCount === 0 ? '보유 종목을 등록하면 AI 평가를 시작할 수 있습니다.' : '현재 보유 구성을 근거로 점검합니다.'}</strong>
            <p>{holdingCount === 0
              ? '위의 포트폴리오에서 종목과 보유 정보를 먼저 등록해 주세요.'
              : '종목 집중도, 통화 노출, 분산 구조와 손익 맥락을 설명하며 매수·매도나 목표가를 추천하지 않습니다.'}</p>
            {notice && <p className="panel-status-message" role="status">{notice}</p>}
          </div>
          {error && <p className="request-error" role="alert">{error}</p>}
          <button type="button" onClick={() => void requestEvaluation()} disabled={loading || holdingCount == null || holdingCount === 0}>
            {loading
              ? '포트폴리오 분석 중…'
              : holdingCount == null
                ? '포트폴리오 확인 후 평가 가능'
                : holdingCount === 0
                  ? '보유 종목 등록 후 평가 가능'
                  : 'AI 포트폴리오 평가'}
          </button>
        </section>
      ) : (
        <div className="portfolio-ai-result" aria-live="polite">
          <header className={`portfolio-ai-hero balance-${evaluation.balanceStatus.toLowerCase()}`}>
            <div>
              <span>{balanceLabel(evaluation.balanceStatus)}</span>
              <strong>{evaluation.headline}</strong>
            </div>
            <p>{evaluation.summary}</p>
          </header>
          <p className="portfolio-ai-snapshot">평가 기준 {new Date(evaluation.snapshotAt).toLocaleString('ko-KR')}</p>

          <div className="portfolio-ai-dimensions">
            {sectionCards.map((card) => {
              const statement = evaluation[card.key]
              return (
                <section key={card.key}>
                  <span>{card.eyebrow}</span>
                  <h3>{card.label}</h3>
                  <p>{statement.text}</p>
                  <EvidenceChips
                    ids={statement.evidenceIds}
                    evidence={evaluation.evidence}
                    onSelect={revealEvidence}
                  />
                </section>
              )
            })}
          </div>

          <div className="portfolio-ai-lists">
            <StatementList
              title="구조상 강점"
              items={evaluation.strengths}
              evidence={evaluation.evidence}
              onEvidenceSelect={revealEvidence}
            />
            <StatementList
              title="확인할 위험"
              items={evaluation.riskFactors}
              evidence={evaluation.evidence}
              onEvidenceSelect={revealEvidence}
            />
            <StatementList
              title="정기 점검 항목"
              items={evaluation.reviewPoints}
              evidence={evaluation.evidence}
              onEvidenceSelect={revealEvidence}
            />
          </div>

          <details ref={evidenceDetailsRef} className="portfolio-ai-evidence">
            <summary>평가 근거 {evaluation.evidence.length}개 확인</summary>
            <div>
              {evaluation.evidence.map((item) => (
                <p
                  key={item.id}
                  ref={(node) => registerEvidence(evidenceRefs.current, item.id, node)}
                  tabIndex={-1}
                  data-evidence-id={item.id}
                >
                  <span><strong>{item.id}</strong>{evidenceCategoryLabel(item.category)}</span>
                  <em>{item.displayValue}</em>
                </p>
              ))}
            </div>
          </details>

          {evaluation.dataLimitations.length > 0 && (
            <section className="portfolio-ai-limitations">
              <strong>데이터 한계</strong>
              {evaluation.dataLimitations.map((item) => <p key={item}>{item}</p>)}
            </section>
          )}

          <p className="analysis-disclaimer">{evaluation.disclaimer}</p>
          {showAdminDetails && (
            <>
              <div className="portfolio-ai-audit">
                <span>기준 {new Date(evaluation.snapshotAt).toLocaleString('ko-KR')}</span>
                <span>생성 {new Date(evaluation.audit.generatedAt).toLocaleString('ko-KR')}</span>
                <span>{evaluation.audit.modelName}</span>
                <span>{evaluation.audit.promptVersion}</span>
              </div>
              <div className="usage-strip">
                <div><strong>{evaluation.audit.inputTokens + evaluation.audit.outputTokens}</strong><span>사용 토큰</span></div>
                <div><strong>{formatCost(evaluation.audit.estimatedCost, evaluation.audit.costCurrency)}</strong><span>이번 요청 비용</span></div>
                <div><strong>{evaluation.audit.responseTimeMs}ms</strong><span>응답 시간</span></div>
              </div>
            </>
          )}
          {error && <p className="request-error" role="alert">{error}</p>}
          <button type="button" className="secondary-button" onClick={() => void requestEvaluation()} disabled={loading}>
            {loading ? '최신 구성 확인 중…' : '현재 구성으로 다시 평가'}
          </button>
        </div>
      )}
    </article>
  )
}

function StatementList({
  title,
  items,
  evidence,
  onEvidenceSelect,
}: {
  title: string
  items: PortfolioEvaluationStatement[]
  evidence: PortfolioEvaluationEvidence[]
  onEvidenceSelect: (id: string) => void
}) {
  return (
    <section>
      <h3>{title}</h3>
      {items.length === 0 ? <p>별도로 표시할 항목이 없습니다.</p> : (
        <ul>
          {items.map((item) => (
            <li key={`${item.text}-${item.evidenceIds.join('-')}`}>
              <span>{item.text}</span>
              <EvidenceChips ids={item.evidenceIds} evidence={evidence} onSelect={onEvidenceSelect} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function EvidenceChips({
  ids,
  evidence,
  onSelect,
}: {
  ids: string[]
  evidence: PortfolioEvaluationEvidence[]
  onSelect: (id: string) => void
}) {
  const availableIds = new Set(evidence.map((item) => item.id))
  const validIds = [...new Set(ids)].filter((id) => availableIds.has(id))
  if (validIds.length === 0) return null
  return (
    <span className="portfolio-evidence-chips" aria-label="연결된 평가 근거">
      {validIds.map((id) => (
        <button key={id} type="button" onClick={() => onSelect(id)}>{id}</button>
      ))}
    </span>
  )
}

function registerEvidence(refs: Map<string, HTMLElement>, id: string, node: HTMLElement | null) {
  if (node) refs.set(id, node)
  else refs.delete(id)
}

function balanceLabel(status: PortfolioBalanceStatus) {
  if (status === 'DIVERSIFIED') return '분산 구조'
  if (status === 'MODERATE_CONCENTRATION') return '일부 집중'
  if (status === 'HIGH_CONCENTRATION') return '높은 집중'
  return '평가 자료 불완전'
}

function evidenceCategoryLabel(category: string) {
  return ({
    PORTFOLIO_TOTAL: '포트폴리오 합계',
    CONCENTRATION: '집중도',
    CURRENCY_EXPOSURE: '통화 노출',
    HOLDING: '보유 종목',
    DATA_QUALITY: '데이터 품질',
  } as Record<string, string>)[category] ?? category
}

function formatCost(value: number, currency: string) {
  const prefix = currency === 'USD' ? '$' : `${currency} `
  return `${prefix}${value === 0 ? '0' : value < 0.01 ? value.toFixed(6) : value.toFixed(2)}`
}
