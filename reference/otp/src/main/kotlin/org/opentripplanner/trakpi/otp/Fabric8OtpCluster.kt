package org.opentripplanner.trakpi.otp

import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.EnvVar
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder
import io.fabric8.kubernetes.api.model.SecurityContextBuilder
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.api.model.VolumeBuilder
import io.fabric8.kubernetes.api.model.VolumeMount
import io.fabric8.kubernetes.api.model.VolumeMountBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder

/**
 * fabric8-backed [OtpCluster]: builds and applies the OTP pod (config-copy + street + transit init-containers
 * sharing one emptyDir base, then a serve container) and its service, reads pod status, and fetches logs.
 * This class is the only place the Kubernetes client is used.
 */
class Fabric8OtpCluster(
    private val client: KubernetesClient,
    val config: OtpConfig,
    private val probe: OtpReadinessProbe,
) : OtpCluster {

    override fun endpoint(): String = "http://${config.serviceName}:${config.port}${config.graphqlPath}"

    override fun launch(image: String, version: String) {
        client.pods().inNamespace(config.namespace).resource(pod(image, version)).create()
        client.services().inNamespace(config.namespace).resource(service()).create()
    }

    override fun probeStatus(): ClusterStatus {
        val pod =
            client.pods().inNamespace(config.namespace).withName(config.podName).get()
                ?: return ClusterStatus(present = false, ready = false, phase = "absent", failure = null)
        val init = pod.status?.initContainerStatuses ?: emptyList()
        val main = pod.status?.containerStatuses ?: emptyList()
        val failure =
            // A build init-container that exited non-zero, or the serve container terminating at all.
            init.firstNotNullOfOrNull { cs ->
                cs.state?.terminated?.takeIf { it.exitCode != 0 }?.let { ContainerFailure(cs.name, it.exitCode) }
            }
                ?: main.firstNotNullOfOrNull { cs ->
                    cs.state?.terminated?.let { ContainerFailure(cs.name, it.exitCode) }
                }
                ?: if (pod.status?.phase == "Failed") ContainerFailure(config.podName, -1) else null
        val phase = (init + main).firstOrNull { it.state?.running != null }?.name ?: pod.status?.phase ?: "scheduling"
        // Once the container is actually up, we can also probe if it responds
        val serving = main.any { it.name == SERVE_CONTAINER && it.state?.running != null }
        val ready = failure == null && serving && probe.responds(endpoint())
        return ClusterStatus(present = true, ready = ready, phase = phase, failure = failure)
    }

    override fun logs(container: String?): String {
        val names = container?.let { listOf(it) } ?: (BUILD_CONTAINERS + SERVE_CONTAINER)
        return names
            .mapNotNull { name ->
                runCatching {
                        client.pods().inNamespace(config.namespace).withName(config.podName).inContainer(name).tailingLines(50).getLog()
                    }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "\n--- $name ---\n$it" }
            }
            .joinToString("")
    }

    override fun teardown() {
        runCatching { client.services().inNamespace(config.namespace).withName(config.serviceName).delete() }
        runCatching { client.pods().inNamespace(config.namespace).withName(config.podName).delete() }
    }

    private fun pod(image: String, version: String): Pod {
        val base = VolumeMountBuilder().withName(BASE_VOLUME).withMountPath(config.baseDir).build()
        val configRo = VolumeMountBuilder().withName(CONFIG_VOLUME).withMountPath(CONFIG_MOUNT).withReadOnly(true).build()
        val pod =
            PodBuilder()
                .withNewMetadata()
                .withName(config.podName)
                .withNamespace(config.namespace)
                .addToLabels("app", config.serviceName)
                .addToAnnotations(VERSION_ANNOTATION, version)
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withVolumes(
                    VolumeBuilder()
                        .withName(BASE_VOLUME)
                        .withNewEmptyDir()
                        .withSizeLimit(Quantity(config.ephemeralStorage))
                        .endEmptyDir()
                        .build(),
                    VolumeBuilder()
                        .withName(CONFIG_VOLUME)
                        .withNewConfigMap()
                        .withName(config.configMapName)
                        .endConfigMap()
                        .build(),
                )
                .withInitContainers(
                    // Copy the read-only ConfigMap into the writable base dir OTP builds in.
                    ContainerBuilder()
                        .withName("fetch-config")
                        .withImage(config.configInitImage)
                        .withCommand("sh", "-c", "cp ${CONFIG_MOUNT}/* ${config.baseDir}/")
                        // Runs as the pod-level runAsUser (busybox has no non-root user of its own).
                        .withSecurityContext(restrictedSecurityContext())
                        .withVolumeMounts(base, configRo)
                        .build(),
                    otpContainer(BUILD_CONTAINERS[0], image, config.buildStreetArgs, base, expose = false),
                    otpContainer(BUILD_CONTAINERS[1], image, config.buildTransitArgs, base, expose = false),
                )
                .withContainers(otpContainer(SERVE_CONTAINER, image, config.serveArgs, base, expose = true))
                .endSpec()
                .build()
        pod.spec.securityContext =
            PodSecurityContextBuilder()
                .withRunAsNonRoot(true)
                .withNewSeccompProfile()
                .withType("RuntimeDefault")
                .endSeccompProfile()
                .apply { config.fsGroup?.let { withFsGroup(it) } }
                // Pod-level so every container (busybox init + the otp2 build/serve containers, whose
                // "appuser" is non-numeric) runs as a verifiable non-root uid.
                .apply { config.runAsUser?.let { withRunAsUser(it) } }
                .build()
        pod.metadata.ownerReferences = ownerReferences()
        return pod
    }

    private fun otpContainer(name: String, image: String, phaseArgs: List<String>, base: VolumeMount, expose: Boolean) =
        ContainerBuilder()
            .withName(name)
            .withImage(image)
            .withImagePullPolicy(config.imagePullPolicy)
            .withSecurityContext(restrictedSecurityContext())
            // Launch OTP directly rather than the image's serve-download entrypoint, so our build/serve
            // phase args reach OTP instead of the graph wrapper (which only downloads a prebuilt graph).
            .withCommand(listOf("java") + config.jvmArgs + listOf("-jar", config.otpJar))
            .withArgs(phaseArgs + config.baseDir)
            .withResources(
                ResourceRequirementsBuilder()
                    .addToRequests("memory", Quantity(config.memory))
                    .addToRequests("cpu", Quantity(config.cpu))
                    .addToRequests("ephemeral-storage", Quantity(config.ephemeralStorage))
                    .addToLimits("memory", Quantity(config.memory))
                    .addToLimits("ephemeral-storage", Quantity(config.ephemeralStorage))
                    .build()
            )
            .withEnv(EnvVar("TZ", "Europe/Oslo", null))
            .withVolumeMounts(base)
            .apply { if (expose) addNewPort().withContainerPort(config.port).endPort() }
            .build()

    /** Container security context satisfying the "restricted" Pod Security Standard the cluster enforces. */
    private fun restrictedSecurityContext() =
        SecurityContextBuilder()
            .withAllowPrivilegeEscalation(false)
            .withRunAsNonRoot(true)
            .withNewCapabilities()
            .withDrop("ALL")
            .endCapabilities()
            .withNewSeccompProfile()
            .withType("RuntimeDefault")
            .endSeccompProfile()
            .build()

    private fun service(): Service =
        ServiceBuilder()
            .withNewMetadata()
            .withName(config.serviceName)
            .withNamespace(config.namespace)
            .withOwnerReferences(ownerReferences())
            .endMetadata()
            .withNewSpec()
            .addToSelector("app", config.serviceName)
            .addNewPort()
            .withPort(config.port)
            .withNewTargetPort(config.port)
            .endPort()
            .endSpec()
            .build()

    /**
     * Owner reference to the pod trakpi runs in, so Kubernetes garbage-collects the OTP pod and service if
     * that pod dies (kill/crash/deadline) before stop runs. Empty when its identity is unknown (e.g. local
     * runs), in which case teardown relies on the finally block and the next launch's cleanup.
     */
    private fun ownerReferences() =
        if (config.ownerPodName != null && config.ownerPodUid != null)
            listOf(
                OwnerReferenceBuilder()
                    .withApiVersion("v1")
                    .withKind("Pod")
                    .withName(config.ownerPodName)
                    .withUid(config.ownerPodUid)
                    .withController(false)
                    .withBlockOwnerDeletion(false)
                    .build()
            )
        else emptyList()

    companion object {
        private const val BASE_VOLUME = "otp-base"
        private const val CONFIG_VOLUME = "otp-config"
        private const val CONFIG_MOUNT = "/config-ro"
        private const val VERSION_ANNOTATION = "trakpi.entur.io/version"
        private val BUILD_CONTAINERS = listOf("build-street", "build-transit")
        private const val SERVE_CONTAINER = "otp"

        /** Builds a fabric8-backed cluster with an in-cluster client, or null when orchestration is not configured. */
        fun createOrNull(): Fabric8OtpCluster? {
            if (System.getenv("TRAKPI_OTP_IMAGE_REPO").isNullOrBlank()) return null
            val client = KubernetesClientBuilder().build()
            val config = OtpConfig.fromEnv(defaultNamespace = client.namespace ?: "default") ?: return null
            return Fabric8OtpCluster(client, config, ServerInfoProbe(config.clientName))
        }
    }
}
