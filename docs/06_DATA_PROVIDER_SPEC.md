# FinWatch 외부 데이터 공급자 명세

상태: v0.3. 공급자 조합과 로컬 LIVE PoC는 구현했으며, 운영 배포 전 이용약관·시세 공개 표시 권한·호출 한도 확인은 게이트로 남아 있다.

## 1. 목적과 범위

이 문서는 다음 범위를 다룬다.

- 종목 기본정보, 현재가와 일봉 OHLCV를 제공하는 `MarketDataProvider`
- 종목 관련 뉴스와 요약 가능한 원문을 제공하는 `NewsProvider`
- 외부 데이터를 내부 종목과 연결하고 정규화·중복 제거·저장하는 수집 파이프라인
- 시장 시간, 데이터 신선도, 저작권, 장애와 호출 한도에 대한 운영 규칙
- `DEMO`에서 실제 공급자로 전환하기 위한 결정 게이트와 인수 조건

AI 모델 공급자와 프롬프트 운영은 이 문서의 범위가 아니다. 단, 뉴스 원문을 AI에 전달할 권리와 원문이 없을 때의 동작은 이 문서에서 정한다.

FinWatch는 거래 체결용 시세 시스템이 아니다. 화면에 표시하는 가격은 실시간 또는 지연 여부를 명확히 표시하는 투자정보이며 주문, 체결 또는 투자 권유에 사용하지 않는다.

## 2. 현재 상태와 목표 상태

### 현재 구현

- `DATA_MODE=DEMO`에서는 Flyway 데모 데이터만 사용하고 `LIVE`에서는 KIS 국내 일봉과 NAVER API HUB·Finnhub 뉴스를 수동 동기화한다.
- KIS REST 현재가와 `H0STCNT0` 국내 체결, Finnhub Quote와 trade WebSocket을 서버에서 구독한다.
- 실시간 틱은 별도 DB 행을 계속 만들지 않고 인메모리 허브의 종목별 최신 값만 교체한다. 종목·관심종목 REST 응답도 유효한 허브 값을 우선한다.
- 브라우저는 `/ws/quotes`에서 연결 직후 snapshot과 이후 quote/status 이벤트를 받고 자동 재연결한다.
- 가격 이력은 DB의 `1D`를 사용하고 선택 종목의 최신 캔들 close/high/low를 수신 틱으로 보정한다.
- 뉴스 한 건은 `news_articles.stock_id`로 종목 하나에 연결되며, AI 요약은 저장·분석 권한이 있는 content에만 실행한다.
- 전체 KRX·미국 종목 마스터의 로컬 검색과 선택 종목 온디맨드 수집 확장은 계획 상태이며 `14_STOCK_DISCOVERY_SPEC.md`를 따른다.
- USD/KRW 환율은 기존 Finnhub 키 재사용을 우선 검증하며 rate 방향·reference fallback·포트폴리오 환산은 `15_FX_RATE_SPEC.md`를 따른다.

### 목표 흐름

```text
Scheduler / 수동 Backfill
  -> 수집 오케스트레이터
  -> MarketDataProvider / NewsProvider
  -> 계약 검증
  -> 정규화와 종목 매핑
  -> 중복 제거와 멱등 Upsert
  -> PostgreSQL
  -> 조회 API
  -> 출처·기준 시각·신선도 표시
```

Provider 어댑터는 외부 응답을 내부 중립 DTO로 변환하는 역할만 한다. DB 저장, 종목 매핑, 재시도 정책, 저작권에 따른 본문 보관과 스케줄 제어는 애플리케이션 계층에서 수행한다.

## 3. 공급자 확정 원칙과 결정 게이트

포트폴리오 MVP의 공급자 조합은 3.1과 같이 확정한다. 공급자 이름을 확정한 것이 이용 권리와 운영 준비 승인을 의미하지는 않는다. 약관 원문과 실제 Sandbox/PoC 결과가 필수 조건을 충족하지 못하면 같은 인터페이스 뒤에서 대체 공급자를 다시 선정한다.

### 필수 통과 조건

다음 중 하나라도 충족하지 못하면 점수와 관계없이 탈락한다.

1. 서버 애플리케이션에서의 호출, 캐시와 화면 재표시 범위가 약관상 허용된다.
2. 뉴스 공급자는 저장, AI 입력, 파생 요약 생성의 허용 범위를 서면 약관으로 확인할 수 있다.
3. 안정적인 외부 종목 ID 또는 거래소와 심볼 조합을 제공한다.
4. 가격의 실시간·지연 여부, 기준 시각, 수정주가 정책을 문서로 제공한다.
5. 기사 ID, 원문 URL, 언론사, 발행 시각을 제공한다.
6. HTTPS와 서버 측 비밀키 사용을 지원하고 키를 프런트엔드에 노출할 필요가 없다.
7. 호출 한도와 초과 시 응답 규칙을 확인할 수 있다.

### 평가표

필수 조건을 통과한 후보만 아래 기준을 5점 척도로 평가한다. 가중 합계와 근거 링크, PoC 측정값을 ADR에 남긴다.

| 기준 | 가중치 | 확인 내용 |
|---|---:|---|
| 라이선스·AI 이용 권리 | 25 | 저장, 표시, 캐시, AI 처리, 파생 결과와 보관 기간 |
| 시장 범위·정확성 | 20 | KRX/NASDAQ 범위, OHLCV, 거래일, 정정, 기업행사 |
| 신선도 | 15 | 공표 지연, 갱신 주기, 발행-수집 지연 |
| API 품질 | 10 | 안정적 ID, 증분 조회, 페이지네이션, 오류 형식, 문서 |
| 한도·안정성 | 10 | 분·일 한도, 동시 호출, 상태 페이지, SLA |
| 총비용 | 10 | 예상 종목·사용자·호출량 기준 월 비용과 초과 비용 |
| 개발·운영 편의 | 5 | Sandbox, SDK 불필요성, 지원, 버전 정책 |
| 보안·개인정보 | 5 | 키 권한 분리, 회전, 로그 정책, 데이터 처리 지역 |
| 합계 | 100 | 최고점이 자동 선정되는 것은 아니며 필수 조건이 우선한다. |

### 결정 게이트

| Gate | 결정 내용 | 통과 증거 | 현재 상태 |
|---|---|---|---|
| DG-0 범위 | 운영 MVP는 KRX와 미국 시장을 함께 지원 | 지원 시장, 데이터 지연 허용치, 예상 관심종목 수 | 시장 범위 확정, 지연·종목 수 TBD |
| DG-1 법무 | 표시·저장·AI 처리·파생 요약·보관·삭제 권리 확인 | 약관 버전/URL, 확인일, 허용 범위 표 | TBD |
| DG-2 기술 PoC | 대표 종목의 식별자, 60거래일 이상 일봉, 뉴스 증분 수집 검증 | 원본 샘플, 매핑 결과, 누락·중복·지연 측정 | TBD |
| DG-3 비용·한도 | 정상 폴링과 장애 재시도를 포함한 월 호출량 산정 | 요금표, 호출 예산, 80%/95% 한도 대응 | TBD |
| DG-4 구현 준비 | 스키마, 출처·신선도 API, 데모 격리, 계약 테스트 완료 | 마이그레이션, 테스트 결과, 운영 설정표 | TBD |
| DG-5 운영 승인 | Secret 등록, 모니터링, 롤백과 장애 시나리오 검증 | 배포 체크리스트와 시연 기록 | TBD |

DG-0부터 DG-4까지 통과하기 전에는 운영 환경에서 `LIVE` 모드를 활성화하지 않는다. 뉴스 제공권이 메타데이터에만 한정되면 뉴스 목록은 라이브로 전환할 수 있지만 U-08 AI 뉴스 요약은 `DEMO`로 명확히 유지하거나 비활성화해야 한다.

### 확정 공급자 조합

| 내부 ID | 공급자 | 확정 용도 | 사용하지 않는 용도 |
|---|---|---|---|
| `kis` | 한국투자증권 KIS Open API | KRX 현재가, 일봉 OHLCV, `H0STCNT0` 실시간 체결 | 미국 시세, 뉴스, 주문·자동매매 |
| `naver-api-hub` | NAVER API HUB 뉴스 검색 | 국내 종목 뉴스 발견, 제목·description·원문 URL·발행 시각 | 언론사 본문 전문 제공 |
| `finnhub` | Finnhub Quote / WebSocket / Company News | 미국 현재가·실시간 체결과 미국 종목 뉴스 발견 | KRX 시세, 언론사 본문 전문 제공 |
| `opendart` | 금융감독원 Open DART | 국내 기업 공시 목록·원문과 구조화 재무·주요공시 | 일반 언론 뉴스 |
| `sec-edgar` | 미국 SEC EDGAR | 미국 기업 제출 문서·공시 원문 | 일반 언론 뉴스 |
| `official-web` | 승인된 기업 IR·뉴스룸·RSS | 기업 공식 보도자료·IR 본문 | 승인되지 않은 언론사·전체 웹 탐색 |

AI 공급자는 `GeminiAiProvider`, 개발·테스트 대역은 `MockAiProvider`로 확정하며 세부 계약은 `08_AI_OPERATION_SPEC.md`를 따른다.

선정 이유:

1. KIS는 국내 체결과 일봉, Finnhub는 무료 등급에서 확인 가능한 미국 Quote·trade 스트림을 담당하도록 시장별로 분리한다.
2. NAVER API HUB는 국내 뉴스 검색, Finnhub는 미국 회사 뉴스 발견에도 사용하여 검색 품질과 종목 연관성을 확보한다.
3. 검색 API가 제공하지 않는 전문은 Open DART·SEC EDGAR·기업 공식 출처부터 확보하여 Gemini의 실제 본문 분석 의미를 유지한다.
4. 언론사 전체 웹 크롤링과 주문 API는 포트폴리오 MVP 범위에서 제외해 법적·보안·운영 위험을 줄인다.

실시간 전달은 다음 구조로 고정한다.

```text
KIS H0STCNT0 WebSocket + Finnhub trade WebSocket
  -> Spring Boot provider adapters
  -> in-memory latest quote hub
  -> FinWatch /ws/quotes
  -> React 상세 차트·관심종목

KIS REST + Finnhub Quote
  -> 시작 시 현재가 snapshot

KIS REST daily bars
  -> 일봉 OHLCV 정규화
  -> PostgreSQL
  -> 기술적 분석·기간 차트
```

KIS App Secret과 WebSocket 접속키를 프런트엔드에 전달하지 않는다. FinWatch는 조회 전용 API만 구현하며 주문·잔고·자동매매 endpoint는 호출하지 않는다.

## 4. 데이터 모드와 DEMO 정책

### 모드

| 모드 | 목적 | 규칙 |
|---|---|---|
| `DEMO` | 로컬 개발, 테스트, 포트폴리오 시연 | 외부 API를 호출하지 않고 고정 데이터만 사용한다. |
| `LIVE` | 실제 공급자 데이터 사용 | 선정된 공급자만 호출하며 데모 데이터를 반환하지 않는다. |
| `MIXED` | 공급자별 전환 검증 | 로컬·스테이징에서만 허용하고 각 영역의 모드를 화면에 표시한다. |

`DEMO`가 기본값이다. 운영에서 `MIXED`를 사용하려면 `ALLOW_MIXED_DATA=true`를 명시하고, 가격과 뉴스 영역 각각에 `DEMO` 또는 공급자 출처를 표시해야 한다.

### DEMO 데이터 규칙

1. 네트워크와 API 키 없이 동일한 결과를 재현할 수 있어야 한다.
2. 모든 데모 가격과 뉴스의 `source`는 `DEMO`이다.
3. 화면에 `데모 데이터` 배지와 `asOf` 또는 발행 시각을 표시하며 현재 실시간 값처럼 표현하지 않는다.
4. 데모 시각이 과거이면 서버 시작 시각으로 임의 변경하지 않는다. 시연용 합성 이력은 실제 거래일 데이터라고 주장하지 않는다.
5. `LIVE` 장애 시 애플리케이션이 데모 데이터로 자동 전환해서는 안 된다. 마지막 정상 라이브 데이터를 `STALE`로 표시하거나 데이터 없음 오류를 반환한다.
6. 운영 DB에서는 `source='DEMO'` 행을 사용자 조회에서 제외한다.
7. 현재 일반 Flyway 마이그레이션에 포함된 데모 INSERT는 라이브 전환 전에 프로필별 시드, 별도 Flyway location 또는 후속 정리 마이그레이션으로 격리한다. 이미 적용된 마이그레이션 파일을 운영 이력에서 임의 수정하지 않는다.

## 5. Provider 계약

### 공통 계약

모든 어댑터는 다음 규칙을 지킨다.

- `providerId`는 설정과 DB `source`에서 사용하는 불변 식별자다. 업체 표시명이나 언론사명과 구분한다.
- 시간은 공급자 지역 시각과 offset을 해석한 뒤 UTC `Instant`로 반환한다.
- 가격과 수량은 부동소수점이 아닌 `BigDecimal`로 반환한다.
- 빈 문자열을 `0`으로 바꾸거나 알 수 없는 시각을 현재 시각으로 채우지 않는다.
- 원본 통화, 거래소, 지연 여부와 공급자 기준 시각을 보존한다.
- 어댑터가 DB Entity나 Repository에 의존하지 않는다.
- 공급자 4xx, 429, 5xx와 파싱 오류를 구분된 내부 오류로 변환한다.
- 원본 응답 전체를 일반 로그에 기록하지 않는다. 계약상 허용된 테스트 Fixture만 비밀정보를 제거해 저장한다.

공통 오류 분류는 `AUTHENTICATION`, `FORBIDDEN`, `NOT_FOUND`, `RATE_LIMITED`, `TIMEOUT`, `UPSTREAM_5XX`, `INVALID_RESPONSE`, `QUOTA_EXHAUSTED`를 사용한다. `retryable`, `retryAfter`, `providerRequestId`를 함께 전달할 수 있어야 한다.

### MarketDataProvider

목표 인터페이스는 다음 기능을 제공한다. 실제 메서드명은 구현 시 변경할 수 있지만 입출력 의미는 유지한다.

```text
searchInstruments(query, market, cursor) -> ProviderPage<ProviderInstrument>
getQuotes(providerInstrumentIds) -> List<ProviderQuote>
getBars(providerInstrumentId, interval, from, to, adjustmentMode) -> List<ProviderBar>
```

`ProviderInstrument` 필수 필드:

- `providerInstrumentId`, `symbol`, `name`
- `exchangeCode`, `market`, `currency`, `exchangeTimezone`
- 가능한 경우 `isin`, 종목 상태와 상장·상장폐지일

`ProviderQuote` 필수 필드:

- `providerInstrumentId`, `price`, `currency`, `asOf`
- 가능한 경우 `open`, `high`, `low`, `previousClose`, `volume`
- `sessionStatus`, `delaySeconds`, `providerId`, `fetchedAt`

`ProviderBar` 필수 필드:

- `providerInstrumentId`, `interval`, `sessionDate`
- `open`, `high`, `low`, `close`, `volume`
- `barStart`, `barEnd`, `isFinal`
- `adjustmentMode`, `providerId`, `fetchedAt`

호출 결과는 요청 구간의 정렬 순서를 보장하지 않는 것으로 간주하며 애플리케이션이 `sessionDate`로 정렬한다. 배치 호출 일부만 실패하면 성공 항목과 항목별 오류를 함께 반환하고 전체 성공으로 위장하지 않는다.

### NewsProvider

목표 인터페이스는 다음 기능을 제공한다.

```text
fetchArticles(markets, symbols, publishedAfter, cursor, pageSize) -> ProviderPage<ProviderNewsArticle>
getArticle(externalId) -> ProviderNewsArticle
```

`ProviderNewsArticle` 필수 필드:

- `externalId`, `title`, `publisher`, `canonicalUrl`
- `publishedAt`, 가능한 경우 `updatedAt`, `language`
- 공급자가 제공한 종목·기업 식별자 목록
- `excerpt`, `content` 또는 라이선스가 허용한 본문 조회 수단
- `providerId`, `fetchedAt`, `rightsProfile`

페이지 응답은 `nextCursor`와 `hasMore`를 제공한다. Cursor를 지원하지 않는 공급자는 `(publishedAt, externalId)`를 안정적으로 정렬할 수 있어야 하며, 애플리케이션이 중첩 조회 구간으로 누락을 보완한다.

### 뉴스 권리 프로필

| 프로필 | 저장 | AI 입력 | API/화면 표시 |
|---|---|---|---|
| `METADATA_ONLY` | 제목, 언론사, URL, 시각만 저장 | 금지 | 메타데이터와 원문 링크만 표시 |
| `TRANSIENT_AI` | 본문 영속 저장 금지 | 메모리 내 일회 처리만 허용 | 파생 요약 허용 범위만 표시 |
| `STORE_FOR_AI` | 계약상 보관 기간 내 저장 | 허용 | 본문은 노출하지 않고 요약·링크만 표시 |
| `STORE_AND_DISPLAY` | 계약상 보관 기간 내 저장 | 허용 여부를 별도 확인 | 허용된 분량의 본문과 출처 표시 |

현재 AI 흐름은 DB의 `news_articles.content`를 읽으므로 `TRANSIENT_AI`는 지원하지 않는다. 이 프로필을 선택하려면 원문을 저장하지 않는 동기 처리 흐름과 캐시·감사 정책을 먼저 구현해야 한다. `METADATA_ONLY` 기사에 요약을 요청하면 `422 NEWS_CONTENT_UNAVAILABLE`을 반환한다.

### 허용 출처 본문 수집기

포트폴리오 MVP는 뉴스 검색 API를 기사 발견과 메타데이터 수집에 사용하고, 전문 분석은 `ArticleContentFetcher`가 허용한 출처에서만 수행한다.

```text
NewsProvider
  -> 제목·설명·언론사·canonical URL·관련 종목
  -> SourcePolicyRegistry 확인
     |- METADATA_ONLY: 링크와 제공 설명만 저장
     |- API_CONTENT: 공급자 API가 제공한 본문 저장
     |- ALLOWLIST_FETCH: 허용된 URL만 ArticleContentFetcher 호출
     `- BLOCKED: 본문 수집·AI 분석 금지
  -> HTML 본문 추출·정규화
  -> content hash와 출처 정책 저장
  -> Gemini 분석
```

`ArticleContentFetcher`는 범용 웹 크롤러가 아니라 도메인·경로별 허용 목록을 적용하는 제한된 어댑터다. 초기 허용 후보는 다음과 같다.

- Open DART 공시 원문과 회사 공시
- SEC EDGAR 제출 문서
- 기업 공식 IR·뉴스룸·보도자료
- 명시적으로 자동 수집과 분석을 허용한 RSS/Atom 또는 웹사이트
- 별도 계약으로 본문 이용 권한을 확보한 뉴스 API

네이버·Finnhub 등 검색 결과에 URL이 있다는 사실만으로 해당 언론사 본문 수집을 허용하지 않는다. 도메인별 `source_policy`가 없으면 기본값은 `METADATA_ONLY`다.

목표 인터페이스:

```text
fetch(canonicalUrl, sourcePolicy) -> FetchedArticleContent
```

`FetchedArticleContent`는 최소 `canonicalUrl`, `finalUrl`, `title`, `extractedText`, `contentType`, `fetchedAt`, `etag`, `lastModified`, `contentHash`, `extractorVersion`을 포함한다.

## 6. 정규화와 검증

### 종목과 시장

- 내부 종목 키는 `(market, symbol)`이며 `symbol` 단독으로 전 세계 종목을 식별하지 않는다.
- 영문 심볼과 시장 코드는 대문자로 정규화한다. KRX 보통 종목 코드는 공급자 계약에 따라 6자리 앞자리 `0`을 보존한다.
- 공급자의 exchange 코드는 내부 `market` 코드로 명시적 매핑하며 알 수 없는 코드를 임의로 `KRX`나 `NASDAQ`으로 바꾸지 않는다.
- 통화는 ISO 4217 대문자 3자리로 검증한다.
- 거래소 시간대는 IANA ID를 사용한다. KRX는 `Asia/Seoul`, 미국 동부 거래소는 `America/New_York`로 해석해 일광절약시간을 반영한다.

가격 행은 저장 전에 다음을 검증한다.

```text
open, high, low, close > 0
high >= max(open, close)
low <= min(open, close)
volume >= 0
barStart < barEnd
currency == stocks.currency
```

검증 실패 행은 저장하지 않고 격리 건수와 공급자 요청 ID를 기록한다. 임의 보정은 하지 않는다. `volume` 미제공이 합법적인 시장은 공급자 선정 시 별도 정책을 정하기 전까지 해당 가격을 수용하지 않는다.

### 뉴스 텍스트와 URL

- 제목과 언론사 양끝 공백을 제거하고 Unicode를 NFKC로 정규화한다.
- URL fragment와 추적 전용 `utm_*` 파라미터를 제거하되 기사 식별에 필요한 query parameter는 유지한다.
- `publishedAt`이 미래 10분을 초과하면 격리한다. 공급자 시간대 오류로 추정해 현재 시각으로 바꾸지 않는다.
- 본문 정제는 원본의 의미와 출처를 바꾸지 않으며 AI 입력 전처리와 수집 중복 판별을 분리한다.
- HTML 원본을 허용하지 않는 계약이면 평문만 저장한다. 스크립트, 추적 픽셀과 광고 블록은 제거한다.

## 7. 종목 매핑

외부 종목을 내부 `stocks`에 연결하는 우선순위는 다음과 같다.

1. `(providerId, providerInstrumentId)`의 승인된 영구 매핑
2. ISIN 등 표준 식별자와 거래소 일치
3. `(provider exchangeCode, provider symbol)`의 명시적 변환 규칙
4. 관리자가 승인한 별칭

회사명 문자열만으로 자동 연결하지 않는다. 심볼이 같아도 시장이 다르거나 후보가 둘 이상이면 `AMBIGUOUS`로 격리한다. 일치 항목이 없으면 `UNMAPPED`로 기록하고 `stocks`를 자동 생성하지 않는다.

라이브 전환 전에 다음 매핑 테이블이 필요하다.

```text
stock_provider_mappings
  stock_id, provider_id, provider_instrument_id,
  provider_symbol, provider_exchange_code,
  valid_from, valid_to, active
```

뉴스 종목 연결은 공급자가 제공한 종목 ID를 위 매핑으로 변환하는 것을 우선한다. 제목·본문의 회사명 추출은 후보 생성에만 사용하고 자동 확정하지 않는다.

현재 `news_articles`는 기사 하나를 종목 하나에만 연결할 수 있고 `(source, external_id)`가 유일하므로 동일 기사를 여러 종목에 연결할 수 없다. 라이브 전환 전 `news_article_stocks(news_id, stock_id, relevance, mapping_method)` 연결 테이블을 추가하고 기존 `news_articles.stock_id`는 제거하거나 호환 기간 뒤 폐기하는 것이 목표다. 그 전까지는 공급자가 명시한 종목이 정확히 하나인 기사만 수집하고, 다종목·모호한 기사는 격리한다.

## 8. 중복 제거와 멱등성

### 가격

- 저장 키는 현재 스키마와 동일하게 `(stock_id, price_interval, recorded_at)`를 사용한다.
- 일봉의 `recorded_at`은 조회 시각이 아니라 해당 거래 세션의 공식 종료 시각을 UTC로 변환한 값이다.
- 동일 키 재수집은 중복 INSERT가 아니라 Upsert로 처리한다.
- 미완성 봉은 `isFinal=false`로 구분하고, 공식 정정이나 완성 봉이 도착하면 OHLCV를 갱신한다.
- 값이 변경되면 공급자, 이전·신규 payload hash와 정정 시각을 감사 로그에 남긴다.

현재 `market_prices`에는 `fetched_at`, `session_date`, `is_final`, `adjustment_mode`, `provider_revision`이 없다. DG-4 전에 이 필드 또는 동일한 의미의 감사 테이블을 추가한다.

### 뉴스

1. 1차 고유 키는 현재 스키마와 동일한 `(source, external_id)`이다.
2. 동일 공급자·언론사의 정규화 canonical URL이 같으면 동일 기사 후보로 본다.
3. 본문 저장이 허용되면 광고·공백을 제거한 평문의 SHA-256을 `content_hash`로 사용한다.
4. 제목·언론사·발행 시각만으로는 오탐 가능성이 있으므로 자동 병합하지 않고 중복 후보로만 기록한다.

동일 ID 재수집 시 `updatedAt`이 더 최신인 경우에만 허용된 메타데이터와 본문을 갱신한다. 공급자가 다른 기사는 동일 본문 hash여도 라이선스와 출처가 다를 수 있으므로 자동 병합하지 않는다. 향후 `duplicate_of_id` 또는 기사 클러스터를 도입하기 전에는 검색 결과에서만 소프트 중복 억제를 적용한다.

모든 수집 작업은 같은 구간을 다시 실행해도 행 수가 증가하지 않아야 한다. 기사 증분 수집은 마지막 성공 cursor와 `publishedAt`을 저장하고 기본 10분의 중첩 구간을 다시 조회해 늦게 도착한 기사를 포착한다.

## 9. 수집 범위와 스케줄

스케줄은 거래소 캘린더와 공급자 한도에 맞춰 조정하되, 아래 값을 운영 MVP 기본 목표로 한다.

| 작업 | 기본 주기 | 범위 | 보정 |
|---|---|---|---|
| 종목 마스터 동기화 | 매일 1회 00:15 UTC | 활성 시장 전체 또는 승인 목록 | 상장폐지는 즉시 삭제하지 않고 `active=false` |
| 장중 현재가 | 장중 1분 | 관심종목과 포트폴리오 종목 | 지연 공급자는 지연 시간을 함께 표시 |
| 장외 현재가 | 30분 또는 폴링 중지 | 마지막 공식 종가 유지 | `sessionStatus=CLOSED` 표시 |
| 일봉 수집 | 공식 장 마감 30분 후 | 활성 종목 | 마감 4시간 후와 다음 거래일 1회 재조회 |
| 뉴스 증분 수집 | 5분 | 관심종목·포트폴리오 종목 | 시작 시 최근 24시간 Backfill, 10분 overlap |
| 뉴스 저우선 범위 | 30분 | 그 외 활성 종목 | 호출량 80%부터 일시 중지 가능 |

무료·지연 요금제로 1분 현재가를 충족하지 못하면 공급자 선정 시 실제 주기를 확정하고 UI에 `DELAYED`와 지연 시간을 표시한다. 계약상 허용 주기가 목표보다 느리다고 해서 호출 한도를 우회하거나 다중 키를 사용하지 않는다.

수집기는 작업별 체크포인트를 저장한다.

```text
provider_sync_state
  provider_id, data_type, scope_key,
  cursor, watermark_at, last_attempt_at,
  last_success_at, status, last_error_code
```

애플리케이션 인스턴스가 여러 개여도 동일 작업은 한 번만 실행되도록 DB 기반 분산 락을 사용한다. 프로세스 재시작 후 마지막 성공 체크포인트부터 재개하고, 한 페이지의 저장과 체크포인트 갱신은 실패 시 재실행 가능한 순서로 처리한다.

## 10. 데이터 신선도와 사용자 표시

### 공통 필드

라이브 API 응답에는 최소 다음 정보를 제공한다.

- `source`: 내부 `providerId` 또는 `DEMO`
- `asOf`: 가격이 유효한 공급자 기준 시각 또는 뉴스 발행 시각
- `fetchedAt`: FinWatch가 공급자 응답을 받은 시각
- `freshness`: `FRESH`, `DELAYED`, `STALE`, `UNAVAILABLE`, `DEMO`
- 가격은 `sessionStatus`: `PRE_MARKET`, `OPEN`, `AFTER_HOURS`, `CLOSED`, `HOLIDAY`, `UNKNOWN`
- 공급자가 공표한 `delaySeconds`

현재 종목 응답은 `source`, `asOf`만 포함하고 뉴스 응답은 출처와 수집 시각을 포함하지 않는다. DG-4 전에 DB와 API를 확장한다.

### 판정 규칙

| 데이터 | `FRESH` | `DELAYED` | `STALE` |
|---|---|---|---|
| 장중 현재가 | `asOf` 경과가 `공표 지연 + 폴링 주기 + 60초` 이내 | Fresh 기준 초과, 30분 이내 | 30분 초과 |
| 장 마감 가격 | 최신 거래일 공식 종가가 존재 | 마감 후 2시간 내 아직 최종 봉 없음 | 마감 2시간 후에도 최신 거래일 봉 없음 |
| 뉴스 피드 상태 | 마지막 성공 수집이 `2 × 수집 주기 + 1분` 이내 | 60분 이내 | 마지막 성공이 60분 초과 |

오래된 기사 자체는 오류가 아니다. 뉴스 `freshness`는 기사 나이가 아니라 해당 종목 피드의 마지막 성공 수집 상태를 뜻한다. 시장이 `CLOSED` 또는 `HOLIDAY`일 때 마지막 공식 종가는 장중 규칙만으로 `STALE` 처리하지 않는다.

화면은 출처, `asOf`, `freshness`를 함께 표시한다. `STALE` 가격으로 포트폴리오 평가를 계산할 수는 있지만 `지연된 가격 기준` 경고를 표시한다. 최신 값이 전혀 없으면 0으로 계산하지 않고 `503 MARKET_DATA_UNAVAILABLE` 또는 해당 필드 `null` 정책을 API 명세에 반영한다.

## 11. 시장 시간, 휴장일과 수정주가

### 거래 세션

- KRX 기준 시간대는 `Asia/Seoul`, 정규장은 기본 09:00~15:30이다.
- NASDAQ 등 미국 동부 거래소는 `America/New_York`, 정규장은 기본 09:30~16:00이며 DST를 시간대 DB로 처리한다.
- 위 시간은 초기 기준이며 조기 종료, 임시 휴장과 제도 변경을 코드 상수만으로 판정하지 않는다.
- 공급자 거래 캘린더 또는 검증된 거래소 캘린더를 연도별로 버전 관리한다.
- 휴장일에는 일봉을 생성하지 않는다. 캘린더를 확인할 수 없으면 시장을 `UNKNOWN`으로 표시하고 합성 가격을 만들지 않는다.

`sessionDate`는 거래소 현지 날짜이며 DB 시간은 해당 세션 종료를 UTC로 변환해 저장한다. 서버 실행 지역과 무관해야 한다. 데모 이력에 주말 날짜가 포함될 수 있으나 이는 합성 데모일 뿐 라이브 거래일 검증의 기준으로 사용하지 않는다.

### 수정주가

- 현재가와 포트폴리오 평가는 거래 시점의 원시 가격을 사용한다.
- 차트와 기술적 지표의 장기 연속성에는 기본 `SPLIT_ADJUSTED` 일봉을 사용한다.
- 배당까지 재투자한 `TOTAL_RETURN` 계열은 별도 표기 없이 사용하지 않는다.
- 공급자가 `RAW`, `SPLIT_ADJUSTED`, `TOTAL_RETURN` 중 무엇을 제공하는지, 과거 값을 언제 재작성하는지 DG-2에서 검증한다.
- 액면분할 등 기업행사 후 영향 구간을 다시 수집하고 수정 모드와 공급자 개정 시각을 기록한다.
- 서로 다른 수정 모드의 가격을 같은 기술지표 계산에 섞지 않는다.

현재 `market_prices`는 수정 여부를 저장하지 않으므로 라이브 기술지표에 사용하기 전에 `adjustment_mode`와 기업행사 재수집 정책을 구현해야 한다.

## 12. 뉴스 저작권과 보관

1. 일반 웹페이지를 약관·robots 정책 확인 없이 크롤링하지 않는다.
2. 기사 제목, 언론사, 원문 URL, 발행 시각과 공급자 출처를 화면에서 제거하지 않는다.
3. 전체 본문을 API나 화면으로 재배포하지 않는다. `STORE_AND_DISPLAY` 권리와 허용 분량이 확인된 경우만 예외다.
4. AI 입력과 파생 요약 생성이 각각 허용되는지 확인한다. 본문 열람 권한이 곧 AI 처리 권한을 의미하지 않는다.
5. 본문 보관 기간은 `min(환경 설정, 계약상 최대 기간)`이다. 계약상 기간이 없더라도 운영 기본값은 30일이며 메타데이터 기본값은 365일이다.
6. 만료된 본문은 `content=null`로 삭제하고 메타데이터와 원문 링크만 유지한다. 해당 본문으로 새 AI 요약을 생성하지 않는다.
7. 삭제·정정·권리 철회 요청은 확인 후 24시간 이내 반영하고 관련 Redis 요약 캐시를 무효화한다. 파생 요약 삭제 의무는 공급자 계약에 따른다.
8. 원문과 API 키를 로그, 오류 추적 도구, 분석 이벤트에 남기지 않는다.
9. 저장 본문 접근은 백엔드 서비스 계정으로 제한하며 DB 백업의 보관 기간도 계약 한도를 초과하지 않는다.

### 허용 목록 크롤링 정책

1. 출처별 이용약관, API/RSS 이용 조건과 robots 규칙을 확인한 뒤 DB `source_policies` 또는 `app.news-content.configured-policies`에 승인 일자와 근거 URL을 등록한다. 설정형 정책도 `enabled=true`, `reviewedAt`, `reviewReference`가 모두 있어야 활성화된다.
2. robots.txt의 허용 여부는 반드시 준수하지만, robots 허용만으로 저장·재배포·AI 분석 권한이 생긴다고 간주하지 않는다.
3. 로그인, 유료 구독, CAPTCHA, paywall, 봇 차단 또는 접근 제어를 우회하지 않는다.
4. 검색결과 전체 웹을 재귀적으로 탐색하지 않고 승인된 기사 URL만 가져온다.
5. 식별 가능한 `User-Agent`와 연락 가능한 프로젝트 URL 또는 이메일을 사용한다.
6. 도메인별 동시 요청은 기본 1개, 기본 간격은 5초 이상으로 하고 출처가 더 엄격한 한도를 제시하면 이를 따른다.
7. `ETag`와 `Last-Modified`가 있으면 조건부 요청을 사용하고 동일 content hash를 반복 저장하지 않는다.
8. 429와 `Retry-After`를 존중하며 차단 응답이 반복되면 해당 출처를 자동 중지한다.
9. HTML·XHTML·명시적으로 지원한 XML만 처리하고 실행 파일, 이미지, 동영상과 알 수 없는 바이너리는 저장하지 않는다.
10. 응답 본문 크기, 압축 해제 크기, redirect 횟수와 처리 시간을 제한한다.
11. redirect마다 허용 도메인과 정책을 다시 확인한다. `localhost`, 사설 IP, link-local, cloud metadata endpoint로의 요청을 차단하여 SSRF를 방지한다.
12. script, style, iframe, form, tracking 요소를 제거하고 검증된 HTML parser와 출처별 extractor로 본문 영역만 추출한다.
13. 원문 전체를 프런트엔드에 재배포하지 않고 제목·출처·링크·AI 분석 결과와 허용된 인용만 표시한다.
14. 출처가 정책을 변경하거나 삭제를 요청하면 수집을 중단하고 저장 본문·파생 결과 처리 여부를 재검토한다.

출처 정책 최소 필드:

```text
source_policies
  source_id, domain_pattern, allowed_path_pattern,
  access_mode, ai_analysis_allowed, display_mode,
  retention_days, requests_per_minute,
  terms_url, robots_url, reviewed_at, active
```

`access_mode`는 `METADATA_ONLY`, `API_CONTENT`, `ALLOWLIST_FETCH`, `BLOCKED` 중 하나다. 새 도메인은 코드 배포나 관리자 승인 없이 자동으로 `ALLOWLIST_FETCH`가 되지 않는다.

공급자별로 약관 버전, 확인일, `rightsProfile`, 본문·메타데이터 보관 기간, 삭제 연락처를 운영 문서에 기록한다. 약관이 변경되면 자동으로 기존 권리가 유지된다고 가정하지 않고 재검토한다.

## 13. 실패, 재시도, 호출 한도와 폴백

### 호출 정책

- 기본 연결 timeout 2초, 응답 timeout 5초, 대량 일봉 Backfill은 응답 timeout 15초를 사용한다.
- 네트워크 오류, timeout, HTTP 408, 429와 5xx만 재시도한다.
- 인증 실패, 권한 오류, 잘못된 요청 등 다른 4xx는 재시도하지 않는다.
- 최대 3회, 500ms·1s·2s 지수 backoff와 0~30% jitter를 사용한다.
- `Retry-After`가 있으면 이를 우선하며 웹 요청 thread에서 장시간 대기하지 않고 다음 수집 작업으로 예약한다.
- 60초 동안 연속 5회 실패하면 circuit를 60초 열고, 이후 단일 시험 호출로 복구를 확인한다.

실제 수치는 공급자 권장값이 더 엄격하면 그 값을 따른다. 재시도마다 별도 데이터 행을 만들지 않으며 하나의 수집 실행 ID로 추적한다.

### 호출 한도

- 공급자별 token bucket 또는 동등한 limiter로 초당·분당·일일 한도를 모두 지킨다.
- 한도의 80%에 도달하면 Backfill과 비관심종목 뉴스 작업을 늦춘다.
- 95%에 도달하면 현재가보다 낮은 우선순위 작업을 중지하고 운영 경고를 발생시킨다.
- 100% 또는 `QUOTA_EXHAUSTED`이면 다음 reset 시각까지 호출하지 않는다.
- 사용자 API 요청마다 공급자를 직접 호출하는 구조를 피하고 DB/Redis의 최근 수집값을 반환한다.

### 장애 시 반환

1. 유효한 마지막 라이브 데이터가 있으면 `STALE` 또는 `DELAYED`와 기준 시각을 붙여 반환한다.
2. 저장된 값이 없으면 명확한 503 오류를 반환한다.
3. `LIVE`에서 데모 값으로, 또는 다른 공급자로 조용히 전환하지 않는다.
4. 보조 공급자는 라이선스, 심볼 매핑, 가격 조정 방식과 계약 테스트를 별도로 통과하고 운영 설정으로 활성화한 경우만 사용할 수 있다.
5. 부분 응답은 누락 종목과 오류를 관측성 지표에 기록하며 0 또는 빈 기사로 공급자 장애를 숨기지 않는다.

## 14. 설정과 비밀정보

아래 장기 목표 설정 중 현재 구현은 `DATA_MODE=DEMO|LIVE`와 KIS·NAVER API HUB·Finnhub 전용 namespace다. `MIXED`, 스케줄러, 공급자별 쿼터와 circuit breaker는 아직 구현하지 않았다.

```yaml
app:
  data:
    mode: ${DATA_MODE:DEMO} # DEMO / LIVE / MIXED
    allow-mixed-data: ${ALLOW_MIXED_DATA:false}
    market:
      provider: ${MARKET_DATA_PROVIDER:demo}
      base-url: ${MARKET_DATA_BASE_URL:}
      api-key: ${MARKET_DATA_API_KEY:}
      requests-per-minute: ${MARKET_REQUESTS_PER_MINUTE:}
      requests-per-day: ${MARKET_REQUESTS_PER_DAY:}
      quote-poll-interval: ${MARKET_QUOTE_POLL_INTERVAL:1m}
      daily-bar-delay: ${MARKET_DAILY_BAR_DELAY:30m}
      adjustment-mode: ${MARKET_ADJUSTMENT_MODE:SPLIT_ADJUSTED}
      stale-after: ${MARKET_STALE_AFTER:30m}
    news:
      provider: ${NEWS_DATA_PROVIDER:demo}
      base-url: ${NEWS_DATA_BASE_URL:}
      api-key: ${NEWS_DATA_API_KEY:}
      requests-per-minute: ${NEWS_REQUESTS_PER_MINUTE:}
      requests-per-day: ${NEWS_REQUESTS_PER_DAY:}
      poll-interval: ${NEWS_POLL_INTERVAL:5m}
      startup-lookback: ${NEWS_STARTUP_LOOKBACK:24h}
      overlap-window: ${NEWS_OVERLAP_WINDOW:10m}
      content-retention: ${NEWS_CONTENT_RETENTION:30d}
      metadata-retention: ${NEWS_METADATA_RETENTION:365d}
    client:
      connect-timeout: ${DATA_CONNECT_TIMEOUT:2s}
      read-timeout: ${DATA_READ_TIMEOUT:5s}
      max-attempts: ${DATA_MAX_ATTEMPTS:3}
```

확정 공급자별 목표 환경변수:

| 공급자 | 환경변수 | 비고 |
|---|---|---|
| KIS | `KIS_APP_KEY`, `KIS_APP_SECRET`, `KIS_HTS_ID` | 서버 Secret, REST·WebSocket 인증 |
| KIS | `KIS_ENV` | `paper` 또는 `prod`; 시세 PoC는 가능한 범위에서 모의·테스트 환경 우선 |
| NAVER API HUB | `NAVER_API_HUB_CLIENT_ID`, `NAVER_API_HUB_CLIENT_SECRET` | 각각 `X-NCP-APIGW-API-KEY-ID`, `X-NCP-APIGW-API-KEY` 헤더로 서버에서 전송 |
| Finnhub | `FINNHUB_API_KEY` | 서버 Secret; Quote·trade WebSocket·뉴스 API에 사용 |
| 실시간 | `REALTIME_ENABLED`, `REALTIME_RECONNECT_MAX_DELAY` | LIVE 스트림 활성화와 지수 백오프 최대 지연 |
| Open DART | `OPENDART_API_KEY` | 서버 Secret |
| SEC EDGAR | `SEC_EDGAR_USER_AGENT` | 프로젝트명과 연락처를 포함; 비밀키는 아님 |

목표 고정값:

```text
MARKET_DATA_PROVIDER=kis
KOREAN_NEWS_PROVIDER=naver-api-hub
US_NEWS_PROVIDER=finnhub
KOREAN_DISCLOSURE_PROVIDER=opendart
US_DISCLOSURE_PROVIDER=sec-edgar
AI_PROVIDER=gemini
```

KIS 국내 체결 TR은 공식 샘플의 `H0STCNT0`, Finnhub는 `wss://ws.finnhub.io?token=...` trade 구독을 사용한다. 호출 제한과 공개 표시 권한은 발급 계정 약관을 운영 배포 전에 다시 확인하며 포털의 Secret이나 계좌정보를 문서·Fixture에 저장하지 않는다.

공급자별 `BASE_URL`, `API_KEY`, 호출 한도와 계정 ID는 어댑터 전용 namespace로 둔다. 호출 한도에는 임의 기본값을 두지 않고 DG-3에서 확인한 계약값을 `LIVE` 필수 설정으로 등록한다. 실제 키는 로컬 사용자 환경변수 또는 배포 플랫폼 Secret에 저장하고 Git, 프런트엔드, 이미지와 일반 로그에 넣지 않는다. 운영 키는 최소 권한으로 발급하고 개발·스테이징·운영을 분리하며 회전 일자를 기록한다.

애플리케이션 시작 시 다음 조건을 검증한다.

- `DEMO`: 외부 키가 없어도 시작하며 외부 어댑터 Bean을 만들지 않는다.
- `LIVE`: 필수 키가 없는 공급자는 `ERROR` 상태를 내보내고 해당 스트림만 비활성화한다.
- `MIXED`: 허용 플래그가 없으면 시작을 거부한다.
- 공급자별 base URL은 운영 허용 목록의 HTTPS 주소만 사용한다.

## 15. 관측성과 운영 기록

수집 작업마다 다음 값을 구조화 로그와 메트릭으로 기록한다.

- `runId`, `providerId`, 데이터 종류, 대상 범위, 시작·종료 시각
- 공급자 호출 수, 응답시간, HTTP 상태와 공급자 request ID
- 수신, 저장, 갱신, 중복, 격리, 매핑 실패 행 수
- 마지막 성공 수집 시각과 데이터 age
- 429 횟수, 남은 한도와 reset 시각(제공되는 경우)
- retry, circuit open, checkpoint 지연

API 키, Authorization header, 뉴스 본문과 전체 공급자 응답은 기록하지 않는다. 공급자 장애가 API readiness 자체를 `DOWN`으로 만들어 배포가 반복 재시작되지 않도록 하고, 별도의 데이터 공급자 health를 `DEGRADED`로 노출한다.

최소 운영 경고 조건:

- 장중 현재가 마지막 성공 수집이 10분 초과
- 뉴스 마지막 성공 수집이 30분 초과
- 15분 동안 수집 실패율 20% 초과
- 일일 호출 한도 80%와 95% 도달
- 매핑 실패 또는 격리 비율 5% 초과
- 최신 거래일의 최종 일봉이 장 마감 2시간 후에도 없음

## 16. 라이브 전환 전 필수 스키마·API 변경

다음은 DG-4의 차단 조건이다.

1. `stock_provider_mappings`와 `provider_sync_state`를 추가한다.
2. 뉴스 다종목 연결을 위한 `news_article_stocks`를 추가하거나 지원 범위를 단일 종목 기사로 명시적으로 제한한다.
3. 가격에 `fetched_at`, `session_date`, `is_final`, `adjustment_mode`와 정정 추적 정보를 추가한다.
4. 뉴스에 `fetched_at`, `updated_at`, canonical URL과 권리 프로필을 저장한다.
5. 조회 API에 `source`, `asOf`, `fetchedAt`, `freshness`, 가격의 `sessionStatus`와 `delaySeconds`를 제공한다.
6. 현재 symbol 단독 조회를 시장과 함께 식별하거나, 지원 범위 안에서 symbol 전역 유일성을 검증한다.
7. 일반 Flyway 경로의 데모 시드를 운영 데이터와 격리한다.
8. `NEWS_CONTENT_UNAVAILABLE`, `MARKET_DATA_UNAVAILABLE`, `DATA_STALE`, `PROVIDER_RATE_LIMITED` 오류 정책을 API 명세에 반영한다.
9. 허용 목록 본문 수집을 사용할 경우 `source_policies`와 기사별 `content_source`, `rights_profile`, `extractor_version`, `fetched_at`, `content_hash`를 저장한다.
10. `ArticleContentFetcher`에 SSRF 방어, 응답 크기 제한, redirect 재검증과 도메인별 rate limit을 적용한다.

## 17. 인수 조건

### DEMO

- [ ] API 키와 네트워크 없이 종목 상세, 60일 이상 가격 이력, 뉴스 목록과 AI 요약 시연이 가능하다.
- [ ] 모든 데모 데이터가 `source=DEMO`이고 화면에 데모 배지와 기준 시각이 표시된다.
- [ ] 동일 환경을 재생성해도 데모 행과 응답이 결정적으로 같다.

### 공급자 선정

- [ ] DG-0~DG-3의 문서와 근거가 승인되었다.
- [ ] 대표 종목 SK하이닉스에 대해 안정적 종목 ID, 최신 가격과 공식 거래일 60개 이상의 일봉을 반환한다.
- [ ] 지원하는 각 시장에서 대표 종목 하나 이상으로 시간대, 휴장, 통화와 수정주가를 검증했다.
- [ ] 뉴스 저장·AI 처리·요약 표시 권리를 약관 버전과 함께 기록했다.
- [ ] 예상 정상 호출량과 재시도 포함 호출량이 계약 한도와 월 예산 안에 있다.

### 수집 정확성

- [ ] 동일 가격·뉴스 구간을 세 번 재실행해도 중복 행이 생기지 않는다.
- [ ] 공급자 정정 데이터가 기존 행을 갱신하고 변경 이력을 남긴다.
- [ ] 주말·휴장일에 라이브 일봉을 생성하지 않으며 DST 경계의 미국 세션 날짜가 정확하다.
- [ ] 잘못된 OHLC, 미래 뉴스, 미매핑·모호한 종목이 저장되지 않고 격리된다.
- [ ] 한 기사가 여러 종목에 관련된 경우 연결 테이블로 표현되거나 명시된 단일 종목 제한에 따라 격리된다.

### 권리와 보안

- [ ] `METADATA_ONLY` 기사의 `content`는 `null`이고 AI 요청은 `422 NEWS_CONTENT_UNAVAILABLE`이다.
- [ ] 본문 만료·삭제 시 원문과 관련 캐시가 정책대로 제거된다.
- [ ] 화면에 언론사, 원문 링크, 발행 시각과 공급자 출처가 표시된다.
- [ ] API 키, 뉴스 본문과 Authorization header가 Git, 프런트 번들, 이미지와 로그에 없다.
- [ ] 승인되지 않은 도메인은 기본 `METADATA_ONLY`이며 본문 요청이 발생하지 않는다.
- [ ] 허용 출처는 약관·robots 검토 근거와 검토 일자를 가진다.
- [ ] paywall·로그인·CAPTCHA를 우회하지 않고 사설 IP·metadata endpoint·비허용 redirect를 차단한다.
- [ ] 도메인별 간격·동시성·429 정책과 조건부 요청을 자동 테스트했다.
- [ ] 본문 추출 실패나 정책 위반 시 검색 API의 설명을 전문인 것처럼 저장하지 않는다.

### 장애와 운영

- [ ] timeout, 429, 5xx Fixture로 재시도 횟수, `Retry-After`, circuit breaker를 검증했다.
- [ ] `LIVE` 공급자 장애 시 데모로 자동 전환하지 않고 마지막 값에 `STALE`을 표시하거나 503을 반환한다.
- [ ] 재시작 후 체크포인트부터 수집하고 다중 인스턴스에서 같은 작업이 중복 실행되지 않는다.
- [ ] 호출량, 오류율, 지연, 신선도, 중복·격리·매핑 실패 메트릭과 경고를 확인할 수 있다.
- [ ] 운영에서 공급자를 끄고 DB의 마지막 정상 데이터를 읽는 롤백 절차를 시연했다.

## 18. 미결정 항목

- 운영 MVP 지원 시장: KRX + 미국 시장으로 확정, 지원 거래소·지연 허용치·최대 종목 수는 TBD
- KIS 계정의 국내·미국 시세 권한, 공개 화면 표시 범위와 확정 호출 한도
- NAVER API HUB·Finnhub의 최종 요금제, 표시 의무와 메타데이터 보관 범위
- 실시간, 지연 또는 종가 중심 가격 정책과 확정 폴링 주기
- 공급자별 수정주가 정의와 기업행사 재수집 범위
- 뉴스 다종목 연결 스키마 도입 시점
- 본문·메타데이터·파생 AI 결과의 최종 보관 기간
- 보조 공급자 도입 여부
- 포트폴리오 MVP에서 전문 수집을 허용할 공식 공시·IR·RSS 도메인 목록
- 출처별 본문 보관·AI 분석·인용 허용 범위와 검토 책임자

이 항목은 구현자가 임의로 정하지 않는다. 각 결정은 DG 증거와 함께 ADR 또는 관련 명세를 갱신한 뒤 구현한다.
