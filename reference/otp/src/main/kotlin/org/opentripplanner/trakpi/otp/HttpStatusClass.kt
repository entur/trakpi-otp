package org.opentripplanner.trakpi.otp

/**
 * The class of an HTTP status code — its leading digit — [label]ed as "1xx".."5xx". Lets results be
 * grouped by broad outcome (client vs. server error, success) without pinning to an exact code.
 */
internal enum class HttpStatusClass(val label: String) {
    INFORMATIONAL("1xx"),
    SUCCESS("2xx"),
    REDIRECTION("3xx"),
    CLIENT_ERROR("4xx"),
    SERVER_ERROR("5xx"),
    UNKNOWN("unknown");

    companion object {
        fun of(code: Int): HttpStatusClass =
            when (code / 100) {
                1 -> INFORMATIONAL
                2 -> SUCCESS
                3 -> REDIRECTION
                4 -> CLIENT_ERROR
                5 -> SERVER_ERROR
                else -> UNKNOWN
            }
    }
}
