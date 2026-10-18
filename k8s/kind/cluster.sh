#!/usr/bin/env bash
# Crea o borra el clúster local de kind.
#   k8s/kind/cluster.sh create   # 3 nodos (1 control-plane + 2 workers), puertos 80/443 mapeados
#   k8s/kind/cluster.sh delete
set -euo pipefail

CLUSTER_NAME=showcase
CONFIG="$(dirname "$0")/kind-config.yaml"

case "${1:-create}" in
  create)
    if kind get clusters | grep -qx "$CLUSTER_NAME"; then
      echo "El clúster $CLUSTER_NAME ya existe"
    else
      kind create cluster --config "$CONFIG" --wait 120s
    fi
    kubectl config use-context "kind-$CLUSTER_NAME"
    kubectl get nodes
    ;;
  delete)
    kind delete cluster --name "$CLUSTER_NAME"
    ;;
  *)
    echo "Uso: $0 [create|delete]" >&2
    exit 1
    ;;
esac
