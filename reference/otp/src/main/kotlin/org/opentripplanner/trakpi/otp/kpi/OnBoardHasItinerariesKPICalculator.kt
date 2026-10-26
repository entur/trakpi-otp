package org.opentripplanner.trakpi.otp.kpi

import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.NullValue
import graphql.language.ObjectValue
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.language.Value
import graphql.language.VariableReference
import graphql.parser.Parser
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.opentripplanner.trakpi.otp.OtpRequestBody
import org.opentripplanner.trakpi.otp.graphql.GraphQlUtil
import org.opentripplanner.trakpi.otp.tripObject
import org.opentripplanner.trakpi.otp.tripPatterns
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

/**
 * Whether an on-board `trip` request returned any itineraries: 1.0 when it did, 0.0 when it did not.
 * Null for requests that do not start on-board, and for responses without a `trip` object.
 */
class OnBoardHasItinerariesKPICalculator : OtpRequestResponseKPICalculator {
    override val requiredFields = RequiredFields(setOf("trip"), "{ tripPatterns { __typename } }")

    override fun calculate(request: TravelPlannerRequest, response: TravelPlannerResponse): Kpi? {
        if (!isOnBoard(request)) return null
        response.tripObject() ?: return null
        return Kpi("onBoardHasItineraries", if (response.tripPatterns().isNotEmpty()) 1.0 else 0.0)
    }

    private companion object {
        private val parser = Parser()

        /** True when a `trip` field of [request] has a `from` location with a `serviceJourneyLocation` set. */
        fun isOnBoard(request: TravelPlannerRequest): Boolean {
            val body = runCatching { OtpRequestBody.parse(request.raw) }.getOrNull() ?: return false
            val document = runCatching { GraphQlUtil.inlineFragments(parser.parseDocument(body.query)) }.getOrNull() ?: return false
            val variables = body.variables as? JsonObject
            return document.getDefinitionsOfType(OperationDefinition::class.java)
                .flatMap { tripFields(it.selectionSet) }
                .any { trip -> trip.arguments.firstOrNull { it.name == "from" }?.value?.let { hasServiceJourneyLocation(it, variables) } ?: false }
        }

        /** The top-level `trip` fields of [selectionSet], including those under inline fragments. */
        private fun tripFields(selectionSet: SelectionSet): List<Field> =
            selectionSet.selections.flatMap { selection ->
                when (selection) {
                    is Field -> if (selection.name == "trip") listOf(selection) else emptyList()
                    is InlineFragment -> tripFields(selection.selectionSet)
                    else -> emptyList()
                }
            }

        /** True when the `from` [location], given inline or as a variable, sets a non-null `serviceJourneyLocation`. */
        private fun hasServiceJourneyLocation(location: Value<*>, variables: JsonObject?): Boolean =
            when (location) {
                is ObjectValue ->
                    when (val value = location.objectFields.firstOrNull { it.name == SERVICE_JOURNEY_LOCATION }?.value) {
                        null, is NullValue -> false
                        is VariableReference -> variables?.get(value.name).let { it != null && it != JsonNull }
                        else -> true
                    }
                is VariableReference -> (variables?.get(location.name) as? JsonObject)?.get(SERVICE_JOURNEY_LOCATION).let { it != null && it != JsonNull }
                else -> false
            }

        private const val SERVICE_JOURNEY_LOCATION = "serviceJourneyLocation"
    }
}
