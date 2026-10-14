-- SOLO PERFIL dev: 50 clientes de ejemplo con una o dos direcciones. Cada 12º está borrado.
INSERT INTO customers (user_id, first_name, last_name, email, phone, active, created_at, updated_at)
SELECT 1 + n,
       (ARRAY['Ana','Luis','Marta','Jorge','Lucía','Pablo','Elena','Sergio','Carmen','Javier'])[1 + n % 10],
       (ARRAY['García','Martínez','López','Sánchez','Pérez','Gómez','Ruiz','Díaz','Moreno','Álvarez'])[1 + (n * 7) % 10],
       'cliente' || lpad(n::text, 2, '0') || '@showcase.dev',
       '+34 600 000 ' || lpad(n::text, 3, '0'),
       n % 12 <> 0,
       TIMESTAMPTZ '2026-01-01 09:00:00+00' + (n || ' days')::interval,
       TIMESTAMPTZ '2026-01-01 09:00:00+00' + (n || ' days')::interval
FROM generate_series(1, 50) AS n;

INSERT INTO addresses (customer_id, street, city, postal_code, country, main)
SELECT c.id, 'Calle Mayor ' || c.id, (ARRAY['Madrid','Barcelona','Valencia','Sevilla','Bilbao'])[1 + c.id % 5],
       lpad((28000 + c.id)::text, 5, '0'), 'ES', TRUE
FROM customers c;

INSERT INTO addresses (customer_id, street, city, postal_code, country, main)
SELECT c.id, 'Avenida del Puerto ' || c.id, 'Málaga', '29001', 'ES', FALSE
FROM customers c WHERE c.id % 3 = 0;
