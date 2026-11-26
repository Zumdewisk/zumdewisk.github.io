package com.example.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdManagerPolicyTest {

    @Test
    fun `interstitial enforces 15 second cooldown and every 3rd tab switch`() {
        val baseTime = 1_000_000L

        // Första och andra flikbytet ska aldrig visa annons
        assertFalse(AdRefreshPolicy.shouldShowInterstitial(1, baseTime, 0L))
        assertFalse(AdRefreshPolicy.shouldShowInterstitial(2, baseTime, 0L))

        // Tredje flikbytet när ingen annons visats nyligen -> ska visas
        assertTrue(AdRefreshPolicy.shouldShowInterstitial(3, baseTime, 0L))

        // Sjätte flikbytet 14,9 sekunder senare -> fortfarande i cooldown
        assertFalse(AdRefreshPolicy.shouldShowInterstitial(6, baseTime + 14_999L, baseTime))

        // Exakt 15 sekunder senare -> ska visas igen
        assertTrue(AdRefreshPolicy.shouldShowInterstitial(6, baseTime + 15_000L, baseTime))
    }

    @Test
    fun `interstitial freshness expires before AdMob 1-hour limit and after 24 hours`() {
        val loadedAt = 10_000_000L

        assertFalse(AdRefreshPolicy.isAdFresh(0L, loadedAt))
        assertTrue(AdRefreshPolicy.isAdFresh(loadedAt, loadedAt + 49L * 60_000L))
        assertFalse(AdRefreshPolicy.isAdFresh(loadedAt, loadedAt + 50L * 60_000L))
        assertFalse(AdRefreshPolicy.isAdFresh(loadedAt, loadedAt + 24L * 60L * 60_000L))
    }

    @Test
    fun `retry backoff scales smoothly and caps at 60 seconds`() {
        assertEquals(10_000L, AdRefreshPolicy.computeRetryDelayMs(0))
        assertEquals(20_000L, AdRefreshPolicy.computeRetryDelayMs(1))
        assertEquals(40_000L, AdRefreshPolicy.computeRetryDelayMs(2))
        assertEquals(60_000L, AdRefreshPolicy.computeRetryDelayMs(3))
        assertEquals(60_000L, AdRefreshPolicy.computeRetryDelayMs(10))
    }
}
