package com.banglu.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.banglu.engine.JvmSqliteDictionaryLoader
import com.banglu.engine.JvmSqlitePhoneticIndexStore
import com.banglu.engine.SmartEngineAdapter
import com.banglu.engine.platform.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** S48: Banglu Desktop — the FULL engine (143MB dictionary + validator). */

private val Bg = Color(0xFF080D16)
private val Card = Color(0xFF0D1524)
private val CardBorder = Color(0xFF1E293B)
private val FieldBg = Color(0xFF101A2A)
private val Sky = Color(0xFF64D2FF)
private val SkySoft = Color(0xFFBAE6FD)
private val Green = Color(0xFF4ADE80)
private val Muted = Color(0xFF64748B)

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
            Item("Banglu খুলুন") { mainVisible = true }
            Item(if (System.getProperty("os.name").lowercase().contains("mac"))
                "মিনি কনভার্টার (⌘⇧B)" else "মিনি কনভার্টার (Ctrl+Shift+B)") { miniVisible = true }
            Separator()
            Item("বন্ধ করুন") { exitApplication() }
        }
    )

    if (mainVisible) Window(
        onCloseRequest = { mainVisible = false }, // tray keeps us alive
        title = "Banglu — বাংলা টাইপিং",
        state = rememberWindowState(width = 720.dp, height = 560.dp)
    ) {
        App()
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

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Card)) {
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

@Composable
private fun App() {
    var status by remember { mutableStateOf("সিড ইঞ্জিন — পূর্ণ অভিধান লোড হচ্ছে…") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var chips by remember { mutableStateOf(listOf<String>()) }
    var copied by remember { mutableStateOf(false) }
    val overrides = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            SmartEngineAdapter.initializeSync()
            val db = findDictionaryFile()
            SmartEngineAdapter.setPhoneticIndex(JvmSqlitePhoneticIndexStore(db))
            SmartEngineAdapter.initialize(FileStorage, JvmSqliteDictionaryLoader(db))
        }
        status = "পূর্ণ অভিধান ✓"
    }

    fun recompute(text: String) {
        input = text
        scope.launch(Dispatchers.Default) {
            val parts = text.split(Regex("(?<=\\s)|(?=\\s)"))
            var ti = 0
            val bn = parts.joinToString("") { p ->
                if (p.isBlank()) p else {
                    val key = "${ti++}:${p.trim().lowercase()}"
                    overrides[key] ?: SmartEngineAdapter.convertWord(p.trim()).bengali
                }
            }
            val lastWord = text.split(Regex("\\s+")).lastOrNull()?.trim().orEmpty()
            val sugg = if (lastWord.isNotEmpty())
                SmartEngineAdapter.getSuggestions(lastWord, 6).map { it.bengali } else emptyList()
            withContext(Dispatchers.Main) { output = bn; chips = sugg }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Card)) {
        Column(
            Modifier.fillMaxSize().background(Bg).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("বাংলু", color = Sky, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                Text(status, color = Muted, fontSize = 12.sp)
            }
            Text("শুধু ছোট হাতের ইংরেজিতে বাংলা টাইপ করুন", color = Green, fontSize = 15.sp)
            val isMac = remember { System.getProperty("os.name").lowercase().contains("mac") }
            // Three truthful states: registered can be a false positive on
            // macOS (tap registers without permission, then hears nothing),
            // so only a really-received key event turns the hint "active".
            Text(
                when {
                    Hotkey.registered && Hotkey.eventsSeen ->
                        if (isMac) "গ্লোবাল হটকি সক্রিয়: ⌘⇧B — যেকোনো অ্যাপে মিনি কনভার্টার"
                        else "গ্লোবাল হটকি সক্রিয়: Ctrl+Shift+B — যেকোনো অ্যাপে মিনি কনভার্টার"
                    Hotkey.registered && isMac ->
                        "কীবোর্ড শোনা যাচ্ছে না — System Settings → Privacy & Security-তে Accessibility এবং Input Monitoring দুই তালিকাতেই Banglu ON করুন (+ দিয়ে যোগ করুন), তারপর অ্যাপ রিস্টার্ট করুন"
                    Hotkey.registered ->
                        "কীবোর্ড শোনা যাচ্ছে না — অনুমতি দিয়ে অ্যাপ রিস্টার্ট করুন"
                    isMac ->
                        "হটকি (⌘⇧B) চালু করতে: Accessibility এবং Input Monitoring-এ Banglu-কে অনুমতি দিন; দিলে কয়েক সেকেন্ডে নিজে থেকেই চালু হবে" +
                            (Hotkey.lastError?.let { " · ($it)" } ?: "")
                    else -> "গ্লোবাল হটকি নিবন্ধন হয়নি — অনুমতি দিলে নিজে থেকেই চালু হবে" +
                        (Hotkey.lastError?.let { " · ($it)" } ?: "")
                },
                color = if (Hotkey.registered && Hotkey.eventsSeen) Muted else Color(0xFFFBBF24),
                fontSize = 11.sp
            )

            OutlinedTextField(
                value = input,
                onValueChange = { recompute(it) },
                modifier = Modifier.fillMaxWidth().height(130.dp),
                placeholder = { Text("ekhane english okkhore likhun…", color = Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = FieldBg, unfocusedContainerColor = FieldBg,
                    focusedBorderColor = Sky.copy(alpha = .5f), unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                )
            )

            if (chips.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips.size) { i ->
                    val c = chips[i]
                    AssistChip(
                        onClick = {
                            val tokens = input.split(Regex("\\s+")).filter { it.isNotBlank() }
                            if (tokens.isNotEmpty()) {
                                overrides["${tokens.size - 1}:${tokens.last().lowercase()}"] = c
                                recompute(if (input.endsWith(" ")) input else "$input ")
                            }
                        },
                        label = { Text(c, color = SkySoft) }
                    )
                }
            }

            Surface(
                Modifier.fillMaxWidth().weight(1f),
                color = Card, shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("বাংলা আউটপুট", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(StringSelection(output), null)
                            copied = true
                            scope.launch { kotlinx.coroutines.delay(1400); copied = false }
                        }) { Text(if (copied) "✓ কপি হয়েছে" else "কপি", color = Green) }
                    }
                    Text(output.ifEmpty { "…" }, color = SkySoft, fontSize = 22.sp, lineHeight = 34.sp)
                }
            }

            Text(
                "সম্পূর্ণ অফলাইন · Android কীবোর্ডের সেই একই ইঞ্জিন — পূর্ণ অভিধানসহ",
                color = Muted, fontSize = 11.sp
            )
        }
    }
}
