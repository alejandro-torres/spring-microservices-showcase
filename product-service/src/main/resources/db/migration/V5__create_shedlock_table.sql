-- ShedLock locks: each scheduled task (outbox, cleanups...) is run by a single replica at a time.
-- TIMESTAMP without time zone, as ShedLock states: it stores the database's UTC time (usingDbTime).
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
