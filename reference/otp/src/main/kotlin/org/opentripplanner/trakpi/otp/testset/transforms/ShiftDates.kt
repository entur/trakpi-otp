package org.opentripplanner.trakpi.otp.testset.transforms

import graphql.language.ArrayValue
import graphql.language.Document
import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.ObjectValue
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.language.StringValue
import graphql.language.Value
import graphql.language.VariableDefinition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.opentripplanner.trakpi.otp.testset.OtpRequest
import org.opentripplanner.trakpi.testset.RequestTransform

/**
 * Moves a request [shift] into the future, so that a testset searches days that still have timetable data
 * while it is in use.
 *
 * - Every date and date-time value in the request moves by [shift], whether it is written in the query or passed
 *   as a variable. Date-times move in [zone] local time, so the weekday and the time of day stay the
 *   same across a daylight-saving change, and the result carries the [zone] offset.
 *
 * Note: A date inside another value, such as an id or a `pageCursor`, can't be moved and stays as it is. A request
 * without a date or date-time is left as it is.
 */
class ShiftDates(
    private val shift: Period,
    private val zone: ZoneId = ZoneId.of("Europe/Oslo"),
) : RequestTransform<OtpRequest> {
    override fun apply(request: OtpRequest): OtpRequest = request.mapAst(::shiftDocument).mapVariables { it?.let(::shiftJson) }

    /** Moves [text] by [shift] when it is an ISO date or date-time, otherwise null. */
    private fun shiftText(text: String): String? {
        if (!ISO_DATE_PREFIX.containsMatchIn(text)) return null
        return try {
            val value = OffsetDateTime.parse(text)
            value.atZoneSameInstant(zone).plus(shift).toOffsetDateTime().toString()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(text).plus(shift).toString()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDate.parse(text).plus(shift).toString()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    private fun shiftDocument(document: Document): Document {
        val definitions =
            document.definitions.map { definition ->
                if (definition !is OperationDefinition) definition
                else
                    definition.transform { builder ->
                        builder.variableDefinitions(definition.variableDefinitions.map(::shiftVariableDefault))
                        builder.selectionSet(shiftSelections(definition.selectionSet))
                    }
            }
        return document.transform { it.definitions(definitions) }
    }

    private fun shiftVariableDefault(definition: VariableDefinition): VariableDefinition {
        val default = definition.defaultValue ?: return definition
        return definition.transform { it.defaultValue(shiftValue(default)) }
    }

    private fun shiftSelections(selectionSet: SelectionSet): SelectionSet {
        val selections =
            selectionSet.selections.map { selection ->
                when (selection) {
                    is Field ->
                        selection.transform { builder ->
                            builder.arguments(selection.arguments.map { argument -> argument.transform { it.value(shiftValue(argument.value)) } })
                            selection.selectionSet?.let { builder.selectionSet(shiftSelections(it)) }
                        }
                    is InlineFragment -> selection.transform { it.selectionSet(shiftSelections(selection.selectionSet)) }
                    else -> selection
                }
            }
        return selectionSet.transform { it.selections(selections) }
    }

    private fun shiftValue(value: Value<*>): Value<*> =
        when (value) {
            is StringValue -> shiftText(value.value)?.let { StringValue.of(it) } ?: value
            is ObjectValue -> value.transform { builder -> builder.objectFields(value.objectFields.map { field -> field.transform { it.value(shiftValue(field.value)) } }) }
            is ArrayValue -> value.transform { it.values(value.values.map(::shiftValue)) }
            else -> value
        }

    private fun shiftJson(element: JsonElement): JsonElement =
        when (element) {
            is JsonPrimitive -> if (element.isString) shiftText(element.content)?.let(::JsonPrimitive) ?: element else element
            is JsonObject -> JsonObject(element.mapValues { shiftJson(it.value) })
            is JsonArray -> JsonArray(element.map(::shiftJson))
        }


    private companion object {
        /** Only values starting like an ISO date are parsed, so most strings are skipped cheaply. */
        val ISO_DATE_PREFIX = Regex("^\\d{4}-\\d{2}-\\d{2}")
    }
}
