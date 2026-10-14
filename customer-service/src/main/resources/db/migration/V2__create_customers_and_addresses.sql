CREATE TABLE customers (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Usuario de auth-service. No es clave foránea: está en otra base de datos.
    user_id     BIGINT,
    first_name  VARCHAR(80)  NOT NULL,
    last_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(30),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_customers_user_id UNIQUE (user_id),
    CONSTRAINT uk_customers_email UNIQUE (email)
);

CREATE TABLE addresses (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customers (id),
    street      VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20)  NOT NULL,
    country     VARCHAR(2)   NOT NULL,
    main        BOOLEAN      NOT NULL DEFAULT FALSE
);

-- "Una sola dirección principal" lo garantiza el dominio: un índice único parcial fallaría al cambiarla,
-- porque Hibernate no garantiza el orden de los UPDATE y DELETE dentro del flush.
CREATE INDEX idx_addresses_customer_id ON addresses (customer_id);
CREATE INDEX idx_customers_active_last_name ON customers (active, last_name);
