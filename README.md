# Spring Microservices Showcase

Sistema de microservicios en Java 21 y Spring Boot 4 con **arquitectura hexagonal**, pensado como demostración de lo que se puede construir en sistemas distribuidos con Java.

> 🚧 Proyecto en construcción. El desarrollo avanza por fases, cada una en su propio pull request.

## Qué incluirá

- **4 microservicios de negocio:** `auth-service` (usuarios, roles y OAuth2), `product-service`, `customer-service` y `order-service`.
- **Infraestructura:** API Gateway (Spring Cloud Gateway) y descubrimiento de servicios (Eureka).
- **Persistencia:** JPA + PostgreSQL, una base de datos por servicio, migraciones con Flyway.
- **Listados paginados** con `Page` y **filtros dinámicos** (`?filter=campo:operador:valor`).
- **Mensajería** con RabbitMQ: patrón outbox, saga de pedidos y colas de errores (DLQ).
- **Escalado horizontal:** varias réplicas por servicio, ShedLock y locks distribuidos con Redis.
- **Observabilidad:** trazas con Zipkin, métricas con Prometheus, logs con Loki y dashboards en Grafana.
- **Errores** con códigos HTTP estándar, mensajes en español e inglés y sin exponer detalles internos.
- **Tests** unitarios con JUnit 5 y Mockito, y de integración con Testcontainers.

## Compilar y ejecutar los tests

Requisitos: **JDK 21** y **Docker** (para los tests de integración con Testcontainers). Maven no hace falta: el proyecto incluye Maven Wrapper.

```bash
./mvnw verify
```

En Windows (PowerShell o CMD) se usa `mvnw.cmd verify`. El informe de cobertura queda en `<módulo>/target/site/jacoco/index.html`.

## Estructura

| Módulo | Contenido |
|---|---|
| `common-lib` | Excepciones de dominio, paginación (`PageQuery`/`PageResult`), filtros dinámicos (parser y `Specification` de JPA), gestión global de errores y mensajes en español e inglés |
| `auth-service` | Usuarios, roles y permisos, y servidor de autorización OAuth2 que emite los JWT (puerto 9000) |
| `product-service` | Catálogo de productos y categorías, y gestión del stock (puerto 8081) |

## Arrancar auth-service en local

No hace falta instalar PostgreSQL ni Redis: el servicio se arranca con los dos en Docker (Testcontainers) y el perfil `dev`, que carga datos de ejemplo.

```bash
./mvnw install -DskipTests
```
```bash
./mvnw -pl auth-service spring-boot:test-run -Dspring-boot.run.profiles=dev
```

### Autenticación (OAuth2)

La API exige un JWT en la cabecera `Authorization: Bearer <token>`. auth-service lo emite:

| Cliente | Flujo | Para qué |
|---|---|---|
| `web-client` | `authorization_code` con PKCE, sin secreto | Aplicaciones de navegador: el usuario inicia sesión en el formulario de auth-service |
| `internal-service` | `client_credentials` | Llamadas entre microservicios (scope `internal`) |

El token de un usuario dura 15 minutos y lleva sus roles y permisos (`roles`, `permissions`), que cada servicio comprueba con `@PreAuthorize`. Las claves públicas para validarlo están en `/oauth2/jwks` y la configuración en `/.well-known/openid-configuration`.

Para probar la API desde la terminal, la forma más rápida es un token de servicio (en el perfil `dev`, el secreto de `internal-service` es `internal-dev-secret`):

```bash
curl -u internal-service:internal-dev-secret -d grant_type=client_credentials -d scope=internal http://localhost:9000/oauth2/token
```

Ese token identifica al servicio, no a un usuario, así que no tiene permisos de gestión. Para actuar como `admin` hay que seguir el flujo `authorization_code` desde un navegador o un cliente como Postman (con PKCE y `redirect_uri` `http://127.0.0.1:3000/callback`).

Otros endpoints públicos:

```bash
curl -X POST http://localhost:9000/api/v1/auth/register -H "Content-Type: application/json" -d "{\"username\":\"nuevo\",\"email\":\"nuevo@example.com\",\"password\":\"Secreta-123\"}"
```
```bash
curl http://localhost:9000/oauth2/jwks
```

En otros entornos, las claves RSA (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`) y el secreto de `internal-service` (`INTERNAL_CLIENT_SECRET`) se pasan por variables de entorno. Sin claves configuradas se genera un par en memoria (los tokens dejan de valer al reiniciar).

### Protección de las cuentas

| Medida | Comportamiento |
|---|---|
| Bloqueo por intentos | 5 contraseñas incorrectas seguidas bloquean la cuenta 15 minutos (configurable en `showcase.security.login`). El bloqueo caduca solo o lo quita un ADMIN con `PUT /api/v1/users/{id}/unlock` |
| Política de contraseñas | De 8 a 72 caracteres, sin espacios, con mayúsculas, minúsculas, números y símbolos. No se pueden reutilizar las 3 últimas |
| Cambio de contraseña | `PUT /api/v1/users/me/password` con la contraseña actual y la nueva |
| Cierre de sesión | `POST /api/v1/auth/logout` revoca el token al instante |
| Revocación automática | Cambiar la contraseña, desactivar o borrar un usuario o quitarle un rol invalida todos sus tokens anteriores |
| Auditoría | Logins, bloqueos, cambios de roles y de contraseña y revocaciones, con IP, user agent y traceId. La consulta un ADMIN en `GET /api/v1/security-audit` |

Los tokens revocados se guardan en Redis con caducidad automática. Si Redis no responde, la API rechaza los tokens (es preferible un 401 a aceptar uno que podría estar revocado).

### Datos de ejemplo (solo perfil `dev`)

- Usuario `admin` (roles ADMIN y USER) y 60 usuarios `user01`…`user60`; cada 7º está deshabilitado y cada 10º borrado.
- Todos tienen la contraseña de desarrollo `Showcase-Dev-2026!`. Estos datos nunca se cargan fuera del perfil `dev`.

### API

| Recurso | Endpoints | Permisos |
|---|---|---|
| Registro | `POST /api/v1/auth/register` (crea un usuario con el rol USER) | Público |
| Usuario actual | `GET /api/v1/users/me` | Cualquier usuario autenticado |
| Usuarios | `POST /api/v1/users`, `GET /api/v1/users/{id}`, `GET /api/v1/users`, `PUT /api/v1/users/{id}`, `DELETE /api/v1/users/{id}`, `POST` y `DELETE /api/v1/users/{id}/roles/{roleId}` | `user:read` / `user:write` |
| Roles | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` en `/api/v1/roles` | `role:read` / `role:write` |
| Permisos | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` en `/api/v1/permissions` | `permission:read` / `permission:write` |

`includeInactive=true` (ver registros borrados) solo está permitido al rol ADMIN.

Los listados admiten `page`, `size` (máximo 100), `sort`, `direction`, `includeInactive` y filtros `filter=campo:operador:valor`:

| Recurso | Filtros | Ordenación |
|---|---|---|
| Usuarios | `username`, `email` (eq, like, startsWith), `enabled` (eq), `role` (eq, in), `createdAt` (gte, lte, between) | `id`, `username`, `email`, `createdAt`, `updatedAt` |
| Roles | `name` (eq, like, startsWith), `permission` (eq, in), `createdAt` | `id`, `name`, `createdAt` |
| Permisos | `name` (eq, like, startsWith), `createdAt` | `id`, `name`, `createdAt` |

## Arrancar product-service en local

Con PostgreSQL en Docker y un catálogo de ejemplo (8 categorías y 64 productos):

```bash
./mvnw -pl product-service spring-boot:test-run -Dspring-boot.run.profiles=dev
```

La API valida los tokens con la clave pública de auth-service (`AUTH_JWK_SET_URI`, por defecto `http://localhost:9000/oauth2/jwks`), así que auth-service tiene que estar arrancado para obtener un token.

| Recurso | Endpoints | Permisos |
|---|---|---|
| Productos | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` en `/api/v1/products` | `product:read` / `product:write` |
| Stock | `PUT /api/v1/products/{id}/stock` con `{"delta": -3}` (ajuste manual, nunca deja el stock en negativo) | Rol ADMIN |
| Categorías | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` en `/api/v1/categories`, y `GET /{id}/products` | `product:read` / `product:write` |

| Recurso | Filtros | Ordenación |
|---|---|---|
| Productos | `name` (eq, like, startsWith), `price` (eq, gt, gte, lt, lte, between), `stock` (eq, gt, gte, lt, lte), `categoryId` (eq, in), `categoryName` (eq, like), `createdAt` (gte, lte, between) | `id`, `name`, `price`, `stock`, `createdAt`, `updatedAt` |
| Categorías | `name` (eq, like, startsWith), `createdAt` | `id`, `name`, `createdAt` |

Ejemplo: portátiles entre 500 y 1500 € con stock, del más caro al más barato:

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/v1/products?filter=categoryName:like:portatil&filter=price:between:500,1500&filter=stock:gt:0&sort=price&direction=desc"
```

## Documentación

- [Especificación del proyecto](docs/prompt-microservicios.md)
- [Diagramas UML](docs/diagramas-uml.html)

## Licencia

[MIT](LICENSE)
