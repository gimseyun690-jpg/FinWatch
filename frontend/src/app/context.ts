import type { AuthSession } from '../types/auth'
import type { IntradayCandle, LiveQuote, RealtimeConnectionState } from '../types/realtime'
import type { StockRef } from '../types/stock'

export type ApiState = 'checking' | 'connected' | 'offline'

export type AppRouteContext = {
  apiState: ApiState
  session: AuthSession
  selectedStock: StockRef
  liveQuotes: Record<string, LiveQuote>
  intradayCandles: Record<string, IntradayCandle[]>
  realtimeConnection: RealtimeConnectionState
  connectedProviders: number
  adminRefreshKey: number
  selectStock: (stock: StockRef) => void
  recordAiUsage: () => void
  logout: () => void
  showAdminDetails: boolean
  setShowAdminDetails: (show: boolean) => void
}
