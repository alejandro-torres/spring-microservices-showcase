# Rol
Actúa como arquitecto de software senior experto en Java, Spring Boot, Spring Security, OAuth2, microservicios y arquitectura hexagonal (Ports & Adapters). Genera código listo para producción, limpio, siguiendo SOLID y Clean Code.

# Objetivo
Crear un sistema de microservicios para [DOMINIO: p. ej. gestión de una tienda online] con al menos 4 microservicios de negocio y 2 de infraestructura:

Microservicios de negocio:
1. `auth-service`: gestión de usuarios, roles y permisos, y servidor de autorización OAuth2 (emite los tokens JWT).
2. `product-service`: gestión de productos (CRUD + búsqueda paginada).
3. `customer-service`: gestión de clientes (CRUD + búsqueda paginada).
4. `order-service`: gestión de pedidos; consulta `product-service` y `customer-service` (Feign) y coordina la reserva de stock mediante eventos (RabbitMQ).

Infraestructura:
- `discovery-service`: Eureka Server.
- `api-gateway`: Spring Cloud Gateway (punto de entrada único: valida los tokens, enruta y aplica rate limiting).
- Zipkin: servidor de trazabilidad distribuida (contenedor oficial `openzipkin/zipkin`).
- Redis: almacén para el rate limiting del gateway y para la lista de tokens revocados.
- RabbitMQ: broker de mensajería para la comunicación asíncrona entre microservicios (imagen `rabbitmq:3-management`, con la consola web en el puerto 15672).
- Prometheus: recogida de métricas de todos los servicios.
- Loki + Promtail (o Grafana Alloy): centralización de los logs de todos los contenedores.
- Grafana: dashboards y alertas sobre métricas, logs y trazas.

# Stack técnico
- Java 21, Spring Boot 4.1 (Spring Framework 7, Jakarta EE 11, Jackson 3), Spring Cloud 2025.1, Maven multimódulo con un `pom.xml` padre y Maven Wrapper
- Spring Data JPA + Hibernate
- PostgreSQL 16 (una base de datos por microservicio: patrón Database per Service)
- Flyway para las migraciones
- MapStruct para mapear entre capas
- Lombok (solo en la infraestructura, nunca en el dominio)
- Bean Validation (Jakarta Validation)
- Spring Security 7 con su servidor de autorización OAuth2 integrado (en `auth-service`) + OAuth2 Resource Server (en el resto)
- Springdoc OpenAPI (Swagger UI con soporte de Bearer/OAuth2)
- Spring Cloud: Eureka, Gateway (con `RequestRateLimiter` sobre Redis), OpenFeign y Resilience4j (circuit breaker)
- Micrometer Tracing (bridge Brave) + reporter de Zipkin para la trazabilidad distribuida
- Spring Data Redis (reactive en el gateway) y Redisson para los locks distribuidos
- ShedLock (proveedor JDBC sobre PostgreSQL) para las tareas programadas con varias réplicas
- Micrometer + `micrometer-registry-prometheus` para las métricas
- `MessageSource` de Spring para la internacionalización (i18n) de los mensajes
- Spring AMQP (`spring-boot-starter-amqp`) con RabbitMQ
- Docker y docker-compose
- JUnit 5, Mockito, AssertJ, Awaitility, JaCoCo, Testcontainers (PostgreSQL, Redis y RabbitMQ) y spring-security-test

# Arquitectura hexagonal (obligatoria en cada microservicio)
Estructura de paquetes:

com.[empresa].[servicio]
├── domain
│   ├── model                 → entidades de dominio puras (sin anotaciones JPA ni de Spring)
│   ├── exception             → excepciones de negocio personalizadas (ver la sección de excepciones)
│   ├── event                 → eventos de dominio (records inmutables: OrderCreatedEvent...)
│   ├── repository            → INTERFACES de repositorio (puertos de salida)
│   │   └── ProductRepository.java
│   └── messaging             → INTERFACES de publicación de eventos (puertos de salida)
│       └── ProductEventPublisher.java
├── application
│   ├── service               → INTERFACES de servicio (puertos de entrada / casos de uso)
│   │   └── ProductService.java
│   ├── service.impl          → IMPLEMENTACIONES de los servicios
│   │   └── ProductServiceImpl.java
│   ├── client                → INTERFACES hacia otros microservicios (p. ej. ProductClient, CustomerClient en order-service)
│   ├── lock                  → INTERFAZ DistributedLockManager
│   └── metrics               → INTERFACES de métricas de negocio
└── infrastructure
    ├── rest
    │   ├── controller        → controllers REST
    │   ├── dto
    │   │   ├── request       → DTOs de ENTRADA (lo que recibe la API): CreateProductRequestDTO, UpdateProductRequestDTO...
    │   │   └── response      → DTOs de SALIDA (lo que devuelve la API): ProductResponseDTO, CategoryResponseDTO...
    │   └── mapper            → mappers DTO ↔ dominio (MapStruct)
    ├── persistence
    │   ├── entity            → entidades JPA (*Entity)
    │   ├── repository        → interfaces Spring Data (extienden JpaRepository y JpaSpecificationExecutor)
    │   │   └── ProductJpaRepository.java
    │   ├── repository.impl   → IMPLEMENTACIONES de las interfaces de domain.repository (adaptadores)
    │   │   └── ProductRepositoryImpl.java
    │   ├── specification     → *FilterMapping (nombre público del filtro → path JPA); el GenericSpecificationBuilder está en common-lib
    │   └── mapper            → mappers Entity ↔ dominio (MapStruct)
    ├── messaging
    │   ├── config            → declaración de exchanges, colas, bindings, DLQ y converter JSON
    │   ├── publisher.impl    → IMPLEMENTACIONES de las interfaces de domain.messaging (guardan el evento en el outbox; el envío real con RabbitTemplate lo hace el publicador del outbox)
    │   │   └── ProductEventPublisherImpl.java
    │   ├── listener          → consumidores @RabbitListener (adaptadores de entrada: llaman a las interfaces de servicio)
    │   ├── outbox            → tabla outbox, entidad, repositorio y publicador programado
    │   └── mapper            → mappers entre los eventos de dominio y los DTOs de mensajería (`*MessageDTO`)
    ├── lock.impl             → implementación de los locks distribuidos con Redisson
    ├── metrics.impl          → implementación de las métricas de negocio con MeterRegistry
    ├── client
    │   ├── feign             → interfaces @FeignClient (ProductFeignClient...) + ErrorDecoder + interceptor del token
    │   ├── impl              → IMPLEMENTACIONES de las interfaces de application.client (ProductClientImpl usa ProductFeignClient y convierte DTO ↔ dominio)
    │   └── dto
    │       ├── request       → DTOs que se ENVÍAN al otro servicio
    │       └── response      → DTOs que se RECIBEN del otro servicio (ProductClientResponseDTO...)
    ├── security              → configuración de seguridad, entry point y access denied handler
    ├── exception             → GlobalExceptionHandler y el mapeo de excepciones a códigos HTTP
    └── config                → beans y configuración de Spring

Reglas:
- Cada interfaz de servicio (`application.service`) tiene su implementación en `application.service.impl` con el sufijo `Impl`, anotada con `@Service`.
- Cada interfaz de repositorio del dominio (`domain.repository`) tiene su implementación en `infrastructure.persistence.repository.impl` con el sufijo `Impl`.
- `ProductRepositoryImpl` usa internamente `ProductJpaRepository`, convierte `PageQuery` → `Pageable` y `Page<ProductEntity>` → `PageResult<Product>`.
- `ProductServiceImpl` depende solo de la interfaz `ProductRepository`, nunca de la implementación ni de Spring Data.
- Los controllers dependen solo de la interfaz `ProductService`.
- Cada interfaz de publicación (`domain.messaging`) tiene su implementación en `infrastructure.messaging.publisher.impl` con el sufijo `Impl`; los `*ServiceImpl` solo conocen la interfaz, nunca RabbitMQ.
- Cada interfaz de `application.client` tiene su implementación en `infrastructure.client.impl` con el sufijo `Impl`, que usa la interfaz `@FeignClient` correspondiente; los `*ServiceImpl` nunca usan Feign directamente.
- Los listeners de RabbitMQ son adaptadores de entrada, igual que los controllers: convierten el mensaje y llaman a la interfaz de servicio, sin lógica de negocio.
- El dominio NO depende de Spring, JPA ni de ningún framework; las dependencias siempre apuntan hacia el dominio.
- Inyección de dependencias por constructor.
- El código común (ErrorResponseDTO, PageResponseDTO, PageResult, PageQuery, la excepción base) puede ir en un módulo `common-lib` compartido, sin dependencias de Spring en su parte de dominio.

# Paginación con `Page`
- Los repositorios Spring Data devuelven `org.springframework.data.domain.Page<T>` recibiendo un `Pageable`.
- Para mantener el dominio libre de Spring, define `PageResult<T>` (content, page, size, totalElements, totalPages, first, last) y `PageQuery` (page, size, sort, direction).
- El adaptador de persistencia convierte `PageQuery` → `PageRequest` y `Page<Entity>` → `PageResult<DomainModel>` usando `page.map(...)`.
- El controller recibe `page`, `size`, `sort` y `direction` como `@RequestParam` (valores por defecto: page=0, size=10, sort=id, direction=ASC), valida que `size` esté entre 1 y 100 y devuelve un `PageResponseDTO<T>`.
- Valida el campo `sort` contra una lista blanca de campos permitidos; un campo inválido devuelve 400, no un error de Hibernate.
- TODOS los endpoints que listan datos paginados admiten filtros dinámicos (ver la sección siguiente).
- Incluye un ejemplo con `@Query` JPQL que devuelva `Page<T>` con su `countQuery`.

Ejemplo de respuesta:
{
  "content": [ ... ],
  "page": 0,
  "size": 10,
  "totalElements": 57,
  "totalPages": 6,
  "first": true,
  "last": false
}

# Filtros dinámicos en los listados paginados (requisito clave)
Todos los `GET` paginados aceptan cualquier combinación de filtros sobre los campos permitidos de cada recurso, sin crear un método de repositorio por cada combinación. Se implementa UNA sola vez de forma genérica en `common-lib` y cada recurso solo declara qué campos se pueden filtrar.

## Sintaxis en la URL
Parámetro `filter` repetible con el formato `campo:operador:valor`:

GET /api/v1/products?filter=name:like:portatil&filter=price:gte:100&filter=price:lte:500&filter=categoryId:in:1,2,3&page=0&size=10&sort=price&direction=DESC

- Se usa `:` como separador, partiendo solo en los dos primeros `:` (así el valor puede contener `:`, p. ej. fechas con hora `createdAt:gte:2026-10-01T00:00:00Z`).
- No se usan corchetes (`price[gte]=100`) porque Tomcat los rechaza sin codificar y obligaría a relajar su configuración.
- Varios filtros se combinan con AND. Para OR sobre un mismo campo se usa el operador `in`.
- Máximo 10 filtros por petición (configurable); si se supera, 400.
- Sin filtros, el endpoint devuelve el listado paginado normal.

## Operadores soportados
| Operador | Significado | Ejemplo | Tipos admitidos |
|---|---|---|---|
| `eq` | igual | `status:eq:CONFIRMED` | todos |
| `ne` | distinto | `status:ne:CANCELLED` | todos |
| `gt` / `gte` | mayor / mayor o igual | `price:gte:100` | números y fechas |
| `lt` / `lte` | menor / menor o igual | `createdAt:lt:2026-10-01T00:00:00Z` | números y fechas |
| `between` | rango inclusivo (2 valores) | `price:between:100,500` | números y fechas |
| `like` | contiene, sin distinguir mayúsculas ni tildes | `name:like:portatil` | texto |
| `startsWith` | empieza por | `email:startsWith:ana` | texto |
| `in` | uno de varios valores | `categoryId:in:1,2,3` | todos |
| `isNull` / `isNotNull` | vacío / no vacío (sin valor) | `phone:isNull` | todos |

## Diseño respetando la arquitectura hexagonal
- **Dominio (`common-lib`, sin Spring):**
  - `FilterCriterion` (record: `field`, `FilterOperator operator`, `List<Object> values`) con los valores ya convertidos a su tipo.
  - `FilterOperator` (enum con los operadores de la tabla).
  - Las interfaces de servicio y de repositorio reciben los filtros junto a la paginación: `findAll(List<FilterCriterion> filters, PageQuery pageQuery)`.
- **Adaptador de entrada (REST):**
  - Cada recurso declara sus campos filtrables en una clase `*FilterFields` (p. ej. `ProductFilterFields`) con: nombre público del campo, tipo Java (`String`, `BigDecimal`, `Long`, `Instant`, `LocalDate`, `Boolean` o un enum) y operadores permitidos para ese campo. Es una lista blanca: lo que no está declarado no se puede filtrar (p. ej. nunca `passwordHash`).
  - Un `FilterCriteriaParser` genérico (en `common-lib`) parsea los `filter`, valida campo, operador y número de valores, convierte el texto al tipo correcto y devuelve la lista de `FilterCriterion`.
  - El controller recibe `@RequestParam(required = false) List<String> filter` y lo pasa al parser.
- **Adaptador de salida (persistencia):**
  - Cada recurso tiene una clase `*FilterMapping` (p. ej. `ProductFilterMapping`) que traduce el nombre público al path JPA, incluidos los joins: `categoryId` → `category.id`, `categoryName` → `category.name`. Así la API no expone la estructura interna de las entidades.
  - Un `GenericSpecificationBuilder<T>` (en `common-lib`, parte de infraestructura) convierte la lista de `FilterCriterion` en una `Specification<T>` combinando los predicados con AND; siempre añade `active = true` salvo que un ADMIN pida `includeInactive=true`.
  - `ProductRepositoryImpl` llama a `jpaRepository.findAll(specification, pageable)` (de `JpaSpecificationExecutor`) y convierte el `Page` en `PageResult` como siempre.
- `like` y `startsWith` usan `lower()` y `unaccent()` de PostgreSQL (extensión creada por Flyway) y escapan `%` y `_` del valor del usuario.
- Los joins a colecciones usan `query.distinct(true)` y se comprueba que la consulta de `count` sigue siendo correcta.

## Campos filtrables por recurso
| Recurso | Campos y operadores principales |
|---|---|
| `products` | `name` (like, startsWith, eq), `price` (eq, gt, gte, lt, lte, between), `stock` (eq, gt, gte, lt, lte), `categoryId` (eq, in), `categoryName` (like), `createdAt` (gte, lte, between) |
| `categories` | `name` (like, eq), `createdAt` (gte, lte, between) |
| `customers` | `firstName`, `lastName`, `email` (like, startsWith, eq), `city` (eq, like; join con direcciones), `active` (eq, solo ADMIN), `createdAt` (gte, lte, between) |
| `orders` | `status` (eq, ne, in), `customerId` (eq, in), `total` (gte, lte, between), `createdAt` (gte, lte, between), `productId` (eq; join con las líneas) |
| `users` | `username`, `email` (like, startsWith, eq), `enabled` (eq), `role` (eq, in; join con roles), `locked` (eq) |
| `roles`, `permissions` | `name` (like, eq) |
| `security-audit` | `eventType` (eq, in), `username` (eq, like), `ip` (eq), `occurredAt` (gte, lte, between) |

## Errores
Todos devuelven 400 con el formato común y el mensaje traducido:
- `INVALID_FILTER_FORMAT`: el filtro no sigue `campo:operador:valor`.
- `FILTER_FIELD_NOT_ALLOWED`: el campo no está en la lista blanca del recurso.
- `FILTER_OPERATOR_NOT_ALLOWED`: el operador no se permite para ese campo (p. ej. `like` sobre `price`).
- `INVALID_FILTER_VALUE`: el valor no se puede convertir al tipo (p. ej. `price:gte:abc`) o el número de valores no es el esperado (`between` necesita 2).
- `TOO_MANY_FILTERS`: se supera el máximo de filtros.

## Documentación y pruebas
- Swagger documenta el parámetro `filter` en cada endpoint con la lista de campos y operadores permitidos del recurso y ejemplos.
- Tests unitarios (JUnit + `@ParameterizedTest`) de `FilterCriteriaParser`: cada operador, conversión de cada tipo, valores con `:`, y todos los errores anteriores.
- Tests unitarios de `GenericSpecificationBuilder` con mocks de `Root`, `CriteriaQuery` y `CriteriaBuilder`, o mejor, tests `@DataJpaTest` con Testcontainers que comprueben los resultados reales de cada operador sobre los datos de ejemplo.
- Test de que un campo no declarado (p. ej. `passwordHash` en usuarios) devuelve 400 y nunca llega a la consulta.
- Test combinado: filtros + paginación + ordenación devuelven el `totalElements` correcto.

# Gestión de excepciones (requisito clave)
Objetivo: la API NUNCA debe exponer errores internos de Java (stack traces, nombres de clase, mensajes de Hibernate/SQL, `NullPointerException`, etc.). Todas las respuestas de error usan únicamente códigos HTTP estándar y un formato JSON común.

## Excepciones personalizadas
- En `domain.exception`, crea una excepción base abstracta `DomainException extends RuntimeException` con un `ErrorCode` y sus parámetros (sin dependencias HTTP). `ErrorCode` es una interfaz de `common-lib` (`code()`, `messageKey()`) implementada por un enum común (`CommonErrorCode`: VALIDATION_ERROR, RESOURCE_NOT_FOUND, CONCURRENT_MODIFICATION, INTERNAL_ERROR...) y por un enum propio en cada servicio (`ProductErrorCode`, `OrderErrorCode`, `AuthErrorCode`...).
- Excepciones concretas como mínimo:
  - `ResourceNotFoundException` → 404
  - `DuplicateResourceException` → 409
  - `BusinessRuleException` (regla de negocio incumplida, p. ej. stock insuficiente) → 422
  - `InvalidRequestException` → 400
  - `UnauthorizedException` (credenciales inválidas, token expirado) → 401
  - `ForbiddenException` (sin permisos) → 403
  - `ExternalServiceException` (fallo de otro microservicio) → 502 / 503
- El mapeo `ErrorCode` → `HttpStatus` se hace en `infrastructure.exception`, no en el dominio.
- Debe ser fácil añadir excepciones nuevas: crear la clase + añadir su `ErrorCode` + su mapeo, sin tocar el handler.

## GlobalExceptionHandler
Un `@RestControllerAdvice` en cada servicio (o compartido en `common-lib`) que capture como mínimo:

| Excepción | Código HTTP |
|---|---|
| `DomainException` y sus subclases | según el mapeo (400/401/403/404/409/422/502/503) |
| `MethodArgumentNotValidException`, `ConstraintViolationException`, `BindException` | 400 (con la lista de campos inválidos) |
| `HttpMessageNotReadableException` (JSON mal formado) | 400 |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `MissingPathVariableException` | 400 |
| `PropertyReferenceException` (campo de ordenación inválido en la paginación) | 400 |
| `AuthenticationException`, `InvalidBearerTokenException`, `JwtException` | 401 |
| `AccessDeniedException` / `AuthorizationDeniedException` | 403 |
| `NoResourceFoundException` / `NoHandlerFoundException` | 404 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `HttpMediaTypeNotAcceptableException` | 406 |
| `DataIntegrityViolationException` (clave duplicada, FK) | 409 |
| `ObjectOptimisticLockingFailureException` | 409 |
| `HttpMediaTypeNotSupportedException` | 415 |
| `AccountLockedException` (cuenta bloqueada por intentos fallidos) | 423 |
| Límite de peticiones superado (rate limiting en el gateway) | 429 (con la cabecera `Retry-After`) |
| `FeignException` (reutilizando el código del servicio remoto cuando sea 4xx) | 4xx / 502 |
| `CallNotPermittedException` (circuit breaker abierto), timeouts | 503 |
| `Exception` (genérica, último recurso) | 500 con el mensaje genérico "Error interno del servidor" |

Reglas:
- Todas las excepciones se registran en el log con el stack trace completo y un `traceId`; al cliente solo le llega un mensaje genérico y seguro.
- Los errores 401/403 que ocurren en los filtros de Spring Security (fuera de los controllers) se gestionan con un `AuthenticationEntryPoint` y un `AccessDeniedHandler` personalizados que devuelven el mismo formato JSON.
- El `api-gateway` (WebFlux) tiene su propio `ErrorWebExceptionHandler` con el mismo formato (p. ej. 503 si un servicio no está disponible, 404 si no hay ruta).
- Los clientes Feign usan un `ErrorDecoder` que traduce las respuestas de error remotas a excepciones del dominio.
- Configura `server.error.include-stacktrace=never`, `include-message=never` e `include-exception=false`.
- Nota: los códigos 3xx son de redirección, no de error; se usan solo cuando corresponda (p. ej. las redirecciones del flujo OAuth2 o 304 Not Modified). Los errores devuelven siempre 4xx (fallo del cliente) o 5xx (fallo del servidor).

## Formato de error común (basado en RFC 7807 / `ProblemDetail`)
{
  "timestamp": "2026-10-13T10:15:30Z",
  "status": 404,
  "error": "Not Found",
  "code": "PRODUCT_NOT_FOUND",
  "message": "No existe el producto con id 25",
  "path": "/api/v1/products/25",
  "traceId": "a1b2c3d4e5",
  "errors": [
    { "field": "price", "message": "debe ser mayor que 0" }
  ]
}
(`errors` solo aparece en los errores de validación. El `traceId` es el mismo que aparece en Zipkin, para poder localizar la petición fallida.)

## Mensajes de error en varios idiomas (i18n)
- Idiomas soportados: español (por defecto) e inglés; debe ser fácil añadir más.
- El idioma se obtiene de la cabecera `Accept-Language` con un `AcceptHeaderLocaleResolver`; si no viene o no está soportado, se usa el español.
- Los textos están en `messages_es.properties` y `messages_en.properties`, con claves basadas en el `ErrorCode` (p. ej. `error.PRODUCT_NOT_FOUND=No existe el producto con id {0}`).
- El dominio NO contiene textos traducidos: las excepciones llevan el `ErrorCode` y sus parámetros, y el `GlobalExceptionHandler` resuelve el mensaje con `MessageSource` según el idioma.
- Los mensajes de Bean Validation también se traducen (`ValidationMessages_es.properties` / `ValidationMessages_en.properties`, o un `LocalValidatorFactoryBean` conectado al `MessageSource`).
- El campo `code` del JSON de error NO se traduce (sirve al frontend para identificar el error); solo se traducen `message` y los mensajes de `errors`.
- El gateway y los handlers de seguridad (401/403/429) también devuelven el mensaje en el idioma pedido.

# auth-service: usuarios, roles y OAuth2
- Implementado con el servidor de autorización OAuth2 de Spring Security 7 (antes proyecto Spring Authorization Server), emite tokens JWT firmados con RSA y expone el JWKS (`/oauth2/jwks`).
- Flujos soportados: `authorization_code` con PKCE (frontends), `client_credentials` (comunicación entre microservicios) y `refresh_token`.
- Modelo: `User` (username, email, password con BCrypt, enabled, accountNonLocked), `Role` (ADMIN, USER, ...) y `Permission` (p. ej. `product:read`, `product:write`), con relaciones muchos a muchos.
- Endpoints de gestión (protegidos, solo ADMIN): CRUD de usuarios paginado con `Page`, CRUD de roles, asignar y quitar roles a un usuario, activar y desactivar usuarios.
- Endpoints públicos: registro de usuario y cambio de contraseña (usuario autenticado).
- Los roles y permisos se incluyen en el JWT como claims personalizados (`OAuth2TokenCustomizer`).
- Clientes OAuth2 registrados en base de datos (`JdbcRegisteredClientRepository`), con datos de ejemplo en Flyway: un usuario ADMIN, un usuario USER y un cliente para cada microservicio. Credenciales de ejemplo solo en las migraciones del perfil `dev`.
- Sigue también la arquitectura hexagonal y las reglas de excepciones (p. ej. `UserNotFoundException`, `EmailAlreadyExistsException`, `InvalidCredentialsException`, `AccountLockedException`).

## Seguridad extra en auth-service
- **Bloqueo por intentos fallidos:** tras 5 intentos fallidos seguidos (configurable), la cuenta se bloquea 15 minutos (configurable) y devuelve 423 con el código `ACCOUNT_LOCKED`. El contador se reinicia tras un login correcto. Un ADMIN puede desbloquear la cuenta manualmente.
- **Auditoría de seguridad:** tabla `security_audit_log` con los eventos LOGIN_SUCCESS, LOGIN_FAILURE, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED, PASSWORD_CHANGED, ROLE_ASSIGNED, ROLE_REMOVED, TOKEN_REVOKED y USER_DISABLED, guardando el usuario, la IP, el user-agent, la fecha y el `traceId`. Se registra escuchando los eventos de Spring Security (`AuthenticationSuccessEvent`, `AbstractAuthenticationFailureEvent`) y los eventos propios del dominio.
- Endpoint paginado con `Page` para consultar la auditoría (solo ADMIN), con filtros por usuario, tipo de evento y rango de fechas.
- **Revocación de tokens:** endpoint de logout y endpoint de revocación (`/oauth2/revoke`). Los tokens revocados se guardan en Redis (clave = `jti`, con TTL igual al tiempo restante del token), y el gateway y los resource servers los rechazan con 401.
- Al cambiar la contraseña, desactivar un usuario o quitarle un rol, se revocan todos sus tokens activos.
- **Política de contraseñas:** mínimo 8 caracteres, mayúsculas, minúsculas, números y símbolos, validada con una anotación personalizada (`@StrongPassword`), y sin poder repetir las últimas 3 contraseñas.
- Tokens de acceso de corta duración (15 min) y refresh tokens con rotación (cada uso genera uno nuevo e invalida el anterior).
- Nunca se registran en el log contraseñas, tokens ni secretos.

# Seguridad en el resto de servicios
- `product-service`, `customer-service` y `order-service` son OAuth2 Resource Servers que validan el JWT usando el JWKS de `auth-service`.
- Convierte los claims de roles y permisos a `GrantedAuthority` con un `JwtAuthenticationConverter`.
- Autorización con `@PreAuthorize` (p. ej. `hasAuthority('product:write')` para crear y `hasRole('ADMIN')` para borrar).
- `order-service` llama a los otros servicios propagando el token del usuario (interceptor de Feign) o con `client_credentials`.
- El `api-gateway` valida el token antes de enrutar (TokenRelay), comprueba que no esté revocado y deja públicas solo las rutas de login, registro y Swagger.

# API Gateway
- Rutas hacia los 4 microservicios mediante Eureka (`lb://product-service`, etc.) con el prefijo `/api/v1/...`.
- **Rate limiting** con `RequestRateLimiter` + Redis (token bucket): límite por usuario autenticado (claim `sub` del JWT) y por IP para las rutas públicas. Valores configurables por ruta (p. ej. 20 peticiones/segundo con ráfagas de 40; login más restrictivo, 5/minuto). Al superarlo devuelve 429 con `Retry-After` y el formato de error común.
- Circuit breaker de Resilience4j por ruta con fallback que devuelve 503 en el formato común.
- Filtro global que genera o propaga el `traceId` y lo devuelve en la cabecera de respuesta `X-Trace-Id`.
- Configuración CORS centralizada.
- Swagger UI agregado con la documentación de todos los servicios.

# API CRUD (obligatoria en todos los servicios)
Cada recurso expone un CRUD completo con estas operaciones y códigos de respuesta:

| Operación | Método y ruta | Respuesta correcta | Errores típicos |
|---|---|---|---|
| Crear | `POST /api/v1/{recurso}` | 201 Created + cabecera `Location` + el recurso creado | 400, 401, 403, 409, 422 |
| Obtener por id | `GET /api/v1/{recurso}/{id}` | 200 OK | 401, 403, 404 |
| Listar paginado con filtros | `GET /api/v1/{recurso}?filter=campo:operador:valor&page=0&size=10&sort=id&direction=ASC` | 200 OK con `PageResponseDTO` | 400, 401, 403 |
| Actualizar | `PUT /api/v1/{recurso}/{id}` (incluye el campo `version` para el bloqueo optimista) | 200 OK + el recurso actualizado | 400, 401, 403, 404, 409, 422 |
| Borrar | `DELETE /api/v1/{recurso}/{id}` (borrado lógico: `active = false`) | 204 No Content | 401, 403, 404, 409 |

Recursos por servicio (cada uno con su CRUD completo y todas sus capas: controller → interfaz de servicio → `*ServiceImpl` → interfaz de repositorio → `*RepositoryImpl` → `*JpaRepository`):

| Servicio | Recursos | Operaciones adicionales |
|---|---|---|
| `auth-service` | `users`, `roles`, `permissions` | `POST /users/{id}/roles/{roleId}` y `DELETE /users/{id}/roles/{roleId}` (asignar y quitar roles), `PUT /users/{id}/unlock`, `PUT /users/me/password`, `POST /auth/register`, `GET /security-audit` (paginado) |
| `product-service` | `products`, `categories` | `PUT /products/{id}/stock` (ajuste manual de stock, solo ADMIN) |
| `customer-service` | `customers`, `addresses` (anidado: `/customers/{id}/addresses`) | `GET /customers/by-user/{userId}` |
| `order-service` | `orders` (con sus líneas `order-items`) | `PUT /orders/{id}/cancel`, `GET /orders/by-customer/{customerId}` (paginado) |

Reglas:
- DTOs separados para cada operación: `Create*RequestDTO`, `Update*RequestDTO` y `*ResponseDTO`; nunca se exponen las entidades JPA ni los modelos de dominio.
- **Convención de nombres: TODOS los DTOs terminan en `DTO`** para identificarlos de un vistazo, tanto los de la API REST (`CreateProductRequestDTO`, `UpdateProductRequestDTO`, `ProductResponseDTO`, `PageResponseDTO<T>`, `ErrorResponseDTO`, `FieldErrorDTO`) como los de mensajería (`EventEnvelopeDTO<T>`, `OrderCreatedMessageDTO`...) y los de los clientes Feign (`ProductClientResponseDTO`...). Ninguna otra clase (entidades, modelos de dominio, eventos de dominio) lleva ese sufijo.
- Los DTOs se implementan como `record` de Java.
- **Los DTOs se separan en dos carpetas:** `dto/request` para los de entrada (los que recibe el endpoint, con el sufijo `RequestDTO`, y con las anotaciones de Bean Validation) y `dto/response` para los de salida (los que devuelve el endpoint, con el sufijo `ResponseDTO`, sin validaciones). Un mismo DTO nunca se usa a la vez como entrada y como salida.
- En `common-lib`, `PageResponseDTO<T>`, `ErrorResponseDTO` y `FieldErrorDTO` van en `infrastructure/rest/dto/response`.
- Validaciones con Bean Validation en los requests (`@NotBlank`, `@Positive`, `@Email`, `@Size`...) y grupos de validación si crear y actualizar tienen reglas distintas.
- Un `DELETE` de un recurso ya borrado devuelve 404; borrar un recurso con dependencias activas (p. ej. una categoría con productos, un cliente con pedidos pendientes) devuelve 409.
- Los `GET` no devuelven los registros con `active = false`, salvo que un ADMIN pase `includeInactive=true`.
- Swagger documenta cada endpoint con sus posibles respuestas de error (`@ApiResponse`).

# Transacciones con `@Transactional` (requisito clave)
Usa `@Transactional` de Spring (`org.springframework.transaction.annotation.Transactional`) con ejemplos reales en TODOS los servicios, explicando con un comentario breve por qué se usa cada configuración.

## Dónde se pone
- En los métodos públicos de los `*ServiceImpl` (capa de aplicación), que es donde empieza y termina cada caso de uso. Nunca en los controllers, ni en el dominio, ni en las interfaces.
- Los `*RepositoryImpl` no abren transacciones propias: participan en la del servicio (propagación `REQUIRED` por defecto).
- Los listeners de RabbitMQ delegan en un método `@Transactional` del servicio, no son transaccionales ellos mismos.

## Configuraciones que deben aparecer
| Configuración | Dónde usarla (ejemplos) |
|---|---|
| `@Transactional(readOnly = true)` | Todos los `GET` (por id y paginados). A nivel de clase en los `*ServiceImpl`, sobrescribiendo con `@Transactional` en los métodos de escritura |
| `@Transactional` (por defecto, `REQUIRED`) | `POST`, `PUT` y `DELETE` de todos los recursos |
| `rollbackFor = Exception.class` | En los métodos que puedan lanzar excepciones *checked*, explicando que por defecto Spring solo hace rollback con `RuntimeException` y `Error` |
| `noRollbackFor = ...` | Un caso justificado, p. ej. en el login fallido: se lanza `InvalidCredentialsException` pero el contador de intentos fallidos sí debe guardarse |
| `propagation = Propagation.REQUIRES_NEW` | Guardar la auditoría de seguridad (`security_audit_log`): el registro se guarda aunque la operación principal haga rollback |
| `propagation = Propagation.MANDATORY` | Métodos internos que solo tienen sentido dentro de una transacción ya abierta (p. ej. guardar en el outbox) |
| `isolation = Isolation.REPEATABLE_READ` (o `SERIALIZABLE`) | Un ejemplo justificado en la reserva de stock, comparándolo con el bloqueo optimista y el lock distribuido |
| `timeout = 5` | Operaciones que no deben bloquear conexiones mucho tiempo (p. ej. crear un pedido) |
| `@TransactionalEventListener(phase = AFTER_COMMIT)` | Acciones que solo deben ocurrir si la transacción se confirma (p. ej. invalidar una caché o registrar una métrica de negocio) |
| `TransactionTemplate` (programático) | Cuando hace falta controlar exactamente dónde empieza y acaba la transacción: la reserva de stock (lock distribuido → transacción → commit → unlock) |

## Ejemplos obligatorios por servicio
- **auth-service:**
  - Crear un usuario con sus roles y el registro del historial de contraseñas en una sola transacción.
  - Asignar o quitar un rol y revocar sus tokens.
  - Login fallido con `noRollbackFor` y auditoría con `REQUIRES_NEW`.
- **product-service:**
  - Crear un producto validando que su categoría existe.
  - Actualizar con control de `@Version`.
  - Borrado lógico de una categoría comprobando que no tiene productos.
  - Reserva de stock de varias líneas: todas o ninguna (si falla una, rollback de todas).
- **customer-service:**
  - Crear un cliente con sus direcciones (cascada) en una transacción.
  - Consumir `UserRegisteredEvent` y crear el perfil de forma idempotente (guardar el `processed_event` en la misma transacción).
- **order-service:**
  - Crear un pedido con sus líneas y su evento en el outbox en la MISMA transacción (si falla algo, no queda ni el pedido ni el evento).
  - Cancelar un pedido cambiando el estado y guardando el `OrderCancelledEvent` en el outbox.
  - Actualizar el estado al recibir la respuesta de la saga.

## Errores típicos que debes evitar (y explicar en comentarios)
- **Autoinvocación:** llamar a un método `@Transactional` desde otro método de la misma clase no pasa por el proxy y la anotación se ignora. Muéstralo y resuélvelo moviendo el método a otro bean.
- `@Transactional` en métodos `private` o `final`: no funciona.
- Capturar la excepción dentro del método transaccional y no relanzarla: la transacción hace commit sin que nadie se entere.
- Llamadas remotas (Feign) o envíos a RabbitMQ dentro de una transacción larga: se hacen antes de la transacción o mediante el outbox, nunca bloqueando la conexión a la base de datos.
- `LazyInitializationException`: con `open-in-view=false`, todo lo que se mapea a DTO se carga dentro de la transacción (con `@EntityGraph` / `JOIN FETCH`).

## Tests de las transacciones
- Tests de integración (Testcontainers) que demuestren el rollback: si falla la segunda línea de un pedido, no queda guardado ni el pedido ni el evento del outbox.
- Test de que la auditoría con `REQUIRES_NEW` se guarda aunque la operación principal falle.
- Test de que el contador de intentos fallidos se guarda pese a la excepción (`noRollbackFor`).
- Test que demuestre el problema de la autoinvocación y su solución.

# Requisitos funcionales comunes
- Auditoría: `createdAt`, `updatedAt`, `createdBy` y `updatedBy` (usuario obtenido del JWT con `AuditorAware`).
- Bloqueo optimista con `@Version`.
- `order-service` valida que el cliente y los productos existan, con un fallback de Resilience4j.

# Requisitos no funcionales
- Perfiles `dev`, `test` y `prod`; credenciales y secretos en variables de entorno.
- `spring.jpa.hibernate.ddl-auto=validate` y `spring.jpa.open-in-view=false`.
- Evitar el problema N+1 (`@EntityGraph` / `JOIN FETCH`, teniendo cuidado al paginar colecciones).
- Actuator con health checks.

# Escalado horizontal (varias réplicas por servicio)
El sistema debe funcionar correctamente con varias instancias de cada microservicio a la vez.

## Réplicas y balanceo
- Los servicios son *stateless*: nada de estado en memoria entre peticiones (sesiones, cachés locales que deban ser coherentes, contadores); lo compartido va en PostgreSQL o Redis.
- En docker-compose se levantan al menos 3 réplicas de `product-service` y 2 de `order-service` (`deploy.replicas` o `docker compose up --scale`), sin puertos fijos publicados (solo el gateway expone puerto).
- Cada réplica se registra en Eureka con un `instance-id` único (`${spring.application.name}:${random.uuid}`), y el gateway y Feign balancean con Spring Cloud LoadBalancer.
- Cada respuesta incluye la cabecera `X-Instance-Id` con la réplica que la atendió, para poder demostrar el reparto de carga.
- Los consumidores de RabbitMQ de todas las réplicas comparten la misma cola (*competing consumers*): cada mensaje lo procesa una sola réplica. Configura `prefetch` y `concurrency` de los listeners.
- **Graceful shutdown** (`server.shutdown=graceful` y `spring.lifecycle.timeout-per-shutdown-phase=30s`): una réplica que se apaga termina las peticiones y mensajes en curso y se da de baja en Eureka antes de parar.
- Ajusta el registro y la caché de Eureka y del LoadBalancer para que una réplica caída deje de recibir tráfico en pocos segundos; combínalo con el retry del gateway para reintentar en otra réplica.

## Tareas programadas con ShedLock
- Todas las tareas `@Scheduled` (publicador del outbox, limpieza de `processed_event`, desbloqueo automático de cuentas, limpieza de tokens caducados) se anotan con `@SchedulerLock`, para que solo una réplica las ejecute en cada momento.
- Tabla `shedlock` creada por Flyway en cada base de datos que lo necesite.
- `lockAtMostFor` (por si la réplica muere con el lock cogido) y `lockAtLeastFor` configurados en cada tarea; valores en `application.yml`.
- El publicador del outbox además procesa los eventos en lotes con `SELECT ... FOR UPDATE SKIP LOCKED` como segunda protección contra duplicados.

## Locks distribuidos con Redisson
- La reserva y liberación de stock en `product-service` se protege con un lock distribuido por producto (`lock:product:{id}`), para que dos réplicas no modifiquen el mismo producto a la vez.
- Si un pedido tiene varios productos, se usa un `RedissonMultiLock` adquiriendo los locks ordenados por id, para evitar *deadlocks*.
- `tryLock` con tiempo de espera y de expiración (nunca un lock sin expiración); si no se consigue, se lanza una `DomainException` que acaba en reintento del mensaje o en 409/503 según el caso.
- El lock se libera siempre en un `finally`, y se adquiere FUERA de la transacción de base de datos (lock → transacción → commit → unlock), para que otra réplica no lea datos sin confirmar.
- El `@Version` (bloqueo optimista) se mantiene como segunda red de seguridad.
- Para respetar la arquitectura hexagonal: interfaz `DistributedLockManager` en `application.lock` e implementación `RedissonDistributedLockManagerImpl` en `infrastructure.lock.impl`; los `*ServiceImpl` nunca usan Redisson directamente.

# Mensajería asíncrona con RabbitMQ
Feign se mantiene para las consultas síncronas (validar que algo existe); RabbitMQ se usa para todo lo que cambia el estado de otro servicio.

## Flujos de eventos
1. **Alta de usuario → perfil de cliente**
   `auth-service` publica `UserRegisteredEvent` → `customer-service` crea el perfil de cliente asociado al `userId`.
2. **Creación de pedido → reserva de stock (saga por coreografía)**
   - `order-service` crea el pedido en estado `PENDING` y publica `OrderCreatedEvent` (líneas con productId y cantidad).
   - `product-service` intenta reservar el stock de todas las líneas en una única transacción:
     - si hay stock → publica `StockReservedEvent`;
     - si no → publica `StockReservationFailedEvent` con el motivo (sin modificar nada).
   - `order-service` consume la respuesta y pasa el pedido a `CONFIRMED` o `REJECTED`.
3. **Cancelación de pedido → liberación de stock (compensación)**
   Solo se pueden cancelar pedidos `CONFIRMED` (con el stock ya reservado); un pedido `PENDING` tiene la saga en curso y se rechaza la cancelación con 422.
   `order-service` publica `OrderCancelledEvent` → `product-service` devuelve el stock reservado y publica `StockReleasedEvent`.
4. **Eventos de seguridad**
   `auth-service` publica `UserDisabledEvent` y `AccountLockedEvent` → `customer-service` marca el cliente como inactivo al desactivar el usuario.

## Topología
- Un exchange de tipo `topic` por servicio que publica: `auth.events`, `order.events`, `product.events`.
- Routing keys con el formato `<entidad>.<acción>`: `user.registered`, `user.disabled`, `order.created`, `order.cancelled`, `stock.reserved`, `stock.reservation-failed`, `stock.released`.
- Una cola por consumidor y evento, con el nombre `<servicio-consumidor>.<evento>` (p. ej. `product-service.order-created`), duradera.
- Cada cola tiene su dead letter exchange y su DLQ (`<cola>.dlq`).
- Exchanges, colas y bindings declarados por código (`Declarables`) en `infrastructure.messaging.config`; los nombres en `application.yml`, no repartidos por el código.

## Formato de los mensajes
- JSON con `Jackson2JsonMessageConverter` y un sobre común:
  `{ "eventId": "uuid", "eventType": "order.created", "version": 1, "occurredAt": "...", "source": "order-service", "payload": { ... } }`
- Los contratos de los mensajes (DTOs de integración, con el sufijo `MessageDTO`: `OrderCreatedMessageDTO`, `StockReservedMessageDTO`...) van en un módulo compartido `events-contract`, separados de los eventos de dominio de cada servicio, para no acoplar los dominios. El sobre común se llama `EventEnvelopeDTO<T>`.
- Diferencia entre ambos: el **evento de dominio** (`domain.event`, p. ej. `OrderCreatedEvent`) es interno del servicio y expresa algo que ha pasado en el negocio; el **MessageDTO** es el formato JSON público que viaja por RabbitMQ. `infrastructure.messaging.dto` contiene los mappers entre uno y otro.
- Campo `version` para poder evolucionar los contratos sin romper a los consumidores.

## Fiabilidad
- **Patrón Outbox (publicación):** el evento se guarda en una tabla `outbox_event` en la MISMA transacción que el cambio de negocio; un publicador programado (`@Scheduled`) los envía a RabbitMQ y los marca como enviados. Así nunca se guarda un pedido sin su evento ni se publica un evento de un pedido que no se llegó a guardar.
- **Publisher confirms y returns** activados, para saber que RabbitMQ ha recibido el mensaje y que tenía una cola de destino.
- **Consumo idempotente:** cada consumidor guarda los `eventId` procesados en una tabla `processed_event`; si llega un duplicado, se ignora. RabbitMQ garantiza "al menos una vez", así que los duplicados pueden ocurrir.
- **Reintentos:** 3 intentos con backoff exponencial (1 s, 2 s, 4 s) mediante el retry de Spring AMQP; tras agotarlos, el mensaje va a la DLQ (`RejectAndDontRequeueRecoverer`).
- Las excepciones no recuperables (mensaje mal formado, `DomainException` de validación) van directamente a la DLQ sin reintentar.
- Endpoint de administración (solo ADMIN) para consultar los mensajes de la DLQ y reenviarlos.
- ACK manual o automático tras el procesamiento correcto; nunca se pierde un mensaje por un fallo a mitad de proceso.

## Integración con el resto del sistema
- **Errores:** las excepciones en los listeners no pasan por el `GlobalExceptionHandler`; se gestionan con un `RabbitListenerErrorHandler` / `ErrorHandler` propio que registra el error con su `traceId` y decide reintentar o mandar a la DLQ.
- **Trazabilidad:** activa la observación en `RabbitTemplate` y en los listeners (`spring.rabbitmq.template.observation-enabled=true` y `spring.rabbitmq.listener.simple.observation-enabled=true`) para que el `traceId` viaje en las cabeceras del mensaje y Zipkin muestre el flujo completo: gateway → order-service → RabbitMQ → product-service → RabbitMQ → order-service.
- **Seguridad:** credenciales de RabbitMQ por variables de entorno y un usuario por servicio con permisos solo sobre sus exchanges y colas. Los listeners no tienen JWT: se ejecutan con un contexto de sistema y el `createdBy` de auditoría es `system:<servicio>`.
- **Health:** el estado de la conexión con RabbitMQ aparece en Actuator.

# Trazabilidad distribuida con Zipkin
- Todos los servicios (incluidos el gateway y `auth-service`) envían sus trazas a Zipkin con Micrometer Tracing + Brave.
- El contexto de traza se propaga automáticamente en las llamadas HTTP del gateway, en Feign y en los `RestClient`/`WebClient` (formato W3C `traceparent`).
- Las consultas a la base de datos aparecen como spans (p. ej. con `datasource-micrometer`), para ver cuánto tarda cada query paginada.
- Spans personalizados con `@Observed` en los métodos clave de los `*ServiceImpl`.
- Muestreo configurable por perfil: 100 % en `dev`, 10 % en `prod` (`management.tracing.sampling.probability`).
- Logs estructurados (JSON en `prod`) que incluyen `traceId` y `spanId` en cada línea, mediante el patrón de logging de Spring Boot.
- El `traceId` que se devuelve en los errores y en la cabecera `X-Trace-Id` es el mismo que se busca en Zipkin.

# Observabilidad: Prometheus + Grafana + Loki
Objetivo: ver todo el sistema desde Grafana, con métricas, logs y trazas correlacionados por `traceId`.

## Métricas (Prometheus)
- Todos los servicios exponen `/actuator/prometheus` (protegido: solo accesible desde la red interna de Docker, no a través del gateway).
- Prometheus descubre las réplicas automáticamente a través de Eureka (`eureka_sd_configs`) o del DNS de Docker, sin listar instancias a mano.
- Etiquetas comunes en todas las métricas: `application`, `instance` y `environment`.
- Histogramas de latencia (`http.server.requests` con percentiles p50/p95/p99) activados.
- **Exemplars** activados para que, desde un punto de una gráfica de latencia, se pueda saltar a la traza concreta de Zipkin.
- Métricas técnicas que deben aparecer: peticiones por segundo, latencia, errores 4xx/5xx por endpoint, JVM (heap, GC, hilos), pool de conexiones Hikari, estado de los circuit breakers de Resilience4j, rate limiting del gateway, colas de RabbitMQ (mensajes pendientes, en la DLQ, consumidores), eventos pendientes en el outbox y tiempo de espera de los locks distribuidos.
- **Métricas de negocio** propias con `MeterRegistry` (definidas tras una interfaz en `application.metrics` e implementadas en `infrastructure.metrics.impl`): pedidos creados, confirmados, rechazados y cancelados; stock insuficiente; registros de usuario; logins correctos y fallidos; cuentas bloqueadas.

## Logs (Loki)
- Logs en formato JSON estructurado (logging estructurado nativo de Spring Boot) con `traceId`, `spanId`, `application`, `instance` y nivel.
- Promtail (o Grafana Alloy) recoge los logs de todos los contenedores Docker y los envía a Loki, con etiquetas por servicio y réplica.
- Grafana configurado para saltar de un log a su traza en Zipkin (por `traceId`) y de una traza a sus logs.
- Nunca se registran datos sensibles (contraseñas, tokens, datos personales completos).

## Grafana
- Datasources de Prometheus, Loki y Zipkin provisionados automáticamente por fichero (`grafana/provisioning`), sin configuración manual.
- Dashboards provisionados como JSON en el repositorio:
  1. **Visión general del sistema:** estado de cada servicio y réplica, peticiones/s, errores y latencia p95 por servicio.
  2. **Por microservicio:** endpoints más lentos, errores por código HTTP, JVM, Hikari y circuit breakers.
  3. **Reparto de carga:** peticiones por réplica (para demostrar el balanceo y qué pasa al tirar una réplica).
  4. **Mensajería:** colas de RabbitMQ, DLQ, outbox y saga de pedidos.
  5. **Negocio:** pedidos por estado, stock insuficiente, registros y logins.
  6. **Seguridad:** logins fallidos, cuentas bloqueadas, respuestas 401/403/429.
- Alertas de ejemplo: tasa de errores 5xx > 5 % durante 5 min, latencia p95 > 1 s, mensajes en una DLQ, circuit breaker abierto, una réplica caída.
- Versionado de la API: `/api/v1/...`.

# Pruebas

## Tests unitarios con JUnit 5 y Mockito (requisito clave)
Herramientas: JUnit 5 (Jupiter), Mockito (`mockito-junit-jupiter`) y AssertJ para las aserciones. Los tests unitarios NO levantan el contexto de Spring (nada de `@SpringBootTest`), no usan base de datos, red ni RabbitMQ, y deben ejecutarse en milisegundos.

### Qué se prueba con tests unitarios
| Capa / clase | Qué verificar | Cómo |
|---|---|---|
| `domain.model` | Reglas de negocio de las entidades (p. ej. `Order.cancel()` solo si está `PENDING`, `Product.reserveStock()` lanza `BusinessRuleException` sin stock, cálculo del total del pedido) | JUnit puro, sin mocks |
| `domain.exception` | Que cada excepción lleva su `ErrorCode` y sus parámetros | JUnit puro |
| `application.service.impl` (`*ServiceImpl`) | Toda la lógica de los casos de uso: caminos felices, errores y casos límite | Mockito: mocks de las interfaces `domain.repository` y `domain.messaging` |
| `infrastructure.persistence.repository.impl` (`*RepositoryImpl`) | Conversión `PageQuery` → `Pageable` (página, tamaño, orden) y `Page<Entity>` → `PageResult<Model>` | Mockito: mock del `*JpaRepository` + `PageImpl` como respuesta |
| Mappers MapStruct | Que todos los campos se mapean bien en los dos sentidos, incluidos los nulos | Instancia real con `Mappers.getMapper(...)` |
| `infrastructure.exception` | Mapeo `ErrorCode` → `HttpStatus` y construcción del `ErrorResponseDTO` con el mensaje traducido | Mockito: mock de `MessageSource` |
| `infrastructure.messaging.listener` | Que el listener convierte el mensaje y llama al servicio correcto, y que ignora los eventos duplicados | Mockito: mock de la interfaz de servicio y del repositorio de `processed_event` |
| `infrastructure.messaging.publisher.impl` | Que se envía al exchange y routing key correctos con el sobre bien formado | Mockito: mock de `RabbitTemplate` + `ArgumentCaptor` |
| Outbox publisher | Que publica los eventos pendientes, los marca como enviados y no los marca si falla el envío | Mockito |
| Feign `ErrorDecoder` | Que cada código HTTP remoto se traduce a la excepción de dominio correcta | JUnit + `Response` construido a mano |
| `auth-service` | Bloqueo tras N intentos, reinicio del contador, política de contraseñas (`@StrongPassword`), historial de contraseñas, revocación de tokens al cambiar la contraseña, `OAuth2TokenCustomizer` añade roles y permisos al JWT | Mockito (mock de `PasswordEncoder`, repositorios, `Clock`) |
| Validadores personalizados | Casos válidos e inválidos | JUnit + `@ParameterizedTest` |

### Convenciones
- Una clase de test por clase probada, en el mismo paquete dentro de `src/test/java`, con el sufijo `Test` (p. ej. `ProductServiceImplTest`).
- `@ExtendWith(MockitoExtension.class)`, `@Mock` para las dependencias e `@InjectMocks` para la clase probada (inyección por constructor).
- Estructura **Given / When / Then** (Arrange / Act / Assert) con comentarios que separen cada bloque.
- Nombres descriptivos con el patrón `metodo_cuandoCondicion_deberiaResultado` y `@DisplayName` en español, p. ej. `@DisplayName("findById lanza ResourceNotFoundException si el producto no existe")`.
- `@Nested` para agrupar los tests por método (`class FindById { ... }`, `class Create { ... }`).
- Aserciones con AssertJ: `assertThat(...)`, `assertThatThrownBy(...).isInstanceOf(...).hasFieldOrPropertyWithValue("errorCode", ...)`.
- Verificar las interacciones importantes con `verify(...)`, `never()`, `times(n)` y `verifyNoMoreInteractions(...)` cuando aporte valor (p. ej. que NO se guarda ni se publica nada si falla una validación).
- `ArgumentCaptor` para comprobar lo que se guarda o se publica (p. ej. que el evento `OrderCreatedEvent` lleva las líneas correctas).
- `@ParameterizedTest` con `@CsvSource` / `@MethodSource` / `@EnumSource` para los casos con muchas variantes (validaciones, mapeo de códigos de error, valores límite de la paginación).
- Nada de `Thread.sleep`, fechas del sistema ni aleatoriedad: inyecta un `Clock` y usa `Clock.fixed(...)` en los tests.
- Datos de prueba con clases *builder* / *Object Mother* (`ProductMother.aProduct()`, `OrderMother.aPendingOrder()`) en un paquete `testdata`, para no repetir la construcción de objetos.
- Mockito en modo estricto (por defecto con `MockitoExtension`): sin stubs innecesarios.
- No mockear lo que no es tuyo cuando se pueda evitar (p. ej. usar `PageImpl` real en vez de mockear `Page`), ni los objetos de dominio.

### Casos mínimos por `*ServiceImpl`
- Crear: guarda correctamente; lanza `DuplicateResourceException` si ya existe; no guarda ni publica nada si falla una regla.
- Buscar por id: devuelve el modelo; lanza `ResourceNotFoundException` si no existe.
- Listar paginado: pasa los filtros y el `PageQuery` tal cual al repositorio y devuelve el `PageResult`; página vacía; última página; sin filtros.
- Actualizar: actualiza solo los campos permitidos; lanza 404 si no existe.
- Borrar: hace el borrado lógico (`active = false`), no un borrado físico.
- En `order-service`: crear un pedido guarda el evento en el outbox; un cliente o producto inexistente (Feign) lanza la excepción correcta; las respuestas de la saga cambian el estado a `CONFIRMED` / `REJECTED`; cancelar un pedido que no está `CONFIRMED` lanza `BusinessRuleException` (422 `ORDER_NOT_CANCELLABLE`).

Ejemplo del estilo esperado:
```java
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductEventPublisher productEventPublisher;
    @InjectMocks private ProductServiceImpl productService;

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("lanza ResourceNotFoundException si el producto no existe")
        void findById_whenProductDoesNotExist_shouldThrowNotFound() {
            // given
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

            verifyNoInteractions(productEventPublisher);
        }
    }
}
```

### Cobertura
- JaCoCo configurado en el `pom.xml` padre, con un informe agregado de todos los módulos.
- Cobertura mínima del 80 % de líneas y del 70 % de ramas en `domain` y `application`; el build (`mvn verify`) falla si no se alcanza.
- Se excluyen de la cobertura las clases de configuración, los DTOs, las entidades JPA, las clases `*Application` y el código generado por MapStruct.
- Tests unitarios con Surefire (`*Test`) y tests de integración con Failsafe (`*IT`), para poder ejecutarlos por separado: `mvn test` solo lanza los unitarios.

## Tests de integración y de slice
- `@DataJpaTest` + Testcontainers verificando la paginación (totalElements, totalPages, orden).
- `@WebMvcTest` verificando que cada tipo de excepción devuelve el código HTTP correcto y el formato JSON común, sin datos internos de Java.
- Pruebas de seguridad con `spring-security-test` (`jwt()`): 401 sin token, 403 sin permisos y 200 con el rol correcto.
- Pruebas de i18n: el mismo error devuelve el mensaje en español o en inglés según `Accept-Language`, y el `code` no cambia.
- Pruebas de seguridad extra: bloqueo tras 5 intentos fallidos (423), desbloqueo, registro en la auditoría y rechazo de un token revocado (401).
- Prueba del rate limiting en el gateway (429 al superar el límite, con Testcontainers de Redis).
- Pruebas de mensajería con Testcontainers de RabbitMQ: publicación desde el outbox, consumo correcto, mensaje duplicado ignorado (idempotencia), reintentos y paso a la DLQ. Usa Awaitility para esperar los resultados asíncronos.
- Prueba de la saga: pedido con stock → `CONFIRMED`; pedido sin stock → `REJECTED` sin modificar el stock; cancelación → stock liberado.
- Prueba de concurrencia del stock: N hilos reservando a la vez el último stock de un producto (con Testcontainers de Redis y PostgreSQL); solo uno lo consigue y el stock nunca queda negativo.
- Prueba de ShedLock: dos instancias del planificador sobre la misma base de datos; la tarea se ejecuta una sola vez.
- Tests unitarios (Mockito) de los `*ServiceImpl` que usan `DistributedLockManager`: se libera el lock aunque falle la operación, y se lanza la excepción correcta si no se consigue.
- Prueba de que `/actuator/prometheus` expone las métricas de negocio con sus etiquetas.
- Una prueba de integración end-to-end: obtener un token de `auth-service` → crear un producto → crear un cliente → crear un pedido a través del gateway.

# Entregables
1. Árbol completo de carpetas de todos los módulos.
2. `pom.xml` padre y de cada módulo con versiones explícitas.
3. Código completo de `auth-service` y `product-service` (todas las capas), incluidos el flujo de paginación y la gestión de excepciones de punta a punta.
4. Código de `customer-service`, `order-service`, `api-gateway` y `discovery-service` (puedes resumir las partes repetitivas, pero sin omitir la seguridad, las excepciones, la paginación, el CRUD completo POST/GET/PUT/DELETE de cada recurso ni los ejemplos de `@Transactional`).
5. Migraciones Flyway por servicio con datos de ejemplo (≥ 50 registros en las tablas paginadas).
6. `application.yml` por servicio y perfil, y los ficheros `messages_es.properties` / `messages_en.properties` con todos los códigos de error.
7. `Dockerfile` por servicio y `docker-compose.yml` (los 6 servicios con sus réplicas + PostgreSQL con una base por servicio + Redis + Zipkin + RabbitMQ + Prometheus + Loki + Promtail + Grafana, con volúmenes, healthchecks y orden de arranque). Opcionalmente, separar la observabilidad en un `docker-compose.observability.yml`.
8. Configuración de Prometheus (`prometheus.yml` y reglas de alerta), de Promtail/Alloy, y el provisioning de Grafana (datasources y dashboards en JSON).
9. Ejemplos `curl`: obtener el token, llamadas paginadas, ejemplos de cada error (400, 401, 403, 404, 409, 422, 423, 429, 500, 503) en los dos idiomas, logout/revocación, cómo buscar una petición en Zipkin (http://localhost:9411) por su `traceId` y cómo seguir un pedido por las colas en la consola de RabbitMQ (http://localhost:15672).
10. README con las instrucciones para levantar y probar el sistema, las URLs de todas las consolas (Grafana http://localhost:3000, Prometheus http://localhost:9090, Zipkin, RabbitMQ, Eureka, Swagger) y una sección **"Escenarios de demostración"** con guiones paso a paso, como mínimo:
    - Escalar `product-service` a 3 réplicas y ver en Grafana cómo se reparte el tráfico; apagar una y comprobar que no se pierde ninguna petición.
    - Lanzar muchos pedidos concurrentes sobre el último stock de un producto y comprobar que el stock nunca queda negativo.
    - Seguir un pedido de punta a punta: dashboard de negocio → log en Loki → traza en Zipkin.

# Forma de trabajo
- Antes del código, muestra con Mermaid: (a) la arquitectura general, (b) el flujo OAuth2 de obtención y validación del token, y (c) el flujo de una petición paginada: Gateway → Controller → ProductService → ProductServiceImpl → ProductRepository → ProductRepositoryImpl → ProductJpaRepository → PostgreSQL.
- Genera el código por fases y espera mi confirmación entre ellas: (1) estructura, `common-lib`, excepciones e i18n, (2) `auth-service` con OAuth2 y la seguridad extra, (3) `product-service` completo con paginación, (4) `customer-service` y `order-service` con Feign, (5) mensajería con RabbitMQ (outbox, saga de pedidos y DLQ), (6) gateway con rate limiting, Eureka, Zipkin y Docker, (7) escalado horizontal (réplicas, ShedLock y locks distribuidos), (8) observabilidad con Prometheus, Loki y Grafana, (9) tests de integración, end-to-end y cobertura.
- Los tests unitarios con JUnit y Mockito NO se dejan para el final: cada fase entrega su código junto con sus tests unitarios.
- Explica brevemente las decisiones de diseño importantes.
