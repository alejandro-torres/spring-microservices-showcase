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
| `common-lib` | Excepciones de dominio, paginación (`PageQuery`/`PageResult`), filtros dinámicos (parser y `Specification` de JPA), gestión global de errores y mensajes en español e inglés, y la mensajería común (outbox, consumo idempotente, reintentos y DLQ) |
| `events-contract` | Contratos de los mensajes de RabbitMQ: el sobre común `EventEnvelopeDTO` y un `*MessageDTO` por evento |
| `auth-service` | Usuarios, roles y permisos, y servidor de autorización OAuth2 que emite los JWT (puerto 9000) |
| `product-service` | Catálogo de productos y categorías, y gestión del stock (puerto 8081) |
| `customer-service` | Clientes y sus direcciones (puerto 8082) |
| `order-service` | Pedidos y sus líneas; consulta clientes y productos con Feign y Resilience4j (puerto 8083) |

## RabbitMQ en local

Los servicios se comunican por RabbitMQ, así que al arrancarlos en local todos se conectan al mismo broker (`localhost:5672`, configurable con `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME` y `RABBITMQ_PASSWORD`). Se levanta una vez con Docker:

```bash
docker run -d --name showcase-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

La consola de RabbitMQ queda en http://localhost:15672 (usuario y contraseña `guest`). Los tests de integración no lo necesitan: cada uno arranca su propio RabbitMQ con Testcontainers.

## Arrancar auth-service en local

No hace falta instalar PostgreSQL ni Redis: el servicio se arranca con los dos en Docker (Testcontainers) y el perfil `dev`, que carga datos de ejemplo. Solo necesita el RabbitMQ compartido de la sección anterior.

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
| Registro | `POST /api/v1/auth/register` con `username`, `email`, `password`, `firstName` y `lastName` (crea un usuario con el rol USER; customer-service crea su perfil de cliente al recibir `user.registered`) | Público |
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

## Arrancar customer-service en local

Con PostgreSQL en Docker y 50 clientes de ejemplo con sus direcciones:

```bash
./mvnw -pl customer-service spring-boot:test-run -Dspring-boot.run.profiles=dev
```

Escucha en el puerto 8082 y valida los tokens igual que product-service.

| Recurso | Endpoints | Permisos |
|---|---|---|
| Clientes | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` en `/api/v1/customers`, y `GET /by-user/{userId}` (perfil del usuario de auth-service) | `customer:read` / `customer:write` |
| Direcciones | `POST /{id}/addresses`, `DELETE /{id}/addresses/{addressId}` y `PUT /{id}/addresses/{addressId}/main` en `/api/v1/customers` | `customer:write` |

Reglas: el email (sin distinguir mayúsculas) y el usuario de auth-service son únicos; un cliente tiene como máximo 5 direcciones y, si tiene alguna, exactamente una es la principal (la primera que se añade, y si se borra, la siguiente).

| Recurso | Filtros | Ordenación |
|---|---|---|
| Clientes | `firstName`, `lastName`, `email` (eq, like, startsWith), `city` (eq, like; cualquiera de sus direcciones), `userId` (eq), `createdAt` (gte, lte, between) | `id`, `firstName`, `lastName`, `email`, `createdAt` |

## Arrancar order-service en local

Con PostgreSQL en Docker y 120 pedidos de ejemplo (de los clientes y productos de ejemplo de los otros servicios):

```bash
./mvnw -pl order-service spring-boot:test-run -Dspring-boot.run.profiles=dev
```

Escucha en el puerto 8083. Para crear pedidos consulta customer-service (dirección de envío) y product-service (nombre, precio y stock) con Feign, así que los dos tienen que estar arrancados. Sus URLs se configuran con `CUSTOMER_SERVICE_URL` y `PRODUCT_SERVICE_URL`.

| Recurso | Endpoints | Permisos |
|---|---|---|
| Pedidos | `POST`, `GET /{id}`, `GET`, `PUT /{id}` (cambia la dirección de envío de un pedido no cerrado) en `/api/v1/orders` | `order:read` / `order:write` |
| Pedidos de un cliente | `GET /api/v1/orders/by-customer/{customerId}` (paginado, del más reciente al más antiguo) | `order:read` |
| Cancelar | `PUT /api/v1/orders/{id}/cancel` (solo pedidos confirmados; un pedido pendiente responde 422 `ORDER_NOT_CANCELLABLE`) | `order:write` |
| Borrar | `DELETE /api/v1/orders/{id}` (borrado lógico de pedidos cancelados o rechazados) | `order:write` y rol ADMIN |

La petición solo lleva productos y cantidades: el nombre y el precio se copian de product-service y la dirección de envío es la principal del cliente, para que el pedido no cambie si después cambia el catálogo.

| Recurso | Filtros | Ordenación |
|---|---|---|
| Pedidos | `status` (eq, in), `customerId` (eq), `productId` (eq; pedidos que contienen el producto), `total` (gte, lte, between), `createdAt` (gte, lte, between) | `id`, `total`, `status`, `createdAt` |

### Llamadas a otros servicios

| Situación | Respuesta |
|---|---|
| El cliente o un producto no existen | 404 (`CUSTOMER_NOT_FOUND`, `PRODUCT_NOT_FOUND`) |
| No hay stock suficiente | 422 (`INSUFFICIENT_STOCK`); es una comprobación previa para fallar pronto, la reserva real la hace la saga |
| El otro servicio falla o tarda más de 3 s | 503 (`SERVICE_UNAVAILABLE`) sin detalles del error remoto |
| Varios fallos seguidos | El circuit breaker (Resilience4j) se abre 10 s y responde 503 al instante, sin esperar al timeout |

El JWT del usuario se reenvía en cada llamada, así que se aplican sus propios permisos en el otro servicio. Las llamadas se hacen antes de abrir la transacción, para no tener una conexión a la base de datos bloqueada mientras se espera la respuesta.

## Mensajería con RabbitMQ

Feign se usa para las consultas síncronas (comprobar que algo existe); todo lo que cambia el estado de otro servicio va por RabbitMQ.

### Flujos

| Flujo | Eventos |
|---|---|
| Alta de usuario → perfil de cliente | auth `user.registered` → customer crea el cliente (sin direcciones) |
| Usuario desactivado o borrado | auth `user.disabled` → customer marca el cliente como inactivo |
| Cuenta bloqueada | auth `account.locked` → customer lo registra en su log (el bloqueo es temporal y no cambia el cliente) |
| Saga de creación de pedido | order crea el pedido `PENDING` y publica `order.created` → product reserva el stock de todas las líneas o de ninguna y responde `stock.reserved` o `stock.reservation-failed` → order pasa el pedido a `CONFIRMED` o `REJECTED` (con el motivo en `rejectionReason`) |
| Cancelación (compensación) | order cancela un pedido `CONFIRMED` y publica `order.cancelled` → product devuelve el stock y responde `stock.released` |

Las líneas de un pedido quedan fijas al crearlo: son las que se reservan en la saga. Por eso `PUT /orders/{id}` solo cambia la dirección de envío.

### Topología

- Un exchange `topic` duradero por servicio que publica: `auth.events`, `order.events` y `product.events`. La routing key es el tipo de evento (`<entidad>.<acción>`).
- Una cola duradera por consumidor y evento (`<servicio>.<evento>`, p. ej. `product-service.order-created`). Todas las réplicas de un servicio escuchan la misma cola, así que cada mensaje lo procesa una sola.
- Cada cola manda sus mensajes rechazados al DLX del servicio (`<servicio>.dlx`), que los deja en su DLQ (`<cola>.dlq`).
- Los nombres están en `application.yml` (`showcase.messaging`); exchanges, colas y bindings se declaran al arrancar.

Todos los mensajes llevan el mismo sobre en JSON:

```json
{ "eventId": "uuid", "eventType": "order.created", "version": 1, "occurredAt": "2026-10-14T10:00:00Z",
  "source": "order-service", "payload": { "orderId": 7, "customerId": 3, "lines": [{ "productId": 10, "quantity": 2 }] } }
```

### Fiabilidad

| Problema | Solución |
|---|---|
| Guardar el cambio y no llegar a publicar el evento (o al revés) | **Outbox**: el evento se guarda en la tabla `outbox_event` en la misma transacción que el cambio; un publicador programado lo envía después |
| No saber si RabbitMQ ha recibido el mensaje | **Publisher confirms y returns**: un evento solo pasa a `SENT` cuando RabbitMQ lo confirma y tenía una cola de destino. Si RabbitMQ está caído, el evento espera en la tabla |
| Mensajes duplicados (RabbitMQ entrega "al menos una vez") | **Consumo idempotente**: cada consumidor guarda el `eventId` en `processed_event` en la misma transacción que su cambio y descarta los repetidos |
| Fallos pasajeros (base de datos caída, conflicto de concurrencia) | **Reintentos**: 3 reintentos esperando 1 s, 2 s y 4 s; después, a la DLQ |
| Fallos que no se arreglan reintentando (JSON mal formado, pedido inexistente, email repetido) | Directos a la **DLQ**, sin reintentos |

### Administración de las DLQ (solo ADMIN)

En cada servicio que consume eventos (customer, product y order):

| Endpoint | Qué hace |
|---|---|
| `GET /api/v1/admin/dead-letters` | Lista las DLQ del servicio con su cola original y cuántos mensajes tienen |
| `GET /api/v1/admin/dead-letters/{dlq}/messages?limit=10` | Muestra los primeros mensajes (con el motivo del rechazo) sin sacarlos de la DLQ |
| `POST /api/v1/admin/dead-letters/{dlq}/replay?max=100` | Devuelve los mensajes a su cola original, una vez corregido el problema |

## Documentación

- [Especificación del proyecto](docs/prompt-microservicios.md)
- [Diagramas UML](docs/diagramas-uml.html)

## Licencia

[MIT](LICENSE)
