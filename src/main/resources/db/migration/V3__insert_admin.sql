INSERT INTO users (
    email,
    password,
    name,
    githubUsername,
    role,
    status,
    createdAt,
    seminarId
) VALUES (
    'admin@wafflestudio.com',
    '$2a$10$FQIyvcYDluX6SVPCzGY5oekCo6iLyM7RZJJK48kfxGBtq5hWj4qTW',
    '와장',
    'waffle-admin',
    'ADMIN',
    'APPROVED',
    CURRENT_TIMESTAMP,
    NULL
);