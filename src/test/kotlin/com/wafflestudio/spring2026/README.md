# 팀 자체 테스트와 동시성·트랜잭션 설계

## 1. upstream 대비 추가한 테스트

이 문서는 운영진 테스트 목록 전체가 아니라, 로컬 `upstream/main`(`5a061d0`)과 비교해 팀이 추가한 테스트를 설명한다. 아래 목록은 `git diff --name-status upstream/main -- src/test`로 확인했다. 이번 수정에서는 기존 Kotlin 테스트 파일과 기대값을 변경하지 않고 운영 코드를 수정했다.

| 추가한 테스트 파일 | 테스트 수 | 확인하는 동작 |
| --- | ---: | --- |
| [EnrollmentCapacityConcurrencyIntegrationTest](enrollment/EnrollmentCapacityConcurrencyIntegrationTest.kt) | 2 | 정원 30명/100명 동시 신청, 정원 5명에서 신청·취소 혼합과 재충원 |
| [DuplicateEnrollmentConcurrencyTest](team/DuplicateEnrollmentConcurrencyTest.kt) | 1 | 한 사용자의 20회 동시 신청 중 한 건만 성공하고 실패한 요청은 정원을 소비하지 않음 |
| [EnrollmentRollbackIntegrationTest](enrollment/EnrollmentRollbackIntegrationTest.kt) | 1 | 실제 신청 INSERT가 끝난 뒤 예외를 주입하여 신청 롤백·기존 데이터 보존·재신청 확인 |
| [ParticipationRollbackIntegrationTest](../team/ParticipationRollbackIntegrationTest.kt) | 1 | 첫 기록을 실제 수정한 뒤 두 번째 저장을 실패시켜 일괄 수정 전체 롤백 확인 |
| [TeamEnrollmentUpdateApiTest](../team/TeamEnrollmentUpdateApiTest.kt) | 4 | 수강 정보 부분 수정, Grace Day 경계값, 잘못된 입력의 무변경, 권한과 세미나 소속 검증 |
| [EnrollmentCancelAndRecordRollbackTest](waggle/EnrollmentCancelAndRecordRollbackTest.kt) | 4 | 중복 취소, 일괄 수정 실패, 생략·null 구분. 기존 로컬 DB 데이터가 필요한 테스트이며 아래에 한계를 명시함 |

추가된 Kotlin 테스트는 총 6개 파일, 13개 메서드다. 앞의 5개 파일은 기존 `ApiIntegrationTest`를 상속해 Testcontainers `mysql:8.4`에서 필요한 사용자·세미나·신청을 직접 만든다. 마지막 파일은 별도 `@SpringBootTest`를 사용한다.

현재 체크아웃과 위 upstream 참조 사이에는 공통 Kotlin 테스트의 수정은 없지만, 5주차 upstream에 있는 `SessionDeletionApiTest`, `UserProfileApiTest`, `UserWithdrawalApiTest` 세 파일이 현재 체크아웃에는 없다. 이는 비교 대상 브랜치의 주차 차이이며, 이번 작업에서 이 파일들을 삭제하거나 복구하지 않았다. 따라서 현재 로컬 전체 테스트의 통과와 최신 upstream의 모든 테스트 통과는 구분해야 한다.

운영진이 제공한 [EnrollmentConcurrencyApiTest](enrollment/EnrollmentConcurrencyApiTest.kt)에도 정원 경쟁·중복 신청·중복 취소·신청/취소 혼합 시나리오가 있다. 팀의 정원·중복 신청 테스트는 HTTP 응답뿐 아니라 실제 DB 행 수와 신청 ID를 직접 확인하는 보충 검증이다. 기존 운영진 테스트를 대체하거나 검증을 완화한 것이 아니다.

## 2. 문제와 발생 순서

### 정원 경쟁과 중복 신청

정원 30명에 현재 신청자가 29명이라고 가정하면 다음 순서로 초과 신청이 발생할 수 있다.

1. 요청 A가 신청 인원 29명을 읽고 빈자리가 있다고 판단한다.
2. A가 저장하기 전에 요청 B도 29명을 읽고 같은 판단을 한다.
3. A와 B가 각각 신청을 저장하면 최종 신청자는 31명이 된다.

같은 사용자의 동시 요청도 두 요청 모두 중복 여부 조회에서 `false`를 읽을 수 있다. `(rookieId, seminarId)` 유니크 인덱스는 같은 사용자의 중복 행은 막지만, 서로 다른 사용자의 전체 정원 초과를 막지는 못한다.

### 중복 취소와 신청·취소 혼합

취소 A와 B가 같은 신청을 조회한 뒤 A가 먼저 삭제하면 B가 보유한 신청 객체는 이미 지워진 상태가 된다. 실제 삭제 여부를 확인하지 않으면 두 번 성공한 것처럼 응답하거나 삭제 관련 예외가 `500`으로 노출될 수 있다. 연결된 출석·과제 기록 삭제와 신청 삭제가 별도 트랜잭션이면 일부만 지워질 수도 있다.

중복 취소 문제는 2026-11-24의 `6984f51` 검증에서 실제로 재현했다. 원본 `EnrollmentConcurrencyApiTest`의 동일 신청 10회 취소에서 기대값은 `204` 1건·`404` 9건이었으나 실제로는 `204` 9건·`404` 1건이었다. 당시 선택 실행한 14개 중 이 한 건이 실패했다. 반면 정원 초과는 락 적용 전 버전을 직접 실행한 재현 결과를 확보하지 않았으므로, 앞의 29명에서 31명이 되는 예시는 가능한 실행 순서에 대한 설명이다.

신규 신청과 취소가 섞이면 정원을 읽는 시점과 취소 커밋 순서에 따라 신청 성공 수가 달라진다. 성공 수 하나를 고정하기보다 `최종 인원 = 초기 인원 + 신청 성공 수 - 취소 성공 수`와 정원 상한, 이후 빈자리 재충원을 확인해야 한다.

### 출석·과제 일괄 수정의 부분 반영

첫 수강생의 기록을 저장한 뒤 두 번째 저장에서 예외가 발생할 수 있다. 각 repository 호출만 독립적으로 커밋하면 첫 변경은 남고 요청 전체는 실패한 상태가 된다. 같은 수강생·회차의 기록이 없다고 두 요청이 동시에 판단하면 중복 행도 생성될 수 있다. 필드 생략과 명시적 `null`을 같은 값으로 처리하면 기존 기록 유지와 초기화도 구분할 수 없다.

## 3. 트랜잭션만으로 충분하지 않은 이유

`@Transactional`은 한 요청의 여러 변경을 함께 커밋하거나 롤백하도록 묶는다. 일반 조회로 같은 인원이나 동일 신청을 읽는 서로 다른 트랜잭션까지 자동으로 직렬화하지는 않는다. `READ_COMMITTED`에서도 A와 B가 저장 전에 동일한 인원을 읽을 수 있으므로 격리 수준 설정만으로 정원이 보장되지는 않는다.

따라서 서비스 트랜잭션 안에서 공통 세미나 행을 먼저 잠그고, 락 획득 후 존재 여부·중복 여부·현재 인원을 읽는다. 현재 변경 메서드는 `@Transactional(isolation = Isolation.READ_COMMITTED)`을 사용한다. 락은 커밋 또는 롤백까지 유지되며, 같은 세미나를 변경하는 다음 요청은 앞 요청이 끝난 뒤 검증한다.

## 4. 해결 방법과 선택 이유

| 방법 | 적용 범위와 역할 |
| --- | --- |
| 세미나 행의 비관적 락 | `SeminarRepository.findByIdForUpdate`의 `SELECT * FROM seminars WHERE id = :id FOR UPDATE`. 수강 신청·취소·수강 정보 수정·출석/과제 일괄 수정이 같은 세미나 행을 잠가 서로의 변경과 충돌하지 않게 함 |
| 서비스 단위 트랜잭션 | `createEnrollment`, `cancelMyEnrollment`, `updateEnrollment`, `batchUpdateRecords`의 검증과 변경을 한 경계로 묶음 |
| 신청 복합 UNIQUE | V1의 `(rookieId, seminarId)`. 중복 신청에 대한 DB 최종 방어선이며 해당 제약 위반을 이미 신청한 상태로 처리 |
| 참여 기록 복합 UNIQUE | V4의 `(enrollmentId, sessionId)`. 같은 수강생·회차의 기록은 최대 한 행으로 제한 |
| 삭제 영향 행 수 검사 | `deleteByIdReturningCount`의 `DELETE FROM enrollments WHERE id = :id` 결과가 정확히 1인지 확인. 0이면 신청 없음으로 처리 |
| 필드별 부분 수정 상태 | `FieldPatch.Omitted`, `Clear`, `Set`으로 생략은 유지, null은 초기화, 값은 갱신하도록 구분 |

신청자가 0명이어도 세미나 행은 존재하므로 빈 세미나의 경쟁을 잠글 수 있다. 잠금 획득 후 다시 읽은 세미나의 정원·신청 기간·Grace Day를 사용한다. 취소도 같은 세미나 락을 획득하므로 삭제 영향 행 수 검사는 별도의 최종 확인이며, 락을 대신하는 유일한 수단이 아니다. `DELETE` 역시 DB 락을 사용하므로 락 대기가 전혀 없다고 볼 수 없다.

선택한 방법과 대안의 차이는 다음과 같다.

| 대안 | 선택 시 고려한 점 |
| --- | --- |
| JVM `synchronized` | 한 프로세스만 보호하므로 여러 애플리케이션 인스턴스가 같은 DB를 쓰는 경우 충분하지 않음 |
| 낙관적 락과 재시도 | 버전 컬럼·충돌 변환·재시도 정책이 필요함. 마지막 자리처럼 경합이 큰 경우 반복 실패가 늘어날 수 있음 |
| 조건부 인원 카운터 갱신 | 가능한 대안이지만 인원 컬럼과 신청·취소의 카운터 일관성 관리가 추가로 필요함 |
| UNIQUE만 적용 | 중복 행은 막지만 서로 다른 사용자의 정원 상한이나 여러 행의 일괄 변경 원자성은 보장하지 않음 |
| 비관적 락과 트랜잭션 | 기존 세미나 행을 공통 잠금 대상으로 사용하여 검증 순서를 명확히 하고 여러 DB 변경을 함께 되돌릴 수 있어 선택 |

같은 세미나의 쓰기 요청이 순서대로 처리되므로 처리량과 락 대기 시간이 비용이다. 트랜잭션에 네트워크 호출 등 불필요한 작업을 포함하지 않아 락 보유 시간을 줄인다. DB 제약조건은 다른 쓰기 경로에 대한 추가 방어선으로 유지한다.

## 5. 트랜잭션 경계와 데이터 보존

### 수강 신청

`createEnrollment`는 세미나 잠금, 중복 신청 검사, 현재 인원·기간 검사, 신청 INSERT를 하나의 트랜잭션으로 실행한다. 런타임 예외가 서비스 경계 밖으로 전파되면 저장한 신청도 롤백한다.

### 수강 취소

`cancelMyEnrollment`는 다음 작업을 하나의 트랜잭션에서 실행한다.

1. 세미나 행을 잠그고 취소 가능 기간을 검사한다.
2. 해당 세미나와 사용자에 대한 신청을 다시 조회한다.
3. 신청에 연결된 `participations`를 삭제한다.
4. 신청을 삭제하고 영향 행 수가 1인지 확인한다.
5. 모두 성공하면 커밋한다. 뒤 단계에서 실패하면 앞서 삭제한 기록도 롤백한다.

같은 신청의 후속 취소는 잠금 대기 후 신청이 없음을 확인하고 `404`가 된다. 첫 성공 요청만 `204`이며 다른 수강생의 신청·기록은 삭제 대상에 포함하지 않는다.

### 출석·과제 일괄 수정

`batchUpdateRecords`는 세미나 잠금, 전체 신청 ID의 존재·소속 검증, 기존 기록 조회, 필드 병합, 모든 INSERT·UPDATE·DELETE를 한 트랜잭션에 포함한다. 사전 검증에서 오류를 발견하면 쓰기를 시작하지 않는다. 사전 검증 이후 실제 DB 쓰기 중 오류가 나도 이미 수행한 첫 번째 변경까지 롤백한다. 출석·과제 값이 모두 null이면 기록 행을 삭제하고, 하나라도 있으면 저장한다.

### V4 migration

추가 스키마 변경은 `src/main/resources/db/migration/V4__add_unique_index_to_participations.sql`의 다음 인덱스다.

```sql
CREATE UNIQUE INDEX UQ_participations_enrollmentId_sessionId
    ON participations (enrollmentId ASC, sessionId ASC);
```

V4에는 기존 데이터를 삭제하거나 덮어쓰는 SQL이 없다. 중복이 없는 기존 기록은 유지한 채 인덱스를 추가한다. 이미 동일 `(enrollmentId, sessionId)`의 중복 데이터가 있으면 인덱스 생성과 migration이 실패하며, 임의로 한 행을 선택하거나 조용히 삭제하지 않는다. 이 경우 중복의 원인과 보존할 출석·과제 값을 확인한 뒤 별도의 명시적인 데이터 정리가 필요하다. 현재 자체 테스트에는 기존 중복 데이터가 있는 DB의 migration 실패를 직접 실행하는 별도 테스트는 없다.

## 6. 자체 테스트의 검증 방식

### 동시성 및 수강 정보 수정

| 테스트 | 실행과 검증 |
| --- | --- |
| 정원 30명/100명 경쟁 | 서로 다른 승인 루키 100명을 준비하고 `parallelism = 100`으로 시작 래치를 해제. `201` 30건, `409 / SEMINAR_IS_NOT_OPEN` 70건, 실제 DB 30행과 성공한 사용자·신청 ID 매핑을 검사 |
| 정원 5명 신청·취소 혼합 | 기존 5명 취소와 신규 20명 신청을 함께 실행. 취소 5건 `204`, 신청은 `201` 또는 정원 마감 `409`, 인원 보존식과 `0..5` 범위를 검사. 취소한 사용자로 빈자리를 모두 채운 뒤 DB 5행과 추가 신청 `409` 확인 |
| 동일 사용자 20회 신청 | `201` 1건, `409` 19건과 해당 사용자 DB 1행을 검사. 다른 사용자 두 명이 남은 정원을 채울 수 있고 이후 신청은 거절되는지 확인 |
| 수강 정보 부분 수정 4개 | 요청한 값만 변경되는지, 기존 신청 시각·출석 기록이 유지되는지, Grace Day 범위 및 잘못된 입력·권한·소속 오류에서 데이터가 바뀌지 않는지 확인 |

공통 `runConcurrently`는 준비 래치로 작업을 모은 뒤 시작 래치를 한 번에 해제한다. 실제 DB 실행 병렬도는 스레드 스케줄러와 커넥션 풀의 영향을 받는다. HTTP 응답 건수만으로 끝내지 않고 `JdbcTemplate` 또는 repository로 실제 DB 상태도 검사한다.

### 실제 신청 INSERT 후 실패 주입

`EnrollmentRollbackIntegrationTest`는 정원 2명 중 기존 신청 1개를 먼저 만든다. 실제 `EnrollmentRepository`의 Spring `Advised` 프록시에 테스트용 `MethodInterceptor`를 가장 바깥쪽인 `index 0`에 추가하고 다음 순서로 검증한다.

1. `invocation.proceed()`로 실제 repository `save`를 실행한다.
2. 반환된 신청 ID가 DB에 있고 현재 신청 행이 2개임을 같은 트랜잭션에서 확인한다.
3. 지정한 런타임 예외를 던진다.
4. 서비스 호출 종료 후 새 신청이 사라지고 기존 신청의 모든 필드와 인원이 그대로인지 확인한다.
5. advice를 `finally`에서 제거한 뒤 별도 작업 스레드에서 같은 사용자가 10초 안에 재신청하여 정원을 채우는지 확인한다.

테스트 메서드에는 `@Transactional`이 없다. 서비스 트랜잭션을 제거하면 repository의 저장이 먼저 커밋된 뒤 예외가 발생하여 실패한 신청이 남으므로 DB 검증이 실패한다. 저장 전 검증 오류나 repository 내부 콜백의 롤백만으로 통과하는 테스트와 구분된다. 자동 증가 ID가 연속인지 여부는 검사하지 않는다.

### 실제 첫 기록 수정 후 두 번째 저장 실패 주입

`ParticipationRollbackIntegrationTest`는 두 수강생의 `ABSENT / NOT_SUBMITTED` 기록을 먼저 저장하고 별도 `TransactionTemplate` 조회로 변경 전 목록을 확보한다. 테스트 설정의 `@Primary` repository 장식자는 실제 repository에 위임하되 실패 주입이 켜진 동안 두 번째 `save`에서 `ForcedSaveFailure`를 던진다.

첫 `save`는 실제 DB에 실행하고 즉시 다시 조회하여 `PRESENT / PASSED`로 바뀐 기록을 보관한다. 서비스 실패 후 `successfulSaves == 1`과 첫 변경 값이 실제로 관찰되었음을 확인한다. 마지막으로 별도 트랜잭션에서 전체 목록을 다시 조회하여 변경 전 목록과 동일한지 비교한다. 실패 주입은 `finally`에서 해제한다. 따라서 존재하지 않는 ID를 사전 검사로 거절하는 경우와 달리, 첫 DB 쓰기 이후 발생한 실패의 롤백을 검증한다.

### 기존 로컬 DB 기반 테스트의 범위와 한계

`EnrollmentCancelAndRecordRollbackTest`도 upstream 대비 추가된 팀 테스트이므로 목록에 포함한다. 다만 이 클래스는 `ApiIntegrationTest`를 상속하지 않아 기본 실행 시 `application.yaml`의 로컬 DB(`localhost:3306/waggle`)를 사용한다. 환경 변수 등으로 datasource를 별도로 지정할 수 있다.

첫 세 메서드는 DB에서 세미나와 해당 회차를 찾아 사용하며, 없으면 `return`으로 끝난다. 이 경우 JUnit은 테스트를 성공으로 표시하지만 동시 취소·일괄 수정·부분 초기화 검증은 실행되지 않는다. 데이터가 있으면 외래 키에 필요한 사용자 ID `999901`, `999902`, `999903`도 준비되어 있어야 한다. 이 클래스는 자체적으로 해당 사용자들을 만들지 않는다.

이 클래스의 이름에 롤백이 포함된 메서드는 두 번째 명령에 존재하지 않는 신청 ID를 전달한다. 현재 서비스는 전체 ID를 먼저 검증하므로 실제 첫 기록을 쓰기 전에 예외가 발생한다. 따라서 이 메서드를 첫 DB 쓰기 이후 롤백의 근거로 사용하지 않는다. 그 근거는 위 두 실제 쓰기 실패 주입 테스트다. 네 번째 파서 테스트는 생략·null 구분을 직접 검사하지만 Spring 컨텍스트 시작에는 DB가 필요하다.

## 실행 명령과 검증 결과

JDK 21과 Docker가 필요하다. 아래 자체 테스트 5개 클래스는 Testcontainers가 별도의 MySQL을 시작하므로 로컬 개발 DB를 준비하지 않아도 된다. 최초 실행에는 Gradle 의존성과 컨테이너 이미지 다운로드가 필요할 수 있다.

프로젝트 루트의 PowerShell에서 fixture를 직접 준비하는 자체 테스트 9개를 실행한다.

```powershell
.\gradlew.bat test --tests '*EnrollmentCapacityConcurrencyIntegrationTest' --tests '*DuplicateEnrollmentConcurrencyTest' --tests '*EnrollmentRollbackIntegrationTest' --tests '*ParticipationRollbackIntegrationTest' --tests '*TeamEnrollmentUpdateApiTest'
```

기존 운영진 동시성 테스트를 별도로 확인한다.

```powershell
.\gradlew.bat test --tests '*EnrollmentConcurrencyApiTest'
```

로컬 DB 기반 팀 테스트는 앞서 설명한 DB와 fixture 조건을 준비한 뒤 실행한다.

```powershell
.\gradlew.bat test --tests '*EnrollmentCancelAndRecordRollbackTest'
```

현재 체크아웃의 전체 테스트는 다음 명령으로 실행한다. Testcontainers 테스트와 로컬 DB 기반 테스트가 모두 포함되므로 두 환경 조건이 모두 적용된다.

```powershell
.\gradlew.bat test
```

2026-11-24 검증 결과: 수정 전 전체 실행에서는 89개 중 71개가 통과하고 18개가 실패했다. 실패 원인은 수강 정보 PATCH의 `404` 응답 12개와 기록 수정 API가 기대한 `204` 대신 `200`을 반환한 6개였으며, 이 결과를 race condition 재현 결과로 해석하지 않는다. 누락된 수강 정보 PATCH 연결과 응답 DTO, 기록 수정 API의 `204` 응답과 입력 처리를 운영 코드에서 수정했다. 기존 Kotlin 테스트 파일과 기대값에는 변경이 없다.

수정 후 실제 실행한 명령은 `.\gradlew.bat test --offline --no-daemon`이며, 2026-11-24 22:35 KST에 `BUILD SUCCESSFUL`로 종료했다. XML 보고서 20개를 합산한 결과 테스트 89개, 실패 0개, 오류 0개, skipped 0개다. `--offline`은 이미 내려받은 의존성을 사용하기 위한 이번 실행 옵션이다.

| 검증 대상 | 메서드 수 | 최종 결과 |
| --- | ---: | --- |
| fixture를 직접 준비하는 팀 자체 테스트 5개 클래스 | 9 | 모두 통과. 정원 경쟁·신청/취소 혼합·중복 신청·실제 첫 DB 쓰기 이후 롤백·수강 정보 수정 검증 포함 |
| 로컬 DB 기반 `EnrollmentCancelAndRecordRollbackTest` | 4 | JUnit 결과 모두 성공. 첫 세 메서드는 fixture 미존재 시 조기 반환할 수 있으므로 이 결과만으로 해당 검증 실행을 보장하지 않음 |
| 현재 체크아웃의 전체 테스트 | 89 | 모두 통과. 실패 0, 오류 0, skipped 0 |

HTML 보고서는 프로젝트 루트의 `build/reports/tests/test/index.html`, XML 결과는 `build/test-results/test`에 생성된다. 전체 테스트 수는 현재 체크아웃 기준이며 upstream에만 있는 세 테스트 파일은 포함하지 않는다.
