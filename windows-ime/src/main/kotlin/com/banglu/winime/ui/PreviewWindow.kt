package com.banglu.winime.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.banglu.winime.composer.Composer
import com.banglu.winime.hook.CaretLocator
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.roundToInt

/** Brand palette, same values as the desktop editor's EditorTheme. */
private val CardBg = Color(0xFF0D1524)
private val CardBorder = Color(0xFF1E293B)
private val ChipBg = Color(0xFF101A2A)
private val Sky = Color(0xFF64D2FF)
private val SkySoft = Color(0xFFBAE6FD)

/** The bundled face (OFL) — the host app's font never applies to our window. */
internal val BengaliFont = FontFamily(
    Font(resource = "fonts/NotoSansBengali-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/NotoSansBengali-Bold.ttf", weight = FontWeight.Bold),
)

// Wide enough for six chips: five is what the strip used to show, and the
// sixth (the raw-roman escape hatch) needs room rather than the clip that
// `clipToBounds` would otherwise give it.
private const val WIDTH_DP = 540

// One row of chips. The forming word used to live above them; it is now typed
// straight into the user's document, so the strip is suggestions and nothing
// else — and the shorter it is, the less of their text it covers.
private const val HEIGHT_DP = 52

/** Below the caret, not on it: the strip must never cover the text being typed. */
private const val CARET_GAP_PX = 24

/**
 * Every candidate a digit can pick. This is deliberately
 * [Composer.MAX_CANDIDATES] and not a local number: a strip that shows fewer
 * chips than the digits reach hides candidates (the last one is always the raw
 * roman escape hatch), and one that shows more offers a chip whose digit does
 * nothing.
 */
private const val MAX_CANDIDATES = Composer.MAX_CANDIDATES

/**
 * The suggestion strip, floating under the caret of whatever application has
 * focus.
 *
 * It no longer shows the word being typed: that is echoed straight into the
 * user's document as they type it, the way Avro does, so repeating it here
 * would only put the same text in two places and cover the real one. What is
 * left is the thing the document cannot show — the alternatives, pickable by
 * digit or by click.
 *
 * It is created once and hidden between words rather than composed
 * conditionally — a native window rebuilt per word costs a visible flash, and
 * on Windows a freshly shown window is exactly the thing most likely to be
 * activated by the window manager.
 *
 * [visible] is driven by "is there at least one candidate?" and nothing else,
 * so an empty box can never appear. Keeping it on screen for the whole word
 * rather than per keystroke is the composer's job: it no longer blanks its
 * candidate list on every letter, which is what used to hide this window
 * during the entire time the user was typing.
 *
 * **It must never take focus.** A preview that activates steals the next
 * keystroke from Word, and the user's sentence lands in a 460px strip they
 * cannot even see the caret in.
 */
@Composable
fun PreviewWindow(
    visible: Boolean,
    candidates: List<String>,
    predictions: Boolean = false,
    onPick: (Int) -> Unit,
) {
    val state = rememberWindowState(width = WIDTH_DP.dp, height = HEIGHT_DP.dp)
    Window(
        // Undecorated: there is no close affordance, and the tray owns the
        // application lifetime — closing this window must never quit Banglu.
        onCloseRequest = {},
        state = state,
        visible = visible,
        title = "বাংলু টাইপার",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        LaunchedEffect(Unit) {
            // The `focusable = false` parameter above maps to this property;
            // setting it directly is deliberate belt-and-braces, because focus
            // theft is the one failure mode that makes the whole product
            // unusable, and `isAutoRequestFocus` (which the parameter does NOT
            // cover) is what stops the window manager activating the strip the
            // moment it becomes visible.
            window.focusableWindowState = false
            window.isAutoRequestFocus = false
        }
        // Re-anchored on every show: between two words the caret has usually
        // moved, and the user may have switched application entirely.
        LaunchedEffect(visible) { if (visible) placeUnderCaret(window) }
        PreviewCard(candidates = candidates, predictions = predictions, onPick = onPick)
    }
}

@Composable
private fun PreviewCard(candidates: List<String>, predictions: Boolean, onPick: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp)
                .clipToBounds(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            candidates.take(MAX_CANDIDATES).forEachIndexed { index, candidate ->
                CandidateChip(
                    index = index,
                    text = candidate,
                    // Predictions are click-only: after a space, digits type
                    // digits, so a digit hint on these chips would lie.
                    showDigit = !predictions,
                    onClick = { onPick(index) },
                )
            }
        }
    }
}

@Composable
private fun CandidateChip(index: Int, text: String, showDigit: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ChipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same number that picks this candidate from the keyboard: while a
        // word is forming, Composer maps digits 1-6 onto the candidate list —
        // the same six entries this row renders. Prediction chips (S130) hide
        // it, because digits do not pick predictions.
        if (showDigit) Text(
            text = bengaliDigit(index + 1),
            color = Sky,
            fontSize = 11.sp,
            fontFamily = BengaliFont,
        )
        Text(
            text = text,
            color = SkySoft,
            fontSize = 15.sp,
            fontFamily = BengaliFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun bengaliDigit(n: Int): String =
    n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

/**
 * Anchors the strip under the caret, or under the mouse pointer when the
 * focused application hides its caret (Chrome and every Electron app do), and
 * keeps it inside the screen it lands on.
 *
 * Never allowed to throw: a positioning failure must cost a badly placed
 * preview, not a dead composition.
 */
private fun placeUnderCaret(window: java.awt.Window) {
    runCatching {
        val anchor = anchorPoint()
        val screen = screenBoundsFor(anchor)
        val x = anchor.x.coerceIn(screen.x, maxOf(screen.x, screen.x + screen.width - window.width))
        val y = (anchor.y + CARET_GAP_PX)
            .coerceIn(screen.y, maxOf(screen.y, screen.y + screen.height - window.height))
        window.setLocation(x, y)
    }
}

private fun anchorPoint(): Point {
    val caret = CaretLocator.caretScreenPos()
    if (caret != null) {
        // GetGUIThreadInfo answers in physical pixels; AWT places windows in
        // user-space units (physical ÷ display scale) on a DPI-aware JVM, so on
        // a 150% laptop an unconverted caret would put the strip a third of the
        // screen away. MouseInfo already reports user space, hence no
        // conversion on the fallback path.
        val scale = displayScale()
        return Point((caret.first / scale).roundToInt(), (caret.second / scale).roundToInt())
    }
    return MouseInfo.getPointerInfo()?.location ?: Point(0, 0)
}

/**
 * The default screen's scale factor. Approximate on a mixed-DPI multi-monitor
 * desktop — resolving the true device would need the point already converted,
 * which is the value we are computing.
 */
private fun displayScale(): Double {
    val scale = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
    return if (scale > 0.0) scale else 1.0
}

private fun screenBoundsFor(point: Point): Rectangle {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    for (device in environment.screenDevices) {
        val bounds = device.defaultConfiguration.bounds
        if (bounds.contains(point)) return bounds
    }
    return environment.defaultScreenDevice.defaultConfiguration.bounds
}
