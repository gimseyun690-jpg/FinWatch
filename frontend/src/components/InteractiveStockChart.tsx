import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'
import { useOutletContext } from 'react-router'
import type { AppRouteContext } from '../app/context'
import {
  CandlestickSeries,
  ColorType,
  CrosshairMode,
  HistogramSeries,
  LineSeries,
  createSeriesMarkers,
  createChart,
  type CandlestickData,
  type HistogramData,
  type IChartApi,
  type ISeriesApi,
  type LineData,
  type SeriesMarker,
  type Time,
  type UTCTimestamp,
} from 'lightweight-charts'
import type { PriceInterval, PricePeriod, PricePoint, TechnicalAnalysis } from '../types/stock'
import { DataStatusBadge, type DataStatus } from './DataStatusBadge'
import { resolveDataStatus } from './dataStatus'

type Props = {
  symbol: string
  market: string
  currency: string
  items: PricePoint[]
  period: PricePeriod
  interval: PriceInterval
  loading: boolean
  error: string | null
  source: string
  freshness?: string | null
  asOf?: string | null
  events?: TechnicalAnalysis['events']
  onPeriodChange: (period: PricePeriod) => void
  onIntervalChange: (interval: PriceInterval) => void
  onRetry?: () => void
}

type ChartTool = 'pan' | 'trend' | 'horizontal'
type OscillatorPanel = 'rsi' | 'macd' | 'atr' | 'none'

type ChartSettings = {
  showMa5: boolean
  showMa20: boolean
  showMa60: boolean
  showBollinger: boolean
  showVolume: boolean
  showVolumeMa20: boolean
  showEvents: boolean
  oscillator: OscillatorPanel
}

const defaultChartSettings: ChartSettings = {
  showMa5: true,
  showMa20: true,
  showMa60: true,
  showBollinger: false,
  showVolume: true,
  showVolumeMa20: true,
  showEvents: false,
  oscillator: 'rsi',
}

let chartInstanceSequence = 0

function loadChartSettings(): ChartSettings {
  try {
    const stored = window.sessionStorage.getItem('finwatch-chart-settings')
    return stored ? { ...defaultChartSettings, ...JSON.parse(stored) as Partial<ChartSettings> } : defaultChartSettings
  } catch {
    return defaultChartSettings
  }
}

type NormalizedChartTime = string | number

type DrawingAnchor = {
  time: NormalizedChartTime
  price: number
}

type TrendDrawing = {
  id: string
  type: 'trend'
  anchors: [DrawingAnchor, DrawingAnchor]
}

type HorizontalDrawing = {
  id: string
  type: 'horizontal'
  anchor: DrawingAnchor
}

type Drawing = TrendDrawing | HorizontalDrawing

type ProjectedDrawing = {
  id: string
  type: Drawing['type']
  x1: number
  y1: number
  x2: number
  y2: number
}

type DragTarget = {
  drawingId: string
  anchorIndex: 0 | 1
}

type HoverData = {
  time: string
  open: number
  high: number
  low: number
  close: number
  volume: number
  ma5?: number
  ma20?: number
  ma60?: number
  volumeMa20?: number
  bollingerUpper?: number
  bollingerLower?: number
  oscillatorValue?: number
}

type ChartSeriesRefs = {
  candle: ISeriesApi<'Candlestick'> | null
  volume: ISeriesApi<'Histogram'> | null
  ma5: ISeriesApi<'Line'> | null
  ma20: ISeriesApi<'Line'> | null
  ma60: ISeriesApi<'Line'> | null
  volumeMa20: ISeriesApi<'Line'> | null
  bollingerUpper: ISeriesApi<'Line'> | null
  bollingerMiddle: ISeriesApi<'Line'> | null
  bollingerLower: ISeriesApi<'Line'> | null
  rsi: ISeriesApi<'Line'> | null
  rsiUpper: ISeriesApi<'Line'> | null
  rsiLower: ISeriesApi<'Line'> | null
  macdHistogram: ISeriesApi<'Histogram'> | null
  macdSignal: ISeriesApi<'Line'> | null
  atr: ISeriesApi<'Line'> | null
}

function emptySeriesRefs(): ChartSeriesRefs {
  return {
    candle: null,
    volume: null,
    ma5: null,
    ma20: null,
    ma60: null,
    volumeMa20: null,
    bollingerUpper: null,
    bollingerMiddle: null,
    bollingerLower: null,
    rsi: null,
    rsiUpper: null,
    rsiLower: null,
    macdHistogram: null,
    macdSignal: null,
    atr: null,
  }
}

function sameCandle(
  left: CandlestickData<Time>,
  right: CandlestickData<Time>,
) {
  return String(left.time) === String(right.time)
    && left.open === right.open
    && left.high === right.high
    && left.low === right.low
    && left.close === right.close
}

function isLatestCandleUpdate(
  previous: CandlestickData<Time>[],
  next: CandlestickData<Time>[],
) {
  if (previous.length === 0 || next.length === 0) return false
  if (next.length !== previous.length && next.length !== previous.length + 1) return false

  const unchangedLength = next.length === previous.length
    ? previous.length - 1
    : previous.length
  for (let index = 0; index < unchangedLength; index += 1) {
    if (!sameCandle(previous[index], next[index])) return false
  }

  const previousTime = previous.at(-1)!.time
  const nextTime = next.at(-1)!.time
  if (typeof previousTime === 'number' && typeof nextTime === 'number') {
    return nextTime >= previousTime
  }
  return String(nextTime) >= String(previousTime)
}

function syncCandles(
  series: ISeriesApi<'Candlestick'> | null,
  data: CandlestickData<Time>[],
  incremental: boolean,
) {
  if (!series) return
  const latest = data.at(-1)
  if (incremental && latest) series.update(latest)
  else series.setData(data)
}

function syncLine(
  series: ISeriesApi<'Line'> | null,
  data: LineData<Time>[],
  incremental: boolean,
) {
  if (!series) return
  const latest = data.at(-1)
  if (incremental && latest) series.update(latest)
  else series.setData(data)
}

function syncHistogram(
  series: ISeriesApi<'Histogram'> | null,
  data: HistogramData<Time>[],
  incremental: boolean,
) {
  if (!series) return
  const latest = data.at(-1)
  if (incremental && latest) series.update(latest)
  else series.setData(data)
}

const periods: Array<{ value: PricePeriod; label: string }> = [
  { value: '1M', label: '1개월' },
  { value: '3M', label: '3개월' },
  { value: '6M', label: '6개월' },
  { value: '1Y', label: '1년' },
  { value: 'ALL', label: '전체' },
]

const emptyDrawings: Drawing[] = []

function formatPrice(value: number, currency: string) {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency,
    maximumFractionDigits: currency === 'KRW' ? 0 : 2,
  }).format(value)
}

function formatVolume(value: number) {
  return `${new Intl.NumberFormat('ko-KR', { notation: 'compact', maximumFractionDigits: 1 }).format(value)}주`
}

function intervalLabel(interval: PriceInterval) {
  return interval === '1m' ? '1분봉' : interval === '1W' ? '주봉' : interval === '1M' ? '월봉' : '일봉'
}

function marketTimeZone(market: string) {
  return market === 'KRX' ? 'Asia/Seoul' : 'America/New_York'
}

function formatBasisTime(value: string | null | undefined, timeZone: string) {
  if (!value) return '확인 불가'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function latestSeriesValue(
  series: Array<LineData<Time> | HistogramData<Time>>,
  latestTime: Time | undefined,
) {
  const point = series.at(-1)
  if (!point || latestTime == null || normalizeTime(point.time) !== normalizeTime(latestTime)) return undefined
  return 'value' in point && typeof point.value === 'number' && Number.isFinite(point.value)
    ? point.value
    : undefined
}

function resolveChartStatus(
  source: string,
  freshness: string | null | undefined,
  interval: PriceInterval,
  hasError: boolean,
  hasData: boolean,
): DataStatus {
  if (hasError) return hasData ? 'STALE' : 'UNAVAILABLE'
  if (source.toUpperCase() === 'DEMO') return 'DEMO'
  if (source.toUpperCase() === 'LIVE' && interval === '1m') return 'LIVE'
  if (source.toUpperCase() === 'MIXED') return 'PARTIAL'
  if (freshness) return resolveDataStatus(freshness)
  return 'REFERENCE'
}

function eventMarkerText(type: NonNullable<TechnicalAnalysis['events']>[number]['type']) {
  const labels = {
    MA_GOLDEN_CROSS: 'MA 골든',
    MA_DEAD_CROSS: 'MA 데드',
    MACD_BULLISH_CROSS: 'MACD 상향',
    MACD_BEARISH_CROSS: 'MACD 하향',
    RSI_OVERSOLD_ENTER: 'RSI 과매도',
    RSI_OVERSOLD_EXIT: 'RSI 과매도 이탈',
    RSI_OVERBOUGHT_ENTER: 'RSI 과매수',
    RSI_OVERBOUGHT_EXIT: 'RSI 과매수 이탈',
  }
  return labels[type]
}

function toChartTime(value: string, market: string, interval: PriceInterval): Time {
  if (interval === '1m') {
    return Math.floor(new Date(value).getTime() / 1000) as UTCTimestamp
  }
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: marketTimeZone(market),
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(value))
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

function normalizeTime(value: Time): NormalizedChartTime {
  if (typeof value === 'string') return value
  if (typeof value === 'number') return value
  return `${value.year}-${String(value.month).padStart(2, '0')}-${String(value.day).padStart(2, '0')}`
}

function displayChartTime(value: Time, market: string, interval: PriceInterval) {
  if (typeof value === 'number' && interval === '1m') {
    return new Intl.DateTimeFormat('ko-KR', {
      timeZone: marketTimeZone(market),
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(new Date(value * 1000))
  }
  return String(normalizeTime(value))
}

function valueAtTime(
  series: Array<LineData<Time> | HistogramData<Time>>,
  time: NormalizedChartTime,
) {
  const point = series.find((item) => normalizeTime(item.time) === time)
  return point && 'value' in point && typeof point.value === 'number' ? point.value : undefined
}

function movingAverage(items: PricePoint[], times: Time[], windowSize: number): LineData<Time>[] {
  let rollingTotal = 0
  const result: LineData<Time>[] = []

  items.forEach((item, index) => {
    rollingTotal += item.close
    if (index >= windowSize) rollingTotal -= items[index - windowSize].close
    if (index >= windowSize - 1) {
      result.push({ time: times[index], value: rollingTotal / windowSize })
    }
  })

  return result
}

function bollingerBands(items: PricePoint[], times: Time[], windowSize = 20) {
  const upper: LineData<Time>[] = []
  const middle: LineData<Time>[] = []
  const lower: LineData<Time>[] = []
  items.forEach((_, index) => {
    if (index < windowSize - 1) return
    const window = items.slice(index - windowSize + 1, index + 1).map((item) => item.close)
    const average = window.reduce((sum, value) => sum + value, 0) / windowSize
    const variance = window.reduce((sum, value) => sum + (value - average) ** 2, 0) / windowSize
    const width = Math.sqrt(variance) * 2
    upper.push({ time: times[index], value: average + width })
    middle.push({ time: times[index], value: average })
    lower.push({ time: times[index], value: average - width })
  })
  return { upper, middle, lower }
}

function wilderRsi(items: PricePoint[], times: Time[], period = 14): LineData<Time>[] {
  if (items.length <= period) return []
  let averageGain = 0
  let averageLoss = 0
  for (let index = 1; index <= period; index += 1) {
    const delta = items[index].close - items[index - 1].close
    averageGain += Math.max(delta, 0)
    averageLoss += Math.max(-delta, 0)
  }
  averageGain /= period
  averageLoss /= period
  const result: LineData<Time>[] = []
  const value = () => averageLoss === 0 ? (averageGain === 0 ? 50 : 100) : 100 - 100 / (1 + averageGain / averageLoss)
  result.push({ time: times[period], value: value() })
  for (let index = period + 1; index < items.length; index += 1) {
    const delta = items[index].close - items[index - 1].close
    averageGain = (averageGain * (period - 1) + Math.max(delta, 0)) / period
    averageLoss = (averageLoss * (period - 1) + Math.max(-delta, 0)) / period
    result.push({ time: times[index], value: value() })
  }
  return result
}

function exponentialMovingAverage(values: number[], period: number) {
  if (values.length === 0) return []
  const multiplier = 2 / (period + 1)
  const result = [values[0]]
  for (let index = 1; index < values.length; index += 1) {
    result.push((values[index] - result[index - 1]) * multiplier + result[index - 1])
  }
  return result
}

function macd(items: PricePoint[], times: Time[]) {
  const closes = items.map((item) => item.close)
  const fast = exponentialMovingAverage(closes, 12)
  const slow = exponentialMovingAverage(closes, 26)
  const values = closes.map((_, index) => fast[index] - slow[index])
  const signalValues = exponentialMovingAverage(values, 9)
  return {
    histogram: values.map((value, index) => ({
      time: times[index],
      value: value - signalValues[index],
      color: value - signalValues[index] >= 0 ? 'rgba(75, 227, 154, .65)' : 'rgba(255, 111, 125, .65)',
    })),
    signal: signalValues.map((value, index) => ({ time: times[index], value })),
  }
}

function averageTrueRange(items: PricePoint[], times: Time[], period = 14): LineData<Time>[] {
  if (items.length <= period) return []
  const ranges = items.slice(1).map((item, index) => Math.max(
    item.high - item.low,
    Math.abs(item.high - items[index].close),
    Math.abs(item.low - items[index].close),
  ))
  let average = ranges.slice(0, period).reduce((sum, value) => sum + value, 0) / period
  const result: LineData<Time>[] = [{ time: times[period], value: average }]
  for (let index = period; index < ranges.length; index += 1) {
    average = (average * (period - 1) + ranges[index]) / period
    result.push({ time: times[index + 1], value: average })
  }
  return result
}

function drawingId() {
  return `drawing-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function InteractiveStockChart({
  symbol,
  market,
  currency,
  items,
  period,
  interval,
  loading,
  error,
  source,
  freshness,
  asOf,
  events,
  onPeriodChange,
  onIntervalChange,
  onRetry,
}: Props) {
  const context = useOutletContext<AppRouteContext | null>()
  const showAdminDetails = context?.showAdminDetails ?? true
  const shellRef = useRef<HTMLDivElement>(null)
  const chartContainerRef = useRef<HTMLDivElement>(null)
  const fullscreenButtonRef = useRef<HTMLButtonElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const seriesRefs = useRef<ChartSeriesRefs>(emptySeriesRefs())
  const projectionFrameRef = useRef<number | null>(null)
  const drawingsRef = useRef<Drawing[]>(emptyDrawings)
  const [hoverData, setHoverData] = useState<HoverData | null>(null)
  const [tool, setTool] = useState<ChartTool>('pan')
  const [pendingAnchor, setPendingAnchor] = useState<DrawingAnchor | null>(null)
  const [selectedDrawingId, setSelectedDrawingId] = useState<string | null>(null)
  const [dragTarget, setDragTarget] = useState<DragTarget | null>(null)
  const [drawingsBySymbol, setDrawingsBySymbol] = useState<Record<string, Drawing[]>>({})
  const [projectedDrawings, setProjectedDrawings] = useState<ProjectedDrawing[]>([])
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [fullscreenFallback, setFullscreenFallback] = useState(false)
  const [drawingFeedback, setDrawingFeedback] = useState('')
  const initialSettings = useRef(loadChartSettings()).current
  const [showMa5, setShowMa5] = useState(initialSettings.showMa5)
  const [showMa20, setShowMa20] = useState(initialSettings.showMa20)
  const [showMa60, setShowMa60] = useState(initialSettings.showMa60)
  const [showBollinger, setShowBollinger] = useState(initialSettings.showBollinger)
  const [showVolume, setShowVolume] = useState(initialSettings.showVolume)
  const [showVolumeMa20, setShowVolumeMa20] = useState(initialSettings.showVolumeMa20)
  const [showEvents, setShowEvents] = useState(initialSettings.showEvents)
  const [oscillator, setOscillator] = useState<OscillatorPanel>(initialSettings.oscillator)

  useEffect(() => {
    window.sessionStorage.setItem('finwatch-chart-settings', JSON.stringify({
      showMa5,
      showMa20,
      showMa60,
      showBollinger,
      showVolume,
      showVolumeMa20,
      showEvents,
      oscillator,
    } satisfies ChartSettings))
  }, [oscillator, showBollinger, showEvents, showMa5, showMa20, showMa60, showVolume, showVolumeMa20])

  const drawingKey = `${symbol}:${interval}`
  const drawings = drawingsBySymbol[drawingKey] ?? emptyDrawings

  const chartData = useMemo(() => {
    const times = items.map((item) => toChartTime(item.time, market, interval))
    const candles: CandlestickData<Time>[] = items.map((item, index) => ({
      time: times[index],
      open: item.open,
      high: item.high,
      low: item.low,
      close: item.close,
    }))
    const volumes: HistogramData<Time>[] = items.map((item, index) => ({
      time: times[index],
      value: item.volume,
      color: item.close >= item.open ? 'rgba(75, 227, 154, .5)' : 'rgba(255, 111, 125, .5)',
    }))
    const volumeItems = items.map((item) => ({ ...item, close: item.volume }))
    const indicatorLine = (selector: (item: NonNullable<PricePoint['indicators']>) => number | null) => items
      .flatMap<LineData<Time>>((item, index) => {
        const value = item.indicators ? selector(item.indicators) : null
        return value == null ? [] : [{ time: times[index], value }]
      })
    const hasProviderIndicators = items.some((item) => item.indicators != null)
    const fallbackBollinger = bollingerBands(items, times)
    const fallbackMacd = macd(items, times)
    return {
      candles,
      volumes,
      ma5: hasProviderIndicators ? indicatorLine((value) => value.ma5) : movingAverage(items, times, 5),
      ma20: hasProviderIndicators ? indicatorLine((value) => value.ma20) : movingAverage(items, times, 20),
      ma60: hasProviderIndicators ? indicatorLine((value) => value.ma60) : movingAverage(items, times, 60),
      volumeMa20: hasProviderIndicators
        ? indicatorLine((value) => value.volumeMa20)
        : movingAverage(volumeItems, times, 20),
      bollinger: hasProviderIndicators ? {
        upper: indicatorLine((value) => value.bollingerUpper),
        middle: indicatorLine((value) => value.bollingerMiddle),
        lower: indicatorLine((value) => value.bollingerLower),
      } : fallbackBollinger,
      rsi: hasProviderIndicators ? indicatorLine((value) => value.rsi) : wilderRsi(items, times),
      macd: hasProviderIndicators ? {
        histogram: items.flatMap<HistogramData<Time>>((item, index) => {
          const value = item.indicators?.macdHistogram
          return value == null ? [] : [{
            time: times[index],
            value,
            color: value >= 0 ? 'rgba(75, 227, 154, .65)' : 'rgba(255, 111, 125, .65)',
          }]
        }),
        signal: indicatorLine((value) => value.macdSignal),
      } : fallbackMacd,
      atr: hasProviderIndicators ? indicatorLine((value) => value.atr) : averageTrueRange(items, times),
      rsiUpper: times.map((time) => ({ time, value: 70 })),
      rsiLower: times.map((time) => ({ time, value: 30 })),
    }
  }, [interval, items, market])
  const chartDataRef = useRef(chartData)
  const renderedChartDataRef = useRef<typeof chartData | null>(null)

  useEffect(() => {
    chartDataRef.current = chartData
  }, [chartData])

  const updateCurrentDrawings = useCallback((updater: (current: Drawing[]) => Drawing[]) => {
    setDrawingsBySymbol((current) => ({
      ...current,
      [drawingKey]: updater(current[drawingKey] ?? emptyDrawings),
    }))
  }, [drawingKey])

  const refreshProjection = useCallback(() => {
    const chart = chartRef.current
    const candleSeries = candleSeriesRef.current
    const container = chartContainerRef.current
    if (!chart || !candleSeries || !container) {
      setProjectedDrawings([])
      return
    }

    const width = container.clientWidth
    const next = drawingsRef.current.flatMap<ProjectedDrawing>((drawing) => {
      if (drawing.type === 'horizontal') {
        const y = candleSeries.priceToCoordinate(drawing.anchor.price)
        if (y == null) return []
        return [{ id: drawing.id, type: drawing.type, x1: 0, y1: y, x2: width, y2: y }]
      }

      const x1 = chart.timeScale().timeToCoordinate(drawing.anchors[0].time as Time)
      const y1 = candleSeries.priceToCoordinate(drawing.anchors[0].price)
      const x2 = chart.timeScale().timeToCoordinate(drawing.anchors[1].time as Time)
      const y2 = candleSeries.priceToCoordinate(drawing.anchors[1].price)
      if (x1 == null || y1 == null || x2 == null || y2 == null) return []
      return [{ id: drawing.id, type: drawing.type, x1, y1, x2, y2 }]
    })
    setProjectedDrawings(next)
  }, [])

  const queueProjection = useCallback(() => {
    if (projectionFrameRef.current != null) cancelAnimationFrame(projectionFrameRef.current)
    projectionFrameRef.current = requestAnimationFrame(refreshProjection)
  }, [refreshProjection])

  useEffect(() => {
    drawingsRef.current = drawings
    queueProjection()
  }, [drawings, queueProjection])

  useEffect(() => {
    const container = chartContainerRef.current
    if (!container) return

    const initialData = chartDataRef.current
    const createdSeries = emptySeriesRefs()
    chartInstanceSequence += 1
    container.dataset.chartInstance = String(chartInstanceSequence)

    const chart = createChart(container, {
      width: container.clientWidth,
      height: container.clientHeight,
      layout: {
        background: { type: ColorType.Solid, color: '#0a121a' },
        textColor: '#8d9aa7',
        panes: {
          separatorColor: '#23303d',
          separatorHoverColor: '#25d6c8',
          enableResize: false,
        },
      },
      localization: {
        locale: 'ko-KR',
        priceFormatter: (price: number) => formatPrice(price, currency),
      },
      grid: {
        vertLines: { color: 'rgba(35, 48, 61, .55)' },
        horzLines: { color: 'rgba(35, 48, 61, .55)' },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: '#6b7e8f', labelBackgroundColor: '#17222e' },
        horzLine: { color: '#6b7e8f', labelBackgroundColor: '#17222e' },
      },
      rightPriceScale: { borderColor: '#23303d' },
      timeScale: {
        borderColor: '#23303d',
        timeVisible: interval === '1m',
        secondsVisible: false,
        rightOffset: 4,
        barSpacing: 8,
        minBarSpacing: 3,
      },
      handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false },
      handleScale: { axisPressedMouseMove: true, mouseWheel: true, pinch: true },
    })

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#4be39a',
      downColor: '#ff6f7d',
      borderUpColor: '#4be39a',
      borderDownColor: '#ff6f7d',
      wickUpColor: '#4be39a',
      wickDownColor: '#ff6f7d',
      priceFormat: {
        type: 'custom',
        minMove: currency === 'KRW' ? 1 : 0.01,
        formatter: (price: number) => formatPrice(price, currency),
      },
    })
    createdSeries.candle = candleSeries
    candleSeries.setData(initialData.candles)
    if (interval === '1D' && showEvents && events && events.length > 0) {
      const availableTimes = new Set(initialData.candles.map((item) => String(item.time)))
      const markers: SeriesMarker<Time>[] = events
        .map((event) => ({ event, time: toChartTime(event.time, market, interval) }))
        .filter(({ time }) => availableTimes.has(String(time)))
        .map(({ event, time }) => ({
          time,
          position: event.signal === 'BUY' ? 'belowBar' : 'aboveBar',
          shape: event.signal === 'BUY' ? 'arrowUp' : event.signal === 'SELL' ? 'arrowDown' : 'circle',
          color: event.signal === 'BUY' ? '#4be39a' : event.signal === 'SELL' ? '#ff6f7d' : '#f5bd50',
          text: eventMarkerText(event.type),
        }))
      createSeriesMarkers(candleSeries, markers)
    }

    if (showMa5) {
      const series = chart.addSeries(LineSeries, {
        color: '#f5bd50', lineWidth: 2, priceLineVisible: false, lastValueVisible: false, title: 'MA5',
      })
      series.setData(initialData.ma5)
      createdSeries.ma5 = series
    }
    if (showMa20) {
      const series = chart.addSeries(LineSeries, {
        color: '#25d6c8', lineWidth: 2, priceLineVisible: false, lastValueVisible: false, title: 'MA20',
      })
      series.setData(initialData.ma20)
      createdSeries.ma20 = series
    }
    if (showMa60) {
      const series = chart.addSeries(LineSeries, {
        color: '#ad86ff', lineWidth: 2, priceLineVisible: false, lastValueVisible: false, title: 'MA60',
      })
      series.setData(initialData.ma60)
      createdSeries.ma60 = series
    }
    if (showBollinger) {
      const upper = chart.addSeries(LineSeries, {
        color: 'rgba(66, 153, 225, .75)', lineWidth: 1, priceLineVisible: false, lastValueVisible: false, title: 'BB Upper',
      })
      const middle = chart.addSeries(LineSeries, {
        color: 'rgba(66, 153, 225, .45)', lineWidth: 1, priceLineVisible: false, lastValueVisible: false, title: 'BB Mid',
      })
      const lower = chart.addSeries(LineSeries, {
        color: 'rgba(66, 153, 225, .75)', lineWidth: 1, priceLineVisible: false, lastValueVisible: false, title: 'BB Lower',
      })
      upper.setData(initialData.bollinger.upper)
      middle.setData(initialData.bollinger.middle)
      lower.setData(initialData.bollinger.lower)
      createdSeries.bollingerUpper = upper
      createdSeries.bollingerMiddle = middle
      createdSeries.bollingerLower = lower
    }

    let volumeSeries: ISeriesApi<'Histogram'> | null = null
    const hasVolumePane = showVolume || showVolumeMa20
    if (showVolume) {
      volumeSeries = chart.addSeries(HistogramSeries, {
        priceFormat: { type: 'volume' },
        priceLineVisible: false,
        lastValueVisible: false,
      }, 1)
      volumeSeries.setData(initialData.volumes)
      createdSeries.volume = volumeSeries
    }
    if (showVolumeMa20) {
      const volumeAverageSeries = chart.addSeries(LineSeries, {
        color: '#f5bd50',
        lineWidth: 2,
        priceFormat: { type: 'volume' },
        priceLineVisible: false,
        lastValueVisible: false,
        title: 'Volume MA20',
      }, 1)
      volumeAverageSeries.setData(initialData.volumeMa20)
      createdSeries.volumeMa20 = volumeAverageSeries
    }

    const oscillatorPane = hasVolumePane ? 2 : 1
    if (oscillator === 'rsi') {
      const rsiSeries = chart.addSeries(LineSeries, {
        color: '#25d6c8', lineWidth: 2, priceLineVisible: false, lastValueVisible: true, title: 'RSI14',
      }, oscillatorPane)
      const upper = chart.addSeries(LineSeries, {
        color: 'rgba(255, 111, 125, .38)', lineWidth: 1, priceLineVisible: false, lastValueVisible: false,
      }, oscillatorPane)
      const lower = chart.addSeries(LineSeries, {
        color: 'rgba(75, 227, 154, .38)', lineWidth: 1, priceLineVisible: false, lastValueVisible: false,
      }, oscillatorPane)
      rsiSeries.setData(initialData.rsi)
      upper.setData(initialData.rsiUpper)
      lower.setData(initialData.rsiLower)
      createdSeries.rsi = rsiSeries
      createdSeries.rsiUpper = upper
      createdSeries.rsiLower = lower
    } else if (oscillator === 'macd') {
      const histogram = chart.addSeries(HistogramSeries, {
        priceLineVisible: false, lastValueVisible: false, title: 'MACD',
      }, oscillatorPane)
      const signal = chart.addSeries(LineSeries, {
        color: '#f5bd50', lineWidth: 2, priceLineVisible: false, lastValueVisible: false, title: 'Signal',
      }, oscillatorPane)
      histogram.setData(initialData.macd.histogram)
      signal.setData(initialData.macd.signal)
      createdSeries.macdHistogram = histogram
      createdSeries.macdSignal = signal
    } else if (oscillator === 'atr') {
      const atrSeries = chart.addSeries(LineSeries, {
        color: '#ad86ff', lineWidth: 2, priceLineVisible: false, lastValueVisible: true, title: 'ATR14',
      }, oscillatorPane)
      atrSeries.setData(initialData.atr)
      createdSeries.atr = atrSeries
    }

    chart.panes().forEach((pane, index) => pane.setStretchFactor(index === 0 ? 4 : 1))

    const crosshairHandler = (param: Parameters<IChartApi['subscribeCrosshairMove']>[0] extends (value: infer P) => void ? P : never) => {
      if (param.time == null) {
        setHoverData(null)
        return
      }
      const candle = param.seriesData.get(candleSeries)
      const volume = volumeSeries ? param.seriesData.get(volumeSeries) : null
      if (!candle || !('open' in candle) || !('high' in candle) || !('low' in candle) || !('close' in candle)) {
        setHoverData(null)
        return
      }
      const normalizedTime = normalizeTime(param.time)
      const currentData = chartDataRef.current
      setHoverData({
        time: displayChartTime(param.time, market, interval),
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
        volume: volume && 'value' in volume && typeof volume.value === 'number' ? volume.value : 0,
        ma5: valueAtTime(currentData.ma5, normalizedTime),
        ma20: valueAtTime(currentData.ma20, normalizedTime),
        ma60: valueAtTime(currentData.ma60, normalizedTime),
        volumeMa20: valueAtTime(currentData.volumeMa20, normalizedTime),
        bollingerUpper: valueAtTime(currentData.bollinger.upper, normalizedTime),
        bollingerLower: valueAtTime(currentData.bollinger.lower, normalizedTime),
        oscillatorValue: oscillator === 'rsi'
          ? valueAtTime(currentData.rsi, normalizedTime)
          : oscillator === 'atr'
            ? valueAtTime(currentData.atr, normalizedTime)
            : oscillator === 'macd'
              ? valueAtTime(currentData.macd.histogram, normalizedTime)
              : undefined,
      })
      queueProjection()
    }

    chart.subscribeCrosshairMove(crosshairHandler)
    chart.timeScale().subscribeVisibleLogicalRangeChange(queueProjection)
    chart.timeScale().fitContent()
    chartRef.current = chart
    candleSeriesRef.current = candleSeries
    seriesRefs.current = createdSeries
    renderedChartDataRef.current = initialData

    const resizeObserver = new ResizeObserver(() => {
      chart.resize(container.clientWidth, container.clientHeight)
      queueProjection()
    })
    resizeObserver.observe(container)
    container.addEventListener('wheel', queueProjection, { passive: true })
    container.addEventListener('pointerup', queueProjection)
    queueProjection()

    return () => {
      resizeObserver.disconnect()
      container.removeEventListener('wheel', queueProjection)
      container.removeEventListener('pointerup', queueProjection)
      chart.unsubscribeCrosshairMove(crosshairHandler)
      chart.timeScale().unsubscribeVisibleLogicalRangeChange(queueProjection)
      chart.remove()
      chartRef.current = null
      candleSeriesRef.current = null
      seriesRefs.current = emptySeriesRefs()
      renderedChartDataRef.current = null
      setHoverData(null)
    }
  }, [
    currency,
    events,
    interval,
    market,
    oscillator,
    queueProjection,
    showBollinger,
    showEvents,
    showMa5,
    showMa20,
    showMa60,
    showVolume,
    showVolumeMa20,
  ])

  useEffect(() => {
    if (!chartRef.current || !seriesRefs.current.candle) return

    const previousData = renderedChartDataRef.current
    if (previousData === chartData) return
    const incremental = previousData != null
      && isLatestCandleUpdate(previousData.candles, chartData.candles)
    const series = seriesRefs.current

    syncCandles(series.candle, chartData.candles, incremental)
    syncHistogram(series.volume, chartData.volumes, incremental)
    syncLine(series.ma5, chartData.ma5, incremental)
    syncLine(series.ma20, chartData.ma20, incremental)
    syncLine(series.ma60, chartData.ma60, incremental)
    syncLine(series.volumeMa20, chartData.volumeMa20, incremental)
    syncLine(series.bollingerUpper, chartData.bollinger.upper, incremental)
    syncLine(series.bollingerMiddle, chartData.bollinger.middle, incremental)
    syncLine(series.bollingerLower, chartData.bollinger.lower, incremental)
    syncLine(series.rsi, chartData.rsi, incremental)
    syncLine(series.rsiUpper, chartData.rsiUpper, incremental)
    syncLine(series.rsiLower, chartData.rsiLower, incremental)
    syncHistogram(series.macdHistogram, chartData.macd.histogram, incremental)
    syncLine(series.macdSignal, chartData.macd.signal, incremental)
    syncLine(series.atr, chartData.atr, incremental)

    renderedChartDataRef.current = chartData
    queueProjection()
  }, [chartData, queueProjection])

  const pointerToAnchor = useCallback((clientX: number, clientY: number): DrawingAnchor | null => {
    const chart = chartRef.current
    const candleSeries = candleSeriesRef.current
    const container = chartContainerRef.current
    if (!chart || !candleSeries || !container) return null
    const rect = container.getBoundingClientRect()
    const x = clientX - rect.left
    const y = clientY - rect.top
    if (x < 0 || x > rect.width || y < 0 || y > chart.paneSize(0).height) return null
    const time = chart.timeScale().coordinateToTime(x)
    const price = candleSeries.coordinateToPrice(y)
    if (time == null || price == null) return null
    return { time: normalizeTime(time), price: Number(price) }
  }, [])

  function handleDrawingPointerDown(event: ReactPointerEvent<SVGSVGElement>) {
    if (tool === 'pan') return
    event.preventDefault()
    const anchor = pointerToAnchor(event.clientX, event.clientY)
    if (!anchor) return
    setSelectedDrawingId(null)

    if (tool === 'horizontal') {
      const id = drawingId()
      updateCurrentDrawings((current) => [...current, { id, type: 'horizontal', anchor }])
      setSelectedDrawingId(id)
      setDrawingFeedback(`수평선을 ${formatPrice(anchor.price, currency)}에 추가했습니다.`)
      setTool('pan')
      return
    }

    if (pendingAnchor == null) {
      setPendingAnchor(anchor)
      return
    }

    const id = drawingId()
    updateCurrentDrawings((current) => [
      ...current,
      { id, type: 'trend', anchors: [pendingAnchor, anchor] },
    ])
    setSelectedDrawingId(id)
    setDrawingFeedback('추세선을 추가했습니다.')
    setPendingAnchor(null)
    setTool('pan')
  }

  function selectDrawing(event: ReactPointerEvent<SVGElement>, drawingIdValue: string) {
    event.preventDefault()
    event.stopPropagation()
    setSelectedDrawingId(drawingIdValue)
  }

  function startDrag(
    event: ReactPointerEvent<SVGElement>,
    drawingIdValue: string,
    anchorIndex: 0 | 1,
  ) {
    selectDrawing(event, drawingIdValue)
    setDragTarget({ drawingId: drawingIdValue, anchorIndex })
  }

  useEffect(() => {
    if (!dragTarget) return

    const move = (event: PointerEvent) => {
      const anchor = pointerToAnchor(event.clientX, event.clientY)
      if (!anchor) return
      updateCurrentDrawings((current) => current.map((drawing) => {
        if (drawing.id !== dragTarget.drawingId) return drawing
        if (drawing.type === 'horizontal') return { ...drawing, anchor }
        const anchors: [DrawingAnchor, DrawingAnchor] = [...drawing.anchors]
        anchors[dragTarget.anchorIndex] = anchor
        return { ...drawing, anchors }
      }))
    }
    const stop = () => setDragTarget(null)
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop, { once: true })
    return () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', stop)
    }
  }, [dragTarget, pointerToAnchor, updateCurrentDrawings])

  const deleteSelectedDrawing = useCallback(() => {
    if (!selectedDrawingId) return
    updateCurrentDrawings((current) => current.filter((drawing) => drawing.id !== selectedDrawingId))
    setSelectedDrawingId(null)
    setDrawingFeedback('선택한 선을 삭제했습니다.')
  }, [selectedDrawingId, updateCurrentDrawings])

  useEffect(() => {
    const handleDelete = (event: KeyboardEvent) => {
      if (event.key !== 'Delete' && event.key !== 'Backspace') return
      const target = event.target
      if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) return
      if (selectedDrawingId) {
        event.preventDefault()
        deleteSelectedDrawing()
      }
    }
    document.addEventListener('keydown', handleDelete)
    return () => document.removeEventListener('keydown', handleDelete)
  }, [deleteSelectedDrawing, selectedDrawingId])

  function clearDrawings() {
    if (drawings.length === 0) return
    if (!window.confirm('현재 종목에 그린 모든 선을 삭제할까요?')) return
    updateCurrentDrawings(() => [])
    setSelectedDrawingId(null)
    setPendingAnchor(null)
    setDrawingFeedback('현재 종목의 모든 그리기를 삭제했습니다.')
  }

  function activateTool(nextTool: ChartTool) {
    setTool(nextTool)
    setPendingAnchor(null)
    if (nextTool !== 'pan') setSelectedDrawingId(null)
  }

  useEffect(() => {
    setPendingAnchor(null)
    setSelectedDrawingId(null)
    setTool('pan')
  }, [drawingKey])

  function resetVisibleRange() {
    chartRef.current?.timeScale().fitContent()
    queueProjection()
  }

  function resetIndicators() {
    setShowMa5(defaultChartSettings.showMa5)
    setShowMa20(defaultChartSettings.showMa20)
    setShowMa60(defaultChartSettings.showMa60)
    setShowBollinger(defaultChartSettings.showBollinger)
    setShowVolume(defaultChartSettings.showVolume)
    setShowVolumeMa20(defaultChartSettings.showVolumeMa20)
    setShowEvents(defaultChartSettings.showEvents)
    setOscillator(defaultChartSettings.oscillator)
  }

  useEffect(() => {
    let wasFullscreen = false
    const handleFullscreenChange = () => {
      const active = document.fullscreenElement === shellRef.current
      setIsFullscreen(active)
      if (wasFullscreen && !active) window.setTimeout(() => fullscreenButtonRef.current?.focus(), 0)
      wasFullscreen = active
      queueProjection()
    }
    document.addEventListener('fullscreenchange', handleFullscreenChange)
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange)
  }, [queueProjection])

  useEffect(() => {
    if (!fullscreenFallback) return
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      setFullscreenFallback(false)
      setIsFullscreen(false)
      window.setTimeout(() => fullscreenButtonRef.current?.focus(), 0)
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [fullscreenFallback])

  async function toggleFullscreen() {
    const shell = shellRef.current
    if (!shell) return
    if (isFullscreen) {
      if (document.fullscreenElement === shell) await document.exitFullscreen()
      else {
        setFullscreenFallback(false)
        setIsFullscreen(false)
        fullscreenButtonRef.current?.focus()
      }
      return
    }

    try {
      if (shell.requestFullscreen) {
        await shell.requestFullscreen()
        setIsFullscreen(true)
      } else {
        setFullscreenFallback(true)
        setIsFullscreen(true)
      }
    } catch {
      setFullscreenFallback(true)
      setIsFullscreen(true)
    }
  }

  const latest = items.at(-1)
  const latestChartTime = chartData.candles.at(-1)?.time
  const latestMa5 = latestSeriesValue(chartData.ma5, latestChartTime)
  const latestMa20 = latestSeriesValue(chartData.ma20, latestChartTime)
  const latestMa60 = latestSeriesValue(chartData.ma60, latestChartTime)
  const latestVolumeMa20 = latestSeriesValue(chartData.volumeMa20, latestChartTime)
  const latestBollingerUpper = latestSeriesValue(chartData.bollinger.upper, latestChartTime)
  const latestBollingerMiddle = latestSeriesValue(chartData.bollinger.middle, latestChartTime)
  const latestBollingerLower = latestSeriesValue(chartData.bollinger.lower, latestChartTime)
  const latestRsi = latestSeriesValue(chartData.rsi, latestChartTime)
  const latestMacdHistogram = latestSeriesValue(chartData.macd.histogram, latestChartTime)
  const latestMacdSignal = latestSeriesValue(chartData.macd.signal, latestChartTime)
  const latestAtr = latestSeriesValue(chartData.atr, latestChartTime)
  const selectedDrawing = drawings.find((drawing) => drawing.id === selectedDrawingId)
  const timeZone = marketTimeZone(market)
  const basisTime = latest?.time ?? asOf
  const dataStatus = resolveChartStatus(source, freshness, interval, error != null, items.length > 0)
  const indicatorValue = (
    value: number | undefined,
    requiredItems: number,
    formatter: (current: number) => string,
  ) => value == null || items.length < requiredItems
    ? `데이터 부족 (${items.length}/${requiredItems})`
    : formatter(value)

  function drawingAnchor(item: PricePoint): DrawingAnchor {
    return {
      time: normalizeTime(toChartTime(item.time, market, interval)),
      price: item.close,
    }
  }

  function addKeyboardTrend() {
    if (items.length < 2) {
      setDrawingFeedback('추세선을 만들려면 가격 데이터가 두 개 이상 필요합니다.')
      return
    }
    const endIndex = items.length - 1
    const startIndex = Math.max(0, endIndex - 19)
    const id = drawingId()
    updateCurrentDrawings((current) => [...current, {
      id,
      type: 'trend',
      anchors: [drawingAnchor(items[startIndex]), drawingAnchor(items[endIndex])],
    }])
    setSelectedDrawingId(id)
    setDrawingFeedback(`최근 ${endIndex - startIndex + 1}개 봉의 종가를 잇는 추세선을 추가했습니다.`)
  }

  function addKeyboardHorizontal() {
    if (!latest) {
      setDrawingFeedback('수평선을 만들 현재가 데이터가 없습니다.')
      return
    }
    const id = drawingId()
    updateCurrentDrawings((current) => [...current, {
      id,
      type: 'horizontal',
      anchor: drawingAnchor(latest),
    }])
    setSelectedDrawingId(id)
    setDrawingFeedback(`최근 종가 ${formatPrice(latest.close, currency)}에 수평선을 추가했습니다.`)
  }

  function moveSelectedPrice(direction: -1 | 1) {
    if (!selectedDrawing) return
    const basis = latest?.close ?? (selectedDrawing.type === 'horizontal'
      ? selectedDrawing.anchor.price
      : selectedDrawing.anchors[1].price)
    const step = Math.max(currency === 'KRW' ? 1 : 0.01, Math.abs(basis) * 0.005)
    updateCurrentDrawings((current) => current.map((drawing) => {
      if (drawing.id !== selectedDrawing.id) return drawing
      if (drawing.type === 'horizontal') {
        return { ...drawing, anchor: { ...drawing.anchor, price: drawing.anchor.price + step * direction } }
      }
      return {
        ...drawing,
        anchors: drawing.anchors.map((anchor) => ({
          ...anchor,
          price: anchor.price + step * direction,
        })) as [DrawingAnchor, DrawingAnchor],
      }
    }))
    setDrawingFeedback(`선택한 선을 가격 기준 ${direction > 0 ? '위' : '아래'}로 0.5% 이동했습니다.`)
  }

  function moveSelectedTime(direction: -1 | 1) {
    if (!selectedDrawing || selectedDrawing.type !== 'trend' || items.length < 2) return
    const times = items.map((item) => normalizeTime(toChartTime(item.time, market, interval)))
    const shift = (anchor: DrawingAnchor): DrawingAnchor => {
      const currentIndex = times.findIndex((time) => String(time) === String(anchor.time))
      const targetIndex = Math.min(times.length - 1, Math.max(0, (currentIndex < 0 ? times.length - 1 : currentIndex) + direction))
      return { ...anchor, time: times[targetIndex] }
    }
    updateCurrentDrawings((current) => current.map((drawing) => drawing.id === selectedDrawing.id && drawing.type === 'trend'
      ? { ...drawing, anchors: drawing.anchors.map(shift) as [DrawingAnchor, DrawingAnchor] }
      : drawing))
    setDrawingFeedback(`선택한 추세선을 ${direction > 0 ? '다음' : '이전'} 봉으로 이동했습니다.`)
  }

  const statusText = tool === 'trend'
    ? pendingAnchor ? '추세선의 두 번째 지점을 선택하세요.' : '추세선의 시작 지점을 선택하세요.'
    : tool === 'horizontal'
      ? '수평선을 놓을 가격 지점을 선택하세요.'
      : selectedDrawingId
        ? '선을 선택했습니다. 핸들을 드래그하거나 Delete 키로 삭제할 수 있습니다.'
        : '드래그로 이동하고 휠 또는 핀치로 확대·축소할 수 있습니다.'

  return (
    <div
      ref={shellRef}
      className={`interactive-chart-shell${fullscreenFallback ? ' fullscreen-fallback' : ''}`}
    >
      <div className="chart-toolbar" role="toolbar" aria-label="상세 차트 도구 모음">
        <div className="chart-range-controls chart-tool-group" role="group" aria-label="시간 설정">
          <div className="chart-intervals" role="group" aria-label="봉 간격">
            <button type="button" aria-pressed={interval === '1D'} onClick={() => onIntervalChange('1D')}>일봉</button>
            <button type="button" aria-pressed={interval === '1W'} onClick={() => onIntervalChange('1W')}>주봉</button>
            <button type="button" aria-pressed={interval === '1M'} onClick={() => onIntervalChange('1M')}>월봉</button>
            <button type="button" aria-pressed={interval === '1m'} onClick={() => onIntervalChange('1m')}>1분봉</button>
          </div>
          {interval !== '1m' ? (
            <div className="chart-periods" role="group" aria-label="조회 기간">
              {periods.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  aria-pressed={period === option.value}
                  onClick={() => onPeriodChange(option.value)}
                  disabled={loading}
                  title={loading ? '가격 데이터를 갱신하는 동안 조회 기간을 변경할 수 없습니다.' : undefined}
                >
                  {option.label}
                </button>
              ))}
            </div>
          ) : <span className="intraday-session-label"><i />현재 서버 세션 · 최대 390봉</span>}
        </div>
        <div className="chart-tools">
          <div className="chart-tool-group chart-view-tools" role="group" aria-label="보기 설정">
            <button type="button" onClick={resetVisibleRange}>범위 초기화</button>
          </div>
          <div className="chart-tool-group chart-drawing-tools" role="group" aria-label="그리기 도구">
            <button type="button" aria-pressed={tool === 'pan'} onClick={() => activateTool('pan')}>이동</button>
            <button type="button" aria-pressed={tool === 'trend'} onClick={() => activateTool('trend')}>추세선</button>
            <button type="button" aria-pressed={tool === 'horizontal'} onClick={() => activateTool('horizontal')}>수평선</button>
            <button type="button" onClick={deleteSelectedDrawing} disabled={!selectedDrawingId} title={!selectedDrawingId ? '먼저 삭제할 선을 선택해 주세요.' : undefined}>선 삭제</button>
            <button type="button" onClick={clearDrawings} disabled={drawings.length === 0} title={drawings.length === 0 ? '삭제할 그리기가 없습니다.' : undefined}>그리기 전체 삭제</button>
          </div>
          <div className="chart-tool-group chart-screen-tools" role="group" aria-label="화면 설정">
            <button
              ref={fullscreenButtonRef}
              type="button"
              aria-pressed={isFullscreen}
              onClick={() => void toggleFullscreen()}
            >
              {isFullscreen ? '전체화면 종료' : '전체화면'}
            </button>
          </div>
        </div>
      </div>

      <details className="chart-keyboard-drawing">
        <summary>키보드 그리기</summary>
        <p>최근 봉을 기준으로 선을 만든 뒤 아래 버튼만으로 선택·이동·삭제할 수 있습니다.</p>
        <div className="keyboard-drawing-create" role="group" aria-label="키보드로 선 추가">
          <button
            type="button"
            onClick={addKeyboardTrend}
            disabled={items.length < 2}
            title={items.length < 2 ? '추세선을 만들려면 가격 데이터가 두 개 이상 필요합니다.' : undefined}
          >
            최근 추세선 추가
          </button>
          <button
            type="button"
            onClick={addKeyboardHorizontal}
            disabled={!latest}
            title={!latest ? '수평선을 만들 현재가 데이터가 없습니다.' : undefined}
          >
            현재가 수평선 추가
          </button>
        </div>
        {drawings.length > 0 && (
          <div className="keyboard-drawing-list" role="group" aria-label="그리기 선택">
            {drawings.map((drawing, index) => (
              <button
                type="button"
                key={drawing.id}
                aria-pressed={drawing.id === selectedDrawingId}
                onClick={() => {
                  setSelectedDrawingId(drawing.id)
                  setDrawingFeedback(`${drawing.type === 'trend' ? '추세선' : '수평선'} ${index + 1}을 선택했습니다.`)
                }}
              >
                {drawing.type === 'trend' ? '추세선' : '수평선'} {index + 1}
              </button>
            ))}
          </div>
        )}
        <div className="keyboard-drawing-move" role="group" aria-label="선택한 그리기 이동과 삭제">
          <button type="button" onClick={() => moveSelectedPrice(1)} disabled={!selectedDrawing} title={!selectedDrawing ? '먼저 이동할 선을 선택해 주세요.' : undefined}>가격 위로</button>
          <button type="button" onClick={() => moveSelectedPrice(-1)} disabled={!selectedDrawing} title={!selectedDrawing ? '먼저 이동할 선을 선택해 주세요.' : undefined}>가격 아래로</button>
          <button type="button" onClick={() => moveSelectedTime(-1)} disabled={selectedDrawing?.type !== 'trend'} title={selectedDrawing?.type !== 'trend' ? '시간 이동은 추세선을 선택한 경우에 사용할 수 있습니다.' : undefined}>이전 봉으로</button>
          <button type="button" onClick={() => moveSelectedTime(1)} disabled={selectedDrawing?.type !== 'trend'} title={selectedDrawing?.type !== 'trend' ? '시간 이동은 추세선을 선택한 경우에 사용할 수 있습니다.' : undefined}>다음 봉으로</button>
          <button type="button" onClick={deleteSelectedDrawing} disabled={!selectedDrawing} title={!selectedDrawing ? '먼저 삭제할 선을 선택해 주세요.' : undefined}>선택 선 삭제</button>
        </div>
        <p className="keyboard-drawing-feedback" role="status" aria-live="polite">{drawingFeedback || '키보드 그리기 조작 결과가 여기에 안내됩니다.'}</p>
      </details>

      <div className="chart-indicators" role="group" aria-label="보기 지표 설정">
        <div role="group" aria-label="가격 오버레이 지표">
          <span>오버레이</span>
          <button type="button" className="has-tooltip" data-tooltip={"MA5 (5일 단기 이동평균선)\n단기적인 주가 트렌드를 가장 빠르게 반영하는 추세 지표입니다."} aria-pressed={showMa5} onClick={() => setShowMa5((value) => !value)}>MA5</button>
          <button type="button" className="has-tooltip" data-tooltip={"MA20 (20일 중기 이동평균선/심리선)\n주가의 단기 방향을 정하는 생명선으로 불리며, 강도 파악에 기준이 됩니다."} aria-pressed={showMa20} onClick={() => setShowMa20((value) => !value)}>MA20</button>
          <button type="button" className="has-tooltip" data-tooltip={"MA60 (60일 수급선/장기 추세선)\n중장기적인 시장 수급 상태 및 주가의 대세 방향성을 의미합니다."} aria-pressed={showMa60} onClick={() => setShowMa60((value) => !value)}>MA60</button>
          <button type="button" className="has-tooltip" data-tooltip={"볼린저 밴드 (20, 2)\n주가의 변동 범위를 표준편차 밴드로 둘러싸 보여주며, 상단/하단 이탈을 과열/침체로 해석합니다."} aria-pressed={showBollinger} onClick={() => setShowBollinger((value) => !value)}>볼린저(20,2)</button>
          <button type="button" className="has-tooltip" data-tooltip={"이벤트 마커\n차트 내 이동평균 교차(골든/데드), RSI 과매수/과매도 발생 지점을 마커로 나타냅니다."} aria-pressed={showEvents} onClick={() => setShowEvents((value) => !value)}>이벤트</button>
          <button type="button" className="has-tooltip" data-tooltip={"거래량\n일정 시간 내에 체결된 주식 수이며, 주가 등락의 힘과 매수/매도 신뢰도를 판단하는 기본 척도입니다."} aria-pressed={showVolume} onClick={() => setShowVolume((value) => !value)}>거래량</button>
          <button type="button" className="has-tooltip" data-tooltip={"거래량 MA20\n최근 20거래일 동안의 평균 거래량을 선으로 이어 보여줍니다."} aria-pressed={showVolumeMa20} onClick={() => setShowVolumeMa20((value) => !value)}>거래량 MA20</button>
        </div>
        <div role="group" aria-label="하단 보조지표 패널">
          <span>하단 패널</span>
          {(['rsi', 'macd', 'atr', 'none'] as const).map((panel) => {
            const tooltips = {
              rsi: 'RSI (상대강도지수)\n과매수(70 이상, 매도 검토) 및 과매도(30 이하, 매수 검토) 수준을 파악해 추세 전환 가능성을 알려주는 오실레이터입니다.',
              macd: 'MACD (이동평균 수렴확산)\n장단기 이평선의 골든크로스(매수) 및 데드크로스(매도) 교차 시점과 기세 강도를 알려주는 추세 추종 지표입니다.',
              atr: 'ATR (평균실제변동폭)\n최근 14거래일 동안 주가가 움직인 평균 범위(변동성)입니다. 높을수록 변동성이 큽니다.',
              none: '하단 보조지표 오실레이터 차트 패널을 닫습니다.'
            }
            return (
              <button
                key={panel}
                type="button"
                className="has-tooltip"
                data-tooltip={tooltips[panel]}
                aria-pressed={oscillator === panel}
                onClick={() => setOscillator(panel)}
              >
                {panel === 'none' ? '숨김' : panel.toUpperCase()}
              </button>
            )
          })}
          <button type="button" onClick={resetIndicators}>지표 초기화</button>
        </div>
      </div>

      <div className="chart-legend" aria-label="차트 범례">
        <div className="chart-legend-group overlay" role="group" aria-label="가격 및 오버레이 범례">
          <span><i className="legend-swatch candle-up" />상승</span>
          <span><i className="legend-swatch candle-down" />하락</span>
          {showMa5 && (
            <span><i className="legend-swatch ma5" />MA(5) {indicatorValue(latestMa5, 5, (value) => formatPrice(value, currency))}</span>
          )}
          {showMa20 && (
            <span><i className="legend-swatch ma20" />MA(20) {indicatorValue(latestMa20, 20, (value) => formatPrice(value, currency))}</span>
          )}
          {showMa60 && (
            <span><i className="legend-swatch ma60" />MA(60) {indicatorValue(latestMa60, 60, (value) => formatPrice(value, currency))}</span>
          )}
          {showBollinger && (
            <span>
              <i className="legend-swatch bollinger" />
              BB(20,2) {latestBollingerMiddle == null || latestBollingerUpper == null || latestBollingerLower == null || items.length < 20
                ? `데이터 부족 (${items.length}/20)`
                : `중앙 ${formatPrice(latestBollingerMiddle, currency)} · ${formatPrice(latestBollingerLower, currency)}–${formatPrice(latestBollingerUpper, currency)}`}
            </span>
          )}
          {showEvents && <span><i className="legend-swatch events" />교차 이벤트</span>}
          {showVolume && <span><i className="legend-swatch volume" />거래량 {latest ? formatVolume(latest.volume) : '데이터 부족 (0/1)'}</span>}
          {showVolumeMa20 && (
            <span><i className="legend-swatch volume-ma20" />거래량 MA(20) {indicatorValue(latestVolumeMa20, 20, formatVolume)}</span>
          )}
        </div>
        <div className="chart-legend-group panel" role="group" aria-label="하단 보조지표 범례">
          {oscillator === 'rsi' && (
            <span><i className="legend-swatch rsi" />RSI(14) {indicatorValue(latestRsi, 15, (value) => value.toFixed(2))}</span>
          )}
          {oscillator === 'macd' && (
            <span>
              <i className="legend-swatch macd" />
              MACD(12,26,9) {latestMacdHistogram == null || latestMacdSignal == null || items.length < 35
                ? `데이터 부족 (${items.length}/35)`
                : `${latestMacdHistogram.toFixed(2)} · 시그널 ${latestMacdSignal.toFixed(2)}`}
            </span>
          )}
          {oscillator === 'atr' && (
            <span><i className="legend-swatch atr" />ATR(14) {indicatorValue(latestAtr, 15, (value) => formatPrice(value, currency))}</span>
          )}
          {oscillator === 'none' && <span className="chart-legend-muted">하단 패널 숨김</span>}
        </div>
      </div>

      {showAdminDetails && (
        <div className="chart-data-meta" aria-label="차트 데이터 기준">
          <DataStatusBadge
            status={dataStatus}
            detail={loading && items.length > 0 ? '백그라운드 갱신 중' : undefined}
          />
          <span><b>시장</b> {market}</span>
          <span><b>통화</b> {currency}</span>
          <span><b>시간대</b> {timeZone}</span>
          <span><b>간격</b> {intervalLabel(interval)}</span>
          <span><b>기준시각</b> {formatBasisTime(basisTime, timeZone)}</span>
        </div>
      )}

      <div className="chart-canvas-wrap">
        <div
          ref={chartContainerRef}
          className="interactive-chart-canvas"
          role="img"
          aria-label={`${symbol} ${interval === '1m' ? '실시간 1분봉' : `${period} ${intervalLabel(interval)}`} 캔들 및 거래량(주) 차트`}
        />

        <svg
          className={`drawing-layer${tool !== 'pan' ? ' drawing-active' : ''}`}
          aria-hidden="true"
          onPointerDown={handleDrawingPointerDown}
        >
          {projectedDrawings.map((drawing) => {
            const selected = drawing.id === selectedDrawingId
            return (
              <g key={drawing.id}>
                <line
                  className={`drawing-shape ${drawing.type}${selected ? ' selected' : ''}`}
                  x1={drawing.x1}
                  y1={drawing.y1}
                  x2={drawing.x2}
                  y2={drawing.y2}
                  onPointerDown={(event) => drawing.type === 'horizontal'
                    ? startDrag(event, drawing.id, 0)
                    : selectDrawing(event, drawing.id)}
                />
                {selected && drawing.type === 'trend' && (
                  <>
                    <circle
                      className="drawing-handle"
                      cx={drawing.x1}
                      cy={drawing.y1}
                      r="6"
                      onPointerDown={(event) => startDrag(event, drawing.id, 0)}
                    />
                    <circle
                      className="drawing-handle"
                      cx={drawing.x2}
                      cy={drawing.y2}
                      r="6"
                      onPointerDown={(event) => startDrag(event, drawing.id, 1)}
                    />
                  </>
                )}
              </g>
            )
          })}
        </svg>

        {hoverData && (
          <div className="chart-tooltip" aria-live="polite">
            <strong>{hoverData.time}</strong>
            <span>시 {formatPrice(hoverData.open, currency)}</span>
            <span>고 {formatPrice(hoverData.high, currency)}</span>
            <span>저 {formatPrice(hoverData.low, currency)}</span>
            <span>종 {formatPrice(hoverData.close, currency)}</span>
            <span>거래량 {formatVolume(hoverData.volume)}</span>
            {showMa5 && hoverData.ma5 != null && <span>MA5 {formatPrice(hoverData.ma5, currency)}</span>}
            {showMa20 && hoverData.ma20 != null && <span>MA20 {formatPrice(hoverData.ma20, currency)}</span>}
            {showMa60 && hoverData.ma60 != null && <span>MA60 {formatPrice(hoverData.ma60, currency)}</span>}
            {showVolumeMa20 && hoverData.volumeMa20 != null && (
              <span>
                거래량 MA20 {formatVolume(hoverData.volumeMa20)}
                {hoverData.volumeMa20 > 0 ? ` · ${(hoverData.volume / hoverData.volumeMa20).toFixed(2)}배` : ''}
              </span>
            )}
            {showBollinger && hoverData.bollingerUpper != null && hoverData.bollingerLower != null && (
              <span>BB {formatPrice(hoverData.bollingerLower, currency)}–{formatPrice(hoverData.bollingerUpper, currency)}</span>
            )}
            {oscillator !== 'none' && hoverData.oscillatorValue != null && (
              <span>{oscillator.toUpperCase()} {hoverData.oscillatorValue.toFixed(2)}</span>
            )}
          </div>
        )}

        {items.length === 0 && (loading || error || items.length === 0) && (
          <div className={`chart-state${error ? ' error' : ''}`} role={error ? 'alert' : 'status'}>
            <span>{loading ? '선택한 기간의 가격 데이터를 불러오는 중입니다.' : error ?? '표시할 가격 데이터가 없습니다.'}</span>
            {error && onRetry && <button type="button" className="button-ghost" onClick={onRetry}>다시 시도</button>}
          </div>
        )}
        {items.length > 0 && (loading || error) && (
          <div className={`chart-background-refresh${error ? ' error' : ''}`} role={error ? 'alert' : 'status'} aria-live="polite">
            <span>{error ? '갱신에 실패해 이전 차트를 유지합니다.' : '기존 차트를 유지하며 새 데이터를 갱신 중입니다.'}</span>
            {error && onRetry && <button type="button" className="button-ghost" onClick={onRetry}>다시 시도</button>}
          </div>
        )}
      </div>

      <div className="chart-status-row">
        <span aria-live="polite">{statusText}</span>
        <a href="https://www.tradingview.com/" target="_blank" rel="noreferrer">Charts by TradingView</a>
      </div>

      {latest && (
        <p className="chart-a11y-summary">
          최신 {intervalLabel(interval)} {new Date(latest.time).toLocaleString('ko-KR')}: 시가 {formatPrice(latest.open, currency)},
          고가 {formatPrice(latest.high, currency)}, 저가 {formatPrice(latest.low, currency)},
          종가 {formatPrice(latest.close, currency)}, 거래량 {formatVolume(latest.volume)}.
        </p>
      )}
    </div>
  )
}
