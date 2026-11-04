{{/* Common labels of all the chart's resources. */}}
{{- define "showcase.labels" -}}
app.kubernetes.io/part-of: showcase
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{/* Selector of a component: app.kubernetes.io/name=<name>. */}}
{{- define "showcase.selector" -}}
app.kubernetes.io/name: {{ . }}
app.kubernetes.io/instance: showcase
{{- end -}}

{{/* initContainer that waits for a TCP port to accept connections: (list host port). */}}
{{- define "showcase.waitFor" -}}
- name: wait-for-{{ regexReplaceAll "[.].*" (index .args 0) "" }}
  image: {{ .root.Values.waitImage }}
  securityContext:
    {{- include "showcase.containerSecurityContext" . | nindent 4 }}
  command: ["sh", "-c", "until nc -z -w 2 {{ index .args 0 }} {{ index .args 1 }}; do echo 'Esperando a {{ index .args 0 }}'; sleep 2; done"]
{{- end -}}

{{/* Unprivileged container: no escalation, no Linux capabilities and a read-only file system. */}}
{{- define "showcase.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end -}}

{{/* Non-root pod with the given user: (dict "user" uid "group" gid). */}}
{{- define "showcase.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: {{ .user }}
runAsGroup: {{ .group }}
fsGroup: {{ .group }}
seccompProfile:
  type: RuntimeDefault
{{- end -}}

{{/* Egress rule towards the pods of a chart component: (dict "name" name "port" port). */}}
{{- define "showcase.egressPod" -}}
- to:
    - podSelector:
        matchLabels:
          app.kubernetes.io/name: {{ .name }}
  ports:
    - port: {{ .port }}
{{- end -}}

{{/* Egress rule towards the cluster DNS. */}}
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

{{/* Source: the microservices that have a key defined in values.yaml (database, rabbitmq, redis). */}}
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
