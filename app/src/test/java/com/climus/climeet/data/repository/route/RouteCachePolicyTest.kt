package com.climus.climeet.data.repository.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RouteCachePolicyTest {
    private val now = Instant.parse("2026-09-25T00:00:00Z")
    private val policy = RouteCachePolicy(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun beforeFreshUntil_evaluate_returnsFresh() {
        assertEquals(Freshness.FRESH, policy.freshness(now.toEpochMilli() + 1))
    }

    @Test
    fun atFreshUntil_evaluate_returnsStale() {
        assertEquals(Freshness.STALE, policy.freshness(now.toEpochMilli()))
    }

    @Test
    fun cacheControlMissing_parseMaxAge_usesDefault() {
        assertEquals(900L, policy.maxAgeSeconds(null))
    }

    @Test
    fun cacheControlOutOfRange_parseMaxAge_clampsOrDefaults() {
        assertEquals(900L, policy.maxAgeSeconds("max-age=-1"))
        assertEquals(86_400L, policy.maxAgeSeconds("public, max-age=999999"))
    }

    @Test
    fun protectedOldestCatalog_selectEvictions_skipsProtectedKey() {
        val protected = RouteCatalogKey(1, LocalDate.parse("2026-08-12"))
        val removable = RouteCatalogKey(2, LocalDate.parse("2026-08-12"))
        val result = policy.selectEvictions(
            entries = listOf(
                CacheEntry(protected, estimatedBytes = 15, lastAccessedAtEpochMs = 1),
                CacheEntry(removable, estimatedBytes = 10, lastAccessedAtEpochMs = 2),
            ),
            protectedKeys = setOf(protected),
            capacityBytes = 20,
        )

        assertEquals(listOf(removable), result)
    }

    @Test
    fun onlyProtectedCatalogExceedsCapacity_selectEvictions_allowsTemporaryOverflow() {
        val protected = RouteCatalogKey(1, LocalDate.parse("2026-08-12"))
        val result = policy.selectEvictions(
            entries = listOf(CacheEntry(protected, estimatedBytes = 25, lastAccessedAtEpochMs = 1)),
            protectedKeys = setOf(protected),
            capacityBytes = 20,
        )

        assertTrue(result.isEmpty())
        assertFalse(policy.isWithinCapacity(25, 20))
    }
}
