package {{ROOT_PACKAGE}}.engineering.ui.menu

/**
 * The menu's search: a **pure function over the rendered strings**, so it matches exactly what the engineer reads
 * (static labels mixed with runtime values) and is unit-tested.
 */
object EngineeringMenuFilter {

    /**
     * Keeps every row whose **title or description** contains [query] (trimmed, ignoring case) and drops a section
     * left with no rows — an empty header over nothing reads as a list that failed to load. A blank query returns
     * [sections] unchanged.
     */
    fun filter(sections: List<EngineeringMenuSection>, query: String): List<EngineeringMenuSection> {
        val needle = query.trim()
        if (needle.isEmpty()) return sections
        return sections.mapNotNull { section ->
            val matches = section.rows.filter { row ->
                row.title.contains(needle, ignoreCase = true) || row.description.contains(needle, ignoreCase = true)
            }
            if (matches.isEmpty()) null else section.copy(rows = matches)
        }
    }
}
