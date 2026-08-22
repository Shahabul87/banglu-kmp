package com.banglu.winime.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.banglu.winime.Mode

/**
 * The window a first-time user actually sees.
 *
 * A tray-only app is undiscoverable on Windows — new tray icons hide behind the
 * taskbar overflow chevron, so the user cannot tell whether the keyboard is on,
 * cannot switch বাংলা/English, and has nothing to click.
 *
 * Design brief, in priority order:
 *  1. This window answers ONE question — "am I typing Bangla right now?" — so
 *     the live status is the hero. The app's name belongs in the title bar,
 *     where Windows already draws it; repeating it as a headline spent the top
 *     third of the window saying something the user just read.
 *  2. The mode control is ONE switch, not three buttons. Three separate slabs
 *     read as three unrelated commands; a segmented track reads as one setting
 *     with three positions, which is what it is.
 *  3. **বন্ধ** (stop converting) and **বন্ধ করুন** (quit the app) previously sat
 *     in the same window looking nearly identical while doing entirely
 *     different things. Quitting is now **প্রস্থান**, a quiet text action placed
 *     away from the mode track — two destructive-looking twins is a trap, not
 *     a styling preference.
 *
 * Closing the window hides it; the keyboard keeps working. Only প্রস্থান quits.
 *
 * [showRequests] counts "bring this window back" requests, including ones made
 * while it is already open — a tray click while the window sits behind Word
 * must raise it, not silently do nothing.
 */
@Composable
fun ControlWindow(
    visible: Boolean,
    showRequests: Int,
    mode: Mode,
    bootStatus: String,
    hookHealthy: Boolean,
    engineReady: Boolean,
    updateLine: String,
    updateActionable: Boolean,
    updateBusy: Boolean,
    onUpdate: () -> Unit,
    onMode: (Mode) -> Unit,
    onHide: () -> Unit,
    onQuit: () -> Unit,
) {
    if (!visible) return
    // Sized for the TALLEST state — the update row plus a two-line release
    // note. A window sized for the common case clipped the footer clean off
    // the moment an update appeared, taking প্রস্থান with it; the flexible
    // spacer above the footer absorbs the slack when no update is pending.
    val state: WindowState = rememberWindowState(width = 440.dp, height = 496.dp)
    Window(
        // The close box hides; it never quits. The keyboard is the product and
        // it must survive the user tidying their desktop.
        onCloseRequest = onHide,
        title = "বাংলু টাইপার",
        state = state,
        resizable = false,
    ) {
        // The update row is the only thing that changes the content's height,
        // so the window follows it. A window fixed at the tallest state leaves
        // dead air in the common one; fixed at the shortest, it clipped the
        // footer the moment an update appeared.
        LaunchedEffect(updateLine.isEmpty()) {
            state.size = androidx.compose.ui.unit.DpSize(
                440.dp, if (updateLine.isEmpty()) 496.dp else 566.dp,
            )
        }
        LaunchedEffect(showRequests) {
            // Minimised counts as hidden to the user, so un-minimise before
            // raising: toFront() on an iconified window does nothing visible.
            state.isMinimized = false
            window.toFront()
            window.requestFocus()
        }

        val live = engineReady && hookHealthy && mode != Mode.OFF
        val accent = when {
            !engineReady || !hookHealthy -> Amber
            mode == Mode.BANGLA -> Brand
            mode == Mode.ENGLISH -> Slate
            else -> Muted
        }
        // One warm bloom behind the status, tinted by the live accent: the
        // window's only atmosphere, and it doubles as an at-a-glance signal —
        // the whole panel changes temperature with the mode.
        val bloom by animateColorAsState(accent.copy(alpha = 0.13f), tween(320))

        Box(Modifier.fillMaxSize().background(Ink)) {
            Box(
                Modifier.fillMaxWidth().height(268.dp).background(
                    Brush.radialGradient(colors = listOf(bloom, Color.Transparent), radius = 540f)
                )
            )
            Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp)) {
                Header(engineReady)
                Spacer(Modifier.height(24.dp))
                Hero(
                    live = live,
                    accent = accent,
                    headline = heroHeadline(engineReady, hookHealthy, bootStatus, mode),
                    detail = heroDetail(engineReady, hookHealthy, mode),
                )
                Spacer(Modifier.height(22.dp))
                ModeTrack(mode = mode, accent = accent, onMode = onMode)
                Spacer(Modifier.height(20.dp))
                TryField()
                if (updateLine.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    UpdateRow(updateLine, updateActionable, updateBusy, onUpdate)
                }
                Spacer(Modifier.weight(1f))
                Footer(onHide = onHide, onQuit = onQuit)
            }
        }
    }
}

/** Brand mark + small wordmark; the title bar already carries the full name. */
@Composable
private fun Header(engineReady: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Brand),
            contentAlignment = Alignment.Center,
        ) {
            Text("অ", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = BengaliFont)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "বাংলু টাইপার",
            color = Cream.copy(alpha = 0.92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BengaliFont,
        )
        Spacer(Modifier.weight(1f))
        if (!engineReady) {
            Text("প্রস্তুত হচ্ছে", color = Muted, fontSize = 11.sp, fontFamily = BengaliFont)
        }
    }
}

/** The one thing this window exists to say, at a size that says it. */
@Composable
private fun Hero(live: Boolean, accent: Color, headline: String, detail: String) {
    val pulse by animateFloatAsState(if (live) 1f else 0.45f, tween(400))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(accent.copy(alpha = pulse)))
        Spacer(Modifier.width(10.dp))
        Text(headline, color = Cream, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = BengaliFont)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        detail,
        color = Muted,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontFamily = BengaliFont,
        modifier = Modifier.padding(start = 20.dp),
    )
}

/**
 * One track, three positions — a setting, not three commands. Bengali sits a
 * point larger than the Latin label: at equal point size Noto's Bengali
 * x-height reads visibly smaller beside "English", which is what made the old
 * row look accidentally unbalanced.
 */
@Composable
private fun ModeTrack(mode: Mode, accent: Color, onMode: (Mode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        Segment("বাংলা", mode == Mode.BANGLA, accent, 15.sp, Modifier.weight(1f)) { onMode(Mode.BANGLA) }
        SegmentDivider(mode, Mode.BANGLA, Mode.ENGLISH)
        Segment("English", mode == Mode.ENGLISH, accent, 14.sp, Modifier.weight(1f)) { onMode(Mode.ENGLISH) }
        SegmentDivider(mode, Mode.ENGLISH, Mode.OFF)
        Segment("বন্ধ", mode == Mode.OFF, accent, 15.sp, Modifier.weight(1f)) { onMode(Mode.OFF) }
    }
    Spacer(Modifier.height(9.dp))
    Text(
        "Ctrl + Space চেপে বাংলা আর English-এর মধ্যে বদলান",
        color = Muted,
        fontSize = 11.sp,
        fontFamily = BengaliFont,
    )
}

/** Hairline between two inactive segments only — a filled segment owns its edge. */
@Composable
private fun SegmentDivider(mode: Mode, left: Mode, right: Mode) {
    val show = mode != left && mode != right
    Box(Modifier.width(1.dp).height(42.dp).background(if (show) Hairline else Color.Transparent))
}

@Composable
private fun Segment(
    label: String,
    active: Boolean,
    accent: Color,
    size: TextUnit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            active -> accent
            hovered -> Raised
            else -> Color.Transparent
        },
        tween(160),
    )
    Box(
        modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(bg)
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Ink else Cream.copy(alpha = if (hovered) 1f else 0.72f),
            fontSize = size,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            fontFamily = BengaliFont,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Deliberately NOT wired to the engine: the hook converts keystrokes
 * system-wide, so whatever lands here is exactly what would land in Word. That
 * is the point — it exercises the real path rather than simulating it.
 */
@Composable
private fun TryField() {
    var text by remember { mutableStateOf("") }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) Brand.copy(alpha = 0.55f) else Hairline, tween(180))
    Box(
        Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(12.dp))
            .background(Surface).border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (text.isEmpty()) {
            Text(
                "এখানে লিখে দেখুন — ami banglay likhi",
                color = Muted.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontFamily = BengaliFont,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = Cream, fontSize = 15.sp, lineHeight = 22.sp, fontFamily = BengaliFont),
            cursorBrush = SolidColor(Brand),
            interactionSource = interaction,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Present only when there is something to say about an update. */
@Composable
private fun UpdateRow(line: String, actionable: Boolean, busy: Boolean, onUpdate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Raised)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (actionable) Mint else Muted))
        Spacer(Modifier.width(10.dp))
        Text(
            line,
            color = Cream.copy(alpha = 0.86f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontFamily = BengaliFont,
            modifier = Modifier.weight(1f),
        )
        if (actionable) {
            Spacer(Modifier.width(8.dp))
            TextAction(if (busy) "চলছে…" else "আপডেট করুন", Mint, enabled = !busy, onClick = onUpdate)
        }
    }
}

@Composable
private fun Footer(onHide: () -> Unit, onQuit: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    Spacer(Modifier.height(15.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GhostButton("লুকান", Modifier.weight(1f), onHide)
        Spacer(Modifier.width(12.dp))
        // Quitting stops Bangla typing everywhere. It gets a quiet text action,
        // not a slab that mirrors the mode switch sitting above it.
        TextAction("প্রস্থান", Danger, enabled = true, quiet = true, onClick = onQuit)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "লুকালে টাইপিং চালু থাকে — টাস্কবারের বাংলু আইকনে ক্লিক করলে ফিরে আসবে। " +
            "প্রস্থান করলে বাংলা লেখা বন্ধ হয়ে যাবে।",
        color = Muted.copy(alpha = 0.8f),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontFamily = BengaliFont,
    )
}

@Composable
private fun GhostButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) Raised else Surface, tween(160))
    val edge by animateColorAsState(if (hovered) Brand.copy(alpha = 0.45f) else Hairline, tween(160))
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(11.dp)).background(bg)
            .border(1.dp, edge, RoundedCornerShape(11.dp))
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Cream, fontSize = 14.sp, fontFamily = BengaliFont)
    }
}

@Composable
private fun TextAction(
    label: String,
    tint: Color,
    enabled: Boolean,
    quiet: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(
        when {
            !enabled -> Muted.copy(alpha = 0.6f)
            hovered -> tint
            quiet -> Muted
            else -> tint.copy(alpha = 0.78f)
        },
        tween(160),
    )
    Box(
        Modifier.clip(RoundedCornerShape(9.dp))
            .background(if (hovered && enabled) tint.copy(alpha = 0.10f) else Color.Transparent)
            .hoverable(interaction).pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(label, color = color, fontSize = 13.sp, fontFamily = BengaliFont)
    }
}

private fun heroHeadline(engineReady: Boolean, hookHealthy: Boolean, bootStatus: String, mode: Mode): String =
    when {
        !engineReady -> bootStatus
        !hookHealthy -> "কীবোর্ড হুক বসেনি"
        mode == Mode.BANGLA -> "বাংলা চালু আছে"
        mode == Mode.ENGLISH -> "English মোডে আছে"
        else -> "বন্ধ আছে"
    }

private fun heroDetail(engineReady: Boolean, hookHealthy: Boolean, mode: Mode): String =
    when {
        !engineReady -> "অভিধান তৈরি হচ্ছে, কয়েক সেকেন্ড লাগবে।"
        !hookHealthy -> "ট্রে মেনু থেকে “কীবোর্ড আবার চালু করুন” চাপুন।"
        mode == Mode.BANGLA -> "Word, Chrome, WhatsApp — যেকোনো জায়গায় ইংরেজি অক্ষরে লিখুন, বাংলা হয়ে যাবে।"
        mode == Mode.ENGLISH -> "এখন সব কিছু ইংরেজিতেই যাবে, কিছু বদলাবে না।"
        else -> "উপরে বাংলা চাপলেই আবার লেখা শুরু হবে।"
    }

/**
 * The Banglu palette, not a new one.
 *
 * Taken from the two surfaces that already shipped: `#080D16` ink and
 * `#FF7A59` coral are the Android app's own (MainActivity), and the desktop
 * editor's EditorTheme uses the same ink with `#0D1524`/`#1E293B`/`#101A2A`
 * for its card, border and field. Sky `#64D2FF` is the editor's cool accent,
 * used here for English mode so the two typing modes read as two temperatures
 * of one brand rather than two unrelated colours.
 */
private val Ink = Color(0xFF080D16)
private val Surface = Color(0xFF0D1524)
private val Raised = Color(0xFF101A2A)
private val Hairline = Color(0xFF1E293B)
private val Cream = Color(0xFFF3F7FF)
private val Muted = Color(0xFF7C8BA1)
private val Brand = Color(0xFFFF7A59)
private val Slate = Color(0xFF64D2FF)
private val Mint = Color(0xFF34C759)
private val Amber = Color(0xFFFFA48F)
private val Danger = Color(0xFFFF7A59)
