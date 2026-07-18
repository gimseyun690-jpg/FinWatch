export type DataStatus =
  | 'LIVE'
  | 'DELAYED'
  | 'REFERENCE'
  | 'STALE'
  | 'DEMO'
  | 'PARTIAL'
  | 'METADATA_ONLY'
  | 'READY'
  | 'UNAVAILABLE'

const labels: Record<DataStatus, string> = {
  LIVE: '실시간',
  DELAYED: '지연',
  REFERENCE: '참고값',
  STALE: '갱신 지연',
  DEMO: '시연 데이터',
  PARTIAL: '일부 준비',
  METADATA_ONLY: '원문 링크만',
  READY: '데이터 준비',
  UNAVAILABLE: '사용 불가',
}

type Props = {
  status: DataStatus
  detail?: string
  className?: string
}

export function DataStatusBadge({ status, detail, className = '' }: Props) {
  const text = detail ? `${labels[status]} · ${detail}` : labels[status]
  return (
    <span className={`data-status-badge status-${status.toLowerCase()} ${className}`.trim()} title={text}>
      <i aria-hidden="true" />
      {text}
    </span>
  )
}
