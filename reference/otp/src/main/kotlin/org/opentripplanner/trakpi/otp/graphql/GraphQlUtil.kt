package org.opentripplanner.trakpi.otp.graphql

import graphql.language.Document
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.parser.Parser

object GraphQlUtil {
    private val parser = Parser()

    /**
     * Additively merges a sub-selection into named top-level fields of a parsed graphql query [Document].
     *
     * For each top-level field named in [rootFields] it appends [required]'s selections (duplicates are
     * legal for non-fragment fields and merge server-side, so no presence check is needed) and de-aliases
     * the field so its response key matches the field name.
     *
     * Note: fragments are not supported.
     */
    fun mergeFields(document: Document, rootFields: Set<String>, required: String): Document {
        val extra = selectionsOf(required)
        val definitions =
            document.definitions.map { def ->
                if (def is OperationDefinition) {
                    def.transform { it.selectionSet(mergeInto(def.selectionSet, rootFields, extra)) }
                } else {
                    def
                }
            }
        return document.transform { it.definitions(definitions) }
    }

    private fun mergeInto(selectionSet: SelectionSet, rootFields: Set<String>, extra: List<Selection<*>>): SelectionSet {
        val selections =
            selectionSet.selections.map { selection ->
                if (selection !is Field || selection.name !in rootFields) return@map selection
                val current = selection.selectionSet ?: return@map selection
                selection.transform {
                    it.alias(null).selectionSet(current.transform { s -> s.selections(current.selections + extra) })
                }
            }
        return selectionSet.transform { it.selections(selections) }
    }

    private fun selectionsOf(selection: String): List<Selection<*>> =
        (parser.parseDocument("query $selection").definitions.first() as OperationDefinition).selectionSet.selections
}
