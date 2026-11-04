#!/usr/bin/env bash
# HorizontalPodAutoscaler demo: load on product-service for 4 minutes, showing how the
# number of replicas rises (from 3 up to 6). It then slowly returns to 3 (one replica per minute after 5 stable minutes).
#   k8s/load/hpa-demo.sh
set -euo pipefail

NAMESPACE=showcase
MANIFEST="$(dirname "$0")/load-generator.yaml"

cleanup() { kubectl -n "$NAMESPACE" delete -f "$MANIFEST" --ignore-not-found >/dev/null; }
trap cleanup EXIT

cleanup
kubectl -n "$NAMESPACE" apply -f "$MANIFEST"
echo "Load running. product-service replicas (Ctrl+C to stop early):"
end=$(( $(date +%s) + 300 ))
while [[ $(date +%s) -lt $end ]]; do
  echo "$(date +%T)  $(kubectl -n "$NAMESPACE" get hpa product-service -o jsonpath='CPU {.status.currentMetrics[0].resource.current.averageUtilization}% (target {.spec.metrics[0].resource.target.averageUtilization}%), replicas {.status.currentReplicas} -> {.status.desiredReplicas}')"
  sleep 15
done
