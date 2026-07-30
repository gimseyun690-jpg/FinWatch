# FinWatch

AI 비용 최적화와 기술적 분석을 결합한 클라우드 기반 투자 정보 모니터링 PWA입니다.

FinWatch는 실제 매매 서비스가 아니라 개인 투자자의 정보 탐색과 판단을 보조하는 서비스입니다. 사용자는 관심종목, 환율 환산 포트폴리오, 가격 알림, 기술적 지표와 AI 뉴스 분석·기술지표 해설·일일 변화 브리핑·포트폴리오 구성 평가를 한곳에서 확인합니다. 관리자는 네 AI 기능의 호출량, 성공·실패, 토큰, 예상 비용, 캐시 적중률과 절감 효과를 확인합니다.

## 기술 스택

- Frontend: React, TypeScript, Vite, React Router 8, PWA
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

발급한 공급자 키는 대화형 스크립트로 하나씩 Windows 사용자 환경변수에 저장할 수 있습니다. 입력한 Secret은 화면과 명령 기록에 표시되지 않으며 상태 표에는 값 대신 설정 여부만 나옵니다. `-Group`에는 `Live`, `Kakao`, `UserAgent`, `All`을 사용할 수 있고, `-MissingOnly`는 미설정 항목만, `-Names`는 지정한 항목만 다시 입력한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\configure-local-api-keys.ps1 -Group All
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\configure-local-api-keys.ps1 -Group All -StatusOnly
& .\scripts\configure-local-api-keys.ps1 -Group All -MissingOnly
& .\scripts\configure-local-api-keys.ps1 -Names NAVER_API_HUB_CLIENT_ID,NAVER_API_HUB_CLIENT_SECRET
```

Windows 사용자 환경변수는 애플리케이션 입력 형식이며 암호화된 비밀 저장소는 아닙니다. 공동 PC에서는 사용하지 않고, 토큰을 채팅·Git·명령행 인자로 전달하지 않습니다. 설정 후 로컬 LIVE 프로필은 다음 스크립트로 실행합니다. 스크립트는 키 값을 출력하지 않으며 필수 키가 빠지면 시작 전에 실패합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-live.ps1 -Build
```

Windows에서 D: 드라이브를 사용할 수 있으면 LIVE 실행 스크립트도 기본 작업 경로를 `D:\FinWatchTest`로 잡아 Gradle cache, 임시 파일과 portable Temurin 21을 사용한다. 다른 위치가 필요하면 `-WorkRoot`로 지정한다.

실행 후 PostgreSQL·Redis readiness, 로그인, KIS 현재가·일봉, NAVER·Finnhub 뉴스, Open DART 공시, USD/KRW와 Gemini 뉴스 요약을 한 번에 점검할 수 있습니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-live.ps1
```

로그인 데모 계정:

```text
USER  user@finwatch.local  / FinWatch123!
ADMIN admin@finwatch.local / FinWatchAdmin123!
```

비밀번호는 서버 시작 시 BCrypt 해시로 DB에 저장됩니다. 브라우저 로그인은 Redis에 저장한 불투명 세션과 `FW_SESSION` HttpOnly 쿠키를 사용하며, 변경 요청은 `XSRF-TOKEN`/`X-XSRF-TOKEN`으로 CSRF를 검증합니다. 기본 idle TTL은 1시간, absolute TTL은 8시간이고 일반 API는 USER/ADMIN, 관리자 API는 ADMIN만 접근할 수 있습니다. Bearer JWT는 기존 자동화 테스트 호환을 위해서만 남겨 두었으며 공개 브라우저에서는 저장하거나 사용하지 않습니다. AWS 배포 시에는 `SESSION_COOKIE_SECURE=true`, 충분히 긴 별도 `JWT_SECRET`, `DEMO_USERS_ENABLED=false`를 환경변수나 비밀 관리 서비스로 설정합니다.

관심종목은 로그인 사용자별로 분리되어 PostgreSQL에 저장되며 개수 제한이 없습니다. 데모 초기값은 USER 2개, ADMIN 4개일 뿐 상한이 아닙니다. 추가 화면에서 심볼·한글명·영문명으로 전체 로컬 종목 카탈로그를 검색해 연속 등록할 수 있고, `(market, symbol)` 기준으로 안전하게 삭제합니다.

전역 검색과 관심종목 추가 검색은 V14 로컬 카탈로그에서 심볼·한글명·영문명·별칭을 조회하므로 검색마다 외부 공급자를 호출하지 않습니다. 검색 결과는 `(market, symbol)` canonical URL로 열리며 새로고침과 뒤로가기에도 선택 종목이 복원됩니다. LIVE에서는 시작 30초 뒤와 이후 24시간마다 KIS·Finnhub 종목 마스터를 안전하게 자동 동기화합니다. 선택하거나 관심종목에 등록한 종목은 quote·일봉·뉴스·공시 온디맨드 수집을 즉시 시작하며, 상세 시세가 없는 `METADATA_ONLY` 종목을 다른 종목의 데모 가격으로 대체하지 않습니다.

포트폴리오는 사용자별 보유 수량과 평균 매수가를 저장하고 WebSocket 실시간 시세를 우선해 평가합니다. USD/KRW 환율은 원통화 USD 값을 보존하면서 KRW 보조 평가액과 혼합 포트폴리오 통합 평가액을 계산합니다. 환산이 완전하면 원화 평가액 기준 종목별 도넛과 상위 7개·기타 범례를 표시하고, 환율이 없거나 stale이면 `conversionComplete=false`로 부분 합계와 비중 그래프를 숨깁니다. 매수 환율이 있으면 평균단가 기반 환차손익 근사치를 표시합니다. 가격 알림은 ABOVE/BELOW 조건과 ACTIVE/TRIGGERED/DISABLED 상태를 관리하며 수신 틱을 250ms 단위로 합쳐 활성 조건을 자동 평가합니다.

상세 차트는 실제 일봉을 기준으로 집계한 `일봉·주봉·월봉`, `1M·3M·6M·1Y·ALL` 기간과 실시간 1분봉 전환을 지원합니다. 캔들·거래량 MA20·MA5/20/60·볼린저 밴드·Wilder RSI·MACD·ATR, 교차 이벤트 마커, 확대·이동·십자선 툴팁, 전체화면, 추세선·수평선 그리기를 제공합니다. 일반 화면에서는 `TICK`, 현재 서버 세션과 원시 공급자 코드 같은 구현 상세를 제거하고 가격·상태·기준시각만 간결하게 보여주며, 공급자·수집 진단은 관리자 상세 화면에만 둡니다. 전체화면은 도구·범례가 차트 영역을 밀어내지 않도록 유연한 viewport 행을 사용하고, 그리기 이동은 animation frame 단위 DOM preview 뒤 최종 좌표만 상태에 반영합니다. 지표 표시 설정은 브라우저 세션 동안 유지되며 사용자 그리기는 종목·봉 간격별 브라우저 메모리에 분리됩니다.

AI 기술지표 해설은 클라이언트가 보낸 지표값을 신뢰하지 않고 서버가 최신 일봉과 계산 버전을 다시 조회합니다. 정규화한 스냅샷에 `I1..In` 근거와 SHA-256 `inputHash`를 만들고, Gemini 또는 Mock이 근거 ID를 인용해 추세·모멘텀·변동성·거래량과 충돌 신호를 설명합니다. 같은 스냅샷은 Redis/메모리와 DB에서 재사용되며 HIT 요청의 토큰과 실제 비용은 0으로 기록됩니다.

일일 변화 브리핑은 최신·직전 완성 일봉의 가격·MA·RSI·MACD·ATR·거래량 delta를 서버에서 결정론적으로 계산하고 비교 구간의 검증된 뉴스·공시 분석만 재사용합니다. 일반 뉴스는 종목 탐색 화면에서 선택 즉시 AI 요약할 수 있고, 같은 검증 결과가 브리핑의 뉴스 관점과 `N` 근거로 연결됩니다. Gemini는 서버가 만든 관점 매트릭스와 `T/N/D/Q` 근거를 변경할 수 없으며 목표주가·확률·직접 매매 명령은 검증 단계에서 거절됩니다.

AI 포트폴리오 구성 평가는 요청 본문에서 보유 비중을 받지 않고 인증 사용자의 서버 포트폴리오를 다시 조회합니다. 서버가 15분 평가 창마다 원화 평가액, 최대·상위 3개 비중, HHI, 통화 노출, 수익 맥락과 `P/C/FX/H` 근거를 계산하고 Gemini 또는 Mock은 그 근거만 설명합니다. 같은 사용자·보유 구성·평가 창·프롬프트 버전은 Redis/DB에서 재사용하며, 목표가·미래 수익률·직접 매수·매도·교체 권고는 검증 단계에서 거절됩니다.

뉴스 본문은 출처 정책을 통과한 경우에만 수집·AI 분석할 수 있습니다. 등록되지 않은 도메인은 기본 `METADATA_ONLY`이며 원문 링크만 제공합니다. 허용 출처 수집기는 HTTPS·공개 IP·경로 allowlist, redirect 재검증, 응답 크기 제한과 호스트별 호출 간격을 적용합니다. SEC EDGAR 수집 PoC를 실행할 때는 공식 접근 정책에 맞는 프로젝트명과 연락처를 `ARTICLE_USER_AGENT` 환경변수로 설정해야 합니다.

Open DART 원문 ZIP과 SEC EDGAR HTML은 관리자 갱신 API를 통해 수집할 수 있습니다. 저장된 `contentHash`가 바뀌면 이전 Redis 캐시는 삭제되고 `newsId + contentHash + promptVersion` 조합으로 새 AI 분석 버전을 생성합니다. Open DART 실제 호출은 `OPENDART_API_KEY`가 있을 때만 가능하며, 키가 없어도 고정 ZIP·HTML Fixture 테스트와 DEMO는 동작합니다.

운영 프론트 빌드는 manifest, 설치 프롬프트 버튼과 서비스 워커를 포함합니다. 첫 설치 시 현재 해시 자산을 선캐시하고 네트워크가 끊긴 직접 route 탐색에는 캐시된 React 앱 셸을 반환해 현재 화면 주소를 유지합니다. 정적 해시 자산은 버전별 cache-first, 화면 navigation은 network-first이며 `/api/**` 응답은 Cache Storage에 저장하지 않습니다. 앱 셸 자체도 없을 때만 독립형 `offline.html`을 최종 fallback으로 사용합니다.

실제 Gemini 무료 등급을 연결할 때는 새로 발급한 키를 시스템 환경변수에만 설정합니다.

```powershell
$env:AI_PROVIDER="gemini"
$env:GEMINI_API_KEY="새로_발급한_키"
$env:GEMINI_MODEL="gemini-3.1-flash-lite"
```

키는 `.env.example`, `application.yml`, 프론트엔드 코드 또는 Git 커밋에 입력하지 않습니다. API 호출은 항상 Spring Boot 백엔드에서 수행합니다.

USD/KRW는 공급자 시각이 포함된 Finnhub `OANDA:USD_KRW` WebSocket 체결을 `LIVE`로 채택하고 `/ws/quotes`의 `fx` 이벤트로 브라우저에 전달합니다. REST 조회는 같은 최신값을 우선 사용하며, WebSocket 값이 없거나 오래됐거나 계정 권한이 제한되면 Finnhub REST와 Frankfurter 일일 기준환율을 `REFERENCE` fallback으로 사용합니다. 프런트는 하나의 실시간 환율 상태를 상단 ticker와 포트폴리오 환산에 함께 적용하고, fallback을 실시간 틱 환율로 표현하지 않습니다. LIVE 모드는 DEMO 고정값을 실제 환율로 자동 대체하지 않습니다.

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
pnpm build
pnpm test:pwa
```

현재 개발 PC처럼 사용자 경로에 한글이 있으면 일부 Gradle 버전의 테스트 런처 classpath가 깨질 수 있습니다. Windows 테스트 스크립트는 저장소를 임시 영문 드라이브 문자로 연결하고, D:가 있으면 Gradle 캐시와 TEMP를 기본적으로 `D:\FinWatchTest`에 둡니다. 다른 작업 드라이브는 `-WorkRoot`로 지정할 수 있습니다.

```powershell
.\scripts\test-backend-windows.ps1 -WorkRoot D:\FinWatchTest
.\scripts\test-backend-windows.ps1 -IncludeDocker -WorkRoot D:\FinWatchTest
```

이 설정은 Gradle 실행 경로만 우회하며 프로젝트 소스와 Git에는 영향을 주지 않습니다. Docker Desktop의 Linux 이미지·컨테이너 가상 디스크는 별도 저장소이므로 C: 공간이 부족하면 Docker Dashboard의 `Settings > Resources > Advanced > Disk image location`에서 D:의 빈 폴더로 이동한 뒤 Docker 테스트를 실행합니다. VHDX 파일을 Explorer로 직접 옮기지 않습니다.

Docker가 실행 중이면 전체 백엔드 테스트에 Testcontainers PostgreSQL 17·Redis 8 검증이 포함됩니다. 2026-07-19 기준 백엔드 전체 173개, 프런트 Playwright 23개, production Service Worker·390px PWA 2개가 통과합니다. PostgreSQL 100,000건 콘텐츠 피드 성능 검사도 D:의 Docker 저장소에서 함께 통과했습니다.

## AWS 공개 배포 준비

AWS용 multi-stage 이미지, 동일 Origin Nginx, production-like Compose, CloudFormation, GitHub OIDC 배포 workflow와 운영 스크립트가 준비되어 있습니다. 로컬 컨테이너 경계를 먼저 검증합니다.

```powershell
docker compose -f deploy/compose.production-like.yml build
docker compose -f deploy/compose.production-like.yml up -d --wait --wait-timeout 180
Invoke-WebRequest http://localhost:4180/api/v1/health
docker compose -f deploy/compose.production-like.yml down
```

실제 AWS 배포는 과금과 공개 상태 변경을 수반합니다. 계정·리전·도메인·예산·책임자·공개 종료일을 확정한 뒤 [AWS 공개 배포 런북](docs/20_AWS_RUNBOOK.md)의 승인 게이트부터 진행합니다. 현재 검증 범위와 환경 차단 항목은 [20번 구현 보고서](docs/20_AWS_IMPLEMENTATION_REPORT.md)에 기록했습니다. 현재 저장소 상태는 “배포 코드 준비”이며 실제 AWS 공개 배포 완료 상태가 아닙니다.

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
17. [내비게이션·콘텐츠 목록·페이징 명세](docs/17_NAVIGATION_AND_CONTENT_LIST_SPEC.md)
18. [UI 폴리싱·제품 디테일 명세](docs/18_UI_POLISH_AND_PRODUCT_DETAIL_SPEC.md)
19. [카카오 로그인 명세](docs/19_KAKAO_LOGIN_SPEC.md)
20. [AWS 공개 배포 명세](docs/20_AWS_DEPLOYMENT_SPEC.md)

기능과 문서가 충돌하면 현재 저장소의 `01_REQUIREMENTS.md`와 실제 검증된 동작을 우선합니다. 초기 작품소개서 PDF와 목업은 기획 배경일 뿐 구현 계약이 아닙니다. 영역별 세부 규칙은 `06`~`15`, `17`~`20` 문서를 따릅니다. API 필드 변경은 `03_API_SPEC.md`, DB 변경은 `04_DB_SCHEMA.md`를 같은 커밋에서 함께 수정합니다.

## 핵심 시연 흐름

```text
SK하이닉스 상세 조회
-> 가격 차트와 MA/RSI/MACD 표시
-> 전일 변화 브리핑과 T/N/D/Q 근거 확인
-> AI 분석 최초 생성(cacheHit=false)
-> 같은 입력 재요청(cacheHit=true)
-> 포트폴리오 자산배분 도넛과 AI 구성 평가의 P/C/FX/H 근거 확인
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
| 미국 현재가·일봉·실시간 체결 | Finnhub Quote / Stock Candles / WebSocket, KIS 해외 일봉 fallback |
| 국내 뉴스 검색 | NAVER API HUB |
| 미국 뉴스 검색 | Finnhub |
| USD/KRW 환율 | Finnhub Forex WebSocket LIVE, Finnhub REST·Frankfurter REFERENCE fallback / DEMO 고정 fixture |
| 국내 공시·전문 | Open DART |
| 미국 공시·전문 | SEC EDGAR |
| 공식 기업 콘텐츠 | 승인된 IR·뉴스룸·RSS |
| AI 분석 | Gemini |

KIS 국내 일봉, KIS 해외 일봉, NAVER API HUB 국내 뉴스와 Finnhub 미국 일봉·뉴스 클라이언트는 DB 동기화 계층에 연결되어 있습니다. 국내 일봉은 공급자 응답 상한과 초당 호출 제한에 맞춰 기간을 나누고 중복 거래일을 제거해 최대 약 5년을 채웁니다. 미국 일봉은 Finnhub 계정에 Stock Candles 권한이 있으면 우선 사용하고, 무료 키에서 `403`이 반환되면 KIS 해외 일봉으로 자동 fallback해 실제 OHLCV를 저장합니다. 저장된 실제 일봉은 서버에서 거래소 현지 주의 월요일과 달을 기준으로 주봉·월봉으로 집계하므로 세 주기의 종가와 거래량이 일관됩니다. LIVE 종목 검색 마스터는 KIS의 코스피·코스닥 master ZIP과 Finnhub `stock/symbol`을 시작 후 및 매일 로컬 DB에 안전하게 upsert하며, 빈 응답이나 종목 수 급감 시 기존 마스터를 유지합니다. 선택 종목의 quote·일봉·뉴스·공시는 비동기 data-load job으로 준비하고 동일 동시 요청은 single-flight로 합칩니다. 공시는 국내 Open DART와 미국 SEC EDGAR에서 최근 1년 목록을 가져오며 화면에서 공식 원문을 바로 열 수 있습니다. `DATA_MODE=LIVE`와 `REALTIME_ENABLED=true`에서는 선택 종목을 최우선으로 관심종목·보유종목·활성 알림을 합쳐 KIS `H0STCNT0`와 Finnhub trade WebSocket을 공급자별 상한 안에서 동적으로 구독하고 브라우저의 단일 `/ws/quotes` 스트림으로 중계합니다. 이 스트림은 주식 `quote`·`candle`뿐 아니라 공급자 시각이 검증된 USD/KRW `fx` 이벤트도 전달합니다. 틱은 PostgreSQL에 매번 저장하지 않고 최신 시세와 종목별 최대 600개의 1분 OHLCV를 메모리에 유지합니다. 차트 조회 API는 최근 390봉을 제공하며 서버를 재시작하면 장중 버퍼는 초기화됩니다. 장이 닫혔거나 스트림이 아직 틱을 보내지 않은 종목은 공급자 REST 스냅샷과 기준 시각을 표시하되 REST 스냅샷을 체결 봉으로 만들지는 않습니다.

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

관리자 동기화 API는 ADMIN 세션이 필요합니다. LIVE 전환 전에는 이용약관, 공개 표시 권한과 실제 계정 호출 한도를 확인하고 외부 키는 백엔드 환경변수에만 저장합니다.
