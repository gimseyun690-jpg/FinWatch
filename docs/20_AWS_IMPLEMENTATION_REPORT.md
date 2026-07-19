# 20번 AWS 배포 구현 보고서

기준일: 2026-07-19

판정: **AWS 공개 배포 및 자동 배포 경로 검증 완료 / 운영 인수 점검 중**

## 구현 산출물

- `backend/Dockerfile`: Java 21 multi-stage, non-root `10001:10001`, liveness health check, SIGTERM
- `frontend/Dockerfile`: pnpm 고정 production build, unprivileged Nginx, local-only health endpoint
- `deploy/nginx/`: HTTPS redirect, TLS, SPA/PWA, API·WebSocket proxy, public Actuator 차단, 보안 header
- `deploy/compose.production-like.yml`: 로컬 운영 유사 검증
- `deploy/compose.portfolio.yml`: 외부 80/443만 공개, backend·Redis internal, read-only·cap drop·CloudWatch logs
- `application-portfolio.yml`과 `PortfolioEnvironmentValidator`: 안전하지 않은 공개 설정 fail-fast
- `RequestIdFilter`: `X-Request-ID` 전파와 query string을 제외한 상태·지연 로그
- `infra/aws/portfolio.yml`: 저비용 VPC·EC2·private encrypted RDS·ECR·S3·SSM·Secrets·CloudWatch·Budget
- `infra/aws/github-oidc-role.yml`: GitHub `portfolio` Environment 전용 최소권한 OIDC 역할
- `.github/workflows/ci.yml`: backend·frontend·PWA·Secret·container 품질 게이트
- `.github/workflows/deploy-portfolio.yml`: SHA image, ECR scan, snapshot, SSM deploy, public smoke
- `scripts/aws/`: runtime Secret render, 전용 DB user, TLS, 배포, 롤백, 인증서 갱신, smoke
- `docs/20_AWS_RUNBOOK.md`: 승인·초기화·배포·복구·종료·비용 정리 절차

## 검증 결과

| 검사 | 결과 |
|---|---|
| CloudFormation `cfn-lint` 2개 template | PASS |
| GitHub Actions·Compose·CloudFormation YAML parse | PASS |
| AWS·보안 shell script 9개 `bash -n` | PASS |
| production-like·portfolio `docker compose config` | PASS |
| portfolio fail-fast·request ID backend test | 6 PASS |
| backend `bootJar` | PASS |
| 생성 JAR demo 기동과 `/api/v1/health`·`X-Request-ID` | PASS |
| frontend lint | PASS |
| frontend TypeScript·Vite production build | PASS |
| backend 전체 + PostgreSQL 17·Redis 8 Testcontainers | 173 PASS, 최종 캐시 실행 1분 38초 |
| PostgreSQL 100,000건 콘텐츠 피드 성능 검사 | PASS |
| Playwright 기존 흐름·Kakao 오류 계약 | 23 PASS, 1 worker 순차 실행 1분 24초 |
| production PWA offline·390px Kakao 흐름 | 2 PASS |
| production-like backend·frontend image build/export | PASS, D: Docker 저장소 |
| production-like 3개 컨테이너 health·외부 포트 경계 | PASS |
| `/`, 직접 route, health, manifest, Service Worker smoke | 모두 HTTP 200 |
| public `/actuator/health` 차단 | HTTP 404 |
| backend/frontend non-root·health metadata | `10001:10001` / `101:101`, PASS |
| repository·frontend bundle 고신뢰 Secret scan | PASS |
| 로컬 LIVE 공급자·DB·Redis 통합 smoke | 13 PASS, warning 0 |

## 로컬 디스크 차단 해소와 재검증

2026-07-18에는 C: 공간 부족과 Docker WSL 가상 디스크 I/O 오류 때문에 최종 image export와 production-like 기동을 중단했다. Docker container·image·volume 삭제나 factory reset은 수행하지 않았다.

2026-07-19에 Docker Dashboard의 공식 `Disk image location` 설정으로 가상 디스크를 `D:\DockerDesktopData`로 이동했다. Gradle·TEMP·Temurin 21·Playwright 브라우저도 `D:\FinWatchTest`에 분리했다. 이후 image build/export, PostgreSQL 17·Redis 8 Testcontainers, 100,000건 성능 검사, production-like 3개 컨테이너 기동과 HTTP smoke가 모두 통과해 로컬 디스크 차단은 해소됐다.

재검증 명령은 다음과 같다.

```powershell
docker compose -f deploy/compose.production-like.yml build
docker compose -f deploy/compose.production-like.yml up -d --wait --wait-timeout 180
Invoke-WebRequest http://localhost:4180/api/v1/health
docker compose -f deploy/compose.production-like.yml ps
docker compose -f deploy/compose.production-like.yml down
```

전체 backend 통합 테스트는 localhost Redis를 요구한다. `docker compose up -d --wait redis` 후 `scripts/test-backend-windows.ps1 -IncludeDocker -WorkRoot D:\FinWatchTest`로 실행하며, 종료 시 `docker compose down`으로 컨테이너를 정리하고 데이터 volume과 image는 보존한다.

2026-07-19에 로컬 Windows 사용자 환경변수로 Gemini·KIS·NAVER API HUB·Finnhub·Open DART·Kakao 설정과 수집 User-Agent를 등록했다. `DATA_MODE=LIVE`, `AI_PROVIDER=gemini`로 로컬 서버를 재기동한 뒤 PostgreSQL·Redis readiness, 세션 로그인, KIS 현재가·일봉, NAVER 뉴스, Finnhub 회사 뉴스, Open DART 공시, USD/KRW와 Gemini 뉴스 요약까지 `scripts/smoke-live.ps1` 13개 검사가 모두 통과했다. Gemini 응답 모델은 `gemini-3.1-flash-lite`였고, Secret 값은 로그·증적에 기록하지 않았다.

이 로컬 LIVE 공급자 계약 검증 뒤 동일 공급자 설정을 AWS Secrets Manager에 주입했다. Kakao Developers에서 Kakao Login·OpenID Connect·카카오 로그인 Client Secret을 활성화하고 운영 callback URI도 등록했다. 실제 카카오계정 동의, authorization code·PKCE token 교환, ID Token 검증, 신규 USER provisioning, HttpOnly session과 `/dashboard` 이동은 로컬 LIVE 브라우저에서 통과했다. 공개 환경에서는 Kakao 활성 상태, 인증 서버 redirect와 Secure·HttpOnly·SameSite 상관관계 쿠키까지 확인했으며 실제 계정 callback 완료는 운영 인수 항목으로 남긴다.

## 실제 AWS 배포 결과

2026-07-19에 서울 리전의 `finwatch-portfolio` CloudFormation 스택을 생성하고 private PostgreSQL 17 RDS, EC2, ECR, S3, Secrets Manager, CloudWatch, Budget 기반을 구성했다. 전용 RDS app user와 공급자 Secret을 초기화했고 DuckDNS `finwatch-hyphoenix.duckdns.org`, Let's Encrypt TLS와 Kakao 운영 callback을 연결했다. GitHub `portfolio` Environment는 저장소·환경으로 제한된 OIDC 역할을 사용하며 장기 AWS key나 공급자 Secret을 GitHub에 저장하지 않는다.

PR #8 병합 commit `a3cac91abe4ec0d9c461ebe906822081713126d5`를 GitHub Actions run `29680176687`로 배포했다. 백엔드·프런트엔드 이미지는 ECR Critical 0 검사를 통과했고 RDS 사전 snapshot 생성, SSM 배포, 컨테이너 readiness와 외부 smoke가 모두 성공했다. Redis·backend·frontend는 healthy이며 HTTP→HTTPS 308, 홈·직접 route, API `UP`, public Actuator 404, Kakao 활성화·authorize 302와 WebSocket 인증 경계를 확인했다. Let's Encrypt 인증서는 2026-10-17까지 유효하고 자동 갱신 timer가 enabled/active이며, 세 서비스의 CloudWatch log stream이 생성됐다.

## 남은 운영 인수

- CloudWatch 경보·SNS와 월 20 USD Budget 알림의 실제 이메일 수신 시험
- RDS snapshot restore drill과 실측 RPO/RTO 기록
- 공개 URL에서 실제 카카오계정 callback·session 브라우저 확인
- 운영·장애·비용 책임자와 공개 종료일 확정
- 수동 snapshot·ECR image·S3 release 보존 정책과 실제 비용 추적
- GitHub Actions Node.js 20 기반 action 사용 경고의 후속 업그레이드 점검

현재 서비스 공개와 재현 가능한 배포는 완료됐으며, 위 항목은 서비스 종료 조건이 아니라 운영 인수와 복구·비용 통제를 완성하기 위한 후속 작업이다.
