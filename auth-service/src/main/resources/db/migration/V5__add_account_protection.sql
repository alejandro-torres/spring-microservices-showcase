-- Account lockout after several failed login attempts.
ALTER TABLE users ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMPTZ;

-- Security audit log. It stores the username as text (no foreign key) to keep the history
-- even if the user changes or is deleted, and also the attempts with users that do not exist.
CREATE TABLE security_audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type  VARCHAR(30)  NOT NULL,
    username    VARCHAR(100) NOT NULL,
    ip          VARCHAR(45),
    user_agent  VARCHAR(255),
    trace_id    VARCHAR(64),
    details     VARCHAR(500),
    occurred_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_security_audit_log_occurred_at ON security_audit_log (occurred_at);
CREATE INDEX idx_security_audit_log_username ON security_audit_log (username, occurred_at);

-- Password history to prevent reusing the latest ones.
CREATE TABLE password_history (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_password_history_user_id ON password_history (user_id, created_at DESC);
