-- Locks de ShedLock: cada tarea programada (outbox, limpiezas...) la ejecuta una sola réplica a la vez.
-- TIMESTAMP sin zona horaria, como indica ShedLock: guarda la hora UTC de la base de datos (usingDbTime).
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
