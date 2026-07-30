# ERD

`schema.dbml`은 ppotto 서버 데이터베이스 설계의 기준 ERD 문서다.

## 사용 방법

- DBML을 지원하는 도구에서 `schema.dbml`을 열어 테이블 관계를 확인한다.
- Flyway 마이그레이션을 작성하기 전에 `Project Note`의 수동 반영 항목을 확인한다.
- Flyway 마이그레이션, jOOQ 생성 스키마, Repository, 도메인 영속화 동작이 바뀌면 `schema.dbml`도 같은 변경 단위에서 검토한다.
- ERD 변경이 API나 도메인 동작에 영향을 주면 `docs/api-spec/api-spec.md`도 함께 갱신한다.

## 수동 반영 항목

DBML만으로 표현하기 어려운 PostgreSQL 세부사항은 마이그레이션에서 직접 작성한다.

- `citext` 확장
- partial unique index
- CHECK 제약
- `uuidv7()` 기본값

## 관리 규칙

- 테이블, 컬럼, enum, 인덱스, 제약, 관계, 삭제 정책, 상태 전이 변경은 `schema.dbml`에 반영한다.
- 실제 DB 변경은 별도 Flyway 마이그레이션으로 반영한다.
- `schema.dbml`과 마이그레이션이 충돌하지 않도록 같은 변경 단위에서 검토한다.
- 구현 변경 후 ERD가 오래된 상태라면 해당 변경은 완료된 것으로 보지 않는다.
