# FinWatch 내비게이션·뉴스·공시 화면 명세

상태: v0.4 — 뉴스·공시 메뉴 분리와 AI 뉴스 분석 중심 화면 구현 완료

기준일: 2026-07-17

대상: React PWA 앱 셸, route 기반 화면 분리, AI 뉴스 분석, 공시 목록·요약

구현 기록(2026-07-15): `GET /api/v1/content-feed`, filter·기간·page·sort validation, 안정 정렬, AI 상태/preview, V18 인덱스와 회귀 테스트를 구현했다. React Router 8 기반 route tree, Auth/Admin guard, 데스크톱 Sidebar·Topbar, 모바일 Bottom Navigation·focus trap drawer, 종목 상세 4개 탭과 URL 기반 통합 콘텐츠 화면도 구현했다. Playwright 회귀 13개와 production Service Worker offline route 1개를 분리 자동화했으며 Docker Desktop이 필요한 100,000건 PostgreSQL 성능 실행과 실제 설치형 standalone·수동 screen reader 확인은 최종 인수 항목으로 남긴다.

UI 변경 기록(2026-07-17): 사용자 화면에서는 통합 콘텐츠 목록을 제거하고 `뉴스`와 `공시`를 독립 메뉴·route·종목 탭으로 분리했다. 뉴스 화면 최상단에는 `AI 뉴스 분석`만 표시하고 별도의 일반 뉴스 피드는 렌더링하지 않는다. `/content`와 종목별 기존 `/content`는 각각 대응 뉴스 route로 이동한다. `NAVER_API_HUB`, `FINNHUB` 같은 내부 공급자 코드는 뉴스 화면에 노출하지 않으며, 원문 URL을 기준으로 실제 언론사명을 표시한다. 기존 `/api/v1/content-feed`는 API 호환성과 관리자·향후 기능을 위해 유지한다. 이 변경 기록과 아래 갱신된 route·페이지 책임이 이전 통합 목록 UI 설명보다 우선한다.

## 1. 목적과 현재 상태

기존 프런트엔드는 `App.tsx` 한 페이지에 모든 기능을 세로로 렌더링하고 anchor·수동 history를 사용했다. 현재는 아래 정보 구조와 route 계약으로 전환했으며 기존 hash URL만 한 release 호환 변환한다.

현재 뉴스 API는 종목별 전체 배열을 최신순으로 반환한다. 뉴스와 공시 데이터가 늘어나면 초기 렌더링, 탐색, 필터 유지와 모바일 사용성이 나빠지므로 다음 구조로 전환한다.

```text
인증
→ 반응형 App Shell
   ├─ 데스크톱 접이식 Sidebar
   ├─ 공통 Topbar
   ├─ Route Outlet
   └─ 모바일 Bottom Navigation + More Drawer

콘텐츠 조회
→ URL filter 해석
→ 서버 pagination·정렬·필터
→ 뉴스/공시 공통 목록
→ 권리·AI 분석 가능 상태 표시
```

목표는 기능을 줄이는 것이 아니라 현재 구현된 모든 기능을 사용 목적에 맞는 페이지로 이동해 실제 투자 서비스와 같은 정보 구조를 만드는 것이다.

## 2. 범위와 제외 범위

포함:

- 데스크톱 접이식 왼쪽 사이드바
- 모바일 PWA 하단 내비게이션과 추가 메뉴 drawer
- 공통 topbar의 종목 검색·환율·연결·사용자 상태
- route 기반 페이지 전환, 새로고침·뒤로가기·공유 URL 복원
- 종목 상세 내부 탭
- 뉴스·공시 공통 콘텐츠 페이지
- 서버 page 기반 pagination, 필터·정렬과 DB 인덱스
- URL query parameter 기반 목록 상태 보존
- 독립 loading·empty·partial·error·retry 상태
- 접근성, 반응형, PWA navigation fallback과 E2E

제외:

- 디자인 시스템 전면 교체
- Next.js·SSR 도입
- MSA나 별도 검색 엔진 도입
- 뉴스 무한 스크롤
- 사용 권한이 없는 뉴스 본문 수집·표시·AI 분석
- 기존 기능의 API·계산 규칙 변경
- 소셜 기능, 개인화 추천 피드와 광고

## 3. 정보 구조

### 3.1 데스크톱 Sidebar

기본 너비는 240px, 접힘 상태는 72px를 목표로 한다. 너비는 최종 CSS에서 4px 단위로 조정할 수 있지만 메뉴 이름과 계층은 유지한다.

```text
FinWatch

홈
  대시보드

시장
  종목 탐색
  뉴스·공시

내 투자
  관심종목
  포트폴리오
  가격 알림

운영  [ADMIN only]
  AI 사용량
  데이터 수집 상태

하단
  PWA 설치
  사용자·로그아웃
```

규칙:

- 현재 route 메뉴는 `aria-current="page"`와 시각적 active 상태를 함께 사용한다.
- 접힘 상태에서도 아이콘만으로 의미를 추측하게 하지 않고 tooltip과 접근 가능한 이름을 제공한다.
- ADMIN 메뉴는 USER DOM에 렌더링하지 않으며 서버 API도 403을 유지한다.
- Sidebar 접힘 상태는 `localStorage`에 UI preference로만 저장한다. 인증·선택 종목 같은 업무 상태는 저장하지 않는다.
- 메뉴 수가 viewport보다 길면 Sidebar 내부만 스크롤하고 본문 스크롤과 충돌하지 않는다.

### 3.2 Topbar

Topbar에는 다음만 유지한다.

- 전역 종목 검색
- 선택 종목 간단 표시 또는 현재 페이지 제목
- USD/KRW compact ticker
- API·실시간 연결 상태
- 사용자 메뉴

데스크톱에서 PWA 설치 버튼은 Sidebar 하단 또는 사용자 메뉴 중 한 곳에만 표시한다. 작은 화면에서는 중요도가 낮은 연결 세부 문구를 상태 아이콘·접근 가능한 tooltip으로 축약한다.

### 3.3 모바일 PWA

390px 기준 하단 내비게이션:

```text
[홈] [검색] [관심종목] [뉴스·공시] [더보기]
```

`더보기` drawer:

- 포트폴리오
- 가격 알림
- AI/데이터 관리자 메뉴(ADMIN)
- PWA 설치 상태
- 사용자와 로그아웃

규칙:

- Bottom Navigation은 safe-area inset을 반영한다.
- drawer가 열리면 배경을 inert 처리하고 focus trap·Esc 닫기·trigger focus 복귀를 제공한다.
- 모바일에서 데스크톱 Sidebar를 화면 밖에 숨겨 중복 탐색 landmark가 생기지 않게 한다.
- 하단 메뉴가 차트 도구·pagination·toast를 가리지 않도록 본문 bottom padding을 확보한다.

## 4. Route 계약

### 4.1 기술 선택

현재 수동 `window.history` 처리를 route library로 교체한다. 구현 시점의 React 19·Vite 호환성을 확인해 React Router `8.2.0`을 정확 버전으로 고정하고 lockfile을 함께 갱신한다. React Router v8은 DOM API를 `react-router`로 통합했으므로 제거된 `react-router-dom`을 새로 설치하지 않는다. 기준은 [공식 declarative 설치 문서](https://reactrouter.com/start/declarative/installation)와 [v8 변경 기록](https://reactrouter.com/start/start/changelog)이다.

### 4.2 Route 표

| Route | 화면 | 권한 |
|---|---|---|
| `/login` | 이메일/향후 소셜 로그인 | PUBLIC |
| `/dashboard` | 요약 대시보드 | USER, ADMIN |
| `/stocks` | 종목 통합 검색·최근/관심 종목 | USER, ADMIN |
| `/stocks/{market}/{symbol}` | 종목 개요 | USER, ADMIN |
| `/stocks/{market}/{symbol}/technical` | 상세 차트·기술지표·AI 기술 해설 | USER, ADMIN |
| `/stocks/{market}/{symbol}/news` | 해당 종목 AI 뉴스 분석 | USER, ADMIN |
| `/stocks/{market}/{symbol}/disclosures` | 해당 종목 공시 목록·AI 요약 | USER, ADMIN |
| `/stocks/{market}/{symbol}/content` | 종목 뉴스 route로 호환 이동 | USER, ADMIN |
| `/stocks/{market}/{symbol}/briefing` | 일일 변화·AI 브리핑 | USER, ADMIN |
| `/content` | `/news`로 호환 이동 | USER, ADMIN |
| `/news` | 현재 선택 종목 AI 뉴스 분석 | USER, ADMIN |
| `/disclosures` | 현재 선택 종목 공시 목록·AI 요약 | USER, ADMIN |
| `/watchlist` | 관심종목 관리 | USER, ADMIN |
| `/portfolio` | 포트폴리오·환산 평가 | USER, ADMIN |
| `/alerts` | 가격 알림 | USER, ADMIN |
| `/admin/ai` | AI 사용량·비용 | ADMIN |
| `/admin/data` | 공급자·동기화·freshness | ADMIN |
| `*` | Not Found와 안전한 이동 | 인증 상태에 따름 |

### 4.3 canonical·호환 규칙

- `/`는 인증 사용자면 `/dashboard`, 비인증이면 `/login`으로 이동한다.
- 기존 `/stocks/{market}/{symbol}`은 종목 개요 canonical URL로 유지한다.
- 기존 `/content`는 `/news`, `/stocks/{market}/{symbol}/content`는 같은 종목의 `/news`로 `replace` 이동한다.
- 기존 `#dashboard`, `#watchlist`, `#news`, `#admin` 진입은 한 release 동안 대응 route로 치환한다.
- symbol과 market은 decode·형식 검증 후 대문자로 정규화한다.
- 잘못된 market/symbol은 기본 종목으로 몰래 바꾸지 않고 404/invalid state를 표시한다.
- 로그아웃·401은 사용자별 query cache와 선택 상태를 폐기하고 `/login?returnTo=...`로 이동할 수 있다.
- 로그인 후 `returnTo`는 same-origin 허용 route만 복원해 open redirect를 막는다.

### 4.4 PWA·서버 fallback

- production server는 API·정적 파일이 아닌 허용된 앱 route 요청에 `index.html`을 반환한다.
- `/api/**`, manifest, service worker와 asset 404를 index.html로 바꾸지 않는다.
- Service Worker는 navigation에서 app shell/offline shell을 제공할 수 있지만 `/api/**` 응답은 cache하지 않는다.
- 직접 URL 진입, 설치형 standalone 실행, 새 버전 활성화 후에도 route가 보존돼야 한다.

## 5. 페이지 책임

### 5.1 대시보드

대시보드는 전체 기능을 모두 렌더링하지 않고 요약과 다음 행동을 제공한다.

- 관심종목 상위 항목과 실시간 상태
- 포트폴리오 기준통화 총 평가액·통화별 요약
- USD/KRW compact card
- 활성·발동 알림 요약

각 카드의 `전체 보기`는 해당 route로 이동한다. 카드 하나의 오류가 대시보드 전체를 실패시키지 않는다.

### 5.2 종목 상세 탭

탭과 route를 일치시킨다.

| 탭 | Route | 포함 기능 |
|---|---|---|
| 개요 | `/stocks/{market}/{symbol}` | 현재가, 기업/시장 정보, 관심종목 동작, 핵심 요약 |
| 차트·기술분석 | `.../technical` | Lightweight Charts, 지표, AI 기술 해설 |
| 뉴스 | `.../news` | 선택 종목 뉴스 선택과 AI 뉴스 분석 |
| 공시 | `.../disclosures` | 선택 종목 공식 공시 목록과 AI 요약 |
| AI 브리핑 | `.../briefing` | 일일 변화 브리핑, 근거 drawer·감사 카드 |

탭 전환 시 종목 식별자는 route parameter가 source of truth다. 컴포넌트별로 별도 `selectedSymbol`을 유지하거나 고정 symbol을 사용하지 않는다.

### 5.3 뉴스·공시 분리 페이지

뉴스 화면은 `AI 뉴스 분석` 카드를 첫 콘텐츠로 표시한다. 카드 내부에서 종목 뉴스를 선택하고 원문 링크를 확인한 뒤 Gemini 분석을 요청한다. 일반 뉴스 피드나 통합 콘텐츠 목록은 함께 렌더링하지 않는다.

공시 화면은 공식 공시 목록과 공시 AI 요약을 표시한다. 뉴스와 공시는 데스크톱 Sidebar, 모바일 내비게이션, 종목 상세 탭에서 각각 독립된 메뉴로 접근한다.

뉴스의 사용자 표시 출처는 다음 우선순위를 따른다.

1. 공급자가 제공한 실제 언론사명
2. 원문 URL 도메인에 대응하는 언론사명
3. 알 수 없는 경우 정리된 원문 도메인

`NAVER_API_HUB`, `FINNHUB`, `Naver News` 같은 수집 경로·placeholder는 사용자 화면에서 언론사명으로 표시하지 않는다.

아래 통합 목록 필터·페이지 계약은 유지되는 `/api/v1/content-feed`의 백엔드 호환 계약이며 현재 사용자 뉴스·공시 화면에는 직접 노출하지 않는다.

상단 탭:

```text
[전체] [뉴스] [공시] [AI 분석 가능] [AI 분석 완료]
```

필터:

- 검색어: 제목·publisher, 2자 이상
- 시장: ALL / KRX / NASDAQ / NYSE
- 종목: `(market, symbol)` pair
- 기간: 24H / 7D / 1M / 3M / 직접 선택
- 출처
- 콘텐츠 종류: ALL / NEWS / DISCLOSURE
- 권리·분석 상태: ALL / METADATA_ONLY / AI_ALLOWED / AI_COMPLETED
- 정렬: 최신순 기본, 오래된순 선택

목록 항목:

- 제목, 종목명·symbol·market
- publisher/source, publishedAt의 사용자 현지 표시
- NEWS/DISCLOSURE badge와 공시 유형
- `원문 링크만`, `공식 공시`, `AI 분석 가능`, `AI 분석 완료` 상태
- AI 결과가 있으면 짧은 핵심 문장만 표시하고 상세는 별도 panel/dialog 또는 상세 route
- source·rights가 허용하지 않으면 요약 버튼을 렌더링하지 않음

NAVER·Finnhub 메타데이터를 공식 공시·본문 분석 결과와 같은 상태로 표시하지 않는다. 권리 정책은 `06_DATA_PROVIDER_SPEC.md`와 `08_AI_OPERATION_SPEC.md`를 따른다.

## 6. URL 상태 계약

목록 상태의 source of truth는 URL query parameter다.

예시:

```text
/content?kind=DISCLOSURE&market=KRX&symbol=005930&period=1M&analysis=AI_ALLOWED&page=2&size=20&sort=publishedAt,desc
```

| parameter | 기본값 | 규칙 |
|---|---|---|
| kind | ALL | ALL / NEWS / DISCLOSURE |
| market | ALL | 서버 허용 market |
| symbol | 없음 | market과 함께 사용 |
| q | 없음 | trim, 최대 100자 |
| period | 7D | 24H / 7D / 1M / 3M / CUSTOM |
| from, to | 없음 | CUSTOM이면 필수, ISO date |
| source | ALL | 허용 source enum 또는 값 목록 |
| analysis | ALL | ALL / METADATA_ONLY / AI_ALLOWED / AI_COMPLETED |
| page | 0 | API는 0-base, UI는 1-base 표시 |
| size | 20 | 10 / 20 / 50, 최대 50 |
| sort | `publishedAt,desc` | whitelist만 허용 |

규칙:

- 필터가 바뀌면 page=0으로 초기화한다.
- page만 바뀔 때 필터와 sort를 보존한다.
- 잘못된 query는 서버 호출 전에 기본값으로 조용히 왜곡하지 않고 교정 가능한 validation 안내를 표시한다.
- URL에는 토큰, 이메일, 원문 콘텐츠와 개인 민감정보를 넣지 않는다.
- 브라우저 뒤로가기 시 이전 필터·page·scroll position을 복원한다.

## 7. 서버 Pagination API

### 7.1 신규 공통 endpoint

`GET /api/v1/content-feed`

요청 예시:

```text
GET /api/v1/content-feed?kind=ALL&market=KRX&symbol=005930&period=1M&analysis=AI_ALLOWED&page=0&size=20&sort=publishedAt,desc
```

응답:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1042,
        "kind": "DISCLOSURE",
        "market": "KRX",
        "symbol": "005930",
        "stockName": "삼성전자",
        "title": "주요사항보고서",
        "publisher": "Open DART",
        "source": "OPENDART",
        "publishedAt": "2026-07-15T01:20:00Z",
        "contentSource": "OFFICIAL_DISCLOSURE",
        "rightsProfile": "STORE_FOR_AI",
        "aiAnalysisAllowed": true,
        "aiAnalysisStatus": "AVAILABLE",
        "summaryPreview": null,
        "url": "https://example.com/original"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 125,
    "totalPages": 7,
    "hasPrevious": false,
    "hasNext": true,
    "sort": "publishedAt,desc"
  },
  "message": "뉴스·공시 목록 조회 성공"
}
```

### 7.2 기존 endpoint 호환

- 기존 `GET /api/v1/stocks/{market}/{symbol}/news` 배열 응답은 한 release 동안 유지한다.
- 신규 UI는 처음부터 `/content-feed`를 사용한다.
- 기존 endpoint를 page 응답으로 즉시 변경해 현재 프런트와 외부 contract를 깨지 않는다.
- 제거 시점에는 deprecation 로그·문서·호출 검색과 E2E 전환 증적을 남긴다.

### 7.3 필터·정렬 규칙

서버는 다음만 허용한다.

- `sort=publishedAt,desc`
- `sort=publishedAt,asc`
- 필요 시 `sort=title,asc`를 후속 승인

클라이언트의 임의 column명은 repository sort로 전달하지 않는다. 같은 publishedAt의 결정적 순서를 위해 항상 `id DESC` 또는 정렬 방향에 맞는 tie-breaker를 추가한다.

기간 기준은 `publishedAt`이며 `[from, to 다음 날 시작)` 반개구간으로 처리한다. 시간대는 요청에 date만 있으면 사용자 표시 시간대와 무관하게 명세된 UTC 경계 또는 명시된 zone을 서버가 일관되게 적용한다.

### 7.4 오류

| HTTP | code | 조건 |
|---|---|---|
| 400 | CONTENT_FILTER_INVALID | enum·기간·검색어 형식 오류 |
| 400 | PAGE_INVALID | page < 0 또는 size 범위 오류 |
| 400 | SORT_NOT_ALLOWED | whitelist 밖 sort |
| 404 | STOCK_NOT_FOUND | market·symbol filter 종목 없음 |
| 401 | UNAUTHORIZED | 인증 없음·만료 |
| 403 | FORBIDDEN | 관리자 목록에 USER 접근 |

정상적인 빈 검색은 `200`과 `items=[]`, `totalElements=0`, `totalPages=0`을 반환한다.

## 8. DB 조회·인덱스 계약

현재 `(stock_id, published_at DESC)`와 `(stock_id, content_kind, published_at DESC)` 인덱스를 유지한다. 전체 시장 콘텐츠 피드를 위해 실제 query plan 측정 후 다음을 migration으로 추가한다.

```text
(content_kind, published_at DESC, id DESC)
(source, published_at DESC, id DESC)
(published_at DESC, id DESC)
```

AI 상태 filter가 분석 테이블 `EXISTS` 조회를 사용한다면 `ai_analyses(news_id, feature_type, created_at DESC)` 또는 실제 schema에 맞는 인덱스를 검증한다.

제목·publisher 부분 검색은 초기에는 안전한 `ILIKE`와 제한된 기간·최대 size를 사용한다. 큰 fixture에서 목표 성능을 넘으면 `pg_trgm` GIN 인덱스를 migration으로 도입한다. 확장 기능 사용은 H2가 아닌 PostgreSQL Testcontainers에서 검증한다.

목표:

- 100,000개 콘텐츠 fixture, warm PostgreSQL 기준 p95 300ms 이하
- page size 최대 50
- count query와 content query의 filter 조건 일치
- 삭제·신규 수집 중에도 한 응답 안에서 중복 item 없음

page 기반 pagination은 조회 사이에 새 뉴스가 삽입되면 다음 page 위치가 이동할 수 있다. 대회 MVP에서는 이를 허용하되 stable sort와 refresh 안내를 제공한다. 높은 빈도의 실시간 피드가 필요해지면 별도 cursor API를 후속 명세로 만든다.

## 9. 프런트 상태·요청 처리

### 9.1 요청 수명주기

- route/query 변경 시 이전 목록 요청을 `AbortController`로 취소한다.
- 늦게 도착한 이전 응답이 현재 route 상태를 덮어쓰지 않는다.
- 동일 URL의 불필요한 중복 요청을 피한다.
- server pagination 결과를 client에서 다시 임의 pagination하지 않는다.
- 페이지 전환 시 목록 heading으로 focus를 이동하거나 결과 갱신을 live region으로 알린다.

### 9.2 상태 모델

각 페이지·카드는 다음을 구분한다.

- `idle`
- `loading`
- `success`
- `empty`
- `partial`: 일부 외부 데이터 또는 AI 결과만 실패
- `error`
- `offline`

이전 성공 목록이 있는 background refresh에서는 목록을 지우지 않고 갱신 표시를 제공한다. 인증 오류는 재시도 버튼 대신 로그인 흐름으로 이동한다.

### 9.3 Pagination UI

- `이전`, 현재 주변 페이지, `다음`을 제공한다.
- 첫·마지막 page의 불가능한 동작은 disabled 상태와 접근 가능한 설명을 제공한다.
- 전체 결과 수와 `1–20 / 125` 범위를 표시한다.
- 모바일에서는 페이지 숫자를 축약하되 이전·다음과 현재 page를 유지한다.
- page size 변경은 page=0으로 이동한다.
- 로딩 중 중복 클릭을 막되 focus를 강제로 잃지 않는다.

## 10. 접근성·반응형

- 페이지마다 하나의 `<h1>`과 의미 있는 document title을 설정한다.
- Sidebar, Topbar, Bottom Navigation을 서로 다른 접근 가능한 `nav` label로 구분한다.
- icon-only 버튼에 `aria-label`과 visible tooltip을 제공한다.
- 탭은 `tablist/tab/tabpanel` 또는 route link 중 한 패턴을 일관되게 사용하고 키보드 동작을 준수한다.
- focus-visible을 제거하지 않는다.
- badge와 상승·하락·오류를 색상만으로 구분하지 않는다.
- 390px에서 가로 body overflow가 없어야 한다.
- 최소 터치 영역 44×44px을 목표로 한다.
- reduced motion 환경에서는 sidebar·drawer·scroll animation을 축소한다.
- chart fullscreen 종료 후 trigger focus 복귀 등 기존 차트 접근성을 유지한다.

## 11. 보안·권리·개인정보

- Sidebar 숨김은 권한 통제가 아니며 ADMIN API는 서버에서 403을 유지한다.
- 외부 URL은 검증된 `http/https`만 새 탭으로 열고 `noopener noreferrer`를 적용한다.
- 뉴스 카드에 수집한 본문을 권리 없이 preview로 노출하지 않는다.
- `summaryPreview`는 AI 결과 공개가 허용된 콘텐츠와 사용자 권한에서만 반환한다.
- 검색·filter 값을 SQL이나 sort expression에 문자열 결합하지 않는다.
- URL query와 analytics에 사용자 토큰·이메일을 넣지 않는다.
- NAVER·Finnhub 메타데이터는 권리 검토 없이 AI 재가공 결과로 표시하지 않는다.

## 12. 구현 순서와 파일 경계

### Phase A: API·DB

1. page envelope와 filter DTO
2. whitelist sort·기간·size validation
3. repository specification/query와 실제 query plan
4. 필요한 migration index
5. `/api/v1/content-feed`
6. 단위·PostgreSQL 통합·보안 테스트

이 단계에서는 `App.tsx`와 전역 CSS를 수정하지 않는다.

### Phase B: App Shell·Router

1. router dependency와 route tree
2. AuthGuard·AdminGuard·NotFound
3. Sidebar·Topbar·MobileBottomNav·MoreDrawer
4. 기존 컴포넌트를 route page로 이동
5. anchor와 수동 history 호환 제거·전환

이 단계에서는 백엔드 응답 계약을 임의 변경하지 않는다.

### Phase C: 콘텐츠 페이지

1. URL filter parser·serializer
2. 뉴스·공시 tabs·filters
3. 서버 pagination 목록과 상태 badge
4. loading·empty·partial·error·retry
5. 종목 상세 content 탭 재사용

### Phase D: 검증·정리

1. 기존 기능 회귀 E2E
2. desktop·390px mobile·keyboard·screen reader contract
3. 직접 URL·새로고침·뒤로가기·offline shell
4. old endpoint·anchor 사용 검색과 deprecation 판단
5. production build·lint·Playwright·backend test

병렬 AI 작업 시 Phase A와 Phase B는 파일 소유권을 분리할 수 있다. Phase C는 API 계약이 고정된 뒤 진행하며 두 AI가 동시에 `App.tsx`, router, 전역 CSS를 수정하지 않는다.

## 13. 테스트 계약

### 백엔드

- 기본값, 모든 filter 조합의 대표 pairwise case
- page 0, 마지막 page, 빈 page, size 1·20·50, 음수·51 이상
- 허용·금지 sort와 SQL injection 유사 입력
- publishedAt 동률에서 id tie-breaker
- NEWS/DISCLOSURE, market/symbol, source, 기간, AI 상태 filter
- totalElements·totalPages·hasNext·hasPrevious 계산
- USER/ADMIN/비인증 권한
- PostgreSQL index·query plan과 큰 fixture 성능
- 기존 종목 뉴스 endpoint 회귀

### 프런트·E2E

- 로그인 후 `/dashboard`와 active menu
- Sidebar 접기·펼치기·새로고침 preference
- 모바일 bottom nav·drawer focus trap·복귀
- 모든 주요 route 직접 진입과 Not Found
- USER의 관리자 route 차단
- 종목 검색 후 overview·technical·content·briefing 탭에서 동일 종목 유지
- 콘텐츠 filter·page URL 반영, 뒤로가기 복원
- 늦은 이전 응답 무시와 요청 취소
- empty·partial·error·offline 상태
- pagination keyboard·screen reader name
- production PWA standalone·offline navigation
- 기존 차트·AI·포트폴리오·알림 회귀

## 14. 인수 조건

- [x] 데스크톱에서 접이식 Sidebar와 공통 Topbar가 동작하고 active route가 명확하다.
- [x] 390px 모바일에서 Bottom Navigation과 More Drawer로 모든 USER 기능에 접근한다.
- [x] USER에게 ADMIN 메뉴가 렌더링되지 않고 관리자 URL·API 직접 접근도 거부된다.
- [ ] 새로고침·직접 URL·뒤로가기·PWA standalone에서 현재 route와 허용된 query 상태가 복원된다.
- [x] 대시보드는 요약 카드만 제공하고 전체 기능은 목적별 route에서 손실 없이 동작한다.
- [x] 종목 상세 네 탭이 같은 `(market, symbol)`을 사용하고 고정 종목이 없다.
- [x] 뉴스·공시 목록이 서버 page 응답을 사용하며 page·size·sort·filter가 URL과 일치한다.
- [x] NEWS, DISCLOSURE, METADATA_ONLY, AI_ALLOWED, AI_COMPLETED 상태가 오인 없이 구분된다.
- [x] 필터 변경 시 page=0, page 이동 시 필터 유지, 뒤로가기 시 이전 목록 상태가 복원된다.
- [x] 동일 publishedAt과 수집 중 신규 데이터에서도 한 응답에 중복 item이 없다.
- [x] 이전 요청 취소와 응답 순서 역전이 현재 화면을 덮어쓰지 않는다.
- [x] loading·empty·partial·error·offline 상태가 페이지별로 독립 동작한다.
- [ ] 100,000개 fixture에서 합의된 p95 목표와 최대 size가 지켜진다.
- [x] Sidebar·tabs·drawer·pagination을 키보드와 screen reader 접근 이름으로 사용할 수 있다.
- [x] body 가로 overflow, 하단 메뉴의 콘텐츠 가림과 focus 유실이 없다.
- [x] 기존 종목 검색·차트·AI·관심종목·포트폴리오·환율·알림·관리자 기능 회귀가 없다.
- [x] production build·lint·backend tests·Playwright와 `git diff --check`가 통과한다.

미완료 항목이 남아 있으면 “사이드 메뉴 기반 다중 페이지 UI와 뉴스·공시 페이징 완료”로 표시하지 않는다.
