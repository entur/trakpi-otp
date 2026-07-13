variable "app_id" {
  description = "Entur application id (metadata.id in .entur/application.yaml)."
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev/tst/prd)."
  type        = string
}

variable "gha_service_account" {
  description = "GitHub Actions SA for this environment, e.g. gh-trakpi-otp-<hash>-tst@ent-github-shr.iam.gserviceaccount.com. Known after `entur apply`."
  type        = string
}

variable "grafana_workload_identity_service_account" {
  description = "Grafana workload SA that reads this dataset."
  type        = string
}
