package net.prototux.aare.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchMapperTest {
    /** Builds a mapper the way CarSession does, from a video frame and its margins. */
    private fun mapper(touchW: Int, touchH: Int, videoW: Int, videoH: Int, marginX: Int, marginY: Int) =
        TouchMapper(touchW, touchH, ProjectionLayout.of(videoW, videoH, marginX, marginY, null, null))

    @Test
    fun zeroSizedHyundaiPanelUsesVisibleVideoCoordinates() {
        val m = mapper(0, 0, 1920, 1080, 0, 360)
        assertEquals(1920, m.panelWidth)
        assertEquals(720, m.panelHeight)
        // Actual first touch from the Kona log. Previously (1384320, 261540).
        assertEquals(721f, m.mapX(721), 0.001f)
        assertEquals(543f, m.mapY(363), 0.001f)
        assertEquals(180f, m.mapY(0), 0.001f)
        assertEquals(899f, m.mapY(719), 0.001f)
    }

    @Test
    fun validPanelStillScalesIntoVisibleVideoAndOffsetsMargins() {
        val m = mapper(1280, 480, 1920, 1080, 0, 360)
        assertEquals(960f, m.mapX(640), 0.001f)
        assertEquals(540f, m.mapY(240), 0.001f)
    }

    @Test
    fun dimensionsFallBackIndependentlyWithoutDiscardingValidAxis() {
        val missingWidth = mapper(-1, 480, 1920, 1080, 0, 360)
        assertEquals(721f, missingWidth.mapX(721), 0.001f)
        assertEquals(540f, missingWidth.mapY(240), 0.001f)
        val missingHeight = mapper(1280, 0, 1920, 1080, 0, 360)
        assertEquals(960f, missingHeight.mapX(640), 0.001f)
        assertEquals(543f, missingHeight.mapY(363), 0.001f)
    }

    @Test
    fun fallbackWithHorizontalAndVerticalMargins() {
        val m = mapper(0, 0, 1280, 720, 256, 120)
        assertEquals(128f, m.mapX(0), 0.001f)
        assertEquals(60f, m.mapY(0), 0.001f)
        assertEquals(640f, m.mapX(512), 0.001f)
        assertEquals(360f, m.mapY(300), 0.001f)
    }

    /**
     * Ford SYNC 4 (Mustang Mach-E, #74): a 1920x1080 frame with 878 px of width margin (visible
     * 1042x1080), the unit declares a 1200x1244 panel but reports touches in visible-area pixels.
     * The declared panel is strictly larger than the visible area and the same shape, so the mapper
     * recognises the visible-pixel case and maps 1:1 + margin — the bottom rail (y up to 1062) and
     * the far right edge become reachable, which scaling by 1200x1244 would put ~13% short.
     */
    @Test
    fun fordVisiblePixelPanelMapsOneToOneIntoVisibleArea() {
        val m = mapper(1200, 1244, 1920, 1080, 878, 0)
        assertEquals(1042, m.panelWidth)
        assertEquals(1080, m.panelHeight)
        assertEquals(439f, m.mapX(0), 0.001f)        // left edge sits past the left margin
        assertEquals(1462f, m.mapX(1023), 0.001f)    // far-right tap: 1023 + 439
        assertEquals(0f, m.mapY(0), 0.001f)
        assertEquals(1062f, m.mapY(1062), 0.001f)    // bottom rail is reachable
    }

    /** A panel genuinely larger than the visible area, but a different shape, is scaled down (not visible-pixels). */
    @Test
    fun honestLargerPanelOfDifferentShapeIsDownscaled() {
        val m = mapper(2000, 2000, 1920, 1080, 878, 0)
        assertEquals(2000, m.panelWidth)             // treated as a real panel, not visible pixels
        assertEquals(960f, m.mapX(1000), 0.001f)     // 1000 * 1042/2000 + 439
        assertEquals(540f, m.mapY(1000), 0.001f)     // 1000 * 1080/2000
    }

    /** A coordinate beyond the declared panel proves it is too small: that axis falls back to visible. */
    @Test
    fun underDeclaredPanelLatchesToVisibleArea() {
        val m = mapper(100, 100, 1000, 1000, 0, 0)
        assertEquals(100, m.panelWidth)
        m.observe(500, 50)                           // x past the 100-px panel, y within it
        assertEquals(1000, m.panelWidth)             // x axis latched to the visible area
        assertEquals(100, m.panelHeight)             // y axis untouched
        assertEquals(500f, m.mapX(500), 0.001f)
    }

    /** Mapped output is clamped to the visible area, so a stray report never dispatches off-surface. */
    @Test
    fun outputIsClampedToTheVisibleArea() {
        val m = mapper(1000, 1000, 1000, 1000, 0, 0)
        assertEquals(1000f, m.mapX(1200), 0.001f)    // beyond the right edge clamps to it
        assertTrue(m.mapX(-20) >= 0f)                // never negative
    }

    /**
     * #269: the car offers 1080p, its panel is 1920x1080, and a lower resolution was picked (720p).
     * The panel is larger than the stream's visible area, but it's the car's screen: scale it,
     * don't take it for visible-area pixels (which put every tap 1.5x off).
     */
    @Test
    fun lowerResolutionStillScalesTheCarsOwnPanel() {
        val m = TouchMapper(1920, 1080, ProjectionLayout.of(1280, 720, 0, 0, null, null), nativeWidth = 1920, nativeHeight = 1080)
        assertEquals(1920, m.panelWidth)
        assertEquals(640f, m.mapX(960), 0.001f)
        assertEquals(360f, m.mapY(540), 0.001f)
        assertEquals(1280f, m.mapX(1919), 1f)
    }

    /** A Mach-E-like unit (touches in its visible pixels) with a lower stream: map from its own visible area. */
    @Test
    fun visiblePixelUnitWithALowerStreamMapsFromItsVisibleArea() {
        val m = TouchMapper(1200, 1244, ProjectionLayout.of(781, 810, 0, 0, null, null), nativeWidth = 1042, nativeHeight = 1080)
        assertEquals(1042, m.panelWidth)
        assertEquals(1080, m.panelHeight)
        assertEquals(390.5f, m.mapX(521), 0.5f)
        assertEquals(810f, m.mapY(1080), 0.5f)
    }

    /**
     * A wide unit offering 1920x1080, 1280x720 and 800x480 with large height margins (visible
     * 1920x720 / 1280x480) declares an 800x480 panel: one of its frame sizes, nowhere near its
     * screen's shape. It reports taps in the stream's visible pixels, so they map 1:1 from the
     * first touch, not only after one lands past x=800 and another past y=480.
     */
    @Test
    fun placeholderPanelOfAnOfferedFrameSizeMapsVisiblePixelsFromTheStart() {
        val frames = listOf(1920 to 1080, 1280 to 720, 800 to 480)
        val m = TouchMapper(800, 480, ProjectionLayout.of(1920, 1080, 0, 360, null, null),
            nativeWidth = 1920, nativeHeight = 720, offeredFrames = frames)
        assertEquals(1920, m.panelWidth)
        assertEquals(720, m.panelHeight)
        assertEquals(400f, m.mapX(400), 0.001f)      // previously 400 * 1920/800 = 960
        assertEquals(380f, m.mapY(200), 0.001f)      // 200 + 180 margin; previously 200 * 720/480 + 180
    }

    /** The same unit streaming 1280x720 (visible 1280x480): taps come in that stream's pixels. */
    @Test
    fun placeholderPanelWithALowerStreamMapsFromTheStreamsVisibleArea() {
        val frames = listOf(1920 to 1080, 1280 to 720, 800 to 480)
        val m = TouchMapper(800, 480, ProjectionLayout.of(1280, 720, 0, 240, null, null),
            nativeWidth = 1920, nativeHeight = 720, offeredFrames = frames)
        assertEquals(1280, m.panelWidth)
        assertEquals(480, m.panelHeight)
        assertEquals(1000f, m.mapX(1000), 0.001f)
        assertEquals(120f, m.mapY(0), 0.001f)
    }

    /** A panel of the screen's own shape is a real panel, even when it is an offered frame size. */
    @Test
    fun offeredFrameSizeOfTheScreensShapeIsStillScaled() {
        val m = TouchMapper(800, 480, ProjectionLayout.of(1280, 720, 0, 0, null, null),
            nativeWidth = 1280, nativeHeight = 720, offeredFrames = listOf(1280 to 720, 800 to 480))
        assertEquals(800, m.panelWidth)
        assertEquals(640f, m.mapX(400), 0.001f)
    }

    /** A differently shaped panel that isn't an offered frame size keeps the old behaviour (and the guard). */
    @Test
    fun otherwiseShapedPanelNotAnOfferedFrameKeepsTheGuard() {
        val fallbacks = mutableListOf<String>()
        val m = TouchMapper(800, 480, ProjectionLayout.of(1920, 1080, 0, 360, null, null),
            nativeWidth = 1920, nativeHeight = 720, offeredFrames = listOf(1920 to 1080), onFallback = { fallbacks += it })
        assertEquals(800, m.panelWidth)
        m.observe(1000, 100)
        assertEquals(1920, m.panelWidth)
        assertEquals(1, fallbacks.size)
    }
}
