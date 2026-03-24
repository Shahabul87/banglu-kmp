package com.banglu.keyboard

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BangluSettingsScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangluSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE) }

    // Setting states
    var autoCapitalize by remember { mutableStateOf(prefs.getBoolean("auto_capitalize", true)) }
    var doubleSpacePeriod by remember { mutableStateOf(prefs.getBoolean("double_space_period", true)) }
    var suggestions by remember { mutableStateOf(prefs.getBoolean("suggestions", true)) }
    var hapticFeedback by remember { mutableStateOf(prefs.getBoolean("haptic_feedback", true)) }
    var soundFeedback by remember { mutableStateOf(prefs.getBoolean("sound_feedback", true)) }
    var keyPreview by remember { mutableStateOf(prefs.getBoolean("key_preview", true)) }
    var numberRow by remember { mutableStateOf(prefs.getBoolean("number_row", true)) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme", "auto") ?: "auto") }
    var defaultMode by remember { mutableStateOf(prefs.getString("default_mode", "banglu") ?: "banglu") }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Banglu Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding)) {
                // TYPING section
                item { SectionHeader("Typing") }
                item {
                    SwitchSetting(
                        title = "Auto-capitalize",
                        subtitle = "Capitalize first letter after sentence",
                        checked = autoCapitalize,
                        onCheckedChange = { autoCapitalize = it; saveBoolean("auto_capitalize", it) }
                    )
                }
                item {
                    SwitchSetting(
                        title = "Double-space period",
                        subtitle = "Tap space twice for period",
                        checked = doubleSpacePeriod,
                        onCheckedChange = { doubleSpacePeriod = it; saveBoolean("double_space_period", it) }
                    )
                }
                item {
                    SwitchSetting(
                        title = "Suggestions",
                        subtitle = "Show word suggestions while typing",
                        checked = suggestions,
                        onCheckedChange = { suggestions = it; saveBoolean("suggestions", it) }
                    )
                }
                item {
                    ListSetting(
                        title = "Default mode",
                        currentValue = if (defaultMode == "banglu") "\u09AC\u09BE\u0982\u09B2\u09C1 (Bengali)" else "English",
                        options = listOf(
                            "banglu" to "\u09AC\u09BE\u0982\u09B2\u09C1 (Bengali)",
                            "english" to "English"
                        ),
                        selected = defaultMode,
                        onSelect = { defaultMode = it; saveString("default_mode", it) }
                    )
                }

                // FEEDBACK section
                item { SectionHeader("Feedback") }
                item {
                    SwitchSetting(
                        title = "Haptic feedback",
                        subtitle = "Vibrate on key press",
                        checked = hapticFeedback,
                        onCheckedChange = { hapticFeedback = it; saveBoolean("haptic_feedback", it) }
                    )
                }
                item {
                    SwitchSetting(
                        title = "Sound on keypress",
                        subtitle = "Play click sound",
                        checked = soundFeedback,
                        onCheckedChange = { soundFeedback = it; saveBoolean("sound_feedback", it) }
                    )
                }
                item {
                    SwitchSetting(
                        title = "Key preview",
                        subtitle = "Enlarge key on press",
                        checked = keyPreview,
                        onCheckedChange = { keyPreview = it; saveBoolean("key_preview", it) }
                    )
                }

                // LAYOUT section
                item { SectionHeader("Layout") }
                item {
                    SwitchSetting(
                        title = "Number row",
                        subtitle = "Show number row above letters",
                        checked = numberRow,
                        onCheckedChange = { numberRow = it; saveBoolean("number_row", it) }
                    )
                }
                item {
                    ListSetting(
                        title = "Theme",
                        currentValue = when (themeMode) {
                            "light" -> "Light"
                            "dark" -> "Dark"
                            "amoled" -> "AMOLED"
                            else -> "Auto (system)"
                        },
                        options = listOf(
                            "auto" to "Auto (system)",
                            "light" to "Light",
                            "dark" to "Dark",
                            "amoled" to "AMOLED"
                        ),
                        selected = themeMode,
                        onSelect = { themeMode = it; saveString("theme", it) }
                    )
                }

                // ABOUT section
                item { SectionHeader("About") }
                item { InfoRow("Version", "1.0.0") }
                item { InfoRow("Engine", "SmartEngine 7-layer AI") }
                item { InfoRow("Dictionary", "485,000 Bengali words") }
                item { InfoRow("Seed Dictionary", "6,500+ curated entries") }

                // RESET
                item {
                    TextButton(
                        onClick = {
                            prefs.edit().clear().apply()
                            autoCapitalize = true
                            doubleSpacePeriod = true
                            suggestions = true
                            hapticFeedback = true
                            soundFeedback = true
                            keyPreview = true
                            numberRow = true
                            themeMode = "auto"
                            defaultMode = "banglu"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Reset all settings", color = Color.Red)
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp, end = 16.dp)
    )
}

@Composable
fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp)
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ListSetting(
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp)
            Text(
                text = currentValue,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(key)
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected == key,
                                onClick = {
                                    onSelect(key)
                                    showDialog = false
                                }
                            )
                            Text(text = label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 16.sp)
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
