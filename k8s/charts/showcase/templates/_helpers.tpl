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
  command: ["sh", "-c", "until nc -z {{ index .args 0 }} {{ index .args 1 }}; do echo 'Esperando a {{ index .args 0 }}'; sleep 2; done"]
{{- end -}}
