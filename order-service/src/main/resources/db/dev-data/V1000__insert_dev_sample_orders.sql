-- Datos de ejemplo (solo perfil dev): 120 pedidos de los clientes 1-50 con 1-3 líneas de los productos 1-64.
-- Los ids coinciden con los datos de ejemplo de customer-service y product-service.
INSERT INTO orders (customer_id, status, total, shipping_street, shipping_city, shipping_postal_code,
                    shipping_country, created_at, updated_at)
SELECT 1 + (n % 50),
       (ARRAY ['PENDING', 'CONFIRMED', 'CONFIRMED', 'CONFIRMED', 'REJECTED', 'CANCELLED'])[1 + n % 6],
       0,
       'Calle Ejemplo ' || n,
       (ARRAY ['Madrid', 'Barcelona', 'Valencia', 'Sevilla', 'Bilbao'])[1 + n % 5],
       lpad((1000 + n * 37 % 50000)::TEXT, 5, '0'),
       'ES',
       now() - (n || ' hours')::INTERVAL,
       now() - (n || ' hours')::INTERVAL
FROM generate_series(1, 120) AS n;

INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity)
SELECT o.id,
       1 + (o.id * 7 + line * 13) % 64,
       'Producto ' || (1 + (o.id * 7 + line * 13) % 64),
       round((9.99 + ((o.id * 31 + line * 17) % 500))::NUMERIC, 2),
       1 + (o.id + line) % 3
FROM orders o
CROSS JOIN generate_series(1, 3) AS line
WHERE line <= 1 + o.id % 3;

UPDATE orders o
SET total = (SELECT sum(i.unit_price * i.quantity) FROM order_items i WHERE i.order_id = o.id);
