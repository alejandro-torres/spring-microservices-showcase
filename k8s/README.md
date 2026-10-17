# Kubernetes

Despliegue del sistema en un clúster de Kubernetes local con [kind](https://kind.sigs.k8s.io/). Docker Compose sigue
siendo la forma de trabajar en desarrollo; esto es la misma aplicación desplegada como se haría en producción.

| Docker Compose | Kubernetes |
|---|---|
| `docker-compose.yml` | chart de Helm en `k8s/charts/showcase` |
| `docker compose up` | `k8s/deploy.sh` (`helm upgrade --install`) |
| un contenedor por servicio | Deployments con réplicas y autoescalado (product 3-6, order 2-4) |
| `ports: 8080:8080` del gateway | Ingress con HTTPS en https://showcase.localhost |
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
k8s/platform/install.sh             # Traefik (Ingress), cert-manager (HTTPS) y metrics-server (autoescalado)
k8s/deploy.sh                       # construye las imágenes, las carga en kind e instala el chart
k8s/e2e.sh                          # e2e contra https://showcase.localhost
```

La API queda en **https://showcase.localhost** (Swagger en https://showcase.localhost/swagger-ui.html, con login
OAuth2 + PKCE). `*.localhost` apunta siempre a la propia máquina, sin tocar el fichero hosts. El certificado lo firma
una CA local que crea `k8s/platform/install.sh`; para que el navegador no avise, importa esa CA como entidad raíz de
confianza (en Windows, doble clic en el fichero → *Instalar certificado* → *Entidades de certificación raíz de
confianza*):

```bash
kubectl -n cert-manager get secret showcase-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > showcase-ca.crt
```

Con `curl`, `--cacert showcase-ca.crt` (en Windows además `--ssl-no-revoke`). [docs/curl-examples.md](../docs/curl-examples.md)
sirve cambiando `http://localhost:8080` por `https://showcase.localhost`. `k8s/e2e.sh` prepara lo que necesita Java (un
truststore con la CA y la resolución de `showcase.localhost`) y lanza los mismos e2e que con Docker Compose.

Sin Ingress, con un port-forward como en Compose: `k8s/deploy.sh --set ingress.enabled=false --set publicUrl=http://localhost:8080`
y `kubectl -n showcase port-forward svc/api-gateway 8080:8080` (con Docker Compose parado para dejar libre el 8080).

`SKIP_BUILD=1 k8s/deploy.sh` reinstala el chart sin reconstruir las imágenes. Para borrarlo todo:
`k8s/kind/cluster.sh delete`.

## Qué se despliega (namespace `showcase`)

- **Microservicios**: un Deployment, Service, ConfigMap y Secret por servicio, con sondas de arranque, liveness y
  readiness sobre `/actuator/health`, requests y limits de CPU y memoria.
- **Descubrimiento de servicios sin Eureka**: el gateway (rutas `lb://`) y los clientes Feign de order-service usan
  [Spring Cloud Kubernetes](https://spring.io/projects/spring-cloud-kubernetes), que lee de la API de Kubernetes las
  réplicas listas de cada Service; Spring Cloud LoadBalancer reparte las llamadas entre ellas como antes. Solo esos
  dos pods tienen permiso para leer la API (ServiceAccount `service-discovery`, de solo lectura). Al detectar que
  corren en Kubernetes, los servicios desactivan el cliente de Eureka (`on-cloud-platform: kubernetes` en su
  `application.yml`), así que `discovery-service` no se despliega. En Docker Compose y en local todo sigue igual,
  con Eureka.
- **PostgreSQL** (StatefulSet): una base de datos y un usuario por servicio con
  [docker/postgres/init-databases.sh](../docker/postgres/init-databases.sh).
- **RabbitMQ** (StatefulSet): un usuario por servicio con [docker/rabbitmq/setup-users.sh](../docker/rabbitmq/setup-users.sh),
  lanzado por un Job. El pod no está listo hasta que existen los usuarios, así que ningún servicio arranca sin poder
  autenticarse.
- **Redis** (StatefulSet) y **Zipkin** (solo sin la observabilidad del clúster).
- **Clave RSA del JWT**: la genera `deploy.sh` en `k8s/.secrets/` (fuera de Git) y va en el Secret de `auth-service`.

La infraestructura usa las mismas imágenes oficiales que Docker Compose (`postgres`, `redis`, `rabbitmq`) con
plantillas propias, para reutilizar los scripts de `docker/`. Los charts de Bitnami ya no publican imágenes
gratuitas con versiones fijas, y en producción la base de datos sería un servicio gestionado.

Las contraseñas de [values.yaml](charts/showcase/values.yaml) son las de desarrollo; para otro entorno se pasan con
`--set` o con un fichero de valores propio.

## Preparado para producción

- **Entrada**: [Traefik](https://doc.traefik.io/traefik/) como controlador de Ingress, con recursos `Ingress` estándar
  ([ingress-nginx](https://github.com/kubernetes/ingress-nginx) está archivado desde marzo de 2026). HTTP redirige a
  HTTPS. El gateway solo se fía de las cabeceras `X-Forwarded-*` que llegan desde la red de pods
  (`GATEWAY_TRUSTED_PROXIES`) y el rate limit usa la IP del cliente que añade ese único proxy
  (`RATE_LIMIT_TRUSTED_PROXY_HOPS=1`).
- **HTTPS**: cert-manager emite el certificado del Ingress con la CA local (`k8s/platform/cluster-issuer.yaml`); en un
  entorno real se cambia el ClusterIssuer por Let's Encrypt u otra CA.
- **Autoescalado**: HorizontalPodAutoscaler por CPU (70 % de lo reservado) en product-service (3 a 6 réplicas) y
  order-service (2 a 4). Sube en cuanto hace falta y baja una réplica por minuto tras 5 minutos estable.
  `k8s/load/hpa-demo.sh` lanza 4 minutos de carga sobre product-service y muestra cómo crecen las réplicas.
- **PodDisruptionBudget**: en los servicios con varias réplicas, como mucho una fuera a la vez durante un drenado de
  nodo o una actualización del clúster.
- **NetworkPolicies**: todo denegado por defecto. Solo el gateway recibe tráfico de fuera (del controlador de
  Ingress); cada servicio solo acepta a quien lo llama y solo sale hacia su base de datos, RabbitMQ, Redis, los
  servicios que usa, el DNS y Tempo; Prometheus lee las métricas desde `monitoring`. El clúster de kind las aplica
  (kindnet).
- **Actualizaciones sin cortes**: rolling update con `maxUnavailable: 0` (la réplica nueva tiene que estar lista antes
  de parar una antigua), una pausa en `preStop` para que deje de recibir tráfico y el apagado ordenado de Spring
  (hasta 30 s). Si una versión falla, `helm rollback` vuelve a la anterior:

  ```bash
  helm -n showcase history showcase           # revisiones
  helm -n showcase rollback showcase          # a la revisión anterior (o: rollback showcase <revisión>)
  ```

- **Contenedores sin privilegios**: todos corren sin root (`runAsNonRoot`, sin escalada de privilegios, sin
  capacidades de Linux, perfil seccomp por defecto). Los microservicios, PostgreSQL y Redis además con el sistema de
  ficheros de solo lectura (solo `/tmp` y los datos son escribibles); RabbitMQ no, porque su imagen escribe la
  configuración al arrancar. Solo el gateway y order-service montan el token de la API de Kubernetes.

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
kubectl -n showcase get hpa                          # autoescalado: uso de CPU y réplicas
kubectl -n showcase scale deploy/customer-service --replicas=2   # los que no tienen HPA
kubectl -n showcase port-forward svc/rabbitmq 15672  # consola de RabbitMQ en http://localhost:15672
helm -n showcase history showcase                    # revisiones del release
```
