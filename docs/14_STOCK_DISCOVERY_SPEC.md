# FinWatch 종목 검색·탐색·온디맨드 데이터 명세

상태: v1.0 로컬 검색·canonical 상세·LIVE 마스터 동기화·온디맨드 수집 구현
기준일: 2026-07-14
지원 목표: KRX + 미국 주식 시장

## 1. 목적

현재 구현은 Flyway에 등록된 `005930`, `000660`, `NVDA`, `AAPL` 네 종목만 조회한다. `GET /api/v1/stocks`도 DB의 활성 종목 전체를 반환할 뿐 `query`, `market`, pagination을 실제 처리하지 않으며, 프런트엔드에는 통합 종목 검색 UI가 없다.

완성 목표는 사용자가 회사명이나 심볼을 검색해 원하는 종목을 열고, 상세 화면에서 시세·차트·기술지표·뉴스·AI 분석을 동일한 종목 컨텍스트로 확인하는 것이다.

```text
검색어 입력
→ 로컬 종목 마스터 검색
→ 시장·심볼이 명확한 결과 선택
→ 종목 상세 즉시 이동
→ 현재가·일봉·뉴스를 필요한 만큼 온디맨드 동기화
→ 기술지표 계산과 AI 기능 연결
→ 관심종목·보유종목·최근 본 종목에 재사용
```

네 종목은 오프라인 시연용 대표 fixture로 유지하되 서비스의 전체 종목 범위로 표현하지 않는다.

## 2. 핵심 설계 결정

### 2.1 검색과 시장 데이터 수집을 분리한다

- 종목명·심볼·거래소·통화 같은 가벼운 `종목 마스터`는 지원 시장 전체를 로컬 DB에 저장한다.
- 검색 요청마다 KIS나 Finnhub를 호출하지 않고 PostgreSQL 종목 마스터를 검색한다.
- 현재가·일봉·뉴스는 사용자가 선택하거나 관심종목·보유종목이 된 종목만 수집한다.
- 모든 상장 종목의 실시간 체결을 상시 구독하지 않는다.

이 방식은 검색 응답 속도, 외부 API 쿼터, 비용과 장애 격리를 동시에 개선한다.

### 2.2 종목 식별자는 `(market, symbol)`이다

`symbol` 단독은 전 세계에서 유일하지 않다. 내부 PK `stockId`와 `(market, symbol)`을 함께 사용한다.

- KRX 종목 코드는 6자리 앞자리 `0`을 보존한다.
- 미국 영문 심볼은 대문자로 정규화하되 `.`, `-` 등 공급자가 허용한 문자를 보존한다.
- 검색 결과 선택 이후 화면과 API 상태에는 `stockId`, `market`, `symbol`을 함께 전달한다.
- 기존 `/stocks/{symbol}`은 호환 기간에만 유지하고 동일 symbol 후보가 둘 이상이면 `409 STOCK_SYMBOL_AMBIGUOUS`를 반환한다.
- 신규 canonical 상세 경로는 `/stocks/{market}/{symbol}`이다.

## 3. 공급자와 종목 마스터

| 범위 | 마스터·검색 원천 | 상세 데이터 |
|---|---|---|
| 국내 KRX | KIS가 제공하는 국내 종목 정보/마스터 자료를 `InstrumentCatalogProvider`로 정규화 | KIS REST·WebSocket, NAVER API HUB 뉴스, Open DART 공시 |
| 미국 | Finnhub의 지원 거래소 종목 목록·심볼 검색 결과를 `InstrumentCatalogProvider`로 정규화 | Finnhub Quote·trade WebSocket·Company News, SEC EDGAR 공시 |

공급자별 URL, 인증과 wire format은 어댑터 내부에 격리한다. 외부 계약 변경이 검색 API 응답을 직접 바꾸면 안 된다. 계정 요금제에서 전체 마스터 제공이 제한되면 승인된 대체 목록을 도입하되 `source`와 범위를 화면·운영 문서에 표시한다.

마스터 동기화 정책:

- 전체 동기화: 매일 1회, 장 시작과 겹치지 않는 시간
- 증분 또는 변경 확인: 공급자가 지원하면 6시간마다
- 신규 상장: insert 또는 upsert 후 `active=true`
- 종목명·거래소 변경: 기존 `stockId`를 유지하며 갱신 이력 기록
- 거래정지·상장폐지: 즉시 삭제하지 않고 상태와 `active=false` 또는 거래 상태 저장
- 동기화 실패: 기존 마스터 유지, 검색 화면에 장애를 노출하지 않고 관리자 freshness 경고
- 성공한 빈 응답: 전체 비활성화 금지, 임계치 검증 후 격리

## 4. 검색 기능 계약

### 4.1 검색 대상

다음 필드에서 대소문자와 일반 공백 차이를 무시한다.

- `symbol`: `005930`, `AAPL`, `BRK.B`
- 한국어·영문 종목명: `삼성전자`, `Samsung Electronics`, `Apple`
- 등록된 별칭과 이전 사명

MVP는 회사명·심볼 검색에 집중하며 ETF·ETN·우선주·ADR 포함 여부는 `instrumentType` 필터로 명시한다. 지수·선물·옵션·암호화폐·외환은 범위 밖이다.

### 4.2 정렬

결과 우선순위는 서버가 결정한다.

1. symbol 완전 일치
2. 종목명 완전 일치
3. symbol 접두 일치
4. 종목명 접두 일치
5. 종목명 부분 일치
6. 동일 점수에서는 활성 종목, 사용자 관심·최근 조회, 거래소·이름 순

검색 인기 순위가 필요해져도 개인 사용자의 검색어 원문을 관리자 로그에 평문으로 장기 보관하지 않는다.

### 4.3 입력과 성능

- trim 후 빈 검색어는 인기/최근 종목 endpoint로 분리하고 전체 마스터를 반환하지 않는다.
- 검색어 최대 100자, 제어문자와 허용되지 않은 symbol 문자를 거부한다.
- 프런트엔드는 250ms debounce하고 이전 요청을 취소한다.
- 기본 10개, 최대 50개 결과를 반환한다.
- 서버 목표: warm DB 기준 p95 200ms 이하.
- PostgreSQL에서는 정규화 검색 컬럼과 적절한 prefix/trigram 인덱스를 사용한다.

## 5. API 계약

### 5.1 종목 검색

`GET /api/v1/stocks/search?q=삼성&market=KRX&type=STOCK&page=0&size=10`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "stockId": 2,
        "market": "KRX",
        "exchange": "KRX",
        "symbol": "005930",
        "name": "삼성전자",
        "englishName": "Samsung Electronics",
        "instrumentType": "STOCK",
        "currency": "KRW",
        "active": true,
        "tradable": true,
        "dataAvailability": "METADATA_ONLY",
        "source": "KIS_MASTER"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "catalogAsOf": "2026-07-14T00:15:00Z"
  },
  "message": "종목 검색 성공"
}
```

`dataAvailability`:

- `READY`: 최신 허용 범위의 시세·일봉이 있음
- `PARTIAL`: 일부 데이터만 있음
- `METADATA_ONLY`: 마스터만 있고 상세 데이터는 아직 수집하지 않음
- `UNAVAILABLE`: 현재 공급자 권한이나 시장 범위로 상세 조회 불가

### 5.2 canonical 종목 상세

```text
GET /api/v1/stocks/{market}/{symbol}
GET /api/v1/stocks/{market}/{symbol}/prices?period=3M&interval=1D
GET /api/v1/stocks/{market}/{symbol}/technical
GET /api/v1/stocks/{market}/{symbol}/news?page=0&size=20
```

기존 symbol 단독 endpoint는 호환 계층이며 신규 프런트엔드는 canonical endpoint를 사용한다.

### 5.3 상세 데이터 준비

검색 결과가 `METADATA_ONLY`이면 상세 화면 진입 시 다음 endpoint로 필요한 데이터를 준비한다.

`POST /api/v1/stocks/{market}/{symbol}/data-loads`

```json
{
  "resources": ["QUOTE", "DAILY_PRICES", "NEWS", "DISCLOSURES"]
}
```

응답:

- 이미 신선함: `200 READY`
- 비동기 수집 시작 또는 진행 중: `202 SYNCING`, 동일 작업 ID 재사용
- 공급자 범위 밖: `422 DATA_NOT_SUPPORTED`
- 호출 한도: `429 PROVIDER_QUOTA_EXCEEDED`, `retryAfterSeconds` 포함

상세 화면은 메타데이터를 먼저 표시하고 quote·chart·news 영역을 독립적으로 갱신한다. 일부 공급자 실패가 전체 화면 실패가 되어서는 안 된다.

## 6. 온디맨드 수집 우선순위

| 우선순위 | 대상 | 정책 |
|---|---|---|
| P0 | 현재 보고 있는 종목 | quote 즉시, 필요한 일봉·뉴스 병렬 수집 |
| P1 | 관심종목·보유종목·활성 알림 | 장중 quote·실시간 구독, 마감 후 일봉 갱신 |
| P2 | 최근 본 종목 | TTL 동안 캐시, 실시간 구독은 유휴 후 해제 |
| P3 | 마스터에만 존재 | 검색 가능, 상세 선택 전 외부 호출 없음 |

WebSocket 구독은 모든 마스터 종목이 아니라 P0·P1만 대상으로 한다. 종목 선택 해제 후 유예 시간을 두고 P1이 아닌 구독을 제거하며, 공급자 구독 상한을 넘으면 LRU와 사용자 중요도를 적용한다.

## 7. DB 계약

### stocks 확장

| 컬럼 | 설명 |
|---|---|
| id | 내부 안정 PK |
| market, exchange, symbol | canonical 식별자 |
| name, english_name | 표시 이름 |
| normalized_name, normalized_english_name | 검색용 정규화 값 |
| instrument_type | STOCK / ETF / ETN / PREFERRED / ADR 등 |
| currency | 거래 통화 |
| provider, provider_instrument_id | 마스터 출처와 안정 ID |
| isin | 제공되는 경우 국제 식별자 |
| active, tradable, status | 활성·거래 가능·상장 상태 |
| listed_at, delisted_at | 제공되는 경우 상장 이력 |
| catalog_updated_at | 공급자 마스터 기준 시각 |
| created_at, updated_at | 내부 변경 시각 |

제약과 인덱스:

- UNIQUE `(market, symbol)`
- 가능한 경우 UNIQUE `(provider, provider_instrument_id)`
- `symbol`, 정규화 종목명 prefix 인덱스
- 한국어·영문 부분 검색용 trigram 또는 동등한 검색 인덱스
- `active`, `market`, `instrument_type` 복합 필터 인덱스

추가 테이블:

- `stock_aliases(stock_id, alias, normalized_alias, alias_type)`
- `instrument_catalog_sync_runs(provider, started_at, finished_at, status, received_count, inserted_count, updated_count, deactivated_count, error_code)`
- 선택 사항 `stock_access_state(stock_id, last_viewed_at, last_data_requested_at, priority, availability)`

## 8. UI 계약

### 8.1 전역 검색

- 데스크톱 topbar와 모바일 홈 상단에 동일한 검색 진입점 제공
- placeholder: `종목명 또는 심볼 검색`
- 결과에 종목명, symbol, 시장, 통화와 유형 표시
- 키보드 ↑/↓ 이동, Enter 선택, Esc 닫기와 screen reader 상태 알림
- 최근 본 종목과 관심종목 바로가기
- 동일 이름·유사 symbol은 시장 badge로 구분
- 빈 결과에는 검색어 수정 안내와 지원 시장 표시

### 8.2 상세 전환

결과 선택 즉시 URL과 `selectedStock`을 갱신한다. 다음 영역은 모두 고정값이 아니라 같은 `stockId/market/symbol`을 사용한다.

- 현재가와 실시간 연결
- 가격 차트와 기술지표
- AI 기술지표 해설
- 뉴스와 AI 뉴스 요약
- 공시와 일일 변화 브리핑
- 관심종목·포트폴리오·알림 추가

현재 `AiNewsSummary symbol="000660"` 고정값은 제거한다. 브라우저 새로고침·뒤로가기·공유 URL에서도 선택 종목을 복원한다.

### 8.3 상태 표시

- `검색 중`, `데이터 준비 중`, `일부 데이터만 제공`, `공급자 지연`, `지원하지 않는 종목`을 구분한다.
- 데이터가 없을 때 네 종목 fixture의 값을 다른 종목처럼 대체 표시하지 않는다.
- 마지막 성공 데이터의 source·asOf·freshness를 각 카드에 표시한다.

## 9. 캐시·쿼터·장애

- 종목 검색 결과 캐시 키: 정규화 query + market + type + page + catalogVersion
- 로컬 검색 캐시는 5~30분, 마스터 갱신 시 catalogVersion 변경으로 무효화
- quote·일봉·뉴스 TTL은 `06_DATA_PROVIDER_SPEC.md`의 시장 상태별 freshness를 사용
- 동일 종목 data-load 요청은 single-flight로 합친다.
- 외부 공급자 장애에도 로컬 종목 검색과 기존 상세 데이터 조회는 유지한다.
- 429 시 검색 결과는 유지하고 해당 데이터 카드에 재시도 가능 시각을 표시한다.
- 사용자 한 명의 빠른 종목 전환이 쿼터를 소진하지 않도록 사용자·IP·종목 기준 rate limit을 둔다.

## 10. DEMO와 LIVE

### DEMO

- 기존 4종목은 시세·차트·뉴스·AI까지 완전한 시연 경로를 유지한다.
- 검색 UI와 pagination을 검증할 수 있도록 합성 종목 마스터 fixture를 충분히 제공한다.
- 상세 데이터가 없는 합성 종목은 `METADATA_ONLY`로 명확히 표시하고 임의 가격을 만들지 않는다.

### LIVE

- KRX와 미국 종목 마스터를 동기화한다.
- 계정 권한과 공급자 범위 안에서 선택 종목의 실제 데이터를 로딩한다.
- 지원하지 않는 거래소·유형을 검색 결과에서 숨기기보다 `UNAVAILABLE` 사유와 함께 표시할 수 있다.

## 11. 보안과 데이터 품질

- 검색어를 SQL 문자열로 조합하지 않고 parameter binding을 사용한다.
- 외부 symbol을 URL에 넣기 전 시장별 허용 형식을 검증한다.
- API 키는 서버 Secret이며 검색 응답·브라우저·로그에 노출하지 않는다.
- 공급자 빈 마스터·급격한 종목 수 감소·중복 식별자·통화/거래소 누락을 저장 전 검증한다.
- 상장폐지 종목의 기존 관심종목·포트폴리오 이력은 삭제하지 않는다.
- 검색 로그를 수집할 경우 사용자 ID를 최소화하고 보존 기간을 설정한다.

## 12. 구현 순서

1. `InstrumentCatalogProvider`와 시장 중립 DTO
2. KIS·Finnhub 마스터 어댑터와 fixture 계약 테스트
3. stocks 확장 migration, alias·sync run 테이블과 검색 인덱스
4. 일일 마스터 upsert·비활성화 안전장치
5. 검색 service와 pagination·정렬·캐시
6. canonical 상세 endpoint와 symbol 단독 호환 계층
7. data-load orchestration, single-flight와 부분 실패 계약
8. 동적 WebSocket 구독 집합
9. 전역 검색 UI, URL 라우팅과 모든 카드의 selectedStock 연결
10. 관리자 마스터 freshness·쿼터·수집 실패 지표
11. AC-13 자동 테스트와 LIVE smoke

## 13. 인수 조건

- [x] `삼성`, `005930`, `Apple`, `AAPL` 검색이 이름·심볼·시장과 함께 올바르게 반환된다.
- [x] symbol·이름 완전 일치가 부분 일치보다 먼저 나오고 pagination 중 중복·누락이 없다.
- [x] 검색 요청은 외부 공급자를 호출하지 않고 로컬 카탈로그만 사용한다.
- [x] 검색 결과 선택 후 URL, 현재가, 차트, 기술지표, 뉴스와 AI 기능이 모두 같은 `(market, symbol)`을 사용한다.
- [x] `METADATA_ONLY` 종목은 선택·관심종목 등록 시 온디맨드 로딩 후 READY·PARTIAL 또는 명확한 부분 실패 상태가 된다.
- [ ] 빠른 검색·종목 전환에서 이전 응답이 현재 선택을 덮어쓰지 않는다.
- [x] 실시간 구독은 선택·관심·보유 종목으로 제한되고 해제·상한 정책이 동작한다.
- [x] 동일 data-load 동시 요청은 외부 호출 한 번으로 합쳐진다.
- [x] 동일 symbol의 다른 시장 후보가 안전하게 구분되고 canonical URL로 복원된다.
- [ ] 거래정지·상장폐지·마스터 빈 응답이 기존 사용자 데이터와 전체 catalog를 삭제하지 않는다.
- [ ] 데스크톱·390px 모바일·키보드·screen reader에서 검색과 선택이 가능하다.
- [x] DEMO 초기 4종목과 LIVE 전체 검색 범위를 UI와 발표에서 혼동하지 않는다.

미완료 항목이 남아 있으면 “원하는 국내·미국 종목 검색 지원”을 구현 완료로 표시하지 않는다.
