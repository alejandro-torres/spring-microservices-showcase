{{/*
Variables de entorno (ConfigMap) de un microservicio: (dict "root" . "name" nombre "svc" valores). Se usa también
para el checksum del Deployment: si cambia cualquier variable, los pods se vuelven a crear al hacer helm upgrade.
*/}}
{{- define "showcase.serviceConfig" -}}
{{- $root := .root -}}
{{- $name := .name -}}
{{- $svc := .svc -}}
# Logs en JSON (ECS) con traceId y spanId.
LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs
SPRING_PROFILES_ACTIVE: {{ $root.Values.springProfile | quote }}
ZIPKIN_ENDPOINT: {{ $root.Values.tracing.endpoint | quote }}
AUTH_JWK_SET_URI: http://auth-service:9000/oauth2/jwks
{{- with $svc.database }}
DB_URL: jdbc:postgresql://postgres:5432/{{ . }}_db
DB_USERNAME: {{ . | quote }}
{{- end }}
{{- with $svc.rabbitmq }}
RABBITMQ_HOST: rabbitmq
RABBITMQ_USERNAME: {{ $name }}
{{- end }}
{{- if $svc.redis }}
REDIS_HOST: redis
{{- end }}
{{- if eq $name "auth-service" }}
# Los tokens se piden a través del gateway: el emisor es su URL pública.
AUTH_ISSUER: {{ $root.Values.publicUrl | quote }}
# Login del Swagger UI desde la URL pública, además de los de desarrollo.
WEB_CLIENT_REDIRECT_URIS: "http://127.0.0.1:3000/callback,http://localhost:8080/swagger-ui/oauth2-redirect.html,{{ $root.Values.publicUrl }}/swagger-ui/oauth2-redirect.html"
{{- end }}
{{- if eq $name "api-gateway" }}
# El propio origen público: detrás del Ingress el gateway recibe http y no ve el https del navegador como suyo.
CORS_ALLOWED_ORIGINS: "{{ $root.Values.corsAllowedOrigins }},{{ $root.Values.publicUrl }}"
{{- if $root.Values.ingress.enabled }}
# Detrás del Ingress: un único proxy de confianza (ver ingress en values.yaml).
GATEWAY_TRUSTED_PROXIES: {{ $root.Values.ingress.trustedProxies | quote }}
RATE_LIMIT_TRUSTED_PROXY_HOPS: {{ $root.Values.ingress.trustedProxyHops | quote }}
{{- end }}
{{- end }}
{{- range $key, $value := $svc.env }}
{{ $key }}: {{ $value | quote }}
{{- end }}
{{- end -}}
