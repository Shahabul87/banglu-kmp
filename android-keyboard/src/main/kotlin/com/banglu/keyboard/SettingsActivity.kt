package com.banglu.keyboard

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand colors (same as MainActivity)
private val Gold = Color(0xFFD4A540)
private val GoldLight = Color(0xFFE8C878)
private val GoldDim = Color(0xFF8B7035)
private val Coral = Color(0xFFC4604A)
private val WarmBlack = Color(0xFF110E08)
private val WarmDark = Color(0xFF1A1510)
private val WarmCard = Color(0xFF231E18)
private val WarmCardBorder = Color(0xFF3A3028)
private val TextMuted = Color(0xFFA0907A)
private val TextLight = Color(0xFFD4C8B0)
private val GoldSwitch = Color(0xFFD4A540)
private val GoldSwitchTrack = Color(0xFF4A3D25)

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { BangluSettingsScreen(onBack = { finish() }) }
    }
}

@Composable
fun BangluSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE) }

    var autoCapitalize by remember { mutableStateOf(prefs.getBoolean("auto_capitalize", true)) }
    var doubleSpacePeriod by remember { mutableStateOf(prefs.getBoolean("double_space_period", true)) }
    var suggestions by remember { mutableStateOf(prefs.getBoolean("suggestions", true)) }
    var keyFeedbackMode by remember {
        mutableStateOf(
            prefs.getString("key_feedback_mode", null)
                ?: when {
                    prefs.getBoolean("sound_feedback", true) -> "sound"
                    prefs.getBoolean("haptic_feedback", true) -> "vibration"
                    else -> "silent"
                }
        )
    }
    var keyPreview by remember { mutableStateOf(prefs.getBoolean("key_preview", true)) }
    var numberRow by remember { mutableStateOf(prefs.getBoolean("number_row", true)) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme", "auto") ?: "auto") }
    var defaultMode by remember { mutableStateOf(prefs.getString("default_mode", "banglu") ?: "banglu") }
    var keyboardHeight by remember { mutableStateOf(prefs.getString("keyboard_height", "normal") ?: "normal") }

    fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(WarmBlack, WarmDark, WarmBlack)))
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
                        Text("←", color = Gold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("সেটিংস", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Banglu Keyboard", color = TextMuted, fontSize = 12.sp)
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
                                Gold.copy(alpha = 0.2f),
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
                BrandListSetting(
                    "ডিফল্ট মোড",
                    if (defaultMode == "banglu") "বাংলু (বাংলা)" else "English",
                    listOf("banglu" to "বাংলু (বাংলা)", "english" to "English"),
                    defaultMode
                ) { defaultMode = it; saveString("default_mode", it) }
            }

            // ── Feedback Section ──
            item { BrandSectionHeader("ফিডব্যাক", "Feedback") }
            item {
                BrandListSetting(
                    "কী ফিডব্যাক",
                    when (keyFeedbackMode) {
                        "silent" -> "সাইলেন্ট"
                        "vibration" -> "ভাইব্রেশন"
                        else -> "সাউন্ড"
                    },
                    listOf(
                        "silent" to "সাইলেন্ট",
                        "vibration" to "ভাইব্রেশন",
                        "sound" to "সাউন্ড"
                    ),
                    keyFeedbackMode
                ) {
                    keyFeedbackMode = it
                    saveString("key_feedback_mode", it)
                    saveBoolean("haptic_feedback", it == "vibration")
                    saveBoolean("sound_feedback", it == "sound")
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

            // ── About Section ──
            item { BrandSectionHeader("সম্পর্কে", "About") }
            item { BrandInfoRow("ভার্সন", "1.0.0") }
            item { BrandInfoRow("ইঞ্জিন", "SmartEngine 7-layer AI") }
            item { BrandInfoRow("শব্দভাণ্ডার", "৪৮৫,০০০ বাংলা শব্দ") }
            item { BrandInfoRow("সিড ডিকশনারি", "৬,৫০০+ নির্বাচিত শব্দ") }

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
                            keyFeedbackMode = "sound"; keyPreview = true
                            numberRow = true; themeMode = "auto"; defaultMode = "banglu"; keyboardHeight = "normal"
                        }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("সব সেটিংস রিসেট করুন", color = Coral, fontSize = 14.sp)
                }
            }
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
                .background(Gold)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(bengali, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(english, color = TextMuted, fontSize = 12.sp)
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
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Gold,
                checkedTrackColor = GoldSwitchTrack,
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
            .clickable { showDialog = true }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(currentValue, color = Gold.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        Text("›", color = GoldDim, fontSize = 22.sp)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = WarmCard,
            titleContentColor = Gold,
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected == key) GoldSwitchTrack else Color.Transparent)
                                .clickable { onSelect(key); showDialog = false }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (selected == key) Gold else TextMuted, CircleShape)
                                    .background(if (selected == key) Gold else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected == key) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(WarmCard))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(label, color = if (selected == key) Gold else TextLight, fontSize = 15.sp)
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
private fun BrandInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextLight, fontSize = 15.sp)
        Text(value, color = Gold.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}
