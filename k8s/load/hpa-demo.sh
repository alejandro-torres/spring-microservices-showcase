#!/usr/bin/env bash
# Demostración del HorizontalPodAutoscaler: carga sobre product-service durante 4 minutos y muestra cómo sube el
# número de réplicas (de 3 hasta 6). Después vuelve poco a poco a 3 (una réplica por minuto tras 5 minutos estable).
#   k8s/load/hpa-demo.sh
set -euo pipefail

NAMESPACE=showcase
MANIFEST="$(dirname "$0")/load-generator.yaml"

cleanup() { kubectl -n "$NAMESPACE" delete -f "$MANIFEST" --ignore-not-found >/dev/null; }
trap cleanup EXIT

cleanup
kubectl -n "$NAMESPACE" apply -f "$MANIFEST"
echo "Carga en marcha. Réplicas de product-service (Ctrl+C para parar antes):"
end=$(( $(date +%s) + 300 ))
while [[ $(date +%s) -lt $end ]]; do
  echo "$(date +%T)  $(kubectl -n "$NAMESPACE" get hpa product-service -o jsonpath='CPU {.status.currentMetrics[0].resource.current.averageUtilization}% (objetivo {.spec.metrics[0].resource.target.averageUtilization}%), réplicas {.status.currentReplicas} -> {.status.desiredReplicas}')"
  sleep 15
done
