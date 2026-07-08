variable "app_id" {
  description = "Entur application id (metadata.id in .entur/application.yaml)."
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev/tst/prd)."
  type        = string
}

variable "gha_service_account" {
  description = "GitHub Actions SA for this environment, e.g. gh-trakpiotp-<hash>-tst@ent-github-shr.iam.gserviceaccount.com. Known after `entur apply`."
  type        = string
}
