module "init" {
  source      = "github.com/entur/terraform-google-init//modules/init?ref=v1.0.0"
  app_id      = var.app_id
  environment = var.environment
}

resource "google_bigquery_dataset" "kpi_tracking" {
  dataset_id = "kpi_tracking"
  project    = module.init.app.project_id
  location   = "EU"
}

resource "google_bigquery_table" "kpi_metrics" {
  dataset_id          = google_bigquery_dataset.kpi_tracking.dataset_id
  table_id            = "kpi_metrics_v1"
  project             = module.init.app.project_id
  deletion_protection = false # flip to true once the schema is stable

  schema = jsonencode([
    { name = "run_id", type = "STRING", mode = "REQUIRED" },
    { name = "version", type = "STRING", mode = "REQUIRED" },
    { name = "application", type = "STRING", mode = "NULLABLE" },
    { name = "run_ts", type = "TIMESTAMP", mode = "REQUIRED" },
    { name = "is_reference_version", type = "BOOL", mode = "NULLABLE" },
    { name = "reference_version", type = "STRING", mode = "NULLABLE" },
    { name = "testset_version", type = "STRING", mode = "NULLABLE" },
    { name = "request_id", type = "STRING", mode = "REQUIRED" },
    { name = "method", type = "STRING", mode = "NULLABLE" },
    { name = "success", type = "BOOL", mode = "NULLABLE" },
    { name = "http_status_code", type = "STRING", mode = "NULLABLE" },
    { name = "http_status_class", type = "STRING", mode = "NULLABLE" },
    { name = "kpi_name", type = "STRING", mode = "NULLABLE" },
    { name = "value", type = "FLOAT64", mode = "NULLABLE" },
  ])

  time_partitioning {
    type  = "DAY"
    field = "run_ts"
  }
}

resource "google_bigquery_dataset_iam_member" "gha_data_editor" {
  project    = module.init.app.project_id
  dataset_id = google_bigquery_dataset.kpi_tracking.dataset_id
  role       = "roles/bigquery.dataEditor"
  member     = "serviceAccount:${var.gha_service_account}"
}

resource "google_project_iam_member" "gha_job_user" {
  project = module.init.app.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${var.gha_service_account}"
}

# grafana.entur.org reads this dataset through the shared BigQuery datasource, which queries as the Grafana
# pod's Workload Identity service account (authenticationType=gce). (See entur/grafana IaC, helm/grafana/env/prd.yaml).
# That SA needs dataViewer on the dataset.
resource "google_bigquery_dataset_iam_member" "grafana_data_viewer" {
  project    = module.init.app.project_id
  dataset_id = google_bigquery_dataset.kpi_tracking.dataset_id
  role       = "roles/bigquery.dataViewer"
  member     = "serviceAccount:${var.grafana_workload_identity_service_account}"
}
