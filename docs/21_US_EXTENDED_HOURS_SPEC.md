# 미국 주식 확장시간 시세 명세

작성 기준일: 2026-08-01
상태: 로컬 배포 후보 PASS, 실제 Finnhub 확장장 entitlement 증적 대기

## 1. 목적과 지원 범위

FinWatch가 Finnhub 미국 주식 체결을 프리마켓·정규장·애프터마켓으로 구분하고, 공급자 시각이 없는 값을 실시간처럼 표시하지 않기 위한 계약이다.

현재 MVP 범위는 `NASDAQ`, `NYSE`의 주식·ETF와 미국 동부 day session이다. 이 상태는 사용자가 특정 거래소나 브로커에서 주문할 수 있다는 뜻이 아니다. 옵션·채권·암호화폐와 야간 세션은 제외한다.

공식 기준:

- [Nasdaq Equity 1](https://listingcenter.nasdaq.com/rulebook/nasdaq/rules/nasdaq-equity-1): Business Day의 프리마켓 04:00~09:30, 정규장 09:30~16:00, post-market 16:00~20:00 ET
- [Nasdaq 미국 시장 휴장 일정](https://www.nasdaq.com/market-activity/stock-market-holiday-schedule): 연도별 휴장·조기 종료 일정
- [NYSE 거래시간·캘린더](https://www.nyse.com/trade/hours-calendars): 연도별 휴장·조기 종료와 거래소별 세션
- [Finnhub WebSocket Trades](https://finnhub.io/docs/api/websocket-trades): `t`는 공급자 Unix millisecond timestamp이고 한 frame에 여러 trade가 올 수 있음

KIS 연결 계약:

- 국내·해외 상품을 합쳐 WebSocket 한 세션만 사용한다. `H0STCNT0`/NXT 국내 체결과 `HDFSCNT0` 해외 체결을 동일 연결에서 구독한다.
- 국내·해외 KIS 구독 수 합계는 40개 이하로 제한하고, 한도를 넘는 미국 종목은 Finnhub 스트림을 fallback으로 사용한다.
- `invalid approval` 또는 접속키 거절 응답을 받으면 캐시된 승인키를 폐기하고 단일 세션 전체를 새 승인키로 재연결한다.

거래소가 23/5 또는 야간 세션 규칙을 공표했더라도 실제 개시일·대상 종목·Finnhub entitlement를 검증하기 전에는 20:00~다음 날 04:00 ET 체결을 `AFTER_HOURS`로 확대 해석하지 않는다.

## 2. 단계별 범위와 완료 표현

### 2.1 이번 배포 후보 범위

다음 항목만 이번 배포 후보의 수용 범위다. 테스트 결과를 확인하기 전에는 완료로 표시하지 않는다.

1. 정상 월요일~금요일을 `America/New_York`로 변환해 `PRE_MARKET`, `REGULAR`, `AFTER_HOURS`, `CLOSED`로 판정한다.
2. 토요일·일요일은 `CLOSED`, null timestamp는 `UNKNOWN`으로 판정한다.
3. Finnhub stock trade는 양수인 공급자 `t`가 있어야 수용하며, 5초 초과 미래값과 수신 시점보다 2분 넘게 오래된 값을 폐기한다.
4. quote와 WebSocket 1분 봉이 같은 `sessionStatus`를 보존한다.
5. 관심종목·상세·차트가 공급자 코드나 `TICK` 대신 장전·정규장·애프터마켓 상태를 표시한다.
6. 미국 전체 04:00~20:00 ET를 담을 수 있도록 종목별 1분 봉 1000개를 보존하고 미국 상세 조회도 limit 1000을 사용한다.
7. 현재 알림 기본 정책은 세 활성 세션을 모두 평가하되 `CLOSED|UNKNOWN` 체결은 quote hub 전에 폐기한다.

이번 단계의 canonical 코드값은 다음 다섯 개뿐이다.

```text
PRE_MARKET | REGULAR | AFTER_HOURS | CLOSED | UNKNOWN
```

기존 `LIVE`, `SNAPSHOT` 값은 KRX와 REST 호환 경로에 남을 수 있으나 미국 Finnhub WebSocket의 시장 세션 코드로 새로 생성하지 않는다.

### 2.2 후속 하드닝 범위

다음 항목은 이번 배포의 완료 주장에 포함하지 않는다.

- 버전된 미국 휴장·조기 종료 캘린더
- 주중 공식 휴장과 주말을 구분하는 `closureReason`
- 현재 시장 상태와 마지막 가격이 속한 세션의 분리
- `sessionStatus`와 `feedStatus=STREAMING|SNAPSHOT|DISCONNECTED` 분리
- `fetchedAt`, `evaluatedAt`, `freshness`, `delaySeconds`, `calendarVersion` API 확장
- 무체결·연결 종료·세션 경계를 재평가하는 stale scheduler
- 정규 종가 기준의 세션별 등락률, 확장시간 volume·알림·포트폴리오 정책
- 공식 halt feed와 야간/23x5 세션

## 3. 현재 코드 감사 기준

| 항목 | 배포 후보에서 확인할 내용 | 남은 한계 |
|---|---|---|
| 미국장 분류 | `America/New_York`와 정상 영업일 반개구간 | 휴장·조기 종료 캘린더 없음 |
| Finnhub timestamp | stock trade의 `t` 필수, 미래 5초·과거 2분 초과 폐기 | REST quote timestamp·관측 지연 필드 미분리 |
| quote 순서 | 오래된 `asOf`가 최신값을 덮지 않음 | 같은 millisecond 다중 trade의 영속 dedupe 없음 |
| 1분 봉 | pre/regular/after 상태 보존, closed/snapshot 제외 | 세션별 volume·재시작 복구 없음 |
| 사용자 화면 | 장전·정규장·애프터마켓 badge | 휴장 이유·stale 상태 미분리 |

## 4. 정상 영업일 세션 판정

### 4.1 공통 원칙

1. 서버 OS 시간대가 아니라 `America/New_York` IANA zone을 사용한다. EST/EDT offset을 상수로 두지 않는다.
2. Finnhub `t`를 UTC `Instant`로 보존한 뒤 미국 동부 현지 날짜·시간으로 변환한다.
3. 구간은 시작 시각 포함, 종료 시각 미포함의 반개구간 `[start, end)`이다.
4. 이번 배포 후보에서 토요일·일요일은 `CLOSED`, timestamp가 없거나 판정할 수 없으면 `UNKNOWN`이다.

| 미국 동부 현지 시각 | `sessionStatus` |
|---|---|
| 00:00 이상 04:00 미만 | `CLOSED` |
| 04:00 이상 09:30 미만 | `PRE_MARKET` |
| 09:30 이상 16:00 미만 | `REGULAR` |
| 16:00 이상 20:00 미만 | `AFTER_HOURS` |
| 20:00 이상 24:00 미만 | `CLOSED` |

경계 예시는 09:29:59.999가 `PRE_MARKET`, 09:30:00.000이 `REGULAR`, 16:00:00.000이 `AFTER_HOURS`, 20:00:00.000이 `CLOSED`다.

### 4.2 DST와 날짜 경계

- 겨울 09:30 ET는 14:30 UTC, 여름 09:30 ET는 13:30 UTC다.
- DST 전환 전 금요일과 전환 후 월요일을 함께 테스트해 고정 UTC offset이 섞이지 않음을 검증한다.
- 20:00 ET가 UTC로 다음 날짜가 되어도 미국 동부의 거래 날짜로 판정한다.

## 5. 공급자 timestamp 수용 기준

1. Finnhub stock WebSocket trade는 양수인 `t`가 있어야 수용한다. 누락·0·파싱 실패 시 서버 `Instant.now()`로 대체하지 않는다.
2. `t`는 Unix millisecond다. 초 단위 오해로 비정상 미래·과거 시각을 만들지 않는다.
3. 기본 `maxFutureSkew=5s`를 넘는 미래 체결과 `maxTradeAge=2m`를 넘는 과거 체결은 폐기한다.
4. 종목별로 더 오래된 `asOf`는 최신 quote를 덮지 못한다. 완전히 같은 quote 재수신은 이벤트를 다시 발행하지 않는다.
5. 같은 millisecond의 서로 다른 trade는 수신 순서를 보존해 마지막 trade를 현재가로 삼는다. 재연결 뒤 봉 거래량·알림의 중복을 방지하는 영속 fingerprint는 후속 하드닝이다.
6. `CLOSED|UNKNOWN`으로 판정된 trade는 quote hub에 게시하지 않아 가격·1분 봉·알림 평가에 사용하지 않는다.

## 6. 이번 배포 후보 API·WS 계약

미국 Finnhub WebSocket quote와 그 quote로 생성한 1분 봉은 같은 canonical `sessionStatus`를 전달한다.

```json
{ "type": "quote", "data": {
  "market": "NASDAQ",
  "symbol": "AAPL",
  "price": 215.42,
  "asOf": "2026-07-30T21:15:03.482Z",
  "source": "FINNHUB_WS",
  "sessionStatus": "AFTER_HOURS"
} }
{ "type": "candle", "data": {
  "market": "NASDAQ",
  "symbol": "AAPL",
  "time": "2026-07-30T21:15:00Z",
  "close": 215.42,
  "source": "FINNHUB_WS",
  "sessionStatus": "AFTER_HOURS"
} }
```

일반 사용자 화면은 `PRE_MARKET=장전`, `REGULAR=정규장`, `AFTER_HOURS=애프터마켓`으로 표시한다. `CLOSED`는 새 streaming 상태처럼 강조하지 않고, `UNKNOWN`은 실시간이라고 표현하지 않는다.

## 7. 후속 휴장·조기 종료 수용 기준

이 절은 목표 기준이며 현재 구현 완료 범위가 아니다.

1. 시장·연도별로 승인된 거래 캘린더를 조회한다. 캘린더를 확인할 수 없으면 월요일~금요일이라도 `UNKNOWN`이다.
2. 토요일·일요일과 주중 공식 휴장 모두 `sessionStatus=CLOSED`다. 후속 `closureReason`으로 각각 `WEEKEND`, `HOLIDAY`를 구분한다.
3. 공식 휴장일에 도착한 trade를 정상 `REGULAR`로 승격하지 않고 공급자 이상 이벤트로 격리한다.
4. 조기 종료일은 캘린더의 `regularClose`와 `extendedClose`를 사용한다. 정규장은 09:30부터 `regularClose` 직전, 애프터마켓은 `regularClose`부터 `extendedClose` 직전이다.
5. 2026-11-27과 2026-12-24처럼 정규장이 13:00 ET에 종료되는 fixture를 둔다. extended close는 시장별 캘린더 값이며 17:00 또는 20:00을 일반 상수로 고정하지 않는다.
6. 거래정지는 휴장과 다르다. 공식 halt feed가 없으면 체결 부재만으로 `HALTED`를 만들지 않는다.

## 8. 후속 stale·상태 분리 기준

이 절도 현재 구현 완료 범위가 아니다.

- `asOf`: 공급자 체결 시각
- `fetchedAt`: FinWatch 수신 시각
- `evaluatedAt`: 현재 시장 상태와 freshness를 계산한 시각
- `priceSession`: 마지막 가격의 세션
- `sessionStatus`: `evaluatedAt` 현재 시장 세션
- `feedStatus`: `STREAMING`, `SNAPSHOT`, `DISCONNECTED`, `UNAVAILABLE`
- `freshness`: `FRESH`, `DELAYED`, `STALE`, `UNAVAILABLE`

기본값은 `freshGrace=60s`, `staleAfter=30m`로 한다.

| 조건 | freshness |
|---|---|
| 활성 세션, 실시간 권한, `evaluatedAt-asOf <= 60s` | `FRESH` |
| 공표 지연 상품 또는 60초 초과·30분 이하 | `DELAYED` |
| 활성 세션에서 30분 초과 | `STALE` |
| 수용 가능한 마지막 값 없음 | `UNAVAILABLE` |

- 공표 지연 상품은 기대 지연 안이어도 `FRESH`로 표시하지 않는다.
- 월요일 프리마켓 시작 시 마지막 값이 직전 거래일 값이면 `STALE`이다.
- `CLOSED`에서는 가장 최근 완료 세션의 공식 종가를 경과 시간만으로 stale 처리하지 않는다. 최신 공식 거래일 종가가 없거나 조기 종료 후 2시간이 지나도 최종 가격이 없을 때 stale이다.
- 세션 경계에는 새 trade가 없어도 현재 `sessionStatus`를 갱신해야 한다. 마지막 quote의 `priceSession`과 현재 `sessionStatus`는 다를 수 있다.

## 9. 회귀 테스트 목록

### 9.1 이번 배포 후보 필수 테스트

1. 여름 정상 영업일의 04:00·09:30·16:00·20:00 ET 전후 경계.
2. 겨울·여름 UTC 변환, 토요일·일요일 `CLOSED`, null `UNKNOWN`.
3. Finnhub `t` millisecond 보존, 누락 timestamp 거부, 5초 초과 미래값·2분 초과 과거값 거부.
4. 과거 quote 역행 차단과 동일 quote 중복 이벤트 억제.
5. pre/regular/after quote와 1분 봉의 `sessionStatus` 일치, REST snapshot·`CLOSED|UNKNOWN` quote의 가격·봉·알림 제외.
6. 종목별 1000봉 보존과 미국 API limit 1000으로 정상 확장 세션 960분 유지.
7. REST/WS 직렬화에서 quote와 candle의 `sessionStatus` 보존.
8. 관심종목·상세·차트에서 세 세션 badge가 바르게 표시되고 공급자 코드·`TICK` 노이즈가 없음.
9. 세션 quote 갱신이 카드·차트 전체 재마운트나 깜빡임을 만들지 않음.

### 9.2 후속 하드닝 테스트

1. 공식 주중 휴장 `CLOSED + closureReason=HOLIDAY`, 미지원 캘린더 `UNKNOWN`.
2. 2026-11-27·2026-12-24 조기 종료와 시장별 extended close.
3. 봄·가을 DST 전환 전후 영업일과 캘린더 version rollover.
4. `FRESH` 60초·`DELAYED`·`STALE` 30분 경계, delayed entitlement.
5. 휴장 중 최신 공식 종가 유지와 활성 세션 시작 뒤 이전 거래일 값 stale 전환.
6. trade 없는 세션 경계 status event와 reconnect snapshot 복구.
7. 확장시간 등락률 baseline·volume·포트폴리오·AI·알림 session 정책.
8. 실제 Finnhub entitlement로 프리마켓·애프터마켓 대표 종목 각 1개의 provider `t`와 지연 증적 저장.

## 10. 남은 제품 보완점 우선순위

| 우선순위 | 보완점 | 완료 기준 |
|---|---|---|
| P0 | Finnhub 요금제·extended-hours 권한 검증 | 실제 장전·애프터 체결 `t`와 공표 지연 증적 확보; 미확인 시 실시간 문구 금지 |
| P1 | 버전된 미국 거래 캘린더 | 휴장·조기 종료·DST 자동 테스트, 캘린더 미확인 `UNKNOWN` |
| P1 | `sessionStatus`·`feedStatus`·freshness 분리 | 장 상태·연결·데이터 나이가 독립적으로 갱신됨 |
| P1 | stale scheduler와 세션 경계 event | 무체결·연결 종료·장 경계에서도 상태 자동 갱신 |
| P1 | 정규 종가 baseline과 세션별 봉·거래량 | 장전/시간외 등락률과 volume이 정규장 데이터와 섞이지 않음 |
| P1 | 포트폴리오·AI·알림 정책 | 확장시간 평가 표시, stale 한계, 알림 session opt-in 확정 |
| P2 | 공식 halt feed·corporate action | 거래정지와 무체결 구분, 분할일 baseline 보정 |
| P2 | 야간/23x5 확장 | 실제 개시·권한·대상 확인 후 별도 세션 코드·테스트 승인 |

로컬 배포 후보 테스트는 2026-08-01 통과했다. 실제 Finnhub 권한의 장전·애프터 체결은 주말이라 관측할 수 없었으므로 다음 거래일에 provider `t`와 공표 지연을 증적으로 남기기 전까지는 “실제 확장장 수신 검증 완료”라고 표시하지 않는다.
