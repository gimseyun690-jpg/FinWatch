import { useEffect, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router'
import type { AppRouteContext } from '../app/context'
import {
  getStock,
  getStockDataLoadJob,
  getStockIntraday,
  getStockPrices,
  getTechnicalAnalysis,
  startStockDataLoad,
} from '../api/stocks'
import type {
  CanonicalStockDetail,
  PriceHistory,
  PriceInterval,
  PricePeriod,
  Signal,
  StockRef,
  TechnicalAnalysis,
} from '../types/stock'
import type { IntradayCandle, LiveQuote } from '../types/realtime'
import { DataStatusBadge, type DataStatus } from './DataStatusBadge'
import { InteractiveStockChart } from './InteractiveStockChart'

type DetailState = {
  stock: CanonicalStockDetail
  technical: TechnicalAnalysis | null
  source: 'API' | 'DEMO'
}

type ReadyStock = CanonicalStockDetail & {
  price: number
  change: number
  changeRate: number
  volume: number
  asOf: string
}

type Props = {
  stockRef: StockRef
  liveQuote?: LiveQuote
  liveCandles?: IntradayCandle[]
  headingLabel?: string
}

const signalLabels: Record<Signal, string> = {
  BUY: '매수',
  NEUTRAL: '중립',
  SELL: '매도',
}

const DATA_LOAD_POLL_ATTEMPTS = 80

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

function mergeIntradayCandles(prices: PriceHistory | null, liveCandles: IntradayCandle[] = []): PriceHistory | null {
  if (prices == null) return null
  const byTime = new Map(prices.items.map((item) => [item.time, item]))
  liveCandles.forEach((candle) => byTime.set(candle.time, {
    time: candle.time,
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
    volume: candle.volume,
  }))
  return {
    ...prices,
    interval: '1m',
    period: 'SESSION',
    source: 'LIVE',
    items: [...byTime.values()]
      .sort((left, right) => left.time.localeCompare(right.time))
      .slice(-390),
  }
}

export function StockDetail({ stockRef, liveQuote, liveCandles, headingLabel }: Props) {
  const context = useOutletContext<AppRouteContext | null>()
  const showAdminDetails = context?.showAdminDetails ?? true
  const { market: stockMarket, symbol: stockSymbol, stockId } = stockRef
  const requestStock = useMemo<StockRef>(() => ({
    market: stockMarket,
    symbol: stockSymbol,
    stockId,
  }), [stockId, stockMarket, stockSymbol])
  const [detail, setDetail] = useState<DetailState | null>(null)
  const [detailError, setDetailError] = useState('')
  const [prices, setPrices] = useState<PriceHistory | null>(null)
  const [period, setPeriod] = useState<PricePeriod>('3M')
  const [interval, setInterval] = useState<PriceInterval>('1D')
  const [detailLoading, setDetailLoading] = useState(true)
  const [pricesLoading, setPricesLoading] = useState(true)
  const [priceSource, setPriceSource] = useState('API')
  const [pricesError, setPricesError] = useState<string | null>(null)
  const [dataLoadMessage, setDataLoadMessage] = useState('')
  const [technicalError, setTechnicalError] = useState('')
  const [detailAttempt, setDetailAttempt] = useState(0)
  const [priceAttempt, setPriceAttempt] = useState(0)
  const [priceDataKey, setPriceDataKey] = useState('')
  const priceRequestKey = `${stockMarket}:${stockSymbol}:${interval}:${interval === '1m' ? 'SESSION' : period}`

  useEffect(() => {
    const controller = new AbortController()
    setDetail(null)
    setDetailLoading(true)
    setDetailError('')
    setDataLoadMessage('')
    setTechnicalError('')

    getStock(requestStock, controller.signal)
      .then(async (initialStock) => {
        if (controller.signal.aborted) return
        let stock = initialStock
        const needsVisiblePreparation = !stock.historyAvailable || stock.historySource === 'DEMO'
        if (needsVisiblePreparation) {
          setDataLoadMessage('실제 현재가와 가격 이력을 공급자에서 준비하고 있습니다.')
        }
        try {
          const resources: Array<'QUOTE' | 'DAILY_PRICES'> = stock.price == null
            ? ['QUOTE', 'DAILY_PRICES']
            : ['DAILY_PRICES']
          let job = await startStockDataLoad(requestStock, resources, controller.signal)
          for (let attempt = 0; job.status === 'SYNCING' && attempt < DATA_LOAD_POLL_ATTEMPTS; attempt += 1) {
            await new Promise((resolve) => window.setTimeout(resolve, 500))
            if (controller.signal.aborted) return
            job = await getStockDataLoadJob(requestStock, job.jobId, controller.signal)
          }
          stock = await getStock(requestStock, controller.signal)
          const failed = job.resources.filter((resource) => resource.status === 'FAILED')
          if (needsVisiblePreparation) {
            setDataLoadMessage(failed.length > 0
              ? '일부 공급자 데이터를 준비하지 못했습니다. 저장된 실제 데이터와 실시간 시세를 우선 표시합니다.'
              : `실제 가격 이력 ${stock.historyPoints.toLocaleString('ko-KR')}개를 준비했습니다.`)
          }
        } catch {
          if (needsVisiblePreparation) {
            setDataLoadMessage('가격 공급자 응답을 기다리는 중입니다. 기존 실제 데이터가 있으면 계속 표시합니다.')
          }
        }
        let technical: TechnicalAnalysis | null = null
        if (stock.historyAvailable) {
          try {
            technical = await getTechnicalAnalysis(requestStock, controller.signal)
          } catch {
            technical = null
            setTechnicalError('기술지표 계산 요청에 실패했습니다. 가격 이력 부족과 구분해 표시합니다.')
          }
        }
        if (!controller.signal.aborted) setDetail({ stock, technical, source: 'API' })
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (!controller.signal.aborted) {
          setDetailError('종목 상세 정보를 불러오지 못했습니다. 저장된 값이나 DEMO 값으로 조용히 대체하지 않았습니다.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false)
      })

    return () => controller.abort()
  }, [detailAttempt, requestStock])

  useEffect(() => {
    const controller = new AbortController()
    setPricesLoading(true)
    setPricesError(null)

    if (detail?.stock.dataAvailability !== 'READY') {
      setPricesLoading(false)
      return () => controller.abort()
    }

    const request = interval === '1m'
      ? getStockIntraday(requestStock, controller.signal)
      : getStockPrices(requestStock, period, interval, controller.signal)
    request
      .then((response) => {
        if (!controller.signal.aborted) {
          setPrices(response)
          setPriceSource(response.source)
          setPriceDataKey(priceRequestKey)
        }
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (!controller.signal.aborted) {
          setPricesError(interval === '1m'
            ? '실시간 1분 봉 갱신에 실패했습니다. LIVE 빈 데이터로 대체하지 않습니다.'
            : '가격 이력 갱신에 실패했습니다. 같은 조건의 마지막 성공 차트가 있으면 유지합니다.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setPricesLoading(false)
      })

    return () => controller.abort()
  }, [detail?.stock.dataAvailability, interval, period, priceAttempt, priceRequestKey, requestStock])

  if (detailLoading) {
    return (
      <section className="card detail-loading detail-skeleton" aria-live="polite" aria-busy="true">
        <span className="skeleton-line" aria-hidden="true" />
        <strong>종목 상세 정보를 불러오는 중입니다.</strong>
        <span>종목 정보와 실제 가격 이력을 확인하고 있습니다.</span>
      </section>
    )
  }

  if (!detail) {
    return (
      <section className="card detail-loading detail-error" id="stock-detail" role="alert">
        <strong>{detailError || '종목 상세 정보가 없습니다.'}</strong>
        <span>네트워크 연결과 시장·심볼을 확인해 주세요.</span>
        <button type="button" onClick={() => setDetailAttempt((value) => value + 1)}>다시 시도</button>
      </section>
    )
  }

  const catalogStock = detail.stock
  if (
    !catalogStock.historyAvailable
    || catalogStock.price == null
    || catalogStock.change == null
    || catalogStock.changeRate == null
    || catalogStock.volume == null
    || catalogStock.asOf == null
  ) {
    return (
      <section className="stock-detail metadata-only-detail" id="stock-detail" aria-labelledby="stock-detail-title">
        <div className="detail-title-row">
          <div>
            <p className="eyebrow">STOCK CATALOG · {catalogStock.catalogSource}</p>
            <h2 id="stock-detail-title">{catalogStock.name}{headingLabel ? ` ${headingLabel}` : ''}</h2>
            <p>{catalogStock.symbol} · {catalogStock.market} · {catalogStock.currency} · {catalogStock.instrumentType}</p>
          </div>
          <DataStatusBadge status={availabilityStatus(catalogStock.dataAvailability)} detail={catalogStock.catalogSource} />
        </div>
        <article className="card metadata-only-card">
          <strong>{catalogStock.price == null ? '종목 메타데이터를 찾았습니다.' : `${formatMoney(catalogStock.price, catalogStock.currency)} · 최근 시세`}</strong>
          <p>{dataLoadMessage || '이 종목은 아직 시세·일봉이 준비되지 않았습니다. 임의의 데모 가격으로 대체하지 않습니다.'}</p>
          {catalogStock.changeRate != null && (
            <p className={catalogStock.changeRate >= 0 ? 'up' : 'down'}>
              전일 대비 {catalogStock.changeRate >= 0 ? '+' : ''}{catalogStock.changeRate.toFixed(2)}%
            </p>
          )}
          <small>{catalogStock.englishName ?? catalogStock.exchange} · {catalogStock.tradable ? '거래 가능' : '거래 지원 확인 필요'}</small>
        </article>
      </section>
    )
  }

  const technical = detail.technical
  const readyStock = catalogStock as ReadyStock
  const quoteMatchesInstrument = liveQuote?.market.toUpperCase() === readyStock.market.toUpperCase()
    && liveQuote.symbol.toUpperCase() === readyStock.symbol.toUpperCase()
  const matchingLiveCandles = (liveCandles ?? []).filter((candle) => (
    candle.market.toUpperCase() === readyStock.market.toUpperCase()
    && candle.symbol.toUpperCase() === readyStock.symbol.toUpperCase()
  ))
  const effectiveLiveQuote = quoteMatchesInstrument && liveQuote != null && new Date(liveQuote.asOf) >= new Date(readyStock.asOf)
    ? liveQuote
    : undefined
  const stock: ReadyStock = effectiveLiveQuote == null ? readyStock : {
    ...readyStock,
    price: effectiveLiveQuote.price,
    change: effectiveLiveQuote.change,
    changeRate: effectiveLiveQuote.changeRate,
    volume: effectiveLiveQuote.volume,
    asOf: effectiveLiveQuote.asOf,
    source: effectiveLiveQuote.source,
  }
  const chartPrices = interval === '1m'
    ? mergeIntradayCandles(priceDataKey === priceRequestKey ? prices : null, matchingLiveCandles)
    : mergeLiveCandle(priceDataKey === priceRequestKey ? prices : null, effectiveLiveQuote)
  const hasIntradayCandles = (chartPrices?.items.length ?? 0) > 0
  const changeClass = stock.changeRate >= 0 ? 'up' : 'down'
  const streaming = effectiveLiveQuote?.sessionStatus === 'LIVE'
  const dataStatus: DataStatus = detail.source === 'DEMO' || stock.status === 'DEMO_ONLY'
    ? 'DEMO'
    : streaming
      ? 'LIVE'
      : effectiveLiveQuote
        ? 'DELAYED'
        : 'REFERENCE'
  const dataSource = effectiveLiveQuote?.source ?? stock.source ?? catalogStock.historySource ?? detail.source
  const chartFreshness: DataStatus = interval === '1m' && hasIntradayCandles
    ? 'LIVE'
    : priceSource.toUpperCase() === 'DEMO'
      ? 'DEMO'
      : 'REFERENCE'

  return (
    <section className="stock-detail" id="stock-detail" aria-labelledby="stock-detail-title">
      <div className="detail-title-row">
        <div>
          {showAdminDetails && <p className="eyebrow">STOCK DETAIL · {dataSource}</p>}
          <h2 id="stock-detail-title">{stock.name} {headingLabel ?? (technical == null ? '실제 시세 차트' : '기술적 분석')}</h2>
          <p className="stock-trust-meta">
            <span>{stock.symbol}</span>
            <span>{stock.market}</span>
            <span>{stock.currency}</span>
            {showAdminDetails && <span>{dataSource}</span>}
            <time dateTime={stock.asOf}>기준 {new Date(stock.asOf).toLocaleString('ko-KR')}</time>
            {showAdminDetails && <DataStatusBadge status={dataStatus} />}
          </p>
        </div>
        {technical == null
          ? <span className="availability-badge">실제 이력 {catalogStock.historyPoints.toLocaleString('ko-KR')}개</span>
          : (
            <span className={`signal-badge ${signalClass(technical.summarySignal)}`}>
              종합 {signalLabels[technical.summarySignal]}
            </span>
          )}
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
            interval={interval}
            loading={pricesLoading}
            error={interval === '1m' && hasIntradayCandles ? null : pricesError}
            source={interval === '1m' && hasIntradayCandles ? 'LIVE' : priceSource}
            freshness={chartFreshness}
            asOf={stock.asOf}
            events={interval === '1D' ? technical?.events : undefined}
            onPeriodChange={setPeriod}
            onIntervalChange={setInterval}
            onRetry={() => setPriceAttempt((value) => value + 1)}
          />
        </article>

        {technical == null ? (
          <div className="technical-grid">
            <article className="card technical-card summary-card">
              <span>실제 가격 이력</span>
              <strong>{catalogStock.historySource ?? '공급자 확인 중'}</strong>
              <p>{technicalError || '기술지표는 일봉 60개 이상이 준비되면 자동 계산됩니다.'}</p>
              {technicalError && <button type="button" className="button-ghost" onClick={() => setDetailAttempt((value) => value + 1)}>기술지표 다시 계산</button>}
            </article>
          </div>
        ) : <div className="technical-grid">
          <article className="card technical-card summary-card">
            <span className="has-tooltip" data-tooltip={"이동평균(MA), RSI, MACD 등의 핵심 지표들을 종합 연산하여 판단한 주식의 전반적인 매매 강도 요약 신호입니다."}>기술적 분석 요약</span>
            <strong className={signalClass(technical.summarySignal)}>{signalLabels[technical.summarySignal]}</strong>
            <p>이동평균과 RSI, MACD를 종합한 참고 신호입니다.</p>
          </article>

          <article className="card technical-card">
            <span className="has-tooltip" data-tooltip={"이동평균선 (MA)\n일정 기간 동안의 주가 평균값의 추세선입니다.\n- 골든크로스(단기가 장기 돌파): 매수 신호\n- 데드크로스(단기가 장기 이탈): 매도 신호"}>이동평균선</span>
            <strong className={signalClass(technical.movingAverages.signal)}>
              {signalLabels[technical.movingAverages.signal]}
            </strong>
            <p>MA5 {formatMoney(technical.movingAverages.ma5, stock.currency)}</p>
            <p>MA20 {formatMoney(technical.movingAverages.ma20, stock.currency)}</p>
          </article>

          <article className="card technical-card">
            <span className="has-tooltip" data-tooltip={`RSI (상대강도지수)\n주가의 상승 압력과 하락 압력 간의 상대적인 강도를 보여주는 오실레이터입니다.\n- 70 이상: 과매수 구간 (매도 검토 신호)\n- 30 이하: 과매도 구간 (매수 검토 신호)`}>RSI {technical.rsi.period} {technical.rsi.method ?? ''}</span>
            <strong className={signalClass(technical.rsi.signal)}>{technical.rsi.value.toFixed(1)}</strong>
            <p>{technical.rsi.value >= 70 ? '과매수 구간' : technical.rsi.value <= 30 ? '과매도 구간' : '중립 구간'}</p>
          </article>

          <article className="card technical-card">
            <span className="has-tooltip" data-tooltip={"MACD (이동평균 수렴확산)\n단기 이평선과 장기 이평선의 수렴·확산을 기반으로 한 추세 지표입니다.\n- MACD 선이 시그널 선 위로 교차: 매수 신호\n- MACD 선이 시그널 선 아래로 교차: 매도 신호"}>MACD</span>
            <strong className={signalClass(technical.macd.signal)}>{signalLabels[technical.macd.signal]}</strong>
            <p>히스토그램 {technical.macd.histogram.toLocaleString('ko-KR')}</p>
          </article>

          {technical.bollingerBands && (
            <article className="card technical-card">
              <span className="has-tooltip" data-tooltip={"볼린저 밴드\n주가의 변동 범위를 표준편차 밴드로 보여줍니다.\n- 상단 밴드 터치/돌파: 과열 상태 (매도 신호 검토)\n- 하단 밴드 이탈/터치: 침체 상태 (매수 신호 검토)"}>볼린저 밴드 ({technical.bollingerBands.period}, {technical.bollingerBands.deviationMultiplier})</span>
              <strong>{formatMoney(technical.bollingerBands.middle, stock.currency)}</strong>
              <p>상단 {formatMoney(technical.bollingerBands.upper, stock.currency)}</p>
              <p>하단 {formatMoney(technical.bollingerBands.lower, stock.currency)}</p>
            </article>
          )}

          {technical.atr && (
            <article className="card technical-card">
              <span className="has-tooltip" data-tooltip={"ATR (평균실제변동폭)\n최근 14거래일 동안의 평균적인 주가 변동폭(변동성)입니다.\n- 높을 때: 변동성이 크며 추세 반전 또는 급등락 신호\n- 낮을 때: 횡보 및 안정적인 흐름"}>ATR {technical.atr.period}</span>
              <strong>{formatMoney(technical.atr.value, stock.currency)}</strong>
              <p>최근 변동성의 Wilder 평활 평균입니다.</p>
              {technical.atr.percent != null && <p>현재가 대비 {technical.atr.percent.toFixed(2)}%</p>}
            </article>
          )}

          {technical.volumeMa20 != null && (
            <article className="card technical-card">
              <span className="has-tooltip" data-tooltip={"거래량 MA20\n최근 20거래일 동안의 평균 거래량입니다.\n- 주가 상승과 함께 평균 이상의 거래량 발생: 강한 매수 세력 유입\n- 주가 하락 및 거래량 감소: 매도 세력 둔화"}>거래량 MA20</span>
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
        </div>}
      </div>

      <p className="detail-disclaimer">
        {technical?.disclaimer ?? '차트는 공급자에서 수집한 실제 가격 이력이며 투자 권유가 아닙니다.'}
      </p>
    </section>
  )
}

function availabilityStatus(value: CanonicalStockDetail['dataAvailability']): DataStatus {
  if (value === 'READY') return 'READY'
  if (value === 'METADATA_ONLY') return 'METADATA_ONLY'
  if (value === 'UNAVAILABLE') return 'UNAVAILABLE'
  return 'PARTIAL'
}
