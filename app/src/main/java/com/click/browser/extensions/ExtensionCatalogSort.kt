package com.click.browser.extensions

/**
 * Catalog ordering for the add-ons manager (1.8.9 B6). Pure and testable:
 * the bundled catalog ships in AMO order, which buries Mozilla's
 * Recommended picks; the default view now floats them first.
 */
internal enum class ExtensionCatalogSort(val label: String) {
    RECOMMENDED("Recommended first"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A");

    fun apply(entries: List<FirefoxAmoCatalogEntry>): List<FirefoxAmoCatalogEntry> = when (this) {
        RECOMMENDED -> entries.sortedWith(
            compareByDescending<FirefoxAmoCatalogEntry> { it.isRecommended }
                .thenBy { it.name.lowercase() }
        )
        NAME_ASC -> entries.sortedBy { it.name.lowercase() }
        NAME_DESC -> entries.sortedByDescending { it.name.lowercase() }
    }
}
