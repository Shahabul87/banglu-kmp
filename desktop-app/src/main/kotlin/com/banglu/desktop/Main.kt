package com.banglu.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.Tray
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.banglu.desktop.editor.Bg
import com.banglu.desktop.editor.PageCard
import com.banglu.desktop.editor.CardBorder
import com.banglu.desktop.editor.FieldBg
import com.banglu.desktop.editor.Sky
import com.banglu.desktop.editor.SkySoft
import com.banglu.desktop.editor.Muted
import com.banglu.desktop.editor.DraftStore
import com.banglu.desktop.editor.EditorScreen
import com.banglu.engine.SmartEngineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** S48: Banglu Desktop — the FULL engine (143MB dictionary + validator). */

fun main() = application {
    var mainVisible by remember { mutableStateOf(true) }
    var miniVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Hotkey.register {
            // hotkey fires on a native thread — hop to the UI state safely
            java.awt.EventQueue.invokeLater { miniVisible = !miniVisible }
        }
    }

    Tray(
        icon = painterResource("tray.png"),
        tooltip = "Banglu",
        menu = {
            Item("বাংলু লেখক খুলুন") { mainVisible = true }
            Item(if (System.getProperty("os.name").lowercase().contains("mac"))
                "মিনি কনভার্টার (⌘⇧B)" else "মিনি কনভার্টার (Ctrl+Shift+B)") { miniVisible = true }
            Separator()
            Item("বন্ধ করুন") { exitApplication() }
        }
    )

    val prefs = remember { DraftStore(java.io.File(System.getProperty("user.home"), ".banglu")).loadPrefs() }
    val winState = rememberWindowState(width = prefs.winW.dp, height = prefs.winH.dp)
    if (mainVisible) Window(
        onCloseRequest = { mainVisible = false },       // tray keeps us alive; draft autosaves
        title = "বাংলু লেখক",
        state = winState,
    ) {
        EditorScreen()
    }

    LaunchedEffect(winState.size) {
        DraftStore(java.io.File(System.getProperty("user.home"), ".banglu"))
            .let { it.savePrefs(it.loadPrefs().copy(
                winW = winState.size.width.value.toInt(),
                winH = winState.size.height.value.toInt())) }
    }

    if (miniVisible) Window(
        onCloseRequest = { miniVisible = false },
        title = "Banglu",
        alwaysOnTop = true,
        state = rememberWindowState(width = 560.dp, height = 190.dp)
    ) {
        MiniConverter(onDone = { text ->
            miniVisible = false
            if (text.isNotBlank()) Paste.copyThenPaste(text)
        }, onDismiss = { miniVisible = false })
    }
}

/**
 * The over-any-app converter: type Banglish, Enter pastes the Bangla into
 * the app you came from (Word, editor, browser). Esc dismisses.
 */
@Composable
private fun MiniConverter(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    fun recompute(text: String) {
        input = text
        scope.launch(Dispatchers.Default) {
            val bn = text.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { p ->
                if (p.isBlank()) p else SmartEngineAdapter.convertWord(p.trim()).bengali
            }
            withContext(Dispatchers.Main) { output = bn }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = PageCard)) {
        Column(Modifier.fillMaxSize().background(Bg).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { recompute(it) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(focus)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter -> { onDone(output); true }
                            Key.Escape -> { onDismiss(); true }
                            else -> false
                        }
                    },
                placeholder = { Text("banglish likhun — Enter = paste", color = Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = FieldBg, unfocusedContainerColor = FieldBg,
                    focusedBorderColor = Sky.copy(alpha = .5f), unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                )
            )
            Text(output.ifEmpty { "…" }, color = SkySoft, fontSize = 20.sp)
            Text("Enter = আগের অ্যাপে পেস্ট · Esc = বন্ধ", color = Muted, fontSize = 10.sp)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
