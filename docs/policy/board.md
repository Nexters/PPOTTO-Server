# 보드 정책 (GUI 2.0)

GUI 2.0 보드 기능(F01–F09, 티켓 T01–T04)의 서버 정책을 세 단계로 나눠 관리한다.

| 단계 | 뜻 | 바꾸려면 |
|---|---|---|
| **확정** | 기획 시안에서 확인됐고, 서버에 구현됐고, `api-spec.md`에 적혀 있다. 회귀 테스트가 지킨다 | 기획과 합의한 뒤 테스트와 이 문서를 같은 변경에서 고친다 |
| **임시 결정** | 서버가 동작하려면 값이 필요해서 서버가 먼저 정했다. 기획 확인이 없다 | 기획이 확인하면 **확정**으로 올리고, 다르게 정하면 코드·테스트·문서를 함께 고친다 |
| **미정** | 서버 동작이 없거나, 지금 동작이 정책이 아니라 우연이다. 테스트로 굳히지 않는다 | 결정되면 구현·테스트와 함께 **확정**으로 옮긴다 |

기준 시안: Figma `[~10/15] GUI_v2.0` 페이지의 "GUI 2.0 — 기능 상세 명세 F01–F09"와 T01–T04 티켓 프레임.
테스트 경로는 `src/test/kotlin/com/github/nexters/ppotto/` 기준이다. 테스트 이름이 곧 정책 문장이므로, 테스트를 지우거나 이름을 바꾸면 이 문서도 함께 고친다.

## 확정

| 기능 | 정책 | API | 회귀 테스트 |
|---|---|---|---|
| F01 생성 | 이름은 최대 10자. 공백뿐인 이름은 거부한다 | `POST /boards` | `board/presentation/BoardControllerTest` "최대 길이를 넘는 이름으로 보드를 만들면 → COMMON-001로 400을 응답한다", "공백뿐인 이름으로 보드를 만들면 → COMMON-001로 400을 응답한다" |
| F01 생성 | 이름을 생략하면 서버가 기본 이름을 붙인다 | `POST /boards` | `board/application/BoardCommandServiceTest` "Board 1 이름으로 저장된다" |
| F01 생성 | 보드는 사용자당 최대 100개. 넘으면 `BOARD-003` | `POST /boards` | `board/application/BoardCommandServiceTest` "BOARD-003 오류가 발생한다" |
| F02 목록 | 내가 만든 보드와 참여한 보드만 내려간다. 다른 사용자의 보드와 삭제된 보드는 절대 내려가지 않는다 | `GET /boards` v2 | `board/application/BoardQueryServiceTest` "내가 만든 보드와 참여한 보드만 최근 활동순으로 반환한다", `board/presentation/BoardControllerTest` "탭별 개수와 함께 내 보드와 참여한 보드만 반환한다" |
| F02 목록 | 전체 / 개인 / 공유 탭으로 나누고 탭별 개수를 준다. 참여자가 한 명이라도 있으면 공유 보드다 | `GET /boards` v2 | `board/application/BoardQueryServiceTest` "참여자가 있으면 공동 보드이고 내 역할과 참여 인원을 함께 준다", `board/presentation/BoardControllerTest` "내 개인 보드는 PERSONAL이고 내 역할은 OWNER다" |
| F02 목록 | 카드에 분석 중 상태를 표시한다. 내 분석만 보이고 다른 참여자의 분석은 보이지 않는다 | `GET /boards` v2 | `board/application/BoardQueryServiceTest` "내가 진행 중인 분석만 그 보드에 표시하고 다른 참여자의 분석은 보이지 않는다" |
| F03 이름 변경 | 생성과 같은 규칙(최대 10자, 공백 불가)을 쓴다 | `PATCH /boards/{boardId}` | `board/presentation/BoardControllerTest` "최대 길이를 넘는 이름으로 변경하면 → COMMON-001로 400을 응답한다", "공백뿐인 이름으로 변경하면 → COMMON-001로 400을 응답한다" |
| F04 삭제 | 분석이 진행 중인 보드는 삭제할 수 없다. `BOARD-005` | `DELETE /boards/{boardId}` | `board/application/BoardCommandServiceTest` "BOARD-005 오류가 발생하고 보드는 유지된다" |
| F04 삭제 | 보드를 지우면 그 위의 스티커, 리캡, 그림을 함께 지운다 | `DELETE /boards/{boardId}` | `board/application/BoardCommandServiceTest` "보드와 드로잉을 숨기고 스티커 삭제를 위임한다" |
| F05 초대 | 초대 코드는 영문·숫자 6자리 | `POST /boards/{boardId}/invitations` | `board/application/BoardInvitationServiceTest` "6자리 코드와 30일 만료 시각을 반환한다", `board/infrastructure/BoardInvitationRepositoryTest` "코드 형식 CHECK 제약으로 거부한다" |
| F06 참여 | 이미 참여한 사용자가 다시 참여해도 참여 관계가 중복 생성되지 않는다 | `POST /board-invitations/{code}/join` | `board/application/BoardParticipationServiceTest` "기존 멤버가 다시 참여하면 → 인원을 늘리지 않고 MEMBER 결과를 반환한다", "방장이 참여하면 → 멤버 행을 만들지 않고 OWNER 결과를 반환한다" |
| F07 나가기 | 참여자는 보드에서 나갈 수 있다. 나가면 즉시 목록과 조회에서 빠진다 | `DELETE /boards/{boardId}/members/me` | `board/application/BoardParticipationServiceTest` "참여자가 나가면 → 참여 관계가 사라지고 보드 목록에서 빠진다" |
| F08 공유 | 공유 범위는 보드만(`BOARD`) / 리캡까지(`RECAP`) / 테마 속 사진까지(`PHOTOS`) 셋 중 하나 | `POST /boards/{boardId}/share` | `board/application/BoardShareServiceTest` "방장 이름의 공유 링크를 요청한 범위로 저장한다", "같은 토큰을 유지하고 범위만 바꾼다" |
| F08 공유 | 공동 보드를 공유하면 공유자가 아닌 참여자의 스티커도 노출된다 | `GET /boards/shared/{shareToken}` | `board/application/BoardShareQueryServiceTest` "보드 이름, 공유자 이름, 범위와 함께 모든 참여자의 스티커와 그림을 반환한다" |
| F09 공유 웹 | 범위 밖 리캡·사진은 화면뿐 아니라 API 직접 호출로도 내려가지 않는다 | `GET /boards/shared/{shareToken}/stickers/{stickerId}` | `sticker/presentation/SharedBoardRecapControllerTest` "공유 범위 밖이라 BOARD-011을 반환한다", "사진을 뺀 리캡을 반환한다", `board/application/BoardShareQueryServiceTest` "리캡을 열 수 없다" |
| F09 공유 웹 | 빈 보드(스티커 0개)와 찾을 수 없는 보드(삭제, 없는 토큰)를 구분한다 | `GET /boards/shared/{shareToken}` | `board/application/BoardShareQueryServiceTest` "빈 스티커와 그림 목록을 반환한다", "공유된 보드를 찾을 수 없다" |
| F09 공유 웹 | 공유 링크 열람은 참여 절차와 다르다. 열람만으로 참여 관계가 생기지 않는다 | `GET /boards/shared/{shareToken}` | `board/application/BoardShareQueryServiceTest` "공유 링크 조회는 열람일 뿐 참여 관계를 만들지 않는다" |
| F08 범위 제외 | 공유 해제는 이번 범위에서 제외한다. 해제 API를 만들지 않는다 | 없음 | 없음 (API가 없는 것이 정책이다) |

## 임시 결정

서버가 먼저 정한 값이다. 지금 동작과 테스트는 아래와 같고, 기획 확인을 기다린다.

| 항목 | 지금 동작 | 회귀 테스트 |
|---|---|---|
| 참여 정원 | 방장 포함 8명. 넘으면 `BOARD-009` | `board/application/BoardParticipationServiceTest` "방장과 멤버 7명으로 가득 찬 보드의 초대가 → 정원 초과로 거부한다" |
| 초대 코드 유효기간 | 최초 발급부터 30일. 재호출로 연장되지 않는다. 만료 후 재발급하면 코드가 바뀐다 | `board/application/BoardInvitationServiceTest` "기존 초대와 만료 시각을 그대로 재사용한다", "같은 초대 행의 코드와 만료 시각을 교체한다" |
| 초대 코드 문자 | 헷갈리는 `0`, `1`, `I`, `O`를 뺀 대문자·숫자. 입력은 대소문자를 가리지 않는다 | `board/application/BoardParticipationServiceTest` "신규 사용자가 참여하면" (소문자 코드로 참여) |
| 초대 발급 권한 | 방장과 참여자 모두 발급할 수 있다 | `board/application/BoardInvitationServiceTest` "참여자 소유의 초대가 저장된다" |
| 공유 링크 수명 | 만료 없음. 다시 공유해도 같은 링크를 쓰고 범위만 바뀐다 | `board/application/BoardShareServiceTest` "같은 토큰을 유지하고 범위만 바꾼다" |
| 공유 권한 | 방장과 참여자 모두 공유할 수 있고, 사람마다 자기 링크와 범위를 따로 가진다 | `board/application/BoardShareServiceTest` "사람마다 다른 토큰과 범위를 가진다", "방장의 공유 범위는 바뀌지 않는다" |
| 마지막 보드 삭제 | 마지막 남은 보드는 지울 수 없다. `BOARD-004` | `board/application/BoardCommandServiceTest` "BOARD-004 오류가 발생한다" |
| 방장 나가기 | 방장은 나갈 수 없다. `BOARD-012`. 보드 삭제를 쓴다 | `board/application/BoardParticipationServiceTest` "방장이 나가려 하면 → 방장은 나갈 수 없어 거부하고 참여 관계는 그대로다" |
| 나간 뒤 재참여 | 막지 않는다. 유효한 초대 코드로 다시 참여할 수 있다 | `board/application/BoardParticipationServiceTest` "참여자가 나가면 → 초대 코드로 다시 참여할 수 있다" |
| 나간 사람의 공유 링크 | 즉시 열리지 않는다 | `board/application/BoardParticipationServiceTest` "참여자가 나가면 → 나간 참여자의 공유 링크는 더 이상 열리지 않는다" |
| 나간 사람의 초대 코드 | 만료 시각까지 유효하다 | `board/application/BoardParticipationServiceTest` "참여자가 나가면 → 나간 참여자가 발급한 초대 코드는 만료 전까지 유효하다" |
| 쓰기 권한 | 이름 변경, 삭제, 레이아웃 저장, 분석 생성은 방장만. 참여자는 `BOARD-002` | `board/presentation/BoardControllerTest` "쓰기는 방장 전용이라 BOARD-002로 404를 응답하고 …" 3건 |
| 목록 정렬 | 최근 스티커가 추가된 순. 페이지 없이 전부 준다 | `board/application/BoardQueryServiceTest` "마지막 활동 시각과 순서는 스티커가 추가된 시각 그대로다" |
| 초기 보드 | 가입 시 기본 보드 1개를 서버가 만든다 | `auth/infrastructure/integration/AuthDomainIntegrationTest` "계정과 기본 보드를 각각 한 번만 생성한다", "기존 사용자에게 기본 보드를 중복 생성하지 않는다" |

## 미정

서버 동작이 없거나 지금 동작이 정책이 아니다. 결정되기 전에는 테스트로 굳히지 않는다.

| 항목 | 질문 | 지금 상태 |
|---|---|---|
| 참여자 쓰기 권한 | 참여자가 사진을 올려 스티커를 만들 수 있나? 배치·그림을 바꿀 수 있나? 남의 스티커를 옮기거나 지울 수 있나? | 전부 방장만. 시안의 "참여자의 스티커도 사라진다" 문구와 맞지 않는다 |
| 참여자 리캡 열람 | 같은 보드 참여자가 서로의 리캡(요약, 코멘트, 사진)을 볼 수 있나? 새 리캡 표시는 사람마다 따로인가? | 본인 것만 볼 수 있다. 표시는 스티커당 하나 |
| 나갈 때 데이터 정리 | 나가는 사람의 스티커·리캡·원본 사진·그림을 지우나? | 참여자 스티커가 없어 지울 것이 없다. 참여자 쓰기 권한과 함께 정한다 |
| 분석 중 나가기 | 자기 분석이 진행 중일 때 나갈 수 있나? | 참여자는 분석을 만들 수 없어 해당 없음 |
| 탈퇴한 참여자 | 탈퇴 즉시 보드에서 빠지나? 정리 배치까지 기다리나? | 배치까지 참여자 수에 포함된다 |
| 탈퇴한 방장 | 방장이 탈퇴하면 공동 보드는? (삭제 / 다른 참여자에게 양도) | 배치까지 참여자 목록과 상세에 그대로 보인다 |
| 100개 제한 집계 | 참여한 보드도 100개에 포함하나? 참여 보드 수에 별도 상한을 두나? | 만든 보드만 센다. 참여 보드는 상한 없음 |
| 방장의 공동 보드 삭제 | 참여자가 있으면 경고·알림을 주나? 삭제를 막나? | 아무 안내 없이 바로 사라진다 |
| 내보내기 | 방장이 참여자를 내보낼 수 있나? 내보낸 사람의 재참여를 막나? | API 없음. 참여 행 삭제 외에 상태를 표현할 수단이 없다 |
| 생성 시 이름 규칙 | 중복 이름 허용? 이모지 글자 수 계산 기준? | 중복 허용. 글자 수는 `@Size`의 UTF-16 단위라 일부 이모지는 2자로 센다 |
