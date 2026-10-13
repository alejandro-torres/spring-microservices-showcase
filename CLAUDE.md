# CLAUDE.md

Guía para trabajar en este repositorio. La especificación completa está en [docs/prompt-microservicios.md](docs/prompt-microservicios.md) y los diagramas en [docs/diagramas-uml.html](docs/diagramas-uml.html); este fichero resume las reglas que se aplican siempre.

## Proyecto
- Java 21, Spring Boot 3, Maven multimódulo con un POM padre.
- Servicios: `auth-service`, `product-service`, `customer-service`, `order-service`, `api-gateway`, `discovery-service`.
- Módulos compartidos: `common-lib` (excepciones, paginación, filtros, manejo de errores) y `events-contract` (mensajes de RabbitMQ).
- Base de paquetes: `com.showcase.<servicio>`.

## Flujo de trabajo con Git
- Nunca se hacen commits directos a `main`: cada cambio va en una rama (`feat/...`, `fix/...`, `docs/...`, `ci/...`, `chore/...`) y se integra con un pull request que aprueba el propietario del repositorio.
- Commits pequeños y frecuentes, uno por paso lógico, con mensajes en español y formato Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`, `ci:`).
- Antes de abrir un PR, el build y los tests tienen que pasar (`./mvnw verify`).
- Nunca se hace merge de un PR ni se activa el auto-merge sin que lo pida el propietario.

## Arquitectura hexagonal
```
domain/            model, exception, event, repository (interfaces), messaging (interfaces)
application/       service (interfaces), service/impl, client (interfaces), lock, metrics
infrastructure/    rest (controller, dto/request, dto/response, mapper),
                   persistence (entity, repository, repository/impl, specification, mapper),
                   messaging (config, publisher/impl, listener, outbox, mapper),
                   client (feign, impl, dto), lock/impl, metrics/impl, security, exception, config
```
- El dominio no depende de Spring, JPA ni de ningún framework. Las dependencias siempre apuntan hacia el dominio.
- Interfaces e implementaciones en carpetas separadas; las implementaciones llevan el sufijo `Impl` y viven en una carpeta `impl`.
- Los controllers y los listeners solo usan las interfaces de servicio. Los `*ServiceImpl` solo usan interfaces (repositorios, publishers, clientes, locks), nunca Spring Data, RabbitMQ, Feign ni Redisson directamente.
- Entidades JPA (`*Entity`), modelos de dominio y DTOs son clases distintas, convertidas con MapStruct.

## Convenciones de nombres
- Todos los DTOs terminan en `DTO` y son `record`: `CreateProductRequestDTO`, `ProductResponseDTO`, `PageResponseDTO`, `ErrorResponseDTO`, `OrderCreatedMessageDTO`.
- DTOs de entrada en `dto/request` (con Bean Validation) y de salida en `dto/response`.
- Eventos de dominio en `domain/event` con nombres en pasado (`OrderCreatedEvent`); los mensajes de RabbitMQ son `*MessageDTO` en `events-contract`.

## Reglas transversales
- Listados paginados: `PageQuery` y `PageResult` en dominio; `Pageable` y `Page` solo en `*RepositoryImpl`. Todos admiten filtros dinámicos `filter=campo:operador:valor` con lista blanca por recurso.
- Errores: excepciones de dominio con `ErrorCode`; el `GlobalExceptionHandler` devuelve solo códigos HTTP estándar y `ErrorResponseDTO`, con mensajes en español e inglés y sin detalles internos de Java.
- `@Transactional` en los métodos públicos de los `*ServiceImpl` (`readOnly = true` en las lecturas). Nunca llamadas remotas dentro de una transacción.
- Borrado lógico (`active = false`) y bloqueo optimista (`@Version`) en todas las entidades de negocio.
- Fechas con un `Clock` inyectado, nunca `Instant.now()` directo, para poder probarlas.

## Tests
- Unitarios con JUnit 5, Mockito y AssertJ, sin contexto de Spring, en el mismo paquete que la clase probada y con el sufijo `Test`.
- Integración con Testcontainers y el sufijo `IT`.
- Estructura given / when / then, `@DisplayName` en español y `@Nested` por método.
- Cobertura mínima con JaCoCo: 80 % de líneas y 70 % de ramas en `domain` y `application`.
