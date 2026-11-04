#!/usr/bin/env bash
# Runs the e2e tests against the cluster through the Ingress (https://showcase.localhost).
#   k8s/e2e.sh
# Java does not trust the local CA or resolve *.localhost on all systems (browsers and curl do): it is
# given a truststore with the cert-manager CA and its own hosts file.
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

# In Git Bash (Windows), Java needs paths in C:/... format
JAVA_WORK="$WORK"
if command -v cygpath >/dev/null; then JAVA_WORK="$(cygpath -m "$WORK")"; fi

cd "$ROOT"
# jdk.net.hosts.file is read when the JVM starts: it goes in argLine (the tests' JVM), not as a Maven property.
./mvnw -Pe2e -pl e2e-tests verify -Djacoco.skip=true \
  -De2e.base-url="https://$HOST" \
  -DargLine="-Djdk.net.hosts.file=$JAVA_WORK/e2e-hosts -Djavax.net.ssl.trustStore=$JAVA_WORK/truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit" \
  "$@"
