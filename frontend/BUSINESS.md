# SSUTODAY App Business Logic

이 문서는 현재 React Native 클라이언트 코드 기준으로 확인되는 비즈니스 로직, 화면 흐름, 전역 상태, 라우팅, API 계약을 정리한다. 응답 DTO는 백엔드 원본 스키마가 아니라 앱이 실제로 참조하는 필드를 기준으로 작성했다.

## 공통 API 규칙

- Base URL: `https://api.ssu.today/`
- 모든 내부 API는 `POST`를 사용한다.
- 인증 필요 API는 `Authorization`, `Refresh-Token` 헤더를 함께 보낸다.
- 성공/실패는 HTTP status보다 `statusCode`로 분기한다.
- 서버가 응답 헤더에 `access-token`, `refresh-token`을 내려주면 앱은 `AsyncStorage`의 `accessToken`, `refreshToken`을 갱신한다.
- 네트워크 실패 또는 521 응답은 클라이언트에서 공통으로 `SSU0000`으로 취급한다.

```ts
type ApiResponse<T> = {
  statusCode: string;
  data: T;
  message?: string;
};

type AuthHeaders = {
  Authorization: `Bearer ${string}`;
  "Refresh-Token": string;
};
```

## 전역 저장 상태

앱은 별도 상태관리 라이브러리 없이 `AsyncStorage`와 일부 싱글턴 서비스를 전역 상태처럼 사용한다.

```ts
type StoredProfile = {
  studentId: number | string;
  name: string;
  major: "cse" | "sw" | "media" | "mediamba" | "infocom" | "aix" | "sec" | string;
  isAdmin?: boolean;
};

type StorageState = {
  accessToken?: string;
  refreshToken?: string;
  profile?: string; // JSON.stringify(StoredProfile)
  provider?: string; // JSON.stringify(("ssuCatch" | "stu" | "major")[])
  notificationEnabled?: "true" | "false";
};
```
- `accessToken`, `refreshToken`: 로그인 세션. 존재하면 앱 시작 시 `student/profile`로 인증 상태를 재검증한다.
- `profile`: 로그인/프로필 조회 성공 시 저장되는 학생 정보. 공지, 예약, 마이페이지에서 사용한다.
- `provider`: 공지 목록 필터. 없으면 `["ssuCatch", "stu", "major"]`로 초기화한다.
- `notificationEnabled`: 전체 푸시 알림 활성 여부.

## 라우팅과 접근 제어

명시적인 `beforeEach` 형태의 라우터 가드는 없다. 대신 `MainScreen`이 앱 시작 게이트 역할을 한다.


### 라우터 가드 로직

1. 네트워크 연결 확인.
2. 앱 버전 확인.
3. CodePush 업데이트 확인 및 적용.
4. 푸시 권한 요청 및 FCM token 조회.
5. `accessToken`, `refreshToken`이 있으면 `student/profile` 호출.
6. `SSU2020`이면 `profile` 저장 후 `ReserveScreen`로 이동.
7. `SSU4001`이면 토큰/프로필 삭제 후 `LandingScreen`로 이동.
8. 토큰이 없으면 `LandingScreen`로 이동.


## 인증/로그인

### SSO 로그인

로그인 화면은 아래 SSO URL로 리다이렉션 한다.
리다이렉션 중 흰 화면이 나오지 않도록 로딩을 먼저 띄우고 리다이렉션 시킨다.

```txt
https://smartid.ssu.ac.kr/Symtra_sso/smln.asp?apiReturnUrl=https://v3.ssu.today/sso_callback
```

sso_callback 경로의 쿼리 파라미터로 sToken과 sIdno가 돌아온다.
로그인 API를 통해 로그인을 진행한다.

### `POST /student/login`

인증 불필요.

```ts
type StudentLoginRequest = {
  sToken: string;
  sIdno: string;
};

type StudentLoginResponse = ApiResponse<{
  accessToken: string;
  refreshToken: string;
  studentId: number | string;
  name: string;
  major: string;
  isAdmin?: boolean;
}>;
```

주요 status code:

- `SSU2010`: 로그인 성공. 토큰과 profile 저장 후 푸시 등록, topic 구독, 공지 화면 이동.
- `SSU4010`: SSO 인증 실패.
- `SSU4011`: 지원하지 않는 학과.
- `SSU0000`: 서버 연결 실패.

### `POST /student/profile`

인증 필요.

```ts
type StudentProfileRequest = {};

type StudentProfileResponse = ApiResponse<StoredProfile>;
```

주요 status code:

- `SSU2020`: 인증 유효. profile 저장.
- `SSU4001`: 인증 만료. 토큰/profile 삭제 후 로그인 화면 이동.
- `SSU0000`: 서버 연결 실패.

### `POST /student/logout`

인증 필요.

```ts
type StudentLogoutRequest = {};
type StudentLogoutResponse = ApiResponse<null>;
```

주요 status code:

- `SSU2030`: 로그아웃 성공. 모든 AsyncStorage key 삭제 후 `LandingScreen`으로 이동.

## 버전/기기/푸시

### `POST /device/checkVersion`

인증 불필요.

```ts
type CheckVersionRequest = {
  osType: "ios" | "android";
  version: string;
};

type CheckVersionResponse = ApiResponse<null>;
```

주요 status code:

- `SSU2070`: 최신 버전.
- `SSU2071`: 강제 업데이트 필요. iOS는 App Store, Android는 Play Store URL을 연다.

### `POST /device/register`

인증 필요.

```ts
type DeviceRegisterRequest = {
  osType: "ios" | "android";
  uuid: string;
  pushToken: string;
};

type DeviceRegisterResponse = ApiResponse<null>;
```

주요 status code:

- `SSU2040`: 등록 성공.

사용 위치:

- 로그인 성공 직후.
- 앱 시작 시 인증 유효하고 `notificationEnabled`가 없거나 `true`인 경우.
- 마이페이지 전체 알림 ON.

### `POST /device/unregister`

인증 필요.

```ts
type DeviceUnregisterRequest = {
  osType: "ios" | "android";
  uuid: string;
};

type DeviceUnregisterResponse = ApiResponse<null>;
```

사용 위치:

- 마이페이지 전체 알림 OFF.
- 로그아웃 전 알림이 켜져 있는 경우.

### `POST /device/getOption`

인증 필요.

```ts
type DeviceGetOptionRequest = {
  osType: "ios" | "android";
  uuid: string;
};

type DeviceGetOptionResponse = ApiResponse<{
  notice: boolean;
  reserve: boolean;
  lms: boolean;
}>;
```

주요 status code:

- `SSU2170`: 알림 옵션 조회 성공.

### `POST /device/updateOption`

인증 필요.

```ts
type DeviceUpdateOptionRequest = {
  osType: "ios" | "android";
  uuid: string;
  option: "notice" | "reserve" | "lms";
  value: boolean;
};

type DeviceUpdateOptionResponse = ApiResponse<null>;
```

주요 status code:

- `SSU2180`: 알림 옵션 변경 성공.

`notice` 옵션을 끄면 FCM topic `all`, `{major}`를 구독 해제하고, 켜면 다시 구독한다.

## 공지

### NoticeScreen 비즈니스 로직

- `profile`이 없으면 `MainScreen`으로 이동한다.
- provider 필터가 없으면 `["ssuCatch", "stu", "major"]`로 초기화한다.
- 정렬은 `DESC`, `ASC`를 지원한다.
- 검색어와 provider 필터를 조합해 목록을 조회한다.
- 20개 미만이면 다음 페이지 로딩을 하지 않는다.
- 공지 카드 선택 시 `NoticeWebViewScreen`으로 `idx`를 넘긴다.

### `POST /article/list`

인증 필요.

```ts
type ArticleProvider = "ssuCatch" | "stu" | "major" | string;

type ArticleListRequest = {
  page: number;
  orderBy: "DESC" | "ASC";
  search: string;
  provider: ArticleProvider[];
};

type ArticleSummary = {
  idx: number;
  title: string;
  content: string;
  createdAt: string;
  provider: string;
};

type ArticleListResponse = ApiResponse<{
  articles: ArticleSummary[];
  totalPages: number;
}>;
```

주요 status code:

- `SSU2060`: 목록 조회 성공.

### `POST /article/get`

인증 필요.

```ts
type ArticleGetRequest = {
  idx: number;
};

type ArticleDetail = {
  idx: number;
  title: string;
  content: string;
  url: string; // decodeURIComponent 후 WebView uri로 사용
};

type ArticleGetResponse = ApiResponse<{
  article: ArticleDetail;
}>;
```

주요 status code:

- `SSU2080`: 상세 조회 성공. `article.url`을 웹뷰로 연다.
- `SSU4080`: 게시글 없음.

공지 상세는 단순 웹뷰 화면이며, API에서 받은 `article.url`만 웹뷰로 렌더링한다.

## 예약

예약 시간은 30분 단위 block으로 처리한다. UI는 06:00부터 22:00까지 표시하고, block 값은 `hour * 2` 방식이라 06:00은 `12`, 06:30은 `13`, 21:30은 `43`이다.

### 예약 시간 선택 규칙

- 이미 예약된 block은 선택할 수 없다.
- 시작 block 선택 후 종료 block을 선택한다.
- 종료 block이 시작 block보다 작으면 시작 block을 다시 지정한다.
- 한 번에 최대 4개 block, 즉 2시간까지만 선택할 수 있다.
- 시작 block만 선택하면 30분 예약으로 처리한다.
- 과거 날짜/시간은 회색 오버레이로 표시된다. 오늘 진행 중인 시간 선택 시 안내 메시지를 띄운다.

### ReserveScreen 비즈니스 로직

- 선택 날짜의 스터디룸 목록을 조회한다.
- 여러 룸의 시간표 가로 스크롤 위치를 동기화한다.
- 룸 선택 시 `ReserveRoomScreen`으로 `date`, `roomNo`를 넘긴다.
- 내 예약이면 예약 내역 화면으로 이동할 수 있다.
- 타인 예약 선택 시 예약자/시간 정보를 보여주고 신고 Google Form URL을 만들어 `ReserveReportScreen`에서 연다.
- `profile.isAdmin`이면 타인 예약에 대해 관리자 메뉴를 제공한다.

신고 웹뷰 URL:

```txt
https://docs.google.com/forms/d/e/1FAIpQLSeCYo0oiuoK-3KNzKFnFLPFP43Bp4fRZq7ulTmxgoMUWGWz8g/viewform?usp=pp_url
```

추가 query:

- `entry.284506795`: 시설명
- `entry.46856824`: 날짜
- `entry.573216846`: 시간

### `POST /room/list`

인증 필요.

```ts
type RoomListRequest = {
  date: string; // YYYY-MM-DD
};

type ReserveInRoom = {
  idx: number;
  startBlock: number;
  endBlock: number;
  studentInfo: string;
  isMine?: boolean;
  [key: string]: unknown;
};

type RoomSummary = {
  no: number | string;
  name: string;
  capacity: number;
  location: string;
  image: string;
  reserves: ReserveInRoom[];
};

type RoomListResponse = ApiResponse<{
  rooms: RoomSummary[];
}>;
```

주요 status code:

- `SSU2110`: 룸 목록 조회 성공.

### ReserveRoomScreen 비즈니스 로직

- 특정 날짜/룸 상세를 조회한다.
- 날짜 변경 시 선택 block을 초기화하고 룸 상세를 재조회한다.
- 예약 전 알림 권한과 앱 내부 알림 설정을 확인한다.
- 알림 권한이 없으면 OS 설정 또는 마이페이지로 유도하되, 사용자가 건너뛰면 예약은 계속 가능하다.
- 예약 요청 성공 후 `reserve/status`를 500ms마다 polling한다.
- 예약 최종 성공 시 `ReserveListScreen`으로 이동한다.

### `POST /room/get`

인증 필요.

```ts
type RoomGetRequest = {
  date: string; // YYYY-MM-DD
  roomNo: number | string;
};

type RoomDetail = RoomSummary & {
  tags: string; // comma separated
  bigImage: string;
};

type RoomGetResponse = ApiResponse<{
  room: RoomDetail;
}>;
```

주요 status code:

- `SSU2100`: 룸 상세 조회 성공.

### `POST /reserve/request`

인증 필요.

```ts
type ReserveRequestRequest = {
  roomNo: number | string;
  date: string; // YYYY-MM-DD
  startBlock: number;
  endBlock: number;
};

type ReserveRequestResponse = ApiResponse<{
  idx: number;
}>;
```

주요 status code:

- `SSU2090`: 예약 요청 접수. 이후 `reserve/status` polling.
- `SSU5091`: 일시적으로 예약 불가.

### `POST /reserve/status`

인증 필요.

```ts
type ReserveStatusRequest = {
  idx: number;
};

type ReserveStatusResponse = ApiResponse<{
  status: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7;
}>;
```

주요 status code:

- `SSU2120`: 상태 조회 성공.

`data.status` 의미:

- `0`: 처리 중. polling 유지.
- `1`: 예약 성공.
- `2`: 지난 날짜.
- `3`: 지난 시간.
- `4`: 예약 가능 시작 전. 앱 메시지 기준으로 전날 20:00부터 예약 가능.
- `5`: 이미 예약된 시간.
- `6`: 하루 최대 예약 가능 시간 초과.
- `7`: 동일 시간대에 사용자가 이미 예약한 스터디룸 존재.

### ReserveListScreen 비즈니스 로직

- `menu = 1`: 사용 중/사용 대기 예약.
- `menu = 0`: 사용 완료 예약.
- 10개 미만이면 다음 페이지 로딩을 하지 않는다.
- 사용 전 예약은 취소 가능하다.
- 사용 중 예약은 길게 누르면 사용 종료 처리 가능하다.
- 사용 중이고 인증 사진이 없으며 연속 예약이 아니면 인증 사진 촬영 화면으로 이동한다.
- 인증 사진이 있으면 사진 보기 화면으로 이동한다.
- 취소된 예약은 같은 룸/날짜로 다시 예약하기 화면으로 이동할 수 있다.

### `POST /reserve/list`

인증 필요.

```ts
type ReserveListRequest = {
  type: 0 | 1;
  page: number;
};

type VerifyPhoto = {
  url: string;
  createdAt: string;
};

type ReserveHistory = {
  idx: number;
  roomNo: number | string;
  date: string;
  startBlock: number;
  endBlock: number;
  createdAt: string;
  deletedAt: string | null;
  deletedReason: string | null;
  isContinuous: boolean;
  roomByRoomNo: {
    name: string;
  };
  verifyPhotosByIdx: VerifyPhoto[];
};

type ReserveListResponse = ApiResponse<{
  reserves: ReserveHistory[];
  totalPages: number;
}>;
```

주요 status code:

- `SSU2130`: 예약 내역 조회 성공.

### `POST /reserve/cancel`

인증 필요.

```ts
type ReserveCancelRequest = {
  idx: number;
};

type ReserveCancelResponse = ApiResponse<null>;
```

주요 status code:

- `SSU2140`: 취소 성공.
- `SSU4141`, `SSU4142`: 이미 완료된 예약.
- `SSU4143`: 현재 사용 중인 예약.

### `POST /reserve/done`

인증 필요.

```ts
type ReserveDoneRequest = {
  idx: number;
};

type ReserveDoneResponse = ApiResponse<null>;
```

주요 status code:

- `SSU2230`: 사용 종료 성공.
- `SSU4231`, `SSU4232`: 이미 완료된 예약.
- `SSU4233`: 아직 시작하지 않은 예약.
- `SSU4234`: 최소 30분 이상 사용하지 않아 종료 불가.
- `SSU4235`: 인증 사진을 먼저 촬영해야 함.
- `SSU4236`: 종료 가능 시간에 너무 근접해 종료 불가.

### 인증 사진 촬영

`ReservePhotoShootScreen`은 카메라 권한을 요청하고, `react-native-vision-camera`로 촬영한 JPEG 파일을 multipart API로 업로드한다.

### `POST /reserve/verifyPhoto/upload`

인증 필요. `multipart/form-data`.

Headers:

```ts
type UploadVerifyPhotoHeaders = AuthHeaders & {
  "Content-Type": "multipart/form-data";
};
```

Request body:

```ts
type UploadVerifyPhotoRequest = FormData & {
  file: {
    name: string;
    type: "image/jpeg";
    uri: string; // file://...
  };
  idx: number;
};

type UploadVerifyPhotoResponse = ApiResponse<null>;
```

주요 status code:

- `SSU2200`: 업로드 성공.
- `SSU4201`, `SSU4202`: 이미 사용 완료된 예약.
- `SSU4203`: 아직 사용 시작 전.
- `SSU4204`: 촬영 기한 경과.

### 인증 사진 보기

`ReservePhotoViewScreen`은 `ReserveListScreen`에서 받은 `verifyPhotosByIdx[0].url` 이미지를 확대 가능한 이미지 뷰어로 표시한다. 별도 API 호출은 없다.

### 관리자 예약 도구

관리자 여부는 `profile.isAdmin`으로 판단한다. 관리자 메뉴는 예약 정보 모달에서 제공된다.

지원 기능:

- `reserveCancel`: 관리자 예약 취소. 취소 사유 텍스트 필요.
- `photoDelete`: 인증 사진 삭제.
- `photoExecpt`: 인증 사진 예외 처리. 코드 오타 그대로 API에 전달된다.

관리자 기능 실행 전 생체 인증을 수행한다.

1. 기기 생체 인증 지원 여부 확인.
2. 생체 키가 없으면 키 생성.
3. 공개키를 서버에 등록.
4. payload `${type}:${idx}`에 대해 signature 생성.
5. `reserve/adminTools` 호출.

### `POST /student/enrollBiometricsKey`

인증 필요.

```ts
type EnrollBiometricsKeyRequest = {
  osType: "ios" | "android";
  uuid: string;
  publicKey: string;
};

type EnrollBiometricsKeyResponse = ApiResponse<null>;
```

주요 status code:

- `SSU2210`: 공개키 등록 성공.

### `POST /reserve/adminTools`

인증 필요.

```ts
type ReserveAdminToolsRequest = {
  type: "reserveCancel" | "photoDelete" | "photoExecpt";
  osType: "ios" | "android";
  uuid: string;
  signature: string;
  idx: number;
  text: string | null;
};

type ReserveAdminToolsResponse = ApiResponse<0 | 1 | 2 | 3 | 4 | 5 | 6 | 7>;
```

주요 status code:

- `SSU2220`: 관리자 작업 처리 완료. 상세 결과는 `data`로 판단.

`data` 의미:

- `0`: 예약이 존재하지 않음.
- `1`: 이미 취소된 예약.
- `2`: 이미 종료된 예약.
- `3`: 예약 취소 성공.
- `4`: 인증 사진이 촬영되지 않음.
- `5`: 인증 사진 삭제 성공.
- `6`: 인증 사진이 이미 촬영됨.
- `7`: 인증 사진 촬영 예외 처리 성공.

## SSO 사물함

사물함 예약 기능은 UI에서 주석 처리되어 있으나 로직은 남아 있다.

### `POST /sso/generateToken`

인증 필요.

```ts
type GenerateSsoTokenRequest = {
  clientId: "itlocker" | string;
};

type GenerateSsoTokenResponse = ApiResponse<{
  ssoToken: string;
  callbackUrl: string;
}>;
```

주요 status code:

- `SSU2150`: SSO token 생성 성공.

성공 시 `callbackUrl + ssoToken`을 `ReserveLockerScreen` 웹뷰로 연다.

## LMS

`LMSScreen`은 대부분 Canvas/LearningX 웹뷰 연동이다. 단순 웹뷰에 가깝지만, Canvas OAuth token과 LearningX API token을 앱 백엔드에 저장하는 비즈니스 로직이 있다.

### LMS 동의

최초 진입 시 `lmsAgree`가 없으면 안내 모달을 표시하고 확인 시 `lmsAgree = "true"`를 저장한다.

### Canvas OAuth 흐름

1. `canvasToken`이 있으면 sessionless launch를 시도한다.
2. 실패하면 `canvasToken`을 삭제하고 로그인 웹뷰를 연다.
3. `canvasToken`이 없으면 LearningX mobileverify API로 client 정보를 가져온다.
4. Canvas OAuth authorize URL을 웹뷰로 연다.
5. redirect URL이 `https://canvas.ssu.ac.kr/login/oauth2/auth?code=`로 시작하면 code를 추출해 token API를 호출한다.
6. `access_token`을 `canvasToken`에 저장하고 dashboard URL을 연다.
7. dashboard 로드 완료 시 cookie에서 `xn_api_token`을 찾아 앱 백엔드에 저장한다.

외부 API:

```ts
type LearningXMobileVerifyRequestQuery = {
  app_name: "LearningX Student";
  platform: "android" | "ios";
  canvas_url: "canvas.ssu.ac.kr";
};

type LearningXMobileVerifyResponse = {
  authorized: boolean;
  client_id: string;
  api_key: string;
  client_secret: string;
};
```

Android:

```txt
GET https://canvas.ssu.ac.kr/learningx/api/v1/mobileverify?app_name=LearningX Student&platform=android&canvas_url=canvas.ssu.ac.kr
```

iOS:

```txt
GET https://canvas.ssu.ac.kr/learningx/api/v1/mobileverify?app_name=LearningX Student&platform=ios&canvas_url=canvas.ssu.ac.kr
```

OAuth authorize:

```txt
GET https://canvas.ssu.ac.kr/login/oauth2/auth?client_id={clientId}&response_type=code&mobile=1&purpose=&redirect_uri=https://canvas.ssu.ac.kr/login/oauth2/auth
```

OAuth token:

```txt
POST https://canvas.ssu.ac.kr/login/oauth2/token?client_id={clientId}&client_secret={clientSecret}&code={code}&redirect_uri=urn:ietf:wg:oauth:2.0:oob
```

```ts
type CanvasTokenResponse = {
  access_token: string;
  [key: string]: unknown;
};
```

Sessionless launch:

```txt
GET https://canvas.ssu.ac.kr/api/v1/accounts/1/external_tools/sessionless_launch?id=67&launch_type=global_navigation
Authorization: Bearer {canvasToken}
```

```ts
type CanvasSessionlessLaunchResponse = {
  url: string;
};
```

Session URL:

```txt
GET https://canvas.ssu.ac.kr/login/session_token?return_to={encodeURIComponent(launchUrl)}
Authorization: Bearer {canvasToken}
```

```ts
type CanvasSessionTokenResponse = {
  session_url: string;
};
```

### `POST /student/updateXnApiToken`

인증 필요.

```ts
type UpdateXnApiTokenRequest = {
  xnApiToken: string | null;
};

type UpdateXnApiTokenResponse = ApiResponse<null>;
```

주요 status code:

- `SSU2190`: LearningX API token 등록/삭제 성공.

사용 위치:

- LMS dashboard 로드 후 cookie의 `xn_api_token`을 서버에 등록한다.
- LMS 로그아웃 시 `null`을 보내 서버 token을 삭제한다.

### LMS 로그아웃

사용자가 LMS 로그아웃을 확인하면:

1. `canvasToken` 삭제.
2. Canvas cookie 전체 삭제.
3. `student/updateXnApiToken`에 `null` 전달.
4. LMS 로그인 웹뷰를 다시 연다.

## 마이페이지

### MyScreen 비즈니스 로직

- `profile`에서 학과, 이름, 학번을 표시한다.
- 전체 알림 ON/OFF를 관리한다.
- 전체 알림 ON이면 서버에서 알림 세부 옵션을 조회한다.
- 공지/예약/LMS 알림 옵션을 개별 변경한다.
- 문의 링크는 외부 카카오 오픈채팅 URL을 연다.
- 개발자 정보에는 GitHub 저장소 링크를 연다.
- 로그아웃 시 기기 등록 해제, FCM topic 구독 해제, 백엔드 로그아웃, 로컬 저장소 삭제를 수행한다.

외부 URL:

- 문의: `https://open.kakao.com/o/sjCaLNTf`
- GitHub: `https://github.com/jonghokim27/ssutoday`

## 약관/단순 웹뷰 화면

다음 화면은 자체 비즈니스 로직이 거의 없고 전달받은 URL 또는 고정 URL을 웹뷰로 연다.

- `TermsScreen`: `https://r2.ssu.today/termsandprivacy.html`
- `NoticeWebViewScreen`: `article/get`으로 받은 `article.url`
- `ReserveReportScreen`: 예약 정보로 만든 Google Form URL
- `ReserveLockerScreen`: `sso/generateToken`으로 만든 callback URL

## 기타 앱 동작

- Firebase Analytics: navigation route 변경 시 `logScreenView` 호출.
- Foreground FCM 메시지: Notifee 로컬 알림으로 표시.
- 알림 클릭: `notification.data.link`가 있으면 해당 링크를 연다.
- CodePush: 앱 시작 중 수동으로 업데이트를 확인하고, 업데이트가 있으면 다운로드/설치 후 앱을 재시작한다.

