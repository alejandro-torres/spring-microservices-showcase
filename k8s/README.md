# Kubernetes

Deployment of the system on a local Kubernetes cluster with [kind](https://kind.sigs.k8s.io/). Docker Compose remains
the way to work in development; this is the same application deployed as it would be in production.
How to operate it (scale, upgrade, roll back, investigate failures) is in [docs/kubernetes.md](../docs/kubernetes.md).

| Docker Compose | Kubernetes |
|---|---|
| `docker-compose.yml` | Helm chart in `k8s/charts/showcase` |
| `docker compose up` | `k8s/deploy.sh` (`helm upgrade --install`) |
| one container per service | Deployments with replicas and autoscaling (product 3-6, order 2-4) |
| the gateway's `ports: 8080:8080` | Ingress with HTTPS at https://showcase.localhost |
| `depends_on` | initContainers that wait for PostgreSQL and RabbitMQ |
| `.env` | Kubernetes Secrets |

## Requirements

- Docker Desktop with at least 12 GB of memory (16 GB with the cluster observability).
- [kubectl](https://kubernetes.io/docs/tasks/tools/), [kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
  and [Helm](https://helm.sh/docs/intro/install/). On Windows they are three standalone executables you just need to
  place in a folder on the `PATH`.
- Git Bash (Windows) or any bash shell for the scripts. `openssl` is used to generate the JWT key.

## Start

```bash
k8s/kind/cluster.sh create          # 3-node cluster: 1 control-plane + 2 workers, ports 80/443 mapped
k8s/platform/install.sh             # Traefik (Ingress), cert-manager (HTTPS) and metrics-server (autoscaling)
k8s/deploy.sh                       # builds the images, loads them into kind and installs the chart
k8s/e2e.sh                          # e2e against https://showcase.localhost
```

The API is at **https://showcase.localhost** (Swagger at https://showcase.localhost/swagger-ui.html, with OAuth2 +
PKCE login). `*.localhost` always points to the machine itself, without touching the hosts file. The certificate is
signed by a local CA created by `k8s/platform/install.sh`; to stop the browser warning, import that CA as a trusted
root authority (on Windows, double-click the file → *Install Certificate* → *Trusted Root Certification
Authorities*):

```bash
kubectl -n cert-manager get secret showcase-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > showcase-ca.crt
```

With `curl`, `--cacert showcase-ca.crt` (on Windows also `--ssl-no-revoke`). [docs/curl-examples.md](../docs/curl-examples.md)
works by replacing `http://localhost:8080` with `https://showcase.localhost`. `k8s/e2e.sh` prepares what Java needs (a
truststore with the CA and the resolution of `showcase.localhost`) and runs the same e2e tests as with Docker Compose.

Without the Ingress, with a port-forward as in Compose: `k8s/deploy.sh --set ingress.enabled=false --set publicUrl=http://localhost:8080`
and `kubectl -n showcase port-forward svc/api-gateway 8080:8080` (with Docker Compose stopped to leave 8080 free).

`SKIP_BUILD=1 k8s/deploy.sh` reinstalls the chart without rebuilding the images. To delete everything:
`k8s/kind/cluster.sh delete`.

## What is deployed (`showcase` namespace)

- **Microservices**: one Deployment, Service, ConfigMap and Secret per service, with startup, liveness and readiness
  probes on `/actuator/health`, and CPU and memory requests and limits.
- **Service discovery without Eureka**: the gateway (`lb://` routes) and order-service's Feign clients use
  [Spring Cloud Kubernetes](https://spring.io/projects/spring-cloud-kubernetes), which reads from the Kubernetes API the
  ready replicas of each Service; Spring Cloud LoadBalancer distributes the calls among them as before. Only those
  two pods have permission to read the API (`service-discovery` ServiceAccount, read-only). On detecting they run on
  Kubernetes, the services disable the Eureka client (`on-cloud-platform: kubernetes` in their `application.yml`), so
  `discovery-service` is not deployed. On Docker Compose and locally everything stays the same, with Eureka.
- **PostgreSQL** (StatefulSet): one database and one user per service with
  [docker/postgres/init-databases.sh](../docker/postgres/init-databases.sh).
- **RabbitMQ** (StatefulSet): one user per service with [docker/rabbitmq/setup-users.sh](../docker/rabbitmq/setup-users.sh),
  launched by a Job. The pod is not ready until the users exist, so no service starts unable to authenticate.
- **Redis** (StatefulSet) and **Zipkin** (only without the cluster observability).
- **JWT RSA key**: `deploy.sh` generates it in `k8s/.secrets/` (outside Git) and it goes in the `auth-service` Secret.
- **PostgreSQL connections**: each replica opens at most `databasePoolSize` (5) and the chart's PostgreSQL allows
  `postgres.maxConnections` (200), so all replicas fit with the HPA at its maximum and in the middle of a rolling
  update.

The infrastructure uses the same official images as Docker Compose (`postgres`, `redis`, `rabbitmq`) with its own
templates, to reuse the `docker/` scripts. The Bitnami charts no longer publish free images with fixed versions, and
in production the database would be a managed service.

The passwords in [values.yaml](charts/showcase/values.yaml) are the development ones. For a real cluster there is
[values-prod.yaml](charts/showcase/values-prod.yaml):
- Managed PostgreSQL, Redis and RabbitMQ, outside the cluster (`postgres.enabled: false` and `externalDatabase`, etc.).
- Secrets created outside the chart (`secrets.create: false`).
- Images from a registry and the `prod` profile.

The steps are in [docs/kubernetes.md](../docs/kubernetes.md#production).

## Production ready

- **Entry**: [Traefik](https://doc.traefik.io/traefik/) as the Ingress controller, with standard `Ingress` resources
  ([ingress-nginx](https://github.com/kubernetes/ingress-nginx) has been archived since March 2026). HTTP redirects to
  HTTPS. The gateway only trusts the `X-Forwarded-*` headers arriving from the pod network
  (`GATEWAY_TRUSTED_PROXIES`) and the rate limit uses the client IP added by that single proxy
  (`RATE_LIMIT_TRUSTED_PROXY_HOPS=1`).
- **HTTPS**: cert-manager issues the Ingress certificate with the local CA (`k8s/platform/cluster-issuer.yaml`); in a
  real environment, the ClusterIssuer is switched to Let's Encrypt or another CA.
- **Autoscaling**: HorizontalPodAutoscaler by CPU (70 % of the requested amount) on product-service (3 to 6 replicas)
  and order-service (2 to 4). It scales up as soon as needed and down one replica per minute after 5 stable minutes.
  `k8s/load/hpa-demo.sh` runs 4 minutes of load on product-service and shows the replicas growing.
- **PodDisruptionBudget**: on services with several replicas, at most one out at a time during a node drain or a
  cluster upgrade.
- **NetworkPolicies**: everything denied by default. Only the gateway receives outside traffic (from the Ingress
  controller); each service only accepts its callers and only goes out to its database, RabbitMQ, Redis, the services
  it uses, DNS and Tempo; Prometheus reads the metrics from `monitoring`. The kind cluster enforces them (kindnet).
- **Zero-downtime updates**: rolling update with `maxUnavailable: 0` (the new replica must be ready before stopping an
  old one), a `preStop` pause so it stops receiving traffic, and Spring's graceful shutdown (up to 30 s). If a version
  fails, `helm rollback` returns to the previous one:

  ```bash
  helm -n showcase history showcase           # revisions
  helm -n showcase rollback showcase          # to the previous revision (or: rollback showcase <revision>)
  ```

- **Unprivileged containers**: all run without root (`runAsNonRoot`, no privilege escalation, no Linux capabilities,
  default seccomp profile). The microservices, PostgreSQL and Redis also have a read-only file system (only `/tmp` and
  the data are writable); RabbitMQ does not, because its image writes its configuration at startup. Only the gateway
  and order-service mount the Kubernetes API token.

## Observability (`monitoring` namespace)

```bash
k8s/observability/install.sh        # Prometheus, Grafana, Alertmanager, Loki, Alloy and Tempo
k8s/deploy.sh                       # redeploys the application with traces going to Tempo
kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80
```

Grafana is at http://localhost:3000 (admin / admin), with the same dashboards as Docker Compose in the *Showcase*
folder plus the Kubernetes ones (pods, nodes, resources).

- **Metrics**: [kube-prometheus-stack](https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack).
  The chart creates one `PodMonitor` per service and another for RabbitMQ, with the same labels as in Compose
  (`job="microservices"`, `eureka_app`), so the dashboards in [docker/grafana/dashboards](../docker/grafana/dashboards)
  and the alerts in [docker/prometheus/alerts.yml](../docker/prometheus/alerts.yml) load unchanged. There are also
  alerts for pods in CrashLoop, pods that are not ready and Deployments without all their replicas
  ([alerts-kubernetes.yml](observability/alerts-kubernetes.yml)).
- **Logs**: Grafana Alloy runs as a DaemonSet (one pod per node), reads the log files of the `showcase` pods and
  sends them to Loki with the labels `service`, `container` and `level`, and the `traceId` as metadata.
- **Traces**: Tempo, with a persistent disk, replaces Zipkin. It receives spans in Zipkin format, so in the
  microservices only `ZIPKIN_ENDPOINT` changes. `deploy.sh` detects it and applies
  [showcase-values.yaml](observability/showcase-values.yaml).

**Search by traceId**: in *Explore → Loki*, `{service=~".+"} | traceId="<traceId>"` shows the request's logs across
all services, and each line's *View the trace in Tempo* link opens the trace. In the other direction, from a Tempo
trace you reach its logs, and from the exemplars of the latency charts, the trace of that request.

On kind two unimportant alerts fire: `NodeClockNotSynchronising`, because the nodes are containers without NTP,
and `CPUThrottlingHigh` while the JVMs start with the 1 CPU limit.

## Continuous integration

[.github/workflows/kubernetes.yml](../.github/workflows/kubernetes.yml) repeats on each pull request the same as
locally: `kind/cluster.sh create`, `platform/install.sh`, `deploy.sh` and `e2e.sh`. If something fails, the job log
includes the pods, the events and each service's logs.

## Useful commands

```bash
kubectl -n showcase get pods -o wide                 # pods and which node each is on
kubectl -n showcase logs -f deploy/order-service     # logs (of one replica)
kubectl -n showcase describe pod <pod>               # events: why it does not start or is not ready
kubectl -n showcase get hpa                          # autoscaling: CPU usage and replicas
kubectl -n showcase scale deploy/customer-service --replicas=2   # those without an HPA
kubectl -n showcase port-forward svc/rabbitmq 15672  # RabbitMQ console at http://localhost:15672
helm -n showcase history showcase                    # release revisions
```
