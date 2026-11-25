package com.progressone.nativeapp.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherServiceTest {
    @Test fun currentCacheIsFresh() {
        val now = 1_700_000_000_000L
        assertTrue(WeatherService.cacheIsFresh(now, now))
    }

    @Test fun expiredCacheIsRejected() {
        val now = 1_700_000_000_000L
        assertFalse(
            WeatherService.cacheIsFresh(
                now - WeatherService.CACHE_MAX_AGE_MILLIS - 1,
                now,
            )
        )
    }

    @Test fun legacyCacheWithoutTimestampIsRejected() {
        val now = 1_700_000_000_000L
        assertFalse(WeatherService.cacheIsFresh(Long.MIN_VALUE, now))
    }

    @Test fun smallClockSkewIsAccepted() {
        val now = 1_700_000_000_000L
        assertTrue(
            WeatherService.cacheIsFresh(
                now + WeatherService.CLOCK_SKEW_MILLIS,
                now,
            )
        )
    }
}
