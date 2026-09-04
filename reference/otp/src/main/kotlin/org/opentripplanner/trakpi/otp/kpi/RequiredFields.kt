package org.opentripplanner.trakpi.otp.kpi

/**
 * A GraphQL [selection] (e.g. `{ debugOutput { totalTime } }`) and the [rootFields] to merge it into,
 * e.g. `setof("trip")`
 */
data class RequiredFields(val rootFields: Set<String>, val selection: String)