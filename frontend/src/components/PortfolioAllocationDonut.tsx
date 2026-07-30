import type { Portfolio } from '../types/portfolio'

type Props = {
  portfolio: Portfolio
}

type AllocationSlice = {
  key: string
  label: string
  value: number
  percentage: number
  color: string
}

const palette = [
  '#25d6c8',
  '#4be39a',
  '#7aa2ff',
  '#ad86ff',
  '#f5bd50',
  '#ff8f70',
  '#ec8ff5',
  '#607d8b',
]

export function PortfolioAllocationDonut({ portfolio }: Props) {
  const total = portfolio.baseCurrencyTotalEvaluationAmount
  if (!portfolio.conversionComplete || total == null || !Number.isFinite(total) || total <= 0) {
    return (
      <section className="portfolio-allocation-card incomplete" aria-label="포트폴리오 자산 비중">
        <div>
          <span>종목별 자산 비중</span>
          <strong>통합 비중 계산 대기</strong>
        </div>
        <p>일부 종목의 가격 또는 환율이 없어 서로 다른 통화를 하나의 비중으로 합치지 않습니다.</p>
      </section>
    )
  }

  const valued = portfolio.holdings
    .filter((holding) => holding.convertedEvaluationAmount != null && holding.convertedEvaluationAmount > 0)
    .sort((left, right) => (right.convertedEvaluationAmount ?? 0) - (left.convertedEvaluationAmount ?? 0))
  const visible = valued.slice(0, 7)
  const remaining = valued.slice(7)
  const rawSlices = visible.map((holding) => ({
    key: `${holding.market}:${holding.symbol}`,
    label: holding.name,
    value: holding.convertedEvaluationAmount ?? 0,
  }))
  if (remaining.length > 0) {
    rawSlices.push({
      key: 'OTHER',
      label: `기타 ${remaining.length}종목`,
      value: remaining.reduce((sum, holding) => sum + (holding.convertedEvaluationAmount ?? 0), 0),
    })
  }

  const slices: AllocationSlice[] = rawSlices.map((slice, index) => ({
    ...slice,
    percentage: slice.value / total * 100,
    color: palette[index % palette.length],
  }))
  let offset = 0

  return (
    <section className="portfolio-allocation-card" aria-labelledby="portfolio-allocation-title">
      <div className="portfolio-allocation-heading">
        <div>
          <span className="eyebrow">ASSET ALLOCATION</span>
          <h3 id="portfolio-allocation-title">종목별 자산 비중</h3>
        </div>
        <small>현재 시세·USD/KRW 환산 기준</small>
      </div>
      <div className="portfolio-allocation-layout">
        <div className="portfolio-donut-wrap">
          <svg
            className="portfolio-donut"
            viewBox="0 0 120 120"
            role="img"
            aria-label={slices.map((slice) => `${slice.label} ${slice.percentage.toFixed(1)}%`).join(', ')}
          >
            <title>포트폴리오 종목별 원화 환산 평가액 비중</title>
            <circle className="portfolio-donut-track" cx="60" cy="60" r="45" pathLength="100" />
            {slices.map((slice) => {
              const currentOffset = offset
              offset += slice.percentage
              return (
                <circle
                  key={slice.key}
                  className="portfolio-donut-slice"
                  cx="60"
                  cy="60"
                  r="45"
                  pathLength="100"
                  stroke={slice.color}
                  strokeDasharray={`${Math.max(0, slice.percentage)} ${Math.max(0, 100 - slice.percentage)}`}
                  strokeDashoffset={-currentOffset}
                  transform="rotate(-90 60 60)"
                >
                  <title>{slice.label} {slice.percentage.toFixed(1)}%</title>
                </circle>
              )
            })}
          </svg>
          <div className="portfolio-donut-center" aria-hidden="true">
            <strong>{valued.length}</strong>
            <span>종목</span>
          </div>
        </div>
        <ul className="portfolio-allocation-legend">
          {slices.map((slice) => (
            <li key={slice.key}>
              <i style={{ backgroundColor: slice.color }} aria-hidden="true" />
              <span>{slice.label}</span>
              <strong>{slice.percentage.toFixed(1)}%</strong>
              <small>{formatWon(slice.value)}</small>
            </li>
          ))}
        </ul>
      </div>
      <p>비중은 반올림 전 원화 환산 평가액으로 계산하며 투자 권유를 의미하지 않습니다.</p>
    </section>
  )
}

function formatWon(value: number) {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: 'KRW',
    maximumFractionDigits: 0,
  }).format(value)
}
