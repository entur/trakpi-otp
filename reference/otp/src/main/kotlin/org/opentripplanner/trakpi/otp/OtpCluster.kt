package org.opentripplanner.trakpi.otp

/**
 * A Kubernetes OTP cluster
 */
interface OtpCluster {
    /** The in-cluster URL the served OTP is reachable at */
    fun endpoint(): String

    /** Create the OTP pod (running [image], annotated with [version]) and its service. */
    fun launch(image: String, version: String)

    /** Probe for whether the pod is present and serving along with a phase description, and any failure. */
    fun probeStatus(): ClusterStatus

    /** Tail of the given container's log, or of every OTP container when [container] is null. */
    fun logs(container: String? = null): String

    /** Delete the pod and service. Safe to call when they do not exist. */
    fun teardown()
}

/**
 * A point-in-time view of the OTP instance: [present] whether the pod exists at all, [ready] once it
 * answers `serverInfo`, [failure] set when a build container has died, and [phase] a human description of
 * what is running, for progress logging.
 */
data class ClusterStatus(
    val present: Boolean,
    val ready: Boolean,
    val phase: String,
    val failure: ContainerFailure?,
)

/** A container that exited in a way that fails the run: a build container non-zero, or serve terminating. */
data class ContainerFailure(val container: String, val exitCode: Int)
