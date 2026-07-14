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
- [x] 작품소개서 분석과 핵심 명세 작성
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
- [x] 프롬프트 인젝션·금지 도메인·429·삭제 요청 테스트

## Step 2.2 AI 기술지표 해설 Vertical Slice

목표: 서버가 계산한 기술지표 스냅샷을 Gemini가 근거 ID와 충돌 신호 중심으로 설명하고, 같은 완성 일봉 요청은 Redis·DB 결과로 재사용한다.

- [ ] `TECHNICAL_EXPLANATION` 공급자 계약과 `technical-explanation-v1` 구조화 스키마
- [ ] 서버 계산 스냅샷 정규화, `I1..In` 근거 생성과 SHA-256 `inputHash`
- [ ] `POST /api/v1/ai/technical-explanations` API와 입력값 서버 재조회
- [ ] 목표주가·수익률 예측·직접 매수/매도 명령·존재하지 않는 근거 ID 검증
- [ ] `ai_technical_explanations` 마이그레이션과 사용량 로그 연결
- [ ] 일봉·계산 버전·입력 hash·프롬프트 버전 기반 Redis/DB 캐시와 single-flight
- [ ] 종목 상세 `AI 기술 분석 해설` 카드, 근거값·충돌 신호·한계·면책 표시
- [ ] 관리자 화면에서 `NEWS_SUMMARY`와 `TECHNICAL_EXPLANATION` 호출·비용·절감액 분리
- [ ] Mock·Gemini 계약, MISS/HIT, 새 일봉·과거 정정 무효화와 금지 출력 테스트

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

애플리케이션 구현의 남은 작업은 실제 AWS 계정에서 EC2·RDS·Redis·CloudWatch를 배포하고 발표 자료·시연 영상을 만드는 것이다. 로컬 코드는 KIS·Finnhub 실시간 체결 WebSocket, KIS·NAVER API HUB·Finnhub 동기화, Gemini/Mock AI, 고급 기술지표와 Playwright E2E까지 완료된 상태를 유지한다.
