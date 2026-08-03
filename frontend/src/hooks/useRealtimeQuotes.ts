import { useEffect, useMemo, useRef, useState } from 'react'
import type { StockRef } from '../types/stock'
import type { RealtimeFxRate } from '../types/fx'
import type {
  LiveQuote,
  IntradayCandle,
  IntradayCandleSnapshot,
  RealtimeConnectionState,
  RealtimeProviderStatus,
  RealtimeSnapshot,
} from '../types/realtime'
import { realtimeInstrumentKey } from '../utils/realtimeInstrument'
import { preferredQuote } from '../utils/realtimeQuote'

type RealtimeEvent = {
  type: 'snapshot' | 'quote' | 'status' | 'candle' | 'candles' | 'subscription' | 'fx'
  data: RealtimeSnapshot | LiveQuote | RealtimeProviderStatus | IntradayCandle | IntradayCandleSnapshot | RealtimeFxRate
}

function mergeCandle(current: IntradayCandle[], candle: IntradayCandle) {
  const index = current.findIndex((item) => item.time === candle.time)
  const next = index < 0
    ? [...current, candle]
    : current.map((item, itemIndex) => itemIndex === index ? candle : item)
  return next.sort((left, right) => left.time.localeCompare(right.time)).slice(-1000)
}

function sameQuote(left: LiveQuote | undefined, right: LiveQuote) {
  return left?.market === right.market
    && left.asOf === right.asOf
    && left.price === right.price
    && left.change === right.change
    && left.changeRate === right.changeRate
    && left.volume === right.volume
    && left.source === right.source
    && left.sessionStatus === right.sessionStatus
}

function websocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/quotes`
}

export function useRealtimeQuotes(enabled: boolean, selectedStock?: StockRef) {
  const [quotes, setQuotes] = useState<Record<string, LiveQuote>>({})
  const [providers, setProviders] = useState<Record<string, RealtimeProviderStatus>>({})
  const [intradayCandles, setIntradayCandles] = useState<Record<string, IntradayCandle[]>>({})
  const [fxRate, setFxRate] = useState<RealtimeFxRate | null>(null)
  const [connection, setConnection] = useState<RealtimeConnectionState>('disconnected')
  const socketRef = useRef<WebSocket | null>(null)
  const selectedStockRef = useRef<StockRef | undefined>(selectedStock)
  const selectedMarket = selectedStock?.market
  const selectedSymbol = selectedStock?.symbol

  useEffect(() => {
    selectedStockRef.current = selectedMarket && selectedSymbol
      ? { market: selectedMarket, symbol: selectedSymbol }
      : undefined
    const socket = socketRef.current
    if (selectedMarket && selectedSymbol && socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'select', market: selectedMarket, symbol: selectedSymbol }))
    }
  }, [selectedMarket, selectedSymbol])

  useEffect(() => {
    if (!enabled) {
      setConnection('disconnected')
      return
    }

    let socket: WebSocket | null = null
    let reconnectTimer: number | null = null
    let realtimeFrame: number | null = null
    let pendingQuotes: Record<string, LiveQuote> = {}
    let pendingCandles = new Map<string, IntradayCandle>()
    let disposed = false
    let attempt = 0

    const flushRealtimeUpdates = () => {
      realtimeFrame = null
      const quoteUpdates = pendingQuotes
      const candleUpdates = [...pendingCandles.values()]
      pendingQuotes = {}
      pendingCandles = new Map()

      if (Object.keys(quoteUpdates).length > 0) {
        setQuotes((current) => {
          let next = current
          for (const quote of Object.values(quoteUpdates)) {
            const key = realtimeInstrumentKey(quote.market, quote.symbol)
            const existing = next[key]
            const preferred = preferredQuote(existing, quote)
            if (preferred === existing || sameQuote(existing, preferred)) continue
            if (next === current) next = { ...current }
            next[key] = preferred
          }
          return next
        })
      }
      if (candleUpdates.length > 0) {
        setIntradayCandles((current) => {
          const next = { ...current }
          for (const candle of candleUpdates) {
            const key = realtimeInstrumentKey(candle.market, candle.symbol)
            next[key] = mergeCandle(next[key] ?? [], candle)
          }
          return next
        })
      }
    }

    const scheduleRealtimeFlush = () => {
      if (realtimeFrame == null) realtimeFrame = window.requestAnimationFrame(flushRealtimeUpdates)
    }

    const connect = () => {
      if (disposed) return
      setConnection(attempt === 0 ? 'connecting' : 'reconnecting')
      const connectionSocket = new WebSocket(websocketUrl())
      socket = connectionSocket
      socketRef.current = connectionSocket

      connectionSocket.onopen = () => {
        if (disposed || socketRef.current !== connectionSocket) {
          connectionSocket.close(1000, 'superseded')
          return
        }
        attempt = 0
        setConnection('connected')
        const currentStock = selectedStockRef.current
        if (currentStock) {
          connectionSocket.send(JSON.stringify({ type: 'select', market: currentStock.market, symbol: currentStock.symbol }))
        }
      }

      connectionSocket.onmessage = (message) => {
        if (disposed || socketRef.current !== connectionSocket) return
        try {
          const event = JSON.parse(String(message.data)) as RealtimeEvent
          if (event.type === 'snapshot') {
            const snapshot = event.data as RealtimeSnapshot
            setQuotes((current) => {
              let next = current
              for (const quote of snapshot.quotes) {
                const key = realtimeInstrumentKey(quote.market, quote.symbol)
                const existing = next[key]
                const preferred = preferredQuote(existing, quote)
                if (preferred === existing || sameQuote(existing, preferred)) continue
                if (next === current) next = { ...current }
                next[key] = preferred
              }
              return next
            })
            setProviders(Object.fromEntries(snapshot.providers.map((status) => [status.provider, status])))
            return
          }
          if (event.type === 'quote') {
            const quote = event.data as LiveQuote
            const key = realtimeInstrumentKey(quote.market, quote.symbol)
            pendingQuotes[key] = preferredQuote(pendingQuotes[key], quote)
            scheduleRealtimeFlush()
            return
          }
          if (event.type === 'status') {
            const status = event.data as RealtimeProviderStatus
            setProviders((current) => ({ ...current, [status.provider]: status }))
            return
          }
          if (event.type === 'fx') {
            const nextRate = event.data as RealtimeFxRate
            setFxRate((current) => (
              current != null && new Date(current.asOf) > new Date(nextRate.asOf)
                ? current
                : nextRate
            ))
            return
          }
          if (event.type === 'candles') {
            const snapshot = event.data as IntradayCandleSnapshot
            const grouped = snapshot.candles.reduce<Record<string, IntradayCandle[]>>((current, candle) => {
              const key = realtimeInstrumentKey(candle.market, candle.symbol)
              current[key] = [...(current[key] ?? []), candle]
              return current
            }, {})
            setIntradayCandles(grouped)
            return
          }
          if (event.type === 'candle') {
            const candle = event.data as IntradayCandle
            pendingCandles.set(`${realtimeInstrumentKey(candle.market, candle.symbol)}:${candle.time}`, candle)
            scheduleRealtimeFlush()
          }
        } catch {
          // Ignore malformed provider relay frames and keep the stream open.
        }
      }

      connectionSocket.onclose = () => {
        if (socketRef.current !== connectionSocket) return
        socketRef.current = null
        if (disposed) return
        attempt += 1
        setConnection('reconnecting')
        const delay = Math.min(15_000, 1_000 * 2 ** Math.min(attempt - 1, 4))
        reconnectTimer = window.setTimeout(() => {
          reconnectTimer = null
          connect()
        }, delay)
      }

      connectionSocket.onerror = () => {
        if (socketRef.current === connectionSocket) connectionSocket.close()
      }
    }

    connect()
    return () => {
      disposed = true
      if (reconnectTimer != null) window.clearTimeout(reconnectTimer)
      if (realtimeFrame != null) window.cancelAnimationFrame(realtimeFrame)
      socket?.close(1000, 'page closed')
      if (socketRef.current === socket) socketRef.current = null
    }
  }, [enabled])

  const connectedProviders = useMemo(
    () => Object.values(providers).filter((provider) => provider.state === 'CONNECTED').length,
    [providers],
  )

  return { quotes, providers, intradayCandles, fxRate, connection, connectedProviders }
}
