package org.opentripplanner.trakpi.otp.graphql

import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.parser.Parser

object GraphQlUtil {
    private val parser = Parser()

    /**
     * Inlines every named fragment spread in [document], replacing each `...F` with the (recursively
     * inlined) selections of fragment `F` and dropping the fragment definitions. Inline
     * fragments (`... on Type`) are left in place.
     */
    fun inlineFragments(document: Document): Document {
        val fragments = document.getDefinitionsOfType(FragmentDefinition::class.java).associateBy { it.name }
        if (fragments.isEmpty()) return document
        val definitions =
            document.definitions.mapNotNull { def ->
                when (def) {
                    is OperationDefinition -> def.transform { it.selectionSet(inlineSpreads(def.selectionSet, fragments, emptySet())) }
                    is FragmentDefinition -> null // inlined into the operations above
                    else -> def
                }
            }
        return document.transform { it.definitions(definitions) }
    }

    /**
     * Ensures the [required] sub-selection is present under the named top-level [rootFields] of a
     * fragment-inlined query [document], additively and without duplicating fields.
     *
     * Each field in [required] is merged into its match: a field already present keeps its own arguments
     * and gains only the missing leaves (so an `estimatedCalls(numberOfDepartures: 10)` never grows a
     * conflicting argument-free twin), and a field that is absent is added. The matched root fields are
     * de-aliased so the response key matches the field name.
     *
     * The [document] must not contain fragment spreads (like `...F`). This is because merging does not resolve
     * spreads, so fields reached only through a spread wouldn't support merging.
     *
     * A target field reachable only under an inline-fragment type condition cannot be merged safely, and
     * is rejected with an exception.
     */
    fun mergeFields(document: Document, rootFields: Set<String>, required: String): Document {
        val requiredSelections = selectionsOf(required)
        val definitions =
            document.definitions.map { def ->
                if (def is OperationDefinition) def.transform { it.selectionSet(mergeIntoRoots(def.selectionSet, rootFields, requiredSelections)) }
                else def
            }
        return document.transform { it.definitions(definitions) }
    }

    /** Replaces every named fragment spread with the fragment's own (recursively inlined) selections. */
    private fun inlineSpreads(selectionSet: SelectionSet, fragments: Map<String, FragmentDefinition>, active: Set<String>): SelectionSet {
        val selections =
            selectionSet.selections.flatMap { selection ->
                when (selection) {
                    is Field ->
                        listOf(selection.transform { b -> selection.selectionSet?.let { b.selectionSet(inlineSpreads(it, fragments, active)) } })
                    is InlineFragment ->
                        listOf(selection.transform { it.selectionSet(inlineSpreads(selection.selectionSet, fragments, active)) })
                    is FragmentSpread -> {
                        require(selection.directives.isEmpty()) { "Cannot inline fragment spread '...${selection.name}' carrying directives." }
                        require(selection.name !in active) { "Fragment cycle through '${selection.name}'." }
                        val def = checkNotNull(fragments[selection.name]) { "Unknown fragment '${selection.name}'." }
                        inlineSpreads(def.selectionSet, fragments, active + selection.name).selections
                    }
                    else -> listOf(selection)
                }
            }
        return selectionSet.transform { it.selections(selections) }
    }

    /** Merges [required] into each field named in [rootFields], recursing through top-level inline fragments. */
    private fun mergeIntoRoots(selectionSet: SelectionSet, rootFields: Set<String>, required: List<Selection<*>>): SelectionSet {
        val selections =
            selectionSet.selections.map { selection ->
                when {
                    selection is Field && selection.name in rootFields ->
                        selection.transform { it.alias(null).selectionSet(deepMerge(selection.selectionSet, required)) }
                    selection is InlineFragment ->
                        selection.transform { it.selectionSet(mergeIntoRoots(selection.selectionSet, rootFields, required)) }
                    else -> selection
                }
            }
        return selectionSet.transform { it.selections(selections) }
    }

    /** Presence-aware merge of [additions] into [target]: a matching field recurses, an absent field is appended. */
    private fun deepMerge(target: SelectionSet?, additions: List<Selection<*>>): SelectionSet {
        val result = (target?.selections ?: emptyList()).toMutableList()
        val hiddenKeys = inlineFieldKeys(result) - result.filterIsInstance<Field>().map { it.responseKey() }.toSet()
        for (addition in additions) {
            if (addition !is Field) {
                result += addition
                continue
            }
            val key = addition.responseKey()
            require(key !in hiddenKeys) { "Cannot merge '$key': it is present only under an inline-fragment type condition." }
            val idx = result.indexOfFirst { it is Field && it.responseKey() == key }
            val additionSubs = addition.selectionSet?.selections
            when {
                idx < 0 -> result += addition
                additionSubs.isNullOrEmpty() -> Unit // leaf already present
                else -> {
                    val existing = result[idx] as Field
                    result[idx] = existing.transform { it.selectionSet(deepMerge(existing.selectionSet, additionSubs)) }
                }
            }
        }
        return SelectionSet.newSelectionSet().selections(result).build()
    }

    /** Response keys of fields nested inside inline fragments of [selections] (their own level, recursively). */
    private fun inlineFieldKeys(selections: List<Selection<*>>): Set<String> =
        selections.filterIsInstance<InlineFragment>().flatMap { inline ->
            val subs = inline.selectionSet.selections
            subs.filterIsInstance<Field>().map { it.responseKey() } + inlineFieldKeys(subs)
        }.toSet()

    private fun Field.responseKey(): String = alias ?: name

    private fun selectionsOf(selection: String): List<Selection<*>> =
        (parser.parseDocument("query $selection").definitions.first() as OperationDefinition).selectionSet.selections
}
