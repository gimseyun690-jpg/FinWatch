# FinWatch 아키텍처

## 1. 설계 원칙

- 단기 2인 이하 프로젝트에 맞춰 모듈형 모놀리스로 시작한다.
- 외부 주가·뉴스·AI 공급자는 어댑터로 격리해 교체 가능하게 한다.
- AI 비용 최적화 흐름은 하나의 AI Gateway 서비스에서 통제한다.
- 개발 환경은 Docker Compose, 운영 환경은 AWS 관리형 서비스를 우선한다.

## 2. 논리 구조

```text
React TypeScript PWA
        |
        | HTTPS / REST
        v
Spring Boot API (modular monolith)
  |- Auth / User
  |- Stock / Market Data
  |- Watchlist / Portfolio / Alert
  |- Technical Analysis
  |- News
  |- AI Gateway
  |- Admin Metrics
  |
  |- PostgreSQL
  |- Redis
  |- Market/News Provider
  `- AI Provider
```

## 3. AWS 배포 구조

```text
Internet
   |
   v
EC2 + Nginx
  |- React 정적 파일
  `- Spring Boot API
        |- RDS PostgreSQL
        |- ElastiCache Redis (운영 권장)
        |- S3 (정적 자산/리포트, 필요 시)
        `- AI 및 시장 데이터 외부 API

CloudWatch <- EC2/API 로그와 기본 인프라 메트릭
```

운영 배포를 진행할 경우 비용과 구현 난도를 낮추기 위해 EC2 한 대에 Nginx와 Spring Boot를 배포한다. 개발 단계 Redis는 Docker로 실행할 수 있다. 운영 캐시는 예산과 가용성 요구에 따라 ElastiCache 또는 단일 서버 Redis 중에서 선택한다. AWS 배포 자체는 현재 구현 범위에서 제외한다.

## 4. 백엔드 모듈

```text
com.finwatch
|- auth
|- user
|- stock
|- watchlist
|- portfolio
|- alert
|- news
|- technical
|- ai
|- admin
|- common
`- config
```

각 모듈은 `controller`, `service`, `repository`, `domain`, `dto` 역할을 분리한다. Entity를 API 응답으로 직접 노출하지 않는다.

## 5. AI 뉴스 요약 흐름

```text
요약 요청
  -> 뉴스와 프롬프트 버전 확인
  -> cacheKey 생성
  -> Redis 조회
     |- HIT: 캐시 결과 반환 + cache-hit 로그 저장
     `- MISS
         -> 원문 전처리
         -> AI 공급자 호출
         -> 토큰/비용 계산
         -> 결과 DB 저장
         -> Redis TTL 저장
         -> usage 로그 저장
         -> 결과 반환
```

권장 캐시 키:

```text
ai:news-summary:{newsId}:{contentHash}:{promptVersion}
```

DB의 `ai_analyses`는 감사와 캐시 복구를 위한 영속 결과이며 Redis는 빠른 반환을 위한 만료 가능한 사본이다.

## 6. 기술적 분석 흐름

1. 가격 공급자에서 일봉 데이터를 수집하거나 DB에서 조회한다.
2. 이동평균선, RSI(기본 14), MACD(기본 12/26/9)를 계산한다.
3. 지표별 규칙으로 BUY, NEUTRAL, SELL 신호를 만든다.
4. 종합 신호와 계산 기준 시각을 응답한다.

신호 규칙은 코드에 흩어놓지 않고 설정 또는 하나의 정책 클래스로 관리한다. 화면에는 투자 권유가 아니라 참고 지표임을 표시한다.

계산식, 초기값, 신호 경계, 데이터 품질과 Lightweight Charts 기반 상세 차트 규칙은 `11_TECHNICAL_ANALYSIS_SPEC.md`를 따른다.

계획된 AI 기술지표 해설은 결정론적 계산 뒤의 선택 기능으로 둔다. Gemini 장애가 차트와 기술지표 조회를 막아서는 안 된다.

```text
서버 계산 기술지표
  -> 정규화 스냅샷·근거 I1..In·inputHash
  -> Redis/DB 조회
     |- HIT: 기존 해설과 절감 로그 반환
     `- MISS: Gemini 구조화 설명
              -> 근거 ID·금지 출력 검증
              -> ai_technical_explanations + Redis + usage 로그
```

Gemini는 추세·모멘텀·변동성·거래량과 충돌 신호를 설명할 뿐 지표·종합 신호를 다시 계산하거나 목표주가와 매매 명령을 만들지 않는다. 상세 계약은 `12_AI_TECHNICAL_EXPLANATION_SPEC.md`를 따른다.

구현된 일일 변화 브리핑은 이전·현재 기술 스냅샷의 서버 계산 delta와 기존 뉴스·공시 분석을 재사용한다. 원문을 중복 전송하지 않고 기술·뉴스·공시 관점과 근거를 분리하며 상세 계약은 `13_AI_DAILY_CHANGE_BRIEFING_SPEC.md`를 따른다.

구현된 종목 탐색 기능은 KRX·미국 전체 종목의 가벼운 마스터를 PostgreSQL에 동기화해 로컬 검색하고, 선택·관심·보유 종목만 시세·일봉·뉴스와 WebSocket 구독 대상으로 승격한다. 검색과 온디맨드 수집의 상세 계약은 `14_STOCK_DISCOVERY_SPEC.md`를 따른다.

구현된 환율 기능은 Finnhub Forex를 우선 사용하고 계정 권한으로 거절되면 Frankfurter 일일 기준환율을 명시적인 `REFERENCE` fallback으로 사용한다. 검증된 USD/KRW 스냅샷은 DB·Redis에 저장하고, 주식 원통화 값과 환산값을 분리하며 환율 장애가 원통화 조회를 막지 않는 계약은 `15_FX_RATE_SPEC.md`를 따른다.

## 7. 데이터 공급자 경계

시장 데이터와 뉴스 공급자는 확정했지만 외부 계약 변경과 테스트 대역을 고려해 다음 인터페이스 뒤에 구현을 둔다.

- `MarketDataProvider`: 종목, 현재가, 가격 이력
- `NewsProvider`: 종목 관련 뉴스와 원문
- `AiProvider`: 뉴스 분석과 기술지표 해설의 구조화 결과·토큰 사용량

확정 조합은 KIS(국내 일봉·현재가·체결), Finnhub(미국 현재가·체결·뉴스 발견), NAVER API HUB(국내 뉴스 발견), Open DART·SEC EDGAR·기업 공식 출처(분석 가능한 전문), Gemini(AI 분석)다. 실시간 틱은 KIS/Finnhub 어댑터에서 인메모리 최신 시세 허브로 들어오고 `/ws/quotes`로 브라우저에 fan-out한다. 실제 키 없이도 고정 데모 데이터로 전체 시연 흐름을 유지한다. 수집·정규화·신선도·저작권·장애 처리 규칙은 `06_DATA_PROVIDER_SPEC.md`, AI 호출과 캐시 운영 규칙은 `08_AI_OPERATION_SPEC.md`를 따른다.

## 8. 환경 구성

### local

- React 개발 서버
- Spring Boot
- Docker PostgreSQL
- Docker Redis
- Mock 또는 Sandbox 외부 API

### production

- Nginx + React build + Spring Boot on EC2
- RDS PostgreSQL
- ElastiCache Redis 또는 비용 절감형 EC2 Redis
- CloudWatch Agent / 애플리케이션 로그

환경별 설정은 프로필과 환경변수로 분리하며 비밀정보는 Git에 저장하지 않는다.

## 9. 주요 기술 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| Backend | Spring Boot 모듈형 모놀리스 | 구현·배포·디버깅 단순화 |
| Database | PostgreSQL | 관계형 데이터 무결성, 집계, 마이그레이션 관리에 적합 |
| Cache | Redis | 반복 AI 요청 제거와 TTL 지원 |
| Frontend | React + TypeScript + Vite PWA | 타입 안전성과 빠른 개발, 설치형 웹 경험 제공 |
| Detail Chart | Lightweight Charts | 캔들·시간축·십자선을 제공하고 Primitive로 그리기 도구 확장 |
| API | REST + JSON | 프론트·백엔드 병렬 개발 용이 |
| Time | DB UTC, API ISO-8601 | 배포 지역과 무관한 일관성 |

## 10. 추후 결정 항목

- 확정 공급자별 키 발급·요금제·호출 한도·공개 표시 권한 검증
- 운영 Gemini 모델과 적용 단가의 변경 절차
- JWT 갱신·폐기 정책과 운영 계정 정책
- ElastiCache 사용 여부와 캐시 TTL
