{{/* Etiquetas comunes de todos los recursos del chart. */}}
{{- define "showcase.labels" -}}
app.kubernetes.io/part-of: showcase
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{/* Selector de un componente: app.kubernetes.io/name=<nombre>. */}}
{{- define "showcase.selector" -}}
app.kubernetes.io/name: {{ . }}
app.kubernetes.io/instance: showcase
{{- end -}}

{{/* initContainer que espera a que un puerto TCP acepte conexiones: (list host port). */}}
{{- define "showcase.waitFor" -}}
- name: wait-for-{{ regexReplaceAll "[.].*" (index .args 0) "" }}
  image: {{ .root.Values.waitImage }}
  securityContext:
    {{- include "showcase.containerSecurityContext" . | nindent 4 }}
  command: ["sh", "-c", "until nc -z -w 2 {{ index .args 0 }} {{ index .args 1 }}; do echo 'Esperando a {{ index .args 0 }}'; sleep 2; done"]
{{- end -}}

{{/* Contenedor sin privilegios: sin escalada, sin capacidades de Linux y con el sistema de ficheros de solo lectura. */}}
{{- define "showcase.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end -}}

{{/* Pod sin root con el usuario dado: (dict "user" uid "group" gid). */}}
{{- define "showcase.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: {{ .user }}
runAsGroup: {{ .group }}
fsGroup: {{ .group }}
seccompProfile:
  type: RuntimeDefault
{{- end -}}

{{/* Regla de salida hacia los pods de un componente del chart: (dict "name" nombre "port" puerto). */}}
{{- define "showcase.egressPod" -}}
- to:
    - podSelector:
        matchLabels:
          app.kubernetes.io/name: {{ .name }}
  ports:
    - port: {{ .port }}
{{- end -}}

{{/* Regla de salida hacia el DNS del clúster. */}}
{{- define "showcase.egressDns" -}}
- to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
      podSelector:
        matchLabels:
          k8s-app: kube-dns
  ports:
    - port: 53
      protocol: UDP
    - port: 53
      protocol: TCP
{{- end -}}

{{/* Origen: los microservicios que tienen definida una clave en values.yaml (database, rabbitmq, redis). */}}
{{- define "showcase.podsWith" -}}
{{- $names := list -}}
{{- range $name, $svc := .root.Values.services -}}
{{- if index $svc $.key -}}
{{- $names = append $names $name -}}
{{- end -}}
{{- end -}}
- podSelector:
    matchExpressions:
      - key: app.kubernetes.io/name
        operator: In
        values: {{ toJson $names }}
{{- end -}}
