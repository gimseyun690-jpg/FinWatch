# FinWatch 빠른 구현 순서

날짜별 일정 대신, 시연 가능한 핵심 흐름을 가장 빨리 완성하는 순서로 진행한다. 한 단계가 동작하면 바로 다음 단계로 이동하며 UI 세부 조정은 핵심 흐름 뒤에 한다.

## 완료 정의

- 요구사항과 API 명세를 충족한다.
- 대표 정상 경로와 오류 경로 테스트가 통과한다.
- 로딩·빈 상태·오류 상태가 사용자 화면에 표시된다.
- API 또는 DB 변경 시 관련 명세를 함께 수정한다.
- 비밀정보는 Git에 포함하지 않는다.

## Step 0. 프로젝트 기반

- [x] GitHub 저장소 연결
- [x] 초기 기획 자료 검토와 현재 저장소 기준 핵심 명세 작성
- [x] Spring Boot 4.1 / Java 21 프로젝트 생성
- [x] React 19 / TypeScript / Vite 8 프로젝트 생성
- [x] PostgreSQL·Redis Compose 작성
- [x] Health API와 프론트 연결
- [x] 반응형 PWA 기본 화면
- [x] 백엔드 테스트와 프론트 빌드·린트

## Step 1. 종목 상세 Vertical Slice

목표: SK하이닉스 상세 화면에서 실제 API 응답으로 가격과 기술 지표를 확인한다.

- [x] `stocks`, `market_prices` 마이그레이션과 데모 데이터
- [x] 종목 목록·상세·가격 이력 API
- [x] 이동평균선, RSI, MACD 계산 서비스
- [x] 기술 지표 단위·API 통합 테스트
- [x] 종목 상세 페이지와 차트
- [x] 로딩 상태와 API 실패 시 데모 폴백

## Step 1.1 기술적 분석 상세 차트 보강

기존 SVG 종가 차트를 `11_TECHNICAL_ANALYSIS_SPEC.md` 기준의 인터랙티브 차트로 교체했다. Playwright에서 데스크톱·390px 모바일 상호작용과 접근 가능한 이름을 검증한다.

- [x] Lightweight Charts 캔들 차트로 교체
- [x] 1M·3M·6M·1Y·ALL 기간 선택과 요청 취소
- [x] 확대·이동·십자선과 OHLC·거래량 툴팁
- [x] 전체화면·리사이즈·키보드 포커스 복귀
- [x] 추세선·수평선 생성·수정·삭제와 데이터 좌표 유지
- [x] KIS·Finnhub WebSocket 체결의 인메모리 1분 OHLCV 집계
- [x] 미국 프리마켓·정규장·애프터마켓 세션 분류, 신선한 provider timestamp 검증과 전체 960분 보존
- [x] 1분 봉 REST 조회와 `candles`·`candle` WebSocket 이벤트
- [x] 실제 일봉 기반 일봉/주봉/월봉과 실시간 1분봉 전환, 장중 시간축과 봉 간격별 그리기 분리
- [x] 데스크톱·모바일 상호작용·접근성 E2E 검증

## Step 1.2 포트폴리오 완성형 기술지표 확장

목표: 핵심 MVP의 MA·RSI·MACD를 유지하면서 거래량·변동성·교차 이벤트까지 설명할 수 있는 전문 기술적 분석 화면을 완성한다. 계산 계약과 인수 조건은 `11_TECHNICAL_ANALYSIS_SPEC.md` 17절을 따른다.

- [x] 거래량 히스토그램과 Volume MA20 패널
- [x] 볼린저 밴드 20·2 계산·API·오버레이
- [x] 단순 RSI14를 Wilder RSI14로 버전 전환
- [x] MA 골든·데드크로스, MACD 교차와 RSI 구간 진입·이탈 이벤트
- [x] ATR14와 현재가 대비 ATR 비율 계산·보조 패널
- [x] MA·거래량·볼린저 밴드·이벤트 표시/숨김과 RSI·MACD·ATR 패널 선택
- [x] 고정 데이터셋 독립 교차검증과 경계·동일값·불완전 구간 테스트
- [x] 데스크톱·모바일 가독성, 십자선 시점 정렬과 설정 유지 E2E 검증

## Step 1.3 국내·미국 종목 검색과 온디맨드 상세 조회

목표: 4개 데모 종목 제한을 제거하고 `14_STOCK_DISCOVERY_SPEC.md` 기준으로 원하는 KRX·미국 종목을 이름·심볼로 찾아 상세 화면을 연다.

- [x] KIS·Finnhub `InstrumentCatalogProvider`와 종목 마스터 동기화
- [x] LIVE 시작 후 및 24시간 주기의 안전한 전체 종목 마스터 자동 동기화
- [x] V14 stocks 확장, alias·sync run 테이블과 prefix 검색 인덱스
- [x] `GET /stocks/search`의 정렬·필터·pagination (분산 검색 캐시는 LIVE 마스터 이후)
- [x] `(market, symbol)` canonical 상세 API와 기존 symbol endpoint 충돌 호환 계층
- [x] 선택 종목 quote·일봉·뉴스·공시 온디맨드 data-load와 single-flight
  - [x] quote·일봉·뉴스 비동기 job, 상태 조회, freshness와 single-flight
  - [x] 부분 실패·DEMO metadata-only·지원 범위 오류 계약
  - [x] Open DART·SEC 종목별 공시 목록 수집 어댑터와 공식 공시 화면
- [x] 선택·관심·보유·활성 알림 종목 중심의 동적 WebSocket 구독·해제·상한
- [x] 전역 검색 UI, 250ms debounce·요청 취소·키보드 선택·URL 복원과 상세/차트/뉴스/AI selectedStock 연결
- [x] 관심종목 개수 제한 제거, 전체 카탈로그 검색·연속 등록·canonical 삭제 UI
- [x] 관심종목 등록 직후 실시간 구독 갱신과 quote·일봉·뉴스·공시 자동 준비
- [x] KIS 최대 약 5년 국내 일봉 기간 분할·호출 간격·부분 성공, Finnhub 일봉 권한 오류 시 KIS 해외 일봉 fallback
- [x] DEMO 검색 fixture, LIVE 공급자 계약과 AC-13 핵심 API·UI 테스트

## Step 1.4 USD/KRW 환율과 기준통화 평가

목표: `15_FX_RATE_SPEC.md` 기준으로 환율을 표시하고 미국 주식 원통화 값을 보존하면서 혼합 포트폴리오의 현재 평가액을 KRW로 환산한다.

- [x] `FxRateProvider`와 Finnhub Forex quote·candle 어댑터
- [x] 공급자 시각이 있는 Finnhub WebSocket USD/KRW 틱만 LIVE로 채택하고 오래된 틱은 폐기
- [x] Finnhub 권한 제한 시 Frankfurter `REFERENCE` 최신·이력 fallback
- [x] `exchange_rates` migration, 품질 검증과 Redis/DB fallback
- [x] 최신 USD/KRW·이력 API, cache와 single-flight
- [x] 대시보드 환율 ticker·기간 차트·source·asOf·freshness
- [x] 포트폴리오 KRW 통합 현재 평가액과 conversionComplete
- [x] 선택적 매수 환율과 정확성 한계가 표시된 환차손익 근사치
- [x] DEMO fixture와 방향·반올림·오류 계약 통합 테스트

## Step 1.5 Sidebar 내비게이션과 뉴스·공시 페이징

목표: `17_NAVIGATION_AND_CONTENT_LIST_SPEC.md` 기준으로 단일 anchor 대시보드를 route 기반 App Shell로 분리하고 뉴스·공시를 서버 pagination으로 조회한다.

- [x] `/content-feed` filter·정렬·page envelope와 허용값 validation
- [x] 뉴스·공시 pagination repository, V18 PostgreSQL 인덱스와 100,000건 fixture 성능 테스트
- [x] React router·Auth/Admin guard·Not Found·기존 URL 호환
- [x] 데스크톱 Sidebar·Topbar와 모바일 Bottom Navigation·More Drawer
- [x] 대시보드 요약화와 종목 상세 4개 route 탭
- [x] 뉴스·공시 통합 목록, URL filter·pagination·권리/AI 상태 badge
- [x] loading·empty·partial·error·offline와 요청 취소·응답 역전 방지
- [x] 접근성·390px·PWA 직접 URL·회귀 E2E와 AC-15 핵심 자동 검증

## Step 2. AI 뉴스 요약 Vertical Slice

목표: 첫 요청은 AI를 호출하고, 두 번째 동일 요청은 Redis 캐시로 반환한다.

- [x] `news_articles`, `ai_analyses`, `ai_usage_logs` 마이그레이션
- [x] 뉴스 목록 API와 데모 데이터
- [x] HTML·광고·URL·중복 문장 전처리
- [x] 프롬프트 버전 기반 캐시 키
- [x] AI 공급자 인터페이스와 Mock 구현
- [x] 운영 Redis 캐시와 Demo 메모리 캐시, TTL
- [x] 토큰·비용·응답시간·캐시 로그
- [x] AI 뉴스 선택·요약·MISS/HIT 화면

## Step 2.1 허용 출처 본문 수집·AI 분석 보강

- [x] `SourcePolicyRegistry`와 기본 `METADATA_ONLY` 정책
- [x] Open DART·SEC EDGAR·기업 IR/RSS 허용 출처 PoC (Open DART ZIP·SEC HTML·RSS/Atom Fixture)
- [x] SSRF·redirect·응답 크기·도메인별 rate limit을 적용한 `ArticleContentFetcher`
- [x] HTML/XML 본문 추출, `contentHash`와 extractor 버전 멱등 저장
- [x] Gemini 전문 분석 범위·긍정/위험 요인·근거 segment 출력
- [x] 본문 변경 시 캐시 무효화와 구간별 토큰·비용 합산
- [x] 공시 카드의 원클릭 원문 확보·Gemini 요약·Redis 캐시·감사 메타데이터 UI
- [x] 프롬프트 인젝션·금지 도메인·429·삭제 요청 테스트

## Step 2.2 AI 기술지표 해설 Vertical Slice

목표: 서버가 계산한 기술지표 스냅샷을 Gemini가 근거 ID와 충돌 신호 중심으로 설명하고, 같은 완성 일봉 요청은 Redis·DB 결과로 재사용한다.

- [x] `TECHNICAL_EXPLANATION` 공급자 계약과 `technical-explanation-v1` 구조화 스키마
- [x] 서버 계산 스냅샷 정규화, `I1..In` 근거 생성과 SHA-256 `inputHash`
- [x] `POST /api/v1/ai/technical-explanations` API와 입력값 서버 재조회
- [x] 목표주가·수익률 예측·직접 매수/매도 명령·존재하지 않는 근거 ID 검증
- [x] `ai_technical_explanations` 마이그레이션과 사용량 로그 연결
- [x] 일봉·계산 버전·입력 hash·프롬프트 버전 기반 Redis/DB 캐시와 single-flight
- [x] 종목 상세 `AI 기술 분석 해설` 카드, 근거값·충돌 신호·한계·면책 표시
- [x] 관리자 화면에서 `NEWS_SUMMARY`와 `TECHNICAL_EXPLANATION` 호출·비용·절감액 분리
- [x] Mock·Gemini 계약, MISS/HIT, 새 일봉·과거 정정 무효화와 금지 출력 테스트

## Step 2.3 근거 기반 일일 변화 브리핑

목표: 직전 거래일과 최신 완성 일봉 사이에서 실제로 달라진 점을 계산하고, 기술·뉴스·공시 관점의 일치와 충돌을 추적 가능한 근거로 설명한다. 상세 계약은 `13_AI_DAILY_CHANGE_BRIEFING_SPEC.md`를 따른다.

- [x] 거래소 현지 날짜 기준 직전·최신 완성 일봉 스냅샷과 결정론적 delta 계산
- [x] 뉴스·공시 비교 창, 중복 제거와 기존 `NEWS_SUMMARY` 분석 결과 재사용
- [x] `T/N/D/Q` 근거 ID와 서버 결정 관점 매트릭스 생성
- [x] `DAILY_CHANGE_BRIEFING` 공급자 계약, `daily-change-briefing-v1` 구조화 스키마와 검증기
- [x] `ai_daily_change_briefings` 마이그레이션, Redis/DB 캐시, single-flight와 사용량 로그 연결
- [x] `POST /api/v1/ai/daily-change-briefings`와 최신 결과 조회 API
- [x] 종목 상세 변화 브리핑·관점 매트릭스·근거 drawer·AI 감사 카드 UI
- [x] 관리자 화면에서 기능별 호출·토큰·비용·캐시 절감액·실패 분리
- [x] AC-12 핵심 MISS/HIT·근거·금지 출력 계약 통합 테스트

## Step 2.4 포트폴리오 구성 AI 평가

목표: 현재 사용자의 서버 계산 포트폴리오만 입력으로 사용해 분산·집중·통화 노출·성과 맥락을 근거 중심으로 설명하고, 매수·매도 추천 없이 정기 점검 항목을 제공한다.

- [x] JWT 사용자 포트폴리오 스냅샷, HHI·상위 비중·통화 노출과 `P/C/FX/H` 근거 계산
- [x] `POST /api/v1/ai/portfolio-evaluations`와 Gemini·Mock 구조화 응답
- [x] 존재하지 않는 근거·추적 불가 숫자·목표가·수익률 예측·직접 거래 권고 검증
- [x] 15분 평가 창 Redis/DB 캐시, V20 영속화와 AI 사용량 로그 연결
- [x] 포트폴리오 평가 카드, 근거 drawer, 데이터 한계·감사 정보 표시
- [x] 원화 환산 평가액 기반 원형 자산배분 그래프와 불완전 환산 차단
- [x] API·검증기·스냅샷·Gemini 계약과 Playwright 회귀 테스트

## Step 3. 관리자 비용 대시보드

목표: AI 요청 전후의 비용과 캐시 절감 효과가 숫자로 보인다.

- [x] 요청 수·모델 호출 수·토큰·비용 집계 API
- [x] 캐시 적중률과 절감 비용 계산
- [x] 기능별 사용량과 고비용 요청 목록
- [x] 관리자 대시보드 차트와 테이블
- [x] 캐시 HIT/MISS 통합 테스트

## Step 4. 사용자 기능

- [x] 데모 로그인과 USER/ADMIN 권한
- [x] 관심종목 CRUD
- [x] 포트폴리오 CRUD와 통화별 최신 가격 평가 계산
- [x] 가격 알림 CRUD와 조건 충족 상태
- [x] WebSocket 시세 기반 포트폴리오 즉시 재평가
- [x] 틱 수신 기반 가격 알림 자동 TRIGGERED 전환
- [x] 메인 대시보드 실제 API 연결

## Step 5. 배포와 마감

- [x] PWA 설치 manifest·설치 버튼과 서버 중단 상태 오프라인 셸 검증
- [ ] EC2 + Nginx + Spring Boot 배포
- [ ] RDS PostgreSQL 연결
- [ ] 운영 Redis 연결
- [ ] CloudWatch 로그 확인
- [x] 모바일·데스크톱 시연 시나리오 점검
- [x] 외부 API 장애 대비 데모 모드
- [x] `live` fail-fast 프로필, liveness/readiness와 로컬 LIVE 스모크 스크립트
- [x] Testcontainers PostgreSQL 17·Redis 8 Flyway·직렬화·TTL 통합 테스트
- [ ] 발표 자료와 시연 영상

## 우선순위 규칙

1. Step 1~3이 완성되기 전에는 고급 UI와 추가 기능을 만들지 않는다.
2. 실제 외부 API가 구현을 막으면 Mock 공급자로 전체 흐름부터 완성한다.
3. 기능은 DB부터 화면까지 세로로 완성한 다음 다음 기능으로 이동한다.
4. `main`은 항상 빌드 가능한 상태로 유지한다.

## 구현 전 결정 게이트

- 외부 주가·뉴스 연동 전 `06_DATA_PROVIDER_SPEC.md`의 공급자, 이용약관, 호출 제한과 신선도 기준을 확정한다.
- 포트폴리오·알림 구현 전 `07_USER_FEATURE_SPEC.md`의 통화, 계산, 중복과 상태 전이 규칙을 확정한다.
- AI 모델·프롬프트·캐시 정책 변경 전 `08_AI_OPERATION_SPEC.md`와 비용 설정을 갱신한다.
- 공개 배포 전 `09_SECURITY_DEPLOYMENT_SPEC.md`의 필수 게이트를 모두 통과한다.
- 기능 완료 표시는 `10_TEST_ACCEPTANCE_SPEC.md`의 필수 인수 조건과 증적을 만족한 뒤 변경한다.
- 기술지표 수식이나 상세 차트를 변경하기 전 `11_TECHNICAL_ANALYSIS_SPEC.md`의 버전·데이터·차트 인수 조건을 확인한다.

## 권장 브랜치

```text
feature/stock-technical-slice
feature/ai-summary-cache
feature/admin-ai-metrics
feature/user-features
feature/aws-deploy
```

## 주요 리스크

| 리스크 | 대응 |
|---|---|
| 주가·뉴스 API 지연 또는 제한 | 공급자 인터페이스와 고정 데모 데이터 유지 |
| AI 키 미준비 또는 비용 발생 | Mock AI로 전체 흐름 완성 후 실제 공급자 교체 |
| 기술지표 값 불일치 | 알려진 데이터셋과 독립 계산 결과로 단위 테스트 |
| AWS 비용 증가 | 단일 EC2, 작은 RDS, 예산 알림 사용 |
| 기능 과다 | 관리자 비용 대시보드 완성 전 확장 기능 금지 |

## 다음 즉시 작업

대회 MVP와 AWS 배포 코드·IaC·CI/CD 산출물은 구현 상태다. 실제 AWS 리소스 생성은 계정·도메인·예산·책임자 승인 뒤 `20_AWS_RUNBOOK.md`로 수행하며, 그 전에는 배포 완료로 표시하지 않는다. 제출 준비의 다음 작업은 전체 회귀·E2E 증적 고정, 5분 시연 영상·발표 Q&A·A1 패널 준비다.
