{{/*
Expand the name of the chart.
*/}}
{{- define "security-log-search.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "security-log-search.fullname" -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- printf "%s" $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "security-log-search.labels" -}}
app.kubernetes.io/name: {{ include "security-log-search.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{- define "security-log-search.selectorLabels" -}}
app.kubernetes.io/name: {{ include "security-log-search.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
ServiceAccount 이름 — serviceAccount.create=false 면 외부 SA 이름을 그대로 사용.
*/}}
{{- define "security-log-search.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "security-log-search.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/*
Secret 이름 — existingSecret 이 지정되면 그것을, 아니면 chart 가 만드는 기본 이름.
*/}}
{{- define "security-log-search.secretName" -}}
{{- if .Values.secrets.existingSecret -}}
{{- .Values.secrets.existingSecret -}}
{{- else -}}
{{- printf "%s-secrets" (include "security-log-search.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "security-log-search.configMapName" -}}
{{- printf "%s-config" (include "security-log-search.fullname" .) -}}
{{- end -}}
