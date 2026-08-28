package org.opentripplanner.trakpi.otp

import java.time.Duration

/**
 * JVM flags OTP needs to build and serve on the otp2 image's JDK: the module opens plus the
 * native-access and unsafe-memory flags recent JDKs require, and a build/serve heap. These mirror
 * entur's own otp2 graph-builder. Override with TRAKPI_OTP_JVM_ARGS.
 */
private val DEFAULT_OTP_JVM_ARGS =
    listOf(
        "-server",
        "-XX:MaxRAMPercentage=80.0",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "-Dfile.encoding=UTF-8",
    )

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
    // OTP is launched directly (java -jar <otpJar> <phase args>), overriding the image's serve-download
    // entrypoint so build/serve phase args reach OTP. jvmArgs carry the flags and heap OTP needs.
    val otpJar: String = "otp-shaded.jar",
    val jvmArgs: List<String> = DEFAULT_OTP_JVM_ARGS,
    val fsGroup: Long? = 1000,
    // The OTP image runs as a named non-root user ("appuser"), which the kubelet can't verify against
    // runAsNonRoot. We pin its numeric uid so the pod is admitted and 1000 is the Buildbox default.
    // Can be overridden via TRAKPI_OTP_RUN_AS_USER if the image differs.
    val runAsUser: Long? = 1000,
    val imagePullPolicy: String = "IfNotPresent",
    // Image tag to pull, when it should differ from the run's --version label. Defaults to the version.
    val imageTag: String? = null,
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
                otpJar = env("TRAKPI_OTP_JAR") ?: "otp-shaded.jar",
                jvmArgs = args("TRAKPI_OTP_JVM_ARGS", DEFAULT_OTP_JVM_ARGS),
                fsGroup = env("TRAKPI_OTP_FS_GROUP")?.toLong() ?: 1000,
                runAsUser = env("TRAKPI_OTP_RUN_AS_USER")?.toLong() ?: 1000,
                imagePullPolicy = env("TRAKPI_OTP_IMAGE_PULL_POLICY") ?: "IfNotPresent",
                imageTag = env("TRAKPI_OTP_IMAGE_TAG"),
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
