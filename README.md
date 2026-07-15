# FinWatch

AI 비용 최적화와 기술적 분석을 결합한 클라우드 기반 투자 정보 모니터링 PWA입니다.

FinWatch는 실제 매매 서비스가 아니라 개인 투자자의 정보 탐색과 판단을 보조하는 서비스입니다. 사용자는 관심종목, 환율 환산 포트폴리오, 가격 알림, 기술적 지표, AI 기술지표 해설·뉴스 요약·일일 변화 브리핑을 한곳에서 확인합니다. 관리자는 기능별 AI 호출량, 성공·실패, 토큰, 예상 비용, 캐시 적중률과 절감 효과를 확인합니다.

## 기술 스택

- Frontend: React, TypeScript, Vite, PWA
- Backend: Java, Spring Boot
- Database: PostgreSQL
- Cache: Redis
- Cloud: AWS EC2, RDS, S3, CloudWatch
- Chart: Lightweight Charts 5.2.0 (캔들·거래량·MA·볼린저·RSI·MACD·ATR·그리기 도구)

## 저장소 구조

```text
FinWatch/
├─ frontend/             React TypeScript PWA
├─ backend/              Spring Boot API
├─ docs/                 프로젝트 기준 명세
└─ README.md
```

`frontend/`와 `backend/`는 독립적으로 빌드하며 개발 환경에서는 Vite 프록시로 API를 연결합니다.

## 로컬 실행

필요 환경:

- JDK 21
- Node.js 20.19+ 또는 22.12+
- pnpm
- Docker Desktop

PostgreSQL과 Redis를 먼저 실행합니다.

```powershell
docker compose up -d
```

백엔드:

```powershell
cd backend
.\gradlew.bat bootRun
```

Docker 없이 데모 데이터와 메모리 캐시로 빠르게 실행하려면:

```powershell
cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=demo"
```

일반 프로필의 AI 요약 캐시는 Redis를 사용하고, `demo` 프로필은 동일한 캐시 계약을 메모리에서 실행합니다.

로그인 데모 계정:

```text
USER  user@finwatch.local  / FinWatch123!
ADMIN admin@finwatch.local / FinWatchAdmin123!
```

비밀번호는 서버 시작 시 BCrypt 해시로 DB에 저장됩니다. 로그인 성공 시 1시간 유효한 JWT가 발급되며 일반 API는 USER/ADMIN, 관리자 API는 ADMIN만 접근할 수 있습니다. AWS 배포 시에는 반드시 충분히 긴 별도 `JWT_SECRET`을 환경변수나 비밀 관리 서비스로 설정하고 `DEMO_USERS_ENABLED=false`로 변경합니다.

관심종목은 로그인 사용자별로 분리되어 PostgreSQL에 저장됩니다. 데모 초기값은 USER 2개, ADMIN 4개이며 화면에서 종목 추가·삭제와 상세 차트 선택을 바로 확인할 수 있습니다.

전역 종목 검색은 V14 로컬 카탈로그에서 심볼·한글명·영문명·별칭을 조회하므로 검색마다 외부 공급자를 호출하지 않습니다. 검색 결과는 `(market, symbol)` canonical URL로 열리며 새로고침과 뒤로가기에도 선택 종목이 복원됩니다. LIVE에서는 KIS·Finnhub 종목 마스터 동기화와 선택 종목 quote·일봉·뉴스·공시 온디맨드 수집을 지원하며, 상세 시세가 없는 `METADATA_ONLY` fixture를 다른 종목의 데모 가격으로 대체하지 않습니다.

포트폴리오는 사용자별 보유 수량과 평균 매수가를 저장하고 WebSocket 실시간 시세를 우선해 평가합니다. USD/KRW 환율은 원통화 USD 값을 보존하면서 KRW 보조 평가액과 혼합 포트폴리오 통합 평가액을 계산합니다. 매수 환율이 있으면 평균단가 기반 환차손익 근사치를 표시하며, 환율이 없거나 stale이면 원통화 조회만 유지하고 `conversionComplete=false`로 부분 합계를 숨깁니다. 가격 알림은 ABOVE/BELOW 조건과 ACTIVE/TRIGGERED/DISABLED 상태를 관리하며 수신 틱을 250ms 단위로 합쳐 활성 조건을 자동 평가합니다.

상세 차트는 `1M·3M·6M·1Y·ALL` 일봉과 현재 서버 세션의 실시간 1분 봉 전환, 캔들·거래량 MA20·MA5/20/60·볼린저 밴드·Wilder RSI·MACD·ATR, 교차 이벤트 마커, 확대·이동·십자선 툴팁, 전체화면, 추세선·수평선 그리기를 지원합니다. 지표 표시 설정은 브라우저 세션 동안 유지되며 사용자 그리기는 종목·봉 간격별 브라우저 메모리에 분리됩니다.

AI 기술지표 해설은 클라이언트가 보낸 지표값을 신뢰하지 않고 서버가 최신 일봉과 계산 버전을 다시 조회합니다. 정규화한 스냅샷에 `I1..In` 근거와 SHA-256 `inputHash`를 만들고, Gemini 또는 Mock이 근거 ID를 인용해 추세·모멘텀·변동성·거래량과 충돌 신호를 설명합니다. 같은 스냅샷은 Redis/메모리와 DB에서 재사용되며 HIT 요청의 토큰과 실제 비용은 0으로 기록됩니다.

일일 변화 브리핑은 최신·직전 완성 일봉의 가격·MA·RSI·MACD·ATR·거래량 delta를 서버에서 결정론적으로 계산하고 비교 구간의 검증된 뉴스·공시 분석만 재사용합니다. Gemini는 서버가 만든 관점 매트릭스와 `T/N/D/Q` 근거를 변경할 수 없으며 목표주가·확률·직접 매매 명령은 검증 단계에서 거절됩니다.

뉴스 본문은 출처 정책을 통과한 경우에만 수집·AI 분석할 수 있습니다. 등록되지 않은 도메인은 기본 `METADATA_ONLY`이며 원문 링크만 제공합니다. 허용 출처 수집기는 HTTPS·공개 IP·경로 allowlist, redirect 재검증, 응답 크기 제한과 호스트별 호출 간격을 적용합니다. SEC EDGAR 수집 PoC를 실행할 때는 공식 접근 정책에 맞는 프로젝트명과 연락처를 `ARTICLE_USER_AGENT` 환경변수로 설정해야 합니다.

Open DART 원문 ZIP과 SEC EDGAR HTML은 관리자 갱신 API를 통해 수집할 수 있습니다. 저장된 `contentHash`가 바뀌면 이전 Redis 캐시는 삭제되고 `newsId + contentHash + promptVersion` 조합으로 새 AI 분석 버전을 생성합니다. Open DART 실제 호출은 `OPENDART_API_KEY`가 있을 때만 가능하며, 키가 없어도 고정 ZIP·HTML Fixture 테스트와 DEMO는 동작합니다.

운영 프론트 빌드는 manifest, 설치 프롬프트 버튼과 서비스 워커를 포함합니다. 첫 설치 시 현재 해시 자산을 선캐시하고 네트워크가 완전히 끊긴 탐색 요청에는 독립형 `offline.html` 앱 셸을 반환합니다.

실제 Gemini 무료 등급을 연결할 때는 새로 발급한 키를 시스템 환경변수에만 설정합니다.

```powershell
$env:AI_PROVIDER="gemini"
$env:GEMINI_API_KEY="새로_발급한_키"
$env:GEMINI_MODEL="gemini-3.1-flash-lite"
```

키는 `.env.example`, `application.yml`, 프론트엔드 코드 또는 Git 커밋에 입력하지 않습니다. API 호출은 항상 Spring Boot 백엔드에서 수행합니다.

Finnhub의 공식 문서상 Forex rate/candle은 유료 권한이 필요할 수 있습니다. 무료 키에서 USD/KRW가 거절되면 DEMO 고정 환율을 사용하거나 제출 전 별도 환율 공급자를 정해야 하며, LIVE 모드는 DEMO 값을 실제 환율로 자동 대체하지 않습니다.

프론트엔드:

```powershell
cd frontend
pnpm install
pnpm dev
```

브라우저에서 `http://localhost:5173`으로 접속합니다. 프론트 개발 서버는 `/api` 요청을 `http://localhost:8080`으로 전달합니다.

프런트 E2E 검증:

```powershell
cd frontend
pnpm exec playwright install chromium
pnpm test:e2e
```

현재 개발 PC처럼 사용자 경로에 한글이 있으면 일부 Gradle 버전의 테스트 런처 classpath가 깨질 수 있습니다. 컴파일 캐시는 영문 경로로 분리할 수 있고, 전체 테스트는 저장소 자체도 영문 경로(예: `C:\Dev\FinWatch`)에서 실행하는 것이 가장 안정적입니다.

```powershell
$env:GRADLE_USER_HOME="C:\FinWatchGradle"
cd backend
.\gradlew.bat test --no-daemon --max-workers=1
```

이 설정은 Gradle 실행 경로만 우회하며 프로젝트 소스와 Git에는 영향을 주지 않습니다.

## 기준 문서

개발 시 아래 문서를 순서대로 확인합니다.

1. [요구사항](docs/01_REQUIREMENTS.md)
2. [아키텍처](docs/02_ARCHITECTURE.md)
3. [API 명세](docs/03_API_SPEC.md)
4. [DB 스키마](docs/04_DB_SCHEMA.md)
5. [작업계획](docs/05_TASKS.md)
6. [외부 데이터 공급자 명세](docs/06_DATA_PROVIDER_SPEC.md)
7. [사용자 기능 명세](docs/07_USER_FEATURE_SPEC.md)
8. [AI 운영 명세](docs/08_AI_OPERATION_SPEC.md)
9. [보안·배포 명세](docs/09_SECURITY_DEPLOYMENT_SPEC.md)
10. [테스트·인수 명세](docs/10_TEST_ACCEPTANCE_SPEC.md)
11. [기술적 분석·상세 차트 명세](docs/11_TECHNICAL_ANALYSIS_SPEC.md)
12. [AI 기술지표 해설 명세](docs/12_AI_TECHNICAL_EXPLANATION_SPEC.md)
13. [근거 기반 일일 변화 브리핑 명세](docs/13_AI_DAILY_CHANGE_BRIEFING_SPEC.md)
14. [종목 검색·탐색·온디맨드 데이터 명세](docs/14_STOCK_DISCOVERY_SPEC.md)
15. [환율·기준통화 포트폴리오 명세](docs/15_FX_RATE_SPEC.md)
16. [대회 제출 준비 점검](docs/16_COMPETITION_READINESS.md)

기능과 문서가 충돌하면 현재 저장소의 `01_REQUIREMENTS.md`와 실제 검증된 동작을 우선합니다. 초기 작품소개서 PDF와 목업은 기획 배경일 뿐 구현 계약이 아닙니다. 영역별 세부 규칙은 `06`~`15` 문서를 따릅니다. API 필드 변경은 `03_API_SPEC.md`, DB 변경은 `04_DB_SCHEMA.md`를 같은 커밋에서 함께 수정합니다.

## 핵심 시연 흐름

```text
SK하이닉스 상세 조회
-> 가격 차트와 MA/RSI/MACD 표시
-> 전일 변화 브리핑과 T/N/D/Q 근거 확인
-> AI 분석 최초 생성(cacheHit=false)
-> 같은 입력 재요청(cacheHit=true)
-> 관리자 대시보드에서 비용 절감 확인
```

## 주의사항

- 본 서비스의 기술적 신호와 AI 요약은 투자 권유가 아닌 참고 정보입니다.
- API 키, 비밀번호, AWS 자격 증명은 Git에 커밋하지 않습니다.
- 실제 금융 API가 불안정해도 시연할 수 있도록 고정 데모 데이터를 유지합니다.

## 확정 외부 공급자

| 영역 | 공급자 |
|---|---|
| 국내 현재가·일봉·실시간 체결 | 한국투자증권 KIS Open API |
| 미국 현재가·실시간 체결 | Finnhub Quote / WebSocket |
| 국내 뉴스 검색 | NAVER API HUB |
| 미국 뉴스 검색 | Finnhub |
| USD/KRW 환율 | Finnhub Forex 계약 / DEMO 고정 fixture |
| 국내 공시·전문 | Open DART |
| 미국 공시·전문 | SEC EDGAR |
| 공식 기업 콘텐츠 | 승인된 IR·뉴스룸·RSS |
| AI 분석 | Gemini |

KIS 국내 일봉, NAVER API HUB 국내 뉴스와 Finnhub 미국 뉴스 클라이언트는 DB 동기화 계층에 연결되어 있습니다. LIVE 종목 검색 마스터는 KIS의 코스피·코스닥 master ZIP과 Finnhub `stock/symbol`을 로컬 DB에 안전하게 upsert하며, 빈 응답이나 종목 수 급감 시 기존 마스터를 유지합니다. 선택 종목의 quote·일봉·뉴스·공시는 비동기 data-load job으로 준비하고 동일 동시 요청은 single-flight로 합칩니다. 공시는 국내 Open DART와 미국 SEC EDGAR에서 최근 1년 목록을 가져오며 화면에서 공식 원문을 바로 열 수 있습니다. `DATA_MODE=LIVE`와 `REALTIME_ENABLED=true`에서는 선택 종목을 최우선으로 관심종목·보유종목·활성 알림을 합쳐 KIS `H0STCNT0`와 Finnhub trade WebSocket을 공급자별 상한 안에서 동적으로 구독하고 브라우저의 단일 `/ws/quotes` 스트림으로 중계합니다. 틱은 PostgreSQL에 매번 저장하지 않고 최신 시세와 종목별 최대 600개의 1분 OHLCV를 메모리에 유지합니다. 차트 조회 API는 최근 390봉을 제공하며 서버를 재시작하면 장중 버퍼는 초기화됩니다. 장이 닫혔거나 스트림이 아직 틱을 보내지 않은 종목은 공급자 REST 스냅샷과 기준 시각을 표시하되 REST 스냅샷을 체결 봉으로 만들지는 않습니다.

```text
POST /api/v1/admin/data/sync
POST /api/v1/admin/data/stocks/{symbol}/sync
POST /api/v1/admin/data/catalogs/sync
POST /api/v1/admin/data/catalogs/{provider}/sync
POST /api/v1/stocks/{market}/{symbol}/data-loads
GET  /api/v1/stocks/{market}/{symbol}/data-loads/{jobId}
GET  /api/v1/stocks/{market}/{symbol}/disclosures
GET  /api/v1/stocks/realtime
WS   /ws/quotes
```

관리자 동기화 API는 ADMIN JWT가 필요합니다. LIVE 전환 전에는 이용약관, 공개 표시 권한과 실제 계정 호출 한도를 확인하고 외부 키는 백엔드 환경변수에만 저장합니다.
