package com.banglu.winime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.banglu.winime.Mode

/**
 * The window a first-time user actually sees.
 *
 * A tray-only app is undiscoverable on Windows — new tray icons are hidden
 * behind the taskbar overflow chevron, so the user cannot tell whether the
 * keyboard is on, cannot switch বাংলা/English, and has nothing to click.
 * This window is the on/off switch, the status light, and a place to try
 * typing without alt-tabbing to another app.
 *
 * Closing it hides it (the app keeps running in the tray) rather than
 * quitting, which is what a keyboard should do.
 */
@Composable
fun ControlWindow(
    visible: Boolean,
    mode: Mode,
    bootStatus: String,
    hookHealthy: Boolean,
    engineReady: Boolean,
    onMode: (Mode) -> Unit,
    onHide: () -> Unit,
    onQuit: () -> Unit,
) {
    if (!visible) return
    val state: WindowState = rememberWindowState(width = 460.dp, height = 470.dp)
    Window(
        onCloseRequest = onHide,
        title = "বাংলু টাইপার",
        state = state,
        resizable = false,
    ) {
        Column(
            Modifier.fillMaxSize().background(Ink).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "বাংলু টাইপার",
                color = Cream,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BengaliFont,
            )
            Text(
                statusLine(engineReady, hookHealthy, bootStatus, mode),
                color = if (engineReady && hookHealthy) Mint else Amber,
                fontSize = 13.sp,
                fontFamily = BengaliFont,
            )

            Spacer(Modifier.height(2.dp))
            Text("এখন কী চলছে", color = Dim, fontSize = 12.sp, fontFamily = BengaliFont)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModeButton("বাংলা", mode == Mode.BANGLA, Brand, BengaliFont, Modifier.weight(1f)) {
                    onMode(Mode.BANGLA)
                }
                ModeButton("English", mode == Mode.ENGLISH, Slate, BengaliFont, Modifier.weight(1f)) {
                    onMode(Mode.ENGLISH)
                }
                ModeButton("বন্ধ", mode == Mode.OFF, Off, BengaliFont, Modifier.weight(1f)) {
                    onMode(Mode.OFF)
                }
            }
            Text(
                "যেকোনো সময় Ctrl+Space চেপে বাংলা ↔ English বদলান।",
                color = Dim,
                fontSize = 12.sp,
                fontFamily = BengaliFont,
            )

            Spacer(Modifier.height(2.dp))
            Text("এখানে লিখে দেখুন", color = Dim, fontSize = 12.sp, fontFamily = BengaliFont)
            TryBox(BengaliFont)

            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModeButton("লুকান", false, Slate, BengaliFont, Modifier.weight(1f)) { onHide() }
                ModeButton("বন্ধ করুন", false, Off, BengaliFont, Modifier.weight(1f)) { onQuit() }
            }
            Text(
                "লুকালে অ্যাপ চালু থাকে — টাস্কবারের ^ চিহ্নের নিচে আইকনটি পাবেন।",
                color = Dim,
                fontSize = 11.sp,
                fontFamily = BengaliFont,
            )
        }
    }
}

/**
 * A plain typing surface. It is deliberately NOT wired to the engine: the
 * hook converts keystrokes system-wide, so whatever appears here is exactly
 * what would appear in Word. That is the point — it proves the real path
 * works rather than simulating it.
 */
@Composable
private fun TryBox(family: FontFamily) {
    var text by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(10.dp)).background(Field)
            .padding(12.dp),
    ) {
        if (text.isEmpty()) {
            Text("ami banglay likhi", color = Dim, fontSize = 15.sp, fontFamily = family)
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = Cream, fontSize = 15.sp, fontFamily = family),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Brand),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    active: Boolean,
    accent: Color,
    family: FontFamily,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent else Field)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Ink else Cream,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            fontFamily = family,
            textAlign = TextAlign.Center,
        )
    }
}

private fun statusLine(
    engineReady: Boolean,
    hookHealthy: Boolean,
    bootStatus: String,
    mode: Mode,
): String = when {
    !engineReady -> bootStatus
    !hookHealthy -> "⚠ কীবোর্ড হুক বসেনি — ট্রে থেকে আবার চালু করুন"
    mode == Mode.BANGLA -> "✓ প্রস্তুত — যেকোনো অ্যাপে বাংলা লিখুন"
    mode == Mode.ENGLISH -> "✓ চালু আছে — এখন English মোডে"
    else -> "বন্ধ আছে — উপরে বাংলা চাপলে আবার চালু হবে"
}

private val Ink = Color(0xFF1A1614)
private val Field = Color(0xFF2A2422)
private val Cream = Color(0xFFF3EAE2)
private val Dim = Color(0xFF9C8F86)
private val Brand = Color(0xFFE08A4C)
private val Slate = Color(0xFF7E9AA8)
private val Mint = Color(0xFF7FC8A0)
private val Amber = Color(0xFFE0B44C)
private val Off = Color(0xFFB86A6A)
