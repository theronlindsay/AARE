package net.prototux.aare.host

import net.prototux.aare.ui.car.CarAppKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MergedPairsTest {
    private val apps = listOf(
        CarAppKind.MEDIA to "com.example.player",
        CarAppKind.STANDALONE to "com.example.player",
        CarAppKind.MEDIA to "com.example.radio",
        CarAppKind.STANDALONE to "com.example.browser",
    )

    @Test
    fun parkedHidesTheMediaEntryOfAPair() {
        assertEquals(setOf("media:com.example.player"), CarRuntime.mergedPairIds(apps, driving = false))
    }

    @Test
    fun drivingHidesTheLaunchedEntryOfAPair() {
        assertEquals(setOf("standalone:com.example.player"), CarRuntime.mergedPairIds(apps, driving = true))
    }

    @Test
    fun appsWithOneEntryAreLeftAlone() {
        assertEquals(emptySet<String>(), CarRuntime.mergedPairIds(apps.drop(2), driving = false))
    }
}
