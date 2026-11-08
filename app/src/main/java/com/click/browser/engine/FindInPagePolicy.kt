package com.click.browser.engine

internal sealed class FindInPageAction {
    data class Search(val query: String) : FindInPageAction()
    object Clear : FindInPageAction()
}

/** Keeps engine highlights and the dialog lifecycle in sync. */
internal object FindInPagePolicy {

    fun actionForQuery(query: String): FindInPageAction =
        if (query.isBlank()) FindInPageAction.Clear else FindInPageAction.Search(query)

    fun dismiss(clearMatches: () -> Unit, hideDialog: () -> Unit) {
        try {
            clearMatches()
        } finally {
            hideDialog()
        }
    }
}
