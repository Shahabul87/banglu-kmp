package com.banglu.keyboard

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.banglu.engine.SmartEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// iOS-inspired Banglu palette: airy surfaces, readable ink, Bengali blue accent.
private var Primary = Color(0xFF0A84FF)
private var Success = Color(0xFF34C759)
private var Secondary = Color(0xFF8E8E93)
private var Coral = Color(0xFFFF7A59)
private var CoralLight = Color(0xFFFFA48F)
private var WarmBlack = Color(0xFFF6F7FB)
private var WarmDark = Color(0xFFEFF3F8)
private var WarmCard = Color(0xFFFFFFFF)
private var WarmCardBorder = Color(0xFFE4E8F0)
private var TextMuted = Color(0xFF6B7280)
private var TextLight = Color(0xFF111827)
private var Green = Color(0xFF34C759)

private fun applyHomePalette(dark: Boolean) {
    if (dark) {
        Primary = Color(0xFF64D2FF)
        Success = Color(0xFF30D158)
        Secondary = Color(0xFF98989D)
        Coral = Color(0xFFFF7A59)
        CoralLight = Color(0xFFFFA48F)
        WarmBlack = Color(0xFF080D16)
        WarmDark = Color(0xFF111827)
        WarmCard = Color(0xFF182235)
        WarmCardBorder = Color(0xFF27364F)
        TextMuted = Color(0xFFA8B3C7)
        TextLight = Color(0xFFF8FAFC)
        Green = Color(0xFF30D158)
    } else {
        Primary = Color(0xFF0A84FF)
        Success = Color(0xFF34C759)
        Secondary = Color(0xFF8E8E93)
        Coral = Color(0xFFFF7A59)
        CoralLight = Color(0xFFFFA48F)
        WarmBlack = Color(0xFFF6F7FB)
        WarmDark = Color(0xFFEFF3F8)
        WarmCard = Color(0xFFFFFFFF)
        WarmCardBorder = Color(0xFFE4E8F0)
        TextMuted = Color(0xFF6B7280)
        TextLight = Color(0xFF111827)
        Green = Color(0xFF34C759)
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_REQUEST_VOICE_PERMISSION = "com.banglu.keyboard.REQUEST_VOICE_PERMISSION"
        private const val REQUEST_RECORD_AUDIO = 9101
    }

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

        setBangluContent { BangluHomeScreen() }

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
    val prefs = remember { remoteBangluPrefs(context) }
    val themeMode = prefs.getString("theme", "dark") ?: "dark"
    val systemDark = isSystemInDarkTheme()
    val darkTheme = themeMode == "dark" || themeMode == "amoled" || (themeMode == "auto" && systemDark)
    applyHomePalette(darkTheme)
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
    var onboardingSeen by remember { mutableStateOf(prefs.getBoolean("onboarding_seen", false)) }
    if (!onboardingSeen) {
        BangluAnimatedOnboarding(
            darkTheme = darkTheme,
            onFinish = {
                prefs.edit().putBoolean("onboarding_seen", true).apply()
                onboardingSeen = true
            }
        )
        return
    }

    var demoInput by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var isDefault by remember { mutableStateOf(isKeyboardDefault(context)) }
    var visible by remember { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentContext = rememberUpdatedState(context)
    LaunchedEffect(Unit) {
        visible = true
        while (isActive) {
            delay(2000)
            isEnabled = isKeyboardEnabled(currentContext.value)
            isDefault = isKeyboardDefault(currentContext.value)
        }
    }

    // Soft iOS-style setup background.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (darkTheme) listOf(Color(0xFF080D16), WarmBlack, WarmDark)
                    else listOf(Color(0xFFF8FAFF), WarmBlack, WarmDark),
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
                        color = Primary.copy(alpha = 0.12f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                }
        )

        LazyColumn(
            state = homeListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Header Navigation ──
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -30 }) {
                    HomeHeaderNav(context)
                }
            }

            // ── Hero Message ──
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(800, 200)) + slideInVertically(tween(700, 200)) { 60 }) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "শুধু ছোট হাতের ইংরেজিতে",
                            color = TextLight,
                            fontSize = 27.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "বাংলা টাইপ করুন",
                            color = Success,
                            fontSize = 32.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Subtitle ──
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(600, 400))) {
                    Text(
                        "সাথে থাকছে — voice typing, নিজের ডিকশনারি তৈরি, smart suggestions, emoji এবং AI ব্যবহার (শীঘ্রই আসছে)।",
                        color = TextMuted,
                        fontSize = 15.sp,
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
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = WarmCard),
                        border = BorderStroke(1.dp, WarmCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "এখানে লিখে দেখুন",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    letterSpacing = 3.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                val clipboard = remember {
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                }
                                @Composable
                                fun ActionChip(label: String, onClick: () -> Unit) {
                                    Text(
                                        label,
                                        color = Primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = onClick)
                                            .background(Primary.copy(alpha = 0.10f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                ActionChip("কপি") {
                                    if (demoInput.isNotEmpty()) clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("Banglu", demoInput)
                                    )
                                }
                                ActionChip("কাট") {
                                    if (demoInput.isNotEmpty()) {
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Banglu", demoInput))
                                        demoInput = ""
                                    }
                                }
                                ActionChip("মুছুন") { demoInput = "" }
                            }

                            // Single Bengali editor: the Banglu keyboard itself
                            // converts and shows suggestions in its strip — no
                            // separate input/output boxes. Standard EditText so
                            // long-press gives cut/copy/paste out of the box.
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().height(230.dp),
                                factory = { viewContext ->
                                    EditText(viewContext).apply {
                                        isFocusableInTouchMode = true
                                        setTextIsSelectable(true)
                                        hint = "ami banglay likhi…"
                                        setText(demoInput)
                                        setSelection(text.length)
                                        textSize = 24f
                                        typeface = Typeface.DEFAULT_BOLD
                                        includeFontPadding = false
                                        gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                        setPadding(28, 22, 28, 22)
                                        inputType = InputType.TYPE_CLASS_TEXT or
                                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                                        addTextChangedListener(object : TextWatcher {
                                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                            override fun afterTextChanged(s: Editable?) {
                                                val value = s?.toString().orEmpty()
                                                if (value != demoInput) demoInput = value
                                            }
                                        })
                                        setOnFocusChangeListener { _, hasFocus ->
                                            if (hasFocus) {
                                                scope.launch {
                                                    homeListState.animateScrollToItem(3)
                                                }
                                            }
                                        }
                                    }
                                },
                                update = { editText ->
                                    editText.setTextColor(Primary.toArgb())
                                    editText.setHintTextColor(TextMuted.copy(alpha = 0.6f).toArgb())
                                    editText.background = GradientDrawable().apply {
                                        shape = GradientDrawable.RECTANGLE
                                        cornerRadius = 12.dp.value * editText.resources.displayMetrics.density
                                        setColor((if (darkTheme) Color(0xFF101A2A) else Color(0xFFF3F7FF)).toArgb())
                                        setStroke(
                                            (1.2f * editText.resources.displayMetrics.density).toInt().coerceAtLeast(1),
                                            WarmCardBorder.toArgb()
                                        )
                                    }
                                    if (editText.text.toString() != demoInput) {
                                        editText.setText(demoInput)
                                        editText.setSelection(editText.text.length)
                                    }
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
                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.22f))
                        ) {
                            Text("⚙ সেটিংস", color = Primary, fontSize = 17.sp)
                        }

                        OutlinedButton(
                            onClick = { context.startActivity(Intent(context, TutorialActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Green.copy(alpha = 0.25f))
                        ) {
                            Text("📘 ব্যবহার শেখা", color = Green, fontSize = 17.sp)
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
                                    Primary.copy(alpha = 0.10f),
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

            // ── Privacy policy footer link ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))
                        )
                    }) {
                        Text(
                            "🔒 প্রাইভেসি পলিসি · Privacy Policy",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Bottom breathing room
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

/** Hosted policy — same URL as the Play Console listing entry. */
private const val PRIVACY_POLICY_URL = "https://shahabul87.github.io/banglu-privacy-policy/"

private data class OnboardingFeature(
    val marker: String,
    val title: String,
    val body: String,
    val examples: List<Pair<String, String>>,
    val accent: Color
)

private val onboardingFeatures = listOf(
    OnboardingFeature(
        marker = "a",
        title = "Lowercase-only Bangla",
        body = "Shift ছাড়াই ছোট হাতের ইংরেজি দিয়ে বাংলা লিখুন। Engine শব্দ, dictionary এবং user preference দেখে সঠিক output বেছে নেয়।",
        examples = listOf("ami" to "আমি", "taka" to "টাকা", "doroja" to "দরজা"),
        accent = Color(0xFF64D2FF)
    ),
    OnboardingFeature(
        marker = "ত",
        title = "Smart ambiguous suggestion",
        body = "তা/টা, দ/ড, থ/ঠ টাইপের confusing sound engine candidate হিসেবে রাখে। সেরা শব্দ editor-এ, বাকি variant suggestion-এ যায়।",
        examples = listOf("tak" to "তাক / টাক", "dan" to "দান / ডান", "pore" to "পরে / পড়ে"),
        accent = Color(0xFF30D158)
    ),
    OnboardingFeature(
        marker = "c",
        title = "Banglu phonetic mapping",
        body = "নতুন rule পরিষ্কার: c = ছ, ch = চ। Tutorial-এ vowel, kar, conjunct, fola, number এবং punctuation mapping রাখা হয়েছে।",
        examples = listOf("caya" to "ছায়া", "chabi" to "চাবি", "shikkha" to "শিক্ষা"),
        accent = Color(0xFFFF7A59)
    ),
    OnboardingFeature(
        marker = "ম",
        title = "Bengali voice typing",
        body = "Mic চাপলেই Bangla dictation editor-এ যায়। pause অনুযায়ী কমা/দাঁড়ি, long sentence handling, cancel এবং delete flow polish করা হয়েছে।",
        examples = listOf("আমি আসব pause" to "আমি আসব।", "কমা" to ",", "দাঁড়ি" to "।"),
        accent = Color(0xFFBF5AF2)
    ),
    OnboardingFeature(
        marker = "অ",
        title = "Emoji, privacy, dark UI",
        body = "Expression panel, controlled learning, font size control, dark-first premium theme এবং low-latency keyboard experience একসাথে রাখা হয়েছে।",
        examples = listOf("হাসি" to "emoji suggestion", "learning" to "user controlled", "dark" to "default theme"),
        accent = Color(0xFFFF9F0A)
    )
)

@Composable
private fun BangluAnimatedOnboarding(darkTheme: Boolean, onFinish: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    val transition = rememberInfiniteTransition(label = "onboardingFloat")
    val floatY by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "floatY"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        visible = true
        while (isActive) {
            delay(2800)
            page = (page + 1) % onboardingFeatures.size
        }
    }

    val feature = onboardingFeatures[page]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (darkTheme) {
                        listOf(Color(0xFF050914), Color(0xFF080D16), Color(0xFF101827))
                    } else {
                        listOf(Color(0xFFF8FAFF), Color(0xFFEFF6FF), Color(0xFFF7FAFC))
                    }
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-80).dp, y = 64.dp)
                .clip(CircleShape)
                .background(feature.accent.copy(alpha = glowAlpha))
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 78.dp, y = 26.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.16f))
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -30 }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.banglu_logo),
                                contentDescription = "Banglu logo",
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("বাংলু", color = TextLight, fontSize = 28.sp, fontWeight = FontWeight.Black)
                                Text("Bangla keyboard", color = TextMuted, fontSize = 14.sp)
                            }
                        }
                        TextButton(onClick = onFinish) {
                            Text("Skip", color = Primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(visible, enter = fadeIn(tween(700, 150)) + slideInVertically(tween(700, 150)) { 45 }) {
                    Column {
                        Text(
                            "বাংলা লিখুন\nআরও দ্রুত",
                            color = TextLight,
                            fontSize = 38.sp,
                            lineHeight = 43.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Lowercase typing, smart suggestion, voice punctuation, emoji এবং privacy-ready design এক keyboard-এ।",
                            color = TextMuted,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
            }

            item {
                OnboardingFeatureCard(
                    feature = feature,
                    page = page,
                    total = onboardingFeatures.size,
                    floatingOffset = floatY
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onboardingFeatures.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(if (index == page) 26.dp else 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (index == page) feature.accent else TextMuted.copy(alpha = 0.28f))
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { page = (page + 1) % onboardingFeatures.size },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, feature.accent.copy(alpha = 0.34f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = feature.accent)
                    ) {
                        Text("আরও দেখুন", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text("শুরু করুন", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Text(
                    "Setup-এর পর যেকোনো app-এ Banglu keyboard খুলে voice, emoji, tutorial এবং smart lowercase typing ব্যবহার করতে পারবেন।",
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeHeaderNav(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(WarmCard.copy(alpha = 0.86f))
            .border(1.dp, WarmCardBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.banglu_logo),
                contentDescription = "Banglu logo",
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("বাংলু", color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Text("Keyboard", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderNavButton("প্রোফাইল", Modifier.weight(1f)) {
                context.startActivity(
                    Intent().setClassName(context.packageName, "com.banglu.keyboard.AccountActivity")
                )
            }
            HeaderNavButton("সেটিংস", Modifier.weight(1f)) {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }
            HeaderNavButton("গাইড", Modifier.weight(1f)) {
                context.startActivity(Intent(context, TutorialActivity::class.java))
            }
        }
    }
}

@Composable
private fun HeaderNavButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun OnboardingFeatureCard(
    feature: OnboardingFeature,
    page: Int,
    total: Int,
    floatingOffset: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = WarmCard.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, feature.accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = floatingOffset.dp)
                        .size(60.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(feature.accent.copy(alpha = 0.18f))
                        .border(1.dp, feature.accent.copy(alpha = 0.28f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(feature.marker, color = feature.accent, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Text("${page + 1}/$total", color = TextMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(feature.title, color = TextLight, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                Text(feature.body, color = TextMuted, fontSize = 14.sp, lineHeight = 21.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                feature.examples.take(2).forEach { (input, output) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (WarmBlack == Color(0xFF080D16)) Color(0xFF101A2A) else Color(0xFFF3F7FF))
                            .border(1.dp, WarmCardBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(input, color = TextLight, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("→", color = feature.accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(output, color = feature.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupCTA(label: String, sublabel: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
    Text(sublabel, color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun StatChip(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Success, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = TextMuted, fontSize = 13.sp)
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
