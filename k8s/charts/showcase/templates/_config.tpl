{{/*
Environment variables (ConfigMap) of a microservice: (dict "root" . "name" name "svc" values). Also used
for the Deployment checksum: if any variable changes, the pods are recreated on helm upgrade.
*/}}
{{- define "showcase.serviceConfig" -}}
{{- $root := .root -}}
{{- $name := .name -}}
{{- $svc := .svc -}}
# JSON logs (ECS) with traceId and spanId.
LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs
SPRING_PROFILES_ACTIVE: {{ $root.Values.springProfile | quote }}
ZIPKIN_ENDPOINT: {{ $root.Values.tracing.endpoint | quote }}
AUTH_JWK_SET_URI: http://auth-service:9000/oauth2/jwks
{{- with $svc.database }}
DB_URL: jdbc:postgresql://{{ include "showcase.postgresHost" $root }}:{{ include "showcase.postgresPort" $root }}/{{ . }}_db{{ if not $root.Values.postgres.enabled }}{{ $root.Values.externalDatabase.jdbcParams }}{{ end }}
DB_USERNAME: {{ . | quote }}
# Connections per replica: with the HPA at its maximum, together they must fit within PostgreSQL's max_connections.
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
# Tokens are requested through the gateway: the issuer is its public URL.
AUTH_ISSUER: {{ $root.Values.publicUrl | quote }}
# Swagger UI login from the public URL, in addition to the development ones.
WEB_CLIENT_REDIRECT_URIS: "http://127.0.0.1:3000/callback,http://localhost:8080/swagger-ui/oauth2-redirect.html,{{ $root.Values.publicUrl }}/swagger-ui/oauth2-redirect.html"
{{- end }}
{{- if eq $name "api-gateway" }}
# The public origin itself: behind the Ingress the gateway receives http and does not see the browser's https as its own.
CORS_ALLOWED_ORIGINS: "{{ $root.Values.corsAllowedOrigins }},{{ $root.Values.publicUrl }}"
{{- if $root.Values.ingress.enabled }}
# Behind the Ingress: a single trusted proxy (see ingress in values.yaml).
GATEWAY_TRUSTED_PROXIES: {{ $root.Values.ingress.trustedProxies | quote }}
RATE_LIMIT_TRUSTED_PROXY_HOPS: {{ $root.Values.ingress.trustedProxyHops | quote }}
{{- end }}
{{- end }}
{{- range $key, $value := $svc.env }}
{{ $key }}: {{ $value | quote }}
{{- end }}
{{- end -}}
