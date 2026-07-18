# FinWatch AWS 공개 배포 명세

상태: Implementation v0.2 — 배포 코드·IaC·CI/CD 구현, 실제 AWS 적용 전

기준일: 2026-07-18

대상: React PWA, Spring Boot, PostgreSQL, Redis, AWS 인프라·CI/CD·운영

## 1. 목적

이 문서는 로컬 PC가 꺼져 있어도 심사위원과 포트폴리오 방문자가 FinWatch를 HTTPS 주소로 사용할 수 있도록 AWS에 배포하는 구조와 운영 규칙을 정의한다.

핵심 목표:

1. React PWA, REST API와 WebSocket을 하나의 HTTPS Origin으로 제공한다.
2. DB와 Secret을 인터넷·Git·Docker image에서 분리한다.
3. 저비용 포트폴리오 구조로 시작하되 운영형 구조로 확장 가능하게 한다.
4. 배포·migration·smoke·rollback을 반복 가능하게 만든다.
5. 로그, 경보, 백업, 복구와 월 비용 상한을 증적으로 남긴다.
6. 카카오 callback을 포함한 외부 provider 연동을 안전하게 제공한다.

카카오 인증 내부 흐름은 `19_KAKAO_LOGIN_SPEC.md`, 공통 보안 게이트는 `09_SECURITY_DEPLOYMENT_SPEC.md`를 따른다.

## 2. 현재 기준선

- 개발 환경은 Spring Boot·Vite와 로컬 PostgreSQL 17·Redis 8을 사용한다.
- backend·frontend multi-stage Dockerfile과 non-root runtime을 제공한다.
- 운영 Nginx는 동일 Origin에서 SPA·PWA·REST API·WebSocket을 제공하고 public Actuator를 차단한다.
- `portfolio` profile은 HTTPS·CORS·secure cookie·private Redis·TLS PostgreSQL·dedicated DB user·실제 AI provider를 fail-fast 검증한다.
- 브라우저 인증은 19번 명세에 따라 Redis opaque session과 HttpOnly cookie·CSRF로 변경되었다.
- CloudFormation은 VPC·EC2·private RDS·ECR·S3·SSM·Secrets·CloudWatch·Budget을 정의한다.
- GitHub Actions는 CI와 OIDC 기반 수동 portfolio 배포를 정의한다.
- 실제 AWS 계정·리전·도메인·예산·책임자가 미확정이므로 AWS resource는 아직 만들지 않았다.
- 실행 절차와 미완료 게이트는 `20_AWS_RUNBOOK.md`에 기록한다.

## 3. 환경

| 환경 | 목적 | 공개 범위 | 데이터 | Secret |
|---|---|---|---|---|
| local | 개발 | localhost | 합성 | 로컬 환경변수 |
| test | 자동화 | 비공개 | fixture | 임시 Mock |
| staging | 통합·배포 검증 | 제한 | 운영과 분리 | 별도 AWS Secret |
| portfolio | 대회·포트폴리오 공개 | 공개 HTTPS | 최소 운영 데이터 | AWS Secret |
| prod | 실제 운영 승격 | 공개 HTTPS | 운영 | 별도 AWS 계정/경계 권장 |

`portfolio`는 저비용 단일 서버이며 고가용성을 주장하지 않는다. `prod`는 별도 승인과 2차 구조를 필요로 한다.

## 4. 범위와 제외 범위

### 4.1 포함

- VPC, subnet, Security Group
- EC2, Elastic IP, Nginx, Docker
- RDS PostgreSQL
- Portfolio Redis와 운영형 ElastiCache/Valkey
- ECR
- Route 53 또는 외부 DNS
- HTTPS
- SSM Parameter Store·Secrets Manager·Instance Profile
- CloudWatch·Budgets
- GitHub Actions OIDC CI/CD
- Flyway, backup, restore, rollback
- API·WebSocket·PWA·Kakao smoke

### 4.2 제외

- EKS·Kubernetes
- 다중 리전
- 실제 매매 인프라
- 24시간 무중단 SLA
- 대규모 CDN·WAF 최적화
- 인증·사용자 스키마 상세

## 5. 확정 배포 전략

### 5.1 1차 — Portfolio 권장

```text
사용자 / OAuth callback
        |
        v HTTPS :443
Route 53 또는 외부 DNS
        |
Elastic IP
        |
EC2 (Docker)
  Nginx :443
    |- React 정적 PWA
    |- /api -> Spring Boot :8080
    `- /ws  -> Spring Boot WebSocket
  Redis :6379 (internal only)
        |
        +--> RDS PostgreSQL private :5432
        +--> Kakao/Gemini/KIS/Finnhub 등 HTTPS

ECR -> versioned backend image
SSM/Secrets -> runtime configuration
CloudWatch <- logs·metrics
AWS Budgets -> cost alerts
```

선택 이유:

- 구현·운영 단위가 단순하다.
- ALB, NAT Gateway와 관리형 Redis의 고정비를 초기에는 피할 수 있다.
- RDS를 분리해 EC2 교체에도 영구 데이터를 보존한다.
- 향후 ALB·다중 compute·ElastiCache로 확장할 수 있다.

### 5.2 2차 — Production-like

```text
Route 53
  -> Public ALB :443 + ACM
       -> private EC2/ECS targets
            -> RDS PostgreSQL private, Multi-AZ 선택
            -> ElastiCache/Valkey private
```

- ALB가 HTTPS 종료와 health check를 담당한다.
- 여러 target은 공유 Redis session을 사용한다.
- compute private subnet의 NAT/VPC endpoint 비용을 사전에 산정한다.
- Multi-AZ와 Auto Scaling은 가용성·예산 요구 확정 후 적용한다.

## 6. VPC와 네트워크

### 6.1 Portfolio

- 최소 두 AZ에 RDS DB subnet group을 구성한다.
- EC2는 인터넷 서비스를 위해 public subnet과 Elastic IP를 사용할 수 있다.
- RDS는 private subnet과 `publiclyAccessible=false`를 사용한다.
- route와 NACL을 기본 허용으로 방치하지 않고 필요한 경로를 확인한다.

### 6.2 Security Group

| SG | Inbound | Outbound |
|---|---|---|
| EC2 Web | 80/443 from internet | RDS, HTTPS providers, AWS endpoints |
| RDS | 5432 from EC2 SG only | response |
| ElastiCache(2차) | 6379 from app SG only | response |
| ALB(2차) | 80/443 from internet | target port |

- 22, 8080, 5432와 6379를 인터넷에 노출하지 않는다.
- SSH 대신 Systems Manager Session Manager를 사용한다.
- Actuator 상세 endpoint를 Nginx public route에서 차단한다.
- readiness는 최소 정보만 노출한다.

## 7. EC2

- Java 21, Spring Boot, Nginx와 Redis를 감당할 사양을 부하 테스트로 선택한다.
- root EBS volume을 암호화한다.
- Amazon Linux 등 지원되는 OS와 SSM Agent를 사용한다.
- EC2 Instance Profile로 ECR pull, SSM, 필요한 Parameter/Secret read만 허용한다.
- static AWS access key와 SSH private key를 배포 방식으로 사용하지 않는다.
- 8080·6379는 Docker network 또는 loopback만 사용한다.
- Elastic IP, AMI, instance type과 volume을 배포 기록에 남긴다.
- CPU, memory, disk와 restart를 CloudWatch에서 관찰한다.

단일 EC2 장애 중 서비스가 중단되고 Redis session이 사라질 수 있다. 사용자는 재로그인하며 영구 데이터는 RDS에서 보존한다.

## 8. HTTPS·DNS·Nginx

### 8.1 DNS

- 운영 도메인을 확정하고 A/AAAA 또는 alias record를 관리한다.
- Kakao, CORS, cookie와 `APP_PUBLIC_BASE_URL`은 같은 canonical HTTPS host를 사용한다.
- 임시 public IP를 Kakao 운영 redirect로 장기 사용하지 않는다.

### 8.2 Portfolio HTTPS

direct EC2에서는 Nginx와 자동 갱신 가능한 공개 인증서를 사용한다.

- HTTP 80은 HTTPS redirect와 인증서 발급 용도로만 사용한다.
- TLS 1.2 이상을 허용한다.
- HSTS는 HTTPS 운영 안정화 후 적용한다.
- 인증서 자동 갱신과 만료 경보를 검증한다.
- ACM 인증서는 direct EC2 Nginx에 설치하는 방식이 아니다. ACM은 ALB/CloudFront 등 지원 지점과 사용한다.

### 8.3 Nginx

- React 정적 파일과 PWA asset을 제공한다.
- 존재하지 않는 static asset을 제외한 SPA route는 `index.html`로 fallback한다.
- `/api/`는 Spring Boot로 proxy한다.
- `/ws/`는 upgrade header와 timeout을 설정한다.
- `/actuator/` public 접근을 차단한다.
- `X-Forwarded-Proto/Host`를 전달하고 Spring의 신뢰 범위를 제한한다.
- CSP, `nosniff`, frame 방지, Referrer/Permissions Policy를 설정한다.
- Service Worker는 장기간 immutable cache하지 않는다.

## 9. RDS PostgreSQL

- PostgreSQL 호환 버전은 현재 PostgreSQL 17 테스트 기준과 검증한다.
- private subnet, public access off, EC2 SG만 허용한다.
- storage encryption을 활성화한다.
- 애플리케이션 연결은 SSL을 사용한다.
- automated backup과 retention을 확정한다.
- migration 전 manual snapshot을 만든다.
- 정기적으로 별도 RDS에 restore해 복구를 검증한다.
- DB master 계정을 애플리케이션 계정으로 사용하지 않는다.
- connection pool 상한을 instance connection 한도에 맞춘다.
- parameter group 변경과 maintenance window를 기록한다.

### 9.1 Flyway

- 배포당 하나의 주체만 migration을 실행한다.
- migration 실패 시 새 버전 traffic을 열지 않는다.
- 이미 적용된 migration 파일을 수정하지 않는다.
- expand → app deploy → contract 순서가 필요한 변경은 여러 release로 분리한다.
- schema version과 app version을 배포 증적에 남긴다.

## 10. Redis

### 10.1 Portfolio EC2 Redis

- Docker internal network만 사용하고 host public port를 열지 않는다.
- session과 cache만 저장하며 영구 사용자 데이터 원본으로 사용하지 않는다.
- EC2 재생성 시 session 손실·재로그인을 허용한다.
- maxmemory, eviction과 persistence 정책을 기록한다.
- AI cache eviction이 로그인 session을 제거하지 않도록 key·memory 분리 또는 정책을 검토한다.

### 10.2 운영 승격

- private ElastiCache/Valkey 또는 관리형 Redis로 이동한다.
- application SG만 접근한다.
- 전송 암호화와 인증을 적용한다.
- 두 개 이상 app target이 같은 session 저장소를 사용한다.
- failover 중 session·cache 동작을 테스트한다.

## 11. Docker·ECR

### 11.1 Backend image

- multi-stage build와 작은 Java 21 runtime image
- non-root user
- Secret·소스·Gradle cache 최종 layer 제외
- graceful shutdown과 health check
- stdout/stderr 구조화 로그
- commit SHA와 release version tag
- 가능하면 image digest 기록
- Critical image 취약점 배포 차단 또는 예외 승인

### 11.2 Frontend

- production build 결과만 Nginx image 또는 배포 artifact에 포함한다.
- `VITE_*`에는 공개 값만 둔다.
- API key, Kakao Client secret, DB 정보가 bundle에 없어야 한다.
- source map 공개 여부를 환경별로 결정한다.

### 11.3 배포 Compose

운영 Compose는 최소 다음 service를 가진다.

```text
nginx
backend
redis  # portfolio only
```

PostgreSQL은 운영 Compose에 넣지 않고 RDS를 사용한다. image는 `latest` 대신 고정 tag/digest를 사용한다.

## 12. Secret과 설정

### 12.1 분류

| 분류 | 예 | 저장 |
|---|---|---|
| Secret | DB password, Kakao secret, Gemini/KIS/Finnhub keys | Secrets Manager 또는 SSM SecureString |
| 민감 설정 | DB URL/user, Redis endpoint | Parameter/SecureString |
| 일반 설정 | TTL, issuer, provider mode | Parameter 또는 deployment env |
| 공개 설정 | public base URL, app version | runtime/build config |

### 12.2 규칙

- EC2 Instance Profile이 필요한 path만 읽는다.
- Secret을 Git, image layer, `.env` commit, command line, log에 넣지 않는다.
- CI가 production Secret 값을 읽지 않도록 runtime injection을 우선한다.
- Secret 조회 실패나 기본 password·JWT·demo account 발견 시 fail fast한다.
- Kakao·DB·provider별 교체 책임자와 runbook을 둔다.
- 프런트 `VITE_*`에 비밀값을 넣지 않는다.

환경 키 예시:

```text
SPRING_PROFILES_ACTIVE=portfolio
APP_PUBLIC_BASE_URL=https://finwatch.example
CORS_ALLOWED_ORIGINS=https://finwatch.example
DEMO_USERS_ENABLED=false
DB_URL=jdbc:postgresql://private-rds:5432/finwatch
REDIS_HOST=redis
REDIS_PORT=6379
```

카카오·session key는 `19_KAKAO_LOGIN_SPEC.md`를 따른다.

## 13. CI/CD

### 13.1 GitHub Actions AWS 인증

- GitHub OIDC로 AWS IAM Role을 assume한다.
- 장기 `AWS_ACCESS_KEY_ID/SECRET_ACCESS_KEY`를 저장하지 않는다.
- trust policy를 정확한 repository, branch 또는 protected environment로 제한한다.
- PR은 deploy role을 assume하지 못한다.
- deploy role은 ECR push와 필요한 배포 작업만 허용한다.

### 13.2 Pipeline

```text
PR
  -> backend test
  -> frontend lint/build/E2E
  -> dependency/image scan

release 승인
  -> 동일 commit artifact/image build
  -> ECR push(SHA tag)
  -> deployment configuration render
  -> migration 1회
  -> EC2 deploy via SSM
  -> readiness·smoke
  -> 실패 시 이전 image rollback
```

- 수동 배포 시에도 commit, image digest, 실행자와 결과를 기록한다.
- 같은 release를 staging에서 검증한 뒤 portfolio/prod로 승격한다.
- 배포 중 Secret을 workflow output·artifact에 기록하지 않는다.

## 14. 배포·롤백

### 14.1 배포

1. test·lint·build·E2E 통과
2. image scan
3. RDS snapshot
4. ECR image push
5. Flyway migration 1회
6. 새 backend·frontend 실행
7. readiness와 smoke
8. 버전·시간·결과 기록

### 14.2 롤백

- 이전 image tag/digest와 Nginx artifact를 보존한다.
- app rollback과 DB rollback을 구분한다.
- destructive migration은 즉시 app rollback을 막을 수 있으므로 expand/contract를 사용한다.
- rollback 후 readiness, API, PWA, WebSocket과 로그인 smoke를 다시 수행한다.
- 실패 원인과 임시 조치를 배포 기록에 남긴다.

## 15. 관찰성

### 15.1 구조화 로그

- timestamp, level, environment, appVersion
- requestId, route template, status, latency
- 비민감 user 식별자, role, authProvider
- provider operation과 error code
- AI operation, cache HIT/MISS

금지:

- password, cookie, session ID
- Authorization header와 JWT
- OAuth code, state, nonce, verifier, Kakao token
- DB password와 API key
- 불필요한 provider response 원문

### 15.2 CloudWatch

- EC2 CPU, memory, disk
- backend 5xx·latency·readiness
- login callback 실패율
- RDS CPU, storage, connection
- Redis memory·eviction
- provider 연속 실패
- 인증서 만료
- AWS 비용·예산

log group별 보존 기간을 설정하고 무기한 기본 보관을 피한다.

## 16. 백업·복구

- RDS automated backup 활성화
- migration 전 snapshot
- 정기 restore drill
- ECR 이전 image 보존
- Nginx·Compose·infra 설정을 코드로 보존
- EC2 재생성 runbook
- Secret 재주입·DNS·인증서 복구 절차
- session Redis 손실 시 재로그인 정책

RPO, RTO, backup retention과 복구 담당자는 공개 전에 확정한다.

## 17. 비용

- AWS Pricing Calculator로 리전·사양별 월 예상치를 기록한다.
- EC2, EBS, RDS, snapshot, Elastic IP, ECR, CloudWatch, DNS, data transfer를 포함한다.
- ALB, NAT Gateway, ElastiCache는 2차 구조 전 별도 비용 승인을 받는다.
- AWS Budgets 알림을 설정한다.
- 비용 태그 `Project=FinWatch`, `Environment=portfolio` 등을 사용한다.
- 공개 종료일을 정하고 불필요한 resource를 중지·삭제한다.
- 무료 사용 가능성을 전제로 비용 0이라고 발표하지 않는다.

## 18. 보안

- OS와 container base image를 갱신한다.
- SSH를 닫고 SSM Session Manager를 사용한다.
- IAM user 장기 key 대신 role을 사용한다.
- RDS·Redis public access를 금지한다.
- 동일 Origin HTTPS와 정확한 CORS allow-list를 사용한다.
- cookie 인증·CSRF는 19번 명세를 따른다.
- ADMIN은 개인별 계정과 최소 인원으로 운영한다.
- 데모 계정은 portfolio 공개 시 비활성화한다.
- Actuator, DB console과 debug endpoint를 외부에 노출하지 않는다.
- 공급자 key와 사용자 정보가 screenshot·발표 영상에 나오지 않게 한다.

## 19. AWS Smoke 계약

- DNS가 canonical host를 가리킨다.
- 인증서 chain과 HTTPS가 정상이다.
- HTTP가 HTTPS로 redirect된다.
- `/`와 SPA direct route가 동작한다.
- PWA manifest, icon, Service Worker와 offline shell이 동작한다.
- `/api/v1/health`와 제한된 readiness가 동작한다.
- WebSocket이 proxy를 통과한다.
- EC2에서 RDS·Redis를 연결한다.
- 외부에서 22·8080·5432·6379에 접근할 수 없다.
- Kakao authorize/callback URI가 등록값과 일치한다.
- Gemini·KIS·Finnhub 등 활성 provider가 fail-fast와 timeout 규칙을 따른다.
- CloudWatch log와 alarm test가 확인된다.
- RDS snapshot·restore와 image rollback runbook이 검증된다.

## 20. 구현 단계

### Phase A — 배포 산출물

1. backend Dockerfile
2. frontend production build·Nginx
3. deployment Compose
4. health check와 graceful shutdown
5. local production-like smoke

### Phase B — AWS 기반

1. AWS 계정·리전·예산
2. VPC·subnet·SG
3. EC2·Instance Profile·SSM
4. ECR
5. private RDS·backup
6. Parameter/Secrets

### Phase C — 공개

1. DNS·Elastic IP·HTTPS
2. deploy와 Flyway
3. CloudWatch·Budgets
4. 전체 smoke
5. Kakao portfolio redirect 등록
6. 발표용 URL·계정·데이터 고정

### Phase D — 운영 승격

1. ALB+ACM
2. private multi-target compute
3. ElastiCache/Valkey
4. RDS Multi-AZ 선택
5. failover·restore·부하 테스트

## 21. 배포 전 게이트

- AWS 계정·리전·책임자
- canonical domain
- 월 예산과 경보 수신자
- EC2·RDS 사양
- RPO/RTO·backup retention
- portfolio 공개 종료일
- Secret 교체 절차
- staging 상시 유지 여부
- 장애 연락·복구 담당자
- production-like 승격 조건

## 22. 인수 조건

- [ ] React, API와 WebSocket이 하나의 HTTPS Origin에서 동작한다.
- [ ] 외부에 80/443만 노출된다.
- [ ] EC2 접속은 Session Manager를 사용한다.
- [ ] backend image가 non-root·version tag·health check를 사용한다.
- [ ] RDS가 private·암호화·자동 백업 상태다.
- [ ] RDS 5432는 EC2 SG만 허용한다.
- [ ] Redis 6379는 public에 노출되지 않는다.
- [ ] Secret이 SSM/Secrets와 Instance Profile로 주입된다.
- [ ] Secret이 Git·image·frontend bundle·로그에 없다.
- [ ] demo account와 안전하지 않은 기본값으로 공개 서버가 시작되지 않는다.
- [ ] SPA direct route와 PWA offline shell이 동작한다.
- [ ] Nginx API·WebSocket proxy가 동작한다.
- [ ] Actuator 상세 정보가 외부에 노출되지 않는다.
- [ ] GitHub Actions가 OIDC Role을 사용한다.
- [ ] 배포당 Flyway migration이 한 번만 실행된다.
- [ ] production build·test·lint·E2E가 배포 전에 통과한다.
- [ ] CloudWatch log·경보와 AWS Budget이 설정된다.
- [ ] RDS snapshot·restore와 image rollback이 검증된다.
- [ ] Kakao Login을 포함한 전체 AWS smoke가 통과한다.
- [ ] 월 비용 상한과 resource 정리일이 기록된다.

미완료 항목이 남아 있으면 “AWS 공개 배포 완료”로 표시하지 않는다.

### 22.1 구현 기록

저장소에 구현된 재현 가능 산출물:

- [x] backend·frontend multi-stage Dockerfile, non-root user, health check
- [x] production-like·portfolio Compose와 backend·Redis 비공개 네트워크 경계
- [x] TLS·SPA fallback·PWA cache header·API/WebSocket proxy·Actuator 차단 Nginx 설정
- [x] `portfolio` 안전 설정 검증기와 자동화 테스트
- [x] request ID 전파와 query string을 제외한 HTTP 상태·지연 구조 로그
- [x] VPC·SG·SSM 전용 EC2·private encrypted RDS·ECR·Secrets·CloudWatch·Budget CloudFormation
- [x] GitHub repository/environment로 제한한 OIDC 배포 역할 CloudFormation
- [x] SHA image build·ECR scan·snapshot·SSM deploy·smoke GitHub Actions
- [x] Secret runtime render·전용 DB user·TLS·deploy·rollback·public smoke script
- [x] 운영·복구·비용 정리 런북

실제 AWS 환경에서만 닫을 수 있는 항목:

- [ ] CloudFormation stack 생성과 SG 외부 포트 검증
- [ ] DNS·TLS·Kakao 운영 Redirect URI 등록
- [ ] RDS app user·provider Secret 초기화와 첫 Flyway migration
- [ ] GitHub Environment 승인 후 OIDC 실제 AssumeRole
- [ ] 외부 HTTPS·PWA·WebSocket·Kakao 전체 smoke
- [ ] CloudWatch alarm·SNS·Budget 알림 시험
- [ ] RDS snapshot restore drill과 RPO/RTO 기록
- [ ] 공개 종료일·운영 책임자·최종 비용 상한 확정

## 23. 공식 참고 문서

- [AWS Systems Manager Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html)
- [AWS Systems Manager Session Manager](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager.html)
- [Amazon RDS for PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html)
- [Amazon RDS 암호화](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.Encryption.html)
- [Application Load Balancer HTTPS listener](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-https-listener.html)
- [AWS IAM OIDC federation](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_providers_oidc.html)

구현 시작과 공개 배포 직전에 서비스 지원 범위와 console 절차를 다시 확인한다.

## 24. 관련 문서

- `03_API_SPEC.md`
- `04_DB_SCHEMA.md`
- `05_TASKS.md`
- `09_SECURITY_DEPLOYMENT_SPEC.md`
- `10_TEST_ACCEPTANCE_SPEC.md`
- `16_COMPETITION_READINESS.md`
- `17_NAVIGATION_AND_CONTENT_LIST_SPEC.md`
- `19_KAKAO_LOGIN_SPEC.md`
- `20_AWS_RUNBOOK.md`
- `20_AWS_IMPLEMENTATION_REPORT.md`
