{{/*
Expand the name of the chart.
*/}}
{{- define "vader.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "vader.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "vader.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "vader.labels" -}}
helm.sh/chart: {{ include "vader.chart" . }}
{{ include "vader.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "vader.selectorLabels" -}}
app.kubernetes.io/name: {{ include "vader.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Pod labels
*/}}
{{- define "vader.podLabels" -}}
{{- range $key, $val := .Values.config.podLabels }}
{{ $key }}: {{ $val | quote }}
{{- end }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "vader.serviceAccountName" -}}
{{- if .Values.k8s.serviceAccount.create }}
{{- default (include "vader.fullname" .) .Values.k8s.serviceAccount.name }}
{{- else }}
{{- default "vader" }}
{{- end }}
{{- end }}

{{- define "vader.core.coreServer.image" -}}
{{ .Values.vader.components.core.coreServer.image.registry }}/{{ .Values.vader.components.core.coreServer.image.repository }}:{{ .Values.vader.components.core.coreServer.image.tag }}
{{- end }}

{{- define "vader.core.coreUi.image" -}}
{{ .Values.vader.components.core.coreUi.image.registry }}/{{ .Values.vader.components.core.coreUi.image.repository }}:{{ .Values.vader.components.core.coreUi.image.tag }}
{{- end }}
