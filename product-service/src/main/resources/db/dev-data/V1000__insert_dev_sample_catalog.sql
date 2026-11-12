-- dev PROFILE ONLY: sample catalog to try pagination and filters.
-- Never loaded in production: this folder is only added to spring.flyway.locations in application-dev.yml.

INSERT INTO categories (name, description, created_at, updated_at) VALUES
    ('Laptops',     'Laptop computers',TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Monitors',    'Screens and monitors',TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Peripherals', 'Keyboards, mice and accessories',TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Audio',       'Headphones and speakers',TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Phones',      'Smartphones',TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Tablets',     'Tablets and readers',TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Storage',     'Disks and memory',TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Networking',  'Routers and network accessories',TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00');

-- 64 products: 8 per category, with varied prices and stock. Every 9th out of stock and every 15th deleted.
INSERT INTO products (name, description, price, stock, category_id, active, created_at, updated_at)
SELECT c.name || ' model ' || lpad(n::text, 2, '0'),
       'Sample product ' || n || ' in category ' || c.name,
       round((19.90 + ((n * 37 + c.id * 53) % 1500))::numeric, 2),
       CASE WHEN (c.id * 8 + n) % 9 = 0 THEN 0 ELSE (n * 7 + c.id * 3) % 120 + 1 END,
       c.id,
       (c.id * 8 + n) % 15 <> 0,
       TIMESTAMPTZ '2026-01-01 09:00:00+00' + ((c.id * 8 + n) || ' days')::interval,
       TIMESTAMPTZ '2026-01-01 09:00:00+00' + ((c.id * 8 + n) || ' days')::interval
FROM categories c CROSS JOIN generate_series(1, 8) AS n;
