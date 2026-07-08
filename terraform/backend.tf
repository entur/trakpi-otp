terraform {
  backend "gcs" {
    bucket = "ent-gcs-tfa-trakpiotp"
    prefix = "bigquery"
  }
}
