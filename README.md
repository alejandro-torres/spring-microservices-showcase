# Spring Microservices Showcase

Sistema de microservicios en Java 21 y Spring Boot 3 con **arquitectura hexagonal**, pensado como demostración de lo que se puede construir en sistemas distribuidos con Java.

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

## Documentación

- [Especificación del proyecto](docs/prompt-microservicios.md)
- [Diagramas UML](docs/diagramas-uml.html)

## Licencia

[MIT](LICENSE)
