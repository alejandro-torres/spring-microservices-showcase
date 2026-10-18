#!/usr/bin/env bash
# Instala (o actualiza) la observabilidad del clúster en el namespace monitoring:
#   - kube-prometheus-stack: Prometheus, Alertmanager, Grafana, kube-state-metrics y node-exporter
#   - Loki (logs) y Grafana Alloy como DaemonSet (recoge los logs de cada nodo)
#   - Tempo (trazas, en lugar de Zipkin)
# y carga los dashboards de docker/grafana/dashboards y las alertas de docker/prometheus/alerts.yml.
#   k8s/observability/install.sh
# Después, k8s/deploy.sh vuelve a desplegar la aplicación enviando las trazas a Tempo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$ROOT/k8s/observability"
NAMESPACE=monitoring

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo add grafana https://grafana.github.io/helm-charts >/dev/null
# Loki y Tempo se mantienen ahora en el repositorio de la comunidad de Grafana.
helm repo add grafana-community https://grafana-community.github.io/helm-charts >/dev/null
helm repo update prometheus-community grafana grafana-community >/dev/null

kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Dashboards: un ConfigMap con los JSON de docker/grafana/dashboards, en la carpeta "Showcase" de Grafana.
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

# Alertas: los mismos grupos que en Docker Compose más las de Kubernetes, como PrometheusRule.
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
