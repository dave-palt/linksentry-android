package com.dav3.linksentry.domain.system

import com.dav3.linksentry.domain.model.HandlerApp

/** One persisted preference/usage row: target key → chosen app → usage. */
data class HandlerUsage(
    val key: String,
    val packageName: String,
    val count: Int,
    val lastUsed: Long,
)

/**
 * Orders the handler list the way an app launcher orders apps:
 * - tier 1: apps the user has used for THIS domain (most recent first, count
 *   breaking ties);
 * - tier 2: apps used for this scheme overall (same ordering);
 * - tier 3: everything else, browsers first then alphabetical.
 */
object HandlerRanker {

    private const val DOMAIN_PREFIX = "domain:"
    private const val SCHEME_PREFIX = "scheme:"
    private const val PSEUDO_PREFIX = "pseudo:"

    fun domainKey(host: String): String = DOMAIN_PREFIX + host.lowercase()
    fun schemeKey(scheme: String): String = SCHEME_PREFIX + scheme.lowercase()

    /** Usage key for pseudo entries (copy actions) — scoped per domain so
     *  "copy" on github.com doesn't float to the top on paypal.com. */
    fun pseudoKey(host: String): String = PSEUDO_PREFIX + host.lowercase()

    fun rank(
        handlers: List<HandlerApp>,
        usage: List<HandlerUsage>,
        host: String,
        scheme: String,
    ): List<HandlerApp> {
        val domainTier = usage.filter { it.key == domainKey(host) }
        val schemeTier = usage.filter { it.key == schemeKey(scheme) }

        fun List<HandlerUsage>.byPreference(): List<String> = sortedWith(
            compareByDescending<HandlerUsage> { it.lastUsed }
                .thenByDescending { it.count },
        ).map { it.packageName }

        val domainOrder = domainTier.byPreference()
        val schemeOrder = schemeTier.byPreference()

        val rest = handlers
            .filter { it.packageName !in domainOrder && it.packageName !in schemeOrder }
            .sortedWith(
                compareByDescending<HandlerApp> { it.isBrowser }
                    .thenBy { it.label.lowercase() },
            )

        val domainPicks = domainOrder.mapNotNull { pkg -> handlers.find { it.packageName == pkg } }
        val schemePicks = schemeOrder.mapNotNull { pkg -> handlers.find { it.packageName == pkg } }

        return domainPicks + schemePicks + rest
    }
}
