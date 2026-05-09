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
