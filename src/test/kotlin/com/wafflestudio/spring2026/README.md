# 수강신청 동시성·롤백 테스트

## 기존 테스트 확인과 보완

요청한 두 동시성 시나리오는 [EnrollmentConcurrencyApiTest](enrollment/EnrollmentConcurrencyApiTest.kt)에 이미 있다. 기존 테스트는 변경하지 않았다. 다만 이 테스트는 미구현 대시보드 API의 응답까지 함께 확인하므로 현재 상태에서는 요청한 두 조건의 검증을 끝까지 완료하지 못한다. 이를 독립적으로 확인할 수 있도록 [EnrollmentCapacityConcurrencyIntegrationTest](enrollment/EnrollmentCapacityConcurrencyIntegrationTest.kt)에 두 시나리오의 보충 테스트를 추가했다. 수강신청 저장 후 예외에 대한 롤백 테스트는 없어 [EnrollmentRollbackIntegrationTest](enrollment/EnrollmentRollbackIntegrationTest.kt)를 추가했다.

| 시나리오 | 준비와 실행 | 반드시 확인하는 결과 |
| --- | --- | --- |
| 정원 경쟁 | 신청 기간 안인 정원 30명의 빈 세미나에 서로 다른 승인 루키 100명이 한 번씩 동시 신청 | `201` 30건, `409 / SEMINAR_IS_NOT_OPEN` 70건, 실제 DB 신청 30개. 성공 응답의 사용자·신청 ID와 DB 행이 일치 |
| 신청·취소 혼합 | 정원 5명이 찬 세미나에서 기존 수강생 5명의 취소와 신규 루키 20명의 신청을 동시 실행 | 취소 `204` 5건. 신청 응답은 `201` 또는 정원 마감 `409`. `최종 인원 = 최초 5명 + 신청 성공 수 - 취소 성공 수`, `0 <= 최종 인원 <= 5`. 이후 빈자리를 모두 채우면 실제 DB 신청 5개이고 추가 신청은 `409` |
| 저장 후 롤백 | 정원 2명 중 기존 신청 1개를 유지한 상태에서 다른 루키의 실제 INSERT가 끝난 직후 예외 발생 | 실패한 신청이 DB에 남지 않고 기존 신청의 모든 필드와 인원이 유지됨. 다른 작업 스레드에서 같은 사용자의 재신청이 완료되어 빈자리와 락이 회복되었음을 확인 |

기존 `EnrollmentConcurrencyApiTest`의 정원 경쟁 메서드는 `(정원, 요청 수)`가 `(1, 8)`, `(5, 20)`, `(30, 100)`, `(105, 5)`인 경우를 순서대로 확인하도록 작성되어 있다. 마지막 한 자리 경쟁과 동일 사용자의 중복 동시 신청 테스트도 기존에 있다. 새 보충 테스트는 요청한 `(30, 100)` 정원 경쟁과 정원 5명의 신청·취소 혼합에 집중한다.

기존 공통 검증은 미구현 `/seminars/{id}/dashboard` 조회에서 `404`로 중단되어 이후 정원 사례와 혼합 실행 후 재신청까지 확인하지 못했다. 이 실패와 기존 대시보드 검증은 그대로 남아 있다. 새 보충 테스트는 대시보드에 의존하지 않고 `JdbcTemplate`으로 실제 저장 상태를 읽어 응답과 비교한다. 기존 `EnrollmentConcurrencyApiTest`와 [SeminarDashboardApiTest](seminar/SeminarDashboardApiTest.kt)의 검증을 삭제하거나 완화하지 않았으며, 이번 작업에서 대시보드 기능을 구현한 것은 아니다.

## 문제와 원인

기존 `createEnrollment`는 중복 신청 여부와 신청 인원을 조회한 뒤 정원이 남으면 저장했다. 정원 확인과 저장이 여러 요청에 걸쳐 원자적으로 처리되지 않아 두 요청이 같은 빈자리를 확인할 수 있었다.

예를 들어 정원 30명에 현재 29명이 신청했다면, 요청 A와 B가 모두 `count = 29`를 읽고 각각 저장하여 최종 인원이 31명이 될 수 있다. `(rookieId, seminarId)` 유니크 인덱스는 같은 사용자의 중복 신청만 막으므로 서로 다른 사용자의 정원 경쟁은 해결하지 못한다.

서비스 전체의 트랜잭션이 없으면 repository의 `save`가 반환될 때 저장이 이미 커밋될 수 있다. 그 뒤 예외가 발생해도 호출자는 실패를 받으면서 신청 데이터는 남는 문제가 생긴다. 현재 서비스는 저장 결과를 바로 반환하지만, 후속 처리가 추가되어도 같은 트랜잭션의 변경이 함께 취소되어야 한다.

## 해결 방법과 선택 이유

`EnrollmentService.createEnrollment`에 `@Transactional`을 적용하고, `SeminarRepository.findByIdForUpdate`에서 다음 쿼리로 대상 세미나 행에 배타적 비관적 락을 건다.

```sql
SELECT * FROM seminars WHERE id = :id FOR UPDATE
```

처리 순서는 다음과 같다.

1. 신청자의 역할과 승인 상태를 확인한다.
2. 트랜잭션 안에서 대상 세미나 행의 락을 획득한다.
3. 락 획득 후 중복 신청과 현재 신청 인원을 조회한다.
4. 잠근 세미나의 정원·신청 기간을 확인하고 신청을 저장한다.
5. 정상 종료 시 커밋하고, 런타임 예외 발생 시 롤백한다. 락은 트랜잭션 종료 시 해제된다.

같은 세미나에 대한 신청은 이 구간을 순서대로 수행한다. 세미나 행은 신청자가 0명이어도 존재하므로 빈 세미나의 경쟁도 보호할 수 있다. 정원과 Grace Day 초기값은 락을 획득하며 다시 조회한 세미나에서 가져온다.

현재 API 호출 경로는 컨트롤러가 Spring 서비스 프록시를 호출하여 서비스 트랜잭션을 시작한다. MySQL 기본 `REPEATABLE READ`에서는 일관 읽기 스냅샷이 만들어지는 시점도 중요하다. 이 구현은 트랜잭션의 첫 DB 접근이 잠금 조회이며, 중복·인원 조회를 그 뒤에 수행해 앞선 신청 트랜잭션이 커밋한 결과를 반영한다. 향후 이미 조회를 수행한 외부 트랜잭션에 참여하도록 호출 구조를 바꾼다면 이 전제도 다시 확인해야 한다.

신청·취소 혼합 시 취소는 실제 신청 행을 삭제해 인원을 줄인다. 취소 반영보다 먼저 정원을 확인한 신청은 마감 응답을 받을 수 있으므로 동시 실행의 성공 수 자체를 고정하지 않는다. 대신 실제 최종 인원, 인원 보존식, 정원 상한, 실행 종료 후 남은 자리의 재신청 가능 여부를 확인한다. 이 시나리오는 같은 신청을 여러 번 동시에 취소하는 경우와는 다르다.

| 방법 | 이번 구현에서의 판단 |
| --- | --- |
| 비관적 락 + 서비스 트랜잭션 | 같은 세미나의 확인·저장을 DB에서 직렬화하고 실패 시 함께 롤백한다. 기존 스키마에서 구현할 수 있어 선택 |
| 트랜잭션만 적용 | 원자성은 제공하지만 여러 요청이 같은 신청 인원을 읽는 경쟁까지 막지는 못함 |
| JVM `synchronized` | 한 프로세스 안의 호출만 보호하므로 여러 서버 인스턴스가 같은 DB를 사용할 때 정원을 보장하기 어려움 |
| 낙관적 락 | 버전 필드, 충돌 감지, 재시도 정책이 필요함. 마지막 자리에 요청이 몰리는 상황에서는 충돌과 재시도가 늘어남 |
| 조건부 인원 카운터 갱신 | 가능한 대안이지만 인원 컬럼 추가와 신청·취소 시 카운터 일관성 관리가 필요함 |
| 중복 신청 유니크 인덱스 | 같은 사용자의 중복 저장에 대한 방어 수단이며, 전체 정원 제한을 대신하지 못함 |

같은 세미나의 신청이 직렬화되므로 처리량과 락 대기 시간이 비용이다. 네트워크 호출이나 불필요한 후속 처리를 이 트랜잭션 안에 넣어 락 보유 시간을 늘리지 않아야 한다.

## 테스트 구현 방식

### 동시성

- 기존 `ApiIntegrationTest`의 Testcontainers `mysql:8.4`와 실제 Spring 서비스·repository를 사용한다.
- `runConcurrently`는 작업 스레드를 준비 래치에서 모은 뒤 시작 래치를 한 번에 해제한다. 정원 경쟁은 작업 수만큼 병렬도를 지정하므로 100개 작업이 준비된 상태로 시작한다. DB 동시 실행 수는 커넥션 풀과 스케줄러의 영향을 받는다.
- 새 보충 테스트는 HTTP 상태뿐 아니라 정원 마감 오류 코드, DB의 정확한 행 수와 사용자·신청 ID 매핑을 검증한다. 대시보드를 포함한 기존 조회 API 검증은 원래 테스트에 유지한다.
- 혼합 시나리오는 완료 순서를 가정하지 않고 응답에서 인원 보존식을 계산한 뒤 DB와 비교한다. 취소한 사용자로 남은 자리를 다시 채워 정원 5명과 추가 신청 거절까지 확인한다.

### 롤백

단순히 저장 전 입력 검증에서 예외를 발생시키면 실제로 되돌릴 DB 변경이 없어 롤백을 검증할 수 없다. `AfterSaveCallback` 안에서만 예외를 던지는 방식도 repository 자체 트랜잭션의 롤백으로 통과할 수 있어 서비스의 트랜잭션 경계를 충분히 확인하지 못한다.

새 테스트는 실제 `EnrollmentRepository`의 Spring `Advised` 프록시에 테스트용 `MethodInterceptor`를 가장 바깥쪽(`index 0`)에 잠시 추가한다. `invocation.proceed()`로 실제 `save`와 repository의 트랜잭션 처리를 완료한 뒤, 대상 신청이 DB에 보이는지 확인하고 지정한 런타임 예외를 던진다.

서비스 트랜잭션이 있으면 repository가 그 트랜잭션에 참여하므로 아직 커밋되지 않은 INSERT가 서비스 예외와 함께 롤백된다. 서비스의 `@Transactional`이 없다면 repository의 저장이 먼저 커밋되어 실패한 행이 남으므로 이후 DB 검증이 실패한다. 테스트 메서드 자체에는 `@Transactional`을 붙이지 않아 서비스 트랜잭션의 누락을 테스트 트랜잭션이 가리지 않도록 했다.

예외 주입 advice는 `finally`에서 제거한다. 그 뒤 실패한 신청의 부재와 기존 신청 보존을 확인하고, 다른 작업 스레드에서 같은 사용자가 10초 안에 재신청에 성공하는지 확인한다. 자동 증가 ID의 연속성은 롤백의 조건으로 검사하지 않는다.

## 실행 방법과 결과

JDK 21과 실행 중인 Docker가 필요하다. Testcontainers가 MySQL을 별도로 시작하므로 개발 DB를 사용할 필요는 없다. 첫 실행에는 Gradle 의존성과 컨테이너 이미지 다운로드가 필요할 수 있다.

프로젝트 루트의 PowerShell에서 요청한 두 시나리오와 롤백 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests '*EnrollmentCapacityConcurrencyIntegrationTest' --tests '*EnrollmentRollbackIntegrationTest'
```

기존 수강신청 흐름까지 포함한 회귀 검증:

```powershell
.\gradlew.bat test --tests '*EnrollmentCapacityConcurrencyIntegrationTest' --tests '*EnrollmentRollbackIntegrationTest' --tests '*EnrollmentLifecycleApiTest'
```

2026-11-23 검증 결과: 위 회귀 검증 명령에 `--offline --no-daemon`을 추가하여 실행했고, **9개 테스트 모두 통과**했다. 기존 테스트 파일에 변경이 없다는 것도 `git diff --exit-code HEAD -- src/test`로 확인했다.

| 테스트 클래스 | 실행 수 | 결과 |
| --- | --- | --- |
| `EnrollmentCapacityConcurrencyIntegrationTest` | 2 | 정원 30명/100명 경쟁, 정원 5명 신청·취소 혼합 모두 통과 |
| `EnrollmentRollbackIntegrationTest` | 1 | 저장 후 예외, DB 롤백, 기존 신청 보존, 별도 스레드 재신청 통과 |
| `EnrollmentLifecycleApiTest` | 6 | 기존 파일을 수정하지 않고 모두 통과 |

보고서는 프로젝트 루트의 `build/reports/tests/test/index.html`, XML 결과는 `build/test-results/test`에 생성된다.

위 명령은 지정한 테스트만 실행한다. 원본 `EnrollmentConcurrencyApiTest`의 대시보드 `404` 실패는 해결되지 않았으며, 대시보드 등 아직 구현하지 않은 API의 테스트와 동시 중복 취소 테스트를 포함한 전체 테스트 모음의 통과를 의미하지 않는다.
