#!/usr/bin/env bash
# Installs (or upgrades) the cluster observability in the monitoring namespace:
#   - kube-prometheus-stack: Prometheus, Alertmanager, Grafana, kube-state-metrics and node-exporter
#   - Loki (logs) and Grafana Alloy as a DaemonSet (collects the logs of each node)
#   - Tempo (traces, instead of Zipkin)
# and loads the dashboards from docker/grafana/dashboards and the alerts from docker/prometheus/alerts.yml.
#   k8s/observability/install.sh
# Afterwards, k8s/deploy.sh redeploys the application sending the traces to Tempo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$ROOT/k8s/observability"
NAMESPACE=monitoring

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo add grafana https://grafana.github.io/helm-charts >/dev/null
# Loki and Tempo are now maintained in the Grafana community repository.
helm repo add grafana-community https://grafana-community.github.io/helm-charts >/dev/null
helm repo update prometheus-community grafana grafana-community >/dev/null

kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Dashboards: a ConfigMap with the JSONs from docker/grafana/dashboards, in Grafana's "Showcase" folder.
kubectl -n "$NAMESPACE" create configmap showcase-dashboards --from-file="$ROOT/docker/grafana/dashboards" \
  --dry-run=client -o yaml \
  | kubectl label --local -f - grafana_dashboard=1 -o yaml \
  | kubectl annotate --local -f - grafana_folder=Showcase -o yaml \
  | kubectl apply --server-side --force-conflicts -f -

helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack --version 92.9.0 \
  --namespace "$NAMESPACE" -f "$DIR/values/kube-prometheus-stack.yaml" --wait --timeout 10m
helm upgrade --install loki grafana-community/loki --version 18.16.0 \
  --namespace "$NAMESPACE" -f "$DIR/values/loki.yaml" --wait --timeout 10m
helm upgrade --install tempo grafana-community/tempo --version 3.2.0 \
  --namespace "$NAMESPACE" -f "$DIR/values/tempo.yaml" --wait --timeout 10m
helm upgrade --install alloy grafana/alloy --version 1.13.1 \
  --namespace "$NAMESPACE" -f "$DIR/values/alloy.yaml" --wait --timeout 10m

# Alerts: the same groups as in Docker Compose plus the Kubernetes ones, as a PrometheusRule.
for rules in "$ROOT/docker/prometheus/alerts.yml" "$DIR/alerts-kubernetes.yml"; do
  name="showcase-$(basename "$rules" .yml)"
  {
    printf 'apiVersion: monitoring.coreos.com/v1\nkind: PrometheusRule\nmetadata:\n  name: %s\n  namespace: %s\nspec:\n' \
      "$name" "$NAMESPACE"
    grep -v '^#' "$rules" | sed 's/^/  /'
  } | kubectl apply -f -
done

kubectl -n "$NAMESPACE" get pods
echo "Grafana: kubectl -n $NAMESPACE port-forward svc/kube-prometheus-stack-grafana 3000:80  ->  http://localhost:3000"
