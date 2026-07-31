package org.opentripplanner.trakpi.common

/** Identifies a build of the planner under test, e.g. a commit hash or release label. */
@JvmInline
value class PlannerVersion(val value: String) {
    init {
        require(value.isNotBlank()) { "planner version must not be blank" }
    }

    override fun toString() = value
}
