package com.example.capsule

/** Small, coherent design palettes for the pill. Platform names describe inspiration, not copied assets. */
data class PillStylePalette(
    val id: String,
    val label: String,
    val top: Int,
    val bottom: Int,
    val outline: Int,
    val accent: Int,
    val primary: Int,
    val secondary: Int,
    val control: Int,
    val cornerDp: Int,
    val artworkCornerDp: Int
)

object PillStyles {
    val ids = listOf("oneui", "iphone", "pixel", "nothing", "capsule")

    fun normalize(raw: String?): String = when (raw) {
        "oneui", "iphone", "pixel", "nothing", "capsule" -> raw ?: "capsule"
        "ios" -> "iphone"
        "frosted" -> "oneui"
        "futuristic" -> "nothing"
        "accent" -> "pixel"
        else -> "capsule"
    }

    fun palette(raw: String?): PillStylePalette = when (normalize(raw)) {
        "oneui" -> PillStylePalette(
            "oneui", "One UI inspired", 0xFF292D35.toInt(), 0xFF17191E.toInt(),
            0xFF454B57.toInt(), 0xFFB4C5FF.toInt(), 0xFFF5F5F7.toInt(),
            0xFFB9BEC8.toInt(), 0xFF383D48.toInt(), 30, 18
        )
        "iphone" -> PillStylePalette(
            "iphone", "iPhone inspired", 0xFF35363D.toInt(), 0xFF1B1C21.toInt(),
            0x88FFFFFF.toInt(), 0xFF73A7FF.toInt(), 0xFFFFFFFF.toInt(),
            0xFFC8C8CE.toInt(), 0xFF3A3B42.toInt(), 36, 16
        )
        "pixel" -> PillStylePalette(
            "pixel", "Pixel inspired", 0xFF202127.toInt(), 0xFF111216.toInt(),
            0x444F5360, 0xFFD0BCFF.toInt(), 0xFFF4F0FA.toInt(),
            0xFFC8C2D2.toInt(), 0xFF302D38.toInt(), 26, 14
        )
        "nothing" -> PillStylePalette(
            "nothing", "Nothing inspired", 0xFF171717.toInt(), 0xFF090909.toInt(),
            0xFF515151.toInt(), 0xFFFF4545.toInt(), 0xFFF7F7F7.toInt(),
            0xFFB7B7B7.toInt(), 0xFF252525.toInt(), 20, 8
        )
        else -> PillStylePalette(
            "capsule", "Capsule original", 0xFF050608.toInt(), 0xFF000000.toInt(),
            0x33FFFFFF, 0xFFD0BCFF.toInt(), 0xFFFFFFFF.toInt(),
            0xFFBDBFC5.toInt(), 0xFF25272D.toInt(), 28, 14
        )
    }
}
