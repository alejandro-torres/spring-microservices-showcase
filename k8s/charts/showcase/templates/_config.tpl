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
DB_URL: jdbc:postgresql://{{ include "showcase.postgresHost" $root }}:{{ include "showcase.postgresPort" $root }}/{{ . }}_db{{ if not $root.Values.postgres.enabled }}{{ $root.Values.externalDatabase.jdbcParams }}{{ end }}
DB_USERNAME: {{ . | quote }}
# Conexiones por réplica: con el HPA al máximo, todas juntas tienen que caber en max_connections de PostgreSQL.
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: {{ $root.Values.databasePoolSize | quote }}
{{- end }}
{{- with $svc.rabbitmq }}
RABBITMQ_HOST: {{ include "showcase.rabbitmqHost" $root | quote }}
RABBITMQ_PORT: {{ include "showcase.rabbitmqPort" $root | quote }}
RABBITMQ_USERNAME: {{ $name }}
{{- if not $root.Values.rabbitmq.enabled }}
RABBITMQ_VHOST: {{ $root.Values.externalRabbitmq.vhost | quote }}
SPRING_RABBITMQ_SSL_ENABLED: {{ $root.Values.externalRabbitmq.ssl | quote }}
{{- end }}
{{- end }}
{{- if $svc.redis }}
REDIS_HOST: {{ include "showcase.redisHost" $root | quote }}
REDIS_PORT: {{ include "showcase.redisPort" $root | quote }}
{{- if not $root.Values.redis.enabled }}
SPRING_DATA_REDIS_SSL_ENABLED: {{ $root.Values.externalRedis.ssl | quote }}
{{- end }}
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
