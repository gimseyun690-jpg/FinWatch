# FinWatch 사용자 기능 상세 명세

상태: v1.0 MVP 구현
기준일: 2026-07-14
범위: 관심종목, 포트폴리오, 가격 알림, 사용자 메인 대시보드

## 1. 목적과 문서 상태

이 문서는 01_REQUIREMENTS.md의 U-02, U-03, U-06, U-07과 03_API_SPEC.md의 사용자 기능 계약을 구현 가능한 업무 규칙으로 구체화한다.

기능별 상태 표기는 다음과 같다.

| 표기 | 의미 |
|---|---|
| 구현 | 현재 백엔드와 화면에서 동작하며 테스트로 확인된 규칙 |
| 부분 구현 | 일부 데이터만 실제 API와 연결됐거나 예외 규칙이 빠진 상태 |
| 미구현 | 구현 전에 이 문서를 계약으로 삼아야 하는 목표 규칙 |

현재 상태는 다음과 같다.

| 기능 | 상태 | 근거 |
|---|---|---|
| 관심종목 조회·등록·삭제 | 구현 | watchlists 테이블, API, 사용자 격리 통합 테스트 존재 |
| 포트폴리오 CRUD·평가 | 구현 | 사용자 분리, 원통화 보존, USD/KRW 기반 KRW 통합 평가와 화면 연결 |
| 가격 알림 CRUD·평가 | 구현 | ABOVE/BELOW 조건, 상태 전이와 최신 가격 평가 화면 연결 |
| 메인 대시보드 | 구현 | 선택 market·symbol이 차트·뉴스·공시·세 AI 기능·WebSocket 구독에 함께 연결됨 |

이 문서의 “현재 동작”은 기존 구현을 설명하고, “목표 규칙”과 인수 조건은 미구현 기능의 완료 기준이다.

## 2. 공통 업무 규칙

### 2.1 사용자와 소유권

1. 모든 사용자 데이터 API는 유효한 JWT가 필요하다.
2. 사용자 식별자는 요청 본문이나 쿼리에서 받지 않고 JWT의 userId 클레임만 사용한다.
3. USER와 ADMIN 모두 자신의 관심종목·포트폴리오·알림만 조회하고 변경한다.
4. 다른 사용자의 holdingId 또는 alertId를 요청한 경우 존재 여부를 노출하지 않고 현재 사용자 기준 404를 반환한다.
5. 인증 누락·만료는 401 AUTHENTICATION_REQUIRED, 권한 부족은 403 ACCESS_DENIED를 사용한다.

### 2.2 종목 식별

1. 입력 symbol은 앞뒤 공백을 제거하고 영문을 대문자로 정규화한다.
2. symbol은 공백일 수 없고 최대 30자다.
3. 등록 시 stocks.active=true인 종목만 허용한다.
4. 현재 API는 symbol만 경로 키로 사용하므로 MVP 종목 카탈로그에서는 symbol이 시장 간에도 유일해야 한다.
5. 서로 다른 시장에 같은 symbol을 등록해야 한다면 market과 symbol의 복합 키로 API를 변경한 후 데이터를 추가한다. 임의로 첫 번째 종목을 선택해서는 안 된다.

### 2.3 금액, 통화와 시간

1. 계산은 Java BigDecimal을 사용하며 부동소수점 number/double로 중간 계산하지 않는다.
2. 저장 정밀도는 수량 NUMERIC(20, 6), 가격·금액 NUMERIC(20, 4)을 기준으로 한다.
3. 금액 응답에는 반드시 ISO 4217 통화 코드가 포함되어야 한다.
4. 서로 다른 통화의 금액을 환율 없이 더하지 않는다.
5. 가격 응답에는 실시간 허브 또는 market_prices의 source와 asOf를 함께 제공한다.
6. 서버 시간은 UTC Instant로 저장하고 API는 ISO-8601 형식으로 반환한다.
7. 현재가가 오래됐다는 이유로 임의의 실시간 가격처럼 표시하지 않는다. 화면에 기준 시각과 출처를 노출한다.

### 2.4 공통 UI 상태

각 카드 또는 영역은 loading, success, empty, error 상태를 구분한다. 일부 영역의 요청이 실패해도 성공한 다른 영역은 유지한다. 오류 시 마지막 정상값을 표시한다면 “이전 데이터”와 기준 시각을 명시해야 하며, 고정 예시값을 실제 사용자 데이터처럼 표시해서는 안 된다.

## 3. 관심종목

### 3.1 API와 현재 구현

| 동작 | API | 성공 |
|---|---|---|
| 목록 조회 | GET /api/v1/watchlists | 200 |
| 등록 | POST /api/v1/watchlists | 201 |
| 삭제 | DELETE /api/v1/watchlists/{symbol} | 200 |

등록 요청:

~~~json
{
  "symbol": "000660"
}
~~~

목록과 등록 응답 항목은 id, symbol, name, market, currency, price, change, changeRate, asOf, source, addedAt을 포함한다.

### 3.2 업무 규칙

1. 관심종목은 사용자와 종목의 조합당 하나만 존재한다.
2. 등록 입력은 trim 후 대문자로 변환한다. 예를 들어 “ nvda ”는 “NVDA”로 처리한다.
3. 등록 대상 종목이 없거나 비활성 상태면 404 STOCK_NOT_FOUND를 반환해야 한다.
4. 같은 사용자가 이미 등록한 종목을 다시 등록하면 기존 항목을 반환하거나 덮어쓰지 않고 409 WATCHLIST_DUPLICATED를 반환한다.
5. DB의 UNIQUE(user_id, stock_id)를 최종 동시성 방어선으로 사용하며, 동시 등록으로 발생한 유니크 충돌도 같은 409 코드로 변환해야 한다.
6. 목록은 addedAt 오름차순으로 반환한다. 현재 구현도 생성 시각 오름차순이다.
7. 목록의 가격과 등락 정보는 관심종목에 복사해 저장하지 않고 조회 시 최신 market_prices로 계산한다.
8. change는 최신 종가에서 직전 1D 종가를 뺀 값이다. changeRate는 change / 직전 종가 × 100이며 직전 종가가 0이면 0이다.
9. 삭제는 현재 로그인 사용자의 목록만 대상으로 하며 symbol 비교는 대소문자를 구분하지 않는다.
10. 삭제 대상이 없으면 성공으로 간주하지 않고 404 WATCHLIST_NOT_FOUND를 반환한다.
11. MVP에는 사용자별 관심종목 개수 제한이 없다. 운영 제한을 도입하면 서버 설정과 오류 코드 WATCHLIST_LIMIT_EXCEEDED를 먼저 명세한다.

### 3.3 화면 선택 규칙

1. 목록 로드 후 현재 선택된 symbol이 목록에 없으면 첫 번째 관심종목을 선택한다.
2. 선택 중인 종목을 삭제하면 남은 첫 번째 종목을 선택한다.
3. 목록이 비면 종목 상세를 숨기고 첫 관심종목 등록 안내를 표시한다.
4. 추가 선택 목록에는 활성 종목 중 아직 등록하지 않은 종목만 표시한다.
5. 등록·삭제 중 같은 버튼의 중복 요청을 막고 실패하면 서버 메시지를 영역 내 오류로 표시한다.

### 3.4 오류

| HTTP | 코드 | 조건 | 현재 상태 |
|---|---|---|---|
| 400 | VALIDATION_ERROR | symbol 누락, 공백 또는 30자 초과 | 입력 검증 구현, 공통 코드 매핑은 보강 필요 |
| 401 | AUTHENTICATION_REQUIRED | 인증 누락·만료 또는 사용자 식별 실패 | 인증 계층 구현 |
| 404 | STOCK_NOT_FOUND | 등록할 활성 종목 없음 | 상태 코드는 구현, 공통 오류 코드 매핑은 보강 필요 |
| 404 | WATCHLIST_NOT_FOUND | 현재 사용자에게 삭제 대상 없음 | 구현 |
| 409 | WATCHLIST_DUPLICATED | 현재 사용자에게 같은 종목 존재 | 구현 |

### 3.5 인수 조건

- 사용자 A가 등록한 종목은 사용자 B의 목록에 나타나지 않는다.
- 공백과 소문자가 섞인 미국 종목 symbol도 정규화되어 등록된다.
- 동일 사용자·동일 종목 두 번째 등록은 409이고 행이 늘지 않는다.
- 동일 종목을 서로 다른 두 사용자가 각각 등록할 수 있다.
- 목록 가격은 최신 시장 데이터가 바뀌면 관심종목 행 수정 없이 함께 바뀐다.
- 없는 종목 삭제는 404, 정상 삭제 후 재조회 결과는 빈 목록 또는 나머지 목록이다.
- 목록의 순서는 addedAt 오름차순으로 결정적이다.

## 4. 포트폴리오

상태: 구현. 사용자별 CRUD, 실시간 우선 최신 가격 평가와 KRW/USD 통화별 합계를 제공한다.

### 4.1 데이터 범위

1. MVP는 거래 체결 내역이 아니라 “현재 보유 스냅샷”을 관리한다.
2. 한 사용자와 한 종목의 보유 행은 하나만 존재한다.
3. 같은 종목의 추가 매수·매도 이력을 서버가 계산하지 않는다. 사용자가 PATCH로 수량과 평균 매수가를 갱신한다.
4. quantity=0으로 삭제를 표현하지 않는다. 보유 제거는 DELETE를 사용한다.
5. symbol과 currency는 생성 후 변경할 수 없다. 다른 종목 또는 통화로 바꾸려면 기존 행을 삭제하고 새로 생성한다.

### 4.2 API

| 동작 | API | 성공 |
|---|---|---|
| 보유 및 평가 조회 | GET /api/v1/portfolios | 200 |
| 보유 등록 | POST /api/v1/portfolios/holdings | 201 |
| 보유 수정 | PATCH /api/v1/portfolios/holdings/{holdingId} | 200 |
| 보유 삭제 | DELETE /api/v1/portfolios/holdings/{holdingId} | 200 |

등록 요청:

~~~json
{
  "symbol": "000660",
  "quantity": 10,
  "averagePurchasePrice": 250000,
  "currency": "KRW"
}
~~~

수정 요청은 quantity와 averagePurchasePrice 중 하나 이상을 포함한다.

~~~json
{
  "quantity": 12.5,
  "averagePurchasePrice": 248000
}
~~~

### 4.3 유효성

| 필드 | 규칙 |
|---|---|
| symbol | trim·대문자 정규화, 필수, 최대 30자, 활성 종목이어야 함 |
| quantity | 0보다 큼, 전체 20자리·소수 6자리 이하 |
| averagePurchasePrice | 0 이상, 전체 20자리·소수 4자리 이하 |
| currency | 대문자 3자이며 대상 stock.currency와 정확히 일치 |

평균 매수가 0은 무상 취득 등 데이터 표현을 위해 허용한다. 이때 매입금액은 0이고 수익률은 0으로 왜곡하지 않고 null로 반환한다.

같은 사용자·종목의 두 번째 POST는 자동 합산하지 않고 409 HOLDING_DUPLICATED를 반환한다. 사용자는 기존 holdingId를 PATCH해야 한다.

### 4.4 평가 계산

종목별 계산식:

~~~text
purchaseAmount   = quantity × averagePurchasePrice
evaluationAmount = quantity × latestPrice
profitLoss        = evaluationAmount - purchaseAmount
returnRate        = purchaseAmount > 0
                    ? profitLoss ÷ purchaseAmount × 100
                    : null
~~~

1. latestPrice는 실시간 허브와 최신 market_prices의 asOf를 비교해 더 새로운 가격을 선택한다.
2. 계산 중간값은 가능한 정밀도를 유지하고 금액 응답은 소수 4자리에서 HALF_UP한다.
3. returnRate는 소수 4자리까지 계산하고 API 응답은 소수 2자리 HALF_UP을 기본 표시 정밀도로 사용한다.
4. 보유별 응답에는 latestPrice, priceAsOf, priceSource, purchaseAmount, evaluationAmount, profitLoss, returnRate를 포함한다.
5. 가격이 존재하지 않는 보유는 valuationStatus=PRICE_UNAVAILABLE로 반환한다. purchaseAmount는 계산하지만 evaluationAmount, profitLoss, returnRate는 null이다.
6. 오래된 가격도 임의 보정하지 않는다. 값과 asOf를 반환하고 화면에서 데이터 기준 시각을 표시한다.

### 4.5 복수 통화

구현된 환율 공급자는 USD/KRW 스냅샷을 DB와 캐시에 보관한다. 포트폴리오는 기존 `currencySummaries`와 원통화 값을 유지하면서 현재 환율 기준 KRW 통합 평가액을 별도로 제공하고, 모든 보유 자산을 환산할 수 없으면 `conversionComplete=false`로 표시한다. 매수 당시 환율이 없으면 해당 보유의 원화 매입원가·손익·환차손익을 계산하지 않는다. 상세 계약은 `15_FX_RATE_SPEC.md`를 따른다.

~~~json
{
  "currencySummaries": [
    {
      "currency": "KRW",
      "totalPurchaseAmount": 2500000,
      "totalEvaluationAmount": 2723000,
      "profitLoss": 223000,
      "returnRate": 8.92,
      "valuationComplete": true
    }
  ],
  "holdings": []
}
~~~

통화별 계산식:

~~~text
totalPurchaseAmount   = 같은 통화 보유의 purchaseAmount 합계
totalEvaluationAmount = 같은 통화 보유의 evaluationAmount 합계
profitLoss            = totalEvaluationAmount - totalPurchaseAmount
returnRate            = totalPurchaseAmount > 0
                        ? profitLoss ÷ totalPurchaseAmount × 100
                        : null
~~~

같은 통화 그룹에 PRICE_UNAVAILABLE 보유가 하나라도 있으면 valuationComplete=false로 하고 totalEvaluationAmount, profitLoss, returnRate는 null로 반환한다. 누락된 종목을 제외한 부분 합계를 완전한 합계처럼 표시하지 않는다.

### 4.6 상태와 동시성

1. 보유 행은 생성과 삭제 두 상태만 가지며 별도의 활성 상태는 없다.
2. PATCH는 현재 사용자의 행을 잠금 또는 낙관적 버전으로 보호해야 한다. MVP에 버전 필드가 없으면 마지막 정상 커밋이 적용되며, 향후 다중 클라이언트 편집 시 version 필드를 추가한다.
3. 등록 중 DB 유니크 충돌은 HOLDING_DUPLICATED로 변환한다.
4. 삭제와 수정 대상은 반드시 current user 조건을 포함해 조회한다.

### 4.7 오류

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | VALIDATION_ERROR | 누락, 범위·자릿수·PATCH 필드 오류 |
| 404 | STOCK_NOT_FOUND | 활성 종목 없음 |
| 404 | HOLDING_NOT_FOUND | 현재 사용자에게 holdingId 없음 |
| 409 | HOLDING_DUPLICATED | 같은 사용자·종목 보유 존재 |
| 422 | HOLDING_CURRENCY_MISMATCH | 요청 통화와 종목 통화 불일치 |

### 4.8 인수 조건

- 수량 10, 평균가 100, 현재가 120이면 매입 1,000, 평가 1,200, 손익 200, 수익률 20.00이다.
- 평균가 0이면 수익률은 null이며 0%로 표시되지 않는다.
- 같은 종목을 두 번 POST해도 자동 합산되지 않고 409가 된다.
- quantity=0, 음수, 소수 6자리 초과는 400이다.
- 종목 통화와 다른 currency는 422이며 행이 생성되지 않는다.
- KRW와 USD 보유는 별도 currencySummaries로 계산되고 하나의 총액으로 더해지지 않는다.
- 최신 가격이 없는 보유가 포함된 통화 요약은 valuationComplete=false이고 평가 합계가 null이다.
- 사용자 A는 사용자 B의 holdingId를 조회·수정·삭제할 수 없다.
- PATCH 후 평가는 저장된 평가액이 아니라 수정된 보유 값과 조회 시 최신 가격으로 다시 계산된다.

## 5. 가격 알림

상태: 구현. CRUD와 실시간 우선 최신 가격 기준 조건 충족 상태를 화면에 표시한다. 모바일 푸시·이메일 전송은 MVP 범위가 아니다.

### 5.1 API와 입력

| 동작 | API | 성공 |
|---|---|---|
| 목록 조회 | GET /api/v1/alerts | 200 |
| 생성 | POST /api/v1/alerts | 201 |
| 수정·상태 변경 | PATCH /api/v1/alerts/{alertId} | 200 |
| 삭제 | DELETE /api/v1/alerts/{alertId} | 200 |

생성 요청:

~~~json
{
  "symbol": "000660",
  "condition": "ABOVE",
  "targetPrice": 280000,
  "currency": "KRW"
}
~~~

유효성:

| 필드 | 규칙 |
|---|---|
| symbol | 공통 종목 식별 규칙 적용 |
| condition | ABOVE 또는 BELOW |
| targetPrice | 0보다 큼, 전체 20자리·소수 4자리 이하 |
| currency | 대상 stock.currency와 일치 |
| status | 생성 요청에서는 받을 수 없으며 서버가 ACTIVE로 설정 |

같은 사용자·종목·condition·targetPrice의 ACTIVE 또는 TRIGGERED 알림이 이미 있으면 409 ALERT_DUPLICATED다. DISABLED 알림은 새로 만들지 말고 기존 alertId를 재활성화한다.

### 5.2 조건 판정

경계값은 포함한다.

~~~text
ABOVE 충족: latestPrice >= targetPrice
BELOW 충족: latestPrice <= targetPrice
~~~

1. 통화가 일치하는 가격만 비교한다.
2. 새 시장 틱은 종목별 최신 값으로 병합한 뒤 최대 약 250ms 안에 해당 종목의 ACTIVE 알림을 평가한다.
3. 장애 복구용 주기 작업은 아직 평가하지 않은 최신 가격을 다시 처리할 수 있지만 같은 가격과 알림을 중복 트리거해서는 안 된다.
4. 가격이 없으면 상태를 변경하지 않고 evaluationStatus=PRICE_UNAVAILABLE로 표시한다.
5. 이미 TRIGGERED 또는 DISABLED인 알림은 자동 평가하지 않는다.
6. triggeredAt은 ACTIVE에서 TRIGGERED로 실제 전환한 서버 UTC 시각이며, 판정에 사용한 price와 priceAsOf도 응답에 제공한다.

### 5.3 상태 전이

| 현재 상태 | 사건 | 다음 상태 | 처리 |
|---|---|---|---|
| 없음 | 정상 생성 | ACTIVE | triggeredAt=null |
| ACTIVE | 조건 미충족 | ACTIVE | 마지막 평가 가격·시각만 갱신 가능 |
| ACTIVE | 조건 충족 | TRIGGERED | triggeredAt을 한 번 기록 |
| ACTIVE | 사용자가 비활성화 | DISABLED | 자동 평가 중단 |
| TRIGGERED | 사용자가 비활성화 | DISABLED | triggeredAt 보존 |
| TRIGGERED | 사용자가 재활성화 | ACTIVE | triggeredAt=null, 다음 가격부터 재평가 |
| DISABLED | 사용자가 재활성화 | ACTIVE | triggeredAt=null, 다음 가격부터 재평가 |
| 임의 상태 | condition 또는 targetPrice 변경 | ACTIVE | triggeredAt=null, 다음 가격부터 새 조건 평가 |
| 임의 상태 | 삭제 | 없음 | 물리 삭제 |

PATCH에서 status=TRIGGERED를 직접 지정할 수 없다. 허용되는 사용자 상태 입력은 ACTIVE와 DISABLED뿐이다. 변경할 필드가 하나도 없으면 400 VALIDATION_ERROR다.

동시에 여러 평가 작업이 실행되더라도 DB에서 status=ACTIVE인 행만 조건부 갱신하여 한 번만 TRIGGERED로 전환한다. 후속 작업은 갱신 행 수 0을 정상적인 중복 방지로 처리한다.

### 5.4 목록과 표시

1. 기본 정렬은 ACTIVE, TRIGGERED, DISABLED 순이며 같은 상태에서는 updatedAt 내림차순이다.
2. 항목에는 alertId, symbol, name, condition, targetPrice, currency, status, currentPrice, priceAsOf, priceSource, triggeredAt, createdAt, updatedAt을 포함한다.
3. TRIGGERED는 충족 시각과 충족 가격을 텍스트로 표시한다.
4. 상승·하락 색상만으로 조건과 상태를 전달하지 않는다.

### 5.5 오류

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | VALIDATION_ERROR | enum, 가격, PATCH 상태·필드 오류 |
| 404 | STOCK_NOT_FOUND | 활성 종목 없음 |
| 404 | ALERT_NOT_FOUND | 현재 사용자에게 alertId 없음 |
| 409 | ALERT_DUPLICATED | 동일한 비활성 외 알림 존재 |
| 422 | ALERT_CURRENCY_MISMATCH | 종목과 목표가 통화 불일치 |

### 5.6 인수 조건

- ABOVE 100은 현재가 99에서 ACTIVE, 100에서 TRIGGERED다.
- BELOW 100은 현재가 101에서 ACTIVE, 100에서 TRIGGERED다.
- 같은 가격 이벤트가 재처리돼도 triggeredAt은 바뀌지 않고 한 번만 전환된다.
- DISABLED 알림은 조건이 충족돼도 TRIGGERED로 바뀌지 않는다.
- 조건 또는 목표가 수정 후 ACTIVE가 되고 기존 triggeredAt은 제거된다.
- 사용자 입력으로 TRIGGERED 상태를 만들 수 없다.
- 통화 불일치, 0 이하 목표가, 동일 알림 중복을 각각 지정 오류로 거절한다.
- 사용자 A는 사용자 B의 알림 존재 여부를 알 수 없으며 404를 받는다.
- 시장 가격이 없으면 오류로 전체 목록을 실패시키지 않고 해당 항목에 PRICE_UNAVAILABLE을 표시한다.

## 6. 메인 대시보드

### 6.1 현재 구현 상태

| 영역 | 현재 동작 | 상태 |
|---|---|---|
| API 상태 | /api/v1/health 결과에 따라 API 연결됨 또는 데모 모드 표시 | 구현 |
| 관심종목 | 인증 사용자 목록 조회·추가·삭제, 종목 선택 | 구현 |
| 종목 상세·기술 지표 | 선택된 관심종목 symbol로 조회 | 구현 |
| AI 뉴스·공시·변화 브리핑 | 선택된 market·symbol로 조회·분석 | 구현 |
| 포트폴리오 | 사용자 API와 WebSocket 시세로 통화별 평가액·손익 갱신 | 구현 |
| 가격 알림 | 사용자 API와 WebSocket 시세로 현재가·조건 상태 갱신 | 구현 |
| 관리자 AI 지표 | ADMIN에게만 표시하고 요약 후 새로고침 | 구현 |

포트폴리오와 알림은 API 응답을 초기 상태로 사용하고, 더 새로운 WebSocket 시세가 들어오면 화면 계산 결과를 즉시 덮어쓴다. 오래된 snapshot으로 더 최신 API 값을 되돌리지 않는다.

### 6.2 목표 데이터 구성

별도 복합 대시보드 API는 MVP 필수가 아니다. 프런트엔드는 다음 API를 병렬 호출하고 영역별로 상태를 관리한다.

| 영역 | 데이터 원천 |
|---|---|
| 관심종목 | GET /watchlists |
| 포트폴리오 | GET /portfolios |
| 가격 알림 현황 | GET /alerts |
| 선택 종목 상세·기술 지표 | GET /stocks/{symbol}, /prices, /technical |
| 선택 종목 뉴스·AI 요약 | GET /stocks/{symbol}/news, POST /ai/news-summaries |

### 6.3 상호작용과 일관성

1. 관심종목 첫 항목 또는 사용자가 선택한 항목이 selectedSymbol이다.
2. 종목 상세, 기술 지표와 뉴스는 모두 같은 selectedSymbol을 사용해야 한다. AI 뉴스 영역의 현재 000660 고정값은 제거해야 한다.
3. 관심종목이 비어 있으면 종목 상세와 관련 뉴스는 빈 상태로 전환한다.
4. 포트폴리오는 통화별 요약 카드를 표시하며 환율이 없을 때 단일 통합 총액을 표시하지 않는다.
5. 알림 현황은 ACTIVE 수, TRIGGERED 수와 최근 트리거 항목을 표시한다.
6. 각 영역은 독립적으로 로드한다. 한 API 실패가 전체 화면을 빈 화면으로 만들지 않는다.
7. 새로고침 후 서버 데이터를 다시 조회하며 프런트 고정 배열을 사용자 데이터 원본으로 사용하지 않는다.
8. 데이터 영역마다 source와 asOf 또는 updatedAt을 표시한다. 서로 다른 시각의 값을 하나의 동시 스냅샷처럼 표현하지 않는다.
9. 로그아웃 또는 401 이벤트가 발생하면 사용자별 화면 상태를 폐기하고 로그인 화면으로 이동한다.

### 6.4 인수 조건

- USER 로그인 후 자신의 관심종목·포트폴리오·알림만 표시된다.
- 관심종목 선택을 바꾸면 종목 상세와 뉴스가 같은 symbol로 함께 바뀐다.
- 관심종목이 0개여도 포트폴리오와 알림 영역은 독립적으로 표시된다.
- 포트폴리오 API 실패 시 고정 수익금이 대신 나타나지 않고 해당 카드만 오류 상태가 된다.
- KRW와 USD 보유가 있으면 통화별 카드로 표시되고 임의 합산 총액은 없다.
- 일부 시장 가격이 없거나 오래되면 해당 상태와 기준 시각이 보인다.
- ADMIN 전용 영역은 USER에게 렌더링되지 않고 서버도 403으로 보호한다.
- 모바일과 데스크톱 모두 loading, empty, error, success 상태를 확인할 수 있고 색상 외 텍스트로 등락·상태를 구분한다.

## 7. 구현 순서와 문서 동기화

1. portfolio_holdings 마이그레이션, 도메인·서비스·API, 계산 단위 테스트를 먼저 구현한다.
2. price_alerts 마이그레이션과 CRUD를 구현한 뒤 가격 수집 이벤트에 평가기를 연결한다.
3. 대시보드 고정 포트폴리오 값을 제거하고 포트폴리오·알림 API를 연결한다.
4. AI 뉴스 symbol 고정값을 selectedSymbol로 교체한다.
5. 구현 시 03_API_SPEC.md의 요청·응답 예시와 04_DB_SCHEMA.md의 실제 Flyway 상태를 함께 갱신한다.
6. 이 문서의 오류 코드를 공통 ApiErrorResponse로 일관되게 매핑한다.
