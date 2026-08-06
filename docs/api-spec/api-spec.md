# PPOTTO API Spec

- 원본: `/Users/dustin.hwang/Documents/ppotto/docs/api-spec/ppotto_api.html`
- OpenAPI: `3.1.0`
- 문서 버전: `1.0.0`
- 엔드포인트 수: 23

## 1. 공통 규칙

- API 버전은 `X-API-Version` 요청 헤더로 지정한다. 헤더가 없으면 서버 기본값 `1`로 처리한다.
- URL 경로에는 `/api` 프리픽스와 버전 세그먼트를 붙이지 않는다.
- 보호 API는 `Authorization: Bearer {accessToken}` 헤더가 필요하다.
- `GET /terms`는 인증 없이 호출할 수 있고 유효한 access token을 보내면 사용자 동의 상태를 함께 반환한다.
- JSON 요청과 응답은 `application/json`을 사용한다.
- UUID는 스펙 예시 기준 uuidv7 형식을 사용한다.

## 2. 공통 응답 형식

성공 응답:
```json
{
  "success": true,
  "data": {},
  "error": null
}
```

실패 응답:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-001",
    "message": "에러 메시지",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

| Field | Description |
| --- | --- |
| success | 요청 성공 여부 |
| data | 성공 시 응답 데이터. 실패 시 `null` |
| error.code | 클라이언트 분기 기준이 되는 에러 코드 |
| error.message | 사용자 또는 개발자 확인용 메시지 |
| error.fieldErrors | 요청 바디 검증 실패 시 필드별 오류. 그 외 빈 배열 |
| error.timestamp | 에러 발생 시각. UTC ISO-8601 |

## 3. 엔드포인트 요약

| Domain | Method | Path | Summary | Success | Failure | Auth |
| --- | --- | --- | --- | --- | --- | --- |
| auth | POST | /auth/login | 소셜 로그인 (가입 겸용) | 200 | 400, 401, 403 | N |
| auth | POST | /auth/refresh | 토큰 재발급 | 200 | 401 | N |
| auth | POST | /auth/logout | 로그아웃 | 200 | 401 | Y |
| users | GET | /users/me | 내 정보 조회 (설정 화면) | 200 | 401 | Y |
| users | DELETE | /users/me | 회원 탈퇴 | 200 | 401 | Y |
| terms | GET | /terms | 현재 유효 약관 목록 | 200 | 401 | 선택 |
| terms | POST | /terms/agreements | 약관 동의 제출 | 200 | 400, 401 | Y |
| boards | GET | /boards | 내 보드 목록 (드롭다운) | 200 | 401 | Y |
| boards | POST | /boards | 보드 생성 | 200 | 400, 401 | Y |
| boards | GET | /boards/{boardId} | 보드 상세 조회 (보드 렌더링) | 200 | 401, 404 | Y |
| boards | PATCH | /boards/{boardId} | 보드 이름 변경 | 200 | 400, 401, 404 | Y |
| boards | DELETE | /boards/{boardId} | 보드 삭제 | 200 | 401, 404, 409 | Y |
| boards | PATCH | /boards/{boardId}/layout | 편집 결과 일괄 저장 (편집 모드 종료 시) | 200 | 400, 401, 404 | Y |
| stickers | GET | /stickers/{stickerId} | 리캡 상세 조회 | 200 | 401, 404 | Y |
| stickers | PATCH | /stickers/{stickerId} | 스티커 제목 수정 | 200 | 400, 401, 404 | Y |
| stickers | DELETE | /stickers/{stickerId} | 스티커 묶음 삭제 | 200 | 401, 404 | Y |
| stickers | POST | /stickers/{stickerId}/regenerate | 스티커 이미지 재생성 | 200 | 400, 401, 404, 409 | Y |
| stickers | POST | /stickers/{stickerId}/view | 리캡 열람 처리 (빨간 점 제거) | 200 | 401, 404 | Y |
| analysis | POST | /analysis | 분석 생성 + 업로드 URL 일괄 발급 | 200 | 400, 401, 404, 409, 429 | Y |
| analysis | GET | /analysis/active | 진행 중 분석 조회 (앱 재진입 복구) | 200 | 401 | Y |
| analysis | POST | /analysis/{analysisId}/reissue | 업로드 URL 재발급 | 200 | 401, 404, 409 | Y |
| analysis | POST | /analysis/{analysisId}/start | 업로드 완료 통보 + 분석 시작 | 202 | 401, 404, 409 | Y |
| analysis | GET | /analysis/{analysisId} | 분석 상태 조회 (로딩 화면 폴링) | 200 | 401, 404 | Y |
| analysis | DELETE | /analysis/{analysisId} | 분석 취소 (업로드 중 이탈) | 200 | 401, 404, 409 | Y |

## 4. auth API

### POST /auth/login

- Operation ID: `login`
- Summary: 소셜 로그인 (가입 겸용)

#### Request Spec
- 인증: 불필요

- Body schema: `object`

Request example (카카오 로그인):
```json
{
  "provider": "KAKAO",
  "accessToken": "v1.eyJraWQiOiI5ZjI1MmRhZGQ1ZjIzM2Y5M2QyZmE1MjhkMTJmZWEi..."
}
```

Request example (애플 최초 로그인):
```json
{
  "provider": "APPLE",
  "identityToken": "eyJraWQiOiJXNldjT0tCIiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJodHRwczovL2FwcGxlaWQuYXBwbGUuY29t...",
  "authorizationCode": "c8ef1d2a90b34c5d8e7f6a5b4c3d2e1f0.0.srtwx.k9J8h7G6f5E4d3C2b1A0",
  "rawNonce": "4A7F0E2B-9C31-45D8-A6F2-8B0C3D9E1F52",
  "name": "뽀또"
}
```

Request example (애플 재로그인 (refresh token 보관 중이면 교환 생략 가능)):
```json
{
  "provider": "APPLE",
  "identityToken": "eyJraWQiOiJXNldjT0tCIiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJodHRwczovL2FwcGxlaWQuYXBwbGUuY29t...",
  "authorizationCode": "f2a4b6c8d0e2f4a6b8c0d2e4f6a8b0c2d.0.mnpqr.Z1y2X3w4V5u6T7s8R9q0",
  "rawNonce": "0F9E8D7C-6B5A-4938-A716-05F4E3D2C1B0"
}
```

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 로그인 성공 | `accessToken`, `refreshToken`, `accessTokenExpiresIn`, `isNewUser`, `pendingTerms` |

200 example (신규 가입 - 약관 동의 필요):
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTk4M2YyYS03YzMxLTdiMDItOTNkNC0xZjJlM2Q0YzViNmEi...",
    "refreshToken": "rT8fK2mZ7pL4vQ9xN3jW6yB1cD5gH0aS9uE2iO7kM4wRt",
    "accessTokenExpiresIn": 3600,
    "isNewUser": true,
    "pendingTerms": [
      {
        "id": "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "code": "TOS",
        "version": "1.0",
        "isRequired": true,
        "contentUrl": "https://nexters.notion.site/ppotto-tos",
        "agreed": false
      },
      {
        "id": "01983f2a-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
        "code": "PRIVACY",
        "version": "1.0",
        "isRequired": true,
        "contentUrl": "https://nexters.notion.site/ppotto-privacy",
        "agreed": false
      }
    ]
  },
  "error": null
}
```

200 example (재로그인 - 바로 보드 진입):
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTk4M2YyYS03YzMxLTdiMDItOTNkNC0xZjJlM2Q0YzViNmEi...",
    "refreshToken": "pV5nX8bJ1zC6qF3jL9dR2mY7wK0hG4sA8eU1oT6yN3tPb",
    "accessTokenExpiresIn": 3600,
    "isNewUser": false,
    "pendingTerms": []
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | COMMON-001 | 잘못된 입력입니다. | provider 별 필수 필드 누락, 카카오 요청에 name 포함, 애플 name 공백 |
| 400 | AUTH-006 | 가입에 필요한 이름이 전달되지 않았습니다. | 애플 신규 가입인데 name 미전달. 최초 인가에서 받은 이름을 함께 보내야 합니다. |
| 401 | AUTH-001 | 소셜 로그인 검증에 실패했습니다. | provider 토큰 검증 실패 (만료, 위조, aud/app_id 불일치, nonce 불일치) |
| 401 | AUTH-003 | 스웨거 예시 없음 | 애플 authorization code 교환 실패 (만료 또는 재사용, 최초 로그인만 치명) |
| 403 | AUTH-004 | 이메일 제공에 동의해야 가입할 수 있습니다. | 카카오 이메일 동의 필요. 클라이언트는 account_email 추가 동의 후 재시도합니다. |
| 403 | AUTH-005 | 닉네임 제공에 동의해야 가입할 수 있습니다. | 카카오 닉네임 동의 필요. 클라이언트는 profile_nickname 추가 동의 후 재시도합니다. |

400 COMMON-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-001",
    "message": "잘못된 입력입니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

401 AUTH-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH-001",
    "message": "소셜 로그인 검증에 실패했습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

401 AUTH-003 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH-003",
    "message": "애플 인증 코드 교환에 실패했습니다. 다시 로그인해 주세요.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

403 AUTH-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH-004",
    "message": "이메일 제공에 동의해야 가입할 수 있습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

403 AUTH-005 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH-005",
    "message": "닉네임 제공에 동의해야 가입할 수 있습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

400 AUTH-006 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH-006",
    "message": "가입에 필요한 이름이 전달되지 않았습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 카카오 또는 애플 계정으로 로그인합니다. 처음 보는 계정이면 가입과 기본 보드 생성까지 한 번에 처리됩니다.  **카카오** - 클라이언트가 카카오 SDK로 받은 `accessToken`을 보내면, 서버가 먼저   `/v1/user/access_token_info`로 토큰의 `app_id`가 우리 앱인지 확인합니다   (다른 서비스에서 수집한 토큰으로 로그인하는 토큰 치환 공격 차단, 불일치 시 AUTH-001).   이후 `/v2/user/me`로 회원번호를 조회해 `providerUserId`로 사용합니다. - 이메일은 필수입니다. 동의하지 않았으면 403 `AUTH-004`로 거부되며,   클라이언트는 추가 동의(`account_email` 스코프)를 요청한 뒤 다시 로그인합니다. - 닉네임도 필수입니다. 서버가 `/v2/user/me`의 `kakao_account.profile.nickname`을 이름으로 저장하며,   동의하지 않았으면 403 `AUTH-005`로 거부됩니다. 카카오 요청에 `name`을 보내면 400입니다.  **애플** - `identityToken`(JWT)을 애플 JWKS로 검증합니다 (iss / aud / exp / nonce).   `nonce` 클레임은 함께 보낸 `rawNonce`의 SHA-256 해시와 대조합니다. - 토큰의 `sub`를 `providerUserId`로 사용합니다. - `authorizationCode`는 5분 안에 refresh token으로 교환해 탈퇴(revoke)용으로   보관합니다. 앱스토어 심사 필수 사항이며, 재로그인 시에는 교환이 실패해도   로그인은 통과됩니다. - 이름은 애플이 최초 인가 1회에만 클라이언트에 내려주므로, 최초 로그인 시 `fullName`을 조합해   `name`으로 함께 보내야 합니다. 신규 가입인데 `name`이 없으면 400 `AUTH-006`으로 거부되고,   기존 사용자의 재로그인은 `name` 없이 통과하며 저장된 이름을 유지합니다.  응답의 `pendingTerms`가 비어 있지 않으면 약관 동의 화면으로 이동합니다.

### POST /auth/refresh

- Operation ID: `refreshToken`
- Summary: 토큰 재발급

#### Request Spec
- 인증: 불필요

- Body schema: refreshToken(필수, `string`)

| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| refreshToken | Y | `string` | - | - |

Request example:
```json
{
  "refreshToken": "rT8fK2mZ7pL4vQ9xN3jW6yB1cD5gH0aS9uE2iO7kM4wRt"
}
```

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 재발급 성공 | `accessToken`, `refreshToken`, `accessTokenExpiresIn` |

200 example:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTk4M2YyYS03YzMxLTdiMDItOTNkNC0xZjJlM2Q0YzViNmEi...",
    "refreshToken": "gQ4kD7sF0aZ9xW2cV5bN8mR1tY6uL3pH7eK0iS4jO9nGc",
    "accessTokenExpiresIn": 3600
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | AUTH-002 | 로그인이 만료되었습니다. 다시 로그인해 주세요. | refresh token 만료 또는 위조. 재로그인 필요 |

401 AUTH-002 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH-002",
    "message": "로그인이 만료되었습니다. 다시 로그인해 주세요.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- accessToken이 만료되면(401 COMMON-004) 호출합니다. refreshToken은 매번 새 값으로 교체되므로 응답을 받는 즉시 저장값을 갱신해야 합니다. AUTH-002가 내려오면 다시 로그인해야 합니다.

### POST /auth/logout

- Operation ID: `logout`
- Summary: 로그아웃

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 로그아웃 완료 | `null` |

200 example:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- refresh token만 폐기합니다. 서버 데이터는 유지되므로 재로그인하면 그대로 다시 불러옵니다.

## 5. users API

### GET /users/me

- Operation ID: `getMe`
- Summary: 내 정보 조회 (설정 화면)

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 내 정보 | `id`, `provider`, `email`, `name`, `createdAt` |

200 example (카카오 유저):
```json
{
  "success": true,
  "data": {
    "id": "01983f2a-7c31-7b02-93d4-1f2e3d4c5b6a",
    "provider": "KAKAO",
    "email": "ppotto@kakao.com",
    "name": "뽀또",
    "createdAt": "2026-07-01T09:12:33+09:00"
  },
  "error": null
}
```

200 example (애플 유저 - 이메일 가리기 (private relay)):
```json
{
  "success": true,
  "data": {
    "id": "01983f2a-6b20-7a01-82c3-0e1d2c3b4a59",
    "provider": "APPLE",
    "email": "mxq7r2v9td@privaterelay.appleid.com",
    "name": "홍길동",
    "createdAt": "2026-07-15T21:40:05+09:00"
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 설정 화면의 내 계정 영역에 사용합니다.

### DELETE /users/me

- Operation ID: `deleteMe`
- Summary: 회원 탈퇴

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 탈퇴 완료 | `null` |

200 example:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 애플 계정은 보관 중인 refresh token으로 revoke를 호출합니다 (앱스토어 심사 필수). email과 provider refresh token은 즉시 파기(익명화)하고, 나머지 데이터는 soft delete 후 유예기간이 지나면 GCS 사진 원본까지 배치로 하드 삭제합니다. 같은 계정으로 다시 로그인하면 신규 가입이 됩니다.
- 탈퇴 즉시 서비스 refresh token 세션이 폐기되므로 `POST /auth/refresh`는 `AUTH-002`로 실패하고, 남아 있는 accessToken으로 `GET /users/me`를 호출하면 `USER-001`을 반환합니다.
- 유예기간이 지난 뒤 정리 배치가 보드, 드로잉, 스티커, 리캡, 분석, 사진, 약관 동의 이력을 하드 삭제하고 GCS 사진 원본과 스티커 생성 이미지를 함께 파기합니다. 유예기간 일수와 배치 활성화 여부는 서버 설정(`user.withdrawn-cleanup.*`)으로 관리하며 배치는 기본 비활성입니다. 이 배치는 클라이언트가 호출하는 API가 아니며 본 문서의 엔드포인트를 추가하지 않습니다.

## 6. terms API

### GET /terms

- Operation ID: `getTerms`
- Summary: 현재 유효 약관 목록

#### Request Spec
- 인증: 불필요
- 선택 헤더: `Authorization: Bearer {accessToken}`. 유효한 토큰을 보내면 사용자별 동의 상태를 반환합니다.

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 약관 목록 | array |

200 example:
```json
{
  "success": true,
  "data": [
    {
      "id": "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
      "code": "TOS",
      "version": "1.0",
      "isRequired": true,
      "contentUrl": "https://nexters.notion.site/ppotto-tos",
      "agreed": true
    },
    {
      "id": "01983f2a-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
      "code": "PRIVACY",
      "version": "1.1",
      "isRequired": true,
      "contentUrl": "https://nexters.notion.site/ppotto-privacy",
      "agreed": false
    }
  ],
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 선택적으로 전달한 accessToken이 만료되었거나 유효하지 않음 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- code별로 현재 유효한 버전을 1건씩 반환합니다.
- 인증하지 않은 요청은 모든 약관의 `agreed`를 `false`로 반환합니다.
- 인증한 요청에서 `agreed`가 `false`면 재동의 대상입니다.
- 설정 화면의 약관 링크에도 `contentUrl`을 사용합니다.

### POST /terms/agreements

- Operation ID: `agreeTerms`
- Summary: 약관 동의 제출

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

- Body schema: termIds(필수, `string`[])

| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| termIds | Y | `string`[] | - | - |

Request example:
```json
{
  "termIds": [
    "01983f2a-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
    "01983f2a-2b3c-7d4e-9f5a-6b7c8d9e0f1a"
  ]
}
```

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 동의 완료 | `null` |

200 example:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | TERM-001 | 필수 약관에 동의해야 합니다. | 필수 약관 미포함 |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |

400 TERM-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "TERM-001",
    "message": "필수 약관에 동의해야 합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 동의 이력을 누적합니다. 이미 동의한 약관은 무시되며(멱등), 필수 약관이 빠져 있으면 TERM-001을 반환합니다.

## 7. boards API

### GET /boards

- Operation ID: `listBoards`
- Summary: 내 보드 목록 (드롭다운)

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 보드 목록 | array |

200 example:
```json
{
  "success": true,
  "data": [
    {
      "id": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
      "name": "Board 7"
    },
    {
      "id": "01983f2a-4d5e-7f6a-b7c8-9d0e1f2a3b4c",
      "name": "여름 휴가"
    }
  ],
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 상단 타이틀 드롭다운의 보드 전환 목록입니다. id(uuidv7) 오름차순 = 생성순입니다.

### POST /boards

- Operation ID: `createBoard`
- Summary: 보드 생성

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

- Body schema: name(선택, `string`)

| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| name | N | `string` | - | - |

Request example:
```json
{
  "name": "여름 휴가"
}
```

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 생성된 보드 | `id`, `name` |

200 example:
```json
{
  "success": true,
  "data": {
    "id": "01983f2a-4d5e-7f6a-b7c8-9d0e1f2a3b4c",
    "name": "여름 휴가"
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | COMMON-001 | 스웨거 예시 없음 | 이름 형식 오류 (10자 초과 등) |
| 400 | BOARD-003 | 보드는 최대 100개까지 만들 수 있습니다. | 보드 개수 제한(100개) 초과 |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |

400 BOARD-003 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD-003",
    "message": "보드는 최대 100개까지 만들 수 있습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 보드를 새로 만듭니다. 유저당 최대 100개입니다. name을 생략하면 서버가 기본 이름을 부여합니다.

### GET /boards/{boardId}

- Operation ID: `getBoard`
- Summary: 보드 상세 조회 (보드 렌더링)

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | boardId | Y | `string` | 01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 보드 상태 | `id`, `name`, `stickers`, `drawings` |

200 example:
```json
{
  "success": true,
  "data": {
    "id": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
    "name": "Board 7",
    "stickers": [
      {
        "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "title": "동물 밈 짤줍",
        "isNew": false,
        "type": "IMAGE",
        "imageUrl": "https://storage.googleapis.com/ppotto-stickers/01983f2b-1a2b.png?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=3600&X-Goog-Signature=8f3a...",
        "textContent": null,
        "posX": 62.5,
        "posY": 318,
        "scale": 0.8,
        "rotation": -12,
        "zIndex": 3,
        "badgeOffsetX": -24,
        "badgeOffsetY": 96,
        "badgeRotation": 0
      },
      {
        "id": "01983f2b-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
        "title": "언제까지 일해요",
        "isNew": true,
        "type": "TEXT",
        "imageUrl": null,
        "textContent": "whats in my mac",
        "posX": 228,
        "posY": 250.5,
        "scale": 1,
        "rotation": 8.5,
        "zIndex": 5,
        "badgeOffsetX": 12,
        "badgeOffsetY": -60,
        "badgeRotation": -4
      }
    ],
    "drawings": [
      {
        "id": "01983f2c-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "scope": "STICKER",
        "stickerId": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "stroke": {
          "points": [
            [
              10.5,
              22
            ],
            [
              14.2,
              25.1
            ],
            [
              19.8,
              27.4
            ]
          ]
        },
        "color": "#FFD400",
        "strokeWidth": 4
      },
      {
        "id": "01983f2c-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
        "scope": "BOARD",
        "stickerId": null,
        "stroke": {
          "points": [
            [
              120,
              480.5
            ],
            [
              126.4,
              483.2
            ],
            [
              133.1,
              481
            ]
          ]
        },
        "color": "#FFFFFF",
        "strokeWidth": 2.5
      }
    ]
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | BOARD-002 | 보드를 찾을 수 없습니다. | 보드 없음 또는 소유자 불일치 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 BOARD-002 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD-002",
    "message": "보드를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 보드, 스티커, 드로잉을 한 번에 반환합니다. isNew가 빨간 점 표시 여부입니다. imageUrl은 만료가 있으므로 보드에 진입할 때마다 새로 조회합니다.

### PATCH /boards/{boardId}

- Operation ID: `renameBoard`
- Summary: 보드 이름 변경

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | boardId | Y | `string` | 01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b | - |

- Body schema: name(필수, `string`)

| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| name | Y | `string` | - | - |

Request example:
```json
{
  "name": "뽀또의 보드"
}
```

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 변경된 보드 | `id`, `name` |

200 example:
```json
{
  "success": true,
  "data": {
    "id": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
    "name": "뽀또의 보드"
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | COMMON-001 | 잘못된 입력입니다. | 빈 이름 또는 10자 초과 |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | BOARD-002 | 보드를 찾을 수 없습니다. | 보드 없음 또는 소유자 불일치 |

400 COMMON-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-001",
    "message": "잘못된 입력입니다.",
    "fieldErrors": [
      {
        "field": "name",
        "value": "열자가넘는아주긴보드이름",
        "reason": "크기가 1에서 10 사이여야 합니다"
      }
    ],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 BOARD-002 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD-002",
    "message": "보드를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 최대 10자입니다.

### DELETE /boards/{boardId}

- Operation ID: `deleteBoard`
- Summary: 보드 삭제

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | boardId | Y | `string` | 01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 삭제 완료 | `null` |

200 example:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | BOARD-002 | 보드를 찾을 수 없습니다. | 보드 없음 또는 소유자 불일치 |
| 409 | BOARD-004 | 마지막 보드는 삭제할 수 없습니다. | 마지막 보드는 삭제 불가 |
| 409 | BOARD-005 | 스웨거 예시 없음 | 진행 중인 분석이 이 보드를 대상으로 함 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 BOARD-002 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD-002",
    "message": "보드를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

409 BOARD-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD-004",
    "message": "마지막 보드는 삭제할 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

409 BOARD-005 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD-005",
    "message": "분석이 진행 중인 보드는 삭제할 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 보드와 그 위의 스티커, 리캡(코멘트·사진 연결), 드로잉을 함께 삭제합니다. 마지막 남은 보드는 삭제할 수 없고(BOARD-004), 진행 중인 분석이 이 보드를 대상으로 하면 분석이 끝나거나 취소된 뒤에 삭제할 수 있습니다(BOARD-005).

### PATCH /boards/{boardId}/layout

- Operation ID: `saveBoardLayout`
- Summary: 편집 결과 일괄 저장 (편집 모드 종료 시)

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | boardId | Y | `string` | 01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b | - |

- Body schema: stickers(선택, `object`[]), drawings(선택, `object`)

| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| stickers | N | `object`[] | - | - |
| drawings | N | `object` | - | - |

Request example (스티커 이동 모드 종료):
```json
{
  "stickers": [
    {
      "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
      "posX": 80,
      "posY": 290.5,
      "scale": 1.1,
      "rotation": -8,
      "zIndex": 6,
      "badgeOffsetX": -24,
      "badgeOffsetY": 96,
      "badgeRotation": 0
    }
  ]
}
```

Request example (텍스트 모드 종료 (제목 + 뱃지 배치)):
```json
{
  "stickers": [
    {
      "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
      "title": "고양이 모음집",
      "posX": 80,
      "posY": 290.5,
      "scale": 1.1,
      "rotation": -8,
      "zIndex": 6,
      "badgeOffsetX": -30,
      "badgeOffsetY": 102,
      "badgeRotation": 6
    }
  ]
}
```

Request example (드로잉 모드 종료 (생성 2건, 삭제 1건)):
```json
{
  "drawings": {
    "created": [
      {
        "id": "01983f2c-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
        "scope": "STICKER",
        "stickerId": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "stroke": {
          "points": [
            [
              10.5,
              22
            ],
            [
              14.2,
              25.1
            ],
            [
              19.8,
              27.4
            ]
          ]
        },
        "color": "#FFD400",
        "strokeWidth": 4
      },
      {
        "id": "01983f2c-4d5e-7f6a-b7c8-9d0e1f2a3b4c",
        "scope": "BOARD",
        "stroke": {
          "points": [
            [
              200,
              512
            ],
            [
              204.8,
              515.5
            ]
          ]
        },
        "color": "#FF5A5A",
        "strokeWidth": 3
      }
    ],
    "deletedIds": [
      "01983f2c-2b3c-7d4e-9f5a-6b7c8d9e0f1a"
    ]
  }
}
```

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 저장 완료 | `null` |

200 example:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | COMMON-001 | 스웨거 예시 없음 | 필드 형식 오류 (제목 15자 초과 등) |
| 400 | BOARD-001 | 편집할 수 없는 항목이 포함되어 있습니다. | 소유하지 않은 항목 포함 |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | BOARD-002 | 보드를 찾을 수 없습니다. | 보드 없음 또는 소유자 불일치 |

400 BOARD-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD-001",
    "message": "편집할 수 없는 항목이 포함되어 있습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 BOARD-002 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD-002",
    "message": "보드를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 편집 모드를 끌 때 해당 모드에서 바뀐 것만 보냅니다. 모든 필드는 선택입니다.  | 편집 모드 | 보내는 필드 | |---|---| | 스티커 이동 | `stickers` (배치) | | 텍스트 | `stickers` (제목 + 뱃지 배치) | | 드로잉 | `drawings.created` / `drawings.deletedIds` |  드로잉 id는 클라이언트가 uuidv7로 생성해 보내고 서버는 upsert합니다. 타임아웃 후 재시도해도 중복 저장되지 않습니다 (멱등). 본인 소유가 아닌 id가 섞여 있으면 `BOARD-001`로 전체 거부됩니다 (부분 저장 없음).

## 8. stickers API

### GET /stickers/{stickerId}

- Operation ID: `getStickerRecap`
- Summary: 리캡 상세 조회

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | stickerId | Y | `string` | 01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 리캡 상세 | `sticker`, `summary`, `comments`, `photos` |

200 example:
```json
{
  "success": true,
  "data": {
    "sticker": {
      "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
      "title": "동물 밈 짤줍",
      "isNew": false,
      "type": "IMAGE",
      "imageUrl": "https://storage.googleapis.com/ppotto-stickers/01983f2b-1a2b.png?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=3600&X-Goog-Signature=8f3a...",
      "textContent": null,
      "posX": 62.5,
      "posY": 318,
      "scale": 0.8,
      "rotation": -12,
      "zIndex": 3,
      "badgeOffsetX": -24,
      "badgeOffsetY": 96,
      "badgeRotation": 0
    },
    "summary": "웃기고 귀여우면 일단 주워요",
    "comments": [
      {
        "id": "01983f2d-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "content": "야옹~",
        "posX": -96,
        "posY": -150
      },
      {
        "id": "01983f2d-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
        "content": "또 주웠네!",
        "posX": -104,
        "posY": 62
      },
      {
        "id": "01983f2d-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
        "content": "복슬복슬",
        "posX": 98,
        "posY": 18
      },
      {
        "id": "01983f2d-4d5e-7f6a-b7c8-9d0e1f2a3b4c",
        "content": "냥집사"
      },
      {
        "id": "01983f2d-5e6f-7a7b-c8d9-0e1f2a3b4c5d",
        "content": "웃긴 동물들"
      },
      {
        "id": "01983f2d-6f7a-7b8c-d9e0-1f2a3b4c5d6e",
        "content": "당신은 밈 수집가!"
      },
      {
        "id": "01983f2d-7a8b-7c9d-e0f1-2a3b4c5d6e7f",
        "content": "복슬복슬"
      },
      {
        "id": "01983f2d-8b9c-7d0e-f1a2-3b4c5d6e7f8a",
        "content": "저장한 동물 짤 중 62%가 고양이다냥!"
      },
      {
        "id": "01983f2d-9c0d-7e1f-a2b3-4c5d6e7f8a9b",
        "content": "감도 높은 취향"
      },
      {
        "id": "01983f2d-a0d1-7f2a-b3c4-5d6e7f8a9b0c",
        "content": "밈잘알"
      },
      {
        "id": "01983f2d-b1e2-7a3b-c4d5-6e7f8a9b0c1d",
        "content": "밈 고르는 안목 보소"
      },
      {
        "id": "01983f2d-c2f3-7b4c-d5e6-7f8a9b0c1d2e",
        "content": "근데 소랑 돌고래가 같이 나는 사진은 뭐임? 🤔"
      }
    ],
    "photos": [
      {
        "id": "01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "imageUrl": "https://storage.googleapis.com/ppotto-photos/01983f2e-1a2b.jpg?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=3600&X-Goog-Signature=1c9b...",
        "takenAt": "2026-06-14T13:22:10+09:00"
      },
      {
        "id": "01983f2e-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
        "imageUrl": "https://storage.googleapis.com/ppotto-photos/01983f2e-2b3c.jpg?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=3600&X-Goog-Signature=7d2e...",
        "takenAt": "2026-07-02T19:05:44+09:00"
      }
    ]
  },
  "error": null
}
```

#### Notes
- `photos`는 연사(버스트) 그룹으로 촬영된 사진이라도 그룹당 대표 사진(`is_representative = true`) 1장만 포함합니다. 단독으로 촬영된 사진은 항상 포함됩니다.

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | STICKER-001 | 스티커를 찾을 수 없습니다. | 스티커 없음 또는 소유자 불일치 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 STICKER-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "STICKER-001",
    "message": "스티커를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 리캡 화면에 필요한 데이터를 모두 반환합니다. 스티커 1개 = 리캡 1개입니다. photos는 takenAt, id 오름차순이며 기간 표시는 클라이언트가 계산합니다. 빨간 점 제거는 별도로 /view를 호출합니다.
- `summary`는 `한 줄 요약` 라벨 아래의 강조 문장입니다. 스티커당 정확히 1개이고 항상 채워져 있습니다. 제목 뱃지인 `sticker.title`과는 다른 값이므로 둘을 섞어 쓰면 안 됩니다.
- `comments`는 두 종류가 한 배열에 섞여 옵니다. **구분 기준은 `posX`(와 `posY`)의 존재 여부 하나뿐입니다.**

| 구분 | 화면 위치 | `posX` / `posY` |
| --- | --- | --- |
| 말풍선 | 스티커 주변에 떠 있는 말풍선 | 값이 있음. 스티커 기준 상대 좌표 |
| 키워드 칩 | 하단 `테마 분석` 영역의 칩 | 없음 (null) |

  `posX`와 `posY`는 항상 함께 있거나 함께 없습니다. 한쪽만 오는 응답은 없습니다. 서버 응답은 null 필드를 직렬화하지 않으므로 키워드 칩에는 `posX` / `posY` 키 자체가 나타나지 않습니다. 따라서 클라이언트는 `posX` 키가 없으면 키워드 칩으로 처리하면 됩니다. 이전 명세의 `isFloat` 필드는 제거되었습니다.
- `테마 속 사진`의 개수는 클라이언트가 `photos` 배열 길이로 계산합니다. 페이지네이션이 없으므로 서버는 개수를 따로 내려주지 않고 언제나 전체 사진을 반환합니다.

### PATCH /stickers/{stickerId}

- Operation ID: `updateStickerTitle`
- Summary: 스티커 제목 수정

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | stickerId | Y | `string` | 01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f | - |

- Body schema: title(필수, `string`)

| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| title | Y | `string` | - | - |

Request example:
```json
{
  "title": "고양이 모음집"
}
```

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 수정 완료 | `id`, `title` |

200 example:
```json
{
  "success": true,
  "data": {
    "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
    "title": "고양이 모음집"
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | COMMON-001 | 잘못된 입력입니다. | 빈 제목 또는 15자 초과 |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | STICKER-001 | 스티커를 찾을 수 없습니다. | 스티커 없음 또는 소유자 불일치 |

400 COMMON-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-001",
    "message": "잘못된 입력입니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 STICKER-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "STICKER-001",
    "message": "스티커를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 리캡 화면의 연필로 제목을 수정할 때 사용합니다. 보드 편집 중에는 layout API를 사용합니다. 제목을 수정해도 빨간 점 상태는 바뀌지 않습니다. 최대 15자.

### DELETE /stickers/{stickerId}

- Operation ID: `deleteSticker`
- Summary: 스티커 묶음 삭제

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | stickerId | Y | `string` | 01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 삭제 완료 | `null` |

200 example:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | STICKER-001 | 스티커를 찾을 수 없습니다. | 스티커 없음 또는 소유자 불일치 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 STICKER-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "STICKER-001",
    "message": "스티커를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 스티커 묶음을 삭제합니다. 연결된 드로잉과 리캡(코멘트, 사진 연결)도 함께 삭제됩니다.

### POST /stickers/{stickerId}/regenerate

- Operation ID: `regenerate`
- Summary: 스티커 이미지 재생성

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | stickerId | Y | `string` | 01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 재생성 완료 | `sticker`, `summary`, `comments`, `photos` |

200 example:
```json
{
  "success": true,
  "data": {
    "sticker": {
      "id": "01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
      "title": "동물 밈 짤줍",
      "isNew": false,
      "type": "IMAGE",
      "imageUrl": "https://storage.googleapis.com/ppotto-stickers/01983f2b-1a2b.png?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=3600&X-Goog-Signature=8f3a...",
      "textContent": null,
      "posX": 62.5,
      "posY": 318,
      "scale": 0.8,
      "rotation": -12,
      "zIndex": 3,
      "badgeOffsetX": -24,
      "badgeOffsetY": 96,
      "badgeRotation": 0
    },
    "summary": "웃기고 귀여우면 일단 주워요",
    "comments": [
      {
        "id": "01983f2d-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "content": "야옹~",
        "posX": -96,
        "posY": -150
      },
      {
        "id": "01983f2d-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
        "content": "또 주웠네!",
        "posX": -104,
        "posY": 62
      },
      {
        "id": "01983f2d-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
        "content": "복슬복슬",
        "posX": 98,
        "posY": 18
      },
      {
        "id": "01983f2d-4d5e-7f6a-b7c8-9d0e1f2a3b4c",
        "content": "냥집사"
      }
    ],
    "photos": [
      {
        "id": "01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "imageUrl": "https://storage.googleapis.com/ppotto-photos/01983f2e-1a2b.jpg?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=3600&X-Goog-Signature=1c9b...",
        "takenAt": "2026-06-14T13:22:10+09:00"
      },
      {
        "id": "01983f2e-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
        "imageUrl": "https://storage.googleapis.com/ppotto-photos/01983f2e-2b3c.jpg?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=3600&X-Goog-Signature=7d2e...",
        "takenAt": "2026-07-02T19:05:44+09:00"
      }
    ]
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | COMMON-001 | 잘못된 입력입니다. | 이미지형 스티커가 아니거나 재생성 가능한 사진 구성이 없음 |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | STICKER-001 | 스티커를 찾을 수 없습니다. | 스티커 없음 또는 소유자 불일치 |
| 409 | STICKER-002 | 이미 재생성이 진행 중입니다. | 같은 스티커에 대한 재생성이 이미 진행 중 |

400 COMMON-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-001",
    "message": "잘못된 입력입니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 STICKER-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "STICKER-001",
    "message": "스티커를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

409 STICKER-002 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "STICKER-002",
    "message": "이미 재생성이 진행 중입니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 이미지형 스티커만 재생성할 수 있습니다. 텍스트 스티커는 400 `COMMON-001`을 반환합니다.
- 기존 리캡의 사진 구성은 유지하고, 서버가 그 사진들 중 새 원본 사진과 피사체를 선택해 스티커 이미지와 `sourcePhotoId`만 교체합니다.
- `title`, `summary`, `comments`, 보드 배치값, 빨간 점 상태는 변경하지 않습니다. 응답은 재생성 후 리캡 상세와 같은 형태입니다.
- 같은 스티커에 대한 재생성은 동시에 하나만 진행됩니다. 진행 중에 다시 요청하면 409 `STICKER-002`를 반환하며, 성공적으로 끝난 직후에는 쿨다운 없이 바로 다음 재생성을 요청할 수 있습니다.

### POST /stickers/{stickerId}/view

- Operation ID: `markStickerViewed`
- Summary: 리캡 열람 처리 (빨간 점 제거)

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | stickerId | Y | `string` | 01983f2b-1a2b-7c3d-8e4f-5a6b7c8d9e0f | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 처리 완료 | `null` |

200 example:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | STICKER-001 | 스티커를 찾을 수 없습니다. | 스티커 없음 또는 소유자 불일치 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 STICKER-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "STICKER-001",
    "message": "스티커를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 리캡 진입 시 호출해 빨간 점을 제거합니다. 멱등이라 여러 번 호출해도 안전합니다.

## 9. analysis API

### POST /analysis

- Operation ID: `createAnalysis`
- Summary: 분석 생성 + 업로드 URL 일괄 발급

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

- Body schema: boardId(필수, `string`), photos(필수, `object`[])

| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| boardId | Y | `string` | - | 결과 스티커가 붙을 보드 |
| photos | Y | `object`[] | - | 촬영 시각 오름차순으로 보내는 사진 그룹. 펼친 사진 수는 총 90~100장 |
| photos[].items | Y | `object`[] | - | 그룹에 속한 사진들. 원소 1개면 단독 사진, 2개 이상이면 연사 그룹 |
| photos[].items[].takenAt | Y | `string` | - | 사진 촬영 시각 |
| photos[].items[].contentType | Y | `string` | `image/jpeg`, `image/png`, `image/heic` | 지원 형식. 업로드 시 Content-Type과 일치해야 함 |
| photos[].items[].isRepresentative | Y | `boolean` | - | 화면에 보여줄 대표 사진 여부. 단독 사진은 true로 처리되고, 연사 그룹은 정확히 1장만 true |

Request example:
```json
{
  "boardId": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
  "photos": [
    {
      "items": [
        {
          "takenAt": "2026-06-14T13:22:10+09:00",
          "contentType": "image/jpeg",
          "isRepresentative": true
        }
      ]
    },
    {
      "items": [
        {
          "takenAt": "2026-06-14T13:24:02+09:00",
          "contentType": "image/heic",
          "isRepresentative": true
        },
        {
          "takenAt": "2026-06-14T13:24:03+09:00",
          "contentType": "image/heic",
          "isRepresentative": false
        }
      ]
    },
    {
      "items": [
        {
          "takenAt": "2026-07-02T19:05:44+09:00",
          "contentType": "image/jpeg",
          "isRepresentative": true
        }
      ]
    }
  ]
}
```

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 발급 완료 (status=UPLOADING) | `analysisId`, `uploads` |

200 example:
```json
{
  "success": true,
  "data": {
    "analysisId": "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
    "uploads": [
      {
        "photoId": "01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "uploadUrl": "https://storage.googleapis.com/ppotto-photos/01983f2e-1a2b.jpg?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=900&X-Goog-Signature=3e8a..."
      },
      {
        "photoId": "01983f2e-2b3c-7d4e-9f5a-6b7c8d9e0f1a",
        "uploadUrl": "https://storage.googleapis.com/ppotto-photos/01983f2e-2b3c.heic?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=900&X-Goog-Signature=b41c..."
      }
    ]
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | ANALYSIS-001 | 사진은 90장에서 100장 사이여야 합니다. | 사진 수 정책 위반 (90~100) |
| 400 | ANALYSIS-009 | 연사 그룹은 대표 사진을 정확히 1장 포함해야 합니다. | 연사 그룹의 대표 사진이 0장 또는 2장 이상 |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | BOARD-002 | 보드를 찾을 수 없습니다. | 보드 없음 또는 소유자 불일치 |
| 409 | ANALYSIS-002 | 이미 진행 중인 분석이 있습니다. | 진행 중인 분석 존재 |
| 429 | ANALYSIS-006 | 오늘 분석 가능 횟수를 모두 사용했습니다. | 일일 분석 횟수 초과 |

400 ANALYSIS-001 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-001",
    "message": "사진은 90장에서 100장 사이여야 합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

400 ANALYSIS-009 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-009",
    "message": "연사 그룹은 대표 사진을 정확히 1장 포함해야 합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 BOARD-002 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BOARD-002",
    "message": "보드를 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

409 ANALYSIS-002 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-002",
    "message": "이미 진행 중인 분석이 있습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

429 ANALYSIS-006 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-006",
    "message": "오늘 분석 가능 횟수를 모두 사용했습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- "이 사진으로 보드 만들기" 시점에 호출합니다. photos 행을 미리 만들고 사진별 업로드 URL(만료 15분)을 발급합니다.
- 클라이언트는 각 URL로 GCS에 직접 PUT 합니다. Content-Type은 요청값과 일치해야 합니다.
- 업로드 URL에는 장당 15MB 크기 제한이 서명되어 있습니다 (x-goog-content-length-range).
- 업로드된 사진은 전부 분석에 사용합니다.
- `photos`는 사진 그룹 배열입니다. 그룹 원소 1개는 단독 사진, 2개 이상은 연사 그룹입니다.
- `burstGroupId`는 클라이언트가 보내지 않고 서버가 연사 그룹마다 발급합니다.
- 단독 사진은 항상 대표 사진으로 처리됩니다. 연사 그룹은 그룹 안에서 정확히 1장만 `isRepresentative=true`여야 합니다.
- 분석은 사용자당 하루 5회로 제한됩니다 (운영 설정값). 초과 시 429 ANALYSIS-006.
- 진행 중인 분석이 있으면 409가 반환됩니다 (보드와 무관하게 유저당 1개). `/analysis/active`로 복귀하거나 취소 후 다시 시도합니다.
- 아래 예시는 지면상 3장만 표기했지만 실제 요청은 펼친 사진 수 기준 90~100장입니다.

### GET /analysis/active

- Operation ID: `getActiveAnalysis`
- Summary: 진행 중 분석 조회 (앱 재진입 복구)

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 진행 중 분석 또는 null | `id`, `boardId`, `status`, `progress`, `failedReason`, `startedAt`, `completedAt` |

200 example (분석 진행 중):
```json
{
  "success": true,
  "data": {
    "id": "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
    "boardId": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
    "status": "ANALYZING",
    "progress": 45,
    "failedReason": null,
    "startedAt": "2026-07-27T14:02:11+09:00",
    "completedAt": null
  },
  "error": null
}
```

200 example (진행 중 분석 없음):
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 앱을 껐다 켰을 때 진행 중인 분석이 있는지 확인합니다. 없으면 data가 null입니다. UPLOADING 상태면 `/reissue`로 URL을 재발급받아 이어서 올리거나, 취소하고 새로 시작합니다.

### POST /analysis/{analysisId}/reissue

- Operation ID: `reissueUploadUrls`
- Summary: 업로드 URL 재발급

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | analysisId | Y | `string` | 01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 재발급된 URL 목록 | `uploads` |

200 example:
```json
{
  "success": true,
  "data": {
    "uploads": [
      {
        "photoId": "01983f2e-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
        "uploadUrl": "https://storage.googleapis.com/ppotto-photos/01983f2e-1a2b.jpg?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Expires=900&X-Goog-Signature=a77d..."
      }
    ]
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | ANALYSIS-005 | 분석을 찾을 수 없습니다. | 분석 없음 또는 소유자 불일치 |
| 409 | ANALYSIS-003 | 이미 분석이 시작되었습니다. | 이미 시작되었거나 종료된 분석 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 ANALYSIS-005 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-005",
    "message": "분석을 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

409 ANALYSIS-003 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-003",
    "message": "이미 분석이 시작되었습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 분석 생성 응답을 유실했거나 업로드 URL(15분)이 만료됐을 때 호출합니다. 아직 업로드가 확인되지 않은(PENDING) 사진의 URL만 새로 발급합니다. UPLOADING 상태에서만 사용할 수 있습니다.

### POST /analysis/{analysisId}/start

- Operation ID: `startAnalysis`
- Summary: 업로드 완료 통보 + 분석 시작

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | analysisId | Y | `string` | 01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 202 | 분석 시작됨 | `uploadedCount`, `failedCount`, `failedPhotoIds` |

202 example:
```json
{
  "success": true,
  "data": {
    "uploadedCount": 97,
    "failedCount": 1,
    "failedPhotoIds": [
      "01983f2e-9f8e-7d6c-b5a4-3c2b1a0f9e8d"
    ]
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | ANALYSIS-005 | 분석을 찾을 수 없습니다. | 분석 없음 또는 소유자 불일치 |
| 409 | ANALYSIS-003 | 이미 분석이 시작되었습니다. | 이미 시작되었거나 종료된 분석 |
| 409 | ANALYSIS-008 | 업로드된 사진이 없습니다. | 업로드 완료된 사진 0장 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 ANALYSIS-005 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-005",
    "message": "분석을 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

409 ANALYSIS-003 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-003",
    "message": "이미 분석이 시작되었습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

409 ANALYSIS-008 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-008",
    "message": "업로드된 사진이 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 업로드를 모두 마친 뒤 호출합니다. 서버는 GCS 오브젝트 존재를 확인해 없는 사진은 FAILED로 제외하고 분석 파이프라인을 시작합니다.  파이프라인: 주제 분류 → 스티커 생성 → 리캡 코멘트 생성 → 보드 배치  배치 단계에서 새 스티커는 중앙에 크게, 기존 스티커는 외곽에 작게 재배치됩니다. 이후 진행 상황은 GET 폴링으로 확인합니다.

### GET /analysis/{analysisId}

- Operation ID: `getAnalysis`
- Summary: 분석 상태 조회 (로딩 화면 폴링)

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | analysisId | Y | `string` | 01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 분석 상태 | `id`, `boardId`, `status`, `progress`, `failedReason`, `startedAt`, `completedAt` |

200 example (분석 중):
```json
{
  "success": true,
  "data": {
    "id": "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
    "boardId": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
    "status": "ANALYZING",
    "progress": 45,
    "failedReason": null,
    "startedAt": "2026-07-27T14:02:11+09:00",
    "completedAt": null
  },
  "error": null
}
```

200 example (완료):
```json
{
  "success": true,
  "data": {
    "id": "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
    "boardId": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
    "status": "COMPLETED",
    "progress": 100,
    "failedReason": null,
    "startedAt": "2026-07-27T14:02:11+09:00",
    "completedAt": "2026-07-27T14:03:38+09:00"
  },
  "error": null
}
```

200 example (실패):
```json
{
  "success": true,
  "data": {
    "id": "01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
    "boardId": "01983f2a-3c4d-7e5f-a6b7-8c9d0e1f2a3b",
    "status": "FAILED",
    "progress": 60,
    "failedReason": "AI 분석 호출이 반복 실패했습니다.",
    "startedAt": "2026-07-27T14:02:11+09:00",
    "completedAt": null
  },
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | ANALYSIS-005 | 분석을 찾을 수 없습니다. | 분석 없음 또는 소유자 불일치 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 ANALYSIS-005 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-005",
    "message": "분석을 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- 로딩 화면에서 2~3초 간격으로 폴링합니다. 단계 문구는 클라이언트가 progress 구간으로 매핑합니다. COMPLETED가 되면 보드를 다시 조회합니다.

### DELETE /analysis/{analysisId}

- Operation ID: `cancelAnalysis`
- Summary: 분석 취소 (업로드 중 이탈)

#### Request Spec
- 인증: 필요 (`Authorization: Bearer {accessToken}`)

| In | Name | Required | Type | Example | Description |
| --- | --- | --- | --- | --- | --- |
| path | analysisId | Y | `string` | 01983f2f-1a2b-7c3d-8e4f-5a6b7c8d9e0f | - |

- Body: 없음

#### Success Spec
| Status | Description | Data |
| --- | --- | --- |
| 200 | 취소 완료 | `null` |

200 example:
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

#### Failure Spec
| Status | Error Code | Message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | COMMON-004 | 인증이 필요합니다. | 인증 필요 (Authorization 헤더 누락 또는 accessToken 만료) |
| 404 | ANALYSIS-005 | 분석을 찾을 수 없습니다. | 분석 없음 또는 소유자 불일치 |
| 409 | ANALYSIS-004 | 분석이 시작되어 취소할 수 없습니다. | 이미 분석이 시작된 세션 |

401 COMMON-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-004",
    "message": "인증이 필요합니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

404 ANALYSIS-005 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-005",
    "message": "분석을 찾을 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

409 ANALYSIS-004 example:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ANALYSIS-004",
    "message": "분석이 시작되어 취소할 수 없습니다.",
    "fieldErrors": [],
    "timestamp": "2026-07-27T05:02:11Z"
  }
}
```

#### Notes
- UPLOADING 상태에서만 취소할 수 있습니다. FAILED(failedReason=CANCELED)로 기록되어 점유가 풀립니다. start 이후에는 파이프라인이 끝까지 돕니다. 취소하지 못하고 죽은 분석은 서버 배치가 정리합니다 (failedReason=EXPIRED).

## 10. 공통 모델

### ApiResponse
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| success | Y | `boolean` | - | - |
| data | N | `object` | - | - |
| error | N | `object` | - | - |

### ApiErrorResponse
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| success | Y | `boolean` | - | - |
| data | N | `object` | - | - |
| error | Y | `object` | - | global/error/ErrorResponse.kt 와 동일 구조 |

### KakaoLoginRequest
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| provider | Y | `string` | KAKAO | - |
| accessToken | Y | `string` | - | 카카오 SDK가 발급한 OAuth access token |

### AppleLoginRequest
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| provider | Y | `string` | APPLE | - |
| identityToken | Y | `string` | - | 애플 로그인 결과로 받은 identity token (JWT) |
| authorizationCode | Y | `string` | - | refresh token 교환용입니다. 발급 후 5분, 1회만 유효합니다. |
| rawNonce | Y | `string` | - | 클라이언트가 생성한 원본 nonce. 애플 로그인 요청에는 SHA-256 해시를 넣고 서버에는 원본을 보냅니다. |

### TokenPair
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| accessToken | Y | `string` | - | JWT. Authorization Bearer 헤더에 넣습니다. |
| refreshToken | Y | `string` | - | 서버가 생성한 랜덤 값입니다 (JWT 아님). Keychain 등 보안 저장소에 보관합니다. |
| accessTokenExpiresIn | Y | `integer` | - | accessToken 만료까지 남은 초 |

### LoginResult
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| accessToken | Y | `string` | - | JWT. Authorization Bearer 헤더에 넣습니다. |
| refreshToken | Y | `string` | - | 서버가 생성한 랜덤 값입니다 (JWT 아님). Keychain 등 보안 저장소에 보관합니다. |
| accessTokenExpiresIn | Y | `integer` | - | accessToken 만료까지 남은 초 |
| isNewUser | Y | `boolean` | - | - |
| pendingTerms | Y | `object`[] | - | 동의가 필요한 현재 버전 약관. 비어 있으면 바로 진입 |

### User
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| provider | Y | `string` | KAKAO, APPLE | - |
| email | Y | `string` | - | 항상 존재합니다. 애플 이메일 가리기 유저는 private relay 주소입니다. |
| createdAt | Y | `string` | - | - |

### Term
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| code | Y | `string` | - | TOS |
| version | Y | `string` | - | 1.0 |
| isRequired | Y | `boolean` | - | - |
| contentUrl | N | `string \| null` | - | 노션 등 외부 문서 링크 |
| agreed | Y | `boolean` | - | 요청 사용자의 동의 여부 |

### Board
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| name | Y | `string` | - | - |

### BoardDetail
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| name | Y | `string` | - | - |
| stickers | Y | `object`[] | - | - |
| drawings | Y | `object`[] | - | - |

### Sticker
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| title | Y | `string` | - | 제목 뱃지 문구이자 리캡 제목 |
| isNew | Y | `boolean` | - | viewed_at IS NULL. 뱃지에 빨간 점 표시 |
| type | Y | `string` | IMAGE, TEXT | - |
| imageUrl | N | `string \| null` | - | IMAGE 형. 누끼 PNG 의 읽기용 signed URL (만료 1시간) |
| textContent | N | `string \| null` | - | TEXT 형 문구 |
| posX | Y | `number` | - | 보드 좌표. 기준 해상도는 클라 정의를 따른다 |
| posY | Y | `number` | - | - |
| scale | Y | `number` | - | 1 |
| rotation | Y | `number` | - | degree |
| zIndex | Y | `integer` | - | - |
| badgeOffsetX | Y | `number` | - | 스티커 기준 상대 좌표. 일정 범위 내로 제한 |
| badgeOffsetY | Y | `number` | - | - |
| badgeRotation | Y | `number` | - | - |

### StickerLayout
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| title | N | `string` | - | 텍스트 모드에서 제목을 바꿨을 때만 보낸다 |
| posX | Y | `number` | - | - |
| posY | Y | `number` | - | - |
| scale | Y | `number` | - | - |
| rotation | Y | `number` | - | - |
| zIndex | Y | `integer` | - | - |
| badgeOffsetX | Y | `number` | - | - |
| badgeOffsetY | Y | `number` | - | - |
| badgeRotation | Y | `number` | - | - |

### Drawing
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| scope | Y | `string` | STICKER, BOARD | - |
| stickerId | N | `string \| null` | - | scope=STICKER 일 때만 |
| stroke | Y | `object` | - | 점 배열 등 선 데이터. 포맷은 클라 정의를 그대로 저장 |
| color | Y | `string` | - | #FFD400 |
| strokeWidth | Y | `number` | - | 4 |

### DrawingCreate
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | 클라이언트가 생성한 uuidv7. 서버가 이 id로 upsert합니다. |
| scope | Y | `string` | STICKER, BOARD | - |
| stickerId | N | `string` | - | scope=STICKER 필수 |
| stroke | Y | `object` | - | - |
| color | Y | `string` | - | - |
| strokeWidth | Y | `number` | - | - |

### RecapDetail
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| sticker | Y | `object` | - | - |
| summary | Y | `string` | - | `한 줄 요약` 강조 문장. 스티커당 1개, 최대 100자. `sticker.title`(제목 뱃지)과 다른 값 |
| comments | Y | `object`[] | - | id(uuidv7) 오름차순. 말풍선과 키워드 칩이 섞여 있음 |
| photos | Y | `object`[] | - | takenAt, id 오름차순. 열람용 사진 포함. 개수는 배열 길이로 계산 (페이지네이션 없음) |

### RecapComment
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| content | Y | `string` | - | - |
| posX | N | `number` | - | 스티커 기준 상대 좌표. **있으면 말풍선, 없으면 하단 `테마 분석` 키워드 칩** |
| posY | N | `number` | - | posX 와 항상 함께 있거나 함께 없음 |

### RecapPhoto
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| imageUrl | Y | `string` | - | 읽기용 signed URL (만료 1시간) |
| takenAt | Y | `string` | - | - |

### Analysis
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| id | Y | `string` | - | - |
| boardId | Y | `string` | - | 결과 스티커가 붙을 보드 |
| status | Y | `string` | UPLOADING, ANALYZING, COMPLETED, FAILED | UPLOADING 업로드 중(생성 직후부터) / ANALYZING 분석·생성·배치 중 / COMPLETED 완료 / FAILED 실패·취소·만료 |
| progress | Y | `integer` | - | 0~100 |
| failedReason | N | `string \| null` | - | - |
| startedAt | N | `string \| null` | - | start 호출로 분석이 시작된 시각 |
| completedAt | N | `string \| null` | - | - |

### AnalysisCreateResult
| Field | Required | Type | Enum | Description |
| --- | --- | --- | --- | --- |
| analysisId | Y | `string` | - | - |
| uploads | Y | `object`[] | - | 요청 photos 와 같은 순서 |
