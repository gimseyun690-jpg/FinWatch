# FinWatch

AI 비용 최적화와 기술적 분석을 결합한 클라우드 기반 투자 정보 모니터링 PWA입니다.

FinWatch는 실제 매매 서비스가 아니라 개인 투자자의 정보 탐색과 판단을 보조하는 서비스입니다. 사용자는 관심종목, 포트폴리오, 가격 알림, 기술적 지표와 AI 뉴스 요약을 한곳에서 확인합니다. 관리자는 AI 호출량, 토큰, 예상 비용, 캐시 적중률과 절감 효과를 확인합니다.

## 기술 스택

- Frontend: React, TypeScript, Vite, PWA
- Backend: Java, Spring Boot
- Database: PostgreSQL
- Cache: Redis
- Cloud: AWS EC2, RDS, S3, CloudWatch
- Chart: Lightweight Charts 5.2.0 (캔들·거래량·이동평균·그리기 도구)

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

포트폴리오는 사용자별 보유 수량과 평균 매수가를 저장하고 최신 시장 가격으로 평가합니다. 환율 공급자가 없는 MVP에서는 KRW와 USD를 합산하지 않고 통화별 평가액·손익·수익률을 보여줍니다. 가격 알림은 ABOVE/BELOW 조건과 ACTIVE/TRIGGERED/DISABLED 상태를 관리하며 현재 저장 가격 기준 조건 충족 여부를 표시합니다.

상세 차트는 `1M·3M·6M·1Y·ALL` 일봉 조회, 캔들·거래량·MA5/20/60, 확대·이동·십자선 툴팁, 전체화면, 추세선·수평선 그리기를 지원합니다. 사용자 그리기는 현재 브라우저 메모리에만 유지되며 새로고침하면 초기화됩니다.

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

프론트엔드:

```powershell
cd frontend
pnpm install
pnpm dev
```

브라우저에서 `http://localhost:5173`으로 접속합니다. 프론트 개발 서버는 `/api` 요청을 `http://localhost:8080`으로 전달합니다.

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

기능과 문서가 충돌하면 대회 작품소개서와 `01_REQUIREMENTS.md`를 우선합니다. 영역별 세부 규칙은 `06`~`11` 문서를 따릅니다. API 필드 변경은 `03_API_SPEC.md`, DB 변경은 `04_DB_SCHEMA.md`를 같은 커밋에서 함께 수정합니다. 미확정 항목은 구현자가 임의로 확정하지 않고 해당 명세의 결정 게이트를 먼저 갱신합니다.

## 첫 번째 완성 목표

```text
SK하이닉스 상세 조회
-> 가격 차트와 MA/RSI/MACD 표시
-> 관련 뉴스 선택
-> AI 요약 최초 생성(cacheHit=false)
-> 같은 뉴스 재요청(cacheHit=true)
-> 관리자 대시보드에서 비용 절감 확인
```

## 주의사항

- 본 서비스의 기술적 신호와 AI 요약은 투자 권유가 아닌 참고 정보입니다.
- API 키, 비밀번호, AWS 자격 증명은 Git에 커밋하지 않습니다.
- 실제 금융 API가 불안정해도 시연할 수 있도록 고정 데모 데이터를 유지합니다.

## 확정 외부 공급자

| 영역 | 공급자 |
|---|---|
| 국장·미장 현재가·일봉·실시간 시세 | 한국투자증권 KIS Open API |
| 국내 뉴스 검색 | NAVER API HUB |
| 미국 뉴스 검색 | Finnhub |
| 국내 공시·전문 | Open DART |
| 미국 공시·전문 | SEC EDGAR |
| 공식 기업 콘텐츠 | 승인된 IR·뉴스룸·RSS |
| AI 분석 | Gemini |

공급자 선택은 확정됐지만 현재 코드는 DEMO 데이터와 Gemini/Mock만 연결되어 있습니다. LIVE 전환 전에는 각 키 발급, 이용약관, 공개 시세 표시 권한, 호출 한도와 대표 종목 PoC를 통과해야 합니다. 외부 키는 백엔드 환경변수에만 저장합니다.
