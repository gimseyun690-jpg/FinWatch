# FinWatch 테스트·인수 명세

- 문서 상태: 초안 v0.1
- 기준일: 2026-07-13
- 적용 범위: FinWatch MVP 기능, 데이터, 보안, PWA와 운영 배포 인수
- 관련 문서: `01_REQUIREMENTS.md`, `03_API_SPEC.md`, `04_DB_SCHEMA.md`, `06_DATA_PROVIDER_SPEC.md`, `07_USER_FEATURE_SPEC.md`, `08_AI_OPERATION_SPEC.md`, `09_SECURITY_DEPLOYMENT_SPEC.md`, `11_TECHNICAL_ANALYSIS_SPEC.md`

## 1. 목적과 판정 원칙

이 문서는 “구현했다”를 재현 가능한 테스트와 증적으로 판정하기 위한 기준이다. 정상 화면 한 번 확인만으로 완료 처리하지 않으며, 기능별 정상·경계·권한·장애 경로를 함께 검증한다.

- **필수(MUST)** 항목과 MVP 인수 시나리오는 모두 통과해야 한다.
- 테스트는 독립적이고 반복 가능해야 하며 실행 순서와 기존 로컬 DB 상태에 의존하지 않는다.
- 자동화 가능한 회귀 검사는 CI에 둔다. 외부 공급자 실제 호출, 복구 훈련, 기기 설치처럼 자동화 비용이 큰 검사는 통제된 수동 증적을 허용한다.
- 테스트 실패를 단순 재실행으로 숨기지 않는다. flaky 원인을 기록하고 수정하거나 해당 검사를 명시적으로 격리한 동안 배포 예외 승인을 받는다.
- 확정되지 않은 threshold는 `TBD`로 표시한다. `배포 전 게이트`인 TBD는 수치와 측정 환경을 정하기 전 운영 배포할 수 없다.

## 2. 현재 테스트 기준선

2026-07-14 저장소 기준으로 백엔드 단위·통합·공급자 계약 테스트와 프런트 Playwright E2E를 함께 실행한다.

| 범위 | 현재 자동 검증 |
|---|---|
| 기술지표 단위 | 이동평균·RSI·MACD 관련 3개 |
| 뉴스 전처리 단위 | HTML·URL·중복 문장·길이 제한 1개 |
| Health Web slice | `/api/v1/health` 1개 |
| 인증·권한 통합 | 로그인, 401, USER/ADMIN 403/허용, 잘못된 비밀번호 2개 |
| 종목 통합 | 상세·90일 가격·기술지표, 데모 관심종목 4개의 지표 계산 2개 |
| AI 통합 | 첫 MISS와 두 번째 HIT, 분석·사용량 row 수 1개 |
| 관리자 AI 통합 | cache 절감 지표와 사용량 정렬 1개 |
| 관심종목 통합 | 사용자별 CRUD 격리, 중복·없는 삭제 1개 |
| 실시간 시세 단위 | KIS 46필드 체결 파싱·부호, Finnhub trade 파싱, 오래된 틱 폐기 |
| 실시간 화면 E2E | snapshot 수신, 공급자 2/2 상태, KIS 실시간 배지·TICK·현재가 반영 |

통합 테스트는 `demo` 프로필의 H2, Mock AI와 메모리 cache를 사용한다. 별도 HTTP fixture로 KIS·NAVER API HUB·Finnhub·Gemini 계약과 오류 정규화를 검증하며, Playwright E2E는 로그인·WebSocket snapshot·실시간 가격 표시·상세 차트·지표 설정 유지·390px 모바일 overflow와 터치 크기를 검증한다. 실제 LIVE smoke에서는 KIS WebSocket의 연속 틱 시각·가격 변경과 Finnhub 연결 및 폐장 시 REST snapshot 유지 여부를 확인한다. 실제 PostgreSQL·Redis serialization/TTL, 부하 테스트와 CI workflow는 아직 별도 게이트로 남아 있다.

따라서 현재 자동 테스트의 통과는 핵심 Vertical Slice의 회귀 신호이지만 AWS 운영 준비 완료를 의미하지 않는다.

## 3. 테스트 환경과 데이터 원칙

### 3.1 환경

| 단계 | DB/cache | 외부 API | 목적 |
|---|---|---|---|
| 단위 | 없음 또는 in-memory 객체 | 호출 없음 | 계산·업무 규칙을 빠르게 검증 |
| API slice | 필요한 컴포넌트 Mock | 호출 없음 | validation, JSON, HTTP status와 보안 filter 검증 |
| 통합 | Testcontainers PostgreSQL 17·Redis 8 | HTTP stub | Flyway, JPA, 제약, cache와 공급자 adapter 검증 |
| E2E | 스테이징과 같은 구성의 격리 환경 | 기본 stub/Mock, 선택적 제한 실호출 | 브라우저부터 DB까지 사용자 흐름 검증 |
| 운영 smoke | 운영 RDS·Redis | 설정된 운영 공급자 | 배포 직후 최소 read/write와 연결 확인 |

H2 테스트는 빠른 피드백용으로 유지할 수 있지만 PostgreSQL 전용 타입, index, timezone, 제약 검증을 대체하지 않는다. Redis 대역 역시 실제 serializer와 TTL 검증을 대체하지 않는다.

### 3.2 fixture와 격리

- 테스트 데이터는 합성 데이터이며 운영 뉴스 본문, 이메일, 비밀번호, API 키를 사용하지 않는다.
- USER A, USER B, ADMIN을 분리해 객체 소유권과 권한을 검증한다.
- 현재 시각에 의존하는 계산은 `Clock`을 주입하거나 시간을 고정한다. DB와 API 기준은 UTC다.
- 가격·환율·AI 토큰 단가 fixture는 명시적으로 고정하고 예상 계산식을 테스트 코드에 드러낸다.
- 각 테스트는 transaction rollback, schema 재생성 또는 고유 namespace로 격리한다. 테스트 순서에 의존하지 않는다.
- AI cache key에는 기능, 대상, prompt version이 들어간다는 계약을 fixture 이름과 assertion으로 검증한다.
- 랜덤 입력을 쓰면 실패 재현을 위한 seed를 리포트에 남긴다.

## 4. 테스트 계층과 필수 범위

| 계층 | 필수 대상 | 실행 시점 | 실패 영향 |
|---|---|---|---|
| 단위 | 계산, validation, 상태 전이, 비용·cache key | 모든 PR | merge 차단 |
| API/Web slice | 요청/응답, 오류 코드, 인증·인가 | 모든 PR | merge 차단 |
| DB·Redis 통합 | Flyway, repository, transaction, TTL·복구 | 모든 PR 또는 필수 통합 workflow | merge 차단 |
| 외부 계약 | Gemini·시장·뉴스 adapter와 HTTP stub | 모든 PR | merge 차단 |
| 프런트 단위·컴포넌트 | 상태·포맷·오류·접근성 | 모든 PR | merge 차단 |
| E2E | 핵심 USER/ADMIN 흐름 | main 및 release | 운영 승격 차단 |
| 성능·복원력 | cache, 동시성, timeout, 부하 | release 또는 주요 구조 변경 | 기준 초과 시 배포 차단 |
| 운영 smoke·복구 | 실제 배포 연결, backup restore | 배포 직후/정기 | 공개·배포 완료 차단 |

## 5. 백엔드 단위·API 테스트

### 5.1 공통 API

다음을 모든 endpoint군에서 대표적으로 검증한다.

- 성공 envelope의 `success`, `data`, `message`, UTC `timestamp`
- validation 실패의 field별 오류, 허용하지 않은 enum·음수·0·과도한 길이
- 존재하지 않는 자원의 404와 기능별 오류 코드
- 중복 생성의 409, 공급자 제한의 429, 외부 장애의 502/503
- 잘못된 JSON과 지원하지 않는 Content-Type·HTTP method
- 페이지가 0 미만, size가 한도 초과, 허용하지 않은 sort field인 경우의 계약
- Entity와 password hash, 내부 stack, Secret이 응답에 노출되지 않음

### 5.2 도메인별 단위 범위

- 종목·가격: 등락액·등락률, 통화, UTC `asOf`, 데이터 부족·중복·역순 가격 처리
- 기술지표: `11_TECHNICAL_ANALYSIS_SPEC.md`의 최소 입력 개수, 계산식·초기값·정밀도, 정확한 경계값, flat price, 데이터 품질과 BUY/NEUTRAL/SELL 규칙
- 관심종목: symbol trim·대문자화, 중복, 없는 종목, 현재 MVP의 무제한 정책과 향후 제한 도입 시 오류 계약
- 포트폴리오: 수량·평균매수가 validation, 추가/수정/삭제, 평가액·손익·수익률·환율·반올림 규칙
- 가격알림: ABOVE/BELOW 경계 포함 여부, ACTIVE/TRIGGERED/DISABLED 상태 전이, 중복 trigger 방지와 재활성화
- 뉴스: 정렬·페이지, 출처·원문 URL·게시 시각, 본문 제공 권한, 여러 종목 연결
- AI: 전처리 최대 길이, prompt version, cache key, 토큰·비용 반올림, 실패 로그, 안전한 sentiment/parser validation
- 관리자: 날짜 경계와 timezone, cache hit rate 분모, 실패 요청 포함 규칙, 비용 정렬·페이지 제한

포트폴리오·알림의 구체 예상값은 `07_USER_FEATURE_SPEC.md`, AI 규칙은 `08_AI_OPERATION_SPEC.md`를 source of truth로 삼는다.

## 6. PostgreSQL·Redis 통합 테스트

### 6.1 PostgreSQL/Flyway

필수 검사는 다음과 같다.

1. 빈 PostgreSQL 17에 V1부터 최신 migration까지 적용되고 JPA `validate`와 애플리케이션 context가 성공한다.
2. 이미 최신인 DB에 재시작해도 데이터와 schema가 변하지 않는다.
3. `users.email`, `(market, symbol)`, 뉴스 외부 ID, AI 대상·prompt, 관심종목 사용자·종목 unique 제약이 실제 DB에서 동작한다.
4. FK와 delete 정책이 `04_DB_SCHEMA.md`와 일치하고 고아 row가 생기지 않는다.
5. `TIMESTAMPTZ`, 금액·비용 numeric 정밀도, 정렬 index를 실제 PostgreSQL에서 검증한다.
6. 새 migration이 직전 release schema와 데이터에 적용된다. 파괴적 변경이면 확장·이관·정리 각 단계와 이전 앱 호환성을 따로 검증한다.
7. migration 실패 시 새 애플리케이션의 readiness가 올라오지 않는다.

### 6.2 Redis/cache

- MISS는 공급자를 한 번 호출하고 DB 분석과 Redis cache를 만든다.
- 동일 뉴스·prompt version의 HIT는 공급자를 호출하지 않고 응답 토큰·비용이 cache 계약과 일치한다.
- prompt version이나 대상이 바뀌면 다른 key로 MISS가 발생한다.
- TTL 전후를 고정 clock 또는 짧은 시험 TTL로 검증한다.
- Redis key가 사라져도 DB의 영속 분석으로 cache를 복구하고 공급자를 다시 호출하지 않는다.
- Redis에 손상되거나 호환되지 않는 값이 있으면 안전하게 무시·교체하며 전체 서비스가 stack trace를 노출하지 않는다.
- Redis 연결 장애에서 `08_AI_OPERATION_SPEC.md`의 degraded 정책이 동작하고 종목·인증 등 비AI 기능은 유지된다.
- 동시에 같은 MISS가 들어올 때 공급자 호출·DB unique 충돌이 폭증하지 않는지 검증한다. 현재 구현에는 명시적 single-flight/분산 lock이 없으므로 이 검사는 목표 상태이며 구현 전에는 실패할 수 있다.

## 7. 외부 API 계약과 대역

정규 CI는 실제 유료·제한 API를 호출하지 않는다. WireMock, MockWebServer 또는 동등한 로컬 HTTP stub으로 adapter의 wire contract를 검증한다.

### 7.1 Gemini

| 응답 조건 | 필수 검증 |
|---|---|
| 정상 구조화 JSON | summary, keyPoints, keywords, sentiment, modelVersion, usage token 매핑 |
| usage metadata 없음 | 명시된 token 추정 fallback과 비용 계산 |
| 빈 candidate/part | 외부 공급자 오류로 변환, 성공 분석 저장 금지 |
| malformed JSON·잘못된 enum | parser 오류 처리와 안전한 오류 응답 |
| 400/401/403 | 설정 또는 요청 오류 분류, API key 비노출 |
| 429 | `Retry-After` 존중 여부, 제한된 backoff, 중복 비용 방지 |
| 5xx·connection reset·timeout | 제한된 retry/circuit 정책, 실패 로그와 사용자 오류 |
| 느린 응답 | client timeout 이후 thread/resource 반환 |

Stub은 `x-goog-api-key` header가 존재하는지만 검사하고 값을 테스트 출력에 남기지 않는다. 실 Gemini smoke는 운영과 분리된 제한 키·고정 짧은 기사·호출 횟수 상한으로 release 전에 1회 수행할 수 있다. `AI_PROVIDER=gemini` 운영 배포라면 성공, 비용 기록, 로그 비노출 증적이 필수다.

### 7.2 시장·뉴스 공급자

`06_DATA_PROVIDER_SPEC.md`에서 선택한 공급자마다 다음 계약을 대역으로 검증한다.

- 정상 pagination·symbol·시장·통화·timezone 매핑
- 중복 외부 ID와 본문 hash의 idempotent 저장
- 빈 결과, 휴장, 늦은 데이터, 수정 데이터
- 401/403, 404 symbol, 429 quota, 5xx, timeout, schema 변경
- 재시도 후 중복 row·중복 알림·중복 AI 분석이 생기지 않음
- 원문 저장 허용 범위와 출처·URL·수집/게시 시각 보존

실제 공급자가 TBD인 동안 adapter 계약 테스트도 미완료다. 공급자 선정과 sandbox 자격 증명은 **배포 전 게이트**다.

허용 출처 본문 수집기를 추가하면 다음을 HTTP 대역으로 검증한다.

- 등록되지 않은 도메인은 `METADATA_ONLY`로 처리되어 본문 요청이 발생하지 않는다.
- 허용 도메인의 정상 HTML에서 본문만 추출하고 script·style·광고·tracking 요소를 제거한다.
- robots 금지, 429·`Retry-After`, timeout, redirect loop, 과대 응답과 잘못된 Content-Type을 거절한다.
- redirect 대상마다 허용 정책을 다시 검사하고 localhost·사설 IP·link-local·cloud metadata endpoint를 차단한다.
- `ETag`·`Last-Modified` 조건부 요청과 동일 `contentHash` 멱등 저장을 검증한다.
- paywall·로그인·CAPTCHA 우회 코드를 포함하지 않으며 정책 비활성화 즉시 후속 수집을 중단한다.
- 본문 수정으로 hash가 바뀌면 새 AI 분석을 만들고 이전 캐시를 최신 결과로 반환하지 않는다.
- 프롬프트 인젝션 fixture에서 내부 지시·Secret을 노출하지 않고 근거 segmentId가 실제 입력에 존재한다.

## 8. 프런트엔드 단위·컴포넌트·E2E

### 8.1 목표 도구와 범위

브라우저 E2E는 Playwright를 사용한다. 계산과 API 계약은 백엔드 JUnit, 타입·번들 계약은 TypeScript build와 lint로 검증한다. 컴포넌트 단위 테스트가 필요해지면 Vitest + Testing Library를 추가한다.

컴포넌트 테스트는 다음을 포함한다.

- 로딩, 빈 결과, 재시도 가능한 오류, 401 session 만료, 403 권한 오류
- 가격·통화·수익률·UTC/현지 시각 포맷과 상승/하락의 텍스트·아이콘 병행
- 관심종목 추가·중복·삭제와 낙관적 UI rollback
- 포트폴리오 입력 validation과 서버 계산 결과 표시
- 알림 상태와 trigger 시각 표시
- AI MISS/HIT, 생성 중 중복 클릭 방지, 출처·면책문구, 공급자 실패
- ADMIN 메뉴의 role 기반 노출과 서버 403 처리
- keyboard 탐색, focus, label, dialog와 status message의 기본 접근성
- 상세 차트의 OHLC 변환, 기간 변경 요청 취소, 로딩·빈 결과·DEMO 상태와 늦은 응답 무시
- 캔들 확대·이동·십자선, 전체화면·포커스 복귀, 추세선·수평선 좌표 유지와 편집

### 8.2 필수 E2E 흐름

1. USER 로그인 후 종목 상세·가격 차트·기술지표·뉴스를 조회한다.
2. 관심종목을 추가·새로고침·삭제하고 USER B 목록과 격리됨을 확인한다.
3. 포트폴리오 보유 종목을 생성·수정·삭제하고 서버 계산값을 확인한다.
4. 가격알림을 만들고 fixture 가격을 경계값 전후로 바꿔 상태 전이를 확인한다.
5. 뉴스 AI 요약을 처음 요청해 MISS, 다시 요청해 HIT와 비용 0을 확인한다.
6. USER는 관리자 URL·API에 접근할 수 없고, ADMIN은 지표와 사용량 로그를 본다.
7. 만료·변조 token에서 session이 안전하게 종료되고 보호 데이터가 남지 않는다.

종목 상세 E2E에서는 `11_TECHNICAL_ANALYSIS_SPEC.md`의 1M·3M·6M·1Y·ALL 기간, 캔들, 확대·이동, 십자선 OHLC, 전체화면과 추세선·수평선 생성·수정·삭제를 데스크톱에서 검증한다. 모바일에서는 기간 선택, 핀치·이동, 십자선 탐색과 그리기 도구의 최소 터치 영역을 검증한다.

브라우저별 최소 범위는 최신 Chromium 필수, Firefox/WebKit 또는 실제 모바일 브라우저 추가 여부가 TBD이며 **배포 전 게이트**다.

## 9. 데이터 품질·보안 테스트

### 9.1 데이터 품질

- 모든 금액에 통화가 있고 서로 다른 통화를 환율 없이 합산하지 않는다.
- DB 시간은 UTC로 저장하고 KRX·미국장 표시 변환과 날짜 경계를 검증한다.
- 가격·뉴스에는 `source`, `asOf/publishedAt/collectedAt`이 있으며 stale 기준을 넘으면 표시·경보한다.
- 동일 공급자 이벤트를 반복 수신해도 가격·뉴스·알림·AI 분석이 중복 생성되지 않는다.
- 계산 입력이 부족하거나 stale이면 임의 신호를 만들지 않고 명세된 unavailable 상태를 반환한다.
- backup을 별도 DB로 복원한 뒤 row 수, FK, 핵심 합계와 로그인·조회 smoke가 일치한다.

### 9.2 인증·인가와 애플리케이션 보안

- 공개 endpoint 외 인증 없는 요청은 401, USER의 ADMIN 접근은 403이다.
- 만료, 잘못된 signature, 다른 issuer, 누락된 roles/userId, 변조 token을 거부한다.
- USER A의 ID나 symbol/path를 바꿔 USER B의 관심종목·포트폴리오·알림을 조회·수정·삭제할 수 없다.
- 운영 프로필은 기본 JWT Secret, 기본 DB 비밀번호, localhost CORS, 데모 계정으로 시작하지 않는다.
- 로그인·AI endpoint rate limit과 429를 검증한다.
- 허용 Origin만 preflight와 실제 CORS header를 받고 임의 Origin은 거부된다.
- XSS payload가 뉴스 제목·AI 결과·사용자 입력에서 실행되지 않고 CSP가 필요한 외부 자원만 허용한다.
- SQL injection, 과도한 page size·본문 길이, 잘못된 symbol과 enum이 안전하게 거부된다.
- 로그·응답·프런트 bundle·Docker image·SBOM·테스트 artifact에 API key, 비밀번호, JWT가 없다.
- 의존성·container·Secret scan을 실행한다. Critical은 배포 차단, High는 수정 또는 책임자·만료일이 있는 예외 승인이 필요하다.

침투 테스트의 범위와 도구는 TBD다. 최소한 OWASP ZAP baseline 또는 동등한 DAST를 스테이징에 적용할지는 **배포 전 게이트**로 결정한다.

## 10. 성능·동시성·복원력 테스트

### 10.1 측정 원칙

- 측정 환경의 CPU·메모리·DB 사양, 데이터량, 동시 사용자, warm-up, 실행 시간과 commit SHA를 기록한다.
- 외부 AI 응답시간은 애플리케이션 SLO와 별도로 측정한다. 공급자의 느린 응답을 cache HIT 성능과 섞지 않는다.
- 평균만 보지 않고 p50, p95, p99, 오류율과 resource 사용량을 기록한다.
- 성능 SLO, 부하 모델, 최대 기사 길이, 동시 사용자 수는 TBD이며 모두 **배포 전 게이트**다.

### 10.2 필수 시나리오

- 종목 상세·관심종목 목록·관리자 로그 pagination의 정상 데이터와 큰 데이터셋 조회
- AI cache HIT 연속 요청에서 공급자 호출 0회, 안정된 p95와 오류율
- 동일 뉴스 MISS 동시 요청에서 공급자 호출 수·DB unique conflict·응답 일관성
- 서로 다른 뉴스 MISS가 동시 발생할 때 thread pool, DB pool, Gemini quota 보호
- Redis latency·connection loss, Gemini timeout·429·5xx, DB 일시 중단 후 복구
- 잘못된 클라이언트의 빠른 재시도와 rate limit
- 컨테이너 종료·재시작 중 요청 정리와 readiness 전환
- Flyway 실행 시간과 배포 허용 점검 시간 내 완료 여부

운영 배포 전 최소 수치 기준을 `09_SECURITY_DEPLOYMENT_SPEC.md`의 경보 threshold와 동일하게 확정한다. 기준 미달은 최적화, 용량 변경 또는 명시적 scope/SLO 조정 후 다시 승인한다.

## 11. PWA·오프라인 테스트

현재 Service Worker는 production build에서만 등록되고 `/api/` GET은 cache하지 않으며 app shell과 같은 정적 요청은 network-first로 cache한다. 다음을 실제 production build·HTTPS 또는 localhost에서 검증한다.

- manifest 이름, start URL, standalone display, theme, 모든 필수 icon이 유효하다.
- 첫 방문 후 app shell이 cache되고 offline 재방문에서 최소 shell과 명확한 offline 상태가 보인다.
- `/api/`의 인증 응답, 포트폴리오, 관리자 데이터, AI 응답은 Cache Storage에 저장되지 않는다.
- offline 중 쓰기 요청을 성공처럼 표시하지 않고 재시도 또는 실패 상태를 제공한다.
- Service Worker 버전 변경 시 이전 cache가 정리되고 새 UI가 무한 reload 없이 활성화된다.
- 손상·누락된 cache에서 흰 화면 대신 복구 가능한 오류가 나타난다.
- 설치 가능 여부와 standalone 실행을 Android 계열 실제 기기 또는 동등한 에뮬레이션으로 확인한다.
- 키보드, mobile viewport, 색상 대비, zoom과 screen reader 기본 탐색을 확인한다.

지원 브라우저·OS 목록, offline에서 제공할 정확한 화면, Lighthouse/접근성 수치 기준은 TBD이며 **배포 전 게이트**다. API 응답을 offline cache하는 요구가 추가되면 사용자별 민감 데이터 암호화·로그아웃 삭제 정책을 먼저 명세해야 한다.

## 12. CI 품질 게이트

### 12.1 PR 필수 게이트

```text
Backend: ./gradlew test
Frontend: pnpm install --frozen-lockfile -> pnpm lint -> pnpm build
Integration: PostgreSQL 17 + Redis 8 + Flyway + provider stubs
Security: secret scan + dependency/SAST scan
```

필수 판정:

- 모든 신규·기존 자동 테스트가 성공한다.
- TypeScript build와 lint 오류가 0이다.
- 빈 PostgreSQL migration과 현재 schema upgrade 검사가 성공한다.
- Critical 취약점과 Secret 탐지가 0이다. High 예외는 책임자·사유·만료일이 있어야 한다.
- 변경된 API·DB·업무 규칙은 해당 명세와 테스트를 같은 PR에서 갱신한다.
- test artifact와 실패 로그를 CI에서 확인할 수 있다.

전역·변경 라인 coverage threshold는 현재 baseline이 없어 TBD다. 숫자를 확정하는 것은 **배포 전 게이트**이며, 그 전에도 인증·사용자 격리·금액 계산·AI cache/비용·migration의 핵심 분기는 반드시 직접 테스트한다. coverage 수치만 맞추기 위한 무의미한 테스트는 완료로 인정하지 않는다.

### 12.2 main·release 게이트

- 동일 commit SHA의 backend/frontend 산출물과 image digest를 생성한다.
- SBOM과 container scan 결과를 보관한다.
- 스테이징 migration, readiness, E2E와 외부 공급자 stub 장애 시험을 통과한다.
- 운영이 Gemini를 쓰면 제한된 live smoke 결과와 비용·로그 redaction을 확인한다.
- 운영 backup 식별자와 rollback 명령, 변경 승인자를 기록한다.
- 운영 배포 후 smoke와 관찰 시간 동안 5xx·latency·비용 경보 이상이 없다.

CI/CD가 구현되기 전 수동 명령 결과는 임시 증적으로 허용하지만, 실행 환경·commit SHA·원본 로그·실행자를 모두 남겨야 한다. “로컬에서 됨”이라는 설명만으로 배포를 승인하지 않는다.

## 13. MVP 인수 시나리오

각 시나리오는 고정 fixture, 새 브라우저 session, 초기화된 cache 상태에서 실행하며 정상 결과와 DB·로그 증적을 함께 확인한다.

### AC-00 배포와 상태

1. 승인된 image digest로 빈 DB에 배포한다.
2. Flyway가 최신 버전까지 한 번 적용되고 JPA validation이 성공한다.
3. liveness와 readiness가 구분되어 정상이고 외부 health 응답에 세부 연결정보가 없다.
4. 버전, 환경, request ID가 Secret 없이 로그에서 식별된다.

판정: migration·readiness·기본 smoke 모두 성공해야 한다.

### AC-01 인증과 권한

1. 운영 방식으로 공급한 USER와 ADMIN이 로그인한다.
2. 인증 없는 보호 API는 401, USER의 관리자 API는 403, ADMIN은 200이다.
3. 만료·변조·다른 issuer token은 거부된다.
4. 로그아웃 또는 session 만료 후 보호 데이터에 접근할 수 없고 브라우저 영속 저장에 token이 남지 않는다.

판정: 데모 계정과 기본 JWT Secret이 운영에서 비활성이고 권한 우회가 없어야 한다.

### AC-02 종목 상세와 기술지표

1. 종목을 선택해 현재가, 등락, 거래량, source와 asOf를 확인한다.
2. 기간별 가격 목록과 MA, RSI, MACD, 종합 신호를 고정 fixture의 기대값과 비교한다.
3. 데이터 부족·stale fixture에서는 임의 신호 대신 명세된 상태와 면책문구를 표시한다.
4. 1M·3M·6M·1Y·ALL 기간을 바꾸면 해당 일봉 캔들이 표시되고, 늦은 이전 응답이 현재 기간을 덮어쓰지 않는다.
5. 확대·이동과 십자선 툴팁에서 선택 캔들의 날짜·OHLC·거래량이 원본 fixture와 일치한다.
6. 전체화면 진입·종료와 리사이즈 후 차트가 정상이며 키보드 포커스가 복귀한다.
7. 추세선과 수평선을 생성·수정·삭제하고 확대·이동·전체화면 후에도 앵커의 시간·가격이 유지된다.

판정: API 값, 화면 값, 계산 fixture와 timezone이 일치하고 `11_TECHNICAL_ANALYSIS_SPEC.md`의 상세 차트 필수 인수 조건이 모두 통과해야 한다.

### AC-03 관심종목과 사용자 격리

1. USER A가 종목을 추가하고 목록·새로고침에서 확인한다.
2. 중복 추가는 409, 없는 종목은 404/validation 계약을 따른다.
3. USER B와 ADMIN의 개인 목록에는 USER A 항목이 보이지 않는다.
4. USER B가 USER A 항목을 경로 조작으로 삭제할 수 없고 USER A만 삭제할 수 있다.

판정: CRUD 결과와 DB unique/FK, 소유권 격리가 모두 맞아야 한다.

### AC-04 포트폴리오

1. USER가 보유 종목, 수량, 평균매수가를 등록한다.
2. 최신 가격·환율 fixture로 매입금액, 평가금액, 손익, 수익률을 기대값과 비교한다.
3. 수정·삭제, 0·음수·과도한 소수, stale 가격, 혼합 통화 경로를 검증한다.
4. 다른 사용자의 holding ID 조작을 거부한다.

판정: `07_USER_FEATURE_SPEC.md`의 계산·반올림·통화 규칙과 일치해야 한다. 현재 기능 미구현이므로 이 시나리오는 현재 통과할 수 없다.

### AC-05 가격알림

1. ABOVE와 BELOW 알림을 경계값 양쪽에 생성한다.
2. 가격 fixture를 갱신해 미충족, 정확한 경계, 초과/미만의 상태 전이를 확인한다.
3. 같은 이벤트가 반복되어도 중복 trigger가 생기지 않고 수정·비활성·재활성 규칙이 맞다.
4. 데이터가 stale이거나 공급자가 실패하면 오탐 없이 명시된 상태가 보인다.

판정: `07_USER_FEATURE_SPEC.md`의 평가 주기·경계·재알림 규칙과 일치해야 한다. 현재 기능 미구현이므로 이 시나리오는 현재 통과할 수 없다.

### AC-06 AI 뉴스 요약 MISS/HIT와 복구

1. 분석이 없는 뉴스를 요청해 `cacheHit=false`, 구조화 결과, 실제 token·비용과 SUCCESS 로그를 확인한다.
2. 같은 뉴스·prompt version을 다시 요청해 `cacheHit=true`, 모델 호출 0회, 요청 비용·token 0을 확인한다.
3. Redis key만 삭제하고 재요청해 DB 분석으로 cache가 복구되고 모델을 다시 호출하지 않음을 확인한다.
4. prompt version을 바꾸면 새 MISS가 발생한다.
5. malformed, timeout, 429 응답에서 성공 분석이 저장되지 않고 실패 로그·오류·retry가 명세와 일치한다.

판정: `08_AI_OPERATION_SPEC.md`의 cache·비용·실패·동시성 기준을 모두 만족해야 한다.

### AC-07 관리자 AI 지표

1. 통제된 MISS와 HIT를 만든 후 기간별 요청, model call, cache hit rate, token, 비용, 절감 비용을 손계산과 비교한다.
2. 고비용·최신순 정렬과 pagination을 검증한다.
3. USER는 접근할 수 없고 ADMIN만 조회한다.
4. 실패 요청 포함 여부와 기간 시작·종료 UTC 경계가 명세와 일치한다.

판정: 화면·API·`ai_usage_logs` 집계가 같은 값을 보여야 한다.

### AC-08 외부 데이터 수집

1. 승인된 시장·뉴스 provider stub에서 새 데이터, 중복, 수정, 429·5xx·timeout을 순서대로 제공한다.
2. 수집·정규화·종목 연결·중복 제거·재시도가 `06_DATA_PROVIDER_SPEC.md`와 일치한다.
3. 사용자 화면에 source와 최신 시각, stale/degraded 상태가 표시된다.

판정: 공급자 계약과 저장 권한이 확정되고, 장애가 기존 정상 데이터를 오염시키지 않아야 한다. 실제 공급자 미구현 동안 현재 통과할 수 없다.

### AC-09 PWA와 모바일

1. production build를 온라인에서 한 번 연 뒤 설치·standalone 실행을 확인한다.
2. offline 재방문에서 app shell과 offline 상태가 보이고 보호 API 데이터가 Cache Storage에 없다.
3. 새 version을 배포해 Service Worker와 UI가 안전하게 갱신된다.
4. mobile viewport와 keyboard에서 핵심 흐름을 완료한다.

판정: 흰 화면, 오래된 인증 데이터 노출, 무한 갱신이 없어야 한다.

### AC-10 backup, rollback과 비용 경보

1. RDS snapshot을 별도 인스턴스에 복원해 핵심 row·FK·로그인·조회가 정상임을 확인한다.
2. 의도적으로 실패하는 새 버전에서 readiness가 트래픽을 막고 직전 digest로 rollback한다.
3. 테스트 AI 비용과 AWS 예산 임계값으로 알림을 발생시켜 지정 채널 수신을 확인한다.
4. 복구 시각과 데이터 손실 범위를 RPO/RTO 목표와 비교한다.

판정: `09_SECURITY_DEPLOYMENT_SPEC.md`의 Runbook과 목표 시간 안에 재현 가능해야 한다.

## 14. 결함 심각도와 인수 판정

| 등급 | 예 | 인수 처리 |
|---|---|---|
| Blocker | 인증 우회, Secret 노출, 데이터 손상, migration/복구 불가, 핵심 흐름 전체 중단 | 배포 금지 |
| Critical | 사용자 간 데이터 노출, 잘못된 금액·알림, AI 비용 무제한, 지속적인 5xx | 배포 금지 |
| Major | 핵심 기능 일부 실패, 주요 브라우저/PWA 사용 불가, SLO 큰 초과 | 원칙적으로 배포 금지; scope 제외는 문서 승인 필요 |
| Minor | 우회 가능한 표시·문구·비핵심 UI 문제 | 담당자·기한을 정하고 승인 시 배포 가능 |

MVP 합격 조건:

1. AC-00~AC-10 중 MVP scope에 포함된 모든 시나리오가 통과한다.
2. Blocker·Critical·미승인 Major가 0건이다.
3. PR/release 품질 게이트와 운영 체크리스트가 통과한다.
4. 모든 배포 전 게이트 TBD가 값·책임자·검증 증적을 갖는다.
5. 요구사항에서 제외할 기능이 있으면 단순히 테스트를 생략하지 않고 `01_REQUIREMENTS.md`, API/DB/세부 명세와 시연 범위를 함께 변경·승인한다.

현재 포트폴리오, 가격알림, 실제 시장·뉴스 공급자, 운영 배포가 미완료이므로 전체 MVP 인수 상태는 **미합격/진행 중**이다. 기존 Vertical Slice와 테스트의 가치를 부정하는 판정이 아니라, 공개 운영 완료와 핵심 시연 완료를 구분하기 위한 상태다.

## 15. 증적과 서명

release별로 다음 증적을 하나의 링크 또는 manifest에서 추적 가능하게 보관한다.

- commit SHA, branch/tag, image tag와 digest, 실행 환경·시각·실행자
- 백엔드 JUnit XML·요약, 프런트 단위/E2E report, 실패 screenshot·trace
- lint·TypeScript build·coverage·Flyway 통합 결과
- OpenAPI/외부 stub 계약 결과, 성능 raw result와 환경 정보
- secret/dependency/container scan, SBOM과 승인된 예외
- AC별 화면 screenshot 또는 짧은 영상, 대표 API 응답(민감정보 제거), DB 검증 query 결과
- 운영 readiness·smoke, CloudWatch dashboard·경보 시험
- backup ID, restore·rollback 실행 기록, 실제 RPO/RTO
- 알려진 결함, scope 제외, 위험 수용 사유·승인자·만료일

증적 경로와 보존 기간은 TBD이며 **배포 전 게이트**다. 개인 프로젝트에서는 제품·개발·운영 역할을 한 사람이 맡을 수 있지만, 다음 형식으로 판단을 명시한다.

```text
Release:
Commit / Image digest:
Environment:
Acceptance result: PASS | FAIL | CONDITIONAL
Open defects / accepted risks:
Product approval (name/date):
Technical approval (name/date):
Operations approval (name/date):
Evidence link:
```
