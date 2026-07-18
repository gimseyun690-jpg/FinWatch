import { useEffect, useState } from 'react'
import { getAiMetrics, getAiUsageLogs, syncExternalData } from '../api/admin'
import type { AiMetrics, AiUsageLog, DataSyncResult } from '../types/admin'
import { DataStatusBadge, type DataStatus } from './DataStatusBadge'

type Props = {
  refreshKey: number
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('ko-KR', { notation: value >= 100000 ? 'compact' : 'standard' }).format(value)
}

function formatCost(value: number, currency = 'USD') {
  const prefix = currency === 'USD' ? '$' : `${currency} `
  if (value === 0) return `${prefix}0`
  return `${prefix}${value < 0.01 ? value.toFixed(6) : value.toFixed(2)}`
}

function featureLabel(feature: string) {
  if (feature === 'NEWS_SUMMARY') return '뉴스 요약'
  if (feature === 'TECHNICAL_EXPLANATION') return '기술지표 해설'
  if (feature === 'DAILY_CHANGE_BRIEFING') return '일일 변화 브리핑'
  return feature
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

function loadErrorMessage(label: string, error: unknown) {
  const detail = error instanceof Error && error.message ? ` (${error.message})` : ''
  return `${label}을 불러오지 못했습니다${detail}`
}

function formatUpdatedAt(value: string | null) {
  if (value == null) return null
  return new Date(value).toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function AdminAiDashboard({ refreshKey }: Props) {
  const [metrics, setMetrics] = useState<AiMetrics | null>(null)
  const [logs, setLogs] = useState<AiUsageLog[]>([])
  const [metricsLoading, setMetricsLoading] = useState(true)
  const [logsLoading, setLogsLoading] = useState(true)
  const [logsLoaded, setLogsLoaded] = useState(false)
  const [metricsError, setMetricsError] = useState('')
  const [logsError, setLogsError] = useState('')
  const [metricsUpdatedAt, setMetricsUpdatedAt] = useState<string | null>(null)
  const [logsUpdatedAt, setLogsUpdatedAt] = useState<string | null>(null)
  const [retryKey, setRetryKey] = useState(0)
  const [isOnline, setIsOnline] = useState(() => typeof navigator === 'undefined' || navigator.onLine)
  const [syncing, setSyncing] = useState(false)
  const [syncResult, setSyncResult] = useState<DataSyncResult | null>(null)
  const [syncError, setSyncError] = useState('')

  async function runDataSync() {
    if (!isOnline) {
      setSyncError('오프라인 상태에서는 외부 데이터를 동기화할 수 없습니다.')
      return
    }

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
    const handleOnline = () => setIsOnline(true)
    const handleOffline = () => setIsOnline(false)
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [])

  useEffect(() => {
    if (!isOnline) {
      setMetricsLoading(false)
      setLogsLoading(false)
      setMetricsError('오프라인 상태여서 운영 지표를 갱신하지 못했습니다.')
      setLogsError('오프라인 상태여서 사용 로그를 갱신하지 못했습니다.')
      return
    }

    const controller = new AbortController()
    let active = true
    setMetricsLoading(true)
    setLogsLoading(true)
    setMetricsError('')
    setLogsError('')

    void getAiMetrics(controller.signal)
      .then((response) => {
        if (!active) return
        setMetrics(response)
        setMetricsUpdatedAt(new Date().toISOString())
      })
      .catch((error: unknown) => {
        if (!active || isAbortError(error)) return
        setMetricsError(loadErrorMessage('AI 운영 지표', error))
      })
      .finally(() => {
        if (active) setMetricsLoading(false)
      })

    void getAiUsageLogs(controller.signal)
      .then((response) => {
        if (!active) return
        setLogs(response.items)
        setLogsLoaded(true)
        setLogsUpdatedAt(new Date().toISOString())
      })
      .catch((error: unknown) => {
        if (!active || isAbortError(error)) return
        setLogsError(loadErrorMessage('AI 사용 로그', error))
      })
      .finally(() => {
        if (active) setLogsLoading(false)
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [isOnline, refreshKey, retryKey])

  const initialLoading = metrics == null && !logsLoaded && (metricsLoading || logsLoading)
  const hasPreviousData = metrics != null || logsLoaded
  const hasLoadError = metricsError !== '' || logsError !== ''
  const bothFailed = metricsError !== '' && logsError !== ''
  const refreshing = metricsLoading || logsLoading
  const dataState = !isOnline
    ? 'OFFLINE'
    : initialLoading
      ? 'LOADING'
      : bothFailed
        ? hasPreviousData ? 'STALE' : 'ERROR'
        : hasLoadError
          ? 'PARTIAL'
          : refreshing
            ? 'REFRESHING'
            : 'READY'
  const badgeStatus: DataStatus = dataState === 'READY'
    ? 'READY'
    : dataState === 'PARTIAL'
      ? 'PARTIAL'
      : dataState === 'ERROR'
        ? 'UNAVAILABLE'
        : dataState === 'STALE' || dataState === 'OFFLINE'
          ? 'STALE'
          : 'REFERENCE'
  const badgeDetail = dataState === 'READY' || dataState === 'PARTIAL' || dataState === 'STALE'
    ? undefined
    : dataState
  const lastUpdatedAt = [metricsUpdatedAt, logsUpdatedAt]
    .filter((value): value is string => value != null)
    .sort()
    .at(-1) ?? null
  const displayedUpdatedAt = formatUpdatedAt(lastUpdatedAt)
  const cacheEvaluatedCount = metrics == null ? 0 : metrics.cacheHitCount + metrics.cacheMissCount
  const missRate = metrics == null ? 0 : Math.max(0, 100 - metrics.cacheHitRate)
  const cacheRateWidth = metrics == null ? 0 : Math.min(100, Math.max(0, metrics.cacheHitRate))
  const costCurrency = metrics?.costCurrency ?? 'USD'

  return (
    <section className="admin-dashboard" id="admin" aria-busy={initialLoading || refreshing}>
      <div className="admin-title-row">
        <div>
          <p className="eyebrow">AI OPERATIONS</p>
          <h2>AI 비용 최적화 대시보드</h2>
          <p>
            {metrics
              ? `${metrics.from} ~ ${metrics.to} · 서버 사용 로그 집계`
              : '서버 사용 로그와 캐시 운영 상태를 집계합니다.'}
          </p>
          <p className="muted" role="note">비용과 절감액은 설정 단가 기반 추정치이며 공급자의 실제 청구액이 아닙니다.</p>
        </div>
        <div className="admin-title-meta">
          <DataStatusBadge status={badgeStatus} detail={badgeDetail} />
          {displayedUpdatedAt && <span>마지막 성공 {displayedUpdatedAt}</span>}
          {metrics && <span>평균 응답 {metrics.averageResponseTimeMs.toFixed(1)}ms</span>}
          <button
            type="button"
            className="data-sync-button"
            onClick={() => void runDataSync()}
            disabled={syncing || !isOnline}
            title={!isOnline ? '네트워크 연결 후 실행할 수 있습니다.' : syncing ? '외부 데이터를 동기화하는 중입니다.' : undefined}
          >
            {syncing ? '데이터 동기화 중…' : '외부 데이터 동기화'}
          </button>
        </div>
      </div>

      {initialLoading && (
        <article className="card admin-loading" role="status" aria-live="polite">
          AI 운영 지표와 사용 로그를 불러오는 중입니다.
        </article>
      )}

      {!initialLoading && refreshing && !hasLoadError && (
        <p className="data-sync-status" role="status" aria-live="polite">
          기존 값을 유지한 채 최신 운영 데이터를 갱신하고 있습니다.
        </p>
      )}

      {hasLoadError && (
        <div className="data-sync-status error" role={hasPreviousData ? 'status' : 'alert'} aria-live="polite">
          <strong>
            {!isOnline
              ? hasPreviousData ? '오프라인 · 마지막 성공 데이터를 표시합니다.' : '오프라인 · 표시할 저장 데이터가 없습니다.'
              : dataState === 'STALE'
                ? '갱신 실패 · 마지막 성공 데이터를 표시합니다.'
                : dataState === 'PARTIAL'
                  ? '일부 데이터만 갱신되었습니다.'
                  : '운영 데이터를 불러오지 못했습니다.'}
          </strong>
          <span>{[metricsError, logsError].filter(Boolean).join(' ')}</span>
          <button
            type="button"
            onClick={() => setRetryKey((value) => value + 1)}
            disabled={!isOnline || refreshing}
            title={!isOnline ? '네트워크 연결 후 다시 시도할 수 있습니다.' : refreshing ? '운영 데이터를 갱신하는 중입니다.' : undefined}
          >
            다시 시도
          </button>
        </div>
      )}

      {(syncResult || syncError) && (
        <p className={`data-sync-status${syncError ? ' error' : ''}`} role={syncError ? 'alert' : 'status'} aria-live="polite">
          {syncError || `${syncResult?.mode === 'DEMO' ? 'DEMO 데이터' : 'LIVE 데이터'} · 시세 ${syncResult?.pricesImported}건 · 뉴스 ${syncResult?.newsImported}건 반영`}
        </p>
      )}

      {metrics ? (
        <>
          <div className="admin-metric-grid">
            <article className="card admin-metric-card"><span>전체 요청</span><strong>{formatNumber(metrics.requestCount)}</strong><small>성공 {formatNumber(metrics.successCount)} · 실패 {formatNumber(metrics.failedCount)}</small></article>
            <article className="card admin-metric-card"><span>실제 모델 호출</span><strong>{formatNumber(metrics.modelCallCount)}</strong><small>캐시 MISS 요청</small></article>
            <article className="card admin-metric-card"><span>총 토큰</span><strong>{formatNumber(metrics.totalTokens)}</strong><small>입력 {formatNumber(metrics.inputTokens)} · 출력 {formatNumber(metrics.outputTokens)}</small></article>
            <article className="card admin-metric-card"><span>예상 비용</span><strong>{formatCost(metrics.estimatedCost, costCurrency)}</strong><small>{costCurrency} 설정 단가 기준 추정치</small></article>
            <article className="card admin-metric-card highlight"><span>캐시 적중률</span><strong>{metrics.cacheHitRate.toFixed(1)}%</strong><small>HIT {formatNumber(metrics.cacheHitCount)} / 전체 {formatNumber(cacheEvaluatedCount)}건</small></article>
            <article className="card admin-metric-card saving"><span>예상 절감액</span><strong>{formatCost(metrics.savedEstimatedCost, costCurrency)}</strong><small>설정 단가로 계산한 AI 재호출 방지 효과</small></article>
          </div>

          <div className="admin-detail-grid">
            <article className="card cache-performance-card">
              <div className="section-heading compact">
                <div><p className="eyebrow">CACHE PERFORMANCE</p><h3>Redis 캐시 효과</h3></div>
                <strong>{metrics.cacheHitRate.toFixed(1)}%</strong>
              </div>
              <div className="cache-progress" aria-label={`캐시 적중률 ${metrics.cacheHitRate.toFixed(1)}%, ${cacheEvaluatedCount}건 중 ${metrics.cacheHitCount}건 적중`}>
                <span className="cache-hit-bar" style={{ width: `${cacheRateWidth}%` }} />
              </div>
              <div className="cache-legend">
                <span><i className="hit-dot" />HIT {formatNumber(metrics.cacheHitCount)} / 전체 {formatNumber(cacheEvaluatedCount)}</span>
                <span><i className="miss-dot" />MISS {formatNumber(metrics.cacheMissCount)} / 전체 {formatNumber(cacheEvaluatedCount)} ({missRate.toFixed(1)}%)</span>
              </div>
              <p>집계 기간 {metrics.from} ~ {metrics.to} · 분모는 HIT + MISS = {formatNumber(cacheEvaluatedCount)}건입니다.</p>
              <p>동일한 뉴스와 프롬프트 버전의 결과를 Redis에서 재사용해 토큰 비용과 응답 시간을 줄입니다.</p>
            </article>

            <article className="card feature-usage-card">
              <div className="section-heading compact"><div><p className="eyebrow">FEATURE USAGE</p><h3>기능별 요청</h3></div></div>
              {metrics.featureUsage.length === 0 ? (
                <p className="empty-admin-data">{metrics.from} ~ {metrics.to} 기간에 기록된 AI 요청이 없습니다.</p>
              ) : metrics.featureUsage.map((item) => (
                <div className="feature-usage-row" key={item.feature}>
                  <div><span>{featureLabel(item.feature)}</span><strong>{formatNumber(item.requestCount)}</strong></div>
                  <div className="feature-bar" role="img" aria-label={`${featureLabel(item.feature)} ${item.requestCount}건, 전체 요청 ${metrics.requestCount}건`}><span style={{ width: `${metrics.requestCount > 0 ? Math.min(100, item.requestCount / metrics.requestCount * 100) : 0}%` }} /></div>
                  <small>전체 {formatNumber(metrics.requestCount)}건 중 {formatNumber(item.requestCount)}건 · 모델 {formatNumber(item.modelCallCount)}회 · 실패 {formatNumber(item.failedCount)} · 토큰 {formatNumber(item.totalTokens)} · 추정 {formatCost(item.estimatedCost, costCurrency)}</small>
                </div>
              ))}
            </article>
          </div>
        </>
      ) : !initialLoading && (
        <article className="card empty-admin-data" role="status">
          표시할 AI 운영 지표가 없습니다. 네트워크와 서버 상태를 확인한 뒤 다시 시도해 주세요.
        </article>
      )}

      <article className="card usage-log-card" aria-busy={logsLoading}>
        <div className="section-heading compact">
          <div><p className="eyebrow">USAGE LOGS</p><h3>최근 AI 요청 로그</h3></div>
          <span className="muted">
            {logsLoading && !logsLoaded ? '불러오는 중' : `${logsError && logsLoaded ? '마지막 성공 · ' : ''}최근 ${logs.length}건`}
          </span>
        </div>
        <div className="usage-table-wrap">
          <table>
            <caption className="sr-only">AI 기능별 모델 호출, 캐시, 토큰, 추정 비용과 응답 시간</caption>
            <thead><tr><th>시각</th><th>기능 / 모델</th><th>캐시</th><th>토큰</th><th>추정 비용</th><th>추정 절감액</th><th>응답</th></tr></thead>
            <tbody>
              {logsLoading && !logsLoaded && <tr><td colSpan={7} className="empty-table" role="status">AI 사용 로그를 불러오는 중입니다.</td></tr>}
              {!logsLoading && logsError && !logsLoaded && <tr><td colSpan={7} className="empty-table">사용 로그를 불러오지 못했습니다. 위의 다시 시도 버튼을 이용해 주세요.</td></tr>}
              {!logsLoading && !logsError && logsLoaded && logs.length === 0 && <tr><td colSpan={7} className="empty-table">선택한 집계 기간에 AI 사용 로그가 없습니다.</td></tr>}
              {logs.map((log) => (
                <tr key={log.id}>
                  <td><time dateTime={log.createdAt}>{new Date(log.createdAt).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</time></td>
                  <td><strong>{featureLabel(log.featureType)}</strong><small>{log.modelName}</small></td>
                  <td><span className={`table-cache ${log.status === 'FAILED' ? 'failed' : log.cacheHit ? 'hit' : 'miss'}`}>{log.status === 'FAILED' ? 'FAILED' : log.cacheHit ? 'HIT' : 'MISS'}</span></td>
                  <td>{formatNumber(log.totalTokens)}</td>
                  <td>{formatCost(log.estimatedCost, costCurrency)}</td>
                  <td className="saved-cost">{formatCost(log.savedEstimatedCost, costCurrency)}</td>
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
