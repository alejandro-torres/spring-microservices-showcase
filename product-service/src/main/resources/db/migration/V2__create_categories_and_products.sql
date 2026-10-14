CREATE TABLE categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- Nombre único sin distinguir mayúsculas (también para los borrados: el nombre queda reservado).
CREATE UNIQUE INDEX uk_categories_name ON categories (lower(name));

CREATE TABLE products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(120)   NOT NULL,
    description VARCHAR(1000),
    price       NUMERIC(10, 2) NOT NULL,
    stock       INT            NOT NULL,
    category_id BIGINT         NOT NULL REFERENCES categories (id),
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    -- Última red de seguridad: aunque fallara el código, la base de datos no admite stock negativo.
    CONSTRAINT ck_products_stock_not_negative CHECK (stock >= 0),
    CONSTRAINT ck_products_price_positive CHECK (price > 0)
);

CREATE UNIQUE INDEX uk_products_name ON products (lower(name));

-- Índices para los filtros y la ordenación más habituales en el listado del catálogo.
CREATE INDEX idx_products_category_active ON products (category_id, active);
CREATE INDEX idx_products_active_price ON products (active, price);
CREATE INDEX idx_products_active_created_at ON products (active, created_at);
