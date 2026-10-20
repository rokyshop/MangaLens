import fr.mangalens.overlay.BorderBlend

private fun channel(color: Int, shift: Int) = (color ushr shift) and 255
private fun checkApprox(actual: Int, expected: Int, tolerance: Int = 1) {
    check(kotlin.math.abs(actual - expected) <= tolerance) {
        "Got $actual, expected $expected +/- $tolerance"
    }
}

fun main() {
    val white = 0xffffffff.toInt()
    val flat = BorderBlend.interpolate(white, white, white, white, 3, 4, 5, 6)
    check(flat == white) { "Flat background must remain solid white" }

    val black = 0xff000000.toInt()
    val gray = BorderBlend.interpolate(black, white, black, white, 5, 5, 5, 5)
    checkApprox(channel(gray, 16), 127)
    checkApprox(channel(gray, 8), 127)
    checkApprox(channel(gray, 0), 127)

    val red = 0xffff0000.toInt()
    val nearLeft = BorderBlend.interpolate(red, black, black, black, 1, 20, 20, 20)
    val nearRight = BorderBlend.interpolate(red, black, black, black, 20, 1, 20, 20)
    check(channel(nearLeft, 16) > channel(nearRight, 16))
    println("PASS: flat colors, gradients and boundary weighting")
}
