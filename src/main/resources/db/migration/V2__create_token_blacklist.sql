CREATE TABLE token_blacklist
(
  jti VARCHAR(36) NOT NULL COMMENT 'JWT의 PK, UUID를 사용',
  exp TIMESTAMP   NOT NULL COMMENT 'JWT의 만료 일시',
  PRIMARY KEY (jti)
) COMMENT '로그아웃으로 인해 블랙리스트된 토큰을 기록합니다.';