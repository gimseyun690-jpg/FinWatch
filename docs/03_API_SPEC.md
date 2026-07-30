# FinWatch API 명세

상태: Living Spec v0.2. 구현과 같은 커밋에서 계약을 갱신한다.

## 1. 공통 규칙

- Base URL: `/api/v1`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC 문자열
- 금액: JSON number와 `currency`를 함께 제공
- 브라우저 인증: Redis 불투명 세션을 가리키는 `FW_SESSION` HttpOnly cookie
- 변경 요청: 쿠키 인증 시 `XSRF-TOKEN` cookie 값을 `X-XSRF-TOKEN` header로 전달
- 기존 Bearer JWT: 로컬·테스트 호환 기간에만 허용하며 공개 브라우저는 사용하지 않음

### 성공 응답

```json
{
  "success": true,
  "data": {},
  "message": "요청 성공",
  "timestamp": "2026-07-13T03:00:00Z"
}
```

### 오류 응답

```json
{
  "success": false,
  "code": "STOCK_NOT_FOUND",
  "message": "종목을 찾을 수 없습니다.",
  "fieldErrors": [],
  "timestamp": "2026-07-13T03:00:00Z"
}
```

## 2. 인증

### `POST /auth/login`

```json
{
  "email": "user@finwatch.local",
  "password": "demo-password"
}
```

로컬 데모 계정:

- USER: `user@finwatch.local` / `FinWatch123!`
- ADMIN: `admin@finwatch.local` / `FinWatchAdmin123!`

로그인 성공 시 Access Token을 본문으로 반환하지 않고 `FW_SESSION` HttpOnly cookie를 발급한다. `/api/v1/admin/**`는 `ADMIN` 역할만 접근할 수 있다. 세션 기본 idle TTL은 1시간, absolute TTL은 8시간이다.

```json
{
  "success": true,
  "data": {
    "authenticated": true,
    "expiresAt": "2026-07-17T12:00:00Z",
    "user": {
      "id": 1,
      "displayName": "user",
      "email": "user@finwatch.local",
      "profileImageUrl": null,
      "role": "USER",
      "authProvider": "LOCAL"
    }
  },
  "message": "로그인 성공",
  "timestamp": "2026-07-13T03:00:00Z"
}
```

추가 인증 endpoint:

```text
GET    /auth/kakao/status
GET    /auth/kakao/authorize?returnTo=/dashboard
GET    /auth/kakao/callback
GET    /auth/session
POST   /auth/logout
DELETE /account
```

카카오 callback은 provider token을 응답·쿠키·DB에 저장하지 않고 FinWatch 세션으로 교환한 뒤 검증된 내부 route로 303 이동한다.

## 3. 종목과 시장 데이터

### 종목 검색 — V14 로컬 카탈로그 구현

`GET /stocks`는 가격 데이터가 준비된 기존 호환 목록을 반환한다. 전체 마스터 검색은 외부 공급자를 호출하지 않고 V14 로컬 카탈로그에서 다음 endpoint로 처리한다.

```text
GET /stocks/search?q=하이닉스&market=KRX&type=STOCK&page=0&size=10
GET /stocks/{market}/{symbol}
GET /stocks/{market}/{symbol}/prices?period=3M&interval=1D
GET /stocks/{market}/{symbol}/technical
POST /stocks/{market}/{symbol}/data-loads
GET /stocks/{market}/{symbol}/data-loads/{jobId}
```

검색 응답, 종목 식별, 온디맨드 수집, 기존 symbol 단독 endpoint의 호환·오류 계약은 `14_STOCK_DISCOVERY_SPEC.md`를 단일 상세 기준으로 한다.

### `POST /stocks/{market}/{symbol}/data-loads`

```json
{
  "resources": ["QUOTE", "DAILY_PRICES", "NEWS", "DISCLOSURES"]
}
```

- 이미 신선한 데이터 또는 완성된 DEMO fixture: `200 READY`
- 새 LIVE 수집 시작 또는 동일 요청 진행 중: `202 SYNCING`
- 동일한 `(market, symbol, resources)` 동시 요청은 같은 `jobId`를 재사용한다.
- 완료 상태는 `GET /stocks/{market}/{symbol}/data-loads/{jobId}`로 조회한다.
- quote·일봉·뉴스·공시의 부분 실패는 `PARTIAL`로 반환하고 성공한 리소스를 유지한다.
- `DISCLOSURES`는 KRX 종목에서 Open DART, NASDAQ·NYSE 종목에서 SEC EDGAR를 사용한다.

```json
{
  "jobId": "5dcf0e48-5d72-4b24-9c76-94b32dd0c669",
  "market": "KRX",
  "symbol": "005930",
  "status": "SYNCING",
  "reused": false,
  "startedAt": "2026-07-14T13:00:00Z",
  "finishedAt": null,
  "resources": [
    { "resource": "QUOTE", "status": "PENDING", "provider": null, "imported": 0 }
  ]
}
```

### `GET /stocks/{market}/{symbol}/disclosures`

저장된 공식 공시를 최신순으로 반환한다. 목록이 비어 있거나 최신 동기화가 필요하면 위 data-load API에 `DISCLOSURES`를 요청한다.

```json
{
  "success": true,
  "data": [
    {
      "id": 301,
      "market": "KRX",
      "symbol": "005930",
      "title": "분기보고서 (2026.06)",
      "publisher": "삼성전자",
      "url": "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260814000001",
      "publishedAt": "2026-08-14T00:00:00Z",
      "source": "OPENDART",
      "disclosureType": "A",
      "aiAnalysisAllowed": false,
      "fetchedAt": "2026-08-14T01:00:00Z"
    }
  ],
  "message": "종목 공시 목록 조회 성공"
}
```

### `GET /stocks/{symbol}`

```json
{
  "success": true,
  "data": {
    "symbol": "000660",
    "name": "SK하이닉스",
    "market": "KRX",
    "currency": "KRW",
    "price": 2723000,
    "change": 38200,
    "changeRate": 1.42,
    "volume": 3870000,
    "high": 2891000,
    "low": 2688000,
    "asOf": "2026-07-13T02:30:00Z",
    "source": "DEMO"
  },
  "message": "종목 상세 조회 성공",
  "timestamp": "2026-07-13T03:00:00Z"
}
```

### `GET /stocks/{market}/{symbol}/prices?period=3M&interval=1D`

- `period`: `1M`, `3M`, `6M`, `1Y`, `ALL`
- `interval`: `1D`(일봉), `1W`(주봉), `1M`(월봉)

서버는 DB에 저장된 실제 `1D` OHLCV를 거래소 현지 시간 기준으로 주봉·월봉 집계한다. 주봉은 월요일부터 시작하며 월봉은 해당 달의 첫 시가, 최고 고가, 최저 저가, 마지막 종가, 거래량 합계를 사용한다. `source`는 `KIS`, `KIS_OVERSEAS`, `FINNHUB`, `DEMO`, `MIXED` 중 실제 저장 출처를 반환한다.

```json
{
  "symbol": "MSFT",
  "interval": "1W",
  "period": "1Y",
  "source": "KIS_OVERSEAS",
  "items": []
}
```

`data.items`는 `{ "time", "open", "high", "low", "close", "volume", "indicators" }` 배열이다. `indicators`에는 선택한 봉 간격으로 계산한 `ma5`, `ma20`, `ma60`, `volumeMa20`, 볼린저 상·중·하단, Wilder `rsi`, MACD 세 값과 `atr`이 들어간다. 계산 전 구간의 값은 `null`이다.

### `GET /stocks/{symbol}/technical`

계산식, 입력 품질, 신호 판정, 오류와 상세 차트의 기간 계약은 `11_TECHNICAL_ANALYSIS_SPEC.md`를 따른다.

```json
{
  "success": true,
  "data": {
    "symbol": "000660",
    "calculatedAt": "2026-07-13T02:30:00Z",
    "calculationVersion": "technical-v2-wilder",
    "summarySignal": "BUY",
    "movingAverages": {
      "ma5": 2701000,
      "ma20": 2689000,
      "ma60": 2612000,
      "signal": "BUY"
    },
    "rsi": { "period": 14, "method": "WILDER", "value": 68.4, "signal": "NEUTRAL" },
    "macd": { "value": 18320.5, "signalLine": 14210.1, "histogram": 4110.4, "signal": "BUY" },
    "bollingerBands": {
      "period": 20,
      "deviationMultiplier": 2,
      "upper": 2750000,
      "middle": 2689000,
      "lower": 2628000,
      "bandwidthPercent": 4.5370
    },
    "atr": { "period": 14, "value": 42850.7, "percent": 1.5744 },
    "volumeMa20": 3198500,
    "events": [
      { "time": "2026-07-08T06:00:00Z", "type": "MA_GOLDEN_CROSS", "signal": "BUY" }
    ],
    "disclaimer": "기술적 신호는 투자 권유가 아닌 참고 정보입니다."
  },
  "message": "기술적 분석 조회 성공",
  "timestamp": "2026-07-13T03:00:00Z"
}
```

### 환율 — 구현됨

```text
GET /market/fx-rates/USD/KRW
GET /market/fx-rates/USD/KRW/history?period=1M&interval=1D
GET /market/fx-rates/pairs
```

환율 방향·출처·freshness, 포트폴리오 KRW 환산과 오류 계약은 `15_FX_RATE_SPEC.md`를 단일 상세 기준으로 한다. LIVE 모드에서는 공급자 시각이 포함된 Finnhub WebSocket `OANDA:USD_KRW` 체결만 `rateType=LIVE`로 사용한다. 해당 스트림이 없거나 오래되면 Finnhub REST 또는 Frankfurter의 검증된 `REFERENCE` 값으로 내려가며, 화면은 visible·online 상태에서 30초마다 갱신하고 실패하면 마지막 정상값을 stale 상태로 유지한다.

### `GET /stocks/realtime`

인증 사용자가 현재 인메모리 실시간 시세와 공급자 연결 상태를 진단하는 스냅샷 API다. 브라우저의 연속 갱신은 REST polling이 아니라 `/ws/quotes`를 사용한다.

```json
{
  "success": true,
  "data": {
    "quotes": [
      {
        "symbol": "000660",
        "price": 1763000,
        "change": -82000,
        "changeRate": -4.44,
        "volume": 4607000,
        "currency": "KRW",
        "asOf": "2026-07-14T02:32:08Z",
        "source": "KIS_WS",
        "sessionStatus": "LIVE"
      }
    ],
    "providers": [
      { "provider": "KIS", "state": "CONNECTED", "message": "2개 KRX 종목 체결 구독 중", "updatedAt": "2026-07-14T02:28:09Z" }
    ]
  },
  "message": "실시간 시세 연결 상태 조회 성공"
}
```

### `WS /ws/quotes`

연결 직후 `snapshot`, 유효한 최신 USD/KRW가 있으면 별도 `fx`, 현재 장중 봉의 `candles`를 전송하고 이후 변경마다 `quote`, `candle`, `fx` 또는 `status` 이벤트를 전송한다. 프런트엔드는 지수 백오프로 자동 재연결하고 새 연결의 snapshot과 최신 `fx` 이벤트로 상태를 복구한다. `sessionStatus=LIVE`와 REST 초기값 `SNAPSHOT`은 내부 신선도·진단 상태로 구분하되, 일반 사용자 종목 카드에는 `TICK`이나 공급자 코드 같은 구현 상세를 반복 표시하지 않고 가격·등락·차트만 제자리 갱신한다.

브라우저는 선택 종목이 바뀔 때 다음 메시지를 보낸다. 서버는 현재 세션 선택 종목을 최우선으로 두고 관심종목·보유종목·활성 알림을 합쳐 공급자별 구독 상한 안에서 동적으로 구독·해제한다.

```json
{ "type": "select", "market": "KRX", "symbol": "005930" }
{ "type": "subscription", "data": { "market": "KRX", "symbol": "005930" } }
{ "type": "quote", "data": { "symbol": "005930", "price": 255000, "source": "KIS_WS", "sessionStatus": "LIVE", "asOf": "2026-07-14T02:32:08Z" } }
{ "type": "candle", "data": { "symbol": "005930", "time": "2026-07-14T02:32:00Z", "open": 254500, "high": 255000, "low": 254000, "close": 255000, "volume": 1820, "currency": "KRW", "source": "KIS_WS" } }
```

### `GET /stocks/{symbol}/intraday?limit=390`

현재 백엔드 프로세스가 WebSocket 체결로 집계한 1분 OHLCV를 시간순으로 반환한다. `limit`은 1~600이며 기본값은 390이다. KIS 누적 거래량은 직전 틱과의 차이를 사용하고 Finnhub 체결 거래량은 같은 분 안에서 합산한다. REST 초기 현재가와 폐장 snapshot은 봉을 생성하지 않으며, 서버 재시작 전 데이터만 제공하므로 빈 배열도 정상 응답이다.

```json
{
  "symbol": "005930",
  "interval": "1m",
  "period": "SESSION",
  "items": [
    { "time": "2026-07-14T02:32:00Z", "open": 254500, "high": 255000, "low": 254000, "close": 255000, "volume": 1820, "indicators": null }
  ]
}
```

### `POST /admin/data/sync`

### `POST /admin/data/stocks/{symbol}/sync`

ADMIN 전용 외부 데이터 동기화 API다. `DATA_MODE=DEMO`에서는 공급자를 호출하지 않고 `SKIPPED`, `LIVE`에서는 KRX 종목의 KIS 일봉·NAVER API HUB 뉴스와 미국 종목의 Finnhub 뉴스를 DB에 중복 없이 저장한다. 공급자 오류는 기존 DB 데이터를 삭제하지 않고 해당 결과를 `FALLBACK`으로 반환한다.

### `POST /admin/data/catalogs/sync`

### `POST /admin/data/catalogs/{provider}/sync`

ADMIN 전용 종목 마스터 동기화 API다. `provider`는 `KIS_MASTER` 또는 `FINNHUB_SYMBOLS`다. DEMO에서는 외부 호출 없이 `SKIPPED`하며, LIVE에서는 KIS 코스피·코스닥 마스터와 Finnhub 미국 심볼을 로컬 `stocks`에 upsert한다. 빈 응답, 최소 종목 수 미달, 기존 활성 종목 대비 급감, 중복 식별자가 감지되면 기존 카탈로그를 그대로 유지하고 sync run을 `FAILED`로 기록한다.

```json
{
  "success": true,
  "data": {
    "mode": "LIVE",
    "startedAt": "2026-07-13T03:00:00Z",
    "finishedAt": "2026-07-13T03:00:02Z",
    "pricesImported": 2,
    "newsImported": 12,
    "stocks": [
      {
        "symbol": "005930",
        "market": "KRX",
        "marketPrices": { "provider": "KIS", "status": "SUCCESS", "imported": 2, "message": "동기화 완료" },
        "news": { "provider": "NAVER_API_HUB", "status": "SUCCESS", "imported": 12, "message": "동기화 완료" }
      }
    ]
  },
  "message": "외부 데이터 동기화가 완료되었습니다."
}
```

진단용 `GET /providers/kis/**`, `GET /providers/naver/news`, `GET /providers/finnhub/news`도 ADMIN만 접근할 수 있다.

## 4. 관심종목

관심종목은 인증 사용자별로 격리한다. 동일 사용자가 같은 종목을 두 번 등록하면 `409 WATCHLIST_DUPLICATED`, 자신의 목록에 없는 종목을 삭제하면 `404 WATCHLIST_NOT_FOUND`를 반환한다.

### `GET /watchlists`

`data`는 다음 항목의 배열이다.

```json
{
  "id": 1,
  "symbol": "000660",
  "name": "SK하이닉스",
  "market": "KRX",
  "currency": "KRW",
  "price": 2723000,
  "change": 38200,
  "changeRate": 1.42,
  "asOf": "2026-07-13T02:30:00Z",
  "source": "DEMO",
  "dataAvailability": "READY",
  "addedAt": "2026-07-13T03:00:00Z"
}
```

카탈로그 메타데이터만 준비된 종목도 관심종목에 등록할 수 있다. 이 경우 `price`, `change`, `changeRate`, `asOf`, `source`는 `null`일 수 있고 `dataAvailability=METADATA_ONLY`다. REST quote가 먼저 준비되면 `PARTIAL`, 일봉까지 준비되면 `READY`로 전환한다.

### `POST /watchlists`

```json
{ "market": "KRX", "symbol": "000660" }
```

`symbol`은 필수이며 최대 30자, `market`은 전체 카탈로그에서 같은 symbol을 안전하게 구분하기 위한 권장 필드다. 기존 클라이언트를 위해 market 없는 요청도 symbol이 전체 카탈로그에서 유일할 때만 허용한다. 서버는 앞뒤 공백을 제거하고 영문 기호를 대문자로 정규화한다. 성공 시 `201 Created`와 생성된 관심종목 항목을 반환하고 커밋 직후 실시간 구독 집합 갱신과 quote·일봉·뉴스·공시 준비를 시작한다. 사용자별 등록 개수 제한은 없다.

### `DELETE /watchlists/{market}/{symbol}`

성공 시 `200 OK`와 `data: null`을 반환한다.

`DELETE /watchlists/{symbol}`은 이전 클라이언트 호환용이며 symbol이 시장 간 중복될 수 있는 신규 화면은 canonical 경로를 사용한다.

모든 요청은 JWT의 `userId`를 기준으로 현재 로그인 사용자의 데이터만 처리한다. 목록과 등록 응답 항목은 아래 필드를 포함한다.

```json
{
  "id": 1,
  "symbol": "000660",
  "name": "SK하이닉스",
  "market": "KRX",
  "currency": "KRW",
  "price": 2723000,
  "change": 38200,
  "changeRate": 1.42,
  "asOf": "2026-07-13T02:30:00Z",
  "source": "DEMO",
  "dataAvailability": "READY",
  "addedAt": "2026-07-13T03:00:00Z"
}
```

등록 성공은 201, 중복 종목은 `409 WATCHLIST_DUPLICATED`, 현재 사용자의 목록에 없는 종목 삭제는 `404 WATCHLIST_NOT_FOUND`를 반환한다.

## 5. 포트폴리오

- `GET /portfolios`
- `POST /portfolios/holdings`
- `PATCH /portfolios/holdings/{holdingId}`
- `DELETE /portfolios/holdings/{holdingId}`

보유 종목 입력 예시:

```json
{
  "symbol": "000660",
  "quantity": 10,
  "averagePurchasePrice": 2500000,
  "currency": "KRW"
}
```

포트폴리오 응답은 `totalPurchaseAmount`, `totalEvaluationAmount`, `profitLoss`, `returnRate`, `holdings`를 포함한다. 평가 가격은 인메모리 실시간 시세와 DB 최신 가격의 `asOf`를 비교해 더 새로운 값을 사용하며, 응답의 `priceSource`와 `priceAsOf`로 근거를 제공한다.

화면은 `conversionComplete=true`이고 기준통화 평가액이 유효할 때 원화 환산 평가액 기준의 종목별 원형 자산배분 그래프를 표시한다. 상위 7개 종목과 나머지 합계만 시각화하며, 환산이 불완전하면 부분 합계를 전체 비중처럼 그리지 않는다.

### `POST /ai/portfolio-evaluations` — 구현됨

요청 본문은 선택적인 `promptVersion`만 받는다. 서버는 JWT 사용자의 최신 포트폴리오를 다시 조회해 원화 평가액, 상위 종목 비중, HHI, 통화 노출과 데이터 한계를 계산한다. 클라이언트가 보유 비중이나 수익률을 임의로 제출하지 않는다.

응답은 `balanceStatus`, headline·summary, 분산도·집중도·통화 노출·성과 맥락, 강점·위험·정기 점검 항목, `P/C/FX/H` evidence와 모델·캐시·토큰·비용 감사 정보를 포함한다. 동일 보유 구성의 15분 평가 창은 Redis·DB에서 재사용하며 HIT는 실제 토큰·비용 0으로 기록한다. 존재하지 않는 근거 ID, 입력에서 추적할 수 없는 숫자, 목표가·수익률 예측과 직접적인 매수·매도·교체 권고는 거부한다.

## 6. 가격 알림

- `GET /alerts`
- `POST /alerts`
- `PATCH /alerts/{alertId}`
- `DELETE /alerts/{alertId}`

```json
{
  "symbol": "000660",
  "condition": "ABOVE",
  "targetPrice": 2800000,
  "currency": "KRW"
}
```

알림 상태는 `ACTIVE`, `TRIGGERED`, `DISABLED` 중 하나다. KIS/Finnhub 틱 수신기는 종목별 최신 틱을 250ms 단위로 병합하고 조건을 충족한 ACTIVE 알림만 조회해 `TRIGGERED`로 전환한다. 목록 조회·생성·수정 때도 같은 최신 가격 선택 정책으로 즉시 평가한다.

## 7. 뉴스

### `GET /stocks/{symbol}/news`

뉴스 항목은 `id`, `symbol`, `title`, `publisher`, `url`, `publishedAt`, `summaryAvailable`과 다음 출처 정책 필드를 포함한다.

- `source`: 데이터를 제공한 공급자 또는 `DEMO`
- `contentSource`: `PROVIDER_SUMMARY`, `ALLOWLIST_ARTICLE`, `ON_DEMAND_ARTICLE`, `OFFICIAL_DISCLOSURE`, `METADATA_ONLY`
- `rightsProfile`: `METADATA_ONLY`, `TRANSIENT_AI`, `STORE_FOR_AI`, `STORE_AND_DISPLAY`
- `aiAnalysisAllowed`: 현재 저장된 입력을 즉시 AI 분석에 사용할 수 있는지 여부. 일반 뉴스가 `false`여도 안전한 원문 URL이 있으면 요약 요청 시 on-demand 수집 후 분석할 수 있다.
- `fetchedAt`: 본문 또는 공급자 입력 수집 시각, 미수집이면 `null`

현재 MVP는 종목의 뉴스 전체 목록을 최신순으로 반환한다. `page`와 `size` 페이지네이션은 외부 뉴스 수집을 연결할 때 추가하며, 추가 전에는 해당 쿼리 계약을 지원한다고 간주하지 않는다.

### `GET /content-feed` — Phase A 구현됨

뉴스·공시 통합 목록의 신규 UI는 다음 서버 pagination endpoint를 사용한다.

```text
GET /content-feed?kind=ALL&market=KRX&symbol=005930&period=1M&analysis=AI_ALLOWED&page=0&size=20&sort=publishedAt,desc
```

응답은 `items`, `page`, `size`, `totalElements`, `totalPages`, `hasPrevious`, `hasNext`, `sort`를 포함한다. 기존 종목별 뉴스 배열 endpoint는 호환 기간 동안 유지하며 filter·정렬·오류의 단일 상세 기준은 `17_NAVIGATION_AND_CONTENT_LIST_SPEC.md`다.

- `kind`: `ALL`, `NEWS`, `DISCLOSURE`
- `market`: `ALL`, `KRX`, `NASDAQ`, `NYSE`; `symbol`은 특정 market과 함께 사용한다.
- `period`: `24H`, `7D`, `1M`, `3M`, `CUSTOM`; CUSTOM은 ISO date `from`, `to`가 필수다.
- `analysis`: `ALL`, `METADATA_ONLY`, `AI_ALLOWED`, `AI_COMPLETED`
- `page`: 0 이상, `size`: 1~50
- `sort`: `publishedAt,desc`, `publishedAt,asc`만 허용하며 같은 시각에는 같은 방향의 `id`를 tie-breaker로 사용한다.
- 오류 code: `CONTENT_FILTER_INVALID`, `PAGE_INVALID`, `SORT_NOT_ALLOWED`, `STOCK_NOT_FOUND`

목록 item은 콘텐츠 출처·권리, AI 분석 가능 여부, `AVAILABLE|COMPLETED|UNAVAILABLE` 상태와 최대 180자의 `summaryPreview`를 반환한다. 인증 없는 요청은 401이다.

### `GET /news/{newsId}` — 구현됨

원문 제공 권한에 따라 본문 대신 외부 링크와 수집된 메타데이터만 반환한다. `rightsProfile=STORE_AND_DISPLAY`일 때만 `contentDisplayAllowed=true`와 본문을 제공하며 나머지는 `content=null`이다. 없는 ID는 `404 NEWS_NOT_FOUND`다.

## 8. AI 뉴스 요약

### `POST /ai/news-summaries`

```json
{
  "newsId": 1042,
  "promptVersion": "news-analysis-v2"
}
```

```json
{
  "success": true,
  "data": {
    "analysisId": 501,
    "newsId": 1042,
    "symbol": "000660",
    "summary": "HBM 수요 확대와 메모리 업황 회복 기대가 핵심 이슈입니다.",
    "keyPoints": ["HBM 수요 증가", "메모리 업황 회복", "실적 개선 기대"],
    "positiveFactors": ["HBM 수요 증가", "고부가 제품 비중 확대"],
    "riskFactors": ["단기 주가 변동성", "공급 경쟁 심화 가능성"],
    "mentionedCompanies": ["SK하이닉스"],
    "evidenceSegments": ["S1"],
    "keywords": ["HBM", "메모리", "실적"],
    "sentiment": "POSITIVE",
    "modelName": "configured-model",
    "promptVersion": "news-analysis-v2",
    "analysisScope": "FULL_PROCESSED_TEXT",
    "originalCharacters": 1350,
    "processedCharacters": 1120,
    "providerCallCount": 1,
    "cacheHit": false,
    "inputTokens": 1200,
    "outputTokens": 250,
    "estimatedCost": 0.0021,
    "costCurrency": "USD",
    "responseTimeMs": 840,
    "generatedAt": "2026-07-13T03:00:00Z"
  },
  "message": "AI 뉴스 요약 완료",
  "timestamp": "2026-07-13T03:00:00Z"
}
```

캐시 적중 시 `cacheHit=true`, `estimatedCost=0`으로 응답하되 원래 생성 결과의 모델과 생성 시각을 유지한다.

긴 본문은 서버가 `S1`부터 시작하는 구간으로 나눠 공급자를 호출한다. `providerCallCount`는 실제 공급자 호출 수이며 토큰과 비용은 모든 구간의 합계다. `analysisScope=PARTIAL_PROCESSED_TEXT`이면 최대 구간 수 때문에 일부 전처리 본문만 분석했다는 의미다.

`aiAnalysisAllowed=false`이거나 전처리 가능한 본문이 없는 뉴스는 공급자를 호출하지 않고 `422`와 아래 오류를 반환한다.

저장된 본문이 없는 일반 뉴스는 서버가 `canonicalUrl`의 원문을 on-demand로 수집한다. HTTP 링크는 같은 호스트의 HTTPS로 승격해 시도하고, SSRF·포트·redirect·응답 크기·콘텐츠 형식 검증을 모두 통과한 본문만 전처리해 AI에 전달한다. `NEWS_CONTENT_UNAVAILABLE`은 원문 주소가 없거나, 명시적 차단 정책이 있거나, 수집 후 분석 가능한 본문이 없는 경우에만 반환한다.

### `POST /ai/disclosure-summaries` — 구현됨

공시 카드에서 사용하는 인증 사용자용 원클릭 API다. 대상이 `DISCLOSURE`인지 먼저 검증하고, `contentHash`가 없는 Open DART·SEC EDGAR 공시는 공식 원문을 서버에서 확보·정제한 뒤 기존 AI 뉴스 분석 파이프라인을 재사용한다. 이미 저장된 원문과 분석이 있으면 원문 공급자와 Gemini를 다시 호출하지 않고 contentHash 기반 DB·Redis 결과를 반환한다.

```json
{
  "disclosureId": 701
}
```

응답은 `POST /ai/news-summaries`와 동일한 `AiSummaryResponse`이며 공시 화면은 요약, 핵심 내용, 긍정·위험 요인, 근거 구간, 분석 범위, 모델·프롬프트·토큰·비용·캐시 정보를 표시한다. `promptVersion`은 회귀 테스트 등 특정 버전이 필요할 때만 선택적으로 전송하고 일반 클라이언트는 생략한다.

- `404 DISCLOSURE_NOT_FOUND`: 공시 ID가 없음
- `422 DISCLOSURE_REQUIRED`: 일반 뉴스 ID를 공시 API에 전달함
- `422 NEWS_CONTENT_UNAVAILABLE`: 허용된 공식 원문을 확보할 수 없음
- `503 OPENDART_API_KEY_REQUIRED`: Open DART 원문에 필요한 서버 키가 없음
- `429 PROVIDER_RATE_LIMITED`: 공식 원문 공급자 호출 한도 초과

## 8.1 AI 기술지표 해설 — 구현됨

### `POST /ai/technical-explanations`

클라이언트는 지표 값을 보내지 않고 `market`, `symbol`, `interval=1D`를 전송한다. `promptVersion`은 관리자·회귀 테스트처럼 특정 버전이 필요한 경우에만 선택적으로 전송하며, 일반 클라이언트는 생략하여 서버 프로필의 활성 버전을 사용한다. 서버가 최신 완성 봉과 기술지표를 다시 조회하여 Gemini 입력을 만든다.

```json
{
  "market": "KRX",
  "symbol": "000660",
  "interval": "1D"
}
```

응답은 대상·입력 버전, summary와 영역별 explanation, supporting/conflicting signals, riskNotes, dataLimitations, `I1..In` evidence, 모델·캐시·토큰·비용 정보를 포함한다. HIT는 토큰·실제 비용 0이며 원 생성 메타데이터를 유지한다.

전체 요청·응답 필드, evidence, 오류와 검증 계약의 단일 기준은 `12_AI_TECHNICAL_EXPLANATION_SPEC.md`다.

## 8.2 근거 기반 일일 변화 브리핑 — 구현됨

- `POST /ai/daily-change-briefings`: 최신·직전 완성 일봉과 비교 구간 뉴스·공시를 서버가 재조회하여 브리핑을 생성하거나 캐시에서 반환한다. 뉴스·공시 요약은 각 기능의 활성 프롬프트 버전을 사용한다.
- `GET /stocks/{symbol}/daily-change-briefings/latest`: 저장된 최신 브리핑을 모델 호출 없이 조회한다.

생성 요청은 `symbol`과 선택적인 `promptVersion`만 받는다. 응답은 거래일·baseline 상태, 정량 변화, 관점 매트릭스, 일치·충돌, `T/N/D/Q` evidence와 모델·버전·토큰·비용·캐시 감사 정보를 포함한다.

전체 API·근거·캐시·오류 계약의 단일 기준은 `13_AI_DAILY_CHANGE_BRIEFING_SPEC.md`다.

## 8.3 뉴스 원문 관리

### `POST /admin/news/{newsId}/content/refresh`

ADMIN 전용이다. 기사 출처 정책을 다시 확인하고 SEC EDGAR 허용 HTML 또는 Open DART 원문 API에서 본문을 가져와 저장한다. 동일 `contentHash`이면 기존 분석을 유지하고 `contentChanged=false`를 반환한다. hash가 바뀌면 이전 빠른 캐시를 삭제하고 새 hash의 분석 버전을 사용한다.

```json
{
  "success": true,
  "data": {
    "newsId": 1042,
    "contentChanged": true,
    "previousContentHash": "old-hash",
    "contentHash": "64-character-sha256",
    "contentSource": "OFFICIAL_DISCLOSURE",
    "rightsProfile": "STORE_FOR_AI",
    "canonicalUrl": "https://example.com/disclosure",
    "finalUrl": "https://example.com/disclosure",
    "extractorVersion": "jsoup-main-text-v1",
    "extractedCharacters": 4321,
    "fetchedAt": "2026-07-13T06:00:00Z"
  },
  "message": "뉴스 원문 수집 및 갱신 완료"
}
```

- 등록되지 않은 출처: `422 NEWS_CONTENT_UNAVAILABLE`
- Open DART 키 누락: `503 OPENDART_API_KEY_REQUIRED`
- 출처 호출 한도: `429 PROVIDER_RATE_LIMITED`
- USER 권한 호출: `403 ACCESS_DENIED`

## 9. 관리자 API

모든 관리자 API는 `ADMIN` 역할만 접근할 수 있다.

### `GET /admin/ai/metrics?from=2026-07-13&to=2026-07-13`

```json
{
  "success": true,
  "data": {
    "requestCount": 1284,
    "modelCallCount": 339,
    "cacheHitCount": 945,
    "inputTokens": 650000,
    "outputTokens": 214200,
    "totalTokens": 864200,
    "estimatedCost": 1.92,
    "cacheHitRate": 73.6,
    "savedEstimatedCost": 4.86,
    "averageResponseTimeMs": 214.8,
    "costCurrency": "USD",
    "featureUsage": [
      { "feature": "NEWS_SUMMARY", "requestCount": 1284, "modelCallCount": 339, "cacheHitCount": 945, "totalTokens": 864200, "estimatedCost": 1.92, "savedEstimatedCost": 4.86 },
      { "feature": "TECHNICAL_EXPLANATION", "requestCount": 426, "modelCallCount": 120, "cacheHitCount": 306, "totalTokens": 231400, "estimatedCost": 0.61, "savedEstimatedCost": 1.72 }
    ]
  },
  "message": "AI 운영 지표 조회 성공",
  "timestamp": "2026-07-13T03:00:00Z"
}
```

### `GET /admin/ai/usage-logs?page=0&size=20&sort=estimatedCost,desc`

로그 항목은 요청 ID, 기능, 대상 ID, 모델, 토큰, 비용, 절감액, 캐시 여부, 응답시간, 프롬프트 버전과 생성 시각을 포함한다. 응답 `data`는 `items`, `page`, `size`, `totalElements`, `totalPages`를 제공한다. `sort`는 `createdAt`, `estimatedCost`, `savedEstimatedCost`, `responseTimeMs`를 지원하며 방향을 생략하면 내림차순이다.

`/admin/**`는 JWT의 `roles` 클레임에 `ADMIN`이 있어야 접근할 수 있다. 인증이 없거나 토큰이 만료되면 401, USER 역할로 접근하면 403을 반환한다.

## 10. 상태 확인

### `GET /health`

```json
{
  "status": "UP",
  "timestamp": "2026-07-13T03:00:00Z"
}
```

## 11. 주요 오류 코드

| HTTP | 코드 | 의미 |
|---|---|---|
| 400 | VALIDATION_ERROR | 입력값 오류 |
| 401 | AUTHENTICATION_REQUIRED | 인증 필요 |
| 403 | ACCESS_DENIED | 권한 부족 |
| 404 | STOCK_NOT_FOUND | 종목 없음 |
| 404 | NEWS_NOT_FOUND | 뉴스 없음 |
| 404 | WATCHLIST_NOT_FOUND | 관심종목 없음 또는 다른 사용자의 항목 |
| 409 | WATCHLIST_DUPLICATED | 이미 등록된 관심종목 |
| 429 | AI_RATE_LIMITED | AI 공급자 또는 서비스 사용 제한 |
| 502 | EXTERNAL_PROVIDER_ERROR | 외부 데이터/AI 공급자 오류 |
