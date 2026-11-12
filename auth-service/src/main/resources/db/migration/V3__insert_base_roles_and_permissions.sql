-- Permissions in the resource:action format and the system's two base roles.
INSERT INTO permissions (name, description, created_at, updated_at) VALUES
    ('user:read',       'View users',now(), now()),
    ('user:write',      'Create, edit and delete users', now(), now()),
    ('role:read',       'View roles',                 now(), now()),
    ('role:write',      'Create, edit and delete roles',now(), now()),
    ('permission:read', 'View permissions',              now(), now()),
    ('permission:write','Create, edit and delete permissions', now(), now()),
    ('product:read',    'View products',now(), now()),
    ('product:write',   'Create, edit and delete products',now(), now()),
    ('customer:read',   'View customers',now(), now()),
    ('customer:write',  'Create, edit and delete customers', now(), now()),
    ('order:read',      'View orders',now(), now()),
    ('order:write',     'Create and cancel orders',now(), now());

INSERT INTO roles (name, description, created_at, updated_at) VALUES
    ('ADMIN', 'Full system administration', now(), now()),
    ('USER',  'Store customer',                now(), now());

-- ADMIN has all the permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN';

-- USER can view the catalog and their customer data and manage their orders.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
    ON p.name IN ('product:read', 'customer:read', 'order:read', 'order:write')
WHERE r.name = 'USER';
