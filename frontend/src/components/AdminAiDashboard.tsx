import { useEffect, useState } from 'react'
import { getAiMetrics, getAiUsageLogs, syncExternalData } from '../api/admin'
import type { AiMetrics, AiUsageLog, AiUsageLogPage, DataSyncResult } from '../types/admin'

type Props = {
  refreshKey: number
}

const demoMetrics: AiMetrics = {
  from: '2026-06-14',
  to: '2026-07-13',
  requestCount: 1284,
  successCount: 1280,
  failedCount: 4,
  modelCallCount: 339,
  cacheHitCount: 945,
  cacheMissCount: 339,
  inputTokens: 650000,
  outputTokens: 214200,
  totalTokens: 864200,
  estimatedCost: 1.92,
  cacheHitRate: 73.6,
  savedEstimatedCost: 4.86,
  averageResponseTimeMs: 214.8,
  costCurrency: 'USD',
  featureUsage: [{ feature: 'NEWS_SUMMARY', requestCount: 1284, successCount: 1280, failedCount: 4, modelCallCount: 335, cacheHitCount: 945, totalTokens: 864200, estimatedCost: 1.92, savedEstimatedCost: 4.86 }],
}

const demoLogs: AiUsageLog[] = [
  {
    id: 2,
    requestId: 'demo-cache-hit',
    featureType: 'NEWS_SUMMARY',
    targetType: 'NEWS',
    targetId: 1,
    modelName: 'gemini-3.1-flash-lite',
    inputTokens: 0,
    outputTokens: 0,
    totalTokens: 0,
    estimatedCost: 0,
    savedEstimatedCost: 0.000037,
    cacheHit: true,
    responseTimeMs: 14,
    promptVersion: 'news-summary-v1',
    status: 'SUCCESS',
    createdAt: '2026-07-13T04:00:05Z',
  },
  {
    id: 1,
    requestId: 'demo-model-call',
    featureType: 'NEWS_SUMMARY',
    targetType: 'NEWS',
    targetId: 1,
    modelName: 'gemini-3.1-flash-lite',
    inputTokens: 105,
    outputTokens: 67,
    totalTokens: 172,
    estimatedCost: 0.000037,
    savedEstimatedCost: 0,
    cacheHit: false,
    responseTimeMs: 842,
    promptVersion: 'news-summary-v1',
    status: 'SUCCESS',
    createdAt: '2026-07-13T04:00:00Z',
  },
]

function formatNumber(value: number) {
  return new Intl.NumberFormat('ko-KR', { notation: value >= 100000 ? 'compact' : 'standard' }).format(value)
}

function formatCost(value: number) {
  if (value === 0) return '$0'
  return value < 0.01 ? `$${value.toFixed(6)}` : `$${value.toFixed(2)}`
}

function featureLabel(feature: string) {
  if (feature === 'NEWS_SUMMARY') return '뉴스 요약'
  if (feature === 'TECHNICAL_EXPLANATION') return '기술지표 해설'
  if (feature === 'DAILY_CHANGE_BRIEFING') return '일일 변화 브리핑'
  return feature
}

export function AdminAiDashboard({ refreshKey }: Props) {
  const [metrics, setMetrics] = useState<AiMetrics | null>(null)
  const [logs, setLogs] = useState<AiUsageLog[]>([])
  const [loading, setLoading] = useState(true)
  const [demoMode, setDemoMode] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [syncResult, setSyncResult] = useState<DataSyncResult | null>(null)
  const [syncError, setSyncError] = useState('')

  async function runDataSync() {
    setSyncing(true)
    setSyncError('')
    try {
      setSyncResult(await syncExternalData())
    } catch (error: unknown) {
      setSyncError(error instanceof Error ? error.message : '외부 데이터 동기화에 실패했습니다.')
    } finally {
      setSyncing(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    Promise.all([getAiMetrics(controller.signal), getAiUsageLogs(controller.signal)])
      .then(([metricsResponse, logsResponse]: [AiMetrics, AiUsageLogPage]) => {
        setMetrics(metricsResponse)
        setLogs(logsResponse.items)
        setDemoMode(false)
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setMetrics(demoMetrics)
        setLogs(demoLogs)
        setDemoMode(true)
      })
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [refreshKey])

  if (loading && metrics == null) {
    return <section className="card admin-dashboard admin-loading" id="admin">AI 운영 지표를 집계하는 중입니다.</section>
  }

  if (metrics == null) return null

  const missRate = Math.max(0, 100 - metrics.cacheHitRate)
  const maxFeatureCount = Math.max(1, ...metrics.featureUsage.map((item) => item.requestCount))

  return (
    <section className="admin-dashboard" id="admin">
      <div className="admin-title-row">
        <div>
          <p className="eyebrow">AI OPERATIONS</p>
          <h2>AI 비용 최적화 대시보드</h2>
          <p>{metrics.from} ~ {metrics.to} · 로그 기반 예상 비용</p>
        </div>
        <div className="admin-title-meta">
          {demoMode && <span className="demo-badge">DEMO DATA</span>}
          <span>평균 응답 {metrics.averageResponseTimeMs.toFixed(1)}ms</span>
          <button type="button" className="data-sync-button" onClick={() => void runDataSync()} disabled={syncing}>
            {syncing ? '데이터 동기화 중…' : '외부 데이터 동기화'}
          </button>
        </div>
      </div>

      {(syncResult || syncError) && (
        <p className={`data-sync-status${syncError ? ' error' : ''}`} role="status">
          {syncError || `${syncResult?.mode} · 시세 ${syncResult?.pricesImported}건 · 뉴스 ${syncResult?.newsImported}건 반영`}
        </p>
      )}

      <div className="admin-metric-grid">
        <article className="card admin-metric-card"><span>전체 요청</span><strong>{formatNumber(metrics.requestCount)}</strong><small>성공 {formatNumber(metrics.successCount)} · 실패 {formatNumber(metrics.failedCount)}</small></article>
        <article className="card admin-metric-card"><span>실제 모델 호출</span><strong>{formatNumber(metrics.modelCallCount)}</strong><small>캐시 MISS 요청</small></article>
        <article className="card admin-metric-card"><span>총 토큰</span><strong>{formatNumber(metrics.totalTokens)}</strong><small>입력 {formatNumber(metrics.inputTokens)} · 출력 {formatNumber(metrics.outputTokens)}</small></article>
        <article className="card admin-metric-card"><span>예상 비용</span><strong>{formatCost(metrics.estimatedCost)}</strong><small>{metrics.costCurrency} 기준</small></article>
        <article className="card admin-metric-card highlight"><span>캐시 적중률</span><strong>{metrics.cacheHitRate.toFixed(1)}%</strong><small>{formatNumber(metrics.cacheHitCount)}건 재사용</small></article>
        <article className="card admin-metric-card saving"><span>예상 절감액</span><strong>{formatCost(metrics.savedEstimatedCost)}</strong><small>AI 재호출 방지 효과</small></article>
      </div>

      <div className="admin-detail-grid">
        <article className="card cache-performance-card">
          <div className="section-heading compact">
            <div><p className="eyebrow">CACHE PERFORMANCE</p><h3>Redis 캐시 효과</h3></div>
            <strong>{metrics.cacheHitRate.toFixed(1)}%</strong>
          </div>
          <div className="cache-progress" aria-label={`캐시 적중률 ${metrics.cacheHitRate.toFixed(1)}%`}>
            <span className="cache-hit-bar" style={{ width: `${metrics.cacheHitRate}%` }} />
          </div>
          <div className="cache-legend">
            <span><i className="hit-dot" />HIT {formatNumber(metrics.cacheHitCount)}</span>
            <span><i className="miss-dot" />MISS {formatNumber(metrics.cacheMissCount)} ({missRate.toFixed(1)}%)</span>
          </div>
          <p>동일한 뉴스와 프롬프트 버전의 결과를 Redis에서 재사용해 토큰 비용과 응답 시간을 줄입니다.</p>
        </article>

        <article className="card feature-usage-card">
          <div className="section-heading compact"><div><p className="eyebrow">FEATURE USAGE</p><h3>기능별 요청</h3></div></div>
          {metrics.featureUsage.length === 0 ? (
            <p className="empty-admin-data">아직 기록된 AI 요청이 없습니다.</p>
          ) : metrics.featureUsage.map((item) => (
            <div className="feature-usage-row" key={item.feature}>
              <div><span>{featureLabel(item.feature)}</span><strong>{formatNumber(item.requestCount)}</strong></div>
              <div className="feature-bar"><span style={{ width: `${item.requestCount / maxFeatureCount * 100}%` }} /></div>
              <small>모델 {formatNumber(item.modelCallCount)}회 · 실패 {formatNumber(item.failedCount)} · 토큰 {formatNumber(item.totalTokens)} · {formatCost(item.estimatedCost)}</small>
            </div>
          ))}
        </article>
      </div>

      <article className="card usage-log-card">
        <div className="section-heading compact">
          <div><p className="eyebrow">USAGE LOGS</p><h3>최근 AI 요청 로그</h3></div>
          <span className="muted">최근 {logs.length}건</span>
        </div>
        <div className="usage-table-wrap">
          <table>
            <thead><tr><th>시각</th><th>기능 / 모델</th><th>캐시</th><th>토큰</th><th>비용</th><th>절감액</th><th>응답</th></tr></thead>
            <tbody>
              {logs.length === 0 && <tr><td colSpan={7} className="empty-table">AI 뉴스 요약을 실행하면 사용 로그가 표시됩니다.</td></tr>}
              {logs.map((log) => (
                <tr key={log.id}>
                  <td>{new Date(log.createdAt).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</td>
                  <td><strong>{featureLabel(log.featureType)}</strong><small>{log.modelName}</small></td>
                  <td><span className={`table-cache ${log.status === 'FAILED' ? 'failed' : log.cacheHit ? 'hit' : 'miss'}`}>{log.status === 'FAILED' ? 'FAILED' : log.cacheHit ? 'HIT' : 'MISS'}</span></td>
                  <td>{formatNumber(log.totalTokens)}</td>
                  <td>{formatCost(log.estimatedCost)}</td>
                  <td className="saved-cost">{formatCost(log.savedEstimatedCost)}</td>
                  <td>{log.responseTimeMs}ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </article>
    </section>
  )
}
