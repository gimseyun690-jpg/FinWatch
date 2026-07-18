# FinWatch 카카오 로그인 명세

상태: Implementation v0.2 — Phase A·B 코드와 자동 검증 완료, Kakao Console 설정·실계정 smoke 대기

기준일: 2026-07-17

대상: Kakao Login(OAuth 2.0/OIDC), Spring Boot 인증, React PWA 세션

구현 기록(2026-07-17): V19 사용자·identity migration, Redis OAuth attempt·불투명 웹 세션, Secure 설정 가능한 HttpOnly cookie, SPA 원본 헤더와 BREACH 보호를 함께 처리하는 쿠키 인증 CSRF, 비밀번호 로그인 세션 이관, session·logout·account delete API, Kakao authorize/callback, state·nonce·PKCE S256, RS256/JWKS·issuer·audience·시간·nonce·sub 검증, USER provisioning, 이메일 자동 병합 차단과 프런트 session bootstrap·카카오 버튼·개인정보 안내를 구현했다. 로컬 개발은 HTTP이므로 `SESSION_COOKIE_SECURE=false`, 운영은 반드시 `true`다. Kakao Developers의 앱 활성화·redirect 등록과 실제 HTTPS 계정 smoke는 외부 설정 전이므로 미완료 상태다. PostgreSQL V19 적용과 실제 브라우저 비밀번호 로그인·로그아웃, 쿠키/CSRF 스모크까지 로컬 LIVE 환경에서 검증했다.

## 1. 목적

이 문서는 FinWatch에 카카오계정 로그인을 추가하는 인증·계정·세션 계약을 정의한다. 사용자는 별도 비밀번호를 만들지 않고 로그인할 수 있으며 기존 USER/ADMIN 권한, 관심종목, 포트폴리오와 알림의 사용자 격리를 그대로 유지해야 한다.

핵심 목표:

1. Authorization Code + OpenID Connect 표준 흐름을 사용한다.
2. 카카오 토큰을 브라우저, URL, 로그 또는 DB에 불필요하게 남기지 않는다.
3. Kakao OIDC `sub`를 안정적인 외부 사용자 식별자로 사용한다.
4. 공개 브라우저 인증을 `localStorage` JWT에서 HttpOnly 세션으로 전환한다.
5. 비밀번호 로그인과 카카오 로그인이 동일한 FinWatch 사용자·권한 계약을 사용한다.
6. 취소·재시도·중복 callback·공급자 장애를 안전하게 처리한다.

AWS 배포 구조와 Secret 공급은 `20_AWS_DEPLOYMENT_SPEC.md`에서 정의한다.

## 2. 인증 이관 현황

### 2.1 구현 전 기준선

- 로그인: `POST /api/v1/auth/login`
- 사용자: `users.email`, `password_hash`, `role`
- 인증: FinWatch HS256 Access Token, 기본 TTL 1시간
- 브라우저: `localStorage`의 `finwatch.auth.session`
- API: `Authorization: Bearer`
- 권한: `USER`, `ADMIN`
- Spring Security: Resource Server
- 세션·OAuth client: 구현 전

### 2.2 현재 구현

- 브라우저 인증: `FW_SESSION` HttpOnly cookie와 Redis 불투명 세션
- 변경 API 보호: `XSRF-TOKEN` cookie와 `X-XSRF-TOKEN` header
- 세션 복원·폐기: `GET /api/v1/auth/session`, `POST /api/v1/auth/logout`
- 카카오 OIDC: authorize/callback, state·nonce·PKCE, ID Token 검증
- 브라우저 저장소: 영속 Access Token 제거 완료
- 기존 HS256 Bearer: 자동화·이행 호환용으로만 유지, 공개 프런트에서는 미사용

## 3. 범위와 제외 범위

### 3.1 포함

- Kakao Developers 앱과 동의항목 설정
- Authorization Code + OIDC
- `state`, `nonce`, PKCE S256
- ID Token 서명·claim 검증
- Kakao identity와 FinWatch user 매핑
- 최초 로그인 USER 자동 생성
- HttpOnly cookie와 Redis session
- CSRF, redirect allow-list, rate limit
- 로그인·현재 세션·로그아웃·계정 삭제 API
- 비밀번호 로그인과 카카오 로그인 공존
- 프런트 로그인·세션 복원·오류 화면
- stub 기반 자동 테스트와 실제 Kakao smoke

### 3.2 제외

- 카카오톡 메시지·친구 목록·채널
- 카카오페이
- 카카오 정보를 이용한 투자 개인화
- 이메일만으로 소셜 계정 자동 병합
- 실제 금융 계좌 연결
- AWS 네트워크·Docker·CI/CD 상세

## 4. 확정 선택

| 항목 | 선택 |
|---|---|
| 프로토콜 | OAuth 2.0 Authorization Code + OIDC |
| 사용자 식별 | Kakao ID Token의 `sub` |
| 브라우저 인증 | 불투명 Secure HttpOnly cookie |
| 세션 저장 | Redis |
| 신규 권한 | 항상 `USER` |
| Kakao token | 로그인 완료 후 즉시 폐기 |
| 기본 화면 전환 | popup이 아닌 full-page redirect |
| 이메일 | 로그인 필수 정보로 사용하지 않음 |

외부 claim이나 이메일만으로 ADMIN을 부여하거나 기존 계정을 자동 연결하지 않는다.

## 5. Kakao Developers 설정

### 5.1 필수 설정

1. FinWatch 앱을 생성한다.
2. 카카오 로그인을 활성화한다.
3. REST API 키의 Client secret을 활성화한다.
4. OpenID Connect를 활성화한다.
5. 서비스 이름, 로고, 개인정보 처리방침과 이용약관 URL을 등록한다.
6. 환경별 redirect URI를 정확히 등록한다.

```text
local     http://localhost:8080/api/v1/auth/kakao/callback
staging   https://staging.finwatch.example/api/v1/auth/kakao/callback
portfolio https://finwatch.example/api/v1/auth/kakao/callback
```

- 등록 URI와 인가·토큰 요청의 `redirect_uri`는 완전히 일치해야 한다.
- 운영에서는 HTTPS만 허용한다.
- wildcard와 사용자 입력 redirect URI를 금지한다.
- callback은 React route가 아니라 Spring Boot endpoint다.

### 5.2 동의항목 최소화

| 정보 | 정책 |
|---|---|
| `sub` | 필수 외부 식별자 |
| nickname | 선택, 없으면 기본 이름 |
| picture | 선택, 없으면 기본 avatar |
| email | 기본 미요청, identity 연결에 사용하지 않음 |

수집 목적이 없는 성별, 연령대, 생일, 전화번호를 요청하지 않는다. 선택 정보에 동의하지 않아도 로그인이 가능해야 한다.

### 5.3 로그인 버튼

- Kakao 공식 디자인 가이드를 준수한다.
- 접근 가능한 이름으로 `카카오로 시작하기`를 제공한다.
- 로딩, 취소와 실패 feedback을 제공한다.
- 데모 로그인은 `데모로 둘러보기`로 시각적으로 분리한다.

## 6. 로그인 시퀀스

```text
브라우저
  -> GET /api/v1/auth/kakao/authorize?returnTo=/dashboard
FinWatch API
  -> state, nonce, code_verifier 생성
  -> Redis OAuth attempt 저장(TTL 5분)
  -> HttpOnly correlation cookie 설정
  -> Kakao authorize endpoint로 302
Kakao
  -> 사용자 인증·동의
  -> GET /api/v1/auth/kakao/callback?code=...&state=...
FinWatch API
  -> correlation/state/TTL/1회 사용 검증
  -> code + PKCE verifier로 token endpoint 호출
  -> ID Token 서명과 claim 검증
  -> sub로 identity 조회 또는 USER 생성
  -> Kakao token 폐기
  -> Redis FinWatch session 생성
  -> Secure HttpOnly SameSite=Lax cookie 설정
  -> 검증된 returnTo로 303
브라우저
  -> GET /api/v1/auth/session
  -> 사용자 상태 복원
```

## 7. OAuth 보안 계약

### 7.1 필수 값

- `state`: CSPRNG, 요청별 고유, CSRF 방지, 한 번만 사용
- `nonce`: ID Token replay 방지
- `code_verifier`: PKCE S256, 서버에만 저장
- correlation cookie: 요청 브라우저와 OAuth attempt 연결
- `returnTo`: 내부 route allow-list

권장 TTL:

| 항목 | TTL |
|---|---:|
| OAuth attempt | 5분 |
| correlation cookie | 5분 |
| session idle | 1시간 |
| session absolute | 8시간 |

원문 state, nonce, verifier와 session ID를 Redis key·로그에 직접 사용하지 않는다.

### 7.2 ID Token 검증

- 알고리즘 `RS256`
- `iss == https://kauth.kakao.com`
- `aud == KAKAO_REST_API_KEY`
- `exp` 유효
- `iat` 허용 clock skew 이내
- `nonce` 일치
- `sub` 존재
- JWKS의 `kid`와 signature 유효

Kakao Discovery와 JWKS를 cache하고 key rotation을 지원한다. payload decode만 하고 signature 검증을 생략해서는 안 된다.

### 7.3 실패 처리

| 상황 | 결과 |
|---|---|
| 사용자 취소 | 로그인 화면 복귀와 안내 |
| state·correlation 불일치 | 401, 세션 생성 금지 |
| nonce 불일치 | 401, 보안 로그 |
| code 재사용 | 거부 |
| token/JWKS timeout | 기존 로그인 유지, 재시도 안내 |
| `sub` 누락 | 사용자 생성 금지 |
| 계정 `DISABLED` | 403 |
| Redis 장애 | fail closed |

code, state, nonce, verifier, cookie와 Kakao token은 로그에 기록하지 않는다.

## 8. 사용자·Identity 스키마

### 8.1 `users`

| 컬럼 | 목표 변경 |
|---|---|
| `email` | nullable, 로컬 계정 식별자 |
| `password_hash` | nullable, 소셜 전용은 null |
| `display_name` | NOT NULL |
| `profile_image_url` | nullable |
| `status` | `ACTIVE`, `DISABLED`, `DELETED` |
| `last_login_at` | nullable |
| `updated_at` | 변경 시 갱신 |

기존 로컬·데모 사용자는 email과 password hash를 유지한다. 사용자 UI는 nullable email 대신 `displayName`을 기본 label로 사용한다.

### 8.2 `auth_identities`

```text
id                 BIGINT PK
user_id            BIGINT FK users(id)
provider           VARCHAR(20) NOT NULL
provider_subject   VARCHAR(255) NOT NULL
provider_email     VARCHAR(255) NULL
created_at         TIMESTAMPTZ NOT NULL
updated_at         TIMESTAMPTZ NOT NULL
last_login_at      TIMESTAMPTZ NULL

UNIQUE(provider, provider_subject)
INDEX(user_id)
```

규칙:

1. Kakao identity는 `(KAKAO, sub)`로 조회한다.
2. 사용자와 identity는 하나의 DB transaction에서 생성한다.
3. 동시 최초 로그인은 unique constraint 후 기존 identity를 재조회한다.
4. 이메일이 같아도 자동 병합하지 않는다.
5. 신규 role은 `USER`다.
6. Kakao token은 저장하지 않는다.

## 9. Redis 모델

```text
auth:oauth:kakao:{attemptHash}
  stateHash, nonceHash, protectedCodeVerifier, returnTo, createdAt

auth:session:{sessionHash}
  userId, role, authProvider, createdAt, lastSeenAt, absoluteExpiresAt
```

- raw session ID는 cookie에만 전달한다.
- 로그인 성공 시 correlation 값을 폐기하고 session ID를 회전한다.
- 계정 정지·권한 변경 시 사용자의 모든 session을 폐기할 수 있어야 한다.
- DB 성공 후 Redis session 생성이 실패하면 로그인 성공으로 응답하지 않는다.

## 10. Cookie와 CSRF

### 10.1 Session cookie

```text
Name: FW_SESSION
HttpOnly: true
Secure: true
SameSite: Lax
Path: /
Domain: host-only
```

- 운영에서 Access Token을 query, fragment, JavaScript 또는 localStorage로 전달하지 않는다.
- broad parent domain cookie를 사용하지 않는다.
- 로그인 성공과 권한 변경 때 session fixation 방지를 위해 ID를 회전한다.

### 10.2 CSRF

쿠키 인증으로 전환할 때 현재의 전역 CSRF disable을 제거한다.

- POST, PUT, PATCH, DELETE에 CSRF token을 검증한다.
- `XSRF-TOKEN` cookie와 `X-XSRF-TOKEN` header 패턴을 사용할 수 있다.
- OAuth callback은 state·nonce·correlation으로 보호하며 예외 범위를 최소화한다.
- CORS wildcard를 금지하고 동일 Origin을 기본으로 한다.
- WebSocket handshake에서 Origin과 session을 검증한다.

## 11. 인증 API

| Method | Path | 보호 | 설명 |
|---|---|---|---|
| GET | `/api/v1/auth/kakao/authorize` | 공개 | attempt 생성, Kakao redirect |
| GET | `/api/v1/auth/kakao/callback` | correlation | code 교환, OIDC 검증, session 생성 |
| GET | `/api/v1/auth/session` | cookie | 현재 사용자 조회 |
| POST | `/api/v1/auth/logout` | cookie+CSRF | session 폐기 |
| POST | `/api/v1/auth/login` | 공개 | 기존 비밀번호 로그인, cookie 발급 |
| DELETE | `/api/v1/account` | cookie+CSRF+재인증 | 계정 삭제·익명화 |

### 11.1 returnTo

내부 USER route와 검증된 query만 허용한다. 외부 URL, protocol-relative URL, backslash, control character와 권한 없는 ADMIN route를 거부한다.

### 11.2 Session 응답

```json
{
  "data": {
    "authenticated": true,
    "expiresAt": "2026-07-17T12:00:00Z",
    "user": {
      "id": 1,
      "displayName": "FinWatch 사용자",
      "email": null,
      "profileImageUrl": null,
      "role": "USER",
      "authProvider": "KAKAO"
    }
  }
}
```

cookie와 provider token은 response body에 포함하지 않는다.

## 12. 로그아웃·계정 삭제

로그아웃:

1. Redis session 삭제
2. `FW_SESSION` 만료
3. 프런트 사용자 상태 초기화
4. Kakao token을 보존하지 않으므로 Kakao API logout 미호출

서비스 로그아웃, Kakao 앱 연결 해제와 계정 삭제는 서로 다른 기능이다.

계정 삭제:

- 관심종목, 포트폴리오, 알림과 identity의 삭제·익명화 순서를 정의한다.
- AI 운영 로그 보존이 필요하면 사용자 직접 식별자를 분리한다.
- 삭제 후 동일 Kakao `sub` 재로그인 정책을 명시한다.
- 개인정보 처리방침에 수집 항목, 목적, 보존 기간과 삭제 SLA를 공개한다.

## 13. 프런트엔드

### 13.1 로그인

- `카카오로 시작하기` 버튼으로 full-page redirect한다.
- 현재 내부 route를 안전한 `returnTo`로 전달한다.
- popup 방식은 기본으로 사용하지 않는다.
- 취소·오류 code를 사용자 문구로 변환한다.
- 비밀번호 데모 로그인과 카카오 로그인을 구분한다.

### 13.2 Session bootstrap

- 앱 시작 시 `GET /api/v1/auth/session`을 호출한다.
- 기존 localStorage Access Token을 제거한다.
- 401은 로그인 이동, 403은 권한 없음으로 구분한다.
- 새로고침·PWA 재실행에서 session을 복원한다.
- 이메일이 없어도 displayName·기본 avatar로 UI가 동작한다.

## 14. 백엔드 구현 제안

의존성:

- `spring-boot-starter-oauth2-client`
- issuer/discovery 기반 Kakao client registration
- timeout과 response size 제한이 있는 HTTP client

패키지:

```text
com.finwatch.auth.kakao
  KakaoAuthorizationController
  KakaoCallbackController
  KakaoOidcClient
  KakaoIdTokenValidator
  OAuthAttemptService

com.finwatch.auth.session
  WebSessionService
  SessionCookieWriter
  SessionAuthenticationFilter

com.finwatch.user.identity
  AuthIdentity
  AuthIdentityRepository
  SocialUserProvisioningService
```

기존 JWT는 local/test 호환 기간에만 유지하고 공개 브라우저 경로에서는 사용하지 않는다.

## 15. 설정 계약

```text
KAKAO_LOGIN_ENABLED=true
KAKAO_REST_API_KEY=
KAKAO_CLIENT_SECRET=
KAKAO_ISSUER=https://kauth.kakao.com
KAKAO_REDIRECT_URI=https://finwatch.example/api/v1/auth/kakao/callback
KAKAO_OAUTH_ATTEMPT_TTL=5m

SESSION_COOKIE_NAME=FW_SESSION
SESSION_IDLE_TTL=1h
SESSION_ABSOLUTE_TTL=8h
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=Lax
SESSION_COOKIE_DOMAIN=
```

- Client secret과 REST API key를 `VITE_*`에 넣지 않는다.
- Secret은 서버 런타임에만 주입한다.
- 운영 Secret이 없으면 fail fast한다.
- AWS 저장 위치와 IAM은 `20_AWS_DEPLOYMENT_SPEC.md`를 따른다.

## 16. 테스트

### 16.1 단위

- state·nonce·verifier 난수와 hash
- PKCE S256
- issuer, audience, expiry, nonce, signature
- allow-list returnTo
- nullable email·nickname·picture
- USER 기본 role과 ADMIN 차단
- cookie attribute와 session TTL

### 16.2 통합

- authorize redirect parameter
- 정상 callback 사용자 생성·재로그인
- 동시 최초 로그인 중복 방지
- 취소, state/nonce mismatch, 만료, code replay
- token/JWKS timeout·4xx·5xx·key rotation
- Redis 장애 fail closed
- 비밀번호·Kakao 공통 session
- logout, CSRF, disabled account
- USER/ADMIN 권한 회귀
- PostgreSQL·Redis Testcontainers

CI에서는 Kakao endpoint를 stub하고 실제 Secret을 사용하지 않는다.

### 16.3 E2E·수동 smoke

- 카카오 버튼 접근성
- authorize 이동과 취소 복귀
- session bootstrap 상태
- 직접 URL returnTo
- 401/403 분리
- logout 후 보호 route 차단
- localStorage에 token 없음
- desktop·390px·standalone PWA
- 실제 HTTPS 환경에서 authorize/callback 성공

## 17. 구현 단계

### Phase A — 계정 기반

1. Kakao 앱·OIDC·redirect 설정
2. users/auth_identities migration
3. Redis OAuth attempt·session
4. authorize/callback·ID Token 검증
5. cookie·CSRF·logout·session API

### Phase B — 기존 인증 이관

1. 비밀번호 로그인을 cookie session으로 이관
2. localStorage Access Token 제거
3. USER/ADMIN·WebSocket 회귀
4. 로그인 UI·returnTo·오류 처리

### Phase C — 공개 검증

1. 개인정보 처리방침·계정 삭제
2. staging/portfolio redirect 등록
3. 실제 Kakao smoke
4. 보안·접근성·PWA 인수

## 18. 인수 조건

- [ ] Kakao Login, Client secret과 OIDC가 활성화된다. — Kakao Console 작업 대기
- [ ] redirect URI가 환경별로 정확히 등록된다. — Kakao Console 작업 대기
- [x] state, nonce, PKCE S256이 적용된다.
- [x] OAuth attempt가 짧은 TTL과 1회 사용을 보장한다.
- [x] ID Token의 RS256, iss, aud, exp, iat, nonce, sub를 검증한다.
- [x] 이메일 없이도 `sub`로 USER를 생성·재로그인한다.
- [x] 이메일만으로 기존 계정을 자동 병합하지 않는다.
- [x] Kakao 신규 사용자는 ADMIN이 될 수 없다.
- [x] Kakao token이 브라우저·URL·DB·로그에 남지 않는다.
- [x] Secure 설정 가능한 HttpOnly SameSite cookie와 Redis session을 사용한다.
- [x] state-changing API에 CSRF 보호가 적용된다.
- [x] localStorage에 Access Token이 남지 않는다.
- [x] 비밀번호·Kakao 로그인이 동일 권한 계약을 사용한다.
- [x] logout이 서버 session을 폐기한다.
- [ ] 취소·만료·재사용·공급자 장애 상태가 검증된다.
- [ ] desktop·390px·PWA 로그인 흐름이 통과한다.
- [x] 개인정보 처리 안내와 재인증 기반 계정 삭제·익명화 정책이 준비된다.

미완료 항목이 남아 있으면 “카카오 로그인 완료”로 표시하지 않는다.

## 19. 공식 참고 문서

- [Kakao Login REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api)
- [Kakao Login 설정하기](https://developers.kakao.com/docs/ko/kakaologin/prerequisite)
- [Kakao Login 활용하기·OIDC](https://developers.kakao.com/docs/ko/kakaologin/utilize)

구현 시작과 공개 직전에 endpoint, 필수 파라미터와 console 설정을 다시 확인한다.

## 20. 관련 문서

- `03_API_SPEC.md`
- `04_DB_SCHEMA.md`
- `07_USER_FEATURE_SPEC.md`
- `09_SECURITY_DEPLOYMENT_SPEC.md`
- `10_TEST_ACCEPTANCE_SPEC.md`
- `17_NAVIGATION_AND_CONTENT_LIST_SPEC.md`
- `18_UI_POLISH_AND_PRODUCT_DETAIL_SPEC.md`
- `20_AWS_DEPLOYMENT_SPEC.md`
