-- waggle 서비스를 위한 테이블 스킴을 정의해주세요.
-- 참고:  한 번 실행된 마이그레이션 파일을 고치면 재 실행 시 Flyway 가 체크섬이 달라졌다며 실패합니다.
-- 아직 내용을 채우기 전이라면 `docker compose down -v` 로 DB 를 비우고 다시 띄우면 됩니다.
-- 파일을 고치는 대신 새 버전의 마이그레이션 파일을 만드는 것이 편합니다.

CREATE TABLE enrollments
(
  id                 BIGINT   NOT NULL AUTO_INCREMENT,
  graceDaysRemaining INT      NOT NULL COMMENT '남은 Grace Day',
  isFailed           BOOLEAN  NOT NULL DEFAULT FALSE COMMENT '탈락 여부',
  createdAt          DATETIME NOT NULL,
  rookieId           BIGINT   NOT NULL,
  seminarId          BIGINT   NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE participations
(
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  attendanceStatus VARCHAR(255) NULL     COMMENT '출석 상태',
  assignmentStatus VARCHAR(255) NULL     COMMENT '과제 상태',
  rookieId         BIGINT       NOT NULL,
  sessionId        BIGINT       NOT NULL,
  PRIMARY KEY (id)
) COMMENT '출석과 과제 기록';

CREATE TABLE seminars
(
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  title          VARCHAR(255) NOT NULL COMMENT '제목',
  description    TEXT         NULL     COMMENT '세미나 소개 글',
  capacity       INT          NOT NULL COMMENT '신청할 수 있는 최대 인원',
  applyStartAt   DATETIME     NOT NULL COMMENT '수강 신청을 받는 기간 (시작)',
  applyEndAt     DATETIME     NOT NULL COMMENT '수강 신청을 받는 기간 (종료)',
  totalGraceDays INT          NOT NULL COMMENT '총 Grace Day, 수강생마다 이 값에서 시작합니다',
  PRIMARY KEY (id)
);

CREATE TABLE sessions
(
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  title             VARCHAR(255) NOT NULL COMMENT '그 회차 수업의 제목',
  startsAt          DATETIME     NOT NULL COMMENT '시작 일시',
  location          VARCHAR(255) NOT NULL COMMENT '수업 장소',
  assignmentTitle   VARCHAR(255) NOT NULL COMMENT '과제 제목',
  lectureContent    TEXT         NULL     COMMENT '수업 설명',
  assignmentContent TEXT         NULL     COMMENT '과제 설명',
  seminarId         BIGINT       NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE users
(
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  email          VARCHAR(255) NOT NULL COMMENT '이메일',
  password       VARCHAR(255) NOT NULL COMMENT '비밀번호',
  name           VARCHAR(255) NOT NULL COMMENT '이름',
  githubUsername VARCHAR(255) NOT NULL COMMENT '깃허브 아이디',
  role           VARCHAR(255) NOT NULL COMMENT '역할',
  status         VARCHAR(255) NOT NULL COMMENT '승인 여부',
  createdAt      DATETIME     NOT NULL,
  seminarId      BIGINT       NULL    ,
  PRIMARY KEY (id)
);

ALTER TABLE users
  ADD CONSTRAINT UQ_users_email UNIQUE (email);

ALTER TABLE sessions
  ADD CONSTRAINT FK_seminars_TO_sessions
    FOREIGN KEY (seminarId)
    REFERENCES seminars (id);

ALTER TABLE enrollments
  ADD CONSTRAINT FK_users_TO_enrollments
    FOREIGN KEY (rookieId)
    REFERENCES users (id);

ALTER TABLE enrollments
  ADD CONSTRAINT FK_seminars_TO_enrollments
    FOREIGN KEY (seminarId)
    REFERENCES seminars (id);

ALTER TABLE users
  ADD CONSTRAINT FK_seminars_TO_users
    FOREIGN KEY (seminarId)
    REFERENCES seminars (id);

ALTER TABLE participations
  ADD CONSTRAINT FK_sessions_TO_participations
    FOREIGN KEY (sessionId)
    REFERENCES sessions (id);

ALTER TABLE participations
  ADD CONSTRAINT FK_users_TO_participations
    FOREIGN KEY (rookieId)
    REFERENCES users (id);

CREATE UNIQUE INDEX UQ_enrollments_rookieId_seminarId
  ON enrollments (rookieId ASC, seminarId ASC);

CREATE UNIQUE INDEX UQ_participations_rookieId_sessionId
  ON participations (rookieId ASC, sessionId ASC);