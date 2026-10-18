#!/usr/bin/env bash
# Lanza los e2e contra el clúster por el Ingress (https://showcase.localhost).
#   k8s/e2e.sh
# Java no confía en la CA local ni resuelve *.localhost en todos los sistemas (los navegadores y curl sí): se le
# pasa un truststore con la CA de cert-manager y un fichero de hosts propio.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${E2E_HOST:-showcase.localhost}"
WORK="$ROOT/k8s/.secrets"
mkdir -p "$WORK"

kubectl -n cert-manager get secret showcase-ca -o jsonpath='{.data.ca\.crt}' | base64 -d > "$WORK/showcase-ca.crt"
rm -f "$WORK/truststore.p12"
keytool -importcert -noprompt -alias showcase-ca -file "$WORK/showcase-ca.crt" \
  -keystore "$WORK/truststore.p12" -storetype PKCS12 -storepass changeit >/dev/null
printf '127.0.0.1 %s\n' "$HOST" > "$WORK/e2e-hosts"

# En Git Bash (Windows), Java necesita las rutas en formato C:/...
JAVA_WORK="$WORK"
if command -v cygpath >/dev/null; then JAVA_WORK="$(cygpath -m "$WORK")"; fi

cd "$ROOT"
# jdk.net.hosts.file se lee al arrancar la JVM: va en argLine (la JVM de los tests), no como propiedad de Maven.
./mvnw -Pe2e -pl e2e-tests verify -Djacoco.skip=true \
  -De2e.base-url="https://$HOST" \
  -DargLine="-Djdk.net.hosts.file=$JAVA_WORK/e2e-hosts -Djavax.net.ssl.trustStore=$JAVA_WORK/truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit" \
  "$@"
