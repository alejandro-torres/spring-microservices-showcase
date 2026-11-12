# Operating the system on Kubernetes

Practical guide: install, scale, upgrade, roll back and investigate failures. What is deployed and why is in
[k8s/README.md](../k8s/README.md). Commands are run from the repository root with bash (Git Bash on Windows).

## Concepts in two minutes

| Resource | What it is | In this project |
|---|---|---|
| **Pod** | One or more containers that start and die together | A replica of a microservice |
| **Deployment** | Keeps N identical pods and updates them gradually | One per microservice |
| **StatefulSet** | Like a Deployment, but with a fixed identity and disk | PostgreSQL, Redis, RabbitMQ |
| **Service** | Stable DNS name that distributes traffic among the ready pods | `product-service:8081`, `postgres:5432`... |
| **Ingress** | HTTP(S) entry from outside the cluster | `https://showcase.localhost` → gateway |
| **ConfigMap / Secret** | Environment variables and passwords of the pods | One of each per microservice |
| **HPA** | Adjusts the number of replicas according to CPU | product-service and order-service |
| **NetworkPolicy** | Firewall between pods | Everything closed except what each service uses |
| **Helm chart / release** | Templates of all the above / an installation with its history | `k8s/charts/showcase` / `showcase` |

## Install from scratch

Requirements: Docker Desktop (12 GB of memory or more), kubectl, kind and Helm (see [k8s/README.md](../k8s/README.md)).

```bash
k8s/kind/cluster.sh create          # 3-node cluster
k8s/platform/install.sh             # Ingress (Traefik), certificates (cert-manager) and CPU metrics
k8s/observability/install.sh        # optional: Prometheus, Grafana, Loki, Alloy and Tempo
k8s/deploy.sh                       # images + chart (a few minutes the first time)
k8s/e2e.sh                          # end-to-end check
```

- API and Swagger: https://showcase.localhost/swagger-ui.html (user `admin`, password `Showcase-Dev-2026!`).
  For the browser to trust the certificate, import the local CA (instructions in [k8s/README.md](../k8s/README.md)).
- Grafana: `kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80` → http://localhost:3000
  (admin / admin).
- RabbitMQ console: `kubectl -n showcase port-forward svc/rabbitmq 15672` → http://localhost:15672.

To start from scratch: `k8s/kind/cluster.sh delete` deletes the cluster with all its data.

## Check the state

```bash
kubectl -n showcase get pods -o wide         # READY 1/1 and STATUS Running on all; which node each is on
kubectl -n showcase get deploy,sts,hpa,pdb   # desired and available replicas, autoscaling, budgets
kubectl -n showcase get ingress,certificate  # entry and certificate (READY True)
kubectl top pods -n showcase                 # CPU and memory of each pod
helm -n showcase list                        # installed release and its revision
```

## Scale

product-service and order-service scale on their own with the HPA, between the minimums and maximums in
[values.yaml](../k8s/charts/showcase/values.yaml) (`autoscaling`). To see it under load:

```bash
k8s/load/hpa-demo.sh                          # 4 minutes of load: product-service goes from 3 to 6 replicas
kubectl -n showcase get hpa -w                # in another terminal: live CPU and replicas
```

The other services are scaled by hand:

```bash
kubectl -n showcase scale deploy/customer-service --replicas=2      # immediate, until the next helm upgrade
k8s/deploy.sh --set services.customer-service.replicas=2            # permanent (or change it in values.yaml)
```

Do not use `kubectl scale` on product-service or order-service: the HPA undoes it right away. Change `minReplicas` and
`maxReplicas`.

Each replica opens up to `databasePoolSize` connections (5). Before raising replicas or `maxReplicas`, check that the
total fits within PostgreSQL's `max_connections` (`postgres.maxConnections`, 200 locally). If it does not fit, new
replicas fail on startup with *remaining connection slots are reserved*.

## Upgrade

```bash
k8s/deploy.sh                                  # new code: rebuilds, loads the images and restarts the pods
SKIP_BUILD=1 k8s/deploy.sh                     # only chart or values.yaml changes
k8s/deploy.sh --set springProfile=prod         # any chart value with --set
kubectl -n showcase rollout status deploy/order-service   # follow an upgrade
```

Each upgrade is a zero-downtime *rolling update*. A new replica starts and, only when it passes the readiness probe,
an old one stops. The old one stops receiving traffic and finishes in-flight requests. If the new replicas never get
ready, the old ones keep serving and the upgrade stays half-done, without cutting the service. To get out of that,
roll back.

If any environment variable (ConfigMap) or secret changes, the pods restart on their own with the `helm upgrade`.

## Roll back

```bash
helm -n showcase history showcase              # revisions: which is deployed and which failed
helm -n showcase rollback showcase             # to the previous revision
helm -n showcase rollback showcase 7           # to a specific revision
kubectl -n showcase rollout undo deploy/api-gateway   # undo a single Deployment (bypassing Helm)
```

Verified example. A `helm upgrade` with an image that does not exist leaves the new pods in `ImagePullBackOff`, the
API keeps responding with the old replicas and `helm rollback` leaves everything as it was. Prefer `helm rollback` to
`kubectl rollout undo`: that way Helm still knows which version is installed.

## Investigate a failure

### 1. Which pod is wrong?

```bash
kubectl -n showcase get pods
```

| STATUS | What it means | Where to look |
|---|---|---|
| `Pending` | It fits on no node or is waiting for a disk | `kubectl describe pod`: `FailedScheduling` events |
| `Init:0/2` | An initContainer is waiting for PostgreSQL or RabbitMQ | `kubectl logs <pod> -c wait-for-postgres` and the state of `postgres-0` / `rabbitmq-0` |
| `ImagePullBackOff` | The image does not exist on the node or registry | `kind load` / `k8s/deploy.sh`? Correct tag? |
| `CrashLoopBackOff` | The application starts and crashes | `kubectl logs <pod> --previous` |
| `Running` with `0/1` | Started but not passing readiness: receives no traffic | `kubectl describe pod` (probes) and the logs |
| `OOMKilled` (in describe) | It exceeded the memory limit | Raise `resources.limits.memory` |
| `CrashLoopBackOff` with *remaining connection slots* | PostgreSQL has no free connections | Replicas × `databasePoolSize` versus `max_connections` |

### 2. Events and logs

```bash
kubectl -n showcase describe pod <pod>                       # at the end, Events: why it does not start or is not ready
kubectl -n showcase get events --sort-by=.lastTimestamp | tail -20
kubectl -n showcase logs <pod> --previous                    # logs of the container that crashed
kubectl -n showcase logs -f deploy/order-service             # live logs (of one replica)
kubectl -n showcase logs -l app.kubernetes.io/name=product-service --prefix --tail=50   # of all replicas
```

The logs are JSON (ECS). The useful parts are in `message`, `error.message`, `error.stack_trace` and `traceId`.

### 3. Connectivity

NetworkPolicies cut everything not allowed. A new service calling another, or a new external service, needs its rule.
A typical symptom is a *timeout* (not a *connection refused*) in the logs. To test from inside a pod:

```bash
kubectl -n showcase exec deploy/order-service -- nc -z -w 3 product-service 8081; echo $?   # 0 = allowed
kubectl -n showcase get networkpolicy
```

Who can talk to whom is defined in each service's `clients` in [values.yaml](../k8s/charts/showcase/values.yaml).

### 4. Follow a request by its traceId (Grafana)

Each error response carries its `traceId` (`ErrorResponseDTO`), and so does each log line of that request, across all
the services it went through.

1. Take the `traceId`, from the error response or from a log line.
2. In Grafana, *Explore → Loki*: `{service=~".+"} | traceId="<traceId>"`. It shows the logs of that request in the
   gateway, auth, order, product... ordered in time.
3. Click *View the trace in Tempo* on any line. You will see the full trace: which service was slow, which SQL query,
   which call failed.
4. From a spike on the latency charts (*Showcase* dashboards), the dots (*exemplars*) open the trace of a specific
   request at that moment.

The alerts (*Alerting* in Grafana or in Prometheus) warn about pods in CrashLoop, pods not ready, Deployments without
replicas, 5xx errors, high latency, DLQs with messages and open circuit breakers.

## Production

[values-prod.yaml](../k8s/charts/showcase/values-prod.yaml) prepares the chart for a real cluster:

- **Managed** PostgreSQL, Redis and RabbitMQ, outside the cluster (`postgres.enabled: false`, etc.), with TLS. The
  NetworkPolicies open egress only towards their network (`cidr`).
- Before installing, create one user and one database per service (as in
  [docker/postgres/init-databases.sh](../docker/postgres/init-databases.sh)) and one RabbitMQ user per service with
  its permissions (as in [docker/rabbitmq/setup-users.sh](../docker/rabbitmq/setup-users.sh)).
- The chart does not create the **Secrets** (`secrets.create: false`). They are created separately with the same keys,
  usually with External Secrets from the cloud's secret manager. This way no password goes through Git.
- Images from a registry, with the version set on each deployment: `--set image.tag=1.4.0`.
- `prod` profile, at least 2 replicas of each service, HPA with more headroom and Let's Encrypt on the Ingress.

```bash
helm upgrade --install showcase k8s/charts/showcase -n showcase --create-namespace \
  -f k8s/charts/showcase/values-prod.yaml --set image.tag=1.4.0
```

## Continuous integration

The [kubernetes.yml](../.github/workflows/kubernetes.yml) workflow repeats on GitHub Actions the same as locally, on
each pull request: it creates the kind cluster, installs the platform, builds and deploys the chart and runs
`k8s/e2e.sh`. On failure, the job log includes the state of the pods, the events and each service's logs, and the e2e
reports are kept as an artifact.
