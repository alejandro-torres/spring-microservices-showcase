# Spring Microservices Showcase

A microservices system in Java 21 and Spring Boot 4 with **hexagonal architecture**, built as a demonstration of what can be done with distributed systems in Java.

> Developed in phases, each in its own pull request: from the initial structure to observability and end-to-end tests, and then deployment on Kubernetes.

It can be started in two ways: with **Docker Compose** for day-to-day development, or on a local **Kubernetes** cluster (kind) with a Helm chart, as it would be deployed in production.

## What's included

- **4 business microservices:** `auth-service` (users, roles and OAuth2), `product-service`, `customer-service` and `order-service`.
- **Infrastructure:** API Gateway (Spring Cloud Gateway) and service discovery (Eureka on Docker Compose, the Kubernetes API with Spring Cloud Kubernetes on the cluster).
- **Persistence:** JPA + PostgreSQL, one database per service, migrations with Flyway.
- **Paginated listings** with `Page` and **dynamic filters** (`?filter=field:operator:value`).
- **Messaging** with RabbitMQ: outbox pattern, order saga and dead-letter queues (DLQ).
- **Horizontal scaling:** several replicas per service, ShedLock and distributed locks with Redis.
- **Observability:** metrics with Prometheus, logs with Loki and Grafana Alloy, traces with Zipkin (Compose) or Tempo (Kubernetes) and dashboards in Grafana, all linked by the `traceId`.
- **Kubernetes:** Helm chart with HTTPS Ingress (Traefik and cert-manager), autoscaling (HPA), PodDisruptionBudgets, NetworkPolicies, non-root read-only containers, and rollback with Helm.
- **Errors** with standard HTTP codes, messages in English and Spanish and no internal details exposed.
- **Tests:** unit tests with JUnit 5 and Mockito, integration tests with Testcontainers and end-to-end tests against the full system, also in CI on a kind cluster.

## Build and run the tests

Requirements: **JDK 21** and **Docker** (for the Testcontainers integration tests). Maven is not needed: the project includes Maven Wrapper.

```bash
./mvnw verify
```

On Windows (PowerShell or CMD) use `mvnw.cmd verify`.

| What | How |
|---|---|
| Unit tests only (`*Test`, JUnit 5 + Mockito, no Spring or Docker; seconds) | `./mvnw test` |
| Unit and integration tests (`*IT`, with real PostgreSQL, Redis and RabbitMQ in Testcontainers) | `./mvnw verify` |
| Coverage | `./mvnw verify` fails if `domain` and `application` drop below 80 % of lines or 70 % of branches. Per-module report at `<module>/target/site/jacoco/index.html` and aggregated report at `coverage-report/target/site/jacoco-aggregate/index.html` (excluding configuration, DTOs, entities, `*Application` classes and MapStruct code) |
| End to end against the system in Docker | With the system running (`docker compose up -d --build`): `./mvnw -Pe2e -pl e2e-tests verify` |
| End to end against Kubernetes | With the kind cluster deployed: `k8s/e2e.sh` (through https://showcase.localhost) |

The end-to-end tests ([e2e-tests](e2e-tests/src/test/java/com/showcase/e2e/ShowcaseE2EIT.java)) do what a real client does, all through the gateway: login with authorization code + PKCE on the auth-service form, creation of a product and a customer, an order the saga confirms by deducting the stock, a cancellation that returns it, and an out-of-stock order rejected with 422.

## Structure

| Module | Contents |
|---|---|
| `common-lib` | Domain exceptions, pagination (`PageQuery`/`PageResult`), dynamic filters (parser and JPA `Specification`), global error handling and messages in English and Spanish, and the common messaging (outbox, idempotent consumption, retries and DLQ) |
| `events-contract` | RabbitMQ message contracts: the common `EventEnvelopeDTO` envelope and one `*MessageDTO` per event |
| `auth-service` | Users, roles and permissions, and the OAuth2 authorization server that issues the JWTs (port 9000) |
| `product-service` | Catalog of products and categories, and stock management (port 8081) |
| `customer-service` | Customers and their addresses (port 8082) |
| `order-service` | Orders and their lines; queries customers and products with Feign and Resilience4j (port 8083) |
| `discovery-service` | Service registry with Eureka (port 8761) |
| `api-gateway` | Single entry point: validates the token, routes, rate limits requests and aggregates Swagger (port 8080) |
| `coverage-report` | Aggregated JaCoCo coverage report of all modules |
| `e2e-tests` | End-to-end tests against the full system, on Docker or Kubernetes (`e2e` profile) |

| Folder | Contents |
|---|---|
| `docker/` | Docker Compose configuration: PostgreSQL and RabbitMQ scripts (one database and one user per service), Prometheus and its alerts, Loki, Alloy and Grafana (datasources and dashboards) |
| `k8s/` | Kubernetes: kind cluster (`kind/`), platform (`platform/`: Traefik, cert-manager, metrics-server), Helm chart (`charts/showcase`, with `values-prod.yaml`), observability (`observability/`), load for autoscaling (`load/`) and the `deploy.sh` and `e2e.sh` scripts |
| `docs/` | Specification, UML diagrams, curl examples and the Kubernetes operations guide |
| `.github/workflows/` | CI: `ci.yml` (build and tests) and `kubernetes.yml` (deployment on kind and e2e) |

## Start everything with Docker

Requirement: Docker with Compose. The images are compiled inside Docker, so you do not even need the JDK.

```bash
docker compose up -d --build
```

The first run takes a few minutes (it downloads the dependencies and compiles the 6 services). Passwords come from a `.env` file (copy [.env.example](.env.example)); without it, development values are used.

| Console | URL |
|---|---|
| API (gateway) | http://localhost:8080 |
| Swagger UI of all services | http://localhost:8080/swagger-ui.html |
| Eureka | http://localhost:8761 |
| RabbitMQ | http://localhost:15672 (user `admin`, password `admin-dev` or the one in `.env`) |
| Zipkin | http://localhost:9411 |

Only the gateway exposes the API; the services publish no ports. There is one PostgreSQL database and user per service, and one RabbitMQ user per service with permissions only over its exchanges and queues ([docker/rabbitmq/setup-users.sh](docker/rabbitmq/setup-users.sh)). The default profile is `dev` (sample data and 100 % of the traces); with `SPRING_PROFILES_ACTIVE=prod`, JSON logs and 10 % of the traces.

Tokens are requested through the gateway (`http://localhost:8080/oauth2/token`, login at `http://localhost:8080/login`), which is the issuer (`iss`) of the JWTs. The gateway forwards to the services the `X-Forwarded-*` headers of clients coming from the machine itself or from private networks (`GATEWAY_TRUSTED_PROXIES`); with a load balancer in front, set its network there and `RATE_LIMIT_TRUSTED_PROXY_HOPS=1` so that rate limiting uses the real client IP.

[docs/curl-examples.md](docs/curl-examples.md) has a full walkthrough with `curl`: how to get a user token, listings with filters, each error code, logout and how to follow an order in Zipkin and RabbitMQ.

## Start everything on Kubernetes

The same system is deployed on a local kind cluster (3 nodes inside Docker) with a Helm chart. Requirements: Docker Desktop with 12 GB of memory or more, kubectl, kind and Helm.

```bash
k8s/kind/cluster.sh create          # 3-node cluster
k8s/platform/install.sh             # Traefik (Ingress), cert-manager (HTTPS) and metrics-server
k8s/observability/install.sh        # optional: Prometheus, Grafana, Loki, Alloy and Tempo
k8s/deploy.sh                       # builds the images, loads them into kind and installs the chart
k8s/e2e.sh                          # e2e through the Ingress
```

| Console | URL |
|---|---|
| API and Swagger | https://showcase.localhost/swagger-ui.html (certificate from a local CA; see [k8s/README.md](k8s/README.md)) |
| Grafana | `kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80` → http://localhost:3000 |
| RabbitMQ | `kubectl -n showcase port-forward svc/rabbitmq 15672` → http://localhost:15672 |

Differences from Docker Compose:
- There is no Eureka: replicas are discovered with the Kubernetes API.
- There is no Zipkin: traces go to Tempo and are viewed in Grafana.
- product-service and order-service autoscale (3-6 and 2-4 replicas).
- Only the gateway receives outside traffic, through the HTTPS Ingress.
- NetworkPolicies limit who each pod talks to.

What is deployed and why is in [k8s/README.md](k8s/README.md); how to scale, upgrade, roll back and investigate failures, in [docs/kubernetes.md](docs/kubernetes.md).

## Start the services one by one (development)

To debug a service from the IDE, each one starts with `spring-boot:test-run`: it brings up its PostgreSQL (and Redis in auth-service) in Docker with Testcontainers. What they share is started once:

```bash
docker run -d --name showcase-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```
```bash
./mvnw -pl discovery-service spring-boot:run
```

They all connect to that RabbitMQ (`localhost:5672`, user `guest`) and register in the Eureka at `localhost:8761`, so they find each other. Traces are sent to `localhost:9411` if a Zipkin is running (`docker run -d -p 9411:9411 openzipkin/zipkin:3`); otherwise, they are discarded. The integration tests need none of this: each starts its own containers.

## Start auth-service locally

There is no need to install PostgreSQL or Redis: the service starts with both in Docker (Testcontainers) and the `dev` profile, which loads sample data. It needs the shared RabbitMQ and Eureka from the previous section.

```bash
./mvnw install -DskipTests
```
```bash
./mvnw -pl auth-service spring-boot:test-run -Dspring-boot.run.profiles=dev
```

### Authentication (OAuth2)

The API requires a JWT in the `Authorization: Bearer <token>` header. auth-service issues it:

| Client | Flow | What for |
|---|---|---|
| `web-client` | `authorization_code` with PKCE, no secret | Browser applications: the user logs in on the auth-service form |
| `internal-service` | `client_credentials` | Calls between microservices (`internal` scope) |

A user's token lasts 15 minutes and carries their roles and permissions (`roles`, `permissions`), which each service checks with `@PreAuthorize`. The public keys to validate it are at `/oauth2/jwks` and the configuration at `/.well-known/openid-configuration`.

To try the API from the terminal, the quickest way is a service token (in the `dev` profile, the `internal-service` secret is `internal-dev-secret`):

```bash
curl -u internal-service:internal-dev-secret -d grant_type=client_credentials -d scope=internal http://localhost:9000/oauth2/token
```

That token identifies the service, not a user, so it has no management permissions. To act as `admin` you have to follow the `authorization_code` flow from a browser or a client such as Postman (with PKCE and `redirect_uri` `http://127.0.0.1:3000/callback`).

Other public endpoints:

```bash
curl -X POST http://localhost:9000/api/v1/auth/register -H "Content-Type: application/json" -d "{\"username\":\"newuser\",\"email\":\"newuser@example.com\",\"password\":\"Secret-123\",\"firstName\":\"New\",\"lastName\":\"User\"}"
```
```bash
curl http://localhost:9000/oauth2/jwks
```

In other environments, the RSA keys (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`) and the `internal-service` secret (`INTERNAL_CLIENT_SECRET`) are passed through environment variables. With no keys configured, a pair is generated in memory (tokens stop being valid on restart).

### Account protection

| Measure | Behavior |
|---|---|
| Lockout after failed attempts | 5 wrong passwords in a row lock the account for 15 minutes (configurable in `showcase.security.login`). The lock expires on its own or an ADMIN removes it with `PUT /api/v1/users/{id}/unlock` |
| Password policy | 8 to 72 characters, no spaces, with uppercase, lowercase, digits and symbols. The last 3 cannot be reused |
| Password change | `PUT /api/v1/users/me/password` with the current and new password |
| Logout | `POST /api/v1/auth/logout` revokes the token instantly |
| Automatic revocation | Changing the password, deactivating or deleting a user or removing a role invalidates all their previous tokens |
| Audit log | Logins, lockouts, role and password changes and revocations, with IP, user agent and traceId. An ADMIN views it at `GET /api/v1/security-audit` |

Revoked tokens are stored in Redis with automatic expiry. If Redis does not respond, the API rejects tokens (a 401 is preferable to accepting one that could be revoked).

### Sample data (`dev` profile only)

- User `admin` (ADMIN and USER roles) and 60 users `user01`…`user60`; every 7th is disabled and every 10th deleted.
- They all have the development password `Showcase-Dev-2026!`. This data is never loaded outside the `dev` profile.

### API

| Resource | Endpoints | Permissions |
|---|---|---|
| Registration | `POST /api/v1/auth/register` with `username`, `email`, `password`, `firstName` and `lastName` (creates a user with the USER role; customer-service creates their customer profile on receiving `user.registered`) | Public |
| Current user | `GET /api/v1/users/me` | Any authenticated user |
| Users | `POST /api/v1/users`, `GET /api/v1/users/{id}`, `GET /api/v1/users`, `PUT /api/v1/users/{id}`, `DELETE /api/v1/users/{id}`, `POST` and `DELETE /api/v1/users/{id}/roles/{roleId}` | `user:read` / `user:write` |
| Roles | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` on `/api/v1/roles` | `role:read` / `role:write` |
| Permissions | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` on `/api/v1/permissions` | `permission:read` / `permission:write` |

`includeInactive=true` (to see deleted records) is only allowed for the ADMIN role.

Listings accept `page`, `size` (maximum 100), `sort`, `direction`, `includeInactive` and `filter=field:operator:value` filters:

| Resource | Filters | Sorting |
|---|---|---|
| Users | `username`, `email` (eq, like, startsWith), `enabled` (eq), `role` (eq, in), `createdAt` (gte, lte, between) | `id`, `username`, `email`, `createdAt`, `updatedAt` |
| Roles | `name` (eq, like, startsWith), `permission` (eq, in), `createdAt` | `id`, `name`, `createdAt` |
| Permissions | `name` (eq, like, startsWith), `createdAt` | `id`, `name`, `createdAt` |

## Start product-service locally

With PostgreSQL in Docker and a sample catalog (8 categories and 64 products):

```bash
./mvnw -pl product-service spring-boot:test-run -Dspring-boot.run.profiles=dev
```

The API validates tokens with auth-service's public key (`AUTH_JWK_SET_URI`, by default `http://localhost:9000/oauth2/jwks`), so auth-service must be running to get a token.

| Resource | Endpoints | Permissions |
|---|---|---|
| Products | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` on `/api/v1/products` | `product:read` / `product:write` |
| Stock | `PUT /api/v1/products/{id}/stock` with `{"delta": -3}` (manual adjustment, never leaves the stock negative) | ADMIN role |
| Categories | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` on `/api/v1/categories`, and `GET /{id}/products` | `product:read` / `product:write` |

| Resource | Filters | Sorting |
|---|---|---|
| Products | `name` (eq, like, startsWith), `price` (eq, gt, gte, lt, lte, between), `stock` (eq, gt, gte, lt, lte), `categoryId` (eq, in), `categoryName` (eq, like), `createdAt` (gte, lte, between) | `id`, `name`, `price`, `stock`, `createdAt`, `updatedAt` |
| Categories | `name` (eq, like, startsWith), `createdAt` | `id`, `name`, `createdAt` |

Example: laptops between 500 and 1500 € in stock, from most to least expensive:

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/v1/products?filter=categoryName:like:laptop&filter=price:between:500,1500&filter=stock:gt:0&sort=price&direction=desc"
```

## Start customer-service locally

With PostgreSQL in Docker and 50 sample customers with their addresses:

```bash
./mvnw -pl customer-service spring-boot:test-run -Dspring-boot.run.profiles=dev
```

It listens on port 8082 and validates tokens just like product-service.

| Resource | Endpoints | Permissions |
|---|---|---|
| Customers | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` on `/api/v1/customers`, and `GET /by-user/{userId}` (profile of the auth-service user) | `customer:read` / `customer:write` |
| Addresses | `POST /{id}/addresses`, `DELETE /{id}/addresses/{addressId}` and `PUT /{id}/addresses/{addressId}/main` on `/api/v1/customers` | `customer:write` |

Rules: the email (case-insensitive) and the auth-service user are unique; a customer has at most 5 addresses and, if they have any, exactly one is the primary (the first one added, and if deleted, the next one).

| Resource | Filters | Sorting |
|---|---|---|
| Customers | `firstName`, `lastName`, `email` (eq, like, startsWith), `city` (eq, like; any of their addresses), `userId` (eq), `createdAt` (gte, lte, between) | `id`, `firstName`, `lastName`, `email`, `createdAt` |

## Start order-service locally

With PostgreSQL in Docker and 120 sample orders (of the sample customers and products of the other services):

```bash
./mvnw -pl order-service spring-boot:test-run -Dspring-boot.run.profiles=dev
```

It listens on port 8083. To create orders it queries customer-service (shipping address) and product-service (name, price and stock) with Feign, so both must be running. Feign looks them up in Eureka and distributes the calls among their replicas; `CUSTOMER_SERVICE_URL` and `PRODUCT_SERVICE_URL` set a specific URL (for testing only).

| Resource | Endpoints | Permissions |
|---|---|---|
| Orders | `POST`, `GET /{id}`, `GET`, `PUT /{id}` (changes the shipping address of an order that is not closed) on `/api/v1/orders` | `order:read` / `order:write` |
| A customer's orders | `GET /api/v1/orders/by-customer/{customerId}` (paginated, from most recent to oldest) | `order:read` |
| Cancel | `PUT /api/v1/orders/{id}/cancel` (confirmed orders only; a pending order responds 422 `ORDER_NOT_CANCELLABLE`) | `order:write` |
| Delete | `DELETE /api/v1/orders/{id}` (soft delete of cancelled or rejected orders) | `order:write` and ADMIN role |

The request only carries products and quantities: the name and price are copied from product-service and the shipping address is the customer's primary one, so that the order does not change if the catalog later does.

| Resource | Filters | Sorting |
|---|---|---|
| Orders | `status` (eq, in), `customerId` (eq), `productId` (eq; orders containing the product), `total` (gte, lte, between), `createdAt` (gte, lte, between) | `id`, `total`, `status`, `createdAt` |

### Calls to other services

| Situation | Response |
|---|---|
| The customer or a product does not exist | 404 (`CUSTOMER_NOT_FOUND`, `PRODUCT_NOT_FOUND`) |
| Not enough stock | 422 (`INSUFFICIENT_STOCK`); it is a prior check to fail fast, the real reservation is done by the saga |
| The other service fails or takes more than 3 s | 503 (`SERVICE_UNAVAILABLE`) with no details of the remote error |
| Several failures in a row | The circuit breaker (Resilience4j) opens for 10 s and responds 503 instantly, without waiting for the timeout |

The user's JWT is forwarded on each call, so their own permissions apply in the other service. The calls are made before opening the transaction, so as not to hold a database connection while waiting for the response.

## Messaging with RabbitMQ

Feign is used for synchronous queries (checking that something exists); everything that changes the state of another service goes through RabbitMQ.

### Flows

| Flow | Events |
|---|---|
| User sign-up → customer profile | auth `user.registered` → customer creates the customer (no addresses) |
| User deactivated or deleted | auth `user.disabled` → customer marks the customer as inactive |
| Account locked | auth `account.locked` → customer records it in its log (the lock is temporary and does not change the customer) |
| Order creation saga | order creates the `PENDING` order and publishes `order.created` → product reserves the stock of all the lines or none and responds `stock.reserved` or `stock.reservation-failed` → order moves the order to `CONFIRMED` or `REJECTED` (with the reason in `rejectionReason`) |
| Cancellation (compensation) | order cancels a `CONFIRMED` order and publishes `order.cancelled` → product returns the stock and responds `stock.released` |

An order's lines are fixed on creation: they are what the saga reserves. That is why `PUT /orders/{id}` only changes the shipping address.

### Topology

- One durable `topic` exchange per publishing service: `auth.events`, `order.events` and `product.events`. The routing key is the event type (`<entity>.<action>`).
- One durable queue per consumer and event (`<service>.<event>`, e.g. `product-service.order-created`). All replicas of a service listen to the same queue, so each message is processed by only one.
- Each queue sends its rejected messages to the service's DLX (`<service>.dlx`), which leaves them in its DLQ (`<queue>.dlq`).
- The names are in `application.yml` (`showcase.messaging`); exchanges, queues and bindings are declared at startup.

All messages carry the same JSON envelope:

```json
{ "eventId": "uuid", "eventType": "order.created", "version": 1, "occurredAt": "2026-10-14T10:00:00Z",
  "source": "order-service", "payload": { "orderId": 7, "customerId": 3, "lines": [{ "productId": 10, "quantity": 2 }] } }
```

### Reliability

| Problem | Solution |
|---|---|
| Saving the change and failing to publish the event (or vice versa) | **Outbox**: the event is stored in the `outbox_event` table in the same transaction as the change; a scheduled publisher sends it later |
| Not knowing whether RabbitMQ received the message | **Publisher confirms and returns**: an event only becomes `SENT` when RabbitMQ confirms it and it had a destination queue. If RabbitMQ is down, the event waits in the table |
| Duplicate messages (RabbitMQ delivers "at least once") | **Idempotent consumption**: each consumer stores the `eventId` in `processed_event` in the same transaction as its change and discards repeats |
| Transient failures (database down, concurrency conflict) | **Retries**: 3 retries waiting 1 s, 2 s and 4 s; then, to the DLQ |
| Failures that retrying will not fix (malformed JSON, missing order, repeated email) | Straight to the **DLQ**, without retries |

### DLQ administration (ADMIN only)

In each service that consumes events (customer, product and order):

| Endpoint | What it does |
|---|---|
| `GET /api/v1/admin/dead-letters` | Lists the service's DLQs with their original queue and how many messages they hold |
| `GET /api/v1/admin/dead-letters/{dlq}/messages?limit=10` | Shows the first messages (with the rejection reason) without taking them out of the DLQ |
| `POST /api/v1/admin/dead-letters/{dlq}/replay?max=100` | Returns the messages to their original queue, once the problem has been fixed |

## API Gateway

The whole API is used through `http://localhost:8080`. The gateway discovers each service's replicas in Eureka (`lb://product-service`...) and distributes requests among them. On Kubernetes there is no Eureka: replicas are read from the Kubernetes API with Spring Cloud Kubernetes ([k8s/README.md](k8s/README.md)).

| Routes | Service |
|---|---|
| `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/roles/**`, `/api/v1/permissions/**`, `/api/v1/security-audit/**`, `/login`, `/oauth2/**`, `/.well-known/**` | auth-service |
| `/api/v1/products/**`, `/api/v1/categories/**` | product-service |
| `/api/v1/customers/**` | customer-service |
| `/api/v1/orders/**` | order-service |
| `/api/v1/admin/{service}/dead-letters/**` | That service's DLQs (the gateway strips the prefix) |

| Function | Behavior |
|---|---|
| Authentication | Validates the JWT's signature and expiry with auth-service's JWKS and rejects revoked tokens (logout, password change, deactivated user) by checking the same Redis list as auth-service. Public: registration, login, OAuth2 endpoints, Swagger and health. The token is forwarded to the service, which applies its permissions |
| Rate limiting | Token bucket in Redis (shared by all replicas): 20 requests per second with bursts of 40 per user (JWT `sub`) or per IP if there is no token; the login (`POST /login` and `/oauth2/token`), 5 per minute per IP. On exceeding it, **429** with `Retry-After` and the common error format |
| Circuit breaker | One per service (Resilience4j): a call taking more than 5 s counts as a failure; with half of the last 20 failed it opens for 10 s and responds **503** instantly |
| Errors | The gateway's 401, 403, 404, 429 and 503 in the same `ErrorResponseDTO` and the same languages as the services |
| Trace | `X-Trace-Id` header on all responses: it is the `traceId` to search for in Zipkin (or in Tempo, on Kubernetes) |
| CORS | Centralized in the gateway (`CORS_ALLOWED_ORIGINS`) |

The limits are adjusted with environment variables (`RATE_LIMIT_REPLENISH_RATE`, `RATE_LIMIT_BURST_CAPACITY`, `LOGIN_RATE_LIMIT_*`, `GATEWAY_TIMEOUT`).

## Horizontal scaling

With `docker compose up`, 3 replicas of product-service and 2 of order-service start (`PRODUCT_SERVICE_REPLICAS`, `ORDER_SERVICE_REPLICAS`, or `docker compose up -d --scale product-service=5`). Nothing is kept in memory between requests: what is shared lives in PostgreSQL or Redis.

| Piece | How it works with several replicas |
|---|---|
| Discovery and balancing | Each replica registers in Eureka with a unique id; the gateway and Feign distribute requests among the live replicas. The list refreshes every 5 s and a replica that stops sending heartbeats leaves the registry in 15 s |
| `X-Instance-Id` | Each response states which replica served it (the same id shown in Eureka) |
| Graceful shutdown | A replica shutting down finishes in-flight requests and messages (up to 30 s) and the gateway retries `GET`s on another replica: no request is lost |
| RabbitMQ queues | All replicas listen to the same queue (*competing consumers*): each message is processed by only one, with `prefetch` 10 and 1 to 4 consumers per replica |
| Scheduled tasks | ShedLock (`shedlock` table in each database): each task is run by a single replica at a time. Tasks: outbox publisher (every 5 s), `processed_event` cleanup (overnight), automatic account unlock (every minute) and deletion of expired OAuth2 authorizations (overnight) |
| Stock reservation | Distributed per-product lock in Redis (Redisson, `lock:product:{id}`), taken before opening the transaction and released after the commit; with several products, all the locks in the same order to avoid deadlocks. If not acquired within 3 s, `RESOURCE_BUSY` (423) and the message is retried. `@Version` remains as a second safety net |

On Docker Compose, auth-service stays at one replica: it generates its JWT signing key at startup. To scale it, give all replicas the same key with `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY` (PEM). On Kubernetes, `k8s/deploy.sh` generates that key once and stores it in a Secret, so auth-service can scale and tokens survive restarts.

On Kubernetes, product-service and order-service also scale on their own according to CPU (HorizontalPodAutoscaler); `k8s/load/hpa-demo.sh` demonstrates it under load.

## Demo scenarios

### Distribute the traffic and shut down a replica without losing requests

```bash
docker compose up -d --build
```
```bash
for i in $(seq 1 9); do curl -s -D - -o /dev/null http://localhost:8080/product-service/v3/api-docs | grep -i x-instance-id; done
```

The responses are spread across the 3 replicas (three different `X-Instance-Id` values). With the requests running, shut down a replica:

```bash
docker stop spring-microservices-showcase-product-service-2
```

All requests keep responding 200: the replica finishes what it had in flight, the gateway retries on another one and, within seconds, Eureka stops sending it traffic. At http://localhost:8761 you can see it disappear from the registry.

### Many orders at once for the last stock

The [StockConcurrencyIT](product-service/src/test/java/com/showcase/product/StockConcurrencyIT.java) test launches 20 simultaneous reservations on a product with 5 units: exactly 5 are reserved, the other 15 are rejected with `INSUFFICIENT_STOCK` and the stock never goes negative. With the system in Docker, the same is seen by creating several orders of the same product at once: those without stock become `REJECTED`.

## Tracing with Zipkin

On Kubernetes, Tempo replaces Zipkin: it receives the same traces and they are viewed in Grafana (*Explore → Tempo*). What follows describes Docker Compose.

All services, including the gateway and auth-service, send their traces to Zipkin (Micrometer Tracing with Brave). A single trace follows the request through the gateway, the Feign calls, each SQL query (without parameter values) and the use cases marked with `@Observed`. It also follows RabbitMQ messages: the outbox stores the request's `traceparent` and the send continues that trace, so an order can be seen end to end: gateway → order-service → RabbitMQ → product-service → RabbitMQ → order-service.

Each log line carries `[traceId-spanId]`, and the `traceId` is the same one returned by the `X-Trace-Id` header and the `traceId` field of errors. To look up a request: http://localhost:9411 → *Search by trace ID*.

## Observability: Prometheus, Loki and Grafana

On Kubernetes it is installed with `k8s/observability/install.sh` and reuses the same dashboards and alerts ([k8s/README.md](k8s/README.md#observability-namespace-monitoring)). On Docker Compose it starts together with the system through a second compose file:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --build
```

| Console | URL |
|---|---|
| Grafana (dashboards, logs and traces) | http://localhost:3000 (user `admin`, password `admin` or the one in `.env`) |
| Prometheus (metrics and alerts) | http://localhost:9090 |

| Piece | Detail |
|---|---|
| Metrics | Each service exposes `/actuator/prometheus`, only on the internal network (the gateway, on its internal port 8081). Prometheus discovers the replicas in Eureka, without listing instances by hand. All metrics carry `application`, `instance` (the replica, the same as `X-Instance-Id`) and `environment` |
| Technical | Requests, latency with histograms (p50/p95/p99), errors per endpoint, JVM, Hikari, circuit breakers, gateway requests per route, RabbitMQ queues and DLQs, pending events in the outbox (`outbox_events`) and lock waits (`distributed_lock_wait_seconds`) |
| Business | `showcase_orders_total` (created, confirmed, rejected with their reason, cancelled), `showcase_stock_operations_total`, `showcase_auth_registrations_total`, `showcase_auth_logins_total` and `showcase_auth_accounts_locked_total`. They are counted after the commit |
| Logs | On Docker, each service writes JSON (ECS) with the service, the replica, the level, the `traceId` and the `spanId`. Grafana Alloy collects them from all containers and sends them to Loki |
| Correlation | From a point on a latency chart (exemplar) you jump to its trace in Zipkin; from a log, to its trace; and from a trace, to its logs |
| Dashboards | *Showcase* folder: overview, by microservice, load distribution, messaging, business and security ([docker/grafana/dashboards](docker/grafana/dashboards)) |
| Alerts | 5xx errors > 5 % for 5 min, p95 latency > 1 s, messages in a DLQ, open circuit breaker, downed replica and events stuck in the outbox ([docker/prometheus/alerts.yml](docker/prometheus/alerts.yml)) |

### Scenario: follow a request end to end

1. In Grafana, open *Showcase → 1. System overview* and find in *p95 latency* a point (exemplar) of the request you are interested in; hovering shows its `trace_id` and the link to Zipkin.
2. Zipkin shows the whole request: gateway → service → SQL queries → send to RabbitMQ → service consuming the event.
3. From the trace, *Logs for this span* opens in Loki the log lines of that request across all services (you can also search with `{service="order-service"} | traceId="<traceId>"`).
4. The API returns the same `traceId` in the `X-Trace-Id` header and in the `traceId` field of errors.

## API documentation (Swagger)

- All services: http://localhost:8080/swagger-ui.html, with a dropdown per service.
- Each service started locally: `http://localhost:<port>/swagger-ui.html`.

Each operation documents its possible errors (400, 401, 403, 404, 409, 422, 500, 503) with the `ErrorResponseDTO` schema.

To try the protected endpoints, click *Authorize*. There are two options:

| Scheme | How it works |
|---|---|
| `oauth2` (recommended) | With the `client_id` `web-client` already filled in and no secret, *Authorize* opens the auth-service login form in a popup (for example `admin` / `Showcase-Dev-2026!`). On login, Swagger receives the token and sends it on all requests. It is the same authorization code + PKCE flow as a browser application: only auth-service sees the password, and the lockout, audit log and login rate limiting still apply |
| `bearer-jwt` | Paste a token obtained some other way, for example with the `login` function in [docs/curl-examples.md](docs/curl-examples.md) |

The gateway's Swagger UI returns from the login window to `http://localhost:8080/swagger-ui/oauth2-redirect.html`, which is among the redirect URIs of `web-client` (`WEB_CLIENT_REDIRECT_URIS`; auth-service synchronizes them at startup). They are compared exactly, so open Swagger on `http://localhost:8080` and not on `127.0.0.1`. On Kubernetes, the chart adds the one for its public URL (`https://showcase.localhost/swagger-ui/oauth2-redirect.html`). On a service started without the gateway, set where auth-service is with `showcase.openapi.auth-server-url` and add its redirect URI.

## Continuous integration

Two GitHub Actions workflows run on each pull request to `main`:

| Workflow | What it does |
|---|---|
| [ci.yml](.github/workflows/ci.yml) | `./mvnw verify`: compilation, unit and integration tests and coverage minimums |
| [kubernetes.yml](.github/workflows/kubernetes.yml) | Creates a kind cluster, installs the platform, builds and deploys the chart and runs `k8s/e2e.sh`. On failure, it dumps the state of the pods and their logs |

## Documentation

- [Project specification](docs/microservices-spec.md)
- [UML diagrams](docs/uml-diagrams.html): architecture, deployment on Docker Compose and Kubernetes, classes, sequences and states
- [curl examples](docs/curl-examples.md): token, pagination, each error in English and Spanish, logout, Zipkin and RabbitMQ
- [Kubernetes](k8s/README.md): what is deployed, requirements and decisions
- [Operating on Kubernetes](docs/kubernetes.md): install, scale, upgrade, roll back and investigate failures

## License

[MIT](LICENSE)
