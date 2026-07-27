package org.opentripplanner.trakpi.config

import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.bufferedReader

/**
 * Resolves a [TrakpiConfig] from an optional properties file overlaid with command-line overrides.
 * Override values take precedence over the file; values are trimmed and blank ones ignored.
 */
object TrakpiConfigLoader {
    fun load(configFile: Path?, commandLineOverrides: Map<String, String>): TrakpiConfig {
        val fromFile = if (configFile != null) readProperties(configFile) else emptyMap()
        return TrakpiConfig.from(merge(normalize(fromFile), normalize(commandLineOverrides)))
    }

    /** Reads the raw key/value pairs from a `.properties` [file], without normalizing them. */
    private fun readProperties(file: Path): Map<String, String> {
        val properties = Properties()
        file.bufferedReader(Charsets.UTF_8).use(properties::load)
        return properties.entries.associate { (key, value) -> key.toString() to value.toString() }
    }

    /** Trims each value and drops blank ones. */
    private fun normalize(source: Map<String, String>): Map<String, String> =
        source.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }

    /** Merges [sources] in order. A later source overwrites the values of an earlier one where their keys match. */
    private fun merge(vararg sources: Map<String, String>): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        for (source in sources) {
            values.putAll(source)
        }
        return values
    }
}
