-- Permisos con el formato recurso:accion y los dos roles base del sistema.
INSERT INTO permissions (name, description, created_at, updated_at) VALUES
    ('user:read',       'Consultar usuarios',              now(), now()),
    ('user:write',      'Crear, editar y borrar usuarios', now(), now()),
    ('role:read',       'Consultar roles',                 now(), now()),
    ('role:write',      'Crear, editar y borrar roles',    now(), now()),
    ('permission:read', 'Consultar permisos',              now(), now()),
    ('permission:write','Crear, editar y borrar permisos', now(), now()),
    ('product:read',    'Consultar productos',             now(), now()),
    ('product:write',   'Crear, editar y borrar productos',now(), now()),
    ('customer:read',   'Consultar clientes',              now(), now()),
    ('customer:write',  'Crear, editar y borrar clientes', now(), now()),
    ('order:read',      'Consultar pedidos',               now(), now()),
    ('order:write',     'Crear y cancelar pedidos',        now(), now());

INSERT INTO roles (name, description, created_at, updated_at) VALUES
    ('ADMIN', 'Administración completa del sistema', now(), now()),
    ('USER',  'Cliente de la tienda',                now(), now());

-- ADMIN tiene todos los permisos.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN';

-- USER puede consultar el catálogo, sus datos de cliente y gestionar sus pedidos.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
    ON p.name IN ('product:read', 'customer:read', 'order:read', 'order:write')
WHERE r.name = 'USER';
