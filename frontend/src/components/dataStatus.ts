import type { DataStatus } from './DataStatusBadge'

const knownStatuses = new Set<DataStatus>([
  'LIVE', 'DELAYED', 'REFERENCE', 'STALE', 'DEMO', 'PARTIAL', 'METADATA_ONLY', 'READY', 'UNAVAILABLE',
])

export function resolveDataStatus(value?: string | null): DataStatus {
  const normalized = value?.toUpperCase()
  // FRESH means the snapshot is current enough for its own cadence; it does not
  // prove that a realtime stream is connected.
  if (normalized === 'FRESH') return 'REFERENCE'
  if (normalized && knownStatuses.has(normalized as DataStatus)) return normalized as DataStatus
  return 'PARTIAL'
}
