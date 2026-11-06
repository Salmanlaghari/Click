package com.click.browser.engine

/** Selects the DNS response path without coupling policy to sockets or Android services. */
internal object DnsResolutionPolicy {
    fun <T> resolve(
        dohSucceeded: Boolean,
        strictDns: Boolean,
        onDoh: () -> T,
        onPlainDnsFallback: () -> T,
        onBlocked: () -> T,
    ): T = when {
        dohSucceeded -> onDoh()
        strictDns -> onBlocked()
        else -> onPlainDnsFallback()
    }
}
