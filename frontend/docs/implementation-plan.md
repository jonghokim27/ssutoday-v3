# Claude Design HTML Implementation Analysis

## 분석 대상

- `design/Studyroom Booking.dc.html`
- Claude Design 전용 태그(`x-dc`, `sc-if`, `sc-for`)와 인라인 스타일, 하단 `DCLogic` 스크립트로 구성된 모바일 앱형 정적 프로토타입이다.
- HTML 내부 일부 한글 문자열은 로컬 텍스트 출력에서 깨져 보이지만, 스크린샷 기준 의도된 화면은 `SSUTODAY 스터디룸 예약` 흐름이다. 실제 구현 시에는 문구를 별도 상수 또는 i18n 리소스로 재정리해야 한다.

## 1. 페이지/섹션 구조

전체는 402px 폭의 모바일 프레임 안에서 여러 화면을 조건부 렌더링하는 단일 페이지 앱 구조다.

### 공통 레이아웃

- 모바일 디바이스 프레임
  - 흰색 앱 배경
  - iOS 상태바 형태의 시간/네트워크/배터리 표시
  - 상단 다이내믹 아일랜드 형태 장식
- 공통 하단 내비게이션
  - 공지 홈
  - 예약 메인
  - 마이페이지
- 공통 피드백 UI
  - 토스트
  - 확인 모달
  - 날짜 선택 모달

### 예약 홈 화면

- sticky 브랜드 바
  - SSU 로고
  - `SSUTODAY`
  - 화면 제목: 스터디룸 예약
  - 예약 내역 버튼
- 인사/설명 영역
- sticky 날짜 스트립
  - 7일 가로 스크롤 날짜 칩
  - 날짜 선택 버튼
  - 실시간 현황 표시
- 스터디룸 카드 목록
  - 썸네일
  - 수용 인원/위치 배지
  - 방 이름
  - 혼잡/여유 상태
  - 미니 시간 히트맵

### 스터디룸 상세 예약 화면

- 상단 히어로 이미지
  - 이미지 오버레이 그라데이션
  - 뒤로가기 버튼
  - 방 이름
- 편의시설 칩 목록
- 날짜 선택/현재 시간 표시
- 30분 단위 예약 타임라인
  - 예약됨
  - 빈 시간
  - 선택된 범위
  - 현재 시간 표시선
- 범례
- 선택 요약 카드
- 초기화 버튼
- 이용 규칙 안내 박스
- 하단 sticky 예약 CTA

### 예약 완료 화면

- 완료 애니메이션
  - 체크 아이콘
  - burst/confetti 효과
- 예약 완료 메시지
- 예약 정보 요약 카드
  - 방
  - 위치
  - 날짜
  - 시간
- 주요 CTA
  - 예약 내역 보기
  - 예약 화면으로

### 예약 내역 화면

- 상단 바
  - 뒤로가기
  - 제목
- segmented tabs
  - 이용 중/대기
  - 이용 완료
- 예약 내역 카드 목록
  - 날짜
  - 시설명
  - 이용 시간
  - 상태
  - 인증샷 촬영/예약 취소/다시 예약 액션
- 빈 상태

### 공지 화면

- sticky 브랜드/필터 헤더
  - 브랜드 영역
  - 정렬 토글
  - 검색 입력
  - 별표 필터
  - 출처/카테고리 칩
- 공지 피드 목록
  - 카테고리 배지
  - 별표
  - HOT/고정 배지
  - 제목
  - 본문 요약
  - 날짜/출처
- 좌측 스와이프 기반 별표 액션
- 빈 상태

### 마이페이지 화면

- 브랜드 헤더
- 프로필 영역
  - 학부 배지
  - 이름
  - 학번
- 알림 설정 카드
  - 전체
  - 공지사항
  - 예약
  - LMS
- 메뉴 카드
  - 지원
  - 개발자 정보 펼침
  - 로그아웃
- 앱 버전/저작권

### 준비중 화면

- 아이콘
- 준비중 제목
- 안내 문구
- 예약 화면으로 이동 CTA

### 오버레이

- 예약자 정보 peek bottom sheet
  - 예약된 슬롯 클릭 시 표시
  - 이름/학번 마스킹
  - 이용 시간/인원 표시
- 확인 모달
  - 예약 확정
  - 예약 취소
  - 로그아웃
- 날짜 선택 모달
  - 월 제목
  - 요일 헤더
  - 날짜 grid
- 토스트

## 2. 반복되는 UI 패턴

- 브랜드 헤더
  - 로고, `SSUTODAY`, 현재 섹션명을 조합한 헤더가 예약 홈/공지/마이페이지에서 반복된다.
- 아이콘 버튼
  - 42px 정사각형, 14px radius, `#F2F3F8` 배경 패턴이 반복된다.
- 카드
  - 흰 배경, `#EDEEF4` border, 20~22px radius, 약한 보라 계열 shadow.
- 칩/배지
  - 카테고리, 위치, 인원, 편의시설, 필터, 상태 표시에서 반복된다.
- segmented control
  - 예약 내역 탭에서 사용.
- toggle switch
  - 알림 설정에서 사용.
- sticky 영역
  - 브랜드 바, 날짜 선택 영역, 공지 검색/필터 헤더, 상세 하단 CTA.
- bottom navigation
  - 3개 주요 탭과 중앙 예약 FAB 형태.
- timeline/timebar
  - 예약 홈의 미니 히트맵과 상세 예약 타임라인이 같은 도메인 패턴을 공유한다.
- modal/sheet/toast
  - dim backdrop, blur, scale/slide animation, rounded panel 패턴.
- empty state
  - 예약 내역/공지 필터 결과 없음.
- gradient primary CTA
  - `#4F7CFF -> #9B5CFF` 그라데이션과 보라 shadow가 반복된다.

## 3. React 컴포넌트 후보

### Layout

- `MobileAppShell`
  - 앱 전체 프레임, 상태바, safe area, 하단 내비게이션 렌더링 책임.
- `StatusBar`
- `BottomNavigation`
- `ScrollableScreen`
- `StickyHeader`

### Common UI

- `BrandMark`
- `BrandHeader`
- `IconButton`
- `Button`
  - variant: `primary`, `secondary`, `ghost`, `danger`, `disabled`
- `Card`
- `Chip`
- `Badge`
- `SegmentedControl`
- `ToggleSwitch`
- `Toast`
- `ConfirmDialog`
- `BottomSheet`
- `EmptyState`
- `DatePickerDialog`

### Reservation

- `ReservationHomePage`
- `DateStrip`
- `DateSelectButton`
- `LiveStatusIndicator`
- `StudyRoomCard`
- `RoomMetaBadges`
- `RoomAvailabilityMiniBar`
- `StudyRoomDetailPage`
- `RoomHero`
- `AmenityChips`
- `ReservationTimeline`
- `TimeSlotButton`
- `CurrentTimeIndicator`
- `TimelineLegend`
- `SelectionSummary`
- `UsageRules`
- `StickyReservationCTA`
- `ReservationSuccessPage`
- `ReservationSummaryCard`
- `ReservationHistoryPage`
- `ReservationHistoryTabs`
- `ReservationHistoryCard`
- `ReservationStatusAction`
- `ReservedUserPeekSheet`

### Notices

- `NoticePage`
- `NoticeHeader`
- `NoticeSearchInput`
- `NoticeFilterChips`
- `NoticeFeed`
- `NoticeListItem`
- `NoticeSwipeAction`

### My Page

- `MyPage`
- `ProfileSummary`
- `NotificationSettingsCard`
- `NotificationSettingRow`
- `MenuListCard`
- `MenuRow`
- `DeveloperInfoPanel`

### 기타

- `ComingSoonPage`
- `SuccessAnimation`
- `Confetti`

## 4. Design Token

### Color

#### Brand

- `color.brand.ssuBlue`: `#4F7CFF`
- `color.brand.ssuPurple`: `#9B5CFF`
- `color.brand.cyan`: `#1E9BD1`
- `color.brand.primaryGradient`: `linear-gradient(135deg, #4F7CFF, #9B5CFF)`
- `color.brand.logoGradient`: `radial-gradient(circle at 30% 110%, #1E9BD1 0%, #4F7CFF 48%, #9B5CFF 100%)`

#### Text

- `color.text.primary`: `#0F1222`
- `color.text.headingBlue`: `#143A66`
- `color.text.secondary`: `#4F5566`
- `color.text.tertiary`: `#6B7180`
- `color.text.muted`: `#8A8F9C`
- `color.text.disabled`: `#B6BAC6`
- `color.text.placeholder`: `#A6ABB8`

#### Surface

- `color.surface.base`: `#FFFFFF`
- `color.surface.subtle`: `#F8F9FC`
- `color.surface.panel`: `#F7F8FB`
- `color.surface.control`: `#F4F5FA`
- `color.surface.controlStrong`: `#F2F3F8`
- `color.surface.disabled`: `#EFF0F5`

#### Border

- `color.border.default`: `#EDEEF4`
- `color.border.subtle`: `#F0F1F6`
- `color.border.soft`: `#F1F2F7`
- `color.border.timeline`: `#E7E9F1`

#### Semantic

- `color.semantic.success`: `#1FB97A`
- `color.semantic.successBg`: `#E8FAF1`
- `color.semantic.danger`: `#FF4D63`
- `color.semantic.dangerSoft`: `#FFECEF`
- `color.semantic.warning`: `#FFB020`
- `color.semantic.warningDark`: `#E8920E`
- `color.semantic.warningOrange`: `#FF8A3D`
- `color.semantic.info`: `#0EA5C4`
- `color.semantic.notice`: `#7C5CFF`

#### Reservation Timeline

- `color.timeline.available`: `#F2F3F8`
- `color.timeline.availableBorder`: `#E7E9F1`
- `color.timeline.booked`: `#FFE1E6`
- `color.timeline.bookedBorder`: `#FFB3BF`
- `color.timeline.bookedStrong`: `#FFC2CD`
- `color.timeline.selectedStart`: `#5C84FF`
- `color.timeline.selectedEnd`: `#9B5CFF`
- `color.timeline.now`: `#FF4D63`

### Spacing

현재 HTML은 1~2px 단위의 많은 인라인 값이 섞여 있다. 구현에서는 아래 scale로 정리하고, 예외값은 컴포넌트 내부 상수로 제한하는 것이 좋다.

- `space.0`: `0`
- `space.1`: `2px`
- `space.2`: `4px`
- `space.3`: `6px`
- `space.4`: `8px`
- `space.5`: `10px`
- `space.6`: `12px`
- `space.7`: `14px`
- `space.8`: `16px`
- `space.9`: `18px`
- `space.10`: `20px`
- `space.11`: `22px`
- `space.12`: `24px`
- `space.13`: `26px`
- `space.14`: `30px`
- `space.screenX`: `22px`
- `space.bottomNavHeight`: `64px`
- `space.statusTop`: `52px`
- `space.stickyCtaY`: `12px 22px 26px`

### Typography

- Font family: `Pretendard`, `-apple-system`, `BlinkMacSystemFont`, system sans-serif
- Weights
  - `font.weight.regular`: `500`
  - `font.weight.medium`: `600`
  - `font.weight.bold`: `700`
  - `font.weight.extraBold`: `800`
- Sizes
  - `font.size.caption2`: `10px`
  - `font.size.caption`: `11px`
  - `font.size.labelSm`: `12px`
  - `font.size.label`: `12.5px`
  - `font.size.bodySm`: `13px`
  - `font.size.body`: `14px`
  - `font.size.bodyLg`: `14.5px`
  - `font.size.button`: `15px`
  - `font.size.buttonLg`: `16px`
  - `font.size.sectionTitle`: `18px`
  - `font.size.dialogTitle`: `20px`
  - `font.size.cardTitle`: `17px`
  - `font.size.pageTitleSm`: `24px`
  - `font.size.profileName`: `26px`
  - `font.size.heroTitle`: `27px`
  - `font.size.homeTitle`: `30px`
- Letter spacing
  - 브랜드 eyebrow: `1.4px`
  - 큰 제목: `-0.4px` ~ `-1px`
  - 원칙적으로 토큰화하되, 본문에는 letter spacing을 적용하지 않는다.

### Radius

- `radius.xs`: `4px`
- `radius.sm`: `8px`
- `radius.badge`: `9px`
- `radius.chip`: `10px`
- `radius.control`: `11px`
- `radius.md`: `12px`
- `radius.icon`: `14px`
- `radius.button`: `16px`
- `radius.buttonLg`: `18px`
- `radius.card`: `20px`
- `radius.cardLg`: `22px`
- `radius.dialog`: `26px`
- `radius.sheet`: `28px 28px 0 0`
- `radius.device`: `46px`
- `radius.full`: `999px`

### Shadow

- `shadow.logo`: `0 8px 18px -6px rgba(106, 76, 255, .4)`
- `shadow.card`: `0 10px 26px -20px rgba(40, 30, 90, .35)`
- `shadow.cardHover`: `0 20px 38px -20px rgba(79, 124, 255, .45)`
- `shadow.sticky`: `0 12px 18px -16px rgba(40, 30, 90, .55)`
- `shadow.primaryButton`: `0 16px 32px -12px rgba(106, 76, 255, .6)`
- `shadow.primarySmall`: `0 8px 16px -6px rgba(106, 76, 255, .55)`
- `shadow.bottomSheet`: `0 -16px 40px -12px rgba(40, 30, 90, .35)`
- `shadow.dialog`: `0 30px 70px -20px rgba(40, 30, 90, .55)`
- `shadow.toast`: `0 14px 30px -10px rgba(0, 0, 0, .5)`
- `shadow.device`: `0 30px 80px -20px rgba(40, 30, 90, .45), 0 0 0 11px #0c0c14, 0 0 0 13px #2a2a36`

## 5. 필요한 라우팅 구조

현재 HTML은 `screen` 상태값으로 화면을 전환한다. React 구현에서는 URL 기반 라우팅으로 분리하는 편이 유지보수에 유리하다.

- `/notices`
  - 공지 홈
- `/reservations`
  - 스터디룸 예약 홈
- `/reservations/:roomId`
  - 스터디룸 상세 예약
- `/reservations/success`
  - 예약 완료
  - 실제 서비스에서는 새로고침 대응을 위해 `bookingId` 기반 `/reservations/:bookingId/success` 또는 완료 후 `/reservations/history`로 redirect도 검토한다.
- `/reservations/history`
  - 예약 내역
  - query: `?tab=active|done`
- `/my`
  - 마이페이지
- `/coming-soon`
  - 미구현 탭 또는 기능 안내

하단 내비게이션 매핑:

- 홈 아이콘: `/notices`
- 중앙 예약 버튼: `/reservations`
- 마이 아이콘: `/my`

모달/시트는 라우팅보다 화면 내부 상태로 유지해도 된다.

- 날짜 선택 모달
- 예약 확정/취소/로그아웃 확인 모달
- 예약자 peek bottom sheet
- 토스트

## 6. 필요한 상태 관리

전역 상태 관리 라이브러리가 반드시 필요한 수준은 아니다. 서버 연동 전까지는 React local state와 URL state 중심으로 충분하다.

### URL/라우터 상태

- 현재 화면
- 예약 내역 탭: `active`, `done`
- 상세 화면의 `roomId`

### 예약 도메인 상태

- 선택 날짜
- 날짜 스트립 window start
- 선택 방
- 선택 슬롯 시작/끝
- 선택 range 여부
- 예약 목록
- 마지막 예약 정보

이 상태는 예약 관련 페이지 묶음에서 공유되므로 `ReservationProvider` 또는 예약 feature 전용 hook 후보다.

### 공지 상태

- 정렬: 최신순/오래된순
- 검색어
- 카테고리 필터
- 별표 목록
- 별표만 보기
- 스와이프 진행 상태

공지 페이지 내부 상태로 충분하다. 별표 목록을 앱 재방문 후에도 유지해야 한다면 localStorage 또는 서버 저장을 고려한다.

### 마이페이지 상태

- 알림 설정
  - 전체
  - 공지
  - 예약
  - LMS
- 개발자 정보 펼침 여부

초기 구현에서는 마이페이지 local state로 충분하다. 실제 알림 설정은 사용자 설정 API와 연결될 가능성이 높다.

### 공통 UI 상태

- confirm dialog
- toast
- date picker open
- reserver peek sheet

`useDialog`, `useToast`, `useDatePicker`처럼 공통 hook으로 분리하면 된다.

## 7. 정적 마크업으로 유지해도 되는 부분과 컴포넌트화해야 하는 부분

### 정적 마크업으로 유지해도 되는 부분

- 상태바/다이내믹 아일랜드 장식
  - 실제 모바일 웹에서는 불필요할 수 있다. 프로토타입 프레임이 필요한 경우 `MobileAppShell` 내부 정적 요소로 둔다.
- 이용 규칙 안내 문구
  - 문구 변경 빈도가 낮으면 `UsageRules` 내부 정적 배열로 충분하다.
- 완료 화면의 체크 아이콘 구조
  - 애니메이션만 CSS로 유지하고 데이터 의존성은 요약 카드에 한정한다.
- 준비중 화면의 기본 안내 문구
- 앱 버전/저작권 표시
- 범례
  - 예약 상태 종류가 고정이면 정적 배열로 충분하다.

### 컴포넌트화해야 하는 부분

- 브랜드 헤더
  - 예약 홈, 공지, 마이페이지에서 반복된다.
- 하단 내비게이션
  - 라우팅과 active 상태를 가져야 한다.
- 날짜 스트립/날짜 선택 모달
  - 예약 홈과 상세 화면에서 날짜 선택 로직을 공유한다.
- 스터디룸 카드
  - 목록 데이터 기반 렌더링, 상태 배지, 미니 히트맵 포함.
- 예약 타임라인
  - 선택, 예약됨, 현재 시간, 예약자 peek 등 상호작용이 많다.
- 예약 요약/CTA
  - 슬롯 선택 상태에 따라 disabled/enabled, 라벨, 스타일이 바뀐다.
- 예약 내역 카드
  - 예약 상태별 액션이 다르다.
- 공지 필터/검색/피드 아이템
  - 정렬, 검색, 필터, 별표, 스와이프 액션을 가진다.
- 알림 설정 토글
  - 전체 토글과 하위 토글 간 동기화 로직이 있다.
- 확인 모달
  - 예약 확정, 예약 취소, 로그아웃에서 톤/아이콘/본문/액션만 바뀐다.
- 토스트
  - 여러 기능에서 공통으로 호출된다.
- bottom sheet
  - 예약자 정보 표시 외에도 향후 재사용 가능성이 높다.

## 구현 전 확인할 사항

- 실제 앱이 모바일 프레임까지 포함해야 하는지, 아니면 반응형 모바일 웹 화면만 구현할지 결정해야 한다.
- Claude Design HTML의 깨진 한글 문자열은 스크린샷 또는 기획 문구를 기준으로 복원해야 한다.
- 현재 데이터는 프로토타입용 mock이다. API 연동 여부에 따라 예약/공지/사용자 설정 상태의 소유 위치가 달라진다.
- 상세 예약의 슬롯 선택 규칙은 현재 `30분 단위`, `예약된 시간 포함 불가`, `첫 탭 단일 선택`, `두 번째 탭 range 선택`, `range 이후 새 선택 시작`으로 정의되어 있다.
- 공지 아이템의 swipe-to-star는 모바일 포인터 이벤트 품질 확인이 필요하다.
