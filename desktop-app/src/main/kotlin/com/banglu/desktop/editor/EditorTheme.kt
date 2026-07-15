package com.banglu.desktop.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** Brand palette (same as Android/web) + the bundled Bangla face (spec §3). */
val Bg = Color(0xFF080D16)
val PageCard = Color(0xFF0D1524)
val CardBorder = Color(0xFF1E293B)
val FieldBg = Color(0xFF101A2A)
val Sky = Color(0xFF64D2FF)
val SkySoft = Color(0xFFBAE6FD)
val Green = Color(0xFF4ADE80)
val Muted = Color(0xFF64748B)

val BengaliFontFamily = FontFamily(
    Font(resource = "fonts/NotoSansBengali-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/NotoSansBengali-Bold.ttf", weight = FontWeight.Bold),
)

/** Same face for java.awt printing — Java2D shapes Bengali correctly. */
val AwtBengaliFont: java.awt.Font by lazy {
    object {}.javaClass.getResourceAsStream("/fonts/NotoSansBengali-Regular.ttf")!!.use {
        java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, it)
    }
}

fun toBengaliDigits(n: Int): String =
    n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")
