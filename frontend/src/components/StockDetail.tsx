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
import { InteractiveStockChart } from './InteractiveStockChart'

type DetailState = {
  stock: StockSummary
  technical: TechnicalAnalysis
  source: 'API' | 'DEMO'
}

type Props = {
  symbol: string
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

export function StockDetail({ symbol }: Props) {
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

  const { stock, technical } = detail
  const changeClass = stock.changeRate >= 0 ? 'up' : 'down'

  return (
    <section className="stock-detail" id="stock-detail" aria-labelledby="stock-detail-title">
      <div className="detail-title-row">
        <div>
          <p className="eyebrow">STOCK DETAIL · {detail.source}</p>
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
              <span>현재가</span>
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
            items={prices?.items ?? []}
            period={period}
            loading={pricesLoading}
            error={null}
            source={priceSource}
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
            <span>RSI {technical.rsi.period}</span>
            <strong className={signalClass(technical.rsi.signal)}>{technical.rsi.value.toFixed(1)}</strong>
            <p>{technical.rsi.value >= 70 ? '과매수 구간' : technical.rsi.value <= 30 ? '과매도 구간' : '중립 구간'}</p>
          </article>

          <article className="card technical-card">
            <span>MACD</span>
            <strong className={signalClass(technical.macd.signal)}>{signalLabels[technical.macd.signal]}</strong>
            <p>히스토그램 {technical.macd.histogram.toLocaleString('ko-KR')}</p>
          </article>
        </div>
      </div>

      <p className="detail-disclaimer">{technical.disclaimer}</p>
    </section>
  )
}
