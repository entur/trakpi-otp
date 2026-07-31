package org.opentripplanner.trakpi.common

/**
 * Identifies a versioned request set.
 */
@JvmInline
value class TestsetVersion(val value: String) {
    init {
        require(value.isNotBlank()) { "testset version must not be blank" }
        require('/' !in value) { "testset version must not contain '/': $value" }
    }

    override fun toString() = value
}
