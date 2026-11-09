package com.click.browser.engine

/** Home news/recommendations stay hidden until the user explicitly opts in. */
internal object HomeNewsFeedPolicy {
    fun isEnabled(storedValue: Boolean?): Boolean = storedValue == true
}
