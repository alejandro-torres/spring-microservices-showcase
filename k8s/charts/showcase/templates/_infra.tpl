{{/*
Where each piece of infrastructure lives: inside the chart (postgres.enabled, redis.enabled, rabbitmq.enabled) or a
managed service outside the cluster (externalDatabase, externalRedis, externalRabbitmq).
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
Egress rule towards a piece of infrastructure: to the chart's pod or, if external, to its network (cidr).
(dict "root" . "enabled" bool "name" pod name "port" port "cidr" external network)
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
