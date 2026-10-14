-- SOLO PERFIL dev: catálogo de ejemplo para probar la paginación y los filtros.
-- Nunca se carga en producción: esta carpeta solo se añade a spring.flyway.locations en application-dev.yml.

INSERT INTO categories (name, description, created_at, updated_at) VALUES
    ('Portátiles',  'Ordenadores portátiles',              TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Monitores',   'Pantallas y monitores',               TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Periféricos', 'Teclados, ratones y accesorios',      TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Audio',       'Auriculares y altavoces',             TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Móviles',     'Smartphones',                         TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Tablets',     'Tabletas y lectores',                 TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Almacenamiento', 'Discos y memorias',                TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00'),
    ('Redes',       'Routers y accesorios de red',         TIMESTAMPTZ '2026-01-01 09:00:00+00', TIMESTAMPTZ '2026-01-01 09:00:00+00');

-- 64 productos: 8 por categoría, con precios y stock variados. Cada 9º sin stock y cada 15º borrado.
INSERT INTO products (name, description, price, stock, category_id, active, created_at, updated_at)
SELECT c.name || ' modelo ' || lpad(n::text, 2, '0'),
       'Producto de ejemplo ' || n || ' de la categoría ' || c.name,
       round((19.90 + ((n * 37 + c.id * 53) % 1500))::numeric, 2),
       CASE WHEN (c.id * 8 + n) % 9 = 0 THEN 0 ELSE (n * 7 + c.id * 3) % 120 + 1 END,
       c.id,
       (c.id * 8 + n) % 15 <> 0,
       TIMESTAMPTZ '2026-01-01 09:00:00+00' + ((c.id * 8 + n) || ' days')::interval,
       TIMESTAMPTZ '2026-01-01 09:00:00+00' + ((c.id * 8 + n) || ' days')::interval
FROM categories c CROSS JOIN generate_series(1, 8) AS n;
