CREATE UNIQUE INDEX UQ_participations_enrollmentId_sessionId
    ON participations (enrollmentId ASC, sessionId ASC);