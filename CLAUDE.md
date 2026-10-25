# CLAUDE.md

Guía para trabajar en este repositorio. La especificación completa está en [docs/prompt-microservicios.md](docs/prompt-microservicios.md) y los diagramas en [docs/diagramas-uml.html](docs/diagramas-uml.html); este fichero resume las reglas que se aplican siempre.

## Proyecto
- Java 21, Spring Boot 4.1 (Spring Framework 7, Jackson 3), Spring Cloud 2025.1, Maven multimódulo con un POM padre y Maven Wrapper (`./mvnw`).
- Servicios: `auth-service`, `product-service`, `customer-service`, `order-service`, `api-gateway`, `discovery-service`.
- Módulos compartidos: `common-lib` (excepciones, paginación, filtros, manejo de errores y mensajería común) y `events-contract` (mensajes de RabbitMQ).
- Base de paquetes: `com.showcase.<servicio>`.
- Despliegue: Docker Compose para desarrollo (`docker-compose.yml` y `docker-compose.observability.yml`, configuración en `docker/`) y Kubernetes en `k8s/` (chart de Helm `k8s/charts/showcase`; guía en [k8s/README.md](k8s/README.md) y [docs/kubernetes.md](docs/kubernetes.md)). `discovery-service` (Eureka) solo se usa en Compose: en Kubernetes los servicios lo desactivan al detectar la plataforma y descubren las réplicas con Spring Cloud Kubernetes.

## Kubernetes
- La configuración que depende del entorno va en `values.yaml` (local) y `values-prod.yaml`, nunca fija en las plantillas. El chart reutiliza los scripts de `docker/` con `--set-file`.
- Cada llamada nueva entre servicios, o hacia una pieza de infraestructura nueva, necesita su regla de NetworkPolicy (`clients` del servicio en `values.yaml`, o la salida en `networkpolicies.yaml`).
- Contenedores sin root y con el sistema de ficheros de solo lectura: lo que haya que escribir va a un `emptyDir`.
- Si cambia `k8s/`, además de `./mvnw verify` se comprueba con `helm lint` y se despliega en kind con `k8s/deploy.sh` y `k8s/e2e.sh` (el workflow `kubernetes.yml` lo repite en cada PR).

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
- Lombok solo en la infraestructura y la aplicación, nunca en el dominio: constructores de inyección con `@RequiredArgsConstructor` y clases de utilidades con `@NoArgsConstructor(access = AccessLevel.PRIVATE)`; solo se escribe un constructor a mano cuando tiene lógica.

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
- Mensajería: se publica siempre por el outbox (`*EventPublisherImpl` → `OutboxEventWriter` de `common-lib`, con `Propagation.MANDATORY`), nunca con `RabbitTemplate` desde un servicio. Cada consumidor registra el evento con `ProcessedEventRegistry` en la misma transacción que su cambio, y cada `@RabbitListener` usa `errorHandler = LoggingRabbitListenerErrorHandler.BEAN_NAME`. Los nombres de exchanges y colas van en `showcase.messaging` de `application.yml`; las tablas `outbox_event` y `processed_event` las crea la migración Flyway de cada servicio.

## Tests
- Unitarios con JUnit 5, Mockito y AssertJ, sin contexto de Spring, en el mismo paquete que la clase probada y con el sufijo `Test`.
- Integración con Testcontainers y el sufijo `IT`.
- Estructura given / when / then, `@DisplayName` en español y `@Nested` por método.
- Cobertura mínima con JaCoCo: 80 % de líneas y 70 % de ramas en `domain` y `application`.
