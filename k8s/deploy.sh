#!/usr/bin/env bash
# Builds the images, loads them into kind and installs (or upgrades) the chart on the local cluster.
#   k8s/deploy.sh              # build + kind load + helm upgrade --install
#   SKIP_BUILD=1 k8s/deploy.sh # without rebuilding the images
# Afterwards: kubectl port-forward svc/api-gateway 8080:8080  ->  http://localhost:8080/swagger-ui.html
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLUSTER_NAME=showcase
RELEASE=showcase
NAMESPACE=showcase
SERVICES=(auth-service product-service customer-service order-service api-gateway)
KEYS_DIR="$ROOT/k8s/.secrets"

cd "$ROOT"

if [[ -z "${SKIP_BUILD:-}" ]]; then
  docker compose build
fi

for service in "${SERVICES[@]}"; do
  kind load docker-image "showcase/$service:latest" --name "$CLUSTER_NAME"
done

# JWT RSA key: generated once and reused (issued tokens stay valid after an upgrade).
if [[ ! -f "$KEYS_DIR/jwt-private.pem" ]]; then
  mkdir -p "$KEYS_DIR"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYS_DIR/jwt-private.pem"
  openssl pkey -in "$KEYS_DIR/jwt-private.pem" -pubout -out "$KEYS_DIR/jwt-public.pem"
fi

# With the cluster observability installed (k8s/observability/install.sh), traces go to Tempo.
EXTRA_VALUES=()
if helm -n monitoring status tempo >/dev/null 2>&1; then
  EXTRA_VALUES=(-f k8s/observability/showcase-values.yaml)
fi

helm upgrade --install "$RELEASE" k8s/charts/showcase "${EXTRA_VALUES[@]}" \
  --namespace "$NAMESPACE" --create-namespace \
  --set-file secrets.jwtPrivateKey="$KEYS_DIR/jwt-private.pem" \
  --set-file secrets.jwtPublicKey="$KEYS_DIR/jwt-public.pem" \
  --set-file scripts.postgresInit=docker/postgres/init-databases.sh \
  --set-file scripts.rabbitmqSetupUsers=docker/rabbitmq/setup-users.sh \
  "$@"

# With the same latest tag, Kubernetes sees no change in the image: the pods are restarted to use the new one.
if [[ -z "${SKIP_BUILD:-}" ]]; then
  kubectl -n "$NAMESPACE" rollout restart deployment "${SERVICES[@]}"
fi

echo "Waiting for the services to start..."
for service in "${SERVICES[@]}"; do
  kubectl -n "$NAMESPACE" rollout status deployment "$service" --timeout=10m
done
kubectl -n "$NAMESPACE" get pods -o wide
