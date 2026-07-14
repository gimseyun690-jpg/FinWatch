import { useEffect, useState } from 'react'
import { getStock, getStockPrices, getTechnicalAnalysis } from '../api/stocks'
import { demoPriceHistory, demoStock, demoTechnical } from '../mocks/stockDetail'
import type {
  PriceHistory,
  PricePeriod,
  Signal,
  StockSummary,
  TechnicalAnalysis,
} from '../types/stock'
import type { LiveQuote } from '../types/realtime'
import { InteractiveStockChart } from './InteractiveStockChart'

type DetailState = {
  stock: StockSummary
  technical: TechnicalAnalysis
  source: 'API' | 'DEMO'
}

type Props = {
  symbol: string
  liveQuote?: LiveQuote
}

const signalLabels: Record<Signal, string> = {
  BUY: '매수',
  NEUTRAL: '중립',
  SELL: '매도',
}

const periodDays: Partial<Record<PricePeriod, number>> = {
  '1M': 31,
  '3M': 93,
  '6M': 186,
  '1Y': 366,
}

const eventLabels: Record<NonNullable<TechnicalAnalysis['events']>[number]['type'], string> = {
  MA_GOLDEN_CROSS: 'MA 골든크로스',
  MA_DEAD_CROSS: 'MA 데드크로스',
  MACD_BULLISH_CROSS: 'MACD 상향 돌파',
  MACD_BEARISH_CROSS: 'MACD 하향 돌파',
  RSI_OVERSOLD_ENTER: 'RSI 과매도 진입',
  RSI_OVERSOLD_EXIT: 'RSI 과매도 이탈',
  RSI_OVERBOUGHT_ENTER: 'RSI 과매수 진입',
  RSI_OVERBOUGHT_EXIT: 'RSI 과매수 이탈',
}

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency,
    maximumFractionDigits: currency === 'KRW' ? 0 : 2,
  }).format(value)
}

function signalClass(signal: Signal) {
  return signal === 'BUY' ? 'buy' : signal === 'SELL' ? 'sell' : 'neutral'
}

function demoPricesFor(period: PricePeriod): PriceHistory {
  const days = periodDays[period]
  if (days == null) return { ...demoPriceHistory, period }
  const latest = new Date(demoPriceHistory.items.at(-1)?.time ?? Date.now()).getTime()
  const from = latest - days * 86_400_000
  return {
    ...demoPriceHistory,
    period,
    items: demoPriceHistory.items.filter((item) => new Date(item.time).getTime() >= from),
  }
}

function mergeLiveCandle(prices: PriceHistory | null, liveQuote?: LiveQuote): PriceHistory | null {
  if (prices == null || liveQuote == null || prices.items.length === 0) return prices
  const items = [...prices.items]
  const latest = items.at(-1)!
  items[items.length - 1] = {
    ...latest,
    high: Math.max(latest.high, liveQuote.price),
    low: Math.min(latest.low, liveQuote.price),
    close: liveQuote.price,
    volume: Math.max(latest.volume, liveQuote.volume),
  }
  return { ...prices, items }
}

export function StockDetail({ symbol, liveQuote }: Props) {
  const [detail, setDetail] = useState<DetailState | null>(null)
  const [prices, setPrices] = useState<PriceHistory | null>(null)
  const [period, setPeriod] = useState<PricePeriod>('3M')
  const [detailLoading, setDetailLoading] = useState(true)
  const [pricesLoading, setPricesLoading] = useState(true)
  const [priceSource, setPriceSource] = useState<'API' | 'DEMO'>('API')

  useEffect(() => {
    const controller = new AbortController()
    setDetail(null)
    setDetailLoading(true)

    Promise.all([
      getStock(symbol, controller.signal),
      getTechnicalAnalysis(symbol, controller.signal),
    ])
      .then(([stock, technical]) => {
        if (!controller.signal.aborted) setDetail({ stock, technical, source: 'API' })
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (!controller.signal.aborted) {
          setDetail({ stock: demoStock, technical: demoTechnical, source: 'DEMO' })
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false)
      })

    return () => controller.abort()
  }, [symbol])

  useEffect(() => {
    const controller = new AbortController()
    setPrices(null)
    setPricesLoading(true)

    getStockPrices(symbol, period, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) {
          setPrices(response)
          setPriceSource('API')
        }
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (!controller.signal.aborted) {
          setPrices(demoPricesFor(period))
          setPriceSource('DEMO')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setPricesLoading(false)
      })

    return () => controller.abort()
  }, [period, symbol])

  if (detailLoading || !detail) {
    return <section className="card detail-loading" aria-live="polite">종목 상세 정보를 불러오는 중입니다.</section>
  }

  const { technical } = detail
  const effectiveLiveQuote = liveQuote != null && new Date(liveQuote.asOf) >= new Date(detail.stock.asOf)
    ? liveQuote
    : undefined
  const stock = effectiveLiveQuote == null ? detail.stock : {
    ...detail.stock,
    price: effectiveLiveQuote.price,
    change: effectiveLiveQuote.change,
    changeRate: effectiveLiveQuote.changeRate,
    volume: effectiveLiveQuote.volume,
    asOf: effectiveLiveQuote.asOf,
    source: effectiveLiveQuote.source,
  }
  const chartPrices = mergeLiveCandle(prices, effectiveLiveQuote)
  const changeClass = stock.changeRate >= 0 ? 'up' : 'down'
  const streaming = effectiveLiveQuote?.sessionStatus === 'LIVE'

  return (
    <section className="stock-detail" id="stock-detail" aria-labelledby="stock-detail-title">
      <div className="detail-title-row">
        <div>
          <p className="eyebrow">STOCK DETAIL · {effectiveLiveQuote?.source ?? detail.source}</p>
          <h2 id="stock-detail-title">{stock.name} 기술적 분석</h2>
          <p>{stock.symbol} · {stock.market} · {new Date(stock.asOf).toLocaleString('ko-KR')}</p>
        </div>
        <span className={`signal-badge ${signalClass(technical.summarySignal)}`}>
          종합 {signalLabels[technical.summarySignal]}
        </span>
      </div>

      <div className="detail-layout">
        <article className="card price-chart-card">
          <div className="quote-row">
            <div>
              <span>현재가 {streaming && <em className="live-tick-badge"><i />TICK</em>}</span>
              <strong>{formatMoney(stock.price, stock.currency)}</strong>
            </div>
            <div className={changeClass}>
              <strong>{stock.changeRate >= 0 ? '+' : ''}{stock.changeRate.toFixed(2)}%</strong>
              <span>{formatMoney(Math.abs(stock.change), stock.currency)}</span>
            </div>
          </div>

          <InteractiveStockChart
            symbol={stock.symbol}
            market={stock.market}
            currency={stock.currency}
            items={chartPrices?.items ?? []}
            period={period}
            loading={pricesLoading}
            error={null}
            source={priceSource}
            events={technical.events}
            onPeriodChange={setPeriod}
          />
        </article>

        <div className="technical-grid">
          <article className="card technical-card summary-card">
            <span>기술적 분석 요약</span>
            <strong className={signalClass(technical.summarySignal)}>{signalLabels[technical.summarySignal]}</strong>
            <p>이동평균과 RSI, MACD를 종합한 참고 신호입니다.</p>
          </article>

          <article className="card technical-card">
            <span>이동평균선</span>
            <strong className={signalClass(technical.movingAverages.signal)}>
              {signalLabels[technical.movingAverages.signal]}
            </strong>
            <p>MA5 {formatMoney(technical.movingAverages.ma5, stock.currency)}</p>
            <p>MA20 {formatMoney(technical.movingAverages.ma20, stock.currency)}</p>
          </article>

          <article className="card technical-card">
            <span>RSI {technical.rsi.period} {technical.rsi.method ?? ''}</span>
            <strong className={signalClass(technical.rsi.signal)}>{technical.rsi.value.toFixed(1)}</strong>
            <p>{technical.rsi.value >= 70 ? '과매수 구간' : technical.rsi.value <= 30 ? '과매도 구간' : '중립 구간'}</p>
          </article>

          <article className="card technical-card">
            <span>MACD</span>
            <strong className={signalClass(technical.macd.signal)}>{signalLabels[technical.macd.signal]}</strong>
            <p>히스토그램 {technical.macd.histogram.toLocaleString('ko-KR')}</p>
          </article>

          {technical.bollingerBands && (
            <article className="card technical-card">
              <span>볼린저 밴드 ({technical.bollingerBands.period}, {technical.bollingerBands.deviationMultiplier})</span>
              <strong>{formatMoney(technical.bollingerBands.middle, stock.currency)}</strong>
              <p>상단 {formatMoney(technical.bollingerBands.upper, stock.currency)}</p>
              <p>하단 {formatMoney(technical.bollingerBands.lower, stock.currency)}</p>
            </article>
          )}

          {technical.atr && (
            <article className="card technical-card">
              <span>ATR {technical.atr.period}</span>
              <strong>{formatMoney(technical.atr.value, stock.currency)}</strong>
              <p>최근 변동성의 Wilder 평활 평균입니다.</p>
              {technical.atr.percent != null && <p>현재가 대비 {technical.atr.percent.toFixed(2)}%</p>}
            </article>
          )}

          {technical.volumeMa20 != null && (
            <article className="card technical-card">
              <span>거래량 MA20</span>
              <strong>{new Intl.NumberFormat('ko-KR', { notation: 'compact' }).format(technical.volumeMa20)}</strong>
              <p>20거래일 평균 거래량</p>
            </article>
          )}

          {technical.events && technical.events.length > 0 && (
            <article className="card technical-card technical-events">
              <span>최근 교차 이벤트</span>
              <ul>
                {technical.events.slice(-4).reverse().map((event) => (
                  <li key={`${event.time}-${event.type}`}>
                    <strong className={signalClass(event.signal)}>{eventLabels[event.type]}</strong>
                    <time dateTime={event.time}>{new Date(event.time).toLocaleDateString('ko-KR')}</time>
                  </li>
                ))}
              </ul>
            </article>
          )}
        </div>
      </div>

      <p className="detail-disclaimer">{technical.disclaimer}</p>
    </section>
  )
}
