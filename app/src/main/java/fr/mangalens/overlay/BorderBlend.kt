package fr.mangalens.overlay

import kotlin.math.max

/** Pure Kotlin, platform-independent pixel interpolation for simple backgrounds. */
object BorderBlend {
    /** Color args are packed RGB/ARGB integers. Returns opaque ARGB. */
    fun interpolate(
        left: Int, right: Int, top: Int, bottom: Int,
        distanceLeft: Int, distanceRight: Int, distanceTop: Int, distanceBottom: Int
    ): Int {
        fun weight(distance: Int): Double {
            val d = max(1, distance).toDouble()
            return 1.0 / (d * d)
        }
        val wl = weight(distanceLeft)
        val wr = weight(distanceRight)
        val wt = weight(distanceTop)
        val wb = weight(distanceBottom)
        val total = wl + wr + wt + wb
        fun channel(shift: Int): Int = (
            (((left ushr shift) and 255) * wl +
             ((right ushr shift) and 255) * wr +
             ((top ushr shift) and 255) * wt +
             ((bottom ushr shift) and 255) * wb) / total
        ).toInt().coerceIn(0, 255)
        return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
