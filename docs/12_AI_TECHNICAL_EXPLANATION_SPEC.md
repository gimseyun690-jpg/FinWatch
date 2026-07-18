# FinWatch AI 기술지표 해설 명세

상태: v0.2 로컬 Vertical Slice 구현, LIVE Gemini smoke·추가 무효화/동시성 증적 진행 중
기준일: 2026-07-14
기능 유형: `TECHNICAL_EXPLANATION`
활성 프롬프트: `technical-explanation-v1`

## 1. 목적과 문서 사용법

이 문서는 서버가 계산한 기술지표를 Gemini가 근거값과 충돌 신호 중심으로 설명하는 기능의 단일 상세 기준이다. Gemini는 지표 계산기나 투자 추천기가 아니며 사용자가 숫자의 의미와 한계를 이해하도록 돕는다.

병렬 작업 시 이 기능 담당자는 이 문서를 먼저 읽고 필요한 계약만 다음 문서에서 확인한다.

| 작업 | 추가로 읽을 범위 |
|---|---|
| 백엔드 API·DTO | `03_API_SPEC.md`의 8.1, 기술지표 응답 |
| DB migration | `04_DB_SCHEMA.md`의 AI 테이블 요약 |
| 계산 입력 | `11_TECHNICAL_ANALYSIS_SPEC.md`의 현재 계산·API 계약 |
| 관리자 비용 | `08_AI_OPERATION_SPEC.md`의 공통 캐시·비용·오류 정책 |
| E2E·인수 | `10_TEST_ACCEPTANCE_SPEC.md`의 AC-11 |

다른 기능 담당자는 이 문서를 다시 읽을 필요가 없다. API 필드, DB 컬럼 또는 완료 상태가 바뀔 때만 관련 요약 문서와 이 문서를 같은 변경에서 갱신한다.

## 2. 범위와 책임 경계

```text
symbol·interval 요청
→ 서버가 최신 완성 봉과 기술지표 재조회
→ 정규화 스냅샷·근거 I1..In 생성
→ inputHash·cacheKey 계산
→ Redis와 DB 조회
→ MISS일 때만 Gemini 구조화 호출
→ 근거·금지 출력 검증
→ DB·Redis·사용량 로그 저장
→ 근거가 연결된 해설 반환
```

서버 책임:

- OHLCV와 기술지표 계산
- 데이터 품질·신선도·DEMO 판정
- `summarySignal` 결정
- 입력 정규화, evidence와 hash 생성
- Gemini 결과 검증, 캐시·비용·로그 관리

Gemini 책임:

- 추세·모멘텀·변동성·거래량을 평이한 문장으로 설명
- 상승 근거와 반대·충돌 신호를 함께 제시
- 모든 핵심 판단을 서버 evidence ID와 연결
- 데이터 한계와 비예측성을 설명

금지 범위:

- 지표 재계산·수정 또는 `summarySignal` 변경
- 미래 가격·수익률·목표주가·손절가 예측
- 직접 매수·매도·보유 명령과 수익 보장
- 입력에 없는 숫자·이벤트·기업 사실 생성
- 자체 신뢰도·확률 생성
- 뉴스·공시와 기술지표를 섞은 종합 투자 의견

향후 통합 브리핑을 추가한다면 뉴스 사실과 기술지표 해설을 구획하고 별도 featureType·promptVersion·cacheKey를 사용한다.

## 3. 요청과 서버 입력 스냅샷

### 3.1 요청

`POST /api/v1/ai/technical-explanations`

```json
{
  "symbol": "000660",
  "interval": "1D",
  "promptVersion": "technical-explanation-v1"
}
```

- USER와 ADMIN이 호출할 수 있다.
- `symbol`은 trim 후 대문자로 정규화하고 활성 종목만 허용한다.
- MVP interval은 `1D`만 허용한다.
- promptVersion 생략 시 서버 활성 버전을 사용한다.
- 클라이언트가 가격·지표·신호를 추가로 보내도 사용하지 않으며 등록되지 않은 필드는 거절하는 것을 기본으로 한다.

### 3.2 서버 재조회 입력

서버는 같은 읽기 스냅샷에서 다음을 구성한다.

- market, symbol, currency, interval
- latestRecordedAt, source, freshness, adjusted 여부
- calculationVersion, sampleCount, summarySignal
- 현재가와 MA5·MA20·MA60
- Wilder RSI14와 기준 30·70
- MACD·Signal·Histogram
- 볼린저 밴드 20·2와 bandwidthPercent
- ATR14와 atrPercent
- 현재 거래량·Volume MA20·평균 대비 배수
- 최근 교차 이벤트 type·occurredAt·관련 값

필수 지표가 없거나 OHLCV가 유효하지 않으면 Gemini를 호출하지 않는다. `DEMO`는 시연용으로 허용하되 응답과 화면에 표시한다. `STALE`을 허용할 경우 서버가 `dataLimitations`에 강제로 추가한다.

## 4. Evidence와 inputHash

서버는 의미 단위별로 `I1..In`을 순서대로 부여한다.

| 근거 그룹 | 예시 indicator |
|---|---|
| 현재가와 MA 값·관계 | `MOVING_AVERAGE` |
| Wilder RSI14와 경계 | `RSI` |
| MACD 세 값 | `MACD` |
| 볼린저 밴드와 폭 | `BOLLINGER_BANDS` |
| ATR과 현재가 대비 비율 | `ATR` |
| 거래량·MA20·배수 | `VOLUME` |
| 최근 교차 이벤트 | `TECHNICAL_EVENT` |
| 출처·시각·신선도 | `DATA_QUALITY` |

각 evidence는 다음 값을 가진다.

```json
{
  "id": "I1",
  "indicator": "MOVING_AVERAGE",
  "values": {
    "ma5": 270100,
    "ma20": 268900,
    "ma60": 261200
  },
  "displayValue": "MA5 270100 > MA20 268900 > MA60 261200"
}
```

JSON 객체 키 순서, 숫자 scale, UTC 시각, enum과 null 표현을 고정한 canonical JSON의 SHA-256을 `inputHash`로 사용한다. 입력 정규화 규칙 변경도 새로운 입력 버전으로 취급한다.

## 5. Gemini 프롬프트와 출력 계약

프롬프트는 서버 지시와 입력 JSON을 명확히 분리하고 입력을 명령이 아닌 데이터로 취급한다. temperature는 공통 AI 운영 정책의 낮은 값을 사용한다.

Gemini 출력 JSON:

```json
{
  "summary": "string",
  "trendExplanation": "string",
  "momentumExplanation": "string",
  "volatilityExplanation": "string",
  "volumeExplanation": "string",
  "supportingSignals": [
    { "text": "string", "evidenceIds": ["I1"] }
  ],
  "conflictingSignals": [
    { "text": "string", "evidenceIds": ["I2", "I3"] }
  ],
  "riskNotes": ["string"],
  "dataLimitations": ["string"]
}
```

| 필드 | 제한 |
|---|---|
| summary | 1~1,000자 |
| 네 explanation | 각각 1~500자 |
| supportingSignals | 1~5개, 항목 300자 이하 |
| conflictingSignals | 0~5개, 항목 300자 이하 |
| riskNotes | 1~5개, 항목 300자 이하 |
| dataLimitations | 0~5개, 항목 300자 이하 |
| evidenceIds | 항목당 1~4개, 입력 ID만 허용 |

## 6. 응답 계약

```json
{
  "analysisId": 801,
  "symbol": "000660",
  "interval": "1D",
  "latestRecordedAt": "2026-07-14T06:00:00Z",
  "source": "KIS",
  "freshness": "FRESH",
  "calculationVersion": "technical-v2-wilder",
  "promptVersion": "technical-explanation-v1",
  "inputHash": "64-character-sha256",
  "summarySignal": "BUY",
  "summary": "단기 이동평균과 MACD는 상승 흐름을 나타내지만 RSI가 과매수 구간에 가까워 단기 변동성을 함께 확인해야 합니다.",
  "trendExplanation": "MA5가 MA20과 MA60 위에 있어 단기 추세가 상대적으로 강합니다.",
  "momentumExplanation": "MACD 히스토그램은 양수지만 RSI는 70에 근접했습니다.",
  "volatilityExplanation": "ATR은 현재가의 1.57%입니다.",
  "volumeExplanation": "현재 거래량은 20일 평균의 1.50배입니다.",
  "supportingSignals": [
    { "text": "단기 이동평균이 중기 이동평균보다 높습니다.", "evidenceIds": ["I1"] }
  ],
  "conflictingSignals": [
    { "text": "상승 모멘텀과 달리 RSI는 과매수 기준에 가깝습니다.", "evidenceIds": ["I2", "I3"] }
  ],
  "riskNotes": ["기술지표는 미래 가격을 예측하지 않습니다."],
  "dataLimitations": [],
  "evidence": [
    { "id": "I1", "indicator": "MOVING_AVERAGE", "displayValue": "MA5 270100 > MA20 268900 > MA60 261200" }
  ],
  "modelName": "configured-model",
  "cacheHit": false,
  "inputTokens": 620,
  "outputTokens": 220,
  "estimatedCost": 0.000485,
  "costCurrency": "USD",
  "responseTimeMs": 780,
  "generatedAt": "2026-07-14T06:01:00Z",
  "disclaimer": "AI 해설과 기술적 신호는 투자 권유가 아닌 참고 정보입니다."
}
```

HIT 응답은 `cacheHit=true`, inputTokens·outputTokens·estimatedCost=0이며 원 modelName·generatedAt을 유지한다.

## 7. 서버 검증과 안전

JSON Schema 검증 후 다음 의미 검증을 수행한다.

- 모든 evidence ID가 실제 입력 집합에 존재한다.
- supportingSignals의 각 항목에는 근거가 하나 이상 있다.
- 출력 숫자는 입력 evidence의 값 또는 표시값에서 추적 가능하다.
- 지표·값·이벤트와 서버 `summarySignal`이 입력과 모순되지 않는다.
- 목표주가·수익률·미래 가격 확정·수익 보장·직접 매매 명령이 없다.
- 모델이 새 확률이나 신뢰도를 만들지 않는다.
- `STALE`·`DEMO` 입력의 한계가 응답에서 누락되지 않는다.

검증 실패 결과는 DB·Redis에 저장하지 않고 `502 AI_RESPONSE_INVALID`와 FAILED 사용량 로그를 남긴다. 금지 표현 탐지는 완벽한 투자자문 판별기로 가정하지 않고 구조화 입력·낮은 temperature·출력 길이 제한·화면 면책을 함께 사용한다.

## 8. 캐시와 무효화

```text
ai:technical-explanation:{market}:{symbol}:{interval}:{latestRecordedAt}:{calculationVersion}:{inputHash}:{promptVersion}
```

- demo는 메모리, 일반 프로필은 Redis 빠른 캐시를 사용한다.
- Redis MISS여도 같은 DB 결과가 있으면 모델을 호출하지 않고 복구한다.
- 새 완성 봉, 과거 OHLCV 정정, 기업행동 반영, calculationVersion·promptVersion·정규화 규칙 변경은 새 키를 만든다.
- TTL 만료만으로 같은 영속 결과를 재생성하지 않는다.
- 같은 키의 동시 MISS는 single-flight 또는 Redis 분산 락으로 모델 호출 한 번만 허용한다.
- HIT 사용량 로그의 savedEstimatedCost에는 원 생성 비용을 기록한다.

## 9. DB와 사용량 로그

### 9.1 ai_technical_explanations

| 컬럼 묶음 | 필드 |
|---|---|
| 대상 | id, stock_id, interval, latest_recorded_at |
| 입력 버전 | calculation_version, prompt_version, input_hash, source, freshness, summary_signal |
| 해설 | summary, trend_explanation, momentum_explanation, volatility_explanation, volume_explanation |
| 구조화 목록 | supporting_signals, conflicting_signals, risk_notes, data_limitations, evidence JSONB |
| 생성 정보 | model_name, input_tokens, output_tokens, estimated_cost, cache_key, generated_at |

유일성:

```text
(stock_id, interval, latest_recorded_at, calculation_version, input_hash, prompt_version)
(cache_key)
```

원시 OHLCV 배열, 전체 프롬프트와 전체 공급자 응답은 저장하지 않는다.

### 9.2 ai_usage_logs

- featureType: `TECHNICAL_EXPLANATION`
- targetType: `STOCK`
- targetId: `stockId`
- technicalExplanationId: 성공 결과 FK, 실패 시 null
- 뉴스와 같은 token·cost·cacheHit·savedCost·status·errorCode 규칙 사용
- 전체 스냅샷·프롬프트·응답 전문과 API 키를 로그에 남기지 않음

관리자 지표는 `NEWS_SUMMARY`와 `TECHNICAL_EXPLANATION`을 분리 집계한다.

## 10. UI 계약

종목 상세에 `AI 기술 분석 해설` 카드를 제공한다.

- 실행 전 안내와 분석 버튼
- 생성 중 상태와 중복 클릭 방지
- MISS/HIT, 모델, 기준 시각, 계산·프롬프트 버전
- 요약, 추세·모멘텀·변동성·거래량 설명
- 상승 근거와 충돌 신호를 같은 비중으로 표시
- 설명에서 실제 지표 evidence를 펼치거나 이동하는 동작
- source, FRESH/STALE/DEMO와 데이터 한계
- 오류 유형과 재시도 가능 여부
- AI 생성 해설과 기술적 신호가 투자 권유가 아니라는 고지

색상만으로 긍정·위험·충돌을 구분하지 않는다. 캐시 결과의 latestRecordedAt·inputHash가 현재 화면 스냅샷과 다르면 표시하지 않는다.

## 11. 오류 계약

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | VALIDATION_ERROR | symbol·promptVersion 형식 오류 또는 등록되지 않은 필드 |
| 400 | AI_PROMPT_VERSION_UNSUPPORTED | 등록되지 않은 프롬프트 버전 |
| 400 | TECHNICAL_INTERVAL_UNSUPPORTED | `1D` 이외 간격 |
| 404 | STOCK_NOT_FOUND | 종목 없음 또는 비활성 |
| 422 | TECHNICAL_DATA_INSUFFICIENT | 완성 봉 또는 필수 지표 부족 |
| 422 | TECHNICAL_SNAPSHOT_INVALID | 스냅샷 품질·버전·신선도 판정 불가 |
| 429 | AI_RATE_LIMITED | 사용자·공급자 한도 초과 |
| 429 | AI_BUDGET_EXCEEDED | 서비스 비용 한도 도달 |
| 502 | AI_RESPONSE_INVALID | 스키마·근거·금지 출력 검증 실패 |
| 502 | EXTERNAL_PROVIDER_ERROR | 재시도 후 공급자 실패 |
| 503 | AI_REQUEST_IN_PROGRESS | 같은 키 생성 대기 초과 |
| 504 | AI_PROVIDER_TIMEOUT | 재시도 후 타임아웃 |

Gemini 장애는 이 엔드포인트만 실패시키며 결정론적 기술지표와 차트 조회는 유지한다.

## 12. 구현 순서

1. DTO, canonical snapshot과 evidence 생성기
2. `technical-explanation-v1` Mock·Gemini 공급자 계약
3. 구조·근거·숫자·금지 출력 검증기
4. DB migration과 repository
5. Redis/DB cache, single-flight와 사용량 로그
6. API와 표준 오류 매핑
7. 종목 상세 해설 카드
8. 관리자 기능별 비용 분리
9. 단위·통합·공급자 계약·E2E 테스트

## 13. 인수 조건

- [ ] 클라이언트가 조작한 지표를 보내도 서버 재조회 스냅샷만 사용한다.
- [ ] 같은 완성 일봉·계산 버전·inputHash·프롬프트의 첫 요청은 MISS, 두 번째는 HIT다.
- [ ] 동시 같은 MISS에서도 실제 Gemini 호출과 DB 결과는 하나다.
- [ ] Redis 키 삭제 후 DB에서 복구하고 모델을 다시 호출하지 않는다.
- [ ] 새 일봉, 과거 정정, 계산·프롬프트·정규화 버전 변경은 새 MISS를 만든다.
- [ ] 존재하지 않는 evidence ID, 입력에 없는 숫자와 모순된 이벤트는 저장되지 않는다.
- [ ] 목표주가·수익률 예측·직접 매매 명령 fixture를 성공 결과로 노출하지 않는다.
- [ ] 상승 근거와 충돌 신호가 실제 evidence로 연결된다.
- [ ] STALE·DEMO 상태와 데이터 한계를 숨기지 않는다.
- [ ] 뉴스와 기술지표 해설의 사용량·비용·캐시 절감액이 분리 집계된다.
- [ ] Gemini 장애 중에도 기술지표와 차트가 정상 조회된다.
- [ ] 데스크톱·모바일·키보드에서 핵심 해설과 근거를 확인할 수 있다.

미완료 항목이 남아 있으면 `TECHNICAL_EXPLANATION`을 구현 완료로 표시하지 않는다.
