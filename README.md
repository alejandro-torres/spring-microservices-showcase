# Spring Microservices Showcase

Sistema de microservicios en Java 21 y Spring Boot 4 con **arquitectura hexagonal**, pensado como demostración de lo que se puede construir en sistemas distribuidos con Java.

> Desarrollado por fases, cada una en su propio pull request (de la estructura inicial a la observabilidad y las pruebas de extremo a extremo).

## Qué incluye

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

En Windows (PowerShell o CMD) se usa `mvnw.cmd verify`.

| Qué | Cómo |
|---|---|
| Solo los tests unitarios (`*Test`, JUnit 5 + Mockito, sin Spring ni Docker; segundos) | `./mvnw test` |
| Unitarios y de integración (`*IT`, con PostgreSQL, Redis y RabbitMQ reales en Testcontainers) | `./mvnw verify` |
| Cobertura | `./mvnw verify` falla si `domain` y `application` bajan del 80 % de líneas o del 70 % de ramas. Informe de cada módulo en `<módulo>/target/site/jacoco/index.html` e informe agregado de todos en `coverage-report/target/site/jacoco-aggregate/index.html` (sin configuración, DTOs, entidades, clases `*Application` ni el código de MapStruct) |
| De extremo a extremo contra el sistema en Docker | Con el sistema arrancado (`docker compose up -d --build`): `./mvnw -Pe2e -pl e2e-tests verify` |

Las pruebas de extremo a extremo ([e2e-tests](e2e-tests/src/test/java/com/showcase/e2e/ShowcaseE2EIT.java)) hacen lo mismo que un cliente real y todo a través del gateway: login con authorization code + PKCE en el formulario de auth-service, alta de un producto y de un cliente, pedido que la saga confirma descontando el stock, cancelación que lo devuelve y pedido sin stock rechazado con 422.

## Estructura

| Módulo | Contenido |
|---|---|
| `common-lib` | Excepciones de dominio, paginación (`PageQuery`/`PageResult`), filtros dinámicos (parser y `Specification` de JPA), gestión global de errores y mensajes en español e inglés, y la mensajería común (outbox, consumo idempotente, reintentos y DLQ) |
| `events-contract` | Contratos de los mensajes de RabbitMQ: el sobre común `EventEnvelopeDTO` y un `*MessageDTO` por evento |
| `auth-service` | Usuarios, roles y permisos, y servidor de autorización OAuth2 que emite los JWT (puerto 9000) |
| `product-service` | Catálogo de productos y categorías, y gestión del stock (puerto 8081) |
| `customer-service` | Clientes y sus direcciones (puerto 8082) |
| `order-service` | Pedidos y sus líneas; consulta clientes y productos con Feign y Resilience4j (puerto 8083) |
| `discovery-service` | Registro de servicios con Eureka (puerto 8761) |
| `api-gateway` | Punto de entrada único: valida el token, enruta, limita la tasa de peticiones y agrega Swagger (puerto 8080) |
| `coverage-report` | Informe de cobertura de JaCoCo agregado de todos los módulos |
| `e2e-tests` | Pruebas de extremo a extremo contra el sistema en Docker (perfil `e2e`) |

## Arrancar todo con Docker

Requisito: Docker con Compose. Las imágenes se compilan dentro de Docker, así que no hace falta ni el JDK.

```bash
docker compose up -d --build
```

La primera vez tarda unos minutos (descarga las dependencias y compila los 6 servicios). Las contraseñas salen de un fichero `.env` (copia [.env.example](.env.example)); sin él se usan valores de desarrollo.

| Consola | URL |
|---|---|
| API (gateway) | http://localhost:8080 |
| Swagger UI de todos los servicios | http://localhost:8080/swagger-ui.html |
| Eureka | http://localhost:8761 |
| RabbitMQ | http://localhost:15672 (usuario `admin`, contraseña `admin-dev` o la de `.env`) |
| Zipkin | http://localhost:9411 |

Solo el gateway expone la API; los servicios no publican puertos. Hay una base de datos y un usuario de PostgreSQL por servicio, y un usuario de RabbitMQ por servicio con permisos solo sobre sus exchanges y colas ([docker/rabbitmq/setup-users.sh](docker/rabbitmq/setup-users.sh)). El perfil por defecto es `dev` (datos de ejemplo y el 100 % de las trazas); con `SPRING_PROFILES_ACTIVE=prod`, logs en JSON y el 10 % de las trazas.

Los tokens se piden a través del gateway (`http://localhost:8080/oauth2/token`, login en `http://localhost:8080/login`), que es el emisor (`iss`) de los JWT. El gateway reenvía a los servicios las cabeceras `X-Forwarded-*` de los clientes que llegan desde la propia máquina o desde redes privadas (`GATEWAY_TRUSTED_PROXIES`); con un balanceador delante, indica su red ahí y `RATE_LIMIT_TRUSTED_PROXY_HOPS=1` para que el rate limiting use la IP real del cliente.

En [docs/curl-examples.md](docs/curl-examples.md) hay un recorrido completo con `curl`: cómo obtener un token de usuario, listados con filtros, cada código de error, cierre de sesión y cómo seguir un pedido en Zipkin y RabbitMQ.

## Arrancar todo en Kubernetes

El mismo sistema se puede desplegar en un clúster local de kind con un chart de Helm (`k8s/kind/cluster.sh create`, `k8s/deploy.sh` y un port-forward al gateway en el 8080). Los pasos y los requisitos están en [k8s/README.md](k8s/README.md).

## Arrancar los servicios uno a uno (desarrollo)

Para depurar un servicio desde el IDE, cada uno se arranca con `spring-boot:test-run`: levanta su PostgreSQL (y Redis en auth-service) en Docker con Testcontainers. Lo que comparten se arranca una vez:

```bash
docker run -d --name showcase-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```
```bash
./mvnw -pl discovery-service spring-boot:run
```

Todos se conectan a ese RabbitMQ (`localhost:5672`, usuario `guest`) y se registran en el Eureka de `localhost:8761`, así que se encuentran entre sí. Las trazas se envían a `localhost:9411` si hay un Zipkin arrancado (`docker run -d -p 9411:9411 openzipkin/zipkin:3`); si no, se descartan. Los tests de integración no necesitan nada de esto: cada uno arranca sus propios contenedores.

## Arrancar auth-service en local

No hace falta instalar PostgreSQL ni Redis: el servicio se arranca con los dos en Docker (Testcontainers) y el perfil `dev`, que carga datos de ejemplo. Necesita el RabbitMQ y el Eureka compartidos de la sección anterior.

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
curl -X POST http://localhost:9000/api/v1/auth/register -H "Content-Type: application/json" -d "{\"username\":\"nuevo\",\"email\":\"nuevo@example.com\",\"password\":\"Secreta-123\",\"firstName\":\"Nuevo\",\"lastName\":\"Usuario\"}"
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

Escucha en el puerto 8083. Para crear pedidos consulta customer-service (dirección de envío) y product-service (nombre, precio y stock) con Feign, así que los dos tienen que estar arrancados. Feign los busca en Eureka y reparte las llamadas entre sus réplicas; `CUSTOMER_SERVICE_URL` y `PRODUCT_SERVICE_URL` fijan una URL concreta (solo para pruebas).

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

## API Gateway

Toda la API se usa a través de `http://localhost:8080`. El gateway descubre las réplicas de cada servicio en Eureka (`lb://product-service`...) y reparte las peticiones entre ellas.

| Rutas | Servicio |
|---|---|
| `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/roles/**`, `/api/v1/permissions/**`, `/api/v1/security-audit/**`, `/login`, `/oauth2/**`, `/.well-known/**` | auth-service |
| `/api/v1/products/**`, `/api/v1/categories/**` | product-service |
| `/api/v1/customers/**` | customer-service |
| `/api/v1/orders/**` | order-service |
| `/api/v1/admin/{servicio}/dead-letters/**` | Las DLQ de ese servicio (el gateway quita el prefijo) |

| Función | Comportamiento |
|---|---|
| Autenticación | Valida la firma y la caducidad del JWT con el JWKS de auth-service y rechaza los tokens revocados (logout, cambio de contraseña, usuario desactivado) consultando la misma lista de Redis que auth-service. Públicas: registro, login, endpoints OAuth2, Swagger y health. El token se reenvía al servicio, que aplica sus permisos |
| Rate limiting | Token bucket en Redis (compartido por todas las réplicas): 20 peticiones por segundo con ráfagas de 40 por usuario (`sub` del JWT) o por IP si no hay token; el login (`POST /login` y `/oauth2/token`), 5 por minuto por IP. Al superarlo, **429** con `Retry-After` y el formato de error común |
| Circuit breaker | Uno por servicio (Resilience4j): una llamada que tarda más de 5 s cuenta como fallo; con la mitad de las últimas 20 fallidas se abre 10 s y responde **503** al instante |
| Errores | 401, 403, 404, 429 y 503 del gateway en el mismo `ErrorResponseDTO` y los mismos idiomas que los servicios |
| Traza | Cabecera `X-Trace-Id` en todas las respuestas: es el `traceId` que se busca en Zipkin |
| CORS | Centralizado en el gateway (`CORS_ALLOWED_ORIGINS`) |

Los límites se ajustan con variables de entorno (`RATE_LIMIT_REPLENISH_RATE`, `RATE_LIMIT_BURST_CAPACITY`, `LOGIN_RATE_LIMIT_*`, `GATEWAY_TIMEOUT`).

## Escalado horizontal

Con `docker compose up` arrancan 3 réplicas de product-service y 2 de order-service (`PRODUCT_SERVICE_REPLICAS`, `ORDER_SERVICE_REPLICAS`, o `docker compose up -d --scale product-service=5`). Nada se guarda en memoria entre peticiones: lo compartido está en PostgreSQL o en Redis.

| Pieza | Cómo funciona con varias réplicas |
|---|---|
| Descubrimiento y balanceo | Cada réplica se registra en Eureka con un id único; el gateway y Feign reparten las peticiones entre las réplicas vivas. La lista se refresca cada 5 s y una réplica que deja de enviar latidos sale del registro en 15 s |
| `X-Instance-Id` | Cada respuesta indica qué réplica la atendió (el mismo id que aparece en Eureka) |
| Apagado ordenado | Una réplica que se apaga termina las peticiones y los mensajes en curso (hasta 30 s) y el gateway reintenta los `GET` en otra réplica: no se pierde ninguna petición |
| Colas de RabbitMQ | Todas las réplicas escuchan la misma cola (*competing consumers*): cada mensaje lo procesa una sola, con `prefetch` 10 y de 1 a 4 consumidores por réplica |
| Tareas programadas | ShedLock (tabla `shedlock` de cada base de datos): cada tarea la ejecuta una sola réplica a la vez. Tareas: publicador del outbox (cada 5 s), limpieza de `processed_event` (de madrugada), desbloqueo automático de cuentas (cada minuto) y borrado de las autorizaciones OAuth2 caducadas (de madrugada) |
| Reserva de stock | Lock distribuido por producto en Redis (Redisson, `lock:product:{id}`), cogido antes de abrir la transacción y soltado después del commit; con varios productos, todos los locks en el mismo orden para evitar interbloqueos. Si no se consigue en 3 s, `RESOURCE_BUSY` (423) y el mensaje se reintenta. `@Version` sigue como segunda red de seguridad |

auth-service se mantiene en una réplica: genera su clave de firma de los JWT al arrancar. Para escalarlo hay que darle la misma clave a todas las réplicas con `JWT_PRIVATE_KEY` y `JWT_PUBLIC_KEY` (PEM).

## Escenarios de demostración

### Repartir el tráfico y apagar una réplica sin perder peticiones

```bash
docker compose up -d --build
```
```bash
for i in $(seq 1 9); do curl -s -D - -o /dev/null http://localhost:8080/product-service/v3/api-docs | grep -i x-instance-id; done
```

Las respuestas se reparten entre las 3 réplicas (tres valores distintos de `X-Instance-Id`). Con las peticiones en marcha, apaga una réplica:

```bash
docker stop spring-microservices-showcase-product-service-2
```

Todas las peticiones siguen respondiendo 200: la réplica termina lo que tenía en curso, el gateway reintenta en otra y, en unos segundos, Eureka deja de enviarle tráfico. En http://localhost:8761 se ve cómo desaparece del registro.

### Muchos pedidos a la vez sobre el último stock

El test [StockConcurrencyIT](product-service/src/test/java/com/showcase/product/StockConcurrencyIT.java) lanza 20 reservas simultáneas sobre un producto con 5 unidades: se reservan exactamente 5, las otras 15 se rechazan con `INSUFFICIENT_STOCK` y el stock nunca queda negativo. Con el sistema en Docker, lo mismo se ve creando a la vez varios pedidos del mismo producto: los que no tienen stock pasan a `REJECTED`.

## Trazabilidad con Zipkin

Todos los servicios, incluidos el gateway y auth-service, envían sus trazas a Zipkin (Micrometer Tracing con Brave). Una misma traza sigue la petición por el gateway, las llamadas Feign, cada consulta SQL (sin los valores de los parámetros) y los casos de uso marcados con `@Observed`. También sigue los mensajes de RabbitMQ: el outbox guarda el `traceparent` de la petición y el envío continúa esa traza, así que un pedido se ve de punta a punta: gateway → order-service → RabbitMQ → product-service → RabbitMQ → order-service.

Cada línea de log lleva `[traceId-spanId]`, y el `traceId` es el mismo que devuelven la cabecera `X-Trace-Id` y el campo `traceId` de los errores. Para buscar una petición: http://localhost:9411 → *Search by trace ID*.

## Observabilidad: Prometheus, Loki y Grafana

Se levanta junto al sistema con un segundo fichero de compose:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --build
```

| Consola | URL |
|---|---|
| Grafana (dashboards, logs y trazas) | http://localhost:3000 (usuario `admin`, contraseña `admin` o la de `.env`) |
| Prometheus (métricas y alertas) | http://localhost:9090 |

| Pieza | Detalle |
|---|---|
| Métricas | Cada servicio expone `/actuator/prometheus`, solo en la red interna (el gateway, en su puerto interno 8081). Prometheus descubre las réplicas en Eureka, sin listar instancias a mano. Todas las métricas llevan `application`, `instance` (la réplica, igual que `X-Instance-Id`) y `environment` |
| Técnicas | Peticiones, latencia con histogramas (p50/p95/p99), errores por endpoint, JVM, Hikari, circuit breakers, peticiones del gateway por ruta, colas y DLQ de RabbitMQ, eventos pendientes en el outbox (`outbox_events`) y espera de los locks (`distributed_lock_wait_seconds`) |
| De negocio | `showcase_orders_total` (creados, confirmados, rechazados con su motivo, cancelados), `showcase_stock_operations_total`, `showcase_auth_registrations_total`, `showcase_auth_logins_total` y `showcase_auth_accounts_locked_total`. Se cuentan después del commit |
| Logs | En Docker, cada servicio escribe JSON (ECS) con el servicio, la réplica, el nivel, el `traceId` y el `spanId`. Grafana Alloy los recoge de todos los contenedores y los envía a Loki |
| Correlación | Desde un punto de una gráfica de latencia (exemplar) se salta a su traza en Zipkin; desde un log, a su traza; y desde una traza, a sus logs |
| Dashboards | Carpeta *Showcase*: visión general, por microservicio, reparto de carga, mensajería, negocio y seguridad ([docker/grafana/dashboards](docker/grafana/dashboards)) |
| Alertas | Errores 5xx > 5 % durante 5 min, latencia p95 > 1 s, mensajes en una DLQ, circuit breaker abierto, réplica caída y eventos atascados en el outbox ([docker/prometheus/alerts.yml](docker/prometheus/alerts.yml)) |

### Escenario: seguir una petición de punta a punta

1. En Grafana, abre *Showcase → 1. Visión general del sistema* y busca en *Latencia p95* un punto (exemplar) de la petición que te interese; al pasar el ratón aparece su `trace_id` y el enlace a Zipkin.
2. En Zipkin se ve la petición entera: gateway → servicio → consultas SQL → envío a RabbitMQ → servicio que consume el evento.
3. Desde la traza, *Logs for this span* abre en Loki las líneas de log de esa petición en todos los servicios (también se puede buscar con `{service="order-service"} | traceId="<traceId>"`).
4. El mismo `traceId` lo devuelve la API en la cabecera `X-Trace-Id` y en el campo `traceId` de los errores.

## Documentación de la API (Swagger)

- Todos los servicios: http://localhost:8080/swagger-ui.html, con un desplegable por servicio.
- Cada servicio arrancado en local: `http://localhost:<puerto>/swagger-ui.html`.

Cada operación documenta sus posibles errores (400, 401, 403, 404, 409, 422, 500, 503) con el esquema `ErrorResponseDTO`.

Para probar los endpoints protegidos, pulsa *Authorize*. Hay dos opciones:

| Esquema | Cómo funciona |
|---|---|
| `oauth2` (recomendado) | Con el `client_id` `web-client` ya relleno y sin secreto, *Authorize* abre en una ventana emergente el formulario de login de auth-service (por ejemplo `admin` / `Showcase-Dev-2026!`). Al entrar, Swagger recibe el token y lo envía en todas las peticiones. Es el mismo flujo authorization code + PKCE que una aplicación de navegador: la contraseña solo la ve auth-service, y siguen valiendo el bloqueo por intentos, la auditoría y el rate limiting del login |
| `bearer-jwt` | Pega un token obtenido por otra vía, por ejemplo con la función `login` de [docs/curl-examples.md](docs/curl-examples.md) |

El Swagger UI del gateway vuelve de la ventana de login a `http://localhost:8080/swagger-ui/oauth2-redirect.html`, que está entre las redirect URIs de `web-client` (`WEB_CLIENT_REDIRECT_URIS`; auth-service las sincroniza al arrancar). Se comparan exactamente, así que abre Swagger en `http://localhost:8080` y no en `127.0.0.1`. En un servicio arrancado sin el gateway, indica dónde está auth-service con `showcase.openapi.auth-server-url` y añade su redirect URI.

## Documentación

- [Especificación del proyecto](docs/prompt-microservicios.md)
- [Diagramas UML](docs/diagramas-uml.html)
- [Ejemplos con curl](docs/curl-examples.md): token, paginación, cada error en español e inglés, logout, Zipkin y RabbitMQ

## Licencia

[MIT](LICENSE)
