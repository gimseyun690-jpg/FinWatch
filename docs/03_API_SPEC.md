# FinWatch API 명세

상태: Living Spec v0.2. 구현과 같은 커밋에서 계약을 갱신한다.

## 1. 공통 규칙

- Base URL: `/api/v1`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC 문자열
- 금액: JSON number와 `currency`를 함께 제공
- 인증: 로그인과 상태 확인을 제외하고 `Authorization: Bearer <token>` 필수

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

로그인과 상태 확인을 제외한 `/api/v1/**` 요청은 `Authorization: Bearer <accessToken>` 헤더가 필요하다. `/api/v1/admin/**`는 `ADMIN` 역할만 접근할 수 있으며 토큰 유효시간 기본값은 1시간이다.

```json
{
  "success": true,
  "data": {
    "accessToken": "token",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": { "id": 1, "email": "user@finwatch.local", "role": "USER" }
  },
  "message": "로그인 성공",
  "timestamp": "2026-07-13T03:00:00Z"
}
```

## 3. 종목과 시장 데이터

### `GET /stocks?query=하이닉스&market=KRX&page=0&size=20`

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

### `GET /stocks/{symbol}/prices?period=3M&interval=1D`

`data.items`는 `{ "time", "open", "high", "low", "close", "volume", "indicators" }` 배열이다. `indicators`에는 같은 입력 스냅샷으로 계산한 `ma5`, `ma20`, `ma60`, `volumeMa20`, 볼린저 상·중·하단, Wilder `rsi`, MACD 세 값과 `atr`이 들어간다. 계산 전 구간의 값은 `null`이다.

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

### `POST /admin/data/sync`

### `POST /admin/data/stocks/{symbol}/sync`

ADMIN 전용 외부 데이터 동기화 API다. `DATA_MODE=DEMO`에서는 공급자를 호출하지 않고 `SKIPPED`, `LIVE`에서는 KRX 종목의 KIS 일봉·NAVER API HUB 뉴스와 미국 종목의 Finnhub 뉴스를 DB에 중복 없이 저장한다. 공급자 오류는 기존 DB 데이터를 삭제하지 않고 해당 결과를 `FALLBACK`으로 반환한다.

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
  "addedAt": "2026-07-13T03:00:00Z"
}
```

### `POST /watchlists`

```json
{ "symbol": "000660" }
```

`symbol`은 필수이며 최대 30자다. 서버는 앞뒤 공백을 제거하고 영문 기호를 대문자로 정규화한다. 성공 시 `201 Created`와 생성된 관심종목 항목을 반환한다.

### `DELETE /watchlists/{symbol}`

성공 시 `200 OK`와 `data: null`을 반환한다.

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

포트폴리오 응답은 `totalPurchaseAmount`, `totalEvaluationAmount`, `profitLoss`, `returnRate`, `holdings`를 포함한다.

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

알림 상태는 `ACTIVE`, `TRIGGERED`, `DISABLED` 중 하나다.

## 7. 뉴스

### `GET /stocks/{symbol}/news`

뉴스 항목은 `id`, `symbol`, `title`, `publisher`, `url`, `publishedAt`, `summaryAvailable`과 다음 출처 정책 필드를 포함한다.

- `source`: 데이터를 제공한 공급자 또는 `DEMO`
- `contentSource`: `PROVIDER_SUMMARY`, `ALLOWLIST_ARTICLE`, `OFFICIAL_DISCLOSURE`, `METADATA_ONLY`
- `rightsProfile`: `METADATA_ONLY`, `TRANSIENT_AI`, `STORE_FOR_AI`, `STORE_AND_DISPLAY`
- `aiAnalysisAllowed`: 현재 저장된 입력을 AI 분석에 사용할 수 있는지 여부
- `fetchedAt`: 본문 또는 공급자 입력 수집 시각, 미수집이면 `null`

현재 MVP는 종목의 뉴스 전체 목록을 최신순으로 반환한다. `page`와 `size` 페이지네이션은 외부 뉴스 수집을 연결할 때 추가하며, 추가 전에는 해당 쿼리 계약을 지원한다고 간주하지 않는다.

### `GET /news/{newsId}` — 계획, 미구현

원문 제공 권한에 따라 본문 대신 외부 링크와 수집된 메타데이터만 반환할 수 있다.

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

```json
{
  "success": false,
  "code": "NEWS_CONTENT_UNAVAILABLE",
  "message": "이 출처는 AI 분석에 사용할 수 있는 뉴스 본문을 제공하지 않습니다.",
  "fieldErrors": [],
  "timestamp": "2026-07-13T03:00:00Z"
}
```

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
      { "feature": "NEWS_SUMMARY", "requestCount": 1284 }
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
