-- dev PROFILE ONLY: sample users to try pagination and filters.
-- They all share the development password given in the README ("Sample data" section).
-- Never loaded in production: this folder is only added to spring.flyway.locations in application-dev.yml.

INSERT INTO users (username, email, password_hash, enabled, active, created_at, updated_at)
VALUES ('admin', 'admin@showcase.dev', '$2a$10$vt6XF3DX6Y9/ho/ifi/9rO51cx63MjCsea9TqLhJzl5w5YCLoE7Z.', TRUE, TRUE,
        TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name IN ('ADMIN', 'USER') WHERE u.username = 'admin';

-- 60 users with spread sign-up dates; every 7th disabled and every 10th deleted (soft delete).
INSERT INTO users (username, email, password_hash, enabled, active, created_at, updated_at)
SELECT 'user' || lpad(n::text, 2, '0'),
       'user' || lpad(n::text, 2, '0') || '@showcase.dev',
       '$2a$10$vt6XF3DX6Y9/ho/ifi/9rO51cx63MjCsea9TqLhJzl5w5YCLoE7Z.',
       n % 7 <> 0,
       n % 10 <> 0,
       TIMESTAMPTZ '2026-01-01 09:00:00+00' + (n || ' days')::interval,
       TIMESTAMPTZ '2026-01-01 09:00:00+00' + (n || ' days')::interval
FROM generate_series(1, 60) AS n;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'USER' WHERE u.username LIKE 'user%';
