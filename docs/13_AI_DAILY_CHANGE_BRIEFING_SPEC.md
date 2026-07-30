# FinWatch 근거 기반 일일 변화 브리핑 명세

상태: v2.0 구현
기준일: 2026-07-24
기능 유형: `DAILY_CHANGE_BRIEFING`
활성 프롬프트: `daily-change-briefing-v2-news-fixed`

## 1. 제품 목적과 차별화

일일 변화 브리핑은 종목을 추천하거나 목표주가를 만드는 기능이 아니다. 사용자가 전일 대비 무엇이 변했고 기술지표·뉴스·공시가 어디에서 일치하거나 충돌하는지 검증 가능한 근거로 확인하도록 돕는다. 종목 탐색에서 선택 즉시 생성한 검증된 뉴스 분석은 브리핑의 `N` 근거로 재사용할 수 있다.

```text
기존 정보 서비스
→ 현재 시세·뉴스·기술평가를 각각 제공

FinWatch 브리핑
→ 이전 완성 일봉과 현재 완성 일봉 비교
→ 새 뉴스·공시만 연결
→ 기술·뉴스·공시 관점을 분리
→ 일치·충돌·데이터 한계를 근거로 설명
→ AI 버전·토큰·비용·캐시까지 공개
```

대표 문구:

> FinWatch는 종목을 추천하는 AI가 아니라, 전일 대비 무엇이 변했고 기술지표·뉴스·공시가 어디에서 일치하거나 충돌하는지를 검증 가능한 근거로 설명하는 투자정보 플랫폼이다.

TradingView와 Investing.com의 차트·글로벌 데이터·종목 추천 범위를 기능 수로 따라가는 것을 목표로 하지 않는다. FinWatch의 차별점은 변화 추적, 공식 공시 연결, 근거 추적성과 AI 운영 투명성이다.

비교 참고:

- [TradingView Technical Ratings](https://www.tradingview.com/support/solutions/43000614331-technical-ratings/)
- [TradingView AI Chart Copilot](https://www.tradingview.com/blog/en/tradingview-ai-chart-copilot-beta-57730/)
- [InvestingPro 기능](https://www.investing.com/pro/pricing)

## 2. 문서 사용법과 범위

이 기능 담당 AI는 이 문서를 먼저 읽고 필요한 계약만 다음 문서에서 확인한다.

| 작업 | 추가로 읽을 범위 |
|---|---|
| 기술지표 입력 | `11_TECHNICAL_ANALYSIS_SPEC.md` |
| 기술지표 AI 해설 재사용 | `12_AI_TECHNICAL_EXPLANATION_SPEC.md` |
| 뉴스·공시 분석 재사용 | `08_AI_OPERATION_SPEC.md`의 NEWS_SUMMARY 계약 |
| API·DB 요약 | `03_API_SPEC.md`, `04_DB_SCHEMA.md`의 해당 섹션 |
| 인수 | `10_TEST_ACCEPTANCE_SPEC.md`의 AC-12 |

포함 범위:

- 최신 완성 일봉과 직전 완성 일봉의 정량 변화
- 이전·현재 기술지표 스냅샷 비교
- 비교 구간에 새로 게시된 뉴스·공시
- 기술·뉴스·공시 관점 매트릭스
- 일치·충돌·새 위험·유지된 흐름 설명
- 근거에서 차트 지표·뉴스·공시 원문으로 이동
- AI 감사 카드와 비용·캐시 투명성

제외 범위:

- 종목 추천 순위와 AI Pick
- 목표주가·적정가·진입가·청산가·손절가
- 미래 가격·수익률·확률 예측
- 개인 투자성향 기반 자문
- 원문 이용 권한이 없는 기사 전문 전송
- 장중 틱을 전일 완성 일봉과 섞은 분석
- 백테스트와 성과 홍보

## 3. 브리핑 기준 시점

MVP 브리핑은 `1D` 완성 봉만 비교한다.

- `currentTradingDate`: 최신 유효 완성 일봉의 거래소 현지 거래일
- `previousTradingDate`: 그 직전 유효 완성 일봉의 거래일
- 주말·휴장일은 거래소 캘린더로 건너뛴다.
- KRX와 미국 시장 날짜를 서버 UTC 날짜로 임의 변환하지 않는다.
- 장중 미완성 일봉은 사용하지 않는다.
- 같은 currentTradingDate의 가격 정정은 새 inputHash와 새 브리핑 버전을 만든다.

뉴스·공시 비교 구간은 이전 스냅샷의 `latestRecordedAt` 초과부터 현재 브리핑 생성 시각 이하까지다. 기사 `publishedAt`과 공시 접수 시각을 사용하며 수집 시각만으로 새 콘텐츠를 판정하지 않는다.

직전 유효 스냅샷이 없으면 `baselineStatus=NO_BASELINE`으로 초기 브리핑을 만들 수 있지만 전일 변화라고 표현하지 않는다.

## 4. 처리 흐름

```text
symbol 요청
→ 최신·직전 완성 일봉과 기술지표 스냅샷 조회
→ 정량 delta와 변화 이벤트 계산
→ 비교 구간의 허용된 뉴스·공시 분석 조회
→ T/N/D/Q evidence 생성
→ 관점 매트릭스 서버 결정
→ inputHash·cacheKey 계산
→ Redis/DB 조회
   |- HIT: 기존 브리핑 반환
   `- MISS: Gemini가 변화·일치·충돌을 구조화 설명
            → evidence·금지 출력 검증
            → DB·Redis·사용량 로그 저장
→ 브리핑·근거·AI 감사 카드 표시
```

Gemini 장애 시 기존 차트, 기술지표, 뉴스와 공시 목록은 계속 조회돼야 한다.

## 5. 정량 변화 계산

서버가 결정론적으로 다음 변화를 계산한다. Gemini가 delta를 계산하지 않는다.

| 영역 | 변화 항목 |
|---|---|
| 가격 | 종가 차이·등락률, 고가·저가 범위 변화 |
| 이동평균 | MA5·20·60 값·기울기·가격과의 관계 변화 |
| RSI | 현재값, 전일값, delta, 30·70 구간 진입·이탈 |
| MACD | value·signal·histogram delta, 부호와 교차 변화 |
| 볼린저 | 현재 위치, bandwidthPercent delta, 밴드 접촉·이탈 |
| ATR | ATR14·atrPercent delta |
| 거래량 | 현재 거래량, Volume MA20, 평균 대비 배수와 전일 배수 delta |
| 이벤트 | 비교 구간의 MA·MACD·RSI 교차 이벤트 |

수치 delta는 API 가격 정밀도보다 높은 내부 정밀도로 계산하고 마지막에 표시 정밀도로 반올림한다. 입력이 없는 값을 0이나 직전 값으로 채우지 않는다.

변화 분류 예시:

- `NEW`: 새 교차·새 구간 진입·새 뉴스·새 공시
- `STRENGTHENED`: 같은 방향의 정량 강도가 커짐
- `WEAKENED`: 같은 방향의 정량 강도가 작아짐
- `REVERSED`: 부호·관계·구간이 반대로 바뀜
- `UNCHANGED`: 정책상 유의미한 변화 없음
- `INSUFFICIENT`: 비교 불가

임계값 없는 지표에 임의의 강함·약함 기준을 만들지 않는다. 분류 임계값을 추가하면 calculationVersion 또는 briefingInputVersion을 올린다.

## 6. 뉴스·공시 재사용 정책

브리핑은 원문을 다시 Gemini에 보내지 않고 기존 `NEWS_SUMMARY`의 검증된 구조화 분석과 근거 segment를 재사용한다. 새 분석이 필요하면 하드코딩된 과거 버전이 아니라 현재 활성 뉴스 프롬프트 버전을 사용한다.

- `aiAnalysisAllowed=true`이고 성공 분석이 있는 콘텐츠만 의미 근거로 사용한다.
- `METADATA_ONLY`는 제목·출처·게시 시각만 표시할 수 있고 의미 판단 근거로 사용하지 않는다.
- Open DART·SEC EDGAR·공식 IR은 `DISCLOSURE`, 허용 뉴스는 `NEWS`로 구분한다.
- 기사·공시가 갱신되어 contentHash가 바뀌면 브리핑 inputHash도 바뀐다.
- 분석되지 않은 신규 콘텐츠 때문에 브리핑 생성을 무기한 막지 않는다. 제외 수와 이유를 dataLimitations에 표시한다.
- 같은 사실을 재전송한 중복 기사들은 canonical URL·externalId·contentHash와 제목 유사도로 대표 근거 하나를 선택한다.

브리핑 입력에는 뉴스·공시별로 title, source, publishedAt, contentHash, 기존 분석의 positiveFactors·riskFactors·summary와 유효 evidenceSegments만 포함한다.

## 7. Evidence 규칙

근거 ID prefix로 출처를 즉시 구분한다.

| Prefix | 근거 |
|---|---|
| `T1..Tn` | 기술지표 현재값·전일값·delta·이벤트 |
| `N1..Nn` | 허용 뉴스의 검증된 분석과 원문 segment |
| `D1..Dn` | Open DART·SEC·IR 공시 분석과 원문 segment |
| `Q1..Qn` | 데이터 품질·출처·시각·DEMO/STALE 한계 |

각 evidence:

```json
{
  "id": "T1",
  "domain": "TECHNICAL",
  "kind": "RSI_CHANGE",
  "currentValue": 68.4,
  "previousValue": 61.2,
  "delta": 7.2,
  "displayValue": "RSI14 61.20 → 68.40",
  "sourceRef": {
    "type": "CHART_INDICATOR",
    "target": "RSI",
    "time": "2026-07-14T06:00:00Z"
  }
}
```

뉴스·공시 evidence의 `sourceRef`는 newsId 또는 disclosureId, source URL과 내부 segmentId를 포함한다. 사용자에게 원문 전체 저장 권한이 없으면 허용된 짧은 근거와 외부 링크만 표시한다.

## 8. 관점 매트릭스

서버는 Gemini 호출 전에 행과 상태를 결정한다. Gemini는 상태를 변경하지 않고 설명만 생성한다.

| 관점 | 상태 enum | 주요 입력 |
|---|---|---|
| 추세 | POSITIVE / CAUTION / NEUTRAL / INSUFFICIENT | 가격·MA 관계와 변화 |
| 모멘텀 | POSITIVE / CAUTION / NEUTRAL / INSUFFICIENT | RSI·MACD와 교차 |
| 과열 | CAUTION / NEUTRAL / INSUFFICIENT | RSI·밴드 위치 |
| 변동성 | CAUTION / NEUTRAL / INSUFFICIENT | ATR·밴드 폭 변화 |
| 거래량 | CONFIRMING / DIVERGING / NEUTRAL / INSUFFICIENT | 가격 방향과 거래량 배수 |
| 뉴스 | POSITIVE / CAUTION / MIXED / NEUTRAL / INSUFFICIENT | 신규 뉴스 분석 |
| 공시 | POSITIVE / CAUTION / MIXED / NEUTRAL / INSUFFICIENT | 신규 공식 공시 분석 |

각 행은 `viewpoint`, `status`, `changeType`, `headline`, `evidenceIds`를 가진다. 상태 규칙은 코드의 단일 정책 클래스로 관리하고 변경 시 `briefingInputVersion`을 올린다.

전체를 하나의 매수·매도 점수로 합치지 않는다. 화면과 AI는 다음 관계만 설명한다.

- `ALIGNED`: 서로 다른 관점이 같은 방향을 지지
- `CONFLICTING`: 긍정과 주의 근거가 동시에 존재
- `PARTIAL`: 일부 관점만 근거가 있음
- `INSUFFICIENT`: 판단 가능한 근거 부족

## 9. Gemini 입력·출력 계약

### 9.1 책임

Gemini는 서버가 만든 delta, matrix와 evidence를 변경하지 않는다. 새 기업 사실이나 시장 원인을 추론하지 않고 입력에 있는 근거만 연결한다.

### 9.2 출력 JSON

```json
{
  "headline": "string",
  "changeSummary": "string",
  "newStrengths": [
    { "text": "string", "evidenceIds": ["T1", "D1"] }
  ],
  "newRisks": [
    { "text": "string", "evidenceIds": ["T2"] }
  ],
  "unchangedContext": [
    { "text": "string", "evidenceIds": ["T3"] }
  ],
  "alignedViews": [
    { "text": "string", "evidenceIds": ["T1", "N1"] }
  ],
  "conflictingViews": [
    { "text": "string", "evidenceIds": ["T1", "T2"] }
  ],
  "dataLimitations": ["string"]
}
```

| 필드 | 제한 |
|---|---|
| headline | 1~120자 |
| changeSummary | 1~1,200자 |
| 각 목록 | 0~5개, 항목 350자 이하 |
| evidenceIds | 항목당 1~6개, 입력 ID만 허용 |
| dataLimitations | 0~5개, 항목 300자 이하 |

headline과 changeSummary도 최소 하나의 서버 evidence와 연결되도록 내부 응답 모델에서는 evidenceIds를 함께 관리한다. 공개 응답에서 headline 근거를 별도 필드로 제공한다.

## 10. API 계약

### 10.1 생성·조회

`POST /api/v1/ai/daily-change-briefings`

```json
{
  "symbol": "000660",
  "promptVersion": "daily-change-briefing-v2-news-fixed"
}
```

응답 핵심:

```json
{
  "briefingId": 901,
  "symbol": "000660",
  "market": "KRX",
  "currentTradingDate": "2026-07-14",
  "previousTradingDate": "2026-07-13",
  "baselineStatus": "AVAILABLE",
  "relation": "CONFLICTING",
  "headline": "상승 모멘텀은 강화됐지만 과열 신호도 가까워졌습니다.",
  "changeSummary": "...",
  "viewpoints": [],
  "newStrengths": [],
  "newRisks": [],
  "unchangedContext": [],
  "alignedViews": [],
  "conflictingViews": [],
  "dataLimitations": [],
  "evidence": [],
  "audit": {
    "priceSource": "KIS",
    "newsSources": ["NAVER_API_HUB"],
    "disclosureSources": ["OPENDART"],
    "latestRecordedAt": "2026-07-14T06:00:00Z",
    "calculationVersion": "technical-v2-wilder",
    "briefingInputVersion": "daily-briefing-input-v2-news-fixed",
    "promptVersion": "daily-change-briefing-v2-news-fixed",
    "modelName": "configured-model",
    "evidenceCount": 9,
    "excludedContentCount": 1,
    "cacheHit": false,
    "inputTokens": 980,
    "outputTokens": 310,
    "estimatedCost": 0.00071,
    "savedEstimatedCost": 0,
    "responseTimeMs": 920,
    "generatedAt": "2026-07-14T06:05:00Z"
  },
  "disclaimer": "AI 브리핑은 투자 권유가 아닌 정보 정리 결과입니다."
}
```

클라이언트는 delta, matrix, evidence와 source 목록을 제출하지 않는다. 서버가 전부 재조회한다.

### 10.2 최신 결과 조회

`GET /api/v1/stocks/{symbol}/daily-change-briefings/latest`

저장된 최신 결과가 있으면 모델 호출 없이 반환한다. 현재 완성 일봉과 inputHash가 다르면 `staleBriefing=true`로 표시하고 자동으로 최신인 것처럼 보여주지 않는다.

## 11. 캐시와 무효화

정렬된 기술·뉴스·공시 입력의 canonical JSON으로 `inputHash`를 만든다.

```text
ai:daily-change-briefing:{market}:{symbol}:{currentTradingDate}:{inputHash}:{promptVersion}
```

inputHash 포함 요소:

- 이전·현재 technical snapshot hash
- calculationVersion과 briefingInputVersion
- 정렬된 뉴스·공시 ID, contentHash, 분석 promptVersion
- matrix 상태·changeType·evidence
- source·freshness·DEMO 상태

새 완성 봉, 과거 OHLCV 정정, 신규·수정 뉴스/공시, 하위 분석 변경, matrix 정책 또는 promptVersion 변경은 새 MISS를 만든다. Redis TTL 만료만으로 같은 DB 브리핑을 재생성하지 않는다.

같은 cacheKey의 동시 MISS는 single-flight 또는 Redis 분산 락으로 Gemini 호출 한 번만 허용한다. HIT는 토큰·실제 비용 0이며 원 생성 비용을 savedEstimatedCost로 기록한다.

## 12. DB 계약

### ai_daily_change_briefings

| 컬럼 묶음 | 필드 |
|---|---|
| 대상 | id, stock_id, market, current_trading_date, previous_trading_date, baseline_status |
| 입력 버전 | latest_recorded_at, calculation_version, briefing_input_version, prompt_version, input_hash |
| 결과 | relation, headline, change_summary |
| 구조화 데이터 | viewpoints, new_strengths, new_risks, unchanged_context, aligned_views, conflicting_views, data_limitations, evidence JSONB |
| 감사 | source_summary JSONB, evidence_count, excluded_content_count |
| 모델 | model_name, input_tokens, output_tokens, estimated_cost, cache_key, generated_at |

유일성:

```text
(stock_id, current_trading_date, input_hash, prompt_version)
(cache_key)
```

`ai_usage_logs`는 `featureType=DAILY_CHANGE_BRIEFING`, `targetType=STOCK`, `targetId=stockId`, `dailyBriefingId`를 사용한다. 실패 로그는 dailyBriefingId가 null일 수 있다.

## 13. AI 감사 카드

사용자 화면에서 다음을 확인할 수 있어야 한다.

- 가격·뉴스·공시 공급자
- 현재·이전 거래일과 데이터 기준 시각
- calculationVersion·briefingInputVersion·promptVersion
- 실제 Gemini modelName
- 사용 근거 수와 제외 콘텐츠 수
- cache HIT/MISS
- 입력·출력·전체 토큰
- 이번 요청 예상 비용과 캐시 절감 예상 비용
- 응답시간과 원 생성 시각

API 키, 전체 프롬프트, 원문 전문과 내부 서버 경로는 표시하거나 로그에 남기지 않는다.

## 14. UI 계약

종목 상세 또는 대시보드에 `오늘의 변화 브리핑` 카드를 제공한다.

1. 날짜 헤더: 현재 거래일 vs 이전 거래일
2. 변화 요약: 새롭게 강해진 신호·위험·유지된 맥락
3. 관점 매트릭스: 추세·모멘텀·과열·변동성·거래량·뉴스·공시
4. 일치·충돌 영역: 어느 관점이 같은 방향이고 어디서 충돌하는지
5. 근거 drawer: `T/N/D/Q` evidence와 원본 위치
6. AI 감사 카드
7. 출처·STALE/DEMO·제외 콘텐츠·면책

근거 상호작용:

- T 근거: 차트 기간·해당 일봉·지표 패널을 열고 강조
- N 근거: 뉴스 카드와 허용된 근거 segment 또는 외부 원문 링크
- D 근거: 공시 카드·문단과 Open DART/SEC 원문 링크
- Q 근거: 데이터 출처·시각·신선도 설명

모바일에서는 매트릭스를 세로 카드로 바꾸고 근거 drawer를 전체폭으로 표시한다. 색상만으로 상태를 구분하지 않는다.

상태:

- 실행 전, 생성 중, MISS, HIT
- NO_BASELINE 초기 브리핑
- 새 뉴스·공시 없음
- 일부 콘텐츠 분석 제외
- STALE·DEMO
- 공급자·Gemini 실패
- 저장된 브리핑이 현재 스냅샷보다 오래된 상태

## 15. 서버 검증과 안전

- 모든 Gemini evidence ID는 입력 집합의 부분집합이어야 한다.
- delta, matrix status, relation과 source를 Gemini가 변경할 수 없다.
- 출력 숫자는 evidence에서 추적 가능해야 한다.
- 뉴스·공시 원인을 가격 움직임의 확정적 인과관계로 표현하지 않는다.
- 목표주가·수익률·확률·직접 매매 명령·수익 보장을 거절한다.
- METADATA_ONLY 콘텐츠를 의미 근거로 인용하지 않는다.
- STALE·DEMO·NO_BASELINE·제외 콘텐츠 한계를 서버가 강제 병합한다.

검증 실패 결과는 DB·Redis에 저장하지 않고 FAILED 사용량 로그와 `502 AI_RESPONSE_INVALID`를 반환한다.

## 16. 오류 계약

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | VALIDATION_ERROR | symbol·promptVersion 또는 등록되지 않은 필드 |
| 400 | AI_PROMPT_VERSION_UNSUPPORTED | 등록되지 않은 프롬프트 |
| 404 | STOCK_NOT_FOUND | 종목 없음 또는 비활성 |
| 404 | DAILY_BRIEFING_NOT_FOUND | 저장된 최신 브리핑 없음 |
| 422 | BRIEFING_BASELINE_UNAVAILABLE | 정책상 NO_BASELINE 생성을 허용하지 않는 환경 |
| 422 | BRIEFING_INPUT_INVALID | 기술·뉴스·공시 입력 정합성 오류 |
| 429 | AI_RATE_LIMITED | 사용자·공급자 한도 초과 |
| 429 | AI_BUDGET_EXCEEDED | 서비스 비용 한도 도달 |
| 502 | AI_RESPONSE_INVALID | 근거·수치·스키마·금지 출력 검증 실패 |
| 502 | EXTERNAL_PROVIDER_ERROR | 재시도 후 Gemini 실패 |
| 503 | AI_REQUEST_IN_PROGRESS | 같은 키 생성 대기 초과 |
| 504 | AI_PROVIDER_TIMEOUT | 재시도 후 타임아웃 |

## 17. 관리자 집계

관리자 화면은 `NEWS_SUMMARY`, `TECHNICAL_EXPLANATION`, `DAILY_CHANGE_BRIEFING`을 분리한다.

- 요청 수와 실제 Gemini 호출 수
- cache hit count·rate
- 입력·출력 토큰
- 실제 예상 비용·절감 예상 비용
- 평균 응답시간과 실패율
- 평균 evidence 수·제외 콘텐츠 수
- 고비용 브리핑 목록

브리핑은 하위 뉴스·공시 분석을 재사용하므로 하위 분석의 과거 비용을 현재 브리핑 비용에 중복 합산하지 않는다. 이번 요청에서 새로 발생한 Gemini 호출만 실제 비용으로 기록한다.

## 18. 구현 순서

1. 이전·현재 기술 스냅샷과 deterministic delta 계산기
2. 비교 구간 뉴스·공시 분석 조회와 중복 제거
3. T/N/D/Q evidence와 관점 매트릭스 정책
4. canonical inputHash와 DB migration
5. Mock·Gemini `daily-change-briefing-v2-news-fixed` 구조화 계약
6. 근거·수치·인과·금지 출력 검증
7. Redis/DB cache, single-flight와 사용량 로그
8. 생성·최신 조회 API
9. 브리핑·매트릭스·근거 drawer·감사 카드 UI
10. 관리자 기능별 비용 분리
11. 단위·통합·계약·E2E 테스트

## 19. 인수 조건

- [ ] 최신·직전 완성 일봉을 거래소 캘린더 기준으로 선택하고 장중 봉을 제외한다.
- [ ] 가격·지표 delta와 changeType이 고정 fixture와 일치한다.
- [ ] 비교 구간 밖 콘텐츠와 METADATA_ONLY 전문은 의미 근거에서 제외된다.
- [ ] T/N/D/Q 근거가 각각 차트·뉴스·공시·품질 정보로 이동한다.
- [ ] 관점 매트릭스 상태는 서버 정책 결과이며 Gemini가 변경하지 못한다.
- [ ] 상승 근거, 새 위험, 유지된 흐름과 충돌 관점이 실제 evidence로 연결된다.
- [ ] 같은 inputHash 첫 요청은 MISS, 재요청·DB 복구는 HIT이며 동시 호출도 모델 한 번이다.
- [ ] 새 일봉·과거 정정·신규 공시·contentHash·하위 분석·정책·프롬프트 변경은 새 MISS다.
- [ ] 목표주가·수익률·확률·직접 매매 명령과 근거 없는 인과관계는 저장·노출되지 않는다.
- [ ] NO_BASELINE·STALE·DEMO·제외 콘텐츠가 숨겨지지 않는다.
- [ ] AI 감사 카드의 버전·근거·토큰·비용·캐시 값이 API와 사용량 로그와 일치한다.
- [ ] 뉴스 분석·기술지표 해설·일일 변화 브리핑·포트폴리오 평가 네 AI 기능의 호출·비용·절감액이 관리자 화면에서 분리된다.
- [ ] Gemini 장애 중에도 차트·기술지표·뉴스·공시 목록이 정상 동작한다.
- [ ] 데스크톱·390px 모바일·키보드에서 브리핑과 근거를 확인할 수 있다.

미완료 항목이 남아 있으면 `DAILY_CHANGE_BRIEFING`을 구현 완료로 표시하지 않는다.
