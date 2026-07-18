# 18번 UI 폴리시·제품 상세 명세 구현 보고서

기준 문서: `18_UI_POLISH_AND_PRODUCT_DETAIL_SPEC.md`

구현일: 2026-07-17

## 1. 구현 결과

18번 명세의 P0·P1 범위를 기존 정보 구조와 API 계약을 유지한 채 적용했다.

- 공통 역할 기반 색상·간격·타입·버튼·입력·카드 토큰 적용
- 문자형 기능 아이콘을 하나의 SVG `Icon` 컴포넌트로 통일
- `LIVE`, `DELAYED`, `REFERENCE`, `STALE`, `DEMO`, `PARTIAL`, `METADATA_ONLY`, `READY`, `UNAVAILABLE` 상태 배지 통일
- 주요 화면의 initial loading, background refresh, empty, partial, stale, error, offline 상태 분리
- 갱신 실패 시 마지막 정상 데이터를 유지하고 실패 이유와 재시도 동작 표시
- 현재가, 가격 이력, 환율, 뉴스·공시, AI 결과의 출처와 기준 시각 분리
- 390×844 모바일, bottom navigation, More drawer focus trap, route 전환 focus, PWA offline shell 보완
- 상세 차트 도구 그룹화, 주기·기간·지표·그리기·전체화면 조작 정리
- 지표 범례에 현재 값·파라미터·필요 데이터 수 표시
- AI 결과를 결론 → 긍정/위험 근거 → 실제 evidence → 한계·면책 → 생성·캐시 메타 순서로 정리
- 실시간 데이터는 `시장:종목코드` canonical key로 격리하고 프레임 단위로 묶어 갱신
- 포트폴리오·알림·관리자 통계에서 부분 실패와 값 없음이 0 또는 정상으로 오인되지 않도록 수정
- 관리자 권한 가드가 중첩 route context를 보존하도록 수정
- 무거운 종목 상세·차트 모듈을 route 단위로 지연 로딩하고 동적 청크도 PWA 설치 캐시에 포함

## 2. 핵심 신뢰도 규칙

- LIVE 현재가 수집 실패를 DEMO 값으로 조용히 대체하지 않는다.
- 일봉·주봉·월봉 차트는 현재가의 LIVE 상태를 상속하지 않는다.
- 차트 기준 시각은 현재 틱이 아니라 화면에 표시된 마지막 캔들의 시각을 우선한다.
- 빈 차트의 조회 실패는 `UNAVAILABLE`, 기존 차트를 유지한 갱신 실패는 `STALE`로 표시한다.
- 동일 종목코드라도 KRX와 NASDAQ 등 서로 다른 시장의 실시간 값은 섞이지 않는다.
- AI가 반환한 evidence ID 중 실제 화면 데이터에 존재하는 항목만 이동 가능한 근거로 제공한다.
- 외부 원문 링크는 `http` 또는 `https`만 허용하고 새 창 링크에 `noopener noreferrer`를 적용한다.
- AI·관리자 비용은 공급자 청구서가 아니라 설정 단가 기반 추정치임을 표시한다.

## 3. 재현 가능한 증빙

- `docs/evidence/18-desktop-dashboard.png`
- `docs/evidence/18-desktop-technical.png`
- `docs/evidence/18-mobile-dashboard.png`
- `docs/evidence/18-pwa-offline.png`
- `docs/evidence/18-ai-cache-miss.png`
- `docs/evidence/18-ai-cache-hit.png`
- `docs/evidence/18-ai-provider-failure.png`

위 이미지는 Playwright 테스트가 실제 React 화면을 열어 생성한다. 초기 목업이나 별도의 디자인 이미지를 구현 증빙으로 사용하지 않는다.

## 4. 자동 검증

| 검증 | 결과 |
|---|---|
| Frontend lint | 통과 |
| Frontend production build | 통과 · 앱 374.11KB, 지연 로딩 차트 219.69KB |
| Playwright desktop·mobile·route·상태·AI E2E | 20개 통과 |
| Playwright production PWA offline | 1개 통과 |
| Backend tests | 135개 통과 · 실패/오류/skip 0 |
| `git diff --check` | 통과 |

상태 E2E는 initial loading, empty, 일부 API 실패 `PARTIAL`, 오프라인에서 마지막 성공 데이터를 유지하는 `STALE`을 한 시나리오에서 검증한다. 실시간 E2E는 같은 심볼이 다른 시장에서 수신되어도 현재 종목을 덮어쓰지 않는 경우와 틱 갱신 시 차트·관심종목이 재마운트되지 않는 경우를 포함한다.

한글 사용자 경로에서 Gradle worker classpath가 깨지는 Windows 환경을 위해 다음 재현 명령을 추가했다.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-backend-windows.ps1
```

이 스크립트는 저장소와 사용자 홈을 임시 ASCII 드라이브 문자에 연결하고, JDK 21·Gradle 캐시·임시 경로를 설정한 뒤 항상 매핑을 정리한다.

## 5. 수동 확인으로 남는 항목

아래 항목은 코드나 브라우저 자동화만으로 최종 제출물을 완성할 수 없으므로 제출 전 실제 기기와 발표 자료에서 확인한다.

- 1024×768 및 768×1024 최종 육안 검사
- 실제 기기의 PWA 설치 아이콘·maskable 영역·OS 아이콘 캐시 확인
- 터치 pinch, tooltip viewport 경계, drawing handle의 실기기 조작성
- Lighthouse 또는 동등한 접근성 보고서 저장
- Docker Desktop daemon을 실행한 뒤 Docker 기반 인프라 통합 테스트 재실행
- 5분 시연 영상 촬영과 발표 Q&A·A1 패널 문구 정합성 확인

시연 영상은 `로그인 → 종목 검색 → 상세 차트 → AI 기술 해설 → 뉴스·공시 원문 → 동일 요청 CACHE HIT → 관리자 비용 지표 → 모바일 PWA` 흐름으로 촬영한다.
