#!/usr/bin/env bash
# Construye las imágenes, las carga en kind e instala (o actualiza) el chart en el clúster local.
#   k8s/deploy.sh              # build + kind load + helm upgrade --install
#   SKIP_BUILD=1 k8s/deploy.sh # sin reconstruir las imágenes
# Después: kubectl port-forward svc/api-gateway 8080:8080  ->  http://localhost:8080/swagger-ui.html
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLUSTER_NAME=showcase
RELEASE=showcase
NAMESPACE=showcase
SERVICES=(discovery-service auth-service product-service customer-service order-service api-gateway)
KEYS_DIR="$ROOT/k8s/.secrets"

cd "$ROOT"

if [[ -z "${SKIP_BUILD:-}" ]]; then
  docker compose build
fi

for service in "${SERVICES[@]}"; do
  kind load docker-image "showcase/$service:latest" --name "$CLUSTER_NAME"
done

# Clave RSA del JWT: se genera una vez y se reutiliza (los tokens emitidos siguen siendo válidos tras un upgrade).
if [[ ! -f "$KEYS_DIR/jwt-private.pem" ]]; then
  mkdir -p "$KEYS_DIR"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYS_DIR/jwt-private.pem"
  openssl pkey -in "$KEYS_DIR/jwt-private.pem" -pubout -out "$KEYS_DIR/jwt-public.pem"
fi

helm upgrade --install "$RELEASE" k8s/charts/showcase \
  --namespace "$NAMESPACE" --create-namespace \
  --set-file secrets.jwtPrivateKey="$KEYS_DIR/jwt-private.pem" \
  --set-file secrets.jwtPublicKey="$KEYS_DIR/jwt-public.pem" \
  --set-file scripts.postgresInit=docker/postgres/init-databases.sh \
  --set-file scripts.rabbitmqSetupUsers=docker/rabbitmq/setup-users.sh \
  "$@"

# Con la misma etiqueta latest, Kubernetes no ve cambios en la imagen: se reinician los pods para usar la nueva.
if [[ -z "${SKIP_BUILD:-}" ]]; then
  kubectl -n "$NAMESPACE" rollout restart deployment "${SERVICES[@]}"
fi

echo "Esperando a que arranquen los servicios..."
for service in "${SERVICES[@]}"; do
  kubectl -n "$NAMESPACE" rollout status deployment "$service" --timeout=10m
done
kubectl -n "$NAMESPACE" get pods -o wide
