package com.banglu.keyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private var Primary = Color(0xFF0A84FF)
private var Success = Color(0xFF34C759)
private var Secondary = Color(0xFF8E8E93)
private var Coral = Color(0xFFFF3B30)
private var WarmBlack = Color(0xFFF6F7FB)
private var WarmDark = Color(0xFFEFF3F8)
private var WarmCard = Color(0xFFFFFFFF)
private var WarmCardBorder = Color(0xFFE4E8F0)
private var TextMuted = Color(0xFF6B7280)
private var TextLight = Color(0xFF111827)
private var PrimaryTrack = Color(0xFFD9EBFF)

private fun applySettingsPalette(dark: Boolean) {
    if (dark) {
        Primary = Color(0xFF64D2FF)
        Success = Color(0xFF30D158)
        Secondary = Color(0xFF98989D)
        Coral = Color(0xFFFF453A)
        WarmBlack = Color(0xFF080D16)
        WarmDark = Color(0xFF111827)
        WarmCard = Color(0xFF182235)
        WarmCardBorder = Color(0xFF27364F)
        TextMuted = Color(0xFFA8B3C7)
        TextLight = Color(0xFFF8FAFC)
        PrimaryTrack = Color(0xFF123B5E)
    } else {
        Primary = Color(0xFF0A84FF)
        Success = Color(0xFF34C759)
        Secondary = Color(0xFF8E8E93)
        Coral = Color(0xFFFF3B30)
        WarmBlack = Color(0xFFF6F7FB)
        WarmDark = Color(0xFFEFF3F8)
        WarmCard = Color(0xFFFFFFFF)
        WarmCardBorder = Color(0xFFE4E8F0)
        TextMuted = Color(0xFF6B7280)
        TextLight = Color(0xFF111827)
        PrimaryTrack = Color(0xFFD9EBFF)
    }
}

private fun buildDiagnosticsSummary(prefs: SharedPreferences): String {
    val all = prefs.all
    val countKeys = all.keys
        .filter { it.startsWith("diag_") && it.endsWith("_count") }
        .sorted()
    if (countKeys.isEmpty()) {
        return "Banglu diagnostics\nVersion: ${BuildConfig.VERSION_NAME}\nNo diagnostics recorded.\nNo typed text is stored here."
    }

    val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    val lines = mutableListOf(
        "Banglu diagnostics",
        "Version: ${BuildConfig.VERSION_NAME}",
        "No typed text is stored here."
    )
    countKeys.forEach { key ->
        val scope = when {
            key.startsWith("diag_ime_") -> "ime"
            key.startsWith("diag_failure_") -> "failure"
            key.startsWith("diag_latency_") -> "latency"
            else -> "event"
        }
        val event = key
            .removePrefix("diag_ime_")
            .removePrefix("diag_failure_")
            .removePrefix("diag_latency_")
            .removeSuffix("_count")
        val lastKey = when (scope) {
            "ime" -> "diag_ime_last_${event}_at"
            "failure" -> "diag_failure_last_${event}_at"
            "latency" -> "diag_latency_last_${event}_ms"
            else -> ""
        }
        val count = prefs.getInt(key, 0)
        val last = if (lastKey.isNotBlank()) prefs.getLong(lastKey, 0L) else 0L
        val lastText = if (scope == "latency") {
            "${last}ms"
        } else if (last > 0L) {
            formatter.format(Date(last))
        } else {
            "never"
        }
        val type = if (scope == "failure") prefs.getString("diag_failure_last_${event}_type", "").orEmpty() else ""
        lines += if (scope == "latency") {
            val total = prefs.getLong("diag_latency_${event}_total_ms", 0L)
            val avg = if (count > 0) total / count else 0L
            val max = prefs.getLong("diag_latency_${event}_max_ms", 0L)
            "$scope/$event: $count, avg ${avg}ms, max ${max}ms, last $lastText"
        } else if (type.isBlank()) {
            "$scope/$event: $count, last $lastText"
        } else {
            "$scope/$event: $count, last $lastText, type $type"
        }
    }
    return lines.joinToString("\n")
}

private fun clearDiagnostics(prefs: SharedPreferences) {
    val editor = prefs.edit()
    prefs.all.keys
        .filter { it.startsWith("diag_") }
        .forEach { editor.remove(it) }
    editor.apply()
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.rgb(248, 250, 255)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(246, 247, 251)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }
        setBangluContent { BangluSettingsScreen(onBack = { finish() }) }
    }
}

@Composable
fun BangluSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { remoteBangluPrefs(context) }
    val storage = remember { AndroidStorage(context) }
    val coroutineScope = rememberCoroutineScope()

    var autoCapitalize by remember { mutableStateOf(prefs.getBoolean("auto_capitalize", true)) }
    var doubleSpacePeriod by remember { mutableStateOf(prefs.getBoolean("double_space_period", true)) }
    var suggestions by remember { mutableStateOf(prefs.getBoolean("suggestions", true)) }
    var typingLearning by remember { mutableStateOf(prefs.getBoolean("typing_learning", true)) }
    var personalDictionary by remember { mutableStateOf(prefs.getBoolean("personal_dictionary", true)) }
    // Full dictionary by default: predictions, context reranking, and the strong
    // commit gate all need the full tables. Weak devices still auto-fall to lite
    // via the IME's device check (isLowRamDevice / memoryClass < 256).
    var liteMode by remember { mutableStateOf(prefs.getBoolean("lite_mode", false)) }
    var voiceTypingEnabled by remember { mutableStateOf(prefs.getBoolean("voice_typing_enabled", true)) }
    var voiceOfflinePreferred by remember { mutableStateOf(prefs.getBoolean("voice_offline_preferred", false)) }
    var keyFeedbackMode by remember {
        mutableStateOf(
            prefs.getString("key_feedback_mode", null)
                ?: when {
                    prefs.getBoolean("sound_feedback", true) && prefs.getBoolean("haptic_feedback", true) -> "both"
                    prefs.getBoolean("sound_feedback", true) -> "sound"
                    prefs.getBoolean("haptic_feedback", true) -> "vibration"
                    else -> "silent"
                }
        )
    }
    var keyPreview by remember { mutableStateOf(prefs.getBoolean("key_preview", true)) }
    var numberRow by remember { mutableStateOf(prefs.getBoolean("number_row", true)) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme", "dark") ?: "dark") }
    var defaultMode by remember { mutableStateOf(prefs.getString("default_mode", "banglu") ?: "banglu") }
    var keyboardHeight by remember { mutableStateOf(prefs.getString("keyboard_height", "normal") ?: "normal") }
    var keyboardFontSize by remember { mutableStateOf(prefs.getString("keyboard_font_size", "large") ?: "large") }
    var showClearLearnedDialog by remember { mutableStateOf(false) }
    var diagnosticsSummary by remember { mutableStateOf(buildDiagnosticsSummary(prefs)) }

    fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = themeMode == "dark" || themeMode == "amoled" || (themeMode == "auto" && systemDark)
    applySettingsPalette(darkTheme)
    SideEffect {
        val activity = context as? Activity ?: return@SideEffect
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = if (darkTheme) android.graphics.Color.rgb(8, 13, 22) else android.graphics.Color.rgb(248, 250, 255)
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = if (darkTheme) android.graphics.Color.rgb(8, 13, 22) else android.graphics.Color.rgb(246, 247, 251)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = if (darkTheme) {
            0
        } else {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(if (darkTheme) 0 else mask, mask)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (darkTheme) listOf(Color(0xFF080D16), WarmBlack, WarmDark)
                    else listOf(Color(0xFFF8FAFF), WarmBlack, WarmDark)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // ── Header ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(WarmCard)
                            .border(1.dp, WarmCardBorder, CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("←", color = Primary, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Image(
                        painter = painterResource(id = R.drawable.banglu_logo),
                        contentDescription = "Banglu logo",
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("সেটিংস", color = Primary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("Banglu Keyboard", color = TextMuted, fontSize = 15.sp)
                    }
                }

                // Dashed separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(1.dp)
                        .drawBehind {
                            drawLine(
                                Primary.copy(alpha = 0.10f),
                                Offset(0f, 0f), Offset(size.width, 0f),
                                strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                            )
                        }
                )
            }

            // ── Typing Section ──
            item { BrandSectionHeader("টাইপিং", "Typing") }
            item {
                BrandSwitch("অটো-ক্যাপিটালাইজ", "বাক্যের পর প্রথম অক্ষর বড় হাতের", autoCapitalize) {
                    autoCapitalize = it; saveBoolean("auto_capitalize", it)
                }
            }
            item {
                BrandSwitch("ডাবল-স্পেস পিরিয়ড", "দুবার স্পেস চাপলে দাড়ি/পিরিয়ড", doubleSpacePeriod) {
                    doubleSpacePeriod = it; saveBoolean("double_space_period", it)
                }
            }
            item {
                BrandSwitch("সাজেশন", "টাইপ করার সময় শব্দ সাজেশন দেখান", suggestions) {
                    suggestions = it; saveBoolean("suggestions", it)
                }
            }
            item {
                BrandSwitch("ব্যবহার শেখা", "আপনি যে বানান বেছে নেন তা পরের বার আগে দেখাবে", typingLearning) {
                    typingLearning = it; saveBoolean("typing_learning", it)
                }
            }
            item {
                BrandSwitch("ব্যক্তিগত অভিধান", "নাম, জায়গা ও নিজের শব্দ রাখুন; সাইন ইন করলে sync হতে পারে", personalDictionary) {
                    personalDictionary = it; saveBoolean("personal_dictionary", it)
                }
            }
            item {
                BrandActionRow("শেখা শব্দ মুছুন", "আপনার বাছাই করা বানান ও কাস্টম কনভার্সন মুছে দিন") {
                    showClearLearnedDialog = true
                }
            }
            item {
                BrandSwitch("হালকা অভিধান", "কম মেমরি ব্যবহার করে দ্রুত কীবোর্ড চালু রাখুন", liteMode) {
                    liteMode = it; saveBoolean("lite_mode", it)
                }
            }
            item {
                BrandListSetting(
                    "ডিফল্ট মোড",
                    if (defaultMode == "banglu") "বাংলু (বাংলা)" else "English",
                    listOf("banglu" to "বাংলু (বাংলা)", "english" to "English"),
                    defaultMode
                ) { defaultMode = it; saveString("default_mode", it) }
            }

            // ── Voice Section ──
            item { BrandSectionHeader("ভয়েস", "Voice") }
            item {
                BrandSwitch("ভয়েস টাইপিং", "মাইক চাপলে বাংলা voice typing চালু হবে", voiceTypingEnabled) {
                    voiceTypingEnabled = it; saveBoolean("voice_typing_enabled", it)
                }
            }
            item {
                BrandSwitch("অফলাইন আগে চেষ্টা", "নেটওয়ার্ক না থাকলে বা আপনি চাইলে ডিভাইসের offline recognizer আগে ব্যবহার করবে", voiceOfflinePreferred) {
                    voiceOfflinePreferred = it; saveBoolean("voice_offline_preferred", it)
                }
            }
            item {
                BrandActionRow("ভয়েস তথ্য আবার দেখান", "ভয়েস typing audio কোথায় যায় সেই অনুমতি বার্তা আবার দেখাবে") {
                    prefs.edit().remove("voice_disclosure_accepted").apply()
                    Toast.makeText(context, "পরেরবার মাইক চাপলে তথ্য বার্তা দেখাবে", Toast.LENGTH_SHORT).show()
                }
            }

            // ── Feedback Section ──
            item { BrandSectionHeader("ফিডব্যাক", "Feedback") }
            item {
                BrandListSetting(
                    "কী ফিডব্যাক",
                    when (keyFeedbackMode) {
                        "silent" -> "সাইলেন্ট"
                        "vibration" -> "ভাইব্রেশন"
                        "both" -> "সাউন্ড + ভাইব্রেশন"
                        else -> "সাউন্ড"
                    },
                    listOf(
                        "both" to "সাউন্ড + ভাইব্রেশন",
                        "silent" to "সাইলেন্ট",
                        "vibration" to "ভাইব্রেশন",
                        "sound" to "সাউন্ড"
                    ),
                    keyFeedbackMode
                ) {
                    keyFeedbackMode = it
                    saveString("key_feedback_mode", it)
                    saveBoolean("haptic_feedback", it == "both" || it == "vibration")
                    saveBoolean("sound_feedback", it == "both" || it == "sound")
                }
            }
            item {
                BrandSwitch("কী প্রিভিউ", "চাপলে কী বড় দেখায়", keyPreview) {
                    keyPreview = it; saveBoolean("key_preview", it)
                }
            }

            // ── Layout Section ──
            item { BrandSectionHeader("লেআউট", "Layout") }
            item {
                BrandSwitch("নম্বর সারি", "অক্ষরের উপরে নম্বর সারি দেখান", numberRow) {
                    numberRow = it; saveBoolean("number_row", it)
                }
            }
            item {
                BrandListSetting(
                    "থিম",
                    when (themeMode) { "light" -> "লাইট"; "dark" -> "ডার্ক"; "amoled" -> "AMOLED"; else -> "অটো (সিস্টেম)" },
                    listOf("auto" to "অটো (সিস্টেম)", "light" to "লাইট", "dark" to "ডার্ক", "amoled" to "AMOLED"),
                    themeMode
                ) { themeMode = it; saveString("theme", it) }
            }
            item {
                BrandListSetting(
                    "কীবোর্ড উচ্চতা",
                    when (keyboardHeight) { "compact" -> "কমপ্যাক্ট"; "tall" -> "লম্বা"; else -> "নরমাল" },
                    listOf("compact" to "কমপ্যাক্ট", "normal" to "নরমাল", "tall" to "লম্বা"),
                    keyboardHeight
                ) { keyboardHeight = it; saveString("keyboard_height", it) }
            }
            item {
                BrandListSetting(
                    "ফন্ট সাইজ",
                    when (keyboardFontSize) { "small" -> "নরমাল"; "extra_large" -> "আরও বড়"; else -> "বড়" },
                    listOf("small" to "নরমাল", "large" to "বড়", "extra_large" to "আরও বড়"),
                    keyboardFontSize
                ) { keyboardFontSize = it; saveString("keyboard_font_size", it) }
            }

            // ── About Section ──
            item { BrandSectionHeader("সম্পর্কে", "About") }
            item {
                BrandActionRow("ব্যবহার শেখার গাইড", "টাইপিং, voice, emoji, settings সব ধাপে ধাপে") {
                    context.startActivity(Intent(context, TutorialActivity::class.java))
                }
            }
            item { BrandInfoRow("ভার্সন", BuildConfig.VERSION_NAME) }
            item { BrandInfoRow("ইঞ্জিন", "SmartEngine 7-layer") }
            item { BrandInfoRow("শব্দভাণ্ডার", "৪৮৫,০০০ বাংলা শব্দ") }
            item { BrandInfoRow("সিড ডিকশনারি", "৬,৫০০+ নির্বাচিত শব্দ") }

            // ── Diagnostics ──
            item { BrandSectionHeader("ডায়াগনস্টিকস", "Diagnostics") }
            item {
                BrandActionRow(
                    "ডায়াগনস্টিকস কপি করুন",
                    "শুধু crash/event count থাকে, কোনো টাইপ করা লেখা থাকে না"
                ) {
                    diagnosticsSummary = buildDiagnosticsSummary(prefs)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Banglu diagnostics", diagnosticsSummary))
                    Toast.makeText(context, "ডায়াগনস্টিকস কপি হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
            item {
                BrandActionRow(
                    "ডায়াগনস্টিকস পরিষ্কার করুন",
                    "টেস্ট রিপোর্ট কাউন্টার রিসেট করুন"
                ) {
                    clearDiagnostics(prefs)
                    diagnosticsSummary = buildDiagnosticsSummary(prefs)
                    Toast.makeText(context, "ডায়াগনস্টিকস পরিষ্কার হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
            item {
                BrandInfoRow("সর্বশেষ অবস্থা", diagnosticsSummary.lineSequence().drop(2).firstOrNull().orEmpty())
            }

            // ── Reset ──
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Coral.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable {
                            prefs.edit().clear().apply()
                            autoCapitalize = true; doubleSpacePeriod = true; suggestions = true
                            typingLearning = true; personalDictionary = true; liteMode = false
                            voiceTypingEnabled = true; voiceOfflinePreferred = false
                            keyFeedbackMode = "both"; keyPreview = true
                            numberRow = true; themeMode = "dark"; defaultMode = "banglu"; keyboardHeight = "normal"; keyboardFontSize = "large"
                        }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("সব সেটিংস রিসেট করুন", color = Coral, fontSize = 16.sp)
                }
            }
        }

        if (showClearLearnedDialog) {
            AlertDialog(
                onDismissRequest = { showClearLearnedDialog = false },
                containerColor = WarmCard,
                titleContentColor = TextLight,
                title = {
                    Text("শেখা শব্দ মুছবেন?", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "এতে আপনার বাছাই করা সাজেশন, শেখা শব্দ এবং নিজের যোগ করা কনভার্সন মুছে যাবে। থিম, উচ্চতা ও অন্য সেটিংস থাকবে।",
                        color = TextMuted,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearLearnedDialog = false
                            coroutineScope.launch {
                                storage.clearLearnedWords()
                                Toast.makeText(context, "শেখা শব্দ মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("মুছে ফেলুন", color = Coral, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearLearnedDialog = false }) {
                        Text("বাতিল", color = TextMuted)
                    }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Brand Components
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BrandSectionHeader(bengali: String, english: String) {
    Row(
        modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 8.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(bengali, color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(english, color = TextMuted, fontSize = 16.sp)
    }
}

@Composable
private fun BrandSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WarmCard)
            .border(1.dp, WarmCardBorder, RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextLight, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 15.sp, lineHeight = 20.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Primary,
                checkedTrackColor = PrimaryTrack,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = WarmCard
            )
        )
    }
}

@Composable
private fun BrandListSetting(
    title: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WarmCard)
            .border(1.dp, WarmCardBorder, RoundedCornerShape(18.dp))
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextLight, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(currentValue, color = Primary.copy(alpha = 0.8f), fontSize = 16.sp)
        }
        Text("›", color = Secondary, fontSize = 26.sp)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = WarmCard,
            titleContentColor = Primary,
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected == key) PrimaryTrack else Color.Transparent)
                                .clickable { onSelect(key); showDialog = false }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (selected == key) Primary else TextMuted, CircleShape)
                                    .background(if (selected == key) Primary else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected == key) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(WarmCard))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(label, color = if (selected == key) Primary else TextLight, fontSize = 18.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("বাতিল", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun BrandSegmentedSetting(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarmDark)
            .border(1.dp, WarmCardBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (key, label) ->
            val active = selected == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Primary else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) Color.White else TextMuted,
                    fontSize = 16.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BrandInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WarmCard)
            .border(1.dp, WarmCardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextLight, fontSize = 18.sp)
        Text(value, color = Primary.copy(alpha = 0.7f), fontSize = 16.sp)
    }
}

@Composable
private fun BrandActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WarmCard)
            .border(1.dp, WarmCardBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextLight, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 15.sp, lineHeight = 20.sp)
        }
        Text("›", color = Primary, fontSize = 22.sp)
    }
}
