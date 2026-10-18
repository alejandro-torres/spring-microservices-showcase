{{/*
Dónde está cada pieza de infraestructura: dentro del chart (postgres.enabled, redis.enabled, rabbitmq.enabled) o un
servicio gestionado fuera del clúster (externalDatabase, externalRedis, externalRabbitmq).
*/}}
{{- define "showcase.postgresHost" -}}
{{- if .Values.postgres.enabled }}postgres{{ else }}{{ required "Falta externalDatabase.host" .Values.externalDatabase.host }}{{ end -}}
{{- end -}}
{{- define "showcase.postgresPort" -}}
{{- if .Values.postgres.enabled }}5432{{ else }}{{ .Values.externalDatabase.port }}{{ end -}}
{{- end -}}
{{- define "showcase.redisHost" -}}
{{- if .Values.redis.enabled }}redis{{ else }}{{ required "Falta externalRedis.host" .Values.externalRedis.host }}{{ end -}}
{{- end -}}
{{- define "showcase.redisPort" -}}
{{- if .Values.redis.enabled }}6379{{ else }}{{ .Values.externalRedis.port }}{{ end -}}
{{- end -}}
{{- define "showcase.rabbitmqHost" -}}
{{- if .Values.rabbitmq.enabled }}rabbitmq{{ else }}{{ required "Falta externalRabbitmq.host" .Values.externalRabbitmq.host }}{{ end -}}
{{- end -}}
{{- define "showcase.rabbitmqPort" -}}
{{- if .Values.rabbitmq.enabled }}5672{{ else }}{{ .Values.externalRabbitmq.port }}{{ end -}}
{{- end -}}

{{/*
Regla de salida hacia una pieza de infraestructura: al pod del chart o, si es externa, a su red (cidr).
(dict "root" . "enabled" bool "name" nombre del pod "port" puerto "cidr" red externa)
*/}}
{{- define "showcase.egressInfra" -}}
{{- if .enabled }}
{{- include "showcase.egressPod" (dict "name" .name "port" .port) }}
{{- else }}
- to:
    - ipBlock:
        cidr: {{ .cidr }}
  ports:
    - port: {{ .port }}
{{- end }}
{{- end -}}
