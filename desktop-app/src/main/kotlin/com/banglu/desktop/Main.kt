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

private object DesktopStorage : PlatformStorage {
    // v1: no persistence — preferences/learning land in a later round.
    override suspend fun getLearnedWords() = emptyList<com.banglu.engine.types.LearnedWord>()
    override suspend fun saveLearnedWord(phonetic: String, bengali: String, frequency: Int) {}
    override suspend fun clearLearnedWords() {}
    override suspend fun getDictionaryVersion(): String? = null
    override suspend fun cacheDictionary(
        words: List<String>, frequencies: Map<String, Int>?,
        disambigMap: Map<String, String>?, version: String
    ) {}
    override suspend fun getCachedDictionary(currentVersion: String) = null
}

private val Bg = Color(0xFF080D16)
private val Card = Color(0xFF0D1524)
private val CardBorder = Color(0xFF1E293B)
private val FieldBg = Color(0xFF101A2A)
private val Sky = Color(0xFF64D2FF)
private val SkySoft = Color(0xFFBAE6FD)
private val Green = Color(0xFF4ADE80)
private val Muted = Color(0xFF64748B)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Banglu — বাংলা টাইপিং",
        state = rememberWindowState(width = 720.dp, height = 560.dp)
    ) {
        App()
    }
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
            val db = JvmSqliteDictionaryLoader.findDictionarySqlite()
            SmartEngineAdapter.setPhoneticIndex(JvmSqlitePhoneticIndexStore(db))
            SmartEngineAdapter.initialize(DesktopStorage, JvmSqliteDictionaryLoader(db))
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
