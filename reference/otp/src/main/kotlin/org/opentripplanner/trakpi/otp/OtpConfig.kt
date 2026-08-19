package org.opentripplanner.trakpi.otp

import java.time.Duration

/**
 * Configuration for running OTP under test in Kubernetes. Only [imageRepo] is required; the rest default
 * and are overridable via `TRAKPI_OTP_*` env vars (surfaced in the trakpi-test Helm values), so nothing
 * cluster- or environment-specific is compiled in.
 */
data class OtpConfig(
    val imageRepo: String,
    val namespace: String,
    val clientName: String = "entur-trakpi",
    val podName: String = "trakpi-otp",
    val serviceName: String = "trakpi-otp",
    val port: Int = 8080,
    val graphqlPath: String = "/otp/transmodel/v3",
    val baseDir: String = "/otp",
    val configMapName: String = "otp-config",
    val configInitImage: String = "busybox:1.36",
    val buildStreetArgs: List<String> = listOf("--buildStreet"),
    val buildTransitArgs: List<String> = listOf("--loadStreet", "--save"),
    val serveArgs: List<String> = listOf("--load", "--serve"),
    val memory: String = "16Gi",
    val cpu: String = "4",
    val ephemeralStorage: String = "50Gi",
    val javaOpts: String? = null,
    val fsGroup: Long? = 1000,
    val imagePullPolicy: String = "IfNotPresent",
    // Identity of the pod trakpi runs in, set as the OTP pod's ownerReference so Kubernetes garbage-collects
    // the OTP pod if that pod dies before stop runs. Null (e.g. local runs) → no owner reference.
    val ownerPodName: String? = null,
    val ownerPodUid: String? = null,
    val readinessTimeout: Duration = Duration.ofMinutes(40),
    val deletionTimeout: Duration = Duration.ofMinutes(2),
    val pollInterval: Duration = Duration.ofSeconds(10),
) {
    companion object {
        private fun args(name: String, default: List<String>) =
            System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }?.split(Regex("\\s+")) ?: default

        /** Builds config from `TRAKPI_OTP_*` env vars, or null when `TRAKPI_OTP_IMAGE_REPO` is unset. */
        fun fromEnv(defaultNamespace: String): OtpConfig? {
            val repo = System.getenv("TRAKPI_OTP_IMAGE_REPO")?.takeIf { it.isNotBlank() } ?: return null
            fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            return OtpConfig(
                imageRepo = repo,
                namespace = env("TRAKPI_OTP_NAMESPACE") ?: defaultNamespace,
                clientName = env("TRAKPI_OTP_CLIENT_NAME") ?: "entur-trakpi",
                podName = env("TRAKPI_OTP_POD_NAME") ?: "trakpi-otp",
                serviceName = env("TRAKPI_OTP_SERVICE_NAME") ?: "trakpi-otp",
                port = env("TRAKPI_OTP_PORT")?.toInt() ?: 8080,
                graphqlPath = env("TRAKPI_OTP_GRAPHQL_PATH") ?: "/otp/transmodel/v3",
                baseDir = env("TRAKPI_OTP_BASE_DIR") ?: "/otp",
                configMapName = env("TRAKPI_OTP_CONFIGMAP") ?: "otp-config",
                configInitImage = env("TRAKPI_OTP_CONFIG_INIT_IMAGE") ?: "busybox:1.36",
                buildStreetArgs = args("TRAKPI_OTP_BUILD_STREET_ARGS", listOf("--buildStreet")),
                buildTransitArgs = args("TRAKPI_OTP_BUILD_TRANSIT_ARGS", listOf("--loadStreet", "--save")),
                serveArgs = args("TRAKPI_OTP_SERVE_ARGS", listOf("--load", "--serve")),
                memory = env("TRAKPI_OTP_MEMORY") ?: "16Gi",
                cpu = env("TRAKPI_OTP_CPU") ?: "4",
                ephemeralStorage = env("TRAKPI_OTP_EPHEMERAL_STORAGE") ?: "50Gi",
                javaOpts = env("TRAKPI_OTP_JAVA_OPTS"),
                fsGroup = env("TRAKPI_OTP_FS_GROUP")?.toLong() ?: 1000,
                imagePullPolicy = env("TRAKPI_OTP_IMAGE_PULL_POLICY") ?: "IfNotPresent",
                ownerPodName = env("POD_NAME"),
                ownerPodUid = env("POD_UID"),
                readinessTimeout = env("TRAKPI_OTP_READINESS_TIMEOUT_SECONDS")?.toLong()?.let(Duration::ofSeconds)
                    ?: Duration.ofMinutes(40),
                deletionTimeout = env("TRAKPI_OTP_DELETION_TIMEOUT_SECONDS")?.toLong()?.let(Duration::ofSeconds)
                    ?: Duration.ofMinutes(2),
                pollInterval = env("TRAKPI_OTP_POLL_INTERVAL_SECONDS")?.toLong()?.let(Duration::ofSeconds)
                    ?: Duration.ofSeconds(10),
            )
        }
    }
}
