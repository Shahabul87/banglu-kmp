package com.banglu.keyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banglu.engine.SmartEngineAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ══════════════════════════════════════════════════════════════════════════════
// Banglu Brand Palette — Warm, Earthy, Bengali Premium
// ══════════════════════════════════════════════════════════════════════════════

private val Gold = Color(0xFFD4A540)
private val GoldLight = Color(0xFFE8C878)
private val GoldDim = Color(0xFF8B7035)
private val Coral = Color(0xFFC4604A)
private val CoralLight = Color(0xFFD4786A)
private val WarmBlack = Color(0xFF110E08)
private val WarmDark = Color(0xFF1A1510)
private val WarmCard = Color(0xFF231E18)
private val WarmCardBorder = Color(0xFF3A3028)
private val TextMuted = Color(0xFFA0907A)
private val TextLight = Color(0xFFD4C8B0)
private val Green = Color(0xFF5CB85C)
private val GreenDim = Color(0xFF2D5A2D)

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_REQUEST_VOICE_PERMISSION = "com.banglu.keyboard.REQUEST_VOICE_PERMISSION"
        private const val REQUEST_RECORD_AUDIO = 9101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent { BangluHomeScreen() }

        if (
            intent?.getBooleanExtra(EXTRA_REQUEST_VOICE_PERMISSION, false) == true &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }
}

@Composable
fun BangluHomeScreen() {
    val context = LocalContext.current
    var demoInput by remember { mutableStateOf("ami") }
    var isEnabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var isDefault by remember { mutableStateOf(isKeyboardDefault(context)) }
    var visible by remember { mutableStateOf(false) }

    val currentContext = rememberUpdatedState(context)
    LaunchedEffect(Unit) {
        SmartEngineAdapter.initializeSync()
        visible = true
        while (isActive) {
            delay(2000)
            isEnabled = isKeyboardEnabled(currentContext.value)
            isDefault = isKeyboardDefault(currentContext.value)
        }
    }

    val demoOutput = remember(demoInput) {
        if (demoInput.isNotEmpty()) try { SmartEngineAdapter.convert(demoInput) } catch (_: Exception) { "" } else ""
    }

    // Gradient background with warm atmosphere
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(WarmBlack, WarmDark, WarmBlack),
                    startY = 0f,
                    endY = 3000f
                )
            )
    ) {
        // Decorative dashed line across top (matching web)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .offset(y = 120.dp)
                .drawBehind {
                    drawLine(
                        color = Gold.copy(alpha = 0.3f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Status Badge ──
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(GreenDim.copy(alpha = 0.4f))
                            .border(1.dp, Green.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pulsing green dot
                        val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
                            initialValue = 0.5f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                            label = "pulseAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .alpha(pulseAlpha)
                                .background(Green)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("বাংলাদেশের #১ ফোনেটিক ইঞ্জিন", color = TextLight, fontSize = 13.sp)
                    }
                }
            }

            // ── Brand Title ──
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(800, 200)) + slideInVertically(tween(700, 200)) { 60 }) {
                    Column {
                        // Large বাংলু with golden gradient feel
                        Text(
                            text = "বাংলু",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = Gold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Tagline
                        Row {
                            Text("ইংরেজি টাইপ করুন, ", color = TextLight, fontSize = 18.sp)
                            Text("বাংলা পান", color = GoldLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Subtitle ──
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(600, 400))) {
                    Text(
                        "মাটির মতো গভীর, নদীর মতো স্বচ্ছন্দ — বাংলু দিয়ে বাংলা টাইপ করুন যেন কথা বলছেন। শুধু ইংরেজিতে লিখুন, বাকিটা আমাদের।",
                        color = TextMuted,
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            // ── Demo Card ──
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(800, 500)) + slideInVertically(tween(700, 500)) { 80 }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = WarmCard),
                        border = BorderStroke(1.dp, WarmCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            // Corner decoration
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .drawBehind {
                                        drawLine(Gold.copy(0.4f), Offset(0f, size.height), Offset(0f, 0f), 1.5f)
                                        drawLine(Gold.copy(0.4f), Offset(0f, 0f), Offset(size.width, 0f), 1.5f)
                                    }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ENGLISH INPUT", color = TextMuted, fontSize = 11.sp, letterSpacing = 3.sp)
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = demoInput,
                                onValueChange = { demoInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Gold.copy(alpha = 0.5f),
                                    unfocusedBorderColor = WarmCardBorder,
                                    cursorColor = Gold
                                )
                            )

                            // Animated arrow
                            val arrowAlpha by rememberInfiniteTransition(label = "arrow").animateFloat(
                                initialValue = 0.4f, targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                                label = "arrowAlpha"
                            )
                            Text(
                                "↓", color = Gold.copy(alpha = arrowAlpha), fontSize = 28.sp,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                textAlign = TextAlign.Center
                            )

                            Text("বাংলা OUTPUT", color = TextMuted, fontSize = 11.sp, letterSpacing = 3.sp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WarmBlack.copy(alpha = 0.5f))
                                    .border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = demoOutput.ifEmpty { " " },
                                    color = Gold,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Bottom corner decoration (right-aligned)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.End)
                                    .drawBehind {
                                        drawLine(Gold.copy(0.4f), Offset(size.width, 0f), Offset(size.width, size.height), 1.5f)
                                        drawLine(Gold.copy(0.4f), Offset(0f, size.height), Offset(size.width, size.height), 1.5f)
                                    }
                            )
                        }
                    }
                }
            }

            // ── Setup CTA ──
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(600, 700)) + slideInVertically(tween(600, 700)) { 40 }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            !isEnabled -> {
                                SetupCTA(
                                    label = "কীবোর্ড সক্রিয় করুন →",
                                    sublabel = "ধাপ ১/৩ — সেটিংসে Banglu চালু করুন",
                                    color = Coral,
                                    onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                                )
                            }
                            !isDefault -> {
                                SetupCTA(
                                    label = "ডিফল্ট কীবোর্ড সেট করুন →",
                                    sublabel = "ধাপ ২/৩ — Banglu কে প্রধান কীবোর্ড হিসেবে বেছে নিন",
                                    color = Coral,
                                    onClick = {
                                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                            .showInputMethodPicker()
                                    }
                                )
                            }
                            else -> {
                                SetupCTA(
                                    label = "✓ সেটআপ সম্পন্ন!",
                                    sublabel = "যেকোনো অ্যাপে বাংলায় টাইপ করুন",
                                    color = Green,
                                    onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
                                )
                            }
                        }

                        // Settings outline button
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f))
                        ) {
                            Text("⚙ সেটিংস", color = Gold.copy(alpha = 0.8f), fontSize = 15.sp)
                        }
                    }
                }
            }

            // ── Stats Bar ──
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(800, 900))) {
                    // Dashed separator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(1.dp)
                            .drawBehind {
                                drawLine(
                                    Gold.copy(alpha = 0.15f),
                                    Offset(0f, 0f), Offset(size.width, 0f),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                                )
                            }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip("৪৮৫,০০০+", "শব্দ")
                        StatChip("৯৯%", "নির্ভুল")
                        StatChip("<৩০ms", "গতি")
                        StatChip("৫০০+", "ইমোজি")
                    }
                }
            }

            // Bottom breathing room
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SetupCTA(label: String, sublabel: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
    Text(sublabel, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun StatChip(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = GoldLight, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

// ── Utility Functions ────────────────────────────────────────────────────────

fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

fun isKeyboardDefault(context: Context): Boolean {
    val currentIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return currentIme?.startsWith(context.packageName) == true
}
