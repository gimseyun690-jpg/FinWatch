import { useCallback, useEffect, useState } from 'react'
import { getDataLoadJob, getDisclosures, startDisclosureLoad } from '../api/disclosures'
import type { Disclosure } from '../types/disclosure'
import type { StockRef } from '../types/stock'

type Props = {
  stock: StockRef
}

const POLL_INTERVAL_MS = 700
const MAX_POLL_ATTEMPTS = 30

export function DisclosurePanel({ stock }: Props) {
  const [items, setItems] = useState<Disclosure[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const load = useCallback(async (signal?: AbortSignal) => {
    const disclosures = await getDisclosures(stock.market, stock.symbol, signal)
    if (!signal?.aborted) setItems(disclosures)
  }, [stock.market, stock.symbol])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setItems([])
    setMessage('')
    setError('')
    load(controller.signal)
      .catch((reason: unknown) => {
        if (reason instanceof DOMException && reason.name === 'AbortError') return
        setError(errorMessage(reason, '공시 목록을 불러오지 못했습니다.'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [load])

  async function refresh() {
    const controller = new AbortController()
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
      await load(controller.signal)
      setMessage(`${providerLabel(disclosureResult.provider)}에서 공시 ${disclosureResult.imported.toLocaleString()}건을 반영했습니다.`)
    } catch (reason: unknown) {
      if (reason instanceof DOMException && reason.name === 'AbortError') return
      setError(errorMessage(reason, '공시 수집에 실패했습니다.'))
    } finally {
      setRefreshing(false)
    }
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
          {items.map((item) => (
            <a key={item.id} href={item.url} target="_blank" rel="noreferrer" className="disclosure-row">
              <div>
                <span className="disclosure-provider">{providerLabel(item.source)}</span>
                {item.disclosureType && <span className="disclosure-type">{item.disclosureType}</span>}
              </div>
              <strong>{item.title}</strong>
              <span>{item.publisher} · {formatDate(item.publishedAt)}</span>
              <b aria-hidden="true">↗</b>
            </a>
          ))}
        </div>
      )}
      <p className="disclosure-note">공시는 투자 판단을 위한 원문 확인 자료이며, FinWatch의 투자 권유가 아닙니다.</p>
    </article>
  )
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
