# Kubernetes

Despliegue del sistema en un clúster de Kubernetes local con [kind](https://kind.sigs.k8s.io/). Docker Compose sigue
siendo la forma de trabajar en desarrollo; esto es la misma aplicación desplegada como se haría en producción.

| Docker Compose | Kubernetes |
|---|---|
| `docker-compose.yml` | chart de Helm en `k8s/charts/showcase` |
| `docker compose up` | `k8s/deploy.sh` (`helm upgrade --install`) |
| un contenedor por servicio | Deployments con réplicas (product 3, order 2) |
| `depends_on` | initContainers que esperan a PostgreSQL y RabbitMQ |
| `.env` | Secrets de Kubernetes |

## Requisitos

- Docker Desktop con al menos 8 GB de memoria (recomendado 12 GB o más).
- [kubectl](https://kubernetes.io/docs/tasks/tools/), [kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
  y [Helm](https://helm.sh/docs/intro/install/). En Windows son tres ejecutables sueltos que basta con dejar en una
  carpeta del `PATH`.
- Git Bash (Windows) o cualquier shell bash para los scripts. Usa `openssl` para generar la clave del JWT.

## Arrancar

```bash
k8s/kind/cluster.sh create          # clúster de 3 nodos: 1 control-plane + 2 workers, puertos 80/443 mapeados
k8s/deploy.sh                       # construye las imágenes, las carga en kind e instala el chart
kubectl -n showcase port-forward svc/api-gateway 8080:8080
```

El gateway queda en http://localhost:8080, como con Docker Compose, así que Swagger
(http://localhost:8080/swagger-ui.html, con login OAuth2 + PKCE), [docs/curl-examples.md](../docs/curl-examples.md)
y los e2e funcionan igual (`./mvnw -Pe2e -pl e2e-tests verify`). Para que el puerto 8080 esté libre, para antes
Docker Compose (`docker compose stop`).

`SKIP_BUILD=1 k8s/deploy.sh` reinstala el chart sin reconstruir las imágenes. Para borrarlo todo:
`k8s/kind/cluster.sh delete`.

## Qué se despliega (namespace `showcase`)

- **Microservicios**: un Deployment, Service, ConfigMap y Secret por servicio, con sondas de arranque, liveness y
  readiness sobre `/actuator/health`, requests y limits de CPU y memoria. Siguen usando Eureka (`discovery-service`).
- **PostgreSQL** (StatefulSet): una base de datos y un usuario por servicio con
  [docker/postgres/init-databases.sh](../docker/postgres/init-databases.sh).
- **RabbitMQ** (StatefulSet): un usuario por servicio con [docker/rabbitmq/setup-users.sh](../docker/rabbitmq/setup-users.sh),
  lanzado por un Job. El pod no está listo hasta que existen los usuarios, así que ningún servicio arranca sin poder
  autenticarse.
- **Redis** (StatefulSet) y **Zipkin**.
- **Clave RSA del JWT**: la genera `deploy.sh` en `k8s/.secrets/` (fuera de Git) y va en el Secret de `auth-service`.

La infraestructura usa las mismas imágenes oficiales que Docker Compose (`postgres`, `redis`, `rabbitmq`) con
plantillas propias, para reutilizar los scripts de `docker/`. Los charts de Bitnami ya no publican imágenes
gratuitas con versiones fijas, y en producción la base de datos sería un servicio gestionado.

Las contraseñas de [values.yaml](charts/showcase/values.yaml) son las de desarrollo; para otro entorno se pasan con
`--set` o con un fichero de valores propio.

## Observabilidad (namespace `monitoring`)

```bash
k8s/observability/install.sh        # Prometheus, Grafana, Alertmanager, Loki, Alloy y Tempo
k8s/deploy.sh                       # vuelve a desplegar la aplicación con las trazas hacia Tempo
kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80
```

Grafana queda en http://localhost:3000 (admin / admin), con los mismos dashboards que Docker Compose en la carpeta
*Showcase* y además los de Kubernetes (pods, nodos, recursos).

- **Métricas**: [kube-prometheus-stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack).
  El chart crea un `PodMonitor` por servicio y otro para RabbitMQ, con las mismas etiquetas que en Compose
  (`job="microservices"`, `eureka_app`), así que los dashboards de [docker/grafana/dashboards](../docker/grafana/dashboards)
  y las alertas de [docker/prometheus/alerts.yml](../docker/prometheus/alerts.yml) se cargan sin cambios. Además hay
  alertas de pods en CrashLoop, pods que no están listos y Deployments sin todas sus réplicas
  ([alerts-kubernetes.yml](observability/alerts-kubernetes.yml)).
- **Logs**: Grafana Alloy corre como DaemonSet (un pod por nodo), lee los ficheros de log de los pods de
  `showcase` y los envía a Loki con las etiquetas `service`, `container` y `level`, y el `traceId` como metadato.
- **Trazas**: Tempo, con disco persistente, sustituye a Zipkin. Recibe los spans en formato Zipkin, así que en los
  microservicios solo cambia `ZIPKIN_ENDPOINT`. `deploy.sh` lo detecta y aplica
  [showcase-values.yaml](observability/showcase-values.yaml).

**Buscar por traceId**: en *Explore → Loki*, `{service=~".+"} | traceId="<traceId>"` muestra los logs de la petición
en todos los servicios, y el enlace *Ver la traza en Tempo* de cada línea abre la traza. En sentido contrario, desde
una traza de Tempo se llega a sus logs, y desde los exemplars de las gráficas de latencia, a la traza de esa petición.

En kind saltan dos alertas sin importancia: `NodeClockNotSynchronising`, porque los nodos son contenedores sin NTP,
y `CPUThrottlingHigh` mientras arrancan las JVM con el límite de 1 CPU.

## Comandos útiles

```bash
kubectl -n showcase get pods -o wide                 # pods y en qué nodo está cada uno
kubectl -n showcase logs -f deploy/order-service     # logs (de una réplica)
kubectl -n showcase describe pod <pod>               # eventos: por qué no arranca o no está listo
kubectl -n showcase scale deploy/product-service --replicas=5
kubectl -n showcase port-forward svc/rabbitmq 15672  # consola de RabbitMQ en http://localhost:15672
kubectl -n showcase port-forward svc/discovery-service 8761
helm -n showcase history showcase                    # revisiones del release
```
