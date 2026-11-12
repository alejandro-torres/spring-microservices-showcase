# CLAUDE.md

Guide for working in this repository. The full specification is in [docs/microservices-spec.md](docs/microservices-spec.md) and the diagrams in [docs/uml-diagrams.html](docs/uml-diagrams.html); this file summarizes the rules that always apply.

## Project
- Java 21, Spring Boot 4.1 (Spring Framework 7, Jackson 3), Spring Cloud 2025.1, multi-module Maven with a parent POM and Maven Wrapper (`./mvnw`).
- Services: `auth-service`, `product-service`, `customer-service`, `order-service`, `api-gateway`, `discovery-service`.
- Shared modules: `common-lib` (exceptions, pagination, filters, error handling and common messaging) and `events-contract` (RabbitMQ messages).
- Package base: `com.showcase.<service>`.
- Language: everything in the repository is in English (documentation, comments, tests, logs, commit messages). The only exception is the `*_es.properties` bundles, because the API also answers in Spanish with `Accept-Language: es`.
- Deployment: Docker Compose for development (`docker-compose.yml` and `docker-compose.observability.yml`, configuration in `docker/`) and Kubernetes in `k8s/` (Helm chart `k8s/charts/showcase`; guide in [k8s/README.md](k8s/README.md) and [docs/kubernetes.md](docs/kubernetes.md)). `discovery-service` (Eureka) is only used on Compose: on Kubernetes the services disable it on detecting the platform and discover the replicas with Spring Cloud Kubernetes.

## Kubernetes
- Environment-dependent configuration goes in `values.yaml` (local) and `values-prod.yaml`, never hardcoded in the templates. The chart reuses the `docker/` scripts with `--set-file`.
- Each new call between services, or to a new piece of infrastructure, needs its NetworkPolicy rule (the service's `clients` in `values.yaml`, or the egress in `networkpolicies.yaml`).
- Non-root containers with a read-only file system: whatever must be written goes to an `emptyDir`.
- If `k8s/` changes, besides `./mvnw verify` check with `helm lint` and deploy on kind with `k8s/deploy.sh` and `k8s/e2e.sh` (the `kubernetes.yml` workflow repeats it on each PR).

## Git workflow
- Never commit directly to `main`: each change goes on a branch (`feat/...`, `fix/...`, `docs/...`, `ci/...`, `chore/...`) and is integrated through a pull request approved by the repository owner.
- Small, frequent commits, one per logical step, with messages in English in Conventional Commits format (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`, `ci:`).
- Before opening a PR, the build and tests must pass (`./mvnw verify`).
- Never merge a PR or enable auto-merge unless the owner asks for it.

## Hexagonal architecture
```
domain/            model, exception, event, repository (interfaces), messaging (interfaces)
application/       service (interfaces), service/impl, client (interfaces), lock, metrics
infrastructure/    rest (controller, dto/request, dto/response, mapper),
                   persistence (entity, repository, repository/impl, specification, mapper),
                   messaging (config, publisher/impl, listener, outbox, mapper),
                   client (feign, impl, dto), lock/impl, metrics/impl, security, exception, config
```
- The domain does not depend on Spring, JPA or any framework. Dependencies always point towards the domain.
- Interfaces and implementations in separate folders; implementations have the `Impl` suffix and live in an `impl` folder.
- Controllers and listeners only use service interfaces. The `*ServiceImpl` classes only use interfaces (repositories, publishers, clients, locks), never Spring Data, RabbitMQ, Feign or Redisson directly.
- JPA entities (`*Entity`), domain models and DTOs are distinct classes, converted with MapStruct.
- Lombok only in infrastructure and application, never in the domain: injection constructors with `@RequiredArgsConstructor` and utility classes with `@NoArgsConstructor(access = AccessLevel.PRIVATE)`; a constructor is only written by hand when it has logic.

## Naming conventions
- All DTOs end in `DTO` and are `record`s: `CreateProductRequestDTO`, `ProductResponseDTO`, `PageResponseDTO`, `ErrorResponseDTO`, `OrderCreatedMessageDTO`.
- Input DTOs in `dto/request` (with Bean Validation) and output DTOs in `dto/response`.
- Domain events in `domain/event` with past-tense names (`OrderCreatedEvent`); RabbitMQ messages are `*MessageDTO` in `events-contract`.

## Cross-cutting rules
- Paginated listings: `PageQuery` and `PageResult` in the domain; `Pageable` and `Page` only in `*RepositoryImpl`. All accept dynamic filters `filter=field:operator:value` with a per-resource whitelist.
- Errors: domain exceptions with `ErrorCode`; the `GlobalExceptionHandler` returns only standard HTTP codes and `ErrorResponseDTO`, with messages in English (default) and Spanish and no internal Java details.
- `@Transactional` on the public methods of the `*ServiceImpl` classes (`readOnly = true` on reads). Never remote calls inside a transaction.
- Soft delete (`active = false`) and optimistic locking (`@Version`) on all business entities.
- Dates with an injected `Clock`, never `Instant.now()` directly, so they can be tested.
- Messaging: always publish through the outbox (`*EventPublisherImpl` → `OutboxEventWriter` from `common-lib`, with `Propagation.MANDATORY`), never with `RabbitTemplate` from a service. Each consumer registers the event with `ProcessedEventRegistry` in the same transaction as its change, and each `@RabbitListener` uses `errorHandler = LoggingRabbitListenerErrorHandler.BEAN_NAME`. Exchange and queue names go in `showcase.messaging` in `application.yml`; the `outbox_event` and `processed_event` tables are created by each service's Flyway migration.

## Tests
- Unit tests with JUnit 5, Mockito and AssertJ, without a Spring context, in the same package as the class under test and with the `Test` suffix.
- Integration tests with Testcontainers and the `IT` suffix.
- given / when / then structure, `@DisplayName` in English and `@Nested` per method.
- Minimum coverage with JaCoCo: 80 % of lines and 70 % of branches in `domain` and `application`.
