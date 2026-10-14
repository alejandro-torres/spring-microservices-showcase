CREATE TABLE orders (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Cliente de customer-service. No es clave foránea: está en otra base de datos.
    customer_id          BIGINT        NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    -- Se guarda (además de calcularse en el dominio) para poder filtrar y ordenar por importe.
    total                NUMERIC(12, 2) NOT NULL,
    shipping_street      VARCHAR(255)  NOT NULL,
    shipping_city        VARCHAR(100)  NOT NULL,
    shipping_postal_code VARCHAR(20)   NOT NULL,
    shipping_country     VARCHAR(2)    NOT NULL,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_orders_total CHECK (total >= 0)
);

CREATE TABLE order_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT         NOT NULL REFERENCES orders (id),
    -- Producto de product-service; el nombre y el precio son una copia del momento de la compra.
    product_id   BIGINT         NOT NULL,
    product_name VARCHAR(150)   NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_active_status ON orders (active, status);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
