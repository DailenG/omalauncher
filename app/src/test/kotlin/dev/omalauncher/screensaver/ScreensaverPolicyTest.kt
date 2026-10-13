package dev.omalauncher.screensaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreensaverPolicyTest {
    @Test fun idleOffNeverShows() {
        assertNull(ScreensaverPolicy.delayMillis(0.0, 60_000L, pluggedIn = false))
    }

    @Test fun pluggedInUsesTheRequestedIdleDelay() {
        assertEquals(60_000L, ScreensaverPolicy.delayMillis(60.0, 60_000L, pluggedIn = true))
    }

    @Test fun batteryWithLongerTimeoutUsesTheRequestedIdleDelay() {
        assertEquals(60_000L, ScreensaverPolicy.delayMillis(60.0, 120_000L, pluggedIn = false))
    }

    @Test fun batteryWithEqualTimeoutUsesTheLead() {
        assertEquals(50_000L, ScreensaverPolicy.delayMillis(60.0, 60_000L, pluggedIn = false))
    }

    @Test fun veryShortTimeoutUsesHalfTheTimeout() {
        assertEquals(7_500L, ScreensaverPolicy.delayMillis(60.0, 15_000L, pluggedIn = false))
    }

    @Test fun idleShorterThanBatteryTimeoutUsesTheRequestedIdleDelay() {
        assertEquals(10_000L, ScreensaverPolicy.delayMillis(10.0, 30_000L, pluggedIn = false))
    }

    @Test fun unknownTimeoutUsesTheRequestedIdleDelay() {
        assertEquals(60_000L, ScreensaverPolicy.delayMillis(60.0, Long.MAX_VALUE, pluggedIn = false))
    }
}
