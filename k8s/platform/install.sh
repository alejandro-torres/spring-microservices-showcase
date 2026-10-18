#!/usr/bin/env bash
# Componentes de plataforma del clúster, previos a la aplicación:
#   - Traefik: controlador de Ingress en los puertos 80/443 del host (https://showcase.localhost)
#   - cert-manager: certificados TLS, firmados por una CA local propia
#   - metrics-server: uso de CPU y memoria para el HorizontalPodAutoscaler
#   k8s/platform/install.sh
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"

helm repo add traefik https://traefik.github.io/charts >/dev/null
helm repo add jetstack https://charts.jetstack.io >/dev/null
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ >/dev/null
helm repo update traefik jetstack metrics-server >/dev/null

helm upgrade --install traefik traefik/traefik --version 42.0.0 \
  --namespace traefik --create-namespace -f "$DIR/values/traefik.yaml" --wait --timeout 5m
helm upgrade --install cert-manager jetstack/cert-manager --version v1.21.2 \
  --namespace cert-manager --create-namespace --set crds.enabled=true --wait --timeout 5m
helm upgrade --install metrics-server metrics-server/metrics-server --version 3.14.0 \
  --namespace kube-system -f "$DIR/values/metrics-server.yaml" --wait --timeout 5m

kubectl apply -f "$DIR/cluster-issuer.yaml"
kubectl -n cert-manager wait --for=condition=Ready certificate/showcase-ca --timeout=2m
