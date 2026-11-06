package org.opentripplanner.trakpi.otp.testset

import graphql.language.Argument
import graphql.language.Document
import graphql.language.Field
import graphql.language.InlineFragment
import graphql.language.NullValue
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.language.StringValue
import graphql.language.VariableReference
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.opentripplanner.trakpi.otp.OtpRequestBody
import org.opentripplanner.trakpi.testset.Request

/**
 * Writes the moment a request was sent into the search-time arguments it leaves out, so the request keeps
 * searching that moment however late it is run.
 *
 * Times are written in [zone] local time. A body that can't be parsed is returned as it is.
 */
class SearchTimeWriter(private val zone: ZoneId = ZoneId.of("Europe/Oslo")) {
    fun write(body: String, sentAt: Instant): String {
        val request = runCatching { OtpRequestCodec.deserialize(Request("", body)) }.getOrElse { return body }
        val variables = OtpRequestBody.parse(body).variables as? JsonObject ?: JsonObject(emptyMap())
        val filler = Filler(sentAt.atZone(zone).toOffsetDateTime(), variables)
        // fill runs first: it finds the unset variables that withVariablesSet then sets.
        return request.mapAst(filler::fill).mapVariables(filler::withVariablesSet).toRequest().body
    }

    private class Filler(private val searchTime: OffsetDateTime, private val variables: JsonObject) {
        private val variablesToSet = mutableMapOf<String, JsonElement>()
        private val variablesWithDefault = mutableSetOf<String>()

        /** [variables] with every unset search-time variable found by [fill] set; unchanged when there is none. */
        fun withVariablesSet(variables: JsonElement?): JsonElement? =
            if (variablesToSet.isEmpty()) variables else JsonObject((variables as? JsonObject).orEmpty() + variablesToSet)

        fun fill(document: Document): Document {
            document.definitions
                .filterIsInstance<OperationDefinition>()
                .flatMap { it.variableDefinitions }
                .filter { it.defaultValue != null && it.defaultValue !is NullValue }
                .mapTo(variablesWithDefault) { it.name }
            val definitions =
                document.definitions.map { definition ->
                    if (definition is OperationDefinition) definition.transform { it.selectionSet(fillSelections(definition.selectionSet, parent = null)) }
                    else definition
                }
            return document.transform { it.definitions(definitions) }
        }

        private fun fillSelections(selectionSet: SelectionSet, parent: String?): SelectionSet {
            val selections =
                selectionSet.selections.map { selection ->
                    when (selection) {
                        is Field ->
                            selection.transform { builder ->
                                builder.arguments(fillArguments(selection, parent))
                                selection.selectionSet?.let { builder.selectionSet(fillSelections(it, parent = selection.name)) }
                            }
                        is InlineFragment -> selection.transform { it.selectionSet(fillSelections(selection.selectionSet, parent)) }
                        else -> selection
                    }
                }
            return selectionSet.transform { it.selections(selections) }
        }

        private fun fillArguments(field: Field, parent: String?): List<Argument> {
            val (name, value) =
                when {
                    field.name == "trip" && !isSet(field.arguments, "pageCursor") -> "dateTime" to searchTime.toString()
                    field.name == "estimatedCalls" && parent == "serviceJourney" -> "date" to searchTime.toLocalDate().toString()
                    field.name == "estimatedCalls" -> "startTime" to searchTime.toString()
                    else -> return field.arguments
                }
            val existing = field.arguments.firstOrNull { it.name == name }
            if (existing == null || existing.value is NullValue) return field.arguments.filter { it.name != name } + Argument(name, StringValue.of(value))
            val reference = existing.value as? VariableReference ?: return field.arguments
            if (isUnset(reference.name)) variablesToSet[reference.name] = JsonPrimitive(value)
            return field.arguments
        }

        /** Whether [name] is given a value, inline or through a variable that is set. */
        private fun isSet(arguments: List<Argument>, name: String): Boolean {
            val value = arguments.firstOrNull { it.name == name }?.value ?: return false
            return when (value) {
                is NullValue -> false
                is VariableReference -> !isUnset(value.name)
                else -> true
            }
        }

        private fun isUnset(variable: String): Boolean = variable !in variablesWithDefault && variables[variable].let { it == null || it is JsonNull }
    }
}
