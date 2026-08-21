package org.opentripplanner.trakpi.otp

import java.time.Instant
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.orchestrator.PlannerOrchestrator

/**
 * Runs the OTP version under test in Kubernetes: `start` builds the graph and serves it, `stop` tears it
 * down again. The version is the image tag and start runs `${imageRepo}:${version}` unmodified.
 */
class OtpOrchestrator(private val cluster: OtpCluster, private val config: OtpConfig) : PlannerOrchestrator {

    /** The in-cluster URL the served OTP is reachable at */
    fun endpoint(): String = cluster.endpoint()

    override fun start(version: PlannerVersion, args: String?) {
        val image = "${config.imageRepo}:${version.value}"
        log("Starting OTP $image (pod ${config.podName})")
        cluster.teardown()
        awaitGone()
        cluster.launch(image, version.value)
        awaitReady()
        log("OTP is serving at ${cluster.endpoint()}")
    }

    override fun stop(version: PlannerVersion) {
        log("Stopping OTP (pod ${config.podName})")
        cluster.teardown()
    }

    /** Polls until the instance reports serving */
    private fun awaitReady() {
        val readinessDeadline = Instant.now().plus(config.readinessTimeout)
        while (Instant.now().isBefore(readinessDeadline)) {
            val status = cluster.probeStatus()
            when {
                status.ready -> return
                status.failure != null ->
                    throw OtpStartupException(
                        "OTP container '${status.failure.container}' failed (exit ${status.failure.exitCode})" +
                            cluster.logs(status.failure.container)
                    )
                else -> log("waiting... ${status.stateDescription}")
            }
            Thread.sleep(config.pollInterval.toMillis())
        }
        throw OtpStartupException("OTP did not become ready within ${config.readinessTimeout}${cluster.logs()}")
    }

    /** Polls until the torn-down pod is gone */
    private fun awaitGone() {
        val deletionDeadline = Instant.now().plus(config.deletionTimeout)
        while (Instant.now().isBefore(deletionDeadline)) {
            val status = cluster.probeStatus()
            if (!status.present) return
            log("tearing down... ${status.stateDescription}")
            Thread.sleep(config.pollInterval.toMillis())
        }
        throw OtpStartupException("OTP pod ${config.podName} was not deleted within ${config.deletionTimeout}")
    }

    private fun log(message: String) = println("[otp-orchestrator] $message")

    companion object {
        /**
         * Builds an orchestrator from the environment (in-cluster Kubernetes client), or null when
         * `TRAKPI_OTP_IMAGE_REPO` is unset, so `start`/`stop` report that orchestration is not configured.
         */
        fun createOrNull(): OtpOrchestrator? {
            val cluster = Fabric8OtpCluster.createOrNull() ?: return null
            return OtpOrchestrator(cluster, cluster.config)
        }
    }
}

/** Signals that the OTP pod failed to build or serve. Carries the failing container's log tail. */
class OtpStartupException(message: String) : RuntimeException(message)
