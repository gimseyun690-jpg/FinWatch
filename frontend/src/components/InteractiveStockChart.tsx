import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'
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
} from 'lightweight-charts'
import type { PricePeriod, PricePoint, TechnicalAnalysis } from '../types/stock'

type Props = {
  symbol: string
  market: string
  currency: string
  items: PricePoint[]
  period: PricePeriod
  loading: boolean
  error: string | null
  source: 'API' | 'DEMO'
  events?: TechnicalAnalysis['events']
  onPeriodChange: (period: PricePeriod) => void
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
  showEvents: true,
  oscillator: 'rsi',
}

function loadChartSettings(): ChartSettings {
  try {
    const stored = window.sessionStorage.getItem('finwatch-chart-settings')
    return stored ? { ...defaultChartSettings, ...JSON.parse(stored) as Partial<ChartSettings> } : defaultChartSettings
  } catch {
    return defaultChartSettings
  }
}

type DrawingAnchor = {
  time: string
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
  return new Intl.NumberFormat('ko-KR', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

function marketTimeZone(market: string) {
  return market === 'KRX' ? 'Asia/Seoul' : 'America/New_York'
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

function toChartTime(value: string, market: string) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: marketTimeZone(market),
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(value))
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

function normalizeTime(value: Time) {
  if (typeof value === 'string') return value
  if (typeof value === 'number') return new Date(value * 1000).toISOString().slice(0, 10)
  return `${value.year}-${String(value.month).padStart(2, '0')}-${String(value.day).padStart(2, '0')}`
}

function valueAtTime(
  series: Array<LineData<Time> | HistogramData<Time>>,
  time: string,
) {
  const point = series.find((item) => normalizeTime(item.time) === time)
  return point && 'value' in point && typeof point.value === 'number' ? point.value : undefined
}

function movingAverage(items: PricePoint[], times: string[], windowSize: number): LineData<Time>[] {
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

function bollingerBands(items: PricePoint[], times: string[], windowSize = 20) {
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

function wilderRsi(items: PricePoint[], times: string[], period = 14): LineData<Time>[] {
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

function macd(items: PricePoint[], times: string[]) {
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

function averageTrueRange(items: PricePoint[], times: string[], period = 14): LineData<Time>[] {
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
  loading,
  error,
  source,
  events,
  onPeriodChange,
}: Props) {
  const shellRef = useRef<HTMLDivElement>(null)
  const chartContainerRef = useRef<HTMLDivElement>(null)
  const fullscreenButtonRef = useRef<HTMLButtonElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
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

  const drawings = drawingsBySymbol[symbol] ?? emptyDrawings

  const chartData = useMemo(() => {
    const times = items.map((item) => toChartTime(item.time, market))
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
  }, [items, market])

  const updateCurrentDrawings = useCallback((updater: (current: Drawing[]) => Drawing[]) => {
    setDrawingsBySymbol((current) => ({
      ...current,
      [symbol]: updater(current[symbol] ?? emptyDrawings),
    }))
  }, [symbol])

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

      const x1 = chart.timeScale().timeToCoordinate(drawing.anchors[0].time)
      const y1 = candleSeries.priceToCoordinate(drawing.anchors[0].price)
      const x2 = chart.timeScale().timeToCoordinate(drawing.anchors[1].time)
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
        timeVisible: false,
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
    candleSeries.setData(chartData.candles)
    if (showEvents && events && events.length > 0) {
      const availableTimes = new Set(chartData.candles.map((item) => String(item.time)))
      const markers: SeriesMarker<Time>[] = events
        .map((event) => ({ event, time: toChartTime(event.time, market) }))
        .filter(({ time }) => availableTimes.has(time))
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
      series.setData(chartData.ma5)
    }
    if (showMa20) {
      const series = chart.addSeries(LineSeries, {
        color: '#25d6c8', lineWidth: 2, priceLineVisible: false, lastValueVisible: false, title: 'MA20',
      })
      series.setData(chartData.ma20)
    }
    if (showMa60) {
      const series = chart.addSeries(LineSeries, {
        color: '#ad86ff', lineWidth: 2, priceLineVisible: false, lastValueVisible: false, title: 'MA60',
      })
      series.setData(chartData.ma60)
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
      upper.setData(chartData.bollinger.upper)
      middle.setData(chartData.bollinger.middle)
      lower.setData(chartData.bollinger.lower)
    }

    let volumeSeries: ISeriesApi<'Histogram'> | null = null
    const hasVolumePane = showVolume || showVolumeMa20
    if (showVolume) {
      volumeSeries = chart.addSeries(HistogramSeries, {
        priceFormat: { type: 'volume' },
        priceLineVisible: false,
        lastValueVisible: false,
      }, 1)
      volumeSeries.setData(chartData.volumes)
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
      volumeAverageSeries.setData(chartData.volumeMa20)
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
      rsiSeries.setData(chartData.rsi)
      upper.setData(chartData.rsiUpper)
      lower.setData(chartData.rsiLower)
    } else if (oscillator === 'macd') {
      const histogram = chart.addSeries(HistogramSeries, {
        priceLineVisible: false, lastValueVisible: false, title: 'MACD',
      }, oscillatorPane)
      const signal = chart.addSeries(LineSeries, {
        color: '#f5bd50', lineWidth: 2, priceLineVisible: false, lastValueVisible: false, title: 'Signal',
      }, oscillatorPane)
      histogram.setData(chartData.macd.histogram)
      signal.setData(chartData.macd.signal)
    } else if (oscillator === 'atr') {
      const atrSeries = chart.addSeries(LineSeries, {
        color: '#ad86ff', lineWidth: 2, priceLineVisible: false, lastValueVisible: true, title: 'ATR14',
      }, oscillatorPane)
      atrSeries.setData(chartData.atr)
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
      setHoverData({
        time: normalizeTime(param.time),
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
        volume: volume && 'value' in volume && typeof volume.value === 'number' ? volume.value : 0,
        ma5: valueAtTime(chartData.ma5, normalizeTime(param.time)),
        ma20: valueAtTime(chartData.ma20, normalizeTime(param.time)),
        ma60: valueAtTime(chartData.ma60, normalizeTime(param.time)),
        volumeMa20: valueAtTime(chartData.volumeMa20, normalizeTime(param.time)),
        bollingerUpper: valueAtTime(chartData.bollinger.upper, normalizeTime(param.time)),
        bollingerLower: valueAtTime(chartData.bollinger.lower, normalizeTime(param.time)),
        oscillatorValue: oscillator === 'rsi'
          ? valueAtTime(chartData.rsi, normalizeTime(param.time))
          : oscillator === 'atr'
            ? valueAtTime(chartData.atr, normalizeTime(param.time))
            : oscillator === 'macd'
              ? valueAtTime(chartData.macd.histogram, normalizeTime(param.time))
              : undefined,
      })
      queueProjection()
    }

    chart.subscribeCrosshairMove(crosshairHandler)
    chart.timeScale().subscribeVisibleLogicalRangeChange(queueProjection)
    chart.timeScale().fitContent()
    chartRef.current = chart
    candleSeriesRef.current = candleSeries

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
      setHoverData(null)
    }
  }, [
    chartData,
    currency,
    events,
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
      updateCurrentDrawings((current) => [...current, { id: drawingId(), type: 'horizontal', anchor }])
      setTool('pan')
      return
    }

    if (pendingAnchor == null) {
      setPendingAnchor(anchor)
      return
    }

    updateCurrentDrawings((current) => [
      ...current,
      { id: drawingId(), type: 'trend', anchors: [pendingAnchor, anchor] },
    ])
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
  }

  function activateTool(nextTool: ChartTool) {
    setTool(nextTool)
    setPendingAnchor(null)
    if (nextTool !== 'pan') setSelectedDrawingId(null)
  }

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
      <div className="chart-toolbar" aria-label="상세 차트 도구 모음">
        <div className="chart-periods" aria-label="조회 기간">
          {periods.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={period === option.value}
              onClick={() => onPeriodChange(option.value)}
              disabled={loading}
            >
              {option.label}
            </button>
          ))}
        </div>
        <div className="chart-tools">
          <button type="button" onClick={resetVisibleRange}>범위 초기화</button>
          <button type="button" aria-pressed={tool === 'pan'} onClick={() => activateTool('pan')}>이동</button>
          <button type="button" aria-pressed={tool === 'trend'} onClick={() => activateTool('trend')}>추세선</button>
          <button type="button" aria-pressed={tool === 'horizontal'} onClick={() => activateTool('horizontal')}>수평선</button>
          <button type="button" onClick={deleteSelectedDrawing} disabled={!selectedDrawingId}>선 삭제</button>
          <button type="button" onClick={clearDrawings} disabled={drawings.length === 0}>전체 삭제</button>
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

      <div className="chart-indicators" aria-label="보조 지표 선택">
        <div>
          <span>오버레이</span>
          <button type="button" aria-pressed={showMa5} onClick={() => setShowMa5((value) => !value)}>MA5</button>
          <button type="button" aria-pressed={showMa20} onClick={() => setShowMa20((value) => !value)}>MA20</button>
          <button type="button" aria-pressed={showMa60} onClick={() => setShowMa60((value) => !value)}>MA60</button>
          <button type="button" aria-pressed={showBollinger} onClick={() => setShowBollinger((value) => !value)}>볼린저(20,2)</button>
          <button type="button" aria-pressed={showEvents} onClick={() => setShowEvents((value) => !value)}>이벤트</button>
          <button type="button" aria-pressed={showVolume} onClick={() => setShowVolume((value) => !value)}>거래량</button>
          <button type="button" aria-pressed={showVolumeMa20} onClick={() => setShowVolumeMa20((value) => !value)}>거래량 MA20</button>
        </div>
        <div>
          <span>하단 패널</span>
          {(['rsi', 'macd', 'atr', 'none'] as const).map((panel) => (
            <button
              key={panel}
              type="button"
              aria-pressed={oscillator === panel}
              onClick={() => setOscillator(panel)}
            >
              {panel === 'none' ? '숨김' : panel.toUpperCase()}
            </button>
          ))}
          <button type="button" onClick={resetIndicators}>지표 초기화</button>
        </div>
      </div>

      <div className="chart-legend" aria-label="차트 범례">
        <span><i className="candle-up" />상승</span>
        <span><i className="candle-down" />하락</span>
        {showMa5 && <span><i className="ma5" />MA5</span>}
        {showMa20 && <span><i className="ma20" />MA20</span>}
        {showMa60 && <span><i className="ma60" />MA60</span>}
        {showBollinger && <span><i className="bollinger" />BB(20,2)</span>}
        {showEvents && <span>교차 이벤트</span>}
        {showVolume && <span>거래량</span>}
        {showVolumeMa20 && <span>거래량 MA20</span>}
        {oscillator !== 'none' && <span>패널 {oscillator.toUpperCase()}</span>}
        <strong>{source}</strong>
      </div>

      <div className="chart-canvas-wrap">
        <div
          ref={chartContainerRef}
          className="interactive-chart-canvas"
          role="img"
          aria-label={`${symbol} ${period} 일봉 캔들 및 거래량 차트`}
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

        {(loading || error || items.length === 0) && (
          <div className={`chart-state${error ? ' error' : ''}`} role="status">
            {loading ? '선택한 기간의 가격 데이터를 불러오는 중입니다.' : error ?? '표시할 가격 데이터가 없습니다.'}
          </div>
        )}
      </div>

      <div className="chart-status-row">
        <span aria-live="polite">{statusText}</span>
        <a href="https://www.tradingview.com/" target="_blank" rel="noreferrer">Charts by TradingView</a>
      </div>

      {latest && (
        <p className="chart-a11y-summary">
          최신 일봉 {new Date(latest.time).toLocaleDateString('ko-KR')}: 시가 {formatPrice(latest.open, currency)},
          고가 {formatPrice(latest.high, currency)}, 저가 {formatPrice(latest.low, currency)},
          종가 {formatPrice(latest.close, currency)}, 거래량 {formatVolume(latest.volume)}.
        </p>
      )}
    </div>
  )
}
