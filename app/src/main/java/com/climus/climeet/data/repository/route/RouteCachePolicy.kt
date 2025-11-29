package com.climus.climeet.data.repository.route

import java.time.Clock
import javax.inject.Inject

class RouteCachePolicy @Inject constructor(
    private val clock: Clock,
) {
    fun freshness(freshUntilEpochMs: Long?): Freshness =
        when {
            freshUntilEpochMs == null -> Freshness.ABSENT
            clock.millis() < freshUntilEpochMs -> Freshness.FRESH
            else -> Freshness.STALE
        }

    fun maxAgeSeconds(cacheControl: String?): Long {
        val parsed = cacheControl
            ?.split(',')
            ?.asSequence()
            ?.map(String::trim)
            ?.firstNotNullOfOrNull { directive ->
                val name = directive.substringBefore('=', missingDelimiterValue = "")
                val rawValue = directive.substringAfter('=', missingDelimiterValue = "")
                if (!name.equals("max-age", ignoreCase = true) || rawValue.isEmpty()) {
                    return@firstNotNullOfOrNull null
                }
                rawValue.trim().trim('"').toLongOrNull()
            }
        return when {
            parsed == null || parsed < 0L -> DEFAULT_MAX_AGE_SECONDS
            else -> parsed.coerceAtMost(MAX_MAX_AGE_SECONDS)
        }
    }

    fun freshUntilEpochMs(
        cacheControl: String?,
        receivedAtEpochMs: Long = clock.millis(),
    ): Long = receivedAtEpochMs + maxAgeSeconds(cacheControl) * MILLIS_PER_SECOND

    fun selectEvictions(
        entries: List<CacheEntry>,
        protectedKeys: Set<RouteCatalogKey>,
        capacityBytes: Long = CAPACITY_BYTES,
    ): List<RouteCatalogKey> {
        var remainingBytes = entries.sumOf(CacheEntry::estimatedBytes)
        if (remainingBytes <= capacityBytes) return emptyList()
        val selected = mutableListOf<RouteCatalogKey>()
        entries.sortedBy(CacheEntry::lastAccessedAtEpochMs).forEach { entry ->
            if (remainingBytes <= capacityBytes) return@forEach
            if (entry.key !in protectedKeys) {
                selected += entry.key
                remainingBytes -= entry.estimatedBytes
            }
        }
        return selected
    }

    fun isWithinCapacity(
        currentBytes: Long,
        capacityBytes: Long = CAPACITY_BYTES,
    ): Boolean = currentBytes <= capacityBytes

    companion object {
        const val DEFAULT_MAX_AGE_SECONDS = 900L
        const val MAX_MAX_AGE_SECONDS = 86_400L
        const val CAPACITY_BYTES = 20L * 1024L * 1024L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
