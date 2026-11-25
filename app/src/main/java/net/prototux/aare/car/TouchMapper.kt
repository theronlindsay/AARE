package net.prototux.aare.car

import aap_protobuf.service.inputsource.message.PointerActionOuterClass.PointerAction
import aap_protobuf.service.inputsource.message.TouchEventOuterClass.TouchEvent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import net.prototux.aare.car.calibration.TouchCalibration
import kotlin.math.abs

/**
 * Turns the head unit's touch reports into Android [MotionEvent]s in projection coordinates.
 *
 * The head unit reports touches in some source resolution covering the visible part of the video
 * (the frame minus its margins, see [ProjectionLayout]); this scales and offsets them into the full
 * frame. The source resolution is worked out from the declared touch panel and the visible area so
 * every real case maps correctly:
 *
 *  - a 0x0 or nonpositive declared panel (e.g. the Hyundai GEN5, #89): the visible area, per axis;
 *  - a panel strictly larger than the visible area on both axes AND sharing its aspect ratio: the
 *    head unit really reports visible-area pixels, not panel pixels (e.g. Ford SYNC 4, #74), so map
 *    1:1 + margin — scaling by the declared panel would land every tap short of the far edge;
 *  - otherwise the declared panel, scaled into the visible area — correct whether the panel is
 *    smaller than the display (upscale) or larger (downscale);
 *  - a panel that is exactly the frame size of one of the car's offered resolutions, but whose shape
 *    is far from the car's visible area (e.g. an 800x480 panel on a 1920x720 wide screen): a
 *    placeholder; the head unit really reports visible-area pixels of the stream, so map 1:1 +
 *    margin from the first touch, instead of only after the runtime guard below catches it;
 *  - plus a runtime guard: a coordinate past the declared panel proves the panel is too small, so
 *    that axis falls back to the visible area ([onFallback] says so, for the diagnostics log).
 *
 * The head unit's touches belong to its own screen, not to the stream AARE chose: with a lower
 * resolution picked (a setting or a vehicles.json cap), a panel the size of the car's screen is
 * larger than the stream's visible area, and must not be taken for visible-area pixels (#269:
 * every tap landed 1.5x off at 720p on a 1080p unit). So that check uses [nativeWidth] x
 * [nativeHeight], the visible area of the car's largest resolution; the same as the stream's when
 * nothing lowered it.
 *
 * Output is clamped to the visible area so a stray report never dispatches off the surface.
 *
 * A [calibration] the user measured for this head unit replaces all of the above.
 */
class TouchMapper(
    touchWidth: Int,
    touchHeight: Int,
    private val layout: ProjectionLayout,
    nativeWidth: Int = layout.visibleWidth,
    nativeHeight: Int = layout.visibleHeight,
    private val calibration: TouchCalibration? = null,
    /** The frame sizes (codec resolutions) of every configuration the car offered. */
    offeredFrames: List<Pair<Int, Int>> = emptyList(),
    /** Called once per axis when a touch past the declared panel makes it fall back to the visible area. */
    private val onFallback: ((String) -> Unit)? = null,
) {
    private val visW = layout.visibleWidth.coerceAtLeast(1)
    private val visH = layout.visibleHeight.coerceAtLeast(1)
    private val natW = nativeWidth.coerceAtLeast(visW)
    private val natH = nativeHeight.coerceAtLeast(visH)
    private val declX = touchWidth
    private val declY = touchHeight

    /** The declared panel is larger than, but the same shape as, the car's visible area: visible-area pixels. */
    private val visiblePixels = touchWidth > natW && touchHeight > natH &&
        aspectClose(touchWidth, touchHeight, natW, natH)

    /**
     * The declared panel is one of the offered frame sizes but not the shape of the car's visible
     * area: a placeholder, not the panel the coordinates are in. Taps come in the stream's visible pixels.
     */
    private val placeholderPanel = !visiblePixels && touchWidth > 0 && touchHeight > 0 &&
        (touchWidth to touchHeight) in offeredFrames && !aspectWithin(touchWidth, touchHeight, natW, natH, PLACEHOLDER_ASPECT_GAP)

    // The source extent each axis's coordinates are expressed in; may latch to the visible area.
    private var srcX = if (visiblePixels) natW else if (placeholderPanel) visW else if (touchWidth > 0) touchWidth else visW
    private var srcY = if (visiblePixels) natH else if (placeholderPanel) visH else if (touchHeight > 0) touchHeight else visH

    /** Why the source resolution is what it is, for the diagnostics log. */
    val describe: String get() = when {
        calibration != null -> "calibration"
        visiblePixels -> "visible-area pixels of the car (${srcX}x$srcY)"
        placeholderPanel -> "placeholder panel ${declX}x$declY, visible-area pixels of the stream (${srcX}x$srcY)"
        else -> "panel ${srcX}x$srcY"
    }

    private var downTime = 0L

    /** The source resolution each axis is mapped from: the declared panel, or the visible area. */
    val panelWidth get() = srcX
    val panelHeight get() = srcY

    /** A coordinate past the declared panel means the panel is too small: fall back to the visible area. */
    internal fun observe(x: Int, y: Int) {
        if (declX > 0 && srcX == declX && x > declX) { srcX = visW; onFallback?.invoke("x=$x past the ${declX}-px panel width, now ${visW}") }
        if (declY > 0 && srcY == declY && y > declY) { srcY = visH; onFallback?.invoke("y=$y past the ${declY}-px panel height, now ${visH}") }
    }

    internal fun mapX(x: Int): Float = clampX(x * (visW.toFloat() / srcX) + layout.marginLeft)

    internal fun mapY(y: Int): Float = clampY(y * (visH.toFloat() / srcY) + layout.marginTop)

    private fun clampX(x: Float) = x.coerceIn(layout.marginLeft.toFloat(), (layout.marginLeft + visW).toFloat())
    private fun clampY(y: Float) = y.coerceIn(layout.marginTop.toFloat(), (layout.marginTop + visH).toFloat())

    fun map(e: TouchEvent): MotionEvent? {
        val n = e.pointerDataCount
        if (n == 0) return null
        if (calibration == null) for (i in 0 until n) observe(e.getPointerData(i).x, e.getPointerData(i).y)
        val now = SystemClock.uptimeMillis()
        val index = e.actionIndex.coerceIn(0, n - 1)
        val action = when (e.action) {
            PointerAction.ACTION_DOWN -> { downTime = now; MotionEvent.ACTION_DOWN }
            PointerAction.ACTION_UP -> MotionEvent.ACTION_UP
            PointerAction.ACTION_MOVED -> MotionEvent.ACTION_MOVE
            PointerAction.ACTION_POINTER_DOWN -> MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            PointerAction.ACTION_POINTER_UP -> MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            else -> return null
        }
        if (downTime == 0L) downTime = now
        val props = Array(n) { i ->
            MotionEvent.PointerProperties().apply {
                id = e.getPointerData(i).pointerId
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(n) { i ->
            val p = e.getPointerData(i)
            MotionEvent.PointerCoords().apply {
                if (calibration != null) {
                    // Raw values are uint32 on the wire.
                    val rx = (p.x.toLong() and 0xffffffffL).toDouble()
                    val ry = (p.y.toLong() and 0xffffffffL).toDouble()
                    x = clampX((calibration.x(rx, ry) * layout.width).toFloat())
                    y = clampY((calibration.y(rx, ry) * layout.height).toFloat())
                } else {
                    x = mapX(p.x)
                    y = mapY(p.y)
                }
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(downTime, now, action, n, props, coords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private companion object {
        /** How far apart the shapes of a placeholder panel and the visible area are at least (25%). */
        const val PLACEHOLDER_ASPECT_GAP = 0.25f

        /** The two rectangles share an aspect ratio within ~1%. */
        fun aspectClose(aw: Int, ah: Int, bw: Int, bh: Int): Boolean = aspectWithin(aw, ah, bw, bh, 0.01f)

        /** The two rectangles' aspect ratios are within [tolerance] (relative) of each other. */
        fun aspectWithin(aw: Int, ah: Int, bw: Int, bh: Int, tolerance: Float): Boolean {
            val a = aw.toFloat() / ah
            val b = bw.toFloat() / bh
            return abs(a - b) <= tolerance * b
        }
    }
}
