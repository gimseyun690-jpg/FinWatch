# FinWatch AI 운영 상세 명세

상태: v0.2
기준일: 2026-07-14
범위: NEWS_SUMMARY·TECHNICAL_EXPLANATION·DAILY_CHANGE_BRIEFING의 입력, 프롬프트, AI 공급자, 결과 검증, 캐시, 사용량·비용, 오류·안전

## 1. 목적과 현재 상태

이 문서는 01_REQUIREMENTS.md의 U-08·U-10과 A-01부터 A-06, 02_ARCHITECTURE.md의 AI 흐름, 03_API_SPEC.md의 AI·관리자 API를 실제 구현 가능한 운영 계약으로 구체화한다.

| 영역 | 현재 상태 | 비고 |
|---|---|---|
| 뉴스 조회와 본문 전처리 | 구현 | HTML·URL·광고 문구·정확히 중복된 문장 제거, 6,000자 제한 |
| Gemini·Mock 공급자 선택 | 구현 | AI_PROVIDER 설정으로 하나의 구현 활성화 |
| 구조화 Gemini 요청 | 구현 | JSON MIME type과 responseSchema 사용 |
| 공급자 결과의 서버 측 유효성 검증 | 구현 | 기능별 길이·항목 수·enum·근거 ID·금지 문구·토큰 검증 |
| Redis·메모리 캐시 | 구현 | demo는 메모리, 그 외는 Redis |
| DB 분석 복구 | 구현 | Redis MISS여도 같은 뉴스·버전의 DB 결과 재사용 |
| 동시 MISS 단일 호출 보장 | 구현 | 프로세스 내 cacheKey single-flight, 다중 인스턴스 분산 락은 AWS 운영 범위 |
| 외부 호출 타임아웃·오류 매핑 | 구현 | 연결·읽기 timeout과 인증·429·공급자 오류 코드 매핑 |
| 성공·캐시 HIT 로그 | 구현 | 요청 ID는 로그 생성 시 UUID 발급 |
| 실패 로그·요청 ID | 구현 | 실패 status·errorCode·UUID requestId 저장, 사용자 FK는 향후 운영 범위 |
| 토큰·예상 비용·절감 비용 | 구현 | 환경변수 단가, USD, 소수 8자리 |
| 사용자 호출 제한·일일 비용 한도 | 구현 | 사용자 key별 분당 요청 수와 서비스 일일 USD 비용 차단, 환경변수 설정 |
| AI 기술지표 해설 | 구현 | 서버 계산 스냅샷·근거 ID 기반 설명 |
| AI 일일 변화 브리핑 | 구현 | 직전 거래일 대비 변화와 기술·뉴스·공시 관점 매트릭스 |

“현재 동작”은 코드 기준 사실이며, “필수 보강”은 운영 배포 전에 구현해야 할 계약이다.

## 2. 요청 계약과 처리 순서

### 2.1 API

POST /api/v1/ai/news-summaries는 인증된 USER와 ADMIN이 사용할 수 있다.

~~~json
{
  "newsId": 1042,
  "promptVersion": "news-analysis-v2"
}
~~~

유효성:

| 필드 | 규칙 | 현재 상태 |
|---|---|---|
| newsId | 필수이며 1 이상의 정수 | 필수 검증만 구현, 양수 검증 필요 |
| promptVersion | 생략·null·공백이면 서버 활성 버전 사용 | 구현 |
| promptVersion | 명시 시 trim, 최대 50자 | trim 구현, 길이 검증 필요 |

현재 구현은 명시된 임의의 비어 있지 않은 promptVersion을 그대로 허용한다. 이는 사용자가 버전을 계속 바꿔 캐시를 우회할 수 있으므로 운영 전에는 서버가 등록한 활성·호환 버전만 허용하고 그 외에는 400 AI_PROMPT_VERSION_UNSUPPORTED를 반환해야 한다.

### 2.2 표준 처리 순서

~~~text
인증과 요청 검증
  -> newsId로 뉴스 조회
  -> 사용할 프롬프트 버전 결정
  -> cacheKey 생성
  -> 빠른 캐시 조회
     -> HIT: 원본 DB 분석 확인 -> 사용 로그 -> 반환
  -> 같은 뉴스·기능·버전의 DB 분석 조회
     -> 존재: 빠른 캐시 복구 -> 사용 로그 -> 반환
  -> 뉴스 본문 전처리
  -> 본문 비어 있음 검증
  -> 동일 cacheKey 단일 호출 구간 진입 및 재확인
  -> AI 공급자 호출
  -> 구조화 결과 검증
  -> 토큰·비용 계산
  -> 분석 DB 저장
  -> 빠른 캐시 저장
  -> 성공 사용 로그 저장
  -> 반환
~~~

현재 구현에는 “단일 호출 구간 진입 및 재확인”과 “구조화 결과 검증” 단계가 없다.

## 3. 뉴스 입력 전처리

### 3.1 현재 알고리즘

NewsTextPreprocessor는 다음 순서로 처리한다.

1. content가 null 또는 공백이면 빈 문자열을 반환한다.
2. 정규식 <[^>]+>로 HTML 태그를 공백으로 치환한다.
3. http:// 또는 https://로 시작해 다음 공백 전까지인 URL을 제거한다.
4. 광고, ADVERTISEMENT, 무단 전재, 재배포 금지 문구부터 다음 문장부호까지를 대소문자 무시하고 제거한다.
5. 연속 공백과 줄바꿈을 하나의 공백으로 합치고 trim한다.
6. 마침표·느낌표·물음표 뒤 공백을 기준으로 문장을 나눈다.
7. 완전히 같은 문장 문자열은 첫 등장만 남기며 원래 순서를 보존한다.
8. 다시 합친 문자열이 6,000자를 넘으면 앞 6,000자에서 자르고 trim한다.

중복 판정은 대소문자, 문장부호와 내부 공백 정규화 이후 문자열이 정확히 같을 때만 적용된다. 의미가 비슷한 문장을 임의 제거하지 않는다.

### 3.2 입력 규칙

1. AI에는 서버가 확보한 `news_articles.content`만 전달한다. 저장된 본문이 없는 일반 뉴스는 요약 요청 시 서버가 원문 링크를 on-demand로 수집·검증·저장하며, 클라이언트가 임의 본문을 직접 보내지는 않는다.
2. 전처리 결과가 비면 공급자를 호출하지 않고 422 NEWS_CONTENT_UNAVAILABLE을 반환한다.
3. 제목은 현재 별도 전처리나 길이 제한 없이 공급자에 전달된다. 운영 전에는 제어문자 제거와 500자 상한을 적용해야 한다.
4. 현재 HTML 제거는 정규식 기반이라 script/style 내용과 HTML entity를 안전하게 해석하지 못한다. 실제 외부 뉴스 수집 전에는 검증된 HTML parser로 본문 텍스트를 추출해야 한다.
5. 6,000자 절단은 현재 문자 위치 기준이라 문장 중간에서 끝날 수 있다. 이 동작을 유지하되 프롬프트 버전 변경 없이 절단 정책을 바꾸지 않는다.
6. 전처리된 전체 본문과 원문은 애플리케이션 로그 또는 사용량 로그에 기록하지 않는다.

### 3.3 허용 출처 본문 분석

본문 수집은 등록된 출처 정책을 우선한다. 일반 뉴스의 저장 본문이 없으면 사용자가 누른 요약 요청에 한해 안전한 원문 링크를 on-demand로 수집한다. 명시적으로 `BLOCKED`된 출처는 이 경로에서도 우회하지 않는다.

| 입력 출처 | AI 사용 | 화면 표시 |
|---|---|---|
| `PROVIDER_SUMMARY` | 공급자가 제공한 설명·요약만 분석 | `제공 요약 기반` 표시 |
| `ALLOWLIST_ARTICLE` | 허용 목록에서 추출한 기사 본문 분석 | 출처·원문 링크·수집 시각 표시 |
| `ON_DEMAND_ARTICLE` | 사용자의 요약 요청 시 원문 링크에서 수집한 본문 분석 | `요청 시 원문 수집`과 원문 링크 표시 |
| `OFFICIAL_DISCLOSURE` | Open DART·SEC EDGAR·기업 IR 문서 분석 | `공식 공시/보도자료 기반` 표시 |
| `METADATA_ONLY` | 저장 본문 없음; 안전한 원문 URL이 있으면 on-demand 수집 시도 | 원문 링크와 `요청 시 원문 수집` 표시 |

검색 API의 짧은 `description`을 기사 전문이라고 표시하지 않는다. 각 분석은 실제로 수집·전처리한 본문과 `contentSource`, `contentHash`, `sourceUrl`, `fetchedAt`, `extractorVersion`을 추적할 수 있어야 한다.

현재 6,000자 단일 입력을 유지하는 동안에는 잘린 입력으로 분석했다는 사실과 `processedCharacters/originalCharacters`를 응답 메타데이터에 표시하는 것을 목표로 한다. 전문 전체 분석이 필요하면 다음 청크 파이프라인을 별도 프롬프트 버전으로 구현한다.

```text
추출 본문
  -> 문단 경계 청크와 segmentId 생성
  -> 청크별 사실·핵심 주장·위험 요인 추출
  -> 중복 제거
  -> 전체 요약·감성·영향 요인 합성
  -> 근거 segmentId와 원문 URL 연결
```

청크 크기, overlap, 최대 청크 수와 총 비용 한도는 실제 Gemini 모델 컨텍스트와 예산 PoC 후 확정한다. 청크별 호출과 최종 합성 호출의 토큰·비용을 모두 한 논리 요청에 집계한다.

### 3.4 전처리 변경 관리

전처리 결과가 달라질 수 있는 다음 변경은 AI 결과 의미와 캐시를 바꾸므로 새 promptVersion을 발급한다.

- 최대 입력 길이 변경
- 광고·URL·HTML 제거 규칙 변경
- 문장 분리 또는 중복 제거 정책 변경
- 제목·본문 조합 방식 변경
- 기사 본문 자체가 수정되어 기존 분석을 폐기해야 하는 경우

현재 cacheKey와 DB 분석 유일성에는 `contentHash`가 포함된다. 같은 `newsId`의 본문이 바뀌면 이전 Redis 키를 삭제하고 새 hash의 분석을 생성하며, 과거 분석 행은 감사 이력으로 유지한다.

## 4. 프롬프트 버전과 템플릿

### 4.1 버전 결정

1. 요청 promptVersion이 null 또는 공백이면 AI_PROMPT_VERSION을 사용한다.
2. 기본값은 구조화 분석 계약의 `news-analysis-v2`다.
3. 현재는 요청 버전이 비어 있지 않으면 trim한 값을 사용한다.
4. cacheKey와 ai_analyses.prompt_version, ai_usage_logs.prompt_version, API 응답에 최종 버전을 동일하게 기록한다.
5. 버전 이름은 소문자 영숫자로 시작하고 소문자 영숫자, 점, 밑줄, 하이픈만 사용하며 최대 50자로 제한하는 것을 목표 규칙으로 한다.

### 4.2 버전을 올려야 하는 경우

다음 중 하나라도 결과에 영향을 주면 기존 버전을 덮어쓰지 않고 새 버전을 발급한다.

- 프롬프트 문구 또는 출력 의미 변경
- 전처리 정책 변경
- JSON 필드·허용 감성·최대 항목 수 변경
- 모델 또는 생성 설정을 바꿔 기존 결과와 구분해야 하는 경우
- 기사 본문 수정 후 재분석이 필요한 경우
- 안전 정책이나 투자 권유 방지 지시 변경
- 검색 API 설명 기반에서 허용 출처 전문·청크 분석으로 입력 범위 변경
- 위험 요인·영향 요인·근거 segment 같은 분석 출력 추가

단순 오탈자처럼 결과 의미에 영향이 없음을 검토로 확인한 경우만 버전을 유지한다.

### 4.3 현재 구현의 한계

1. Gemini 프롬프트는 코드에 하드코딩되어 있다.
2. Mock은 promptVersion을 토큰 추정에만 포함하고 별도 템플릿을 선택하지 않는다.
3. 04_DB_SCHEMA.md의 prompt_templates 테이블은 실제 V4 마이그레이션에 없다.
4. 호출자가 서로 다른 버전을 보내도 Gemini의 템플릿 본문은 같고 캐시·DB 행만 분리된다.
5. 운영 전에는 호출자가 버전을 선택하는 방식 대신 서버 활성 버전을 기본으로 고정하고, 호환성 시험용 허용 목록만 선택 가능하게 해야 한다.

## 5. AI 공급자 계약

### 5.1 공통 인터페이스

AiProvider 입력:

- title
- preprocessedContent
- promptVersion

AiProviderResult 출력:

- modelName
- summary
- keyPoints
- keywords
- sentiment
- inputTokens
- outputTokens

공급자 구현은 예외 대신 불완전한 성공 객체를 반환해서는 안 된다. 서비스는 저장 전에 5.4의 공통 검증을 다시 수행한다.

### 5.2 Mock 공급자

AI_PROVIDER=mock 또는 공급자 설정 생략 시 MockAiProvider가 활성화된다.

1. 전처리 본문의 앞 문장 최대 3개를 keyPoints로 사용한다.
2. keyPoints를 공백으로 합쳐 summary로 사용한다.
3. 본문이 비어 summary가 없으면 “{title} 관련 핵심 내용을 확인했습니다.”를 사용하지만 서비스 단계에서 빈 본문을 먼저 거절하므로 정상 API 흐름에서는 사용되지 않는다.
4. HBM, 메모리, AI, 반도체, 실적, 공급망 중 제목·본문에 포함된 키워드를 순서대로 선택하고, 없으면 “시장 이슈”를 사용한다.
5. 증가·확대·회복·성장·상향 중 하나가 있으면 POSITIVE, 그렇지 않고 감소·하락·위험·둔화 중 하나가 있으면 NEGATIVE, 나머지는 NEUTRAL이다.
6. 토큰은 문자열 길이 / 3.2의 올림값, 최소 1로 추정한다.
7. modelName은 finwatch-demo-summarizer-v1이다.

현재 Mock은 여섯 후보가 모두 있으면 keywords를 6개 반환할 수 있다. 공통 계약의 최대 5개에 맞춰 제한하는 보강이 필요하다.

### 5.3 Gemini 공급자

AI_PROVIDER=gemini일 때 GEMINI_API_KEY가 비어 있으면 애플리케이션 시작을 실패시킨다.

기본 설정:

| 항목 | 값 |
|---|---|
| base URL | https://generativelanguage.googleapis.com |
| model | GEMINI_MODEL, 기본 gemini-3.1-flash-lite |
| endpoint | /v1beta/models/{model}:generateContent |
| 인증 | x-goog-api-key 헤더 |
| responseMimeType | application/json |
| temperature | 0.2 |
| maxOutputTokens | 500 |

프롬프트는 한국어 금융 뉴스 요약, 기사에 포함된 사실만 사용, 투자 권유·가격 예측 금지, 핵심 문장 최대 3개, 키워드 최대 5개, 감성 세 값 중 하나를 요구한다.

JSON 스키마 필수 필드:

- summary: string
- keyPoints: string array
- keywords: string array
- sentiment: POSITIVE, NEUTRAL, NEGATIVE 중 하나

Gemini usageMetadata가 있으면 promptTokenCount와 candidatesTokenCount를 사용한다. 없으면 입력과 응답 문자 길이 / 3.2 올림값으로 추정한다. modelVersion이 있으면 실제 값을 저장하고, 없으면 요청 model 설정값을 저장한다.

### 5.4 구조화 응답 검증

다음 검증은 공급자 종류와 무관하게 DB 저장 전에 적용한다. Gemini JSON Schema와 별개로 서버 검증기가 null·enum·항목 수·항목 길이·근거 ID·금지 문구·토큰 값을 확인하고 위반을 `502 AI_RESPONSE_INVALID`로 변환한다.

| 필드 | 검증 |
|---|---|
| modelName | null·공백 금지, 최대 100자 |
| summary | null·공백 금지, trim 후 최대 2,000자 |
| keyPoints | null 금지, 1~3개, 각 항목 trim 후 공백 금지·최대 500자 |
| keywords | null 금지, 1~5개, trim 후 공백 금지·최대 100자, 중복 제거 |
| sentiment | POSITIVE, NEUTRAL, NEGATIVE 중 하나 |
| inputTokens | 0 이상 |
| outputTokens | 0 이상 |

항목 수 초과 결과를 조용히 잘라 정상 처리하지 않는다. 공급자 계약 위반으로 보고 502 AI_RESPONSE_INVALID를 반환해야 운영 이상을 발견할 수 있다. 다만 앞뒤 공백 제거와 정확히 같은 keyword 중복 제거는 허용한다.

Gemini 응답에서 candidates, content, parts, text 중 하나가 없거나 JSON 파싱이 실패한 경우에도 AI_RESPONSE_INVALID다.

`news-analysis-v2`는 기존 필드에 다음 필드를 추가한다.

- `positiveFactors`: 기사에 명시된 긍정 요인, 최대 3개
- `riskFactors`: 기사에 명시된 위험·불확실성, 최대 3개
- `mentionedCompanies`: 기사에 실제 등장하는 회사·종목
- `evidenceSegments`: 각 핵심 판단을 뒷받침한 내부 `segmentId` 목록
- `analysisScope`: `FULL_PROCESSED_TEXT`, `PARTIAL_PROCESSED_TEXT`, 이전 데이터의 `LEGACY`

`evidenceSegments`는 모델이 만든 URL이나 긴 원문 인용이 아니라 서버가 실제로 공급한 `S1..Sn`을 기록한다. 긴 본문은 구간별로 공급자를 호출하며 `inputTokens`, `outputTokens`, `estimatedCost`는 모든 호출의 합계다.

## 6. 캐시, 영속 분석과 무효화

### 6.1 키와 저장 계층

~~~text
ai:news-summary:{newsId}:{contentHash}:{promptVersion}
~~~

1. demo 프로필은 ConcurrentHashMap 기반 메모리 캐시를 사용한다.
2. 그 외 프로필은 Redis를 사용한다.
3. AI_CACHE_TTL 기본값은 24시간이다.
4. 빠른 캐시 값은 analysisId, newsId, symbol, 결과 필드, modelName, promptVersion, 원 생성 비용, generatedAt을 포함한다.
5. ai_analyses는 뉴스·기능·프롬프트 버전당 하나의 영속 결과를 보관한다.

허용 출처 본문은 `contentHash`를 캐시와 DB 유일성에 포함한다. 같은 hash는 멱등 재사용하고 다른 hash는 새 분석 버전으로 저장한다.

```text
ai:news-analysis:{newsId}:{contentHash}:{promptVersion}
```

같은 `newsId`라도 본문 hash가 바뀌면 이전 분석을 최신 기사 분석으로 반환하지 않는다. 구조화 응답은 `news-analysis-v2`로 버전을 올려 이전 캐시와 분리한다.

### 6.2 HIT 판정

다음 두 경우 모두 API와 운영 로그에서 cacheHit=true다.

1. 빠른 캐시에서 값을 찾고 연결된 ai_analyses 행을 확인한 경우
2. 빠른 캐시는 MISS지만 DB에 같은 newsId·NEWS_SUMMARY·promptVersion 분석이 있어 빠른 캐시를 복구한 경우

HIT 응답은 inputTokens=0, outputTokens=0, estimatedCost=0 USD다. modelName과 generatedAt은 원 생성 결과를 유지하고 savedEstimatedCost에는 원 생성 estimatedCost를 기록한다.

### 6.3 TTL의 현재 의미

현재 서비스는 ai_analyses.expires_at을 조회 시 검사하지 않는다. 따라서 Redis 또는 메모리 TTL 24시간이 지나도 DB 분석을 다시 읽어 캐시를 채우며 모델을 재호출하지 않는다.

즉, 현재 MVP에서 빠른 캐시 TTL은 “모델 결과의 신선도”가 아니라 “빠른 저장소의 보유 시간”이다. 분석 결과는 프롬프트 버전이 유지되는 동안 영속 재사용된다. expires_at을 실제 만료로 쓰려면 DB 조회 시 정책을 추가하고 기존 동작·비용 영향과 함께 새 명세로 변경해야 한다.

### 6.4 무효화

현재 지원되는 안전한 무효화 방법은 새 promptVersion 발급이다. Redis 키만 삭제하면 DB 결과가 즉시 복구되므로 재생성되지 않는다.

같은 버전으로 강제 재생성이 필요하면 다음을 하나의 관리 작업으로 처리해야 한다.

1. 해당 cacheKey의 빠른 캐시 삭제
2. 연결된 ai_analyses 행 삭제 또는 폐기 상태 변경
3. 감사·운영 사유 기록
4. 다음 요청에서 새 분석 생성

캐시 삭제 API, 분석 폐기 상태와 운영자 감사 로그는 현재 대회 MVP 범위에서 제외한다. 운영자가 DB와 Redis를 수동으로 불일치하게 수정해서는 안 된다.

### 6.5 동시성

현재 두 요청이 동시에 같은 키로 MISS하면 둘 다 공급자를 호출할 수 있고, ai_analyses 유니크 제약에서 한 트랜잭션이 실패할 수 있다.

필수 보강 규칙:

1. cacheKey 단위 로컬 single-flight 또는 Redis 분산 락을 사용한다.
2. 락 획득 후 빠른 캐시와 DB를 다시 확인한다.
3. 첫 요청만 공급자를 호출하고 나머지는 생성된 DB 결과를 HIT로 반환한다.
4. 락에는 외부 호출 최대시간보다 긴 유한 TTL을 둔다.
5. 대기 시간 초과 시 무제한 중복 호출하지 않고 503 AI_REQUEST_IN_PROGRESS 또는 한 번의 DB 재확인 후 실패한다.
6. DB UNIQUE 제약은 락 장애 시 최종 방어선으로 유지한다.

빠른 캐시가 존재하지만 analysisId의 DB 행이 없으면 현재 구현은 서버 오류를 낸다. 보강 후에는 해당 캐시를 제거하고 DB·공급자 흐름을 다시 시작하되 불일치 경고 로그를 남긴다.

## 7. 외부 호출 실패, 재시도와 오류

### 7.1 현재 동작

Gemini RestClient에는 애플리케이션 수준의 명시적 연결·응답 타임아웃, 재시도, 429 Retry-After 처리와 도메인 오류 매핑이 없다. 공급자 실패 요청은 ai_usage_logs에 저장되지 않는다.

### 7.2 운영 목표 정책

| 항목 | 목표 |
|---|---|
| 연결 타임아웃 | 2초 |
| 응답 타임아웃 | 15초 |
| 최대 시도 횟수 | 최초 호출 포함 3회 |
| 재시도 대상 | 연결 오류, 응답 타임아웃, HTTP 408·429·5xx |
| 재시도 제외 | API 키 오류 등 400·401·403·404, 구조화 결과 검증 실패 |
| 지연 | 200ms, 500ms 지수형 지연과 작은 무작위 지터 |
| Retry-After | 429·503 응답에 있으면 설정 상한 내 우선 적용 |

모델이 응답을 생성했을 가능성이 있는 읽기 타임아웃 재시도는 비용을 중복 발생시킬 수 있다. 모든 시도 횟수와 공급자 요청 식별자를 구조화 로그에 남기고, 운영 지표의 estimatedCost는 확인 가능한 토큰 사용량만 집계한다.

### 7.3 오류 계약

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | VALIDATION_ERROR | newsId·필드 형식 오류 |
| 400 | AI_PROMPT_VERSION_UNSUPPORTED | 허용되지 않은 버전 |
| 404 | NEWS_NOT_FOUND | newsId 없음 |
| 422 | NEWS_CONTENT_UNAVAILABLE | 전처리 후 본문 없음 |
| 429 | AI_RATE_LIMITED | 사용자 한도 또는 공급자 429가 재시도 후 지속 |
| 429 | AI_BUDGET_EXCEEDED | 설정된 비용 한도 도달 |
| 502 | AI_RESPONSE_INVALID | 응답 누락·JSON·스키마·필드 검증 실패 |
| 502 | EXTERNAL_PROVIDER_ERROR | 재시도 후 공급자 오류 |
| 503 | AI_REQUEST_IN_PROGRESS | 동일 키 생성 대기 제한 초과 |
| 504 | AI_PROVIDER_TIMEOUT | 재시도 후 타임아웃 |

API 키, 전체 프롬프트, 기사 원문, 공급자 원문 응답은 사용자 오류 메시지에 포함하지 않는다.

## 8. 사용량 로그와 관측성

### 8.1 성공 로그

모든 성공 요청은 캐시 여부와 무관하게 ai_usage_logs 한 행을 남긴다.

MISS 모델 호출:

- inputTokens와 outputTokens: 공급자 사용량 또는 추정값
- estimatedCost: 계산된 이번 호출 비용
- savedEstimatedCost: 0
- cacheHit: false

빠른 캐시 또는 DB 복구 HIT:

- inputTokens, outputTokens, estimatedCost: 0
- savedEstimatedCost: 원 분석 estimatedCost
- cacheHit: true
- modelName: 원 분석 모델

### 8.2 실패 로그

필수 보강 후, 인증·기본 요청 파싱 전에 거절된 요청을 제외한 모든 AI 처리 실패는 status=FAILED 로그를 남긴다.

실패 로그 최소 필드:

- requestId
- userId
- featureType=NEWS_SUMMARY
- targetType=NEWS
- targetId=newsId
- promptVersion
- modelName, 확인 가능한 경우
- cacheHit=false
- responseTimeMs
- errorCode
- 확인 가능한 토큰·비용, 없으면 0
- createdAt

현재 AiUsageLog 엔티티는 error_code 필드 매핑과 실패 생성 메서드, user 관계가 없으므로 구현이 필요하다.

### 8.3 요청 ID와 보안 로그

1. 인바운드 X-Request-Id가 허용 형식이면 재사용하고 없으면 서버가 UUID를 생성한다.
2. 같은 requestId를 API 응답 헤더, 구조화 로그와 ai_usage_logs에 사용한다.
3. 로그에는 requestId, userId, newsId, promptVersion, provider, model, cacheHit, attemptCount, responseTimeMs, status, errorCode를 기록한다.
4. GEMINI_API_KEY와 Authorization 헤더는 어떤 로그에도 기록하지 않는다.
5. 제목·본문·요약 전문 대신 ID, 길이와 해시 등 비민감 메타데이터만 기록한다.

성공·실패 로그마다 별도 UUID requestId를 만들며 HTTP 추적 ID와 사용자 FK 연결은 다중 인스턴스 운영 관측 범위로 남긴다.

## 9. 토큰, 비용과 관리자 지표

### 9.1 비용 계산

설정 기본값:

| 설정 | 기본값 |
|---|---|
| AI_INPUT_PRICE_PER_MILLION | 0.25 |
| AI_OUTPUT_PRICE_PER_MILLION | 1.50 |
| 통화 | USD |

~~~text
inputCost  = inputTokens  × inputPricePerMillion  ÷ 1,000,000
outputCost = outputTokens × outputPricePerMillion ÷ 1,000,000
estimatedCost = inputCost + outputCost
~~~

각 나눗셈은 소수 12자리 HALF_UP, 최종 비용은 소수 8자리 HALF_UP이다.

이 값은 공급자 청구서가 아니라 설정 단가를 이용한 예상 비용이다. 현재 ai_model_prices 테이블은 구현되지 않았고 모델별·시점별 단가 이력을 적용하지 않는다. 모델 단가가 바뀌면 환경변수 적용 시점 이후 새 로그에만 새 단가를 사용하며 과거 로그를 재계산하지 않는다.

### 9.2 관리자 집계의 현재 정의

1. 날짜 범위는 UTC 날짜의 시작 이상, 종료일 다음 날 시작 미만이다.
2. 범위 생략 시 UTC 오늘을 포함한 최근 30일이다.
3. requestCount는 성공·실패 로그 전체이며 successCount와 failedCount를 함께 제공한다. 모델 호출·캐시 적중률·비용은 성공 로그 기준으로 계산한다.
4. cacheHitCount는 cacheHit=true 로그 수다.
5. modelCallCount와 cacheMissCount는 requestCount - cacheHitCount다.
6. cacheHitRate는 cacheHitCount / requestCount × 100, 요청이 없으면 0.00이다.
7. estimatedCost와 savedEstimatedCost는 각 로그 합계다.
8. averageResponseTimeMs는 범위 내 로그의 단순 평균이며 요청이 없으면 0.00이다.
9. 토큰은 HIT에서 0이므로 실제 모델 호출 로그의 토큰만 합산된다.

실패 로그를 구현할 때 requestCount, modelCallCount와 평균 응답시간에 어떤 실패를 포함하는지 관리자 응답에 successCount와 failedCount를 추가해 명확히 해야 한다.

## 10. 한도와 안전

### 10.1 현재 적용된 한도

- 전처리 본문 최대 6,000자
- Gemini 출력 최대 500토큰
- 인증된 사용자만 호출 가능
- Gemini temperature 0.2
- 프롬프트에 사실 기반 요약, 투자 권유·가격 예측 금지 지시
- 감성 값 세 종류 제한

### 10.2 운영 전 필수 한도

다음 값은 환경별 설정으로 만들고 운영에서 반드시 명시한다. 포트폴리오 시연 기본 정책은 아래와 같다.

| 정책 | 기준값 |
|---|---|
| 사용자별 요약 요청 | 분당 10회 |
| 사용자별 실제 모델 호출 | 일 100회 |
| 서비스 실제 모델 비용 경보 | 일 3 USD |
| 서비스 실제 모델 비용 차단 | 일 5 USD |
| 동일 뉴스 동시 생성 | cacheKey당 1개 |

캐시 HIT는 요약 요청 한도에는 포함하지만 실제 모델 호출·비용 한도에는 포함하지 않는다. 비용 차단 시 이미 존재하는 캐시·DB 결과는 계속 반환하고 새 모델 호출만 AI_BUDGET_EXCEEDED로 거절한다. ADMIN도 무제한 우회하지 않으며 별도 운영 설정으로만 한도를 조정한다.

현재 로컬·단일 서버 범위에서는 사용자별 분당 요청 수와 서비스 일일 비용 차단을 적용한다. 다중 인스턴스에서 완전히 일관된 사용자 카운터는 운영 배포 시 Redis 원자 카운터로 교체한다.

### 10.3 콘텐츠 안전

1. 기사 본문은 명령이 아니라 인용할 데이터로 취급해야 한다.
2. 운영 프롬프트는 기사 안의 지시문을 따르지 말라는 경계를 명시하고 제목·본문을 명확한 구분자로 감싼다.
3. 공급자 safety 결과로 차단된 응답을 빈 성공으로 처리하지 않고 EXTERNAL_PROVIDER_ERROR 또는 별도 AI_CONTENT_BLOCKED로 기록한다.
4. 결과 화면에는 “AI 생성 요약이며 투자 권유가 아니다”라는 문구와 원 기사 링크·출처를 표시한다.
5. 요약에 수익 보장, 매수·매도 명령 또는 기사에 없는 가격 예측이 포함됐는지 자동으로 완벽히 판별할 수 있다고 가정하지 않는다.
6. Gemini 공급자 기본 안전 설정에만 암묵적으로 의존하지 말고 운영 시 적용한 safety 설정을 코드와 버전 문서에 고정한다.
7. 원문 또는 결과를 제3자 공급자에 전송할 권한이 있는 뉴스만 AI 대상으로 삼는다.
8. 크롤링한 본문의 “이전 지시를 무시하라”, “시스템 정보를 출력하라” 같은 문장은 데이터로 취급하고 실행 지시로 해석하지 않는다.
9. 본문을 명확한 데이터 구분자와 segmentId로 감싸며 시스템 지시와 동일한 문자열 템플릿에 무방비하게 연결하지 않는다.
10. 모델 출력의 회사명·숫자·감성 근거가 실제 입력 segment에 존재하는지 가능한 범위에서 서버 검증 또는 근거 표시로 확인한다.

현재 프롬프트는 user role 하나에 지시와 본문을 함께 넣으며 명확한 데이터 구분자, 별도 safety 설정과 결과 안전 검사가 없다.

## 11. 인수 조건

### 11.1 현재 MVP 기준

- HTML, 광고 문구, URL과 정확히 중복된 문장이 제거되고 결과는 6,000자를 넘지 않는다.
- 존재하지 않는 newsId는 공급자 호출 없이 404다.
- 전처리 후 빈 본문은 공급자 호출 없이 422다.
- promptVersion 생략 시 AI_PROMPT_VERSION이 응답·DB·캐시 키·로그에 동일하게 사용된다.
- 첫 동일 요청은 cacheHit=false이고 분석 1건과 사용 로그 1건이 생성된다.
- 두 번째 동일 요청은 cacheHit=true, 토큰과 실제 비용 0이며 사용 로그만 한 건 늘어난다.
- 빠른 캐시가 비어도 DB 분석이 있으면 모델을 호출하지 않고 cacheHit=true로 복구한다.
- 서로 다른 promptVersion은 서로 다른 분석과 캐시 키를 사용한다.
- HIT 응답은 원 modelName과 generatedAt을 유지한다.
- 비용 계산은 설정 단가 공식과 소수 8자리 반올림에 일치한다.
- USER는 AI 요약을 호출할 수 있지만 /admin/ai/**에는 403, ADMIN은 관리자 지표를 조회할 수 있다.

### 11.2 운영 보강 완료 기준

- 등록되지 않은 promptVersion은 400이며 공급자 호출과 분석 행 생성을 하지 않는다.
- Mock과 Gemini의 모든 결과에 공통 구조 검증이 적용되고 잘못된 JSON·빈 summary·잘못된 sentiment·항목 수 초과는 502 AI_RESPONSE_INVALID다.
- 같은 cacheKey로 20개 동시 요청을 보내도 실제 공급자 호출과 ai_analyses 행은 각각 하나이고 나머지는 HIT로 완료된다.
- 연결 오류·429·5xx는 정해진 횟수만 재시도하고 영구 오류는 재시도하지 않는다.
- 공급자 실패도 FAILED 로그 한 건으로 남고 HTTP requestId, userId, errorCode와 연결된다.
- 빠른 캐시가 고아 analysisId를 가리키면 키를 제거하고 복구하며 불일치 경고를 남긴다.
- 일일 비용 차단 후 기존 HIT는 성공하고 새 MISS만 429 AI_BUDGET_EXCEEDED다.
- API 키, Authorization, 기사 본문과 전체 프롬프트가 애플리케이션·사용량 로그에 나타나지 않는다.
- 프롬프트 인젝션 형태의 기사 문장이 있어도 출력 스키마를 벗어나지 않으며, 기사 안의 지시를 시스템 지시보다 우선하지 않는다.
- 관리자 지표는 성공·실패 수와 실제 모델 호출 수를 구분해 설명할 수 있다.
- 저장 본문이 없는 일반 뉴스는 사용자 요청 시에만 원문 fetch가 발생하고, 안전 검증과 본문 추출을 통과한 경우에만 Gemini로 전달된다.
- 화면에서 제공 요약·부분 본문·전체 본문·공식 공시 중 어떤 범위를 분석했는지 구분한다.
- 본문이 수정되어 contentHash가 바뀌면 새 분석을 생성하고 이전 캐시를 최신 결과로 반환하지 않는다.
- 프롬프트 인젝션 문장이 포함된 기사에서도 내부 지시·Secret을 출력하지 않고 허용된 JSON 계약만 반환한다.
- 청크 분석을 사용할 경우 모든 청크와 최종 합성 비용이 요청 로그에 합산되고 근거 segmentId가 유효하다.

## 12. 구현 우선순위

1. 공급자 결과 공통 검증과 도메인 오류 매핑
2. 명시적 타임아웃·제한된 재시도와 실패 로그
3. promptVersion 서버 허용 목록과 요청 길이 검증
4. cacheKey 단위 single-flight와 고아 캐시 복구
5. HTTP 요청 ID·userId 로그 연결
6. Redis 기반 호출·비용 한도
7. 프롬프트 템플릿 저장·활성 버전 관리와 관리자 무효화 절차
8. 허용 출처 본문·공시 분석용 contentHash 캐시 키와 분석 범위 메타데이터
9. 긴 본문의 청크 추출·합성과 근거 segment 검증

구현 변경 시 03_API_SPEC.md의 오류 코드와 응답, 04_DB_SCHEMA.md의 실제 마이그레이션 상태, 05_TASKS.md의 체크 상태를 함께 갱신한다.

## 13. TECHNICAL_EXPLANATION 확장 계약

`TECHNICAL_EXPLANATION`은 구현 상태다. 서버가 계산한 기술지표만 Gemini가 설명하며 지표 재계산, 목표주가·수익률 예측과 직접 매매 명령을 금지한다. 입력 evidence·API·DB·캐시·UI·인수 조건의 단일 상세 기준은 `12_AI_TECHNICAL_EXPLANATION_SPEC.md`다.

## 14. DAILY_CHANGE_BRIEFING 확장 계약

`DAILY_CHANGE_BRIEFING`은 구현 상태다. 서버가 계산한 직전·최신 완성 일봉 delta와 이미 검증된 뉴스·공시 분석만 Gemini가 설명한다. 브리핑이 온디맨드로 뉴스·공시 요약을 보완할 때는 하드코딩된 과거 버전 대신 각 요약 기능의 활성 프롬프트 버전을 사용한다. 기술·뉴스·공시를 하나의 매수·매도 점수로 합치거나 근거 없는 인과관계, 목표주가와 직접 매매 명령을 생성해서는 안 된다. 상세 기준은 `13_AI_DAILY_CHANGE_BRIEFING_SPEC.md`다.
