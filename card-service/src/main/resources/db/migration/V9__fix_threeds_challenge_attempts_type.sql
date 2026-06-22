-- Fix entity/schema drift: ThreeDsSession.challengeAttempts is mapped as `int`
-- (Hibernate expects INTEGER), but V4 created the column as SMALLINT. With
-- spring.jpa.hibernate.ddl-auto=validate this mismatch fails card-service at
-- startup ("wrong column type ... found int2, but expecting integer").
--
-- Widen SMALLINT -> INTEGER (safe, no data loss; values are small attempt counts).
ALTER TABLE threeds_sessions ALTER COLUMN challenge_attempts TYPE INTEGER;
