# 4주차 설계 문서: 수강 신청 중복 취소 제어 및 출석·과제 일괄 수정 정합성 보장

## 1. 어떤 문제가 있었는가?

### ① 수강 신청 중복 취소 요청 (`DELETE /seminars/{seminarId}/enrollments/me`)
* **문제가 발생하는 실행 순서:**
    1. 신청 기간 내에 동일한 루키가 취소 버튼을 빠르게 두 번 클릭하거나 네트워크 재시도로 같은 취소 요청(예: 10회)이 동시에 유입됩니다.
    2. 스레드 A와 스레드 B가 동시에 `findBySeminarIdAndRookieId`를 실행해 동일한 `Enrollment` 레코드를 조회합니다.
    3. 스레드 A가 먼저 삭제를 완료하고 커밋합니다.
    4. 스레드 B도 이미 조회해 둔 엔티티로 삭제를 시도하면서 `404 Not Found` 대신 낙관적 락/삭제 예외(`500 Internal Server Error`)가 발생하거나, 연관된 `participations` 기록 삭제 및 인원 반영 정합성이 어긋날 수 있습니다.

### ② 출석·과제 일괄 수정 (`PATCH /sessions/{sessionId}/records`)
* **문제가 발생하는 실행 순서:**
    1. 운영진이 한 회차에서 여러 수강생의 출석·과제 기록을 일괄 수정할 때, 첫 번째 수강생의 기록이 DB에 먼저 저장된 뒤 두 번째 수강생 처리 중 예외(존재하지 않는 `enrollmentId`, 다른 세미나 소속 `enrollmentId`, DB 쓰기 오류 등)가 발생하면 **앞선 변경만 DB에 남아 부분 반영(Partial Update)**이 발생할 수 있습니다.
    2. 동일한 `(enrollmentId, sessionId)` 조합에 대해 동시에 요청이 유입될 경우 중복 출석·과제 행이 생성될 수 있습니다.
    3. JSON 요청에서 특정 필드를 **생략(기존 값 유지)**한 경우와 **`null`로 전달(기록 초기화/삭제)**한 경우를 구분하지 못하면 기존 출석·과제 기록이 의도치 않게 삭제되거나 유지될 수 있습니다.

---

## 2. 왜 발생했는가?

* **조회와 변경 사이의 경쟁 상태 (Check-Then-Act Race Condition):**
  애플리케이션 코드에서 먼저 `SELECT`로 대상 레코드의 존재 여부를 확인한 뒤 `DELETE` 또는 `INSERT`/`UPDATE`를 수행하면, 동시에 유입된 여러 스레드가 모두 `SELECT` 시점의 동일한 스냅샷을 읽고 통과하여 중복 변경을 시도하게 됩니다.
* **단순 `@Transactional`만으로 해결되지 않는 이유:**
  MySQL의 기본 트랜잭션 격리 수준(`REPEATABLE READ`)에서 일반 `SELECT`는 잠금 없는 일관된 읽기(MVCC)로 동작하므로 행에 배타적 락(Exclusive Lock)을 걸지 않습니다. 따라서 각 요청을 `@Transactional`로 감싸더라도 조회 시점에는 여러 트랜잭션이 동시에 같은 데이터를 읽을 수 있어 경쟁 상태가 그대로 발생합니다.

---

## 3. 어떤 방법으로 해결했는가?

| 기능 | 해결 방법 | 적용 범위 |
| :--- | :--- | :--- |
| **수강 신청 중복 취소 제어** | **조건부 원자적 삭제 (Conditional Atomic Delete)** | `EnrollmentRepository.deleteByIdReturningCount`<br>`EnrollmentService.cancelMyEnrollment` |
| **출석·과제 중복 행 생성 방지** | **DB 복합 유니크 제약조건 (Unique Index)** | `V4__add_unique_index_to_participations.sql`<br>`participations (enrollmentId, sessionId)` |
| **출석·과제 일괄 수정 원자성 및 부분 수정** | **트라이-스테이트 파싱(`FieldPatch`) + 단일 `@Transactional` 롤백** | `BatchUpdateRecordsParser`<br>`ParticipationService.batchUpdateRecords` |

1. **조건부 원자적 삭제 (`DELETE ... WHERE id = :id`):**
   엔티티 객체 기반 `delete(entity)` 대신 `DELETE FROM enrollments WHERE id = :id` 쿼리를 직접 실행하고 실제 삭제된 행 수(`Int`)를 반환받도록 구현했습니다. 반환값이 `0`이면 이미 다른 요청이 먼저 삭제한 것으로 판단하여 `MyEnrollmentNotFoundException`(`404 ENROLLMENT_NOT_FOUND`)을 발생시킵니다.
2. **DB 복합 유니크 인덱스 적용:**
   `participations` 테이블의 `(enrollmentId, sessionId)` 컬럼에 복합 유니크 인덱스(`UQ_participations_enrollmentId_sessionId`)를 추가하여 동시 요청 시에도 동일 회차·수강생의 중복 기록이 생성되지 않도록 DB 레벨에서 차단했습니다.
3. **필드 생략·`null` 구분 및 전체 트랜잭션 롤백:**
   `JsonNode` 기반 파서(`BatchUpdateRecordsParser`)에서 필드를 `Omitted`(생략) / `Clear`(`null`) / `Set`(값 지정)의 3가지 상태로 파싱해 기존 값 유지와 초기화를 명확히 구분하고, `ParticipationService.batchUpdateRecords` 전체를 단일 `@Transactional`로 묶어 처리 도중 예외 발생 시 앞선 DB 쓰기까지 모두 롤백되도록 구현했습니다.

---

## 4. 왜 그 방법을 선택했는가?

* **비관적 락(`SELECT ... FOR UPDATE`)과 비교:**
  수강 신청 취소는 후발 요청이 선행 요청의 종료를 대기한 뒤 후속 작업을 이어갈 필요가 없으며, 가장 먼저 도달한 1건만 성공하고 나머지는 즉시 `404`로 실패해야 합니다. `DELETE WHERE id = :id`는 DB 엔진이 대상 행에 배타적 락을 획득해 원자적으로 삭제 건수를 반환하므로, 불필요한 락 대기(Lock Wait)나 데드락 위험 없이 단일 쿼리로 경합을 제어할 수 있습니다.
* **재시도(Retry) 전략과 비교:**
  중복 취소는 충돌 시 재시도하여 성공시켜야 하는 작업이 아니므로, 재시도 비용 없이 반환값 `0`을 즉시 `404` 실패 응답으로 변환하는 것이 도메인 규칙에 부합합니다.
* **낙관적 락(`@Version`)과 비교:**
  `version` 컬럼 추가를 위한 스키마 변경이나 버전 불일치 예외 변환 로직 없이도, 기존 테이블 구조와 `DELETE` 영향 행 수(`Int`)만으로 정확히 1건의 삭제만 허용할 수 있어 구현 복잡도와 유지보수 비용이 낮습니다.

---

## 5. 트랜잭션 경계는 어디인가?

### ① 수강 신청 취소 (`EnrollmentService.cancelMyEnrollment` — `@Transactional`)
하나의 트랜잭션 경계 안에서 아래 DB 변경이 모두 성공하거나 모두 롤백됩니다.
1. `participations` 테이블에서 해당 `enrollmentId`에 연결된 출석·과제 기록 일괄 삭제 (`deleteAllByEnrollmentId`)
2. `enrollments` 테이블에서 해당 `enrollmentId` 레코드 원자적 삭제 (`deleteByIdReturningCount`) 및 삭제 건수 `1` 검증
   *(2번 단계에서 삭제 건수가 `0`이어서 `MyEnrollmentNotFoundException`이 발생하거나 예기치 못한 오류가 발생하면, 1번 단계에서 수행한 `participations` 삭제도 함께 롤백됩니다.)*

### ② 출석·과제 일괄 수정 (`ParticipationService.batchUpdateRecords` — `@Transactional`)
하나의 트랜잭션 경계 안에서 아래 검증 및 DB 변경이 모두 성공하거나 모두 롤백됩니다.
1. 요청 목록의 모든 `enrollmentId` 존재 여부 및 해당 세미나 소속 여부 검증
2. 각 수강생별 기존 `Participation` 조회 후 `Omitted` / `Clear` / `Set` 규칙에 따라 상태 병합
3. 출석과 과제 상태가 모두 `null`이 된 레코드는 `participations`에서 `DELETE`, 하나라도 값이 존재하면 `INSERT` 또는 `UPDATE`
   *(일괄 수정 도중 예외가 발생하면 앞서 DB에 반영된 모든 `participations` 생성·수정·삭제 작업이 전부 롤백됩니다.)*

---

## 6. 어떻게 검증했는가?

* **자체 테스트 위치 및 실행 방법:**
    * **테스트 위치:** `src/test/kotlin/com/wafflestudio/spring2026/waggle/EnrollmentCancelAndRecordRollbackTest.kt`
    * **실행 명령:** `./gradlew test` (Windows PowerShell: `./gradlew.bat test`)
* **동시성 검증 결과 (중복 취소):**
    * `ExecutorService`와 `CountDownLatch`를 사용해 동일 루키의 수강 신청에 대해 동시에 여러 건의 `cancelMyEnrollment` 호출이 동시에 실행되도록 구성했습니다.
    * 실행 결과 **정확히 1건만 성공**하고 **나머지는 모두 `MyEnrollmentNotFoundException`(`404`)으로 실패**했으며, 작업 종료 후 별도 조회에서 `enrollments` 레코드와 연결된 `participations` 출석·과제 기록이 모두 제거되고 다른 수강생의 기록은 그대로 유지됨을 확인했습니다.
* **첫 DB 쓰기 이후 실패 주입 및 롤백 검증 결과:**
    * 변경 전 기록을 실제 MySQL에 커밋한 상태에서 `ParticipationService.batchUpdateRecords`를 호출하고, 첫 번째 수강생의 기록 저장(`participationRepository.save`)이 **실제 DB에 쓰기 실행된 직후** 두 번째 항목 처리 단계에서 예외가 발생하도록 실패를 주입했습니다.
    * 호출자에게 예외가 전파된 뒤 별도 트랜잭션에서 DB를 재조회한 결과, 첫 번째 수강생의 수정 내역까지 반영되지 않고 **모든 대상의 기록이 작업 전 초기 상태로 완전히 롤백**됨을 확인했습니다.

---

## 7. DB Migration 및 기존 데이터 유지 방법

* **추가된 Migration 파일:** `src/main/resources/db/migration/V4__add_unique_index_to_participations.sql`
* **변경된 스키마:**
  ```sql
  CREATE UNIQUE INDEX UQ_participations_enrollmentId_sessionId
    ON participations (enrollmentId ASC, sessionId ASC);