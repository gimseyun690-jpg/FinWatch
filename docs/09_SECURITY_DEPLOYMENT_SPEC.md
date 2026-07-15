# FinWatch 보안·배포·운영 명세

- 문서 상태: 초안 v0.1
- 기준일: 2026-07-13
- 적용 범위: FinWatch MVP의 로컬·테스트·스테이징·운영 환경
- 관련 문서: `01_REQUIREMENTS.md`, `02_ARCHITECTURE.md`, `03_API_SPEC.md`, `04_DB_SCHEMA.md`, `05_TASKS.md`, `06_DATA_PROVIDER_SPEC.md`, `07_USER_FEATURE_SPEC.md`, `08_AI_OPERATION_SPEC.md`

## 1. 목적과 명세 수준

이 문서는 API 키와 사용자 자격 증명을 코드 밖에서 관리하고, 같은 빌드 산출물을 안전하게 배포·복구·관찰하기 위한 최소 운영 계약을 정의한다. Kubernetes와 다중 리전 같은 확장 설계는 MVP 범위가 아니다.

문서의 용어는 다음과 같이 해석한다.

- **필수(MUST)**: 위반 시 운영 배포를 중단한다.
- **권장(SHOULD)**: 특별한 사유가 없으면 적용한다. 미적용 사유를 배포 기록에 남긴다.
- **현재**: 2026-07-13 저장소에서 확인한 구현 상태다.
- **목표**: MVP 운영 배포 전에 구현·검증해야 하는 상태다.
- **TBD**: 아직 확정되지 않은 값이다. `배포 전 게이트`로 표시된 TBD는 값과 책임자를 정하기 전에는 운영 배포할 수 없다.

## 2. 현재 기준선과 목표

| 영역 | 현재 | MVP 운영 목표 |
|---|---|---|
| 애플리케이션 실행 | Spring Boot와 Vite를 호스트에서 직접 실행 | 검증된 불변 이미지 또는 산출물로 배포 |
| Docker | `compose.yaml`이 로컬 PostgreSQL 17·Redis 8만 실행 | 운영에서는 백엔드와 Nginx를 배포 단위로 만들고 DB·Redis는 AWS 관리형 서비스 사용 |
| 환경 | 기본 설정, `demo`, 필수 공급자 키 fail-fast가 적용된 로컬 `live` 프로필 | `local`, `test`, `staging`, `prod`의 값·데이터·Secret 분리 |
| 인증 | HS256 JWT, 1시간 Access Token, USER/ADMIN, 데모 계정 | 강한 운영 Secret, 데모 계정 비활성화, 계정 공급 절차와 로그인 제한 확정 |
| 브라우저 토큰 | `localStorage`에 세션 저장 | 공개 운영 전 영속 브라우저 저장 제거 또는 HttpOnly 쿠키 방식으로 재설계 |
| CORS | localhost 두 Origin 기본 허용 | 실제 HTTPS Origin만 정확히 허용; 와일드카드 금지 |
| DB 변경 | Flyway V1~V17을 실행하며 Testcontainers PostgreSQL 17에서 검증 | 운영 백업·호환성 검증 후 단일 마이그레이션 수행, 실패 시 트래픽 차단 |
| 상태 확인 | liveness와 DB·Redis readiness 분리, 로컬 LIVE smoke 구현 | 운영 네트워크에서 Actuator 접근 제한 |
| 로그·모니터링 | 기본 Spring 로그, AI 사용량 DB 기록 | 구조화 로그, 요청 ID, CloudWatch 대시보드·경보·보존 정책 |
| CI/CD | 저장소에 워크플로 없음 | PR 품질 게이트, 버전 이미지 생성·스캔, 승인형 운영 배포·롤백 |
| 백업·복구 | 로컬 Docker volume 외 명세 없음 | RDS 자동 백업, 배포 전 스냅샷, 복구 훈련과 RPO/RTO 확정 |

## 3. 환경 분리

### 3.1 환경 계약

| 환경 | 용도 | 데이터 | AI 공급자 | 데모 계정 | Secret 공급원 |
|---|---|---|---|---|---|
| `local` | 개발자 기능 개발 | 로컬 PostgreSQL·Redis의 합성 데이터 | 기본 `mock`, 개발자가 명시할 때만 Gemini | 허용 | 현재 PowerShell 세션 또는 Git에서 제외된 로컬 파일 |
| `test` | 자동 테스트 | 테스트마다 초기화되는 합성 데이터 | Mock·대역만 사용 | 테스트 fixture만 허용 | CI의 임시 값; 실서비스 Secret 금지 |
| `staging` | 배포·통합·E2E 검증 | 운영과 분리된 합성 데이터 | 기본 Mock, 제한된 실호출 smoke만 명시적으로 허용 | 운영과 같은 정책 | 별도 AWS Secret/Parameter 및 CI Environment |
| `prod` | 공개 서비스 | 운영 데이터 | 승인된 공급자 | `DEMO_USERS_ENABLED=false` 필수 | AWS Secrets Manager 또는 암호화된 SSM Parameter Store |

필수 규칙:

1. 운영과 스테이징은 AWS 계정 또는 최소한 VPC, DB, Redis, IAM Role, Secret을 분리한다.
2. 운영 데이터를 로컬·테스트로 복사하지 않는다. 장애 재현에 필요하면 개인정보와 토큰을 제거한 익명 데이터만 사용한다.
3. 동일 커밋으로 만든 산출물을 스테이징에서 검증한 뒤 운영으로 승격한다. 환경별로 코드를 다시 빌드하지 않는다.
4. `prod` 시작 시 로컬 기본값 사용 여부를 검사하고, 기본 DB 비밀번호·기본 JWT Secret·localhost CORS·데모 계정이 발견되면 시작에 실패해야 한다.
5. `SPRING_PROFILES_ACTIVE`와 애플리케이션 버전을 배포 기록과 로그에 남긴다.

현재 `application-live.yml`은 로컬 LIVE 공급자 키를 fail-fast로 검증한다. 운영 전용 `prod` 프로필은 DB·JWT·CORS·데모 계정까지 더 엄격하게 검증해야 하므로 구현 완료 전 공개 운영 배포를 금지한다.

### 3.2 환경별 미확정 항목

다음은 모두 **배포 전 게이트**다.

- AWS 계정, 리전, VPC와 가용 영역: TBD
- 운영 도메인과 DNS 관리자: TBD
- EC2·RDS·ElastiCache 사양과 월 예산: TBD
- 스테이징을 상시 유지할지 배포 때만 생성할지: TBD
- 운영 배포·장애 대응 최종 책임자와 연락 수단: TBD

## 4. 설정과 Secret 관리

### 4.1 분류

| 분류 | 항목 | 저장·전달 규칙 |
|---|---|---|
| Secret | `DB_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY`, 초기 계정 비밀번호 | Secret Manager/SSM SecureString에 저장하고 런타임에만 주입 |
| 민감 설정 | `DB_URL`, `DB_USERNAME`, Redis endpoint/auth, 공급자 endpoint | 환경별 Parameter 또는 Secret으로 관리; 로그에 전체 값 출력 금지 |
| 일반 설정 | `SERVER_PORT`, `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`, `AI_PROVIDER`, `GEMINI_MODEL`, `AI_PROMPT_VERSION`, `AI_CACHE_TTL` | 이미지 밖의 환경변수 또는 환경별 설정으로 관리 |
| 공개 설정 | 프런트 공개 API base URL, 빌드 버전 | 빌드·런타임 설정 가능; Secret으로 취급하지 않음 |

`.env.example`에는 키 이름과 안전한 개발 기본값만 둔다. `.env`, `application-secret.yml`, 개인 키 파일은 Git에서 제외한다. 루트 `.env`는 Docker Compose가 자동으로 읽을 수 있지만 현재 `gradlew bootRun`은 자동으로 읽지 않으므로, 로컬 백엔드에는 PowerShell 환경변수나 IDE의 비공개 실행 설정으로 주입한다.

다음 위치에는 Secret을 넣지 않는다.

- Git 커밋, PR, Issue, Wiki, 스크린샷
- `application.yml`, `compose.yaml`, Dockerfile과 이미지 layer
- 프런트엔드의 `VITE_*` 변수 또는 정적 번들
- URL query string, 애플리케이션 로그, 예외 메시지
- CI 명령행 인자와 테스트 리포트

### 4.2 Secret 수명주기

1. 운영 Secret은 충분한 난수로 생성하고 개발·스테이징과 재사용하지 않는다.
2. EC2 Instance Profile과 최소 권한 IAM 정책으로 필요한 Secret만 읽는다. 장기 AWS Access Key를 EC2나 저장소에 두지 않는다.
3. CI는 환경 보호 규칙이 있는 운영 Secret을 PR 단계에 노출하지 않는다. Fork PR과 일반 빌드는 Mock 값만 사용한다.
4. Secret 값은 로그 마스킹 대상이며, 애플리케이션 시작 로그에는 설정 여부와 비민감 식별자만 남긴다.
5. 정기 교체 주기는 TBD이며 **배포 전 게이트**다. Gemini 키, DB 비밀번호, JWT Secret별 소유자와 교체 절차를 Runbook에 기록한다.
6. 노출이 의심되면 먼저 공급자에서 키를 폐기·교체하고, 영향을 받은 로그·토큰·사용량을 조사한 뒤 사후 기록을 남긴다. Git에서 문자열을 지우는 것만으로 대응을 끝내지 않는다.

JWT Secret을 바꾸면 기존 토큰이 즉시 무효화된다. 이 동작을 계획된 교체와 사고 대응에 명시적으로 이용한다.

## 5. 인증·인가 보안

### 5.1 현재 계약

- 비밀번호는 BCrypt hash로 DB에 저장한다.
- Access Token은 HS256으로 서명하며 `iss`, `iat`, `exp`, `sub`, `userId`, `roles` claim을 포함한다.
- JWT Secret은 코드상 UTF-8 32바이트 미만이면 시작에 실패한다.
- 기본 TTL은 1시간이며 Refresh Token, 서버측 blacklist, 계정 잠금은 없다.
- `/api/v1/admin/**`는 `ADMIN`, 나머지 `/api/v1/**`는 인증된 사용자만 접근한다. 로그인과 health만 공개한다.
- 현재 프런트엔드는 토큰을 `localStorage`에 저장하며 로그아웃은 브라우저 저장 값만 삭제한다.

### 5.2 운영 필수 규칙

1. `JWT_SECRET`은 최소 256비트 난수로 생성하고 기본값을 금지한다. `JWT_ISSUER`는 환경별 고정값으로 검증한다.
2. `DEMO_USERS_ENABLED=false`를 운영 시작 조건으로 강제한다. 문서에 있는 데모 비밀번호로 운영 계정을 만들지 않는다.
3. 현재 회원가입 기능이 없으므로 초기 USER/ADMIN을 만드는 일회성 보안 절차가 필요하다. 생성 명령, 실행자, 비밀번호 전달·변경 방법은 TBD이며 **배포 전 게이트**다.
4. 브라우저 `localStorage`의 JWT는 XSS 시 탈취될 수 있다. MVP 운영은 Access Token을 메모리에만 보관하고 새로고침 시 재로그인하는 방식을 기본 목표로 한다. HttpOnly/Secure/SameSite 쿠키를 택한다면 CSRF 보호까지 함께 설계·테스트해야 한다. 최종 방식은 **배포 전 게이트**다.
5. Bearer header 방식인 동안 서버는 stateless이고 CSRF 비활성화를 유지할 수 있다. 인증을 쿠키로 바꾸면서 CSRF 비활성 상태를 유지해서는 안 된다.
6. 로그인과 AI 생성 API에는 IP·계정 기준 호출 제한을 둔다. 한도와 차단 시간은 TBD이며 **배포 전 게이트**다. 429 응답은 공통 오류 형식을 사용한다.
7. 비밀번호·Bearer Token·전체 JWT claim·Gemini 요청 본문은 로그에 남기지 않는다.
8. ADMIN 계정은 개인별로 분리하는 것이 원칙이다. 공용 관리자 계정이 불가피한 포트폴리오 MVP라면 사용 기간과 접근자를 기록하고 시연 종료 후 폐기한다.

Refresh Token과 강제 로그아웃은 MVP 밖이다. 따라서 토큰 탈취 시 최대 TTL 동안 유효할 수 있다는 잔여 위험을 배포 승인 기록에 남긴다.

## 6. CORS, HTTPS와 네트워크 경계

### 6.1 HTTP 경계

- 운영 외부 통신은 HTTPS만 허용한다. HTTP는 HTTPS로 영구 리다이렉트한다.
- TLS 인증서는 ACM 또는 자동 갱신 가능한 신뢰 체계로 관리한다. 인증서 발급·갱신 책임자는 TBD이며 **배포 전 게이트**다.
- TLS 1.2 이상을 허용하고, 안정화 후 HSTS를 활성화한다.
- Nginx는 `X-Content-Type-Options: nosniff`, 클릭재킹 방지, 제한적 `Referrer-Policy`, `Permissions-Policy`, 애플리케이션에 맞춘 CSP를 설정한다.
- 업로드 기능이 없는 현재 API는 Nginx와 Spring 양쪽에 작은 요청 본문 한도를 둔다. 실제 한도는 TBD이며 **배포 전 게이트**다.

### 6.2 CORS

- Nginx가 프런트와 `/api`를 같은 Origin으로 제공하는 구성을 우선한다.
- 교차 Origin이 필요하면 `CORS_ALLOWED_ORIGINS`에 `https://` 전체 Origin을 정확히 나열한다. `*`, 정규식 전체 허용, 임시 Preview URL 전체 허용은 금지한다.
- 현재 `allowCredentials(true)`이므로 와일드카드 Origin을 사용할 수 없다.
- 허용 메서드는 GET, POST, PATCH, DELETE, OPTIONS로 제한하고 CORS 거부 동작을 자동 테스트한다.

### 6.3 AWS 네트워크

- 인터넷에서 접근 가능한 포트는 Nginx의 80/443뿐이다. SSH는 Session Manager를 우선하며, 불가피한 경우 승인된 IP로만 제한한다.
- Spring Boot 8080은 loopback 또는 내부 Docker network에만 노출한다.
- RDS는 public access를 끄고 EC2 Security Group에서만 5432 접근을 허용한다.
- ElastiCache는 private subnet에 두고 EC2 Security Group에서만 접근시킨다. 전송 암호화와 인증 사용 여부는 상품 선택과 함께 확정한다.
- `/actuator/**`는 외부에서 직접 노출하지 않는다. 외부에는 세부 정보를 숨긴 readiness 결과만 필요한 범위에서 제공한다.

현재 설정은 `/actuator/health`와 함께 `/actuator/info`도 노출하며 Security의 최종 규칙상 외부 접근이 가능하다. 운영 전 노출 목록과 네트워크 경계를 축소해야 한다.

## 7. Docker와 AWS 배포 구조

### 7.1 목표 구조

```text
사용자
  -> DNS / HTTPS
  -> EC2 Nginx :443
       |- React 정적 파일
       `- /api -> Spring Boot 컨테이너 :8080
                     |- RDS PostgreSQL (private)
                     |- ElastiCache Redis (private)
                     `- Gemini 및 승인된 데이터 공급자 (HTTPS)

ECR -> 버전 고정 이미지
Secrets Manager/SSM -> 런타임 설정
CloudWatch <- 애플리케이션·Nginx 로그와 지표
AWS Budgets/CloudWatch Alarm -> 비용·장애 알림
```

MVP는 단일 EC2를 허용하므로 인스턴스 장애 중 무중단을 보장하지 않는다. 가용성 목표와 허용 점검 시간은 TBD이며 **배포 전 게이트**다.

### 7.2 이미지와 런타임 규칙

1. 로컬 `compose.yaml`의 PostgreSQL·Redis는 개발 전용이다. 운영 DB와 Redis를 EC2 Docker volume에 두지 않는다.
2. 백엔드 Dockerfile과 운영 실행 정의는 아직 없다. 운영 전 multi-stage build 또는 CI 산출 JAR 기반의 작은 JRE 21 이미지를 만든다.
3. 이미지는 non-root 사용자로 실행하고 Secret, 소스 저장소, Gradle cache를 포함하지 않는다.
4. 이미지 tag는 `latest`만 사용하지 않고 Git commit SHA와 release version을 기록한다. 배포 기록에는 가능하면 image digest를 남긴다.
5. 컨테이너 로그는 stdout/stderr로 보내고 로컬 파일에만 보존하지 않는다.
6. 종료 신호를 받아 진행 중 요청을 정리할 시간을 주며, 재시작 정책과 health check를 설정한다.
7. Nginx와 백엔드 이미지의 base image 취약점을 배포 전에 스캔한다. Critical 취약점은 예외 승인 없이는 배포할 수 없다.
8. 운영 EC2는 Instance Profile을 사용한다. SSH로 접속해 컨테이너 안에 환경변수를 수동 입력하는 방식을 배포 절차로 사용하지 않는다.

## 8. CI/CD 계약

### 8.1 현재 상태

`.github/workflows`와 동등한 CI/CD 정의가 없고, 백엔드 Dockerfile도 없다. 아래 단계가 자동화되기 전에는 수동 실행 결과와 실행자를 배포 증적으로 남겨야 한다.

### 8.2 목표 파이프라인

| 단계 | 트리거 | 필수 작업 | 실패 시 |
|---|---|---|---|
| PR 검증 | 모든 PR | 백엔드 테스트, 프런트 lint·build, PostgreSQL/Redis 통합 테스트, Flyway 검증, Secret·의존성·정적 분석 | merge 차단 |
| 산출물 | `main` merge 또는 release tag | JAR·프런트 build, 이미지 생성, SBOM·취약점 scan, SHA tag로 ECR push | 배포 차단 |
| 스테이징 | 승인된 main 산출물 | 별도 Secret 주입, migration, readiness, smoke·E2E | 운영 승격 차단 |
| 운영 승인 | release 승인 | 백업 확인, 변경·복구 계획 승인, 동일 digest 배포 | 승인 없이는 실행 금지 |
| 운영 검증 | 배포 직후 | readiness, 핵심 API smoke, 로그·5xx·비용 확인 | 자동 또는 수동 rollback 판단 |

구체적인 테스트와 품질 기준은 `10_TEST_ACCEPTANCE_SPEC.md`를 따른다.

추가 규칙:

- PR 파이프라인은 실제 Gemini·시장·뉴스 API를 호출하지 않는다.
- CI 서비스 계정은 ECR push와 필요한 배포 작업만 허용한다. 운영 승인 권한과 코드 merge 권한을 가능하면 분리한다.
- 브랜치 보호에서 필수 검사를 통과하지 않은 merge를 막는다.
- 배포는 사람이 재현 가능한 스크립트 또는 워크플로로 수행한다. 서버에서 소스를 직접 수정하거나 `git pull` 후 임의 빌드하지 않는다.
- 실패한 단계는 성공으로 덮지 않으며, 예외 배포에는 사유·위험·승인자·만료일을 기록한다.

## 9. DB migration, 백업과 rollback

### 9.1 Flyway 규칙

- 적용된 migration 파일은 수정·삭제·번호 재사용을 금지한다. 변경은 새 버전 파일로 추가한다.
- Hibernate `ddl-auto`는 운영에서도 `validate`를 유지하고 스키마 자동 생성·갱신을 사용하지 않는다.
- CI는 빈 PostgreSQL 17에 V1부터 최신까지 적용하고 애플리케이션이 시작되는지 검증한다. H2 성공만으로 운영 migration을 승인하지 않는다.
- 운영 migration은 백업 성공 후 한 번만 수행한다. 다중 인스턴스가 생기면 별도 migration job 또는 대표 인스턴스 하나만 실행한다.
- 컬럼 삭제·이름 변경·NOT NULL 강제 같은 파괴적 변경은 `확장 -> 데이터 이관 -> 애플리케이션 전환 -> 정리` 단계로 나누고, 이전 앱 버전과 최소 한 배포 동안 호환되게 한다.
- migration 실패 시 readiness를 올리지 않고 새 버전에 트래픽을 보내지 않는다.

### 9.2 백업

- RDS 자동 백업과 Point-in-Time Recovery를 활성화하고 보존 기간은 최소 7일로 한다. 더 긴 최종 기간은 비용과 데이터 정책에 따라 TBD다.
- 스키마 변경이나 대량 데이터 작업 전 수동 snapshot을 만들고 복원 가능 상태를 확인한다.
- snapshot과 backup 접근 권한은 운영자 최소 인원으로 제한하고 암호화를 활성화한다.
- Redis는 재생성 가능한 cache로 취급한다. AI 분석 원본은 PostgreSQL `ai_analyses`가 복구 기준이며 Redis backup은 필수 데이터 백업을 대체하지 않는다.
- 공개 배포 전 실제 snapshot을 별도 DB로 복원하는 훈련을 한 번 수행한다. 이후 훈련 주기는 TBD이며 운영 체크리스트에 등록한다.

RPO와 RTO는 서비스 중요도와 비용에 따라 TBD이며 모두 **배포 전 게이트**다. 잠정 목표조차 정하지 않은 상태에서는 복구 성공 여부를 판단할 수 없다.

### 9.3 rollback

1. 애플리케이션 문제이고 DB 스키마가 하위 호환이면 직전 정상 image digest로 되돌린다.
2. 새 migration이 추가됐더라도 이전 앱과 호환되면 DB를 역변경하지 않고 앱만 되돌린다.
3. 파괴적 migration 이후에는 Flyway 파일 삭제나 수동 `DROP`으로 되돌리지 않는다. 승인된 보정 migration 또는 snapshot/PITR 복원을 사용한다.
4. DB 복원은 복원 시점 이후 데이터 손실 가능성을 명시하고 서비스 중지·승인 후 수행한다.
5. rollback 후 readiness, 로그인, 종목 조회, AI cache/persisted 조회, 관리자 접근을 smoke test하고 사고 기록을 남긴다.

운영 배포마다 직전 정상 digest, DB backup 식별자, rollback 명령과 실행 책임자를 배포 기록에 포함한다.

## 10. Health, 로그, 모니터링과 비용 경보

### 10.1 상태 확인

| 확인 | 의미 | 목표 동작 |
|---|---|---|
| liveness | JVM 프로세스가 응답 가능한가 | 외부 의존성 일시 장애만으로 재시작하지 않음 |
| readiness | 요청을 정상 처리할 수 있는가 | DB 연결과 필수 migration 상태를 포함; 실패 시 트래픽 제외 |
| dependency 진단 | PostgreSQL·Redis·외부 공급자 상태 | 인증된 운영자만 세부 상태 확인 |
| `/api/v1/health` | 단순 공개 상태 | 민감한 endpoint·오류 stack·자격 정보 노출 금지 |

현재 `/api/v1/health`는 의존성을 확인하지 않고 항상 `UP`을 반환한다. Actuator health group과 배포 health check를 구성하기 전에는 readiness 증적으로 사용할 수 없다. Redis는 AI cache 의존성이므로 Redis 장애 시 전체 readiness를 내릴지 AI만 degraded로 둘지는 TBD이며 `08_AI_OPERATION_SPEC.md`의 fallback 정책과 함께 **배포 전 게이트**로 확정한다.

### 10.2 구조화 로그

운영 로그는 한 요청을 추적할 수 있도록 최소 다음 필드를 JSON 또는 파싱 가능한 고정 형식으로 남긴다.

```text
timestamp(UTC), level, service, environment, version,
requestId/traceId, method, routeTemplate, status, durationMs,
errorCode, externalProvider, cacheHit
```

- 클라이언트 `X-Request-Id`는 형식과 길이를 검증한 뒤 사용하거나 서버가 새 값을 만든다. 응답에도 같은 ID를 돌려준다.
- URL의 민감 query, 이메일, 비밀번호, JWT, API 키, DB URL 비밀번호, 기사 전체 본문, Gemini prompt·response 원문은 기록하지 않는다.
- 예외 stack trace는 서버 로그에만 남기고 API 응답에는 내부 구현을 노출하지 않는다.
- 사용자 식별이 필요하면 내부 숫자 ID 또는 비가역 pseudonym을 사용한다. 이메일 원문은 기본 로그에서 제외한다.
- CloudWatch 로그 보존 기간과 archive 정책은 TBD이며 **배포 전 게이트**다.

현재 AI 사용량의 `request_id`는 AI 기능 내부에서 생성되며 HTTP 요청 전체의 상관관계 ID와 연결되지 않는다. 운영 전 통합 요청 ID를 구현한다.

### 10.3 대시보드와 경보

최소 대시보드는 다음을 보여준다.

- 요청 수, 2xx/4xx/5xx 비율, p50/p95 응답시간
- 인스턴스 CPU·메모리·disk, JVM heap·GC, 프로세스 restart
- DB connection pool, RDS CPU·저장공간·connection, migration 실패
- Redis 연결 오류, cache hit/miss, 메모리와 eviction
- 외부 공급자별 성공·429·5xx·timeout, 데이터 최신 시각
- Gemini model call 수, 토큰, 예상 비용, cache hit rate, 절감 추정 비용
- 로그인 실패와 401/403/429 추이

최소 경보 범주는 다음과 같다.

| 경보 | 초기 조건 |
|---|---|
| 서비스 불가 | readiness 연속 실패 또는 5xx 급증 |
| 성능 저하 | p95가 합의한 SLO를 일정 시간 초과 |
| DB/Redis | 연결 실패, 저장공간 부족, Redis eviction 급증 |
| 외부 API | timeout·429·5xx 비율 초과 |
| AWS 비용 | 월 예산의 단계별 비율 도달 |
| AI 비용 | 일·월 호출 또는 예상 비용 한도 도달 |
| 보안 | 로그인 실패·429 급증, Secret scan 탐지 |

구체 임계값, 평가 시간, 알림 채널, 주·야간 대응자는 모두 TBD이며 **배포 전 게이트**다. AI 비용 한도 초과 시 신규 MISS 호출을 중단하고 기존 cache/DB 결과만 제공하거나 `AI_PROVIDER=mock`으로 전환하는 비상 절차를 `08_AI_OPERATION_SPEC.md`와 일치시킨다.

## 11. 장애 대응과 복구 정책

| 장애 | 사용자 영향 | 목표 대응 | 현재 차이 |
|---|---|---|---|
| Spring Boot crash | 전체 API 중단 | health check 감지, 컨테이너 재시작, 반복 시 직전 image rollback | 운영 restart/rollback 자동화 없음 |
| Redis 장애 | AI cache 사용 불가 | 일반 기능 유지, DB의 기존 분석을 우선 제공, 신규 MISS 정책에 따라 제한·오류 | 현재 Redis 예외가 DB fallback 전에 요청을 실패시킬 수 있음 |
| Gemini timeout/429/5xx | 신규 AI 요약 실패 | 짧은 timeout·제한된 retry/backoff, 기존 결과 제공, 명확한 429/502/503 | 현재 명시적 timeout/retry/circuit breaker 없음 |
| RDS 장애 | 인증·조회·저장 중단 | readiness DOWN, 쓰기 중단, RDS 복구 또는 PITR, 무결성 smoke test | 복구 Runbook 없음 |
| EC2/Nginx 장애 | 전체 서비스 중단 | ECR의 정상 digest와 환경 설정으로 재생성 | IaC와 재생성 절차 없음 |
| Flyway 실패 | 새 버전 시작 실패 | 트래픽 전환 금지, 로그 확인, 호환 앱 rollback 또는 보정 migration | 시작 시 자동 실행만 구성됨 |
| 데이터 공급자 지연 | 가격·뉴스가 오래됨 | 마지막 정상값의 source/asOf와 stale 상태 표시, 수집 중단 경보 | KIS·Naver·Finnhub·공시 어댑터 구현, 제출 전 LIVE smoke 필요 |
| Secret 노출 | 외부 오용·계정 탈취 | 즉시 폐기·교체, JWT 강제 무효화, 로그·비용 조사 | 교체 Runbook 없음 |
| AI 비용 급증 | 예산 초과 | 사용자·IP rate limit, 일 한도, MISS 차단, 운영자 경보 | 관리자 사후 지표만 존재 |

자동 retry는 멱등성과 공급자 요금 중복 발생을 검토한 경우에만 사용하며 무한 재시도를 금지한다. 장애 중 임의로 데모 데이터나 Mock 응답을 실데이터처럼 표시하지 않고 `source`와 degraded 상태를 사용자에게 밝힌다.

장애 기록에는 시작·탐지·완화·복구 시각, 영향 범위, 관련 request ID, 원인, 데이터 손실 여부, 비용, 재발 방지 작업과 담당자를 남긴다.

## 12. 운영 체크리스트

### 12.1 최초 공개 전

- [ ] 운영 AWS 계정·리전·도메인·책임자가 확정됐다.
- [ ] `prod` 프로필의 fail-fast 검증이 있고 로컬 기본값으로 시작되지 않는다.
- [ ] `DEMO_USERS_ENABLED=false`이며 초기 계정 공급 절차를 검증했다.
- [ ] JWT 브라우저 저장 방식과 로그인·AI rate limit을 확정·테스트했다.
- [ ] 운영 Secret을 저장소 밖에 만들고 읽기 권한과 교체 절차를 검증했다.
- [ ] HTTPS, 정확한 CORS, 보안 header, private RDS·Redis Security Group을 검증했다.
- [ ] 이미지 scan 결과와 배포 digest를 기록했다.
- [ ] RDS 자동 백업·PITR·암호화를 켜고 실제 restore를 1회 검증했다.
- [ ] readiness, 로그 수집, 대시보드, 장애·비용 경보가 동작한다.
- [ ] RPO/RTO, 로그 보존, 비용 예산, 경보 연락 채널을 확정했다.
- [ ] `10_TEST_ACCEPTANCE_SPEC.md`의 운영 배포 게이트와 MVP 인수 시나리오가 통과했다.

### 12.2 매 배포 전

- [ ] 변경 범위, DB migration, Secret·비용 영향, 장애 가능성을 검토했다.
- [ ] PR 필수 검사와 스테이징 smoke/E2E가 같은 commit SHA에서 성공했다.
- [ ] 운영 Secret을 로그로 출력하지 않고 필요한 값이 존재하는지만 확인했다.
- [ ] backup 또는 snapshot ID, 직전 정상 image digest, rollback 명령을 기록했다.
- [ ] migration의 이전 앱 호환성과 예상 실행 시간을 확인했다.
- [ ] 배포자·승인자·관찰 담당자와 점검 시간을 기록했다.

### 12.3 배포 직후

- [ ] liveness와 readiness가 정상이다.
- [ ] 로그인, USER API, ADMIN 권한, 종목 조회, AI 기존 결과와 신규 요약 smoke가 정상이다.
- [ ] 5xx, latency, DB connection, Redis 오류, Gemini 호출·비용이 기준 범위다.
- [ ] 프런트 정적 자산과 Service Worker가 새 버전을 제공한다.
- [ ] 배포 버전·digest·migration 버전·검증 증적을 release 기록에 남겼다.

### 12.4 장애·rollback 후

- [ ] 영향 사용자와 데이터 손실 범위를 확인했다.
- [ ] 직전 버전과 DB 호환성을 확인하고 승인된 절차로 복구했다.
- [ ] 핵심 smoke와 데이터 무결성 검사를 다시 수행했다.
- [ ] 임시 자격 증명과 노출 가능 Secret을 폐기했다.
- [ ] 사후 분석과 재발 방지 작업을 Issue로 만들고 담당자·기한을 정했다.

## 13. 배포 차단 TBD와 완료 정의

다음 항목 중 하나라도 미확정·미검증이면 공개 운영 배포를 차단한다.

1. AWS 리전·도메인·리소스 사양·월 예산
2. 운영/스테이징 분리 수준과 배포·장애 책임자
3. 초기 운영 계정 공급과 브라우저 JWT 저장 방식
4. 로그인·AI rate limit과 요청 크기 제한
5. 인증서 갱신, Redis 암호화·인증 방식
6. RPO, RTO, backup·로그 보존 기간과 restore 훈련 주기
7. 가용성·성능 SLO와 각 경보 임계값·알림 채널
8. AI 비용 한도와 자동·수동 차단 방식

이 문서의 완료 증적은 배포 workflow 실행 링크 또는 로그, image digest, 설정 점검 결과(값 자체 제외), backup/restore 기록, health·smoke 결과, CloudWatch 대시보드·경보 시험 결과와 승인 기록이다.
