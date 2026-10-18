# Operar el sistema en Kubernetes

Guía práctica: instalar, escalar, actualizar, volver atrás e investigar fallos. Qué se despliega y por qué está en
[k8s/README.md](../k8s/README.md). Los comandos se lanzan desde la raíz del repositorio con bash (Git Bash en
Windows).

## Conceptos en dos minutos

| Recurso | Qué es | En este proyecto |
|---|---|---|
| **Pod** | Uno o varios contenedores que arrancan y mueren juntos | Una réplica de un microservicio |
| **Deployment** | Mantiene N pods iguales y los actualiza poco a poco | Uno por microservicio |
| **StatefulSet** | Como un Deployment, pero con identidad y disco fijos | PostgreSQL, Redis, RabbitMQ |
| **Service** | Nombre DNS estable que reparte el tráfico entre los pods listos | `product-service:8081`, `postgres:5432`... |
| **Ingress** | Entrada HTTP(S) desde fuera del clúster | `https://showcase.localhost` → gateway |
| **ConfigMap / Secret** | Variables de entorno y contraseñas de los pods | Uno de cada por microservicio |
| **HPA** | Ajusta el número de réplicas según la CPU | product-service y order-service |
| **NetworkPolicy** | Cortafuegos entre pods | Todo cerrado salvo lo que cada servicio usa |
| **Helm chart / release** | Plantillas de todo lo anterior / una instalación con su historial | `k8s/charts/showcase` / `showcase` |

## Instalar desde cero

Requisitos: Docker Desktop (12 GB de memoria o más), kubectl, kind y Helm (ver [k8s/README.md](../k8s/README.md)).

```bash
k8s/kind/cluster.sh create          # clúster de 3 nodos
k8s/platform/install.sh             # Ingress (Traefik), certificados (cert-manager) y métricas de CPU
k8s/observability/install.sh        # opcional: Prometheus, Grafana, Loki, Alloy y Tempo
k8s/deploy.sh                       # imágenes + chart (unos minutos la primera vez)
k8s/e2e.sh                          # comprobación de punta a punta
```

- API y Swagger: https://showcase.localhost/swagger-ui.html (usuario `admin`, contraseña `Showcase-Dev-2026!`).
  Para que el navegador confíe en el certificado, importa la CA local (instrucciones en [k8s/README.md](../k8s/README.md)).
- Grafana: `kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80` → http://localhost:3000
  (admin / admin).
- Consola de RabbitMQ: `kubectl -n showcase port-forward svc/rabbitmq 15672` → http://localhost:15672.

Para empezar de cero: `k8s/kind/cluster.sh delete` borra el clúster con todos sus datos.

## Ver el estado

```bash
kubectl -n showcase get pods -o wide         # READY 1/1 y STATUS Running en todos; en qué nodo está cada uno
kubectl -n showcase get deploy,sts,hpa,pdb   # réplicas deseadas y disponibles, autoescalado, presupuestos
kubectl -n showcase get ingress,certificate  # entrada y certificado (READY True)
kubectl top pods -n showcase                 # CPU y memoria de cada pod
helm -n showcase list                        # release instalado y su revisión
```

## Escalar

product-service y order-service se escalan solos con el HPA, entre los mínimos y máximos de
[values.yaml](../k8s/charts/showcase/values.yaml) (`autoscaling`). Para verlo con carga:

```bash
k8s/load/hpa-demo.sh                          # 4 minutos de carga: product-service pasa de 3 a 6 réplicas
kubectl -n showcase get hpa -w                # en otra terminal: CPU y réplicas en directo
```

Los demás servicios se escalan a mano:

```bash
kubectl -n showcase scale deploy/customer-service --replicas=2      # inmediato, hasta el próximo helm upgrade
k8s/deploy.sh --set services.customer-service.replicas=2            # permanente (o cámbialo en values.yaml)
```

No uses `kubectl scale` con product-service ni order-service: el HPA lo deshace enseguida. Cambia `minReplicas` y
`maxReplicas`.

Cada réplica abre hasta `databasePoolSize` conexiones (5). Antes de subir réplicas o `maxReplicas`, comprueba que el
total cabe en el `max_connections` de PostgreSQL (`postgres.maxConnections`, 200 en local). Si no cabe, las réplicas
nuevas fallan al arrancar con *remaining connection slots are reserved*.

## Actualizar

```bash
k8s/deploy.sh                                  # nuevo código: reconstruye, carga las imágenes y reinicia los pods
SKIP_BUILD=1 k8s/deploy.sh                     # solo cambios del chart o de values.yaml
k8s/deploy.sh --set springProfile=prod         # cualquier valor del chart con --set
kubectl -n showcase rollout status deploy/order-service   # seguir una actualización
```

Cada actualización es un *rolling update* sin cortes. Arranca una réplica nueva y, solo cuando pasa la sonda de
readiness, para una antigua. La antigua deja de recibir tráfico y termina las peticiones en curso. Si las réplicas
nuevas no llegan a estar listas, las antiguas siguen atendiendo y la actualización se queda a medias, sin cortar el
servicio. Para salir de ahí, haz un rollback.

Si cambia cualquier variable de entorno (ConfigMap) o secreto, los pods se reinician solos con el `helm upgrade`.

## Volver atrás (rollback)

```bash
helm -n showcase history showcase              # revisiones: cuál está deployed y cuál failed
helm -n showcase rollback showcase             # a la revisión anterior
helm -n showcase rollback showcase 7           # a una revisión concreta
kubectl -n showcase rollout undo deploy/api-gateway   # deshacer solo un Deployment (sin pasar por Helm)
```

Ejemplo comprobado. Un `helm upgrade` con una imagen que no existe deja los pods nuevos en `ImagePullBackOff`, la
API sigue respondiendo con las réplicas antiguas y `helm rollback` vuelve a dejarlo todo como estaba. Prefiere
`helm rollback` a `kubectl rollout undo`: así Helm sigue sabiendo qué versión está instalada.

## Investigar un fallo

### 1. ¿Qué pod está mal?

```bash
kubectl -n showcase get pods
```

| STATUS | Qué significa | Dónde mirar |
|---|---|---|
| `Pending` | No cabe en ningún nodo o espera un disco | `kubectl describe pod`: eventos `FailedScheduling` |
| `Init:0/2` | Un initContainer espera a PostgreSQL o RabbitMQ | `kubectl logs <pod> -c wait-for-postgres` y el estado de `postgres-0` / `rabbitmq-0` |
| `ImagePullBackOff` | La imagen no existe en el nodo o el registro | ¿`kind load` / `k8s/deploy.sh`? ¿etiqueta correcta? |
| `CrashLoopBackOff` | La aplicación arranca y se cae | `kubectl logs <pod> --previous` |
| `Running` con `0/1` | Arrancada pero no pasa la readiness: no recibe tráfico | `kubectl describe pod` (sondas) y los logs |
| `OOMKilled` (en describe) | Superó el límite de memoria | Sube `resources.limits.memory` |
| `CrashLoopBackOff` con *remaining connection slots* | PostgreSQL sin conexiones libres | Réplicas × `databasePoolSize` frente a `max_connections` |

### 2. Eventos y logs

```bash
kubectl -n showcase describe pod <pod>                       # al final, Events: por qué no arranca o no está listo
kubectl -n showcase get events --sort-by=.lastTimestamp | tail -20
kubectl -n showcase logs <pod> --previous                    # logs del contenedor que se cayó
kubectl -n showcase logs -f deploy/order-service             # logs en directo (de una réplica)
kubectl -n showcase logs -l app.kubernetes.io/name=product-service --prefix --tail=50   # de todas las réplicas
```

Los logs son JSON (ECS). Lo útil está en `message`, `error.message`, `error.stack_trace` y `traceId`.

### 3. Conectividad

Las NetworkPolicies cortan todo lo que no está permitido. Un servicio nuevo que llama a otro, o un servicio externo
nuevo, necesita su regla. Un síntoma típico es un *timeout* (no un *connection refused*) en los logs. Para probar
desde dentro de un pod:

```bash
kubectl -n showcase exec deploy/order-service -- nc -z -w 3 product-service 8081; echo $?   # 0 = permitido
kubectl -n showcase get networkpolicy
```

Quién puede hablar con quién se define en `clients` de cada servicio en [values.yaml](../k8s/charts/showcase/values.yaml).

### 4. Seguir una petición por su traceId (Grafana)

Cada respuesta de error lleva su `traceId` (`ErrorResponseDTO`), y cada línea de log de esa petición también, en
todos los servicios por los que pasó.

1. Coge el `traceId`, de la respuesta de error o de una línea de log.
2. En Grafana, *Explore → Loki*: `{service=~".+"} | traceId="<traceId>"`. Salen los logs de esa petición en el
   gateway, auth, order, product... ordenados en el tiempo.
3. Pulsa *Ver la traza en Tempo* en cualquier línea. Verás la traza completa: qué servicio tardó, qué consulta SQL,
   en qué llamada falló.
4. Desde un pico en las gráficas de latencia (dashboards de *Showcase*), los puntos (*exemplars*) abren la traza de
   una petición concreta de ese momento.

Las alertas (*Alerting* en Grafana o en Prometheus) avisan de pods en CrashLoop, pods no listos, Deployments sin
réplicas, errores 5xx, latencia alta, DLQ con mensajes y circuit breakers abiertos.

## Producción

[values-prod.yaml](../k8s/charts/showcase/values-prod.yaml) prepara el chart para un clúster real:

- PostgreSQL, Redis y RabbitMQ **gestionados**, fuera del clúster (`postgres.enabled: false`, etc.), con TLS. Las
  NetworkPolicies abren la salida solo hacia su red (`cidr`).
- Antes de instalar hay que crear un usuario y una base de datos por servicio (como
  [docker/postgres/init-databases.sh](../docker/postgres/init-databases.sh)) y un usuario de RabbitMQ por servicio con
  sus permisos (como [docker/rabbitmq/setup-users.sh](../docker/rabbitmq/setup-users.sh)).
- Los **Secrets** no los crea el chart (`secrets.create: false`). Se crean aparte con las mismas claves, normalmente
  con External Secrets desde el gestor de secretos de la nube. Así ninguna contraseña pasa por Git.
- Imágenes de un registro, con la versión fijada en cada despliegue: `--set image.tag=1.4.0`.
- Perfil `prod`, al menos 2 réplicas de cada servicio, HPA con más margen y Let's Encrypt en el Ingress.

```bash
helm upgrade --install showcase k8s/charts/showcase -n showcase --create-namespace \
  -f k8s/charts/showcase/values-prod.yaml --set image.tag=1.4.0
```

## Integración continua

El workflow [kubernetes.yml](../.github/workflows/kubernetes.yml) repite en GitHub Actions lo mismo que en local, en
cada pull request: crea el clúster de kind, instala la plataforma, construye y despliega el chart y lanza `k8s/e2e.sh`.
Si falla, el log del job incluye el estado de los pods, los eventos y los logs de cada servicio, y los informes de los
e2e quedan como artefacto.
