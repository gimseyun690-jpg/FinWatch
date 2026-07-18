# 20번 AWS 배포 구현 보고서

기준일: 2026-07-18

판정: **배포 준비 코드 완료 / 실제 AWS 배포 전**

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
| Playwright 기존 흐름 | 22 PASS, 1 worker 순차 실행 1분 36초 |
| production PWA offline route | 1 PASS |
| repository·frontend bundle 고신뢰 Secret scan | PASS |

## 환경 차단 항목

최종 image export와 production-like 컨테이너 기동 smoke는 로컬 디스크 부족으로 완료하지 못했다. 최초 확인 당시 C: 여유 공간은 약 29MiB였고, 2026-07-18 최종 재확인 시에도 0.89GiB로 안전 기준에 미달했다. Docker WSL 커널에는 containerd 가상 디스크의 EXT4 journal abort, write I/O error와 potential data loss가 기록됐다. backend image 내부 Gradle build 자체는 56초 만에 성공했지만 `/var/lib/desktop-containerd` image export에서 I/O error가 발생했다. 컨테이너·image·volume 삭제나 Docker factory reset은 수행하지 않았다.

추가 Docker 쓰기를 중단하고 C:에서 최소 10GiB, 권장 20GiB 이상을 확보한 뒤 Docker Desktop을 재시작해야 한다. I/O error가 계속되면 volume을 먼저 백업하고 Docker Desktop 데이터 복구를 별도로 진행한다. `Clean / Purge data`나 factory reset은 volume을 삭제할 수 있으므로 백업·명시적 승인 없이 실행하지 않는다.

로컬 디스크 확장이 어려운 현재 환경에서는 추가 Docker image build를 중단한다. 동일 품질 게이트는 GitHub Actions의 격리 runner에서 image build와 test를 수행하고, 실제 AWS 임시 배포 환경에서 HTTPS·API·WebSocket·PWA smoke를 수행하는 방식으로 대체한다. 향후 로컬 공간이 확보된 경우에만 다음 명령으로 production-like 검증을 재실행한다.

```powershell
docker compose -f deploy/compose.production-like.yml build
docker compose -f deploy/compose.production-like.yml up -d --wait --wait-timeout 180
Invoke-WebRequest http://localhost:4180/api/v1/health
docker compose -f deploy/compose.production-like.yml ps
docker compose -f deploy/compose.production-like.yml down
```

전체 backend 통합 테스트는 Redis를 요구한다. Docker 엔진이 중단된 동안 오래된 Docker backend가 `6379`만 점유해 연결이 대기한 원인을 thread dump로 확인했고, 해당 프로세스를 정리했다. Docker 복구 후 `scripts/test-backend-windows.ps1 -IncludeDocker`로 PostgreSQL·Redis Testcontainers까지 다시 실행한다.

## 실제 AWS에서 남은 인수

- 계정·리전·도메인·월 예산·책임자·공개 종료일 확정
- CloudFormation stack 생성
- 전용 RDS app user와 provider Secret 초기화
- DNS·TLS·Kakao 운영 Redirect URI 등록
- GitHub Environment 승인과 실제 OIDC AssumeRole
- 외부 HTTPS·WebSocket·Kakao smoke
- CloudWatch·SNS·Budget 알림 시험
- RDS snapshot restore drill과 RPO/RTO 기록

위 항목이 끝나기 전에는 “AWS 공개 배포 완료”가 아니라 “AWS 배포 준비 완료”로 표현한다.
