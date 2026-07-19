# FinWatch AWS 공개 배포 런북

상태: AWS 기반 스택·GitHub OIDC 준비 완료, 최초 TLS와 애플리케이션 배포 진행 중

기준일: 2026-07-19

이 문서는 `20_AWS_DEPLOYMENT_SPEC.md`를 실제 AWS 계정에서 실행할 때의 순서다. CloudFormation을 실행하면 EC2, EBS, RDS, Elastic IP, Route 53, CloudWatch, Secrets Manager 등에서 비용이 발생할 수 있다. 담당자와 상한을 확정하기 전에는 스택을 만들지 않는다.

## 1. 배포 전 승인 게이트

다음 값을 기록하고 책임자가 승인한다.

| 항목 | 확정값 |
|---|---|
| AWS 계정 ID / 리전 | 별도 운영 기록 / `ap-northeast-2` |
| canonical domain / Hosted Zone ID | `finwatch-hyphoenix.duckdns.org` / 외부 DNS |
| 비용 알림 이메일 / 월 상한(USD) | 운영 이메일 / 20 |
| EC2 / RDS 사양 | `t3.small` / `db.t4g.micro` 초안 |
| RPO / RTO / 백업 보존 | 24시간 / 4시간 / 1일(AWS Free Plan) |
| 공개 시작·종료일 | 미정 |
| 운영·장애·비용 책임자 | 미정 |
| Kakao 운영 Redirect URI 승인 | 미정 |

또한 선택한 리전에서 PostgreSQL 17 엔진 버전과 인스턴스 클래스를 확인하고, Redis와 Certbot 이미지는 digest 또는 고정 버전으로 정한다. `latest` 태그는 사용하지 않는다.

## 2. 로컬 사전 검증

저장소 루트에서 실행한다.

```powershell
docker compose -f deploy/compose.production-like.yml build
docker compose -f deploy/compose.production-like.yml up -d --wait --wait-timeout 180
Invoke-WebRequest http://localhost:4180/api/v1/health
docker compose -f deploy/compose.production-like.yml ps
docker compose -f deploy/compose.production-like.yml down
```

필수 확인:

- 프론트 컨테이너만 호스트 `4180`에 공개된다.
- backend·Redis에는 published port가 없다.
- `/`, 직접 route, manifest, Service Worker, `/api/v1/health`가 정상이다.
- `/actuator/health`는 Nginx 외부 경로에서 404다.
- 두 앱 이미지는 non-root이며 health check가 있다.

## 3. AWS 기반 스택

AWS CLI 자격 증명은 관리자 개인의 단기 세션을 사용한다. 키를 파일이나 GitHub Secret에 저장하지 않는다.

```bash
aws cloudformation validate-template \
  --region "$AWS_REGION" \
  --template-body file://infra/aws/portfolio.yml

aws cloudformation deploy \
  --region "$AWS_REGION" \
  --stack-name finwatch-portfolio \
  --template-file infra/aws/portfolio.yml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    PublicHost=finwatch.example.com \
    CertificateEmail=owner@example.com \
    HostedZoneId='' \
    BudgetEmail=owner@example.com \
    MonthlyBudgetUsd=20 \
    DBBackupRetentionDays=1 \
    RedisImage=redis@sha256:REPLACE_WITH_VERIFIED_DIGEST \
    CertbotImage=certbot/certbot:vREPLACE_WITH_PINNED_VERSION
```

AWS Free Plan 계정에서는 RDS 자동 백업 보존 기간 상한에 맞게 `DBBackupRetentionDays=1`을 사용한다. 유료 플랜에서는 기본값 7일을 유지할 수 있다.

완료 후 스택 Outputs를 별도 배포 기록에 저장한다. `DatabaseMasterSecretArn`은 애플리케이션 EC2 역할에서 읽을 수 없도록 설계되어 있다.

## 4. 전용 DB 사용자 생성

이 단계만 RDS master secret을 읽을 수 있는 보안 운영 세션에서 수행한다. EC2 Instance Profile이나 GitHub 배포 역할에 master secret 권한을 추가하지 않는다.

```bash
export PGHOST='stack-output-database-endpoint'
export MASTER_USERNAME='finwatch_admin'
export MASTER_PASSWORD='temporary-secure-value'
export APP_USERNAME='finwatch_app'
export APP_PASSWORD='generated-database-application-secret-value'
bash scripts/aws/bootstrap-rds-app-user.sh
unset PGHOST MASTER_USERNAME MASTER_PASSWORD APP_USERNAME APP_PASSWORD
```

SSM으로 EC2에서 부트스트랩할 때는 master secret에 대한 읽기 권한을 해당 작업 동안에만 임시로 부여하고 다음 wrapper를 사용한다. 이 wrapper는 secret JSON을 출력하지 않으며 종료 시 관련 환경변수를 제거한다.

```bash
sudo bash scripts/aws/bootstrap-rds-from-secrets.sh \
  "$AWS_REGION" "$MASTER_SECRET_ARN" "$APP_SECRET_ARN" \
  "$DB_ENDPOINT" /path/to/scripts/aws/bootstrap-rds-app-user.sh
```

스크립트 완료 후 임시 보안 세션을 종료하고 CloudTrail에서 master secret 접근을 확인한다.

## 5. 공급자 Secret 등록

기존 로컬 키를 채팅, 명령 기록, 스크린샷에 붙이지 않는다. 보안 터미널의 환경변수로만 주입한다.

```bash
export GEMINI_API_KEY='...'
export KIS_APP_KEY='...'
export KIS_APP_SECRET='...'
export KIS_HTS_ID='...'
export NAVER_API_HUB_CLIENT_ID='...'
export NAVER_API_HUB_CLIENT_SECRET='...'
export FINNHUB_API_KEY='...'
export OPENDART_API_KEY='...'
export ARTICLE_USER_AGENT='FinWatch/1.0 owner@example.com'
export SEC_EDGAR_USER_AGENT='FinWatch/1.0 owner@example.com'
export KAKAO_REST_API_KEY='...'
export KAKAO_CLIENT_SECRET='...'
bash scripts/aws/put-provider-secrets.sh finwatch portfolio "$AWS_REGION"
unset GEMINI_API_KEY KIS_APP_KEY KIS_APP_SECRET KIS_HTS_ID
unset NAVER_API_HUB_CLIENT_ID NAVER_API_HUB_CLIENT_SECRET FINNHUB_API_KEY OPENDART_API_KEY
unset ARTICLE_USER_AGENT SEC_EDGAR_USER_AGENT KAKAO_REST_API_KEY KAKAO_CLIENT_SECRET
```

Kakao를 사용하지 않으면 두 Kakao 변수 모두 비워 둔다. 다만 공개 smoke의 Kakao 항목은 실패하므로 대회 공개 환경에서는 운영 Redirect URI 등록까지 완료해야 한다.

## 6. DNS와 최초 TLS

Route 53을 쓰지 않으면 외부 DNS의 A 레코드를 스택의 Elastic IP로 먼저 연결한다. DuckDNS에서는 도메인의 `current ip`를 Elastic IP로 바꾸고 `update ip`를 누른다. 토큰은 배포 서버나 저장소에 넣지 않는다. DNS 전파 후 SSM Session Manager에서 EC2에 접속해 실행한다.

```bash
export CERTBOT_IMAGE='certbot/certbot:vREPLACE_WITH_PINNED_VERSION'
sudo -E bash /path/to/scripts/aws/bootstrap-tls.sh finwatch-hyphoenix.duckdns.org owner@example.com
```

인증서 파일을 확인한 뒤에만 첫 애플리케이션 배포를 시작한다. 배포 스크립트가 갱신 timer를 설치하며, timer와 만료일을 운영자가 별도로 확인한다.

## 7. GitHub OIDC 역할

AWS 계정에 GitHub OIDC provider가 없을 때만 `token.actions.githubusercontent.com` provider를 한 번 생성한다. trust 대상은 `repo:gimseyun690-jpg/FinWatch:environment:portfolio`로 제한한다.

```bash
aws cloudformation deploy \
  --region "$AWS_REGION" \
  --stack-name finwatch-github-oidc-role \
  --template-file infra/aws/github-oidc-role.yml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    GitHubOidcProviderArn="arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com" \
    DeploymentBucketName="STACK_OUTPUT_BUCKET"
```

GitHub의 `portfolio` Environment에 reviewer를 지정하고 다음 **Environment variables**를 설정한다. 운영 Secret 값은 GitHub에 넣지 않는다.

```text
AWS_ACCOUNT_ID
AWS_REGION
AWS_DEPLOY_ROLE_ARN
ECR_BACKEND_REPOSITORY=finwatch-backend
ECR_FRONTEND_REPOSITORY=finwatch-frontend
DEPLOYMENT_BUCKET
EC2_INSTANCE_ID
RDS_INSTANCE_ID
REDIS_IMAGE
PUBLIC_BASE_URL=https://finwatch.example.com
```

## 8. 배포

1. 대상 commit의 CI가 전부 통과했는지 확인한다.
2. GitHub Actions `Deploy portfolio to AWS`를 수동 실행한다.
3. `release_ref`에 검증된 commit SHA를 입력한다.
4. Environment reviewer가 비용·스냅샷·변경 범위를 확인하고 승인한다.
5. workflow가 SHA 태그 이미지, ECR scan, RDS snapshot, SSM 배포, 외부 smoke를 완료하는지 확인한다.

SSM은 `SsmDeployTarget=true` 태그가 붙은 인스턴스만 대상으로 한다. 배포 스크립트는 Secret 값을 출력하지 않고 `/run/finwatch/portfolio.env`에 mode 0600으로 원자적으로 기록한다.

## 9. 인수·증거

```bash
bash scripts/aws/aws-smoke.sh https://finwatch.example.com
```

다음 증거를 Secret 없이 `docs/evidence/aws/RELEASE_SHA/` 또는 제출 기록에 보관한다.

- CloudFormation stack ID와 Outputs
- commit SHA, backend/frontend image digest
- ECR critical finding 0 결과
- RDS 사전 snapshot ID
- SSM command ID와 성공 상태
- smoke 결과 시각
- CloudWatch alarm·SNS 구독 확인 화면
- AWS Budget 알림 설정
- Kakao 운영 Redirect URI 일치 확인
- Security Group inbound 규칙(80/443만 공개)

## 10. 장애와 롤백

새 컨테이너 readiness가 실패하면 배포 스크립트가 이전 release Compose로 앱만 되돌린다. 수동 앱 롤백은 다음과 같다.

```bash
sudo bash /opt/finwatch/current/scripts/aws/rollback-portfolio.sh PREVIOUS_RELEASE_SHA
```

DB 스키마는 이미 적용될 수 있으므로 app rollback과 DB restore를 같은 작업으로 취급하지 않는다. 비호환·파괴 migration은 만들지 않고 expand → app deploy → contract를 여러 release로 나눈다. 데이터 복원이 필요한 경우 새 RDS 인스턴스로 snapshot을 restore하고 검증한 뒤 endpoint를 전환한다.

복구 훈련 최소 절차:

1. 최신 자동 snapshot으로 별도 RDS를 restore한다.
2. private network에서 row count와 Flyway version을 확인한다.
3. 임시 app user와 readiness로 읽기 검증한다.
4. 목표 RPO/RTO를 기록한다.
5. 훈련 리소스를 승인 후 삭제한다.

## 11. 공개 종료와 비용 정리

공개 종료일에 다음 순서로 진행한다.

1. 최종 DB snapshot과 deployment evidence를 보존한다.
2. DNS에서 public A record를 내린다.
3. EC2를 중지하거나 stack 정책에 따라 제거한다.
4. RDS deletion protection을 명시적으로 해제한 뒤 최종 snapshot을 확인하고 제거한다.
5. EIP, EBS snapshot, RDS snapshot, ECR, S3 version, CloudWatch log, Route 53, Secrets Manager 잔존 비용을 확인한다.
6. 공급자 key와 Kakao client secret을 교체하거나 폐기한다.
7. Budget와 Cost Explorer에서 잔존 일별 비용이 없는지 최소 48시간 확인한다.

## 12. 현재 미완료 항목

- AWS 계정·리전·도메인·예산·담당자가 확정되지 않았다.
- 실제 CloudFormation stack, DNS, TLS, RDS, EC2, ECR은 생성하지 않았다.
- 실제 공개 URL smoke, CloudWatch alarm test, RDS restore drill은 수행하지 않았다.
- 따라서 현재 상태를 “AWS 배포 완료”라고 표시하면 안 된다. 현재 완료 상태는 “재현 가능한 배포 코드와 런북 준비”다.
