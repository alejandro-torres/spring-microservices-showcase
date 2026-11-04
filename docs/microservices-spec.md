# Role
Act as a senior software architect expert in Java, Spring Boot, Spring Security, OAuth2, microservices and hexagonal architecture (Ports & Adapters). Generate clean, production-ready code that follows SOLID and Clean Code.

# Goal
Create a microservices system for [DOMAIN: e.g. managing an online store] with at least 4 business microservices and 2 infrastructure ones:

Business microservices:
1. `auth-service`: management of users, roles and permissions, and OAuth2 authorization server (issues the JWT tokens).
2. `product-service`: product management (CRUD + paginated search).
3. `customer-service`: customer management (CRUD + paginated search).
4. `order-service`: order management; queries `product-service` and `customer-service` (Feign) and coordinates the stock reservation through events (RabbitMQ).

Infrastructure:
- `discovery-service`: Eureka Server.
- `api-gateway`: Spring Cloud Gateway (single entry point: validates tokens, routes and applies rate limiting).
- Zipkin: distributed tracing server (official `openzipkin/zipkin` container).
- Redis: store for the gateway's rate limiting and the list of revoked tokens.
- RabbitMQ: messaging broker for asynchronous communication between microservices (`rabbitmq:3-management` image, with the web console on port 15672).
- Prometheus: collection of metrics from all services.
- Loki + Promtail (or Grafana Alloy): centralization of the logs of all containers.
- Grafana: dashboards and alerts on metrics, logs and traces.

# Tech stack
- Java 21, Spring Boot 4.1 (Spring Framework 7, Jakarta EE 11, Jackson 3), Spring Cloud 2025.1, multi-module Maven with a parent `pom.xml` and Maven Wrapper
- Spring Data JPA + Hibernate
- PostgreSQL 16 (one database per microservice: Database per Service pattern)
- Flyway for migrations
- MapStruct to map between layers
- Lombok in infrastructure and application (constructors with `@RequiredArgsConstructor`), never in the domain
- Bean Validation (Jakarta Validation)
- Spring Security 7 with its built-in OAuth2 authorization server (in `auth-service`) + OAuth2 Resource Server (in the rest)
- Springdoc OpenAPI (Swagger UI with Bearer/OAuth2 support)
- Spring Cloud: Eureka, Gateway (with `RequestRateLimiter` on Redis), OpenFeign and Resilience4j (circuit breaker)
- Micrometer Tracing (Brave bridge) + Zipkin reporter for distributed tracing
- Spring Data Redis (reactive in the gateway) and Redisson for distributed locks
- ShedLock (JDBC provider on PostgreSQL) for scheduled tasks with several replicas
- Micrometer + `micrometer-registry-prometheus` for metrics
- Spring's `MessageSource` for message internationalization (i18n)
- Spring AMQP (`spring-boot-starter-amqp`) with RabbitMQ
- Docker and docker-compose
- JUnit 5, Mockito, AssertJ, Awaitility, JaCoCo, Testcontainers (PostgreSQL, Redis and RabbitMQ) and spring-security-test

# Hexagonal architecture (mandatory in each microservice)
Package structure:

com.[company].[service]
├── domain
│   ├── model                 → pure domain entities (no JPA or Spring annotations)
│   ├── exception             → custom business exceptions (see the exceptions section)
│   ├── event                 → domain events (immutable records: OrderCreatedEvent...)
│   ├── repository            → repository INTERFACES (outbound ports)
│   │   └── ProductRepository.java
│   └── messaging             → event publishing INTERFACES (outbound ports)
│       └── ProductEventPublisher.java
├── application
│   ├── service               → service INTERFACES (inbound ports / use cases)
│   │   └── ProductService.java
│   ├── service.impl          → service IMPLEMENTATIONS
│   │   └── ProductServiceImpl.java
│   ├── client                → INTERFACES towards other microservices (e.g. ProductClient, CustomerClient in order-service)
│   ├── lock                  → DistributedLockManager INTERFACE
│   └── metrics               → business metrics INTERFACES
└── infrastructure
    ├── rest
    │   ├── controller        → REST controllers
    │   ├── dto
    │   │   ├── request       → INPUT DTOs (what the API receives): CreateProductRequestDTO, UpdateProductRequestDTO...
    │   │   └── response      → OUTPUT DTOs (what the API returns): ProductResponseDTO, CategoryResponseDTO...
    │   └── mapper            → DTO ↔ domain mappers (MapStruct)
    ├── persistence
    │   ├── entity            → JPA entities (*Entity)
    │   ├── repository        → Spring Data interfaces (extending JpaRepository and JpaSpecificationExecutor)
    │   │   └── ProductJpaRepository.java
    │   ├── repository.impl   → IMPLEMENTATIONS of the domain.repository interfaces (adapters)
    │   │   └── ProductRepositoryImpl.java
    │   ├── specification     → *FilterMapping (public filter name → JPA path); the GenericSpecificationBuilder is in common-lib
    │   └── mapper            → Entity ↔ domain mappers (MapStruct)
    ├── messaging
    │   ├── config            → declaration of exchanges, queues, bindings, DLQ and JSON converter
    │   ├── publisher.impl    → IMPLEMENTATIONS of the domain.messaging interfaces (they store the event in the outbox; the actual send with RabbitTemplate is done by the outbox publisher)
    │   │   └── ProductEventPublisherImpl.java
    │   ├── listener          → @RabbitListener consumers (inbound adapters: they call the service interfaces)
    │   ├── outbox            → outbox table, entity, repository and scheduled publisher
    │   └── mapper            → mappers between domain events and messaging DTOs (`*MessageDTO`)
    ├── lock.impl             → implementation of the distributed locks with Redisson
    ├── metrics.impl          → implementation of the business metrics with MeterRegistry
    ├── client
    │   ├── feign             → @FeignClient interfaces (ProductFeignClient...) + ErrorDecoder + token interceptor
    │   ├── impl              → IMPLEMENTATIONS of the application.client interfaces (ProductClientImpl uses ProductFeignClient and converts DTO ↔ domain)
    │   └── dto
    │       ├── request       → DTOs SENT to the other service
    │       └── response      → DTOs RECEIVED from the other service (ProductClientResponseDTO...)
    ├── security              → security configuration, entry point and access denied handler
    ├── exception             → GlobalExceptionHandler and the mapping of exceptions to HTTP codes
    └── config                → Spring beans and configuration

Rules:
- Each service interface (`application.service`) has its implementation in `application.service.impl` with the `Impl` suffix, annotated with `@Service`.
- Each domain repository interface (`domain.repository`) has its implementation in `infrastructure.persistence.repository.impl` with the `Impl` suffix.
- `ProductRepositoryImpl` internally uses `ProductJpaRepository`, and converts `PageQuery` → `Pageable` and `Page<ProductEntity>` → `PageResult<Product>`.
- `ProductServiceImpl` depends only on the `ProductRepository` interface, never on the implementation or on Spring Data.
- Controllers depend only on the `ProductService` interface.
- Each publishing interface (`domain.messaging`) has its implementation in `infrastructure.messaging.publisher.impl` with the `Impl` suffix; the `*ServiceImpl` classes only know the interface, never RabbitMQ.
- Each `application.client` interface has its implementation in `infrastructure.client.impl` with the `Impl` suffix, which uses the corresponding `@FeignClient` interface; the `*ServiceImpl` classes never use Feign directly.
- RabbitMQ listeners are inbound adapters, just like controllers: they convert the message and call the service interface, with no business logic.
- The domain does NOT depend on Spring, JPA or any framework; dependencies always point towards the domain.
- Constructor dependency injection.
- Common code (ErrorResponseDTO, PageResponseDTO, PageResult, PageQuery, the base exception) can go in a shared `common-lib` module, with no Spring dependencies in its domain part.

# Pagination with `Page`
- Spring Data repositories return `org.springframework.data.domain.Page<T>` receiving a `Pageable`.
- To keep the domain free of Spring, define `PageResult<T>` (content, page, size, totalElements, totalPages, first, last) and `PageQuery` (page, size, sort, direction).
- The persistence adapter converts `PageQuery` → `PageRequest` and `Page<Entity>` → `PageResult<DomainModel>` using `page.map(...)`.
- The controller receives `page`, `size`, `sort` and `direction` as `@RequestParam` (defaults: page=0, size=10, sort=id, direction=ASC), validates that `size` is between 1 and 100 and returns a `PageResponseDTO<T>`.
- Validate the `sort` field against a whitelist of allowed fields; an invalid field returns 400, not a Hibernate error.
- ALL endpoints that list paginated data accept dynamic filters (see the next section).
- Include an example with JPQL `@Query` returning `Page<T>` with its `countQuery`.

Sample response:
{
  "content": [ ... ],
  "page": 0,
  "size": 10,
  "totalElements": 57,
  "totalPages": 6,
  "first": true,
  "last": false
}

# Dynamic filters on paginated listings (key requirement)
All paginated `GET`s accept any combination of filters on each resource's allowed fields, without creating a repository method per combination. It is implemented ONCE generically in `common-lib` and each resource only declares which fields can be filtered.

## URL syntax
Repeatable `filter` parameter in the format `field:operator:value`:

GET /api/v1/products?filter=name:like:laptop&filter=price:gte:100&filter=price:lte:500&filter=categoryId:in:1,2,3&page=0&size=10&sort=price&direction=DESC

- `:` is the separator, splitting only on the first two `:` (so the value can contain `:`, e.g. dates with time `createdAt:gte:2026-10-01T00:00:00Z`).
- Brackets (`price[gte]=100`) are not used because Tomcat rejects them unencoded and it would force relaxing its configuration.
- Several filters combine with AND. For OR on the same field, use the `in` operator.
- At most 10 filters per request (configurable); above that, 400.
- Without filters, the endpoint returns the normal paginated listing.

## Supported operators
| Operator | Meaning | Example | Allowed types |
|---|---|---|---|
| `eq` | equal | `status:eq:CONFIRMED` | all |
| `ne` | not equal | `status:ne:CANCELLED` | all |
| `gt` / `gte` | greater / greater or equal | `price:gte:100` | numbers and dates |
| `lt` / `lte` | less / less or equal | `createdAt:lt:2026-10-01T00:00:00Z` | numbers and dates |
| `between` | inclusive range (2 values) | `price:between:100,500` | numbers and dates |
| `like` | contains, ignoring case and accents | `name:like:laptop` | text |
| `startsWith` | starts with | `email:startsWith:ana` | text |
| `in` | one of several values | `categoryId:in:1,2,3` | all |
| `isNull` / `isNotNull` | empty / not empty (no value) | `phone:isNull` | all |

## Design respecting the hexagonal architecture
- **Domain (`common-lib`, no Spring):**
  - `FilterCriterion` (record: `field`, `FilterOperator operator`, `List<Object> values`) with the values already converted to their type.
  - `FilterOperator` (enum with the operators in the table).
  - Service and repository interfaces receive the filters along with the pagination: `findAll(List<FilterCriterion> filters, PageQuery pageQuery)`.
- **Inbound adapter (REST):**
  - Each resource declares its filterable fields in a `*FilterFields` class (e.g. `ProductFilterFields`) with: public field name, Java type (`String`, `BigDecimal`, `Long`, `Instant`, `LocalDate`, `Boolean` or an enum) and the operators allowed for that field. It is a whitelist: what is not declared cannot be filtered (e.g. never `passwordHash`).
  - A generic `FilterCriteriaParser` (in `common-lib`) parses the `filter`s, validates field, operator and number of values, converts the text to the right type and returns the list of `FilterCriterion`.
  - The controller receives `@RequestParam(required = false) List<String> filter` and passes it to the parser.
- **Outbound adapter (persistence):**
  - Each resource has a `*FilterMapping` class (e.g. `ProductFilterMapping`) that translates the public name to the JPA path, including joins: `categoryId` → `category.id`, `categoryName` → `category.name`. This way the API does not expose the entities' internal structure.
  - A `GenericSpecificationBuilder<T>` (in `common-lib`, infrastructure part) converts the list of `FilterCriterion` into a `Specification<T>` combining the predicates with AND; it always adds `active = true` unless an ADMIN requests `includeInactive=true`.
  - `ProductRepositoryImpl` calls `jpaRepository.findAll(specification, pageable)` (from `JpaSpecificationExecutor`) and converts the `Page` to `PageResult` as usual.
- `like` and `startsWith` use PostgreSQL's `lower()` and `unaccent()` (extension created by Flyway) and escape `%` and `_` in the user's value.
- Joins to collections use `query.distinct(true)` and it is verified that the `count` query is still correct.

## Filterable fields per resource
| Resource | Main fields and operators |
|---|---|
| `products` | `name` (like, startsWith, eq), `price` (eq, gt, gte, lt, lte, between), `stock` (eq, gt, gte, lt, lte), `categoryId` (eq, in), `categoryName` (like), `createdAt` (gte, lte, between) |
| `categories` | `name` (like, eq), `createdAt` (gte, lte, between) |
| `customers` | `firstName`, `lastName`, `email` (like, startsWith, eq), `city` (eq, like; join with addresses), `active` (eq, ADMIN only), `createdAt` (gte, lte, between) |
| `orders` | `status` (eq, ne, in), `customerId` (eq, in), `total` (gte, lte, between), `createdAt` (gte, lte, between), `productId` (eq; join with the lines) |
| `users` | `username`, `email` (like, startsWith, eq), `enabled` (eq), `role` (eq, in; join with roles), `locked` (eq) |
| `roles`, `permissions` | `name` (like, eq) |
| `security-audit` | `eventType` (eq, in), `username` (eq, like), `ip` (eq), `occurredAt` (gte, lte, between) |

## Errors
All return 400 with the common format and the translated message:
- `INVALID_FILTER_FORMAT`: the filter does not follow `field:operator:value`.
- `FILTER_FIELD_NOT_ALLOWED`: the field is not on the resource's whitelist.
- `FILTER_OPERATOR_NOT_ALLOWED`: the operator is not allowed for that field (e.g. `like` on `price`).
- `INVALID_FILTER_VALUE`: the value cannot be converted to the type (e.g. `price:gte:abc`) or the number of values is not the expected one (`between` needs 2).
- `TOO_MANY_FILTERS`: the maximum number of filters is exceeded.

## Documentation and tests
- Swagger documents the `filter` parameter on each endpoint with the resource's allowed fields and operators and examples.
- Unit tests (JUnit + `@ParameterizedTest`) of `FilterCriteriaParser`: each operator, conversion of each type, values with `:`, and all the errors above.
- Unit tests of `GenericSpecificationBuilder` with mocks of `Root`, `CriteriaQuery` and `CriteriaBuilder` or, better, `@DataJpaTest` tests with Testcontainers that check the real results of each operator on the sample data.
- Test that an undeclared field (e.g. `passwordHash` on users) returns 400 and never reaches the query.
- Combined test: filters + pagination + sorting return the right `totalElements`.

# Exception handling (key requirement)
Goal: the API must NEVER expose internal Java errors (stack traces, class names, Hibernate/SQL messages, `NullPointerException`, etc.). All error responses use only standard HTTP codes and a common JSON format.

## Custom exceptions
- In `domain.exception`, create an abstract base exception `DomainException extends RuntimeException` with an `ErrorCode` and its parameters (no HTTP dependencies). `ErrorCode` is a `common-lib` interface (`code()`, `messageKey()`) implemented by a common enum (`CommonErrorCode`: VALIDATION_ERROR, RESOURCE_NOT_FOUND, CONCURRENT_MODIFICATION, INTERNAL_ERROR...) and by each service's own enum (`ProductErrorCode`, `OrderErrorCode`, `AuthErrorCode`...).
- Specific exceptions at a minimum:
  - `ResourceNotFoundException` → 404
  - `DuplicateResourceException` → 409
  - `BusinessRuleException` (broken business rule, e.g. insufficient stock) → 422
  - `InvalidRequestException` → 400
  - `UnauthorizedException` (invalid credentials, expired token) → 401
  - `ForbiddenException` (no permissions) → 403
  - `ExternalServiceException` (failure of another microservice) → 502 / 503
- The `ErrorCode` → `HttpStatus` mapping is done in `infrastructure.exception`, not in the domain.
- Adding new exceptions must be easy: create the class + add its `ErrorCode` + its mapping, without touching the handler.

## GlobalExceptionHandler
A `@RestControllerAdvice` in each service (or shared in `common-lib`) that catches at a minimum:

| Exception | HTTP code |
|---|---|
| `DomainException` and its subclasses | per the mapping (400/401/403/404/409/422/502/503) |
| `MethodArgumentNotValidException`, `ConstraintViolationException`, `BindException` | 400 (with the list of invalid fields) |
| `HttpMessageNotReadableException` (malformed JSON) | 400 |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `MissingPathVariableException` | 400 |
| `PropertyReferenceException` (invalid sort field in pagination) | 400 |
| `AuthenticationException`, `InvalidBearerTokenException`, `JwtException` | 401 |
| `AccessDeniedException` / `AuthorizationDeniedException` | 403 |
| `NoResourceFoundException` / `NoHandlerFoundException` | 404 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `HttpMediaTypeNotAcceptableException` | 406 |
| `DataIntegrityViolationException` (duplicate key, FK) | 409 |
| `ObjectOptimisticLockingFailureException` | 409 |
| `HttpMediaTypeNotSupportedException` | 415 |
| `AccountLockedException` (account locked by failed attempts) | 423 |
| Request limit exceeded (rate limiting in the gateway) | 429 (with the `Retry-After` header) |
| `FeignException` (reusing the remote service's code when it is 4xx) | 4xx / 502 |
| `CallNotPermittedException` (open circuit breaker), timeouts | 503 |
| `Exception` (generic, last resort) | 500 with the generic message "Internal server error" |

Rules:
- All exceptions are logged with the full stack trace and a `traceId`; the client only gets a generic, safe message.
- 401/403 errors occurring in the Spring Security filters (outside the controllers) are handled with a custom `AuthenticationEntryPoint` and `AccessDeniedHandler` that return the same JSON format.
- The `api-gateway` (WebFlux) has its own `ErrorWebExceptionHandler` with the same format (e.g. 503 if a service is unavailable, 404 if there is no route).
- Feign clients use an `ErrorDecoder` that translates remote error responses to domain exceptions.
- Configure `server.error.include-stacktrace=never`, `include-message=never` and `include-exception=false`.
- Note: 3xx codes are for redirection, not errors; use them only where appropriate (e.g. OAuth2 flow redirects or 304 Not Modified). Errors always return 4xx (client failure) or 5xx (server failure).

## Common error format (based on RFC 7807 / `ProblemDetail`)
{
  "timestamp": "2026-10-13T10:15:30Z",
  "status": 404,
  "error": "Not Found",
  "code": "PRODUCT_NOT_FOUND",
  "message": "No active product with id 25 was found.",
  "path": "/api/v1/products/25",
  "traceId": "a1b2c3d4e5",
  "errors": [
    { "field": "price", "message": "must be greater than 0" }
  ]
}
(`errors` only appears in validation errors. The `traceId` is the same one that appears in Zipkin, to be able to locate the failed request.)

## Error messages in several languages (i18n)
- Supported languages: English (default) and Spanish; adding more must be easy.
- The language is taken from the `Accept-Language` header with an `AcceptHeaderLocaleResolver`; if missing or not supported, English is used.
- The texts are in `messages.properties` (English) and `messages_es.properties`, with keys based on the `ErrorCode` (e.g. `error.PRODUCT_NOT_FOUND=No active product with id {0} was found.`).
- The domain does NOT contain translated texts: exceptions carry the `ErrorCode` and its parameters, and the `GlobalExceptionHandler` resolves the message with `MessageSource` according to the language.
- Bean Validation messages are also translated (a `LocalValidatorFactoryBean` connected to the `MessageSource`).
- The `code` field of the error JSON is NOT translated (it lets the frontend identify the error); only `message` and the messages in `errors` are translated.
- The gateway and the security handlers (401/403/429) also return the message in the requested language.

# auth-service: users, roles and OAuth2
- Implemented with Spring Security 7's OAuth2 authorization server (formerly the Spring Authorization Server project), it issues JWT tokens signed with RSA and exposes the JWKS (`/oauth2/jwks`).
- Supported flows: `authorization_code` with PKCE (frontends), `client_credentials` (communication between microservices) and `refresh_token`.
- Model: `User` (username, email, password with BCrypt, enabled, accountNonLocked), `Role` (ADMIN, USER, ...) and `Permission` (e.g. `product:read`, `product:write`), with many-to-many relations.
- Management endpoints (protected, ADMIN only): paginated user CRUD with `Page`, role CRUD, assigning and removing a user's roles, enabling and disabling users.
- Public endpoints: user registration and password change (authenticated user).
- Roles and permissions are included in the JWT as custom claims (`OAuth2TokenCustomizer`).
- OAuth2 clients registered in the database (`JdbcRegisteredClientRepository`), with sample data in Flyway: an ADMIN user, a USER user and a client for each microservice. Sample credentials only in the `dev` profile migrations.
- It also follows the hexagonal architecture and the exception rules (e.g. `UserNotFoundException`, `EmailAlreadyExistsException`, `InvalidCredentialsException`, `AccountLockedException`).

## Extra security in auth-service
- **Lockout after failed attempts:** after 5 failed attempts in a row (configurable), the account is locked for 15 minutes (configurable) and returns 423 with the `ACCOUNT_LOCKED` code. The counter resets after a successful login. An ADMIN can unlock the account manually.
- **Security audit log:** `security_audit_log` table with the events LOGIN_SUCCESS, LOGIN_FAILURE, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED, PASSWORD_CHANGED, ROLE_ASSIGNED, ROLE_REMOVED, TOKEN_REVOKED and USER_DISABLED, storing the user, IP, user-agent, date and `traceId`. It is recorded by listening to Spring Security's events (`AuthenticationSuccessEvent`, `AbstractAuthenticationFailureEvent`) and the domain's own events.
- Paginated endpoint with `Page` to view the audit log (ADMIN only), with filters by user, event type and date range.
- **Token revocation:** logout endpoint and revocation endpoint (`/oauth2/revoke`). Revoked tokens are stored in Redis (key = `jti`, with a TTL equal to the token's remaining time), and the gateway and the resource servers reject them with 401.
- On changing the password, disabling a user or removing a role, all their active tokens are revoked.
- **Password policy:** minimum 8 characters, uppercase, lowercase, digits and symbols, validated with a custom annotation (`@StrongPassword`), and the last 3 passwords cannot be repeated.
- Short-lived access tokens (15 min) and refresh tokens with rotation (each use generates a new one and invalidates the previous one).
- Passwords, tokens and secrets are never logged.

# Security in the other services
- `product-service`, `customer-service` and `order-service` are OAuth2 Resource Servers that validate the JWT using `auth-service`'s JWKS.
- Convert the role and permission claims to `GrantedAuthority` with a `JwtAuthenticationConverter`.
- Authorization with `@PreAuthorize` (e.g. `hasAuthority('product:write')` to create and `hasRole('ADMIN')` to delete).
- `order-service` calls the other services propagating the user's token (Feign interceptor) or with `client_credentials`.
- The `api-gateway` validates the token before routing (TokenRelay), checks it is not revoked and leaves only the login, registration and Swagger routes public.

# API Gateway
- Routes to the 4 microservices through Eureka (`lb://product-service`, etc.) with the `/api/v1/...` prefix.
- **Rate limiting** with `RequestRateLimiter` + Redis (token bucket): limit per authenticated user (JWT `sub` claim) and per IP for public routes. Values configurable per route (e.g. 20 requests/second with bursts of 40; login more restrictive, 5/minute). On exceeding it, returns 429 with `Retry-After` and the common error format.
- Resilience4j circuit breaker per route with a fallback returning 503 in the common format.
- Global filter that generates or propagates the `traceId` and returns it in the `X-Trace-Id` response header.
- Centralized CORS configuration.
- Aggregated Swagger UI with the documentation of all services.

# CRUD API (mandatory in all services)
Each resource exposes a full CRUD with these operations and response codes:

| Operation | Method and path | Successful response | Typical errors |
|---|---|---|---|
| Create | `POST /api/v1/{resource}` | 201 Created + `Location` header + the created resource | 400, 401, 403, 409, 422 |
| Get by id | `GET /api/v1/{resource}/{id}` | 200 OK | 401, 403, 404 |
| Paginated list with filters | `GET /api/v1/{resource}?filter=field:operator:value&page=0&size=10&sort=id&direction=ASC` | 200 OK with `PageResponseDTO` | 400, 401, 403 |
| Update | `PUT /api/v1/{resource}/{id}` (includes the `version` field for optimistic locking) | 200 OK + the updated resource | 400, 401, 403, 404, 409, 422 |
| Delete | `DELETE /api/v1/{resource}/{id}` (soft delete: `active = false`) | 204 No Content | 401, 403, 404, 409 |

Resources per service (each with its full CRUD and all its layers: controller → service interface → `*ServiceImpl` → repository interface → `*RepositoryImpl` → `*JpaRepository`):

| Service | Resources | Additional operations |
|---|---|---|
| `auth-service` | `users`, `roles`, `permissions` | `POST /users/{id}/roles/{roleId}` and `DELETE /users/{id}/roles/{roleId}` (assign and remove roles), `PUT /users/{id}/unlock`, `PUT /users/me/password`, `POST /auth/register`, `GET /security-audit` (paginated) |
| `product-service` | `products`, `categories` | `PUT /products/{id}/stock` (manual stock adjustment, ADMIN only) |
| `customer-service` | `customers`, `addresses` (nested: `/customers/{id}/addresses`) | `GET /customers/by-user/{userId}` |
| `order-service` | `orders` (with their `order-items` lines) | `PUT /orders/{id}/cancel`, `GET /orders/by-customer/{customerId}` (paginated) |

Rules:
- Separate DTOs for each operation: `Create*RequestDTO`, `Update*RequestDTO` and `*ResponseDTO`; JPA entities and domain models are never exposed.
- **Naming convention: ALL DTOs end in `DTO`** to identify them at a glance, both those of the REST API (`CreateProductRequestDTO`, `UpdateProductRequestDTO`, `ProductResponseDTO`, `PageResponseDTO<T>`, `ErrorResponseDTO`, `FieldErrorDTO`) and those of messaging (`EventEnvelopeDTO<T>`, `OrderCreatedMessageDTO`...) and of the Feign clients (`ProductClientResponseDTO`...). No other class (entities, domain models, domain events) has that suffix.
- DTOs are implemented as Java `record`s.
- **DTOs are separated into two folders:** `dto/request` for input (what the endpoint receives, with the `RequestDTO` suffix and the Bean Validation annotations) and `dto/response` for output (what the endpoint returns, with the `ResponseDTO` suffix, no validations). The same DTO is never used as both input and output.
- In `common-lib`, `PageResponseDTO<T>`, `ErrorResponseDTO` and `FieldErrorDTO` go in `infrastructure/rest/dto/response`.
- Validations with Bean Validation on requests (`@NotBlank`, `@Positive`, `@Email`, `@Size`...) and validation groups if create and update have different rules.
- A `DELETE` of an already deleted resource returns 404; deleting a resource with active dependencies (e.g. a category with products, a customer with pending orders) returns 409.
- `GET`s do not return records with `active = false`, unless an ADMIN passes `includeInactive=true`.
- Swagger documents each endpoint with its possible error responses (`@ApiResponse`).

# Transactions with `@Transactional` (key requirement)
Use Spring's `@Transactional` (`org.springframework.transaction.annotation.Transactional`) with real examples in ALL services, explaining with a brief comment why each configuration is used.

## Where it goes
- On the public methods of the `*ServiceImpl` classes (application layer), which is where each use case starts and ends. Never on controllers, the domain or interfaces.
- The `*RepositoryImpl` classes do not open their own transactions: they join the service's (default `REQUIRED` propagation).
- RabbitMQ listeners delegate to a `@Transactional` method of the service; they are not transactional themselves.

## Configurations that must appear
| Configuration | Where to use it (examples) |
|---|---|
| `@Transactional(readOnly = true)` | All `GET`s (by id and paginated). At class level on the `*ServiceImpl` classes, overriding with `@Transactional` on write methods |
| `@Transactional` (default, `REQUIRED`) | `POST`, `PUT` and `DELETE` of all resources |
| `rollbackFor = Exception.class` | On methods that may throw *checked* exceptions, explaining that by default Spring only rolls back on `RuntimeException` and `Error` |
| `noRollbackFor = ...` | A justified case, e.g. on a failed login: `InvalidCredentialsException` is thrown but the failed attempts counter must still be saved |
| `propagation = Propagation.REQUIRES_NEW` | Saving the security audit log (`security_audit_log`): the record is saved even if the main operation rolls back |
| `propagation = Propagation.MANDATORY` | Internal methods that only make sense within an already open transaction (e.g. saving to the outbox) |
| `isolation = Isolation.REPEATABLE_READ` (or `SERIALIZABLE`) | A justified example in the stock reservation, comparing it with optimistic locking and the distributed lock |
| `timeout = 5` | Operations that must not hold connections for long (e.g. creating an order) |
| `@TransactionalEventListener(phase = AFTER_COMMIT)` | Actions that must only happen if the transaction commits (e.g. invalidating a cache or recording a business metric) |
| `TransactionTemplate` (programmatic) | When you need to control exactly where the transaction starts and ends: the stock reservation (distributed lock → transaction → commit → unlock) |

## Mandatory examples per service
- **auth-service:**
  - Creating a user with their roles and the password history record in a single transaction.
  - Assigning or removing a role and revoking their tokens.
  - Failed login with `noRollbackFor` and audit log with `REQUIRES_NEW`.
- **product-service:**
  - Creating a product validating that its category exists.
  - Updating with `@Version` control.
  - Soft delete of a category checking it has no products.
  - Stock reservation of several lines: all or none (if one fails, roll back all).
- **customer-service:**
  - Creating a customer with their addresses (cascade) in a transaction.
  - Consuming `UserRegisteredEvent` and creating the profile idempotently (saving the `processed_event` in the same transaction).
- **order-service:**
  - Creating an order with its lines and its outbox event in the SAME transaction (if anything fails, neither the order nor the event remains).
  - Cancelling an order by changing the status and saving the `OrderCancelledEvent` in the outbox.
  - Updating the status on receiving the saga's response.

## Typical mistakes to avoid (and explain in comments)
- **Self-invocation:** calling a `@Transactional` method from another method of the same class does not go through the proxy and the annotation is ignored. Show it and solve it by moving the method to another bean.
- `@Transactional` on `private` or `final` methods: it does not work.
- Catching the exception inside the transactional method and not rethrowing it: the transaction commits without anyone noticing.
- Remote calls (Feign) or sends to RabbitMQ inside a long transaction: do them before the transaction or through the outbox, never holding the database connection.
- `LazyInitializationException`: with `open-in-view=false`, everything mapped to a DTO is loaded within the transaction (with `@EntityGraph` / `JOIN FETCH`).

## Transaction tests
- Integration tests (Testcontainers) that demonstrate the rollback: if an order's second line fails, neither the order nor the outbox event remains.
- Test that the audit log with `REQUIRES_NEW` is saved even if the main operation fails.
- Test that the failed attempts counter is saved despite the exception (`noRollbackFor`).
- Test demonstrating the self-invocation problem and its solution.

# Common functional requirements
- Auditing: `createdAt`, `updatedAt`, `createdBy` and `updatedBy` (user obtained from the JWT with `AuditorAware`).
- Optimistic locking with `@Version`.
- `order-service` validates that the customer and products exist, with a Resilience4j fallback.

# Non-functional requirements
- `dev`, `test` and `prod` profiles; credentials and secrets in environment variables.
- `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`.
- Avoid the N+1 problem (`@EntityGraph` / `JOIN FETCH`, being careful when paginating collections).
- Actuator with health checks.

# Horizontal scaling (several replicas per service)
The system must work correctly with several instances of each microservice at once.

## Replicas and balancing
- Services are *stateless*: no in-memory state between requests (sessions, local caches that must be consistent, counters); what is shared goes in PostgreSQL or Redis.
- In docker-compose at least 3 replicas of `product-service` and 2 of `order-service` are started (`deploy.replicas` or `docker compose up --scale`), with no fixed published ports (only the gateway exposes a port).
- Each replica registers in Eureka with a unique `instance-id` (`${spring.application.name}:${random.uuid}`), and the gateway and Feign balance with Spring Cloud LoadBalancer.
- Each response includes the `X-Instance-Id` header with the replica that served it, to demonstrate the load distribution.
- The RabbitMQ consumers of all replicas share the same queue (*competing consumers*): each message is processed by only one replica. Configure the listeners' `prefetch` and `concurrency`.
- **Graceful shutdown** (`server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`): a replica shutting down finishes in-flight requests and messages and deregisters from Eureka before stopping.
- Adjust the registry and the Eureka and LoadBalancer caches so that a downed replica stops receiving traffic within seconds; combine it with the gateway's retry to retry on another replica.

## Scheduled tasks with ShedLock
- All `@Scheduled` tasks (outbox publisher, `processed_event` cleanup, automatic account unlock, expired token cleanup) are annotated with `@SchedulerLock`, so that only one replica runs them at any moment.
- `shedlock` table created by Flyway in each database that needs it.
- `lockAtMostFor` (in case the replica dies holding the lock) and `lockAtLeastFor` configured on each task; values in `application.yml`.
- The outbox publisher also processes events in batches with `SELECT ... FOR UPDATE SKIP LOCKED` as a second protection against duplicates.

## Distributed locks with Redisson
- Stock reservation and release in `product-service` is protected with a distributed per-product lock (`lock:product:{id}`), so two replicas do not modify the same product at once.
- If an order has several products, a `RedissonMultiLock` is used, acquiring the locks sorted by id, to avoid *deadlocks*.
- `tryLock` with wait and expiry times (never a lock without expiry); if not acquired, a `DomainException` is thrown that ends in a message retry or in 409/503 as appropriate.
- The lock is always released in a `finally`, and acquired OUTSIDE the database transaction (lock → transaction → commit → unlock), so another replica does not read uncommitted data.
- `@Version` (optimistic locking) remains as a second safety net.
- To respect the hexagonal architecture: `DistributedLockManager` interface in `application.lock` and `RedissonDistributedLockManagerImpl` implementation in `infrastructure.lock.impl`; the `*ServiceImpl` classes never use Redisson directly.

# Asynchronous messaging with RabbitMQ
Feign remains for synchronous queries (validating that something exists); RabbitMQ is used for everything that changes the state of another service.

## Event flows
1. **User sign-up → customer profile**
   `auth-service` publishes `UserRegisteredEvent` → `customer-service` creates the customer profile associated with the `userId`.
2. **Order creation → stock reservation (choreography saga)**
   - `order-service` creates the order in `PENDING` status and publishes `OrderCreatedEvent` (lines with productId and quantity).
   - `product-service` tries to reserve the stock of all the lines in a single transaction:
     - if there is stock → publishes `StockReservedEvent`;
     - if not → publishes `StockReservationFailedEvent` with the reason (without modifying anything).
   - `order-service` consumes the response and moves the order to `CONFIRMED` or `REJECTED`.
3. **Order cancellation → stock release (compensation)**
   Only `CONFIRMED` orders (with the stock already reserved) can be cancelled; a `PENDING` order has the saga running and the cancellation is rejected with 422.
   `order-service` publishes `OrderCancelledEvent` → `product-service` returns the reserved stock and publishes `StockReleasedEvent`.
4. **Security events**
   `auth-service` publishes `UserDisabledEvent` and `AccountLockedEvent` → `customer-service` marks the customer as inactive when the user is disabled.

## Topology
- One `topic` exchange per publishing service: `auth.events`, `order.events`, `product.events`.
- Routing keys in the format `<entity>.<action>`: `user.registered`, `user.disabled`, `order.created`, `order.cancelled`, `stock.reserved`, `stock.reservation-failed`, `stock.released`.
- One durable queue per consumer and event, named `<consumer-service>.<event>` (e.g. `product-service.order-created`).
- Each queue has its dead letter exchange and its DLQ (`<queue>.dlq`).
- Exchanges, queues and bindings declared in code (`Declarables`) in `infrastructure.messaging.config`; the names in `application.yml`, not scattered through the code.

## Message format
- JSON with `Jackson2JsonMessageConverter` and a common envelope:
  `{ "eventId": "uuid", "eventType": "order.created", "version": 1, "occurredAt": "...", "source": "order-service", "payload": { ... } }`
- Message contracts (integration DTOs, with the `MessageDTO` suffix: `OrderCreatedMessageDTO`, `StockReservedMessageDTO`...) go in a shared `events-contract` module, separate from each service's domain events, so as not to couple the domains. The common envelope is called `EventEnvelopeDTO<T>`.
- The difference between both: the **domain event** (`domain.event`, e.g. `OrderCreatedEvent`) is internal to the service and expresses something that happened in the business; the **MessageDTO** is the public JSON format that travels through RabbitMQ. `infrastructure.messaging.dto` contains the mappers between them.
- `version` field to be able to evolve the contracts without breaking consumers.

## Reliability
- **Outbox pattern (publishing):** the event is stored in an `outbox_event` table in the SAME transaction as the business change; a scheduled publisher (`@Scheduled`) sends them to RabbitMQ and marks them as sent. This way an order is never saved without its event and an event is never published for an order that was never saved.
- **Publisher confirms and returns** enabled, to know that RabbitMQ received the message and that it had a destination queue.
- **Idempotent consumption:** each consumer stores the processed `eventId`s in a `processed_event` table; if a duplicate arrives, it is ignored. RabbitMQ guarantees "at least once", so duplicates can happen.
- **Retries:** 3 attempts with exponential backoff (1 s, 2 s, 4 s) through Spring AMQP's retry; once exhausted, the message goes to the DLQ (`RejectAndDontRequeueRecoverer`).
- Non-recoverable exceptions (malformed message, validation `DomainException`) go straight to the DLQ without retrying.
- Administration endpoint (ADMIN only) to view the DLQ's messages and re-send them.
- Manual or automatic ACK after successful processing; a message is never lost through a failure midway.

## Integration with the rest of the system
- **Errors:** exceptions in listeners do not go through the `GlobalExceptionHandler`; they are handled with a custom `RabbitListenerErrorHandler` / `ErrorHandler` that logs the error with its `traceId` and decides whether to retry or send to the DLQ.
- **Tracing:** enable observation on `RabbitTemplate` and the listeners (`spring.rabbitmq.template.observation-enabled=true` and `spring.rabbitmq.listener.simple.observation-enabled=true`) so that the `traceId` travels in the message headers and Zipkin shows the full flow: gateway → order-service → RabbitMQ → product-service → RabbitMQ → order-service.
- **Security:** RabbitMQ credentials through environment variables and one user per service with permissions only over its exchanges and queues. Listeners have no JWT: they run with a system context and the audit `createdBy` is `system:<service>`.
- **Health:** the state of the RabbitMQ connection appears in Actuator.

# Distributed tracing with Zipkin
- All services (including the gateway and `auth-service`) send their traces to Zipkin with Micrometer Tracing + Brave.
- The trace context propagates automatically in the gateway's HTTP calls, in Feign and in `RestClient`/`WebClient` (W3C `traceparent` format).
- Database queries appear as spans (e.g. with `datasource-micrometer`), to see how long each paginated query takes.
- Custom spans with `@Observed` on the key methods of the `*ServiceImpl` classes.
- Sampling configurable per profile: 100 % in `dev`, 10 % in `prod` (`management.tracing.sampling.probability`).
- Structured logs (JSON in `prod`) including `traceId` and `spanId` on each line, through Spring Boot's logging pattern.
- The `traceId` returned in errors and in the `X-Trace-Id` header is the same one searched for in Zipkin.

# Observability: Prometheus + Grafana + Loki
Goal: see the whole system from Grafana, with metrics, logs and traces correlated by `traceId`.

## Metrics (Prometheus)
- All services expose `/actuator/prometheus` (protected: only accessible from the internal Docker network, not through the gateway).
- Prometheus discovers the replicas automatically through Eureka (`eureka_sd_configs`) or Docker DNS, without listing instances by hand.
- Common tags on all metrics: `application`, `instance` and `environment`.
- Latency histograms (`http.server.requests` with p50/p95/p99 percentiles) enabled.
- **Exemplars** enabled so that, from a point on a latency chart, you can jump to the specific trace in Zipkin.
- Technical metrics that must appear: requests per second, latency, 4xx/5xx errors per endpoint, JVM (heap, GC, threads), Hikari connection pool, state of the Resilience4j circuit breakers, gateway rate limiting, RabbitMQ queues (pending messages, in the DLQ, consumers), pending events in the outbox and wait time of the distributed locks.
- **Custom business metrics** with `MeterRegistry` (defined behind an interface in `application.metrics` and implemented in `infrastructure.metrics.impl`): orders created, confirmed, rejected and cancelled; insufficient stock; user registrations; successful and failed logins; locked accounts.

## Logs (Loki)
- Logs in structured JSON format (Spring Boot's native structured logging) with `traceId`, `spanId`, `application`, `instance` and level.
- Promtail (or Grafana Alloy) collects the logs of all Docker containers and sends them to Loki, with labels per service and replica.
- Grafana configured to jump from a log to its trace in Zipkin (by `traceId`) and from a trace to its logs.
- Sensitive data (passwords, tokens, full personal data) is never logged.

## Grafana
- Prometheus, Loki and Zipkin datasources provisioned automatically through files (`grafana/provisioning`), with no manual configuration.
- Dashboards provisioned as JSON in the repository:
  1. **System overview:** state of each service and replica, requests/s, errors and p95 latency per service.
  2. **By microservice:** slowest endpoints, errors by HTTP code, JVM, Hikari and circuit breakers.
  3. **Load distribution:** requests per replica (to demonstrate the balancing and what happens when a replica goes down).
  4. **Messaging:** RabbitMQ queues, DLQ, outbox and order saga.
  5. **Business:** orders by status, insufficient stock, registrations and logins.
  6. **Security:** failed logins, locked accounts, 401/403/429 responses.
- Sample alerts: 5xx error rate > 5 % for 5 min, p95 latency > 1 s, messages in a DLQ, open circuit breaker, a downed replica.
- API versioning: `/api/v1/...`.

# Tests

## Unit tests with JUnit 5 and Mockito (key requirement)
Tools: JUnit 5 (Jupiter), Mockito (`mockito-junit-jupiter`) and AssertJ for assertions. Unit tests do NOT start the Spring context (no `@SpringBootTest`), use no database, network or RabbitMQ, and must run in milliseconds.

### What is unit tested
| Layer / class | What to verify | How |
|---|---|---|
| `domain.model` | Business rules of the entities (e.g. `Order.cancel()` only if `PENDING`, `Product.reserveStock()` throws `BusinessRuleException` without stock, order total calculation) | Plain JUnit, no mocks |
| `domain.exception` | That each exception carries its `ErrorCode` and its parameters | Plain JUnit |
| `application.service.impl` (`*ServiceImpl`) | All the use case logic: happy paths, errors and edge cases | Mockito: mocks of the `domain.repository` and `domain.messaging` interfaces |
| `infrastructure.persistence.repository.impl` (`*RepositoryImpl`) | Conversion `PageQuery` → `Pageable` (page, size, sort) and `Page<Entity>` → `PageResult<Model>` | Mockito: mock of the `*JpaRepository` + `PageImpl` as the response |
| MapStruct mappers | That all fields map correctly both ways, including nulls | Real instance with `Mappers.getMapper(...)` |
| `infrastructure.exception` | `ErrorCode` → `HttpStatus` mapping and building the `ErrorResponseDTO` with the translated message | Mockito: mock of `MessageSource` |
| `infrastructure.messaging.listener` | That the listener converts the message and calls the right service, and ignores duplicate events | Mockito: mock of the service interface and of the `processed_event` repository |
| `infrastructure.messaging.publisher.impl` | That it sends to the right exchange and routing key with a well-formed envelope | Mockito: mock of `RabbitTemplate` + `ArgumentCaptor` |
| Outbox publisher | That it publishes the pending events, marks them as sent and does not mark them if the send fails | Mockito |
| Feign `ErrorDecoder` | That each remote HTTP code translates to the right domain exception | JUnit + a hand-built `Response` |
| `auth-service` | Lockout after N attempts, counter reset, password policy (`@StrongPassword`), password history, token revocation on password change, `OAuth2TokenCustomizer` adds roles and permissions to the JWT | Mockito (mock of `PasswordEncoder`, repositories, `Clock`) |
| Custom validators | Valid and invalid cases | JUnit + `@ParameterizedTest` |

### Conventions
- One test class per class under test, in the same package inside `src/test/java`, with the `Test` suffix (e.g. `ProductServiceImplTest`).
- `@ExtendWith(MockitoExtension.class)`, `@Mock` for the dependencies and `@InjectMocks` for the class under test (constructor injection).
- **Given / When / Then** structure (Arrange / Act / Assert) with comments separating each block.
- Descriptive names with the pattern `method_whenCondition_shouldResult` and `@DisplayName` in English, e.g. `@DisplayName("findById throws ResourceNotFoundException if the product does not exist")`.
- `@Nested` to group tests by method (`class FindById { ... }`, `class Create { ... }`).
- Assertions with AssertJ: `assertThat(...)`, `assertThatThrownBy(...).isInstanceOf(...).hasFieldOrPropertyWithValue("errorCode", ...)`.
- Verify important interactions with `verify(...)`, `never()`, `times(n)` and `verifyNoMoreInteractions(...)` when it adds value (e.g. that nothing is saved or published if a validation fails).
- `ArgumentCaptor` to check what is saved or published (e.g. that the `OrderCreatedEvent` carries the right lines).
- `@ParameterizedTest` with `@CsvSource` / `@MethodSource` / `@EnumSource` for cases with many variants (validations, error code mapping, pagination boundary values).
- No `Thread.sleep`, system dates or randomness: inject a `Clock` and use `Clock.fixed(...)` in tests.
- Test data with *builder* / *Object Mother* classes (`ProductMother.aProduct()`, `OrderMother.aPendingOrder()`) in a `testdata` package, so as not to repeat object construction.
- Mockito in strict mode (default with `MockitoExtension`): no unnecessary stubs.
- Do not mock what is not yours when it can be avoided (e.g. use a real `PageImpl` instead of mocking `Page`), nor domain objects.

### Minimum cases per `*ServiceImpl`
- Create: saves correctly; throws `DuplicateResourceException` if it already exists; saves and publishes nothing if a rule fails.
- Find by id: returns the model; throws `ResourceNotFoundException` if it does not exist.
- Paginated list: passes the filters and the `PageQuery` as they are to the repository and returns the `PageResult`; empty page; last page; no filters.
- Update: updates only the allowed fields; throws 404 if it does not exist.
- Delete: does the soft delete (`active = false`), not a physical delete.
- In `order-service`: creating an order saves the event in the outbox; a missing customer or product (Feign) throws the right exception; the saga's responses change the status to `CONFIRMED` / `REJECTED`; cancelling an order that is not `CONFIRMED` throws `BusinessRuleException` (422 `ORDER_NOT_CANCELLABLE`).

Example of the expected style:
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
        @DisplayName("throws ResourceNotFoundException if the product does not exist")
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

### Coverage
- JaCoCo configured in the parent `pom.xml`, with an aggregated report of all modules.
- Minimum coverage of 80 % of lines and 70 % of branches in `domain` and `application`; the build (`mvn verify`) fails if not reached.
- Configuration classes, DTOs, JPA entities, `*Application` classes and MapStruct-generated code are excluded from coverage.
- Unit tests with Surefire (`*Test`) and integration tests with Failsafe (`*IT`), to run them separately: `mvn test` only runs the unit tests.

## Integration and slice tests
- `@DataJpaTest` + Testcontainers verifying the pagination (totalElements, totalPages, sort).
- `@WebMvcTest` verifying that each type of exception returns the right HTTP code and the common JSON format, with no internal Java data.
- Security tests with `spring-security-test` (`jwt()`): 401 without a token, 403 without permissions and 200 with the right role.
- i18n tests: the same error returns the message in English or Spanish according to `Accept-Language`, and the `code` does not change.
- Extra security tests: lockout after 5 failed attempts (423), unlock, audit log record and rejection of a revoked token (401).
- Gateway rate limiting test (429 on exceeding the limit, with Redis Testcontainers).
- Messaging tests with RabbitMQ Testcontainers: publishing from the outbox, correct consumption, duplicate message ignored (idempotency), retries and moving to the DLQ. Use Awaitility to wait for asynchronous results.
- Saga test: order with stock → `CONFIRMED`; order without stock → `REJECTED` without modifying the stock; cancellation → stock released.
- Stock concurrency test: N threads reserving a product's last stock at once (with Redis and PostgreSQL Testcontainers); only one gets it and the stock never goes negative.
- ShedLock test: two scheduler instances on the same database; the task runs only once.
- Unit tests (Mockito) of the `*ServiceImpl` classes using `DistributedLockManager`: the lock is released even if the operation fails, and the right exception is thrown if it is not acquired.
- Test that `/actuator/prometheus` exposes the business metrics with their tags.
- An end-to-end integration test: get a token from `auth-service` → create a product → create a customer → create an order through the gateway.

# Deliverables
1. Full folder tree of all modules.
2. Parent `pom.xml` and that of each module with explicit versions.
3. Full code of `auth-service` and `product-service` (all layers), including the pagination flow and exception handling end to end.
4. Code of `customer-service`, `order-service`, `api-gateway` and `discovery-service` (you can summarize the repetitive parts, but without omitting security, exceptions, pagination, the full POST/GET/PUT/DELETE CRUD of each resource or the `@Transactional` examples).
5. Flyway migrations per service with sample data (≥ 50 records in the paginated tables).
6. `application.yml` per service and profile, and the `messages.properties` / `messages_es.properties` files with all the error codes.
7. `Dockerfile` per service and `docker-compose.yml` (the 6 services with their replicas + PostgreSQL with one database per service + Redis + Zipkin + RabbitMQ + Prometheus + Loki + Promtail + Grafana, with volumes, healthchecks and startup order). Optionally, separate observability into a `docker-compose.observability.yml`.
8. Prometheus configuration (`prometheus.yml` and alert rules), Promtail/Alloy configuration, and Grafana provisioning (datasources and dashboards in JSON).
9. `curl` examples: getting the token, paginated calls, examples of each error (400, 401, 403, 404, 409, 422, 423, 429, 500, 503) in both languages, logout/revocation, how to find a request in Zipkin (http://localhost:9411) by its `traceId` and how to follow an order through the queues in the RabbitMQ console (http://localhost:15672).
10. README with the instructions to start and try the system, the URLs of all consoles (Grafana http://localhost:3000, Prometheus http://localhost:9090, Zipkin, RabbitMQ, Eureka, Swagger) and a **"Demo scenarios"** section with step-by-step scripts, at a minimum:
    - Scale `product-service` to 3 replicas and see in Grafana how the traffic is distributed; shut one down and check that no request is lost.
    - Launch many concurrent orders on a product's last stock and check that the stock never goes negative.
    - Follow an order end to end: business dashboard → log in Loki → trace in Zipkin.

# Way of working
- Before the code, show with Mermaid: (a) the overall architecture, (b) the OAuth2 flow for getting and validating the token, and (c) the flow of a paginated request: Gateway → Controller → ProductService → ProductServiceImpl → ProductRepository → ProductRepositoryImpl → ProductJpaRepository → PostgreSQL.
- Generate the code in phases and wait for my confirmation between them: (1) structure, `common-lib`, exceptions and i18n, (2) `auth-service` with OAuth2 and the extra security, (3) full `product-service` with pagination, (4) `customer-service` and `order-service` with Feign, (5) messaging with RabbitMQ (outbox, order saga and DLQ), (6) gateway with rate limiting, Eureka, Zipkin and Docker, (7) horizontal scaling (replicas, ShedLock and distributed locks), (8) observability with Prometheus, Loki and Grafana, (9) integration tests, end-to-end tests and coverage.
- Unit tests with JUnit and Mockito are NOT left for the end: each phase delivers its code along with its unit tests.
- Briefly explain the important design decisions.

# Extension: deployment on Kubernetes
After the 9 phases, the system is also deployed on Kubernetes, in `k8s/`, without leaving Docker Compose (which remains the development environment). One pull request per phase:
0. Tools and local cluster: kind with 1 control-plane and 2 workers, and ports 80 and 443 mapped (`k8s/kind/cluster.sh`).
1. The system on Kubernetes with Eureka: Helm chart `k8s/charts/showcase` with Deployment, Service, ConfigMap and Secret per service (including the JWT RSA key), `/actuator/health` probes, requests and limits, and replicas (product 3, order 2). PostgreSQL, Redis and RabbitMQ with the official images and the same user and database scripts as Compose. The existing e2e tests pass against the cluster.
2. Observability on the cluster: kube-prometheus-stack, Loki and Grafana Alloy as a DaemonSet, and Tempo instead of Zipkin (persistent). The Compose dashboards and alerts are reused, alerts for pods in CrashLoop or not ready are added, and searching by `traceId` still works.
3. No Eureka on Kubernetes: Spring Cloud Kubernetes for the gateway (`lb://`) and the Feign clients. Compose keeps `discovery-service`.
4. Production: Ingress (Traefik) with HTTPS from cert-manager on `showcase.localhost`, gateway with trusted proxies and `RATE_LIMIT_TRUSTED_PROXY_HOPS=1`, HPA on product and order (demonstrated under load), PodDisruptionBudget, NetworkPolicies (only the gateway receives external traffic and each service only talks to what it needs), rolling update and `helm rollback`, and non-root containers with a read-only file system.
5. CI and documentation: GitHub Actions job that creates kind, installs the chart and runs the e2e tests; `docs/kubernetes.md` (install, scale, upgrade, roll back, investigate failures with kubectl and Grafana by `traceId`); and `values-prod.yaml` for managed databases, Redis and RabbitMQ.
