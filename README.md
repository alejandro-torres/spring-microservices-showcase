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
| `auth-service` | Usuarios, roles y permisos (puerto 9000). La parte de OAuth2 y seguridad llega en el siguiente PR |

## Arrancar auth-service en local

No hace falta instalar PostgreSQL: el servicio se arranca con una base de datos en Docker (Testcontainers) y el perfil `dev`, que carga datos de ejemplo.

```bash
./mvnw install -DskipTests
```
```bash
./mvnw -pl auth-service spring-boot:test-run -Dspring-boot.run.profiles=dev
```

Algunas peticiones para probarlo:

```bash
curl "http://localhost:9000/api/v1/users?filter=role:eq:ADMIN"
```
```bash
curl "http://localhost:9000/api/v1/users?filter=enabled:eq:false&sort=createdAt&direction=desc&page=0&size=5"
```
```bash
curl -H "Accept-Language: en" "http://localhost:9000/api/v1/users?filter=passwordHash:eq:x"
```

### Datos de ejemplo (solo perfil `dev`)

- Usuario `admin` (roles ADMIN y USER) y 60 usuarios `user01`…`user60`; cada 7º está deshabilitado y cada 10º borrado.
- Todos tienen la contraseña de desarrollo `Showcase-Dev-2026!`. Estos datos nunca se cargan fuera del perfil `dev`.

### API

| Recurso | Endpoints |
|---|---|
| Usuarios | `POST /api/v1/users`, `GET /api/v1/users/{id}`, `GET /api/v1/users`, `PUT /api/v1/users/{id}`, `DELETE /api/v1/users/{id}`, `POST` y `DELETE /api/v1/users/{id}/roles/{roleId}` |
| Roles | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` en `/api/v1/roles` |
| Permisos | `POST`, `GET /{id}`, `GET`, `PUT /{id}`, `DELETE /{id}` en `/api/v1/permissions` |

Los listados admiten `page`, `size` (máximo 100), `sort`, `direction`, `includeInactive` y filtros `filter=campo:operador:valor`:

| Recurso | Filtros | Ordenación |
|---|---|---|
| Usuarios | `username`, `email` (eq, like, startsWith), `enabled` (eq), `role` (eq, in), `createdAt` (gte, lte, between) | `id`, `username`, `email`, `createdAt`, `updatedAt` |
| Roles | `name` (eq, like, startsWith), `permission` (eq, in), `createdAt` | `id`, `name`, `createdAt` |
| Permisos | `name` (eq, like, startsWith), `createdAt` | `id`, `name`, `createdAt` |

## Documentación

- [Especificación del proyecto](docs/prompt-microservicios.md)
- [Diagramas UML](docs/diagramas-uml.html)

## Licencia

[MIT](LICENSE)
