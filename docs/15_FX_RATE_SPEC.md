# FinWatch 환율·기준통화 포트폴리오 명세

상태: v1.0 USD/KRW MVP 구현
기준일: 2026-07-14
MVP 통화쌍: `USD/KRW`
사용자 기본 기준통화: `KRW`

## 1. 목적과 현재 한계

현재 포트폴리오는 KRW와 USD 평가액을 통화별로 분리하며 환율 없이 합산하지 않는다. 이는 안전하지만 국장·미장을 함께 보는 사용자가 전체 자산 규모와 미국 주식의 원화 환산 가치를 한눈에 확인할 수 없다.

환율 기능의 목표는 다음과 같다.

- 대시보드에서 USD/KRW 환율과 등락·기준시각 표시
- 미국 주식 가격과 평가액의 USD 원값 유지
- 현재 환율을 적용한 KRW 환산 평가액 제공
- 포트폴리오의 기준통화 통합 평가액 제공
- 매수 당시 환율이 없을 때 환차손익을 임의 계산하지 않음
- 모든 환산값에 환율 출처, 기준시각과 freshness 연결

환율은 투자상품 추천이나 외환 거래 기능이 아니다. 환전·주문·자동매매는 범위 밖이다.

## 2. 환율 표기 규칙

`USD/KRW`는 다음 의미로 고정한다.

```text
baseCurrency  = USD
quoteCurrency = KRW
rate          = 1 USD를 사는 데 필요한 KRW

예: USD/KRW = 1,382.50
    100 USD × 1,382.50 = 138,250 KRW
```

API·DB·UI에서 역수 표기를 섞지 않는다. `KRW/USD`가 필요하면 별도 통화쌍으로 명시하거나 서버가 정밀도를 유지해 역산하되 원본 pair와 source를 보존한다.

## 3. 공급자 결정

MVP 공급자 체인은 공급자 시각이 있는 Finnhub Forex WebSocket 체결, Finnhub REST 기준값, Frankfurter 일일 기준환율 순으로 확정한다.

- WebSocket은 공식 공급자 식별자 `OANDA:USD_KRW`를 설정값으로 사용하며 운영 환경에서 재정의할 수 있다.
- 공급자 timestamp가 없는 메시지, 미래 시각 또는 2분을 초과한 틱은 LIVE로 채택하지 않는다.
- Finnhub REST rate처럼 공급자 시장 시각이 없는 응답은 서버 조회 시각을 `fetchedAt`으로만 기록하고 `REFERENCE`로 표시한다.
- quote와 candle 사용 가능 여부는 발급 계정의 권한·요금제 PoC로 검증한다.
- 문서 참고: [Finnhub WebSocket Trades](https://finnhub.io/docs/api/websocket-trades), [Finnhub Forex Rates](https://finnhub.io/docs/api/forex-rates), [Finnhub Forex Candles](https://finnhub.io/docs/api/forex-candles)

Finnhub 계정에서 USD/KRW rate/candle 권한이 거절되면 `api.frankfurter.dev`의 일일 reference rate와 기간별 reference rate를 사용한다. 이 fallback은 `source=FRANKFURTER`, `rateType=REFERENCE`로 표시하며 일중 체결·호가 또는 실시간 환율처럼 표현하지 않는다. 두 공급자가 모두 실패하면 기존 마지막 검증값을 상태와 함께 보여주거나 환산 합계를 숨기며, 임의 상수 환율을 LIVE 화면에 사용하지 않는다.

## 4. 데이터 모델과 품질

환율 레코드는 최소 다음 필드를 가진다.

| 필드 | 설명 |
|---|---|
| baseCurrency | USD |
| quoteCurrency | KRW |
| rate | 1 USD당 KRW |
| previousClose | 공급자가 제공하는 경우 직전 기준값 |
| change, changeRate | 서버가 같은 source·세션의 값으로 계산 |
| rateType | LIVE / DELAYED / REFERENCE / DEMO |
| source | FINNHUB 등 |
| providerSymbol | 공급자 통화쌍 식별자 |
| asOf | 공급자 기준 시각 UTC |
| fetchedAt | 서버 수집 시각 UTC |
| freshness | FRESH / DELAYED / STALE / UNAVAILABLE |

검증 규칙:

- ISO 4217 대문자 3자리
- baseCurrency와 quoteCurrency가 달라야 함
- rate > 0
- 미래 5분을 초과한 asOf 거부
- 동일 pair·asOf 중복 저장 금지
- 공급자 시각 없이 fetchedAt을 시장 시각으로 위장하지 않음
- 비정상 급등락은 임의 보정하지 않고 격리·경고
- 주말·휴일에는 마지막 정상 영업일 값과 시장 상태를 함께 표시

## 5. API 계약

### 5.1 최신 환율

`GET /api/v1/market/fx-rates/USD/KRW`

```json
{
  "success": true,
  "data": {
    "baseCurrency": "USD",
    "quoteCurrency": "KRW",
    "rate": 1382.50,
    "previousClose": 1378.20,
    "change": 4.30,
    "changeRate": 0.31,
    "rateType": "LIVE",
    "source": "FINNHUB_WS",
    "asOf": "2026-07-14T06:00:00Z",
    "fetchedAt": "2026-07-14T06:00:08Z",
    "freshness": "FRESH"
  },
  "message": "환율 조회 성공"
}
```

### 5.2 환율 이력

`GET /api/v1/market/fx-rates/USD/KRW/history?period=1M&interval=1D`

- MVP 기간: `1W`, `1M`, `3M`, `1Y`
- MVP interval: `1D`
- 각 item: `time`, `open`, `high`, `low`, `close`, `source`
- 공급자가 candle을 제공하지 않으면 저장한 일별 reference rate만 반환하고 `rateType=REFERENCE`를 표시한다.

### 5.3 지원 통화쌍

`GET /api/v1/market/fx-rates/pairs`

MVP는 USD/KRW만 활성화한다. 지원하지 않는 pair는 빈 값이 아니라 `404 FX_PAIR_NOT_SUPPORTED`를 반환한다.

### 5.4 실시간 WebSocket 이벤트

인증 브라우저는 주식 시세와 같은 `WS /ws/quotes` 연결에서 USD/KRW를 수신한다. 연결 직후 현재 유효한 환율 snapshot이 있으면 전송하고 이후 공급자 시각이 더 최신인 체결만 게시한다.

```json
{
  "type": "fx",
  "data": {
    "baseCurrency": "USD",
    "quoteCurrency": "KRW",
    "rate": 1382.50,
    "rateType": "LIVE",
    "source": "FINNHUB_WS",
    "providerSymbol": "OANDA:USD_KRW",
    "asOf": "2026-07-30T06:00:00Z",
    "fetchedAt": "2026-07-30T06:00:01Z"
  }
}
```

프런트는 `USD/KRW` canonical pair 하나의 최신 event만 수용한다. `asOf`가 같거나 오래된 이벤트는 무시하고, 일반 사용자에게는 원시 `source`·`providerSymbol`을 표시하지 않는다.

## 6. 포트폴리오 환산 계약

### 6.1 현재 평가액

기준통화가 KRW일 때:

```text
KRW holding convertedEvaluationAmount = evaluationAmount
USD holding convertedEvaluationAmount = evaluationAmount × USDKRW.rate

baseCurrencyTotalEvaluationAmount
  = 모든 convertedEvaluationAmount의 합
```

환산은 중간 계산에서 충분한 정밀도를 유지하고 KRW 화면 표시는 마지막에 1원 단위 HALF_UP한다. 응답에는 적용한 `fxRate`, `fxAsOf`, `fxSource`, `fxFreshness`를 포함한다.

환율이 없거나 허용 freshness를 초과하면 USD 보유의 `convertedEvaluationAmount`와 통합 평가액은 null이며 `conversionComplete=false`다. 환산 가능한 종목만 더한 부분 합계를 전체 자산처럼 표시하지 않는다.

### 6.2 매입금액과 환차손익

현재 보유 모델은 USD 평균매수가만 저장하고 매수 당시 환율을 저장하지 않는다. 따라서 현재 환율로 평가액은 환산할 수 있지만 정확한 KRW 매입원가·통합 손익·환차손익은 계산할 수 없다.

정확한 환차손익을 제공하려면 보유 등록·수정에 다음 선택 필드를 추가한다.

```json
{
  "averagePurchaseFxRate": 1320.40,
  "purchaseFxBaseCurrency": "USD",
  "purchaseFxQuoteCurrency": "KRW"
}
```

값이 있을 때:

```text
convertedPurchaseAmount = purchaseAmount × averagePurchaseFxRate
convertedProfitLoss     = convertedEvaluationAmount - convertedPurchaseAmount
fxEffectApproximation   = evaluationAmount × (currentFxRate - averagePurchaseFxRate)
```

`fxEffectApproximation`은 평균값 기반 근사치이므로 그렇게 표시한다. 실제 체결별 환차손익은 거래 이력 기능을 도입하기 전까지 제공하지 않는다. 매수 환율이 없으면 `convertedPurchaseAmount`, `convertedProfitLoss`, `fxEffectApproximation`은 null이다.

### 6.3 포트폴리오 응답 확장

```json
{
  "baseCurrency": "KRW",
  "baseCurrencyTotalEvaluationAmount": 25342000,
  "baseCurrencyTotalPurchaseAmount": null,
  "baseCurrencyProfitLoss": null,
  "conversionComplete": true,
  "profitLossComplete": false,
  "fxRates": [
    {
      "pair": "USD/KRW",
      "rate": 1382.50,
      "asOf": "2026-07-14T06:00:00Z",
      "source": "FINNHUB",
      "freshness": "FRESH"
    }
  ]
}
```

기존 `currencySummaries`와 원통화 금액은 제거하지 않는다. 환산값은 별도 필드로 추가해 사용자가 원값을 검증할 수 있게 한다.

## 7. DB 계약

### exchange_rates

| 컬럼 | 타입/설명 |
|---|---|
| id | BIGINT PK |
| base_currency, quote_currency | CHAR(3), NOT NULL |
| rate | NUMERIC(24, 10), NOT NULL |
| open_rate, high_rate, low_rate, close_rate | 이력 candle이면 사용 |
| rate_type | LIVE / DELAYED / REFERENCE / DEMO |
| source, provider_symbol | 공급자 식별 |
| as_of, fetched_at | TIMESTAMPTZ |
| created_at | TIMESTAMPTZ |

UNIQUE `(base_currency, quote_currency, source, as_of)`. 최신 조회용 `(base_currency, quote_currency, as_of DESC)` 인덱스를 둔다.

### portfolio_holdings 확장

- `average_purchase_fx_rate NUMERIC(24, 10) NULL`
- `purchase_fx_base_currency CHAR(3) NULL`
- `purchase_fx_quote_currency CHAR(3) NULL`

세 필드는 모두 null이거나 모두 유효해야 한다. KRW 종목에는 USD/KRW 매수 환율을 요구하지 않는다.

## 8. 캐시·수집·장애

- 최신 환율 Redis key: `fx:latest:{base}:{quote}:{source}`
- 이력 key: `fx:history:{base}:{quote}:{period}:{interval}:{source}`
- 장중 quote TTL 목표 30~60초, reference rate는 다음 영업일 갱신까지 사용
- 브라우저는 `/ws/quotes`의 `fx` 이벤트를 우선 적용하고 상단 ticker·환율 상세·포트폴리오가 같은 실시간 상태를 공유
- WebSocket 값이 없을 때 REST 최신 조회를 초기값·복구 경로로 사용하며, visible·online 상태의 제한된 재조회 실패 시 마지막 정상값을 stale로 유지
- 동일 pair 동시 MISS는 single-flight로 외부 호출 한 번만 수행
- 외부 장애 시 마지막 검증 DB 값을 freshness와 함께 반환
- 환율 장애가 주식 원통화 가격·차트·포트폴리오 조회를 막아서는 안 됨
- 환산값은 해당 응답에 포함한 동일 환율 스냅샷 하나로 계산해 카드별 값이 달라지지 않게 함

## 9. UI 계약

### 대시보드

- 상단 시장 요약 영역에 `USD/KRW 1,382.50 · +0.31%` 표시
- 일반 화면은 LIVE/지연/기준환율 상태와 기준시각을 간결하게 표시하고, source·providerSymbol·수집시각은 관리자 진단에서만 제공
- 클릭하면 `1W·1M·3M·1Y` 미니 환율 차트 표시
- 상승·하락을 색상만이 아니라 부호·텍스트로 구분

### 미국 종목 상세

- 주가는 원통화 USD를 기본으로 유지
- 선택적으로 현재 환율을 적용한 `약 ₩...` 보조값 표시
- 차트 축 자체를 환산해 기술지표 의미를 바꾸지 않음

### 포트폴리오

- 기존 KRW·USD 통화별 카드 유지
- `현재 환율 기준 총 평가액` KRW 카드 추가
- 환율과 asOf를 카드 바로 아래 표시
- 실시간 `fx` 이벤트가 오면 USD 보유의 KRW 환산 평가액·자산배분 도넛을 같은 환율로 제자리 갱신
- 매수 환율이 없으면 `원화 손익 계산 불가 — 매수 당시 환율 필요` 표시
- stale·reference 환율을 실시간으로 표현하지 않음

## 10. AI 기능과의 관계

MVP에서 Gemini는 환율을 예측하지 않는다. 환율은 서버가 제공하는 사실 데이터로만 사용한다.

- 미국 종목 가격을 원화로 설명할 때 사용한 pair·rate·asOf를 근거에 포함
- 환율과 기업 주가 사이의 인과관계를 근거 없이 단정하지 않음
- 일일 변화 브리핑에 환율을 추가할 경우 별도 `F` evidence ID와 `MACRO_CONTEXT` 관점으로 분리
- 목표 환율, 환전 시점과 외환 매매 권고 금지

## 11. 오류 계약

| HTTP | code | 조건 |
|---|---|---|
| 400 | FX_PAIR_INVALID | 통화 코드·동일 통화쌍 오류 |
| 404 | FX_PAIR_NOT_SUPPORTED | 지원 범위 밖 pair |
| 422 | FX_RATE_UNAVAILABLE | 환산에 사용할 검증 환율 없음 |
| 429 | FX_PROVIDER_QUOTA_EXCEEDED | 공급자 호출 제한 |
| 502 | FX_PROVIDER_INVALID_RESPONSE | 공급자 schema·rate·시각 검증 실패 |
| 503 | FX_PROVIDER_UNAVAILABLE | 공급자 장애이고 fallback도 없음 |

포트폴리오 원통화 조회는 환율 오류 대신 성공 응답과 `conversionComplete=false`를 반환한다.

## 12. 구현 순서

1. `FxRateProvider` 중립 인터페이스와 fixture
2. Finnhub WebSocket provider timestamp·quote·candle 어댑터 계약 테스트
3. Frankfurter reference 최신·이력 fallback과 계약 테스트
4. `exchange_rates` migration과 저장·품질 검증
5. 최신·이력 API와 Redis/DB fallback·single-flight
6. 대시보드 환율 ticker와 미니 차트
7. 포트폴리오 현재 평가액 KRW 환산
8. 매수 환율 선택 입력과 환차손익 근사치
9. 관리자 freshness·호출량·오류 지표
10. AC-14 자동 테스트와 LIVE smoke

## 13. 인수 조건

- [ ] USD/KRW의 방향과 단위가 API·DB·UI·계산에서 동일하다.
- [ ] 일반 환율 카드에 rate, 등락, rateType, asOf와 freshness가 표시되고 source·providerSymbol은 관리자 진단에서 확인된다.
- [ ] 공급자 timestamp가 있는 Finnhub 체결만 `/ws/quotes`의 `fx` LIVE 이벤트로 게시되고 오래된 이벤트는 화면 상태를 되돌리지 않는다.
- [ ] 미국 주식 원통화 USD 값은 보존되고 KRW 보조값과 혼동되지 않는다.
- [ ] 고정 fixture에서 USD 평가액의 KRW 환산과 반올림 결과가 일치한다.
- [ ] 혼합 포트폴리오의 통합 현재 평가액은 같은 환율 스냅샷으로 계산된다.
- [ ] 환율이 없거나 stale이면 부분 합계를 전체처럼 표시하지 않고 원통화 조회는 유지된다.
- [ ] 매수 환율이 없으면 원화 매입원가·손익·환차손익을 생성하지 않는다.
- [ ] 매수 환율이 있으면 평균값 기반 환차손익 근사치임을 표시한다.
- [ ] 주말·휴일의 마지막 값은 시장 상태와 함께 표시되고 실시간으로 위장되지 않는다.
- [ ] 외부 장애·429·비정상 rate·미래 asOf가 데이터 손상 없이 처리된다.
- [ ] 동일 pair 동시 MISS가 외부 호출 한 번으로 합쳐진다.
- [ ] DEMO에서는 고정 환율 fixture임을 표시하고 LIVE 데이터처럼 표현하지 않는다.

미완료 항목이 남아 있으면 “환율 기반 통합 포트폴리오 평가”를 구현 완료로 표시하지 않는다.
