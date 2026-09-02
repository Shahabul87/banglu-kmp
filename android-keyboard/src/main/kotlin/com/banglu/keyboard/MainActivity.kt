package com.banglu.keyboard

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.banglu.engine.ShowcaseWords
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ── S147 mock palette (banglu-android-mocks.html, "same to same") ───────────
// One committed dark-plum look for the whole app UI (:ui process screens).
internal val MockBg = Color(0xFF0F0E1A)
internal val MockBg2 = Color(0xFF1A1930)
internal val MockCard = Color(0xFF22213A)
internal val MockCard2 = Color(0xFF2B2A46)
internal val MockLine = Color(0x17FFFFFF)          // rgba(255,255,255,.09)
internal val MockInk = Color(0xFFF4EEE3)
internal val MockMuted = Color(0xFFA9A4BC)
internal val MockTerra = Color(0xFFD9633F)
internal val MockMustard = Color(0xFFE9B84A)
internal val MockMoss = Color(0xFF3FA372)
internal val MockSky = Color(0xFF6AA9FF)
internal val MockCapHotBorder = Color(0xFFB8862F)
internal val MockFieldInk = Color(0xFF1B1A2E)

// Mock fonts: Tiro Bangla (the mock's --bn display serif), JetBrains Mono for
// romans; Noto Sans Bengali is the app-wide default via BangluComposeHost.
internal val BanglaSerif = FontFamily(Font(R.font.tiro_bangla))
internal val RomanMono = FontFamily(Font(R.font.jetbrains_mono))
internal val BanglaSans = FontFamily(
    Font(R.font.noto_sans_bengali_regular, FontWeight.Normal),
    Font(R.font.noto_sans_bengali_regular, FontWeight.Medium),
    Font(R.font.noto_sans_bengali_bold, FontWeight.SemiBold),
    Font(R.font.noto_sans_bengali_bold, FontWeight.Bold),
    Font(R.font.noto_sans_bengali_bold, FontWeight.Black)
)

internal fun toBengaliDigits(s: String): String =
    buildString { s.forEach { c -> append(if (c in '0'..'9') "০১২৩৪৫৬৭৮৯"[c - '0'] else c) } }

private fun bnDigit(n: Int): String = toBengaliDigits(n.toString())

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_REQUEST_VOICE_PERMISSION = "com.banglu.keyboard.REQUEST_VOICE_PERMISSION"
        private const val REQUEST_RECORD_AUDIO = 9101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyMockSystemBars(this)
        setBangluContent { BangluHomeScreen() }

        if (
            intent?.getBooleanExtra(EXTRA_REQUEST_VOICE_PERMISSION, false) == true &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }
}

/** Dark-plum system bars for every mock-themed activity. */
internal fun applyMockSystemBars(activity: Activity) {
    @Suppress("DEPRECATION")
    activity.window.statusBarColor = MockBg.toArgb()
    @Suppress("DEPRECATION")
    activity.window.navigationBarColor = MockBg2.toArgb()
    @Suppress("DEPRECATION")
    activity.window.decorView.systemUiVisibility = 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        activity.window.insetsController?.setSystemBarsAppearance(0, mask)
    }
}

@Composable
fun BangluHomeScreen() {
    val context = LocalContext.current
    val prefs = remember { remoteBangluPrefs(context) }
    SideEffect { (context as? Activity)?.let { applyMockSystemBars(it) } }

    var onboardingSeen by remember { mutableStateOf(prefs.getBoolean("onboarding_seen", false)) }
    if (!onboardingSeen) {
        BangluAnimatedOnboarding(
            onFinish = {
                prefs.edit().putBoolean("onboarding_seen", true).apply()
                onboardingSeen = true
            }
        )
        return
    }

    var demoInput by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    // S55 (F-ANDROID-007): once the user has been sent to Android's keyboard
    // settings at least once, a still-disabled toggle on return means they
    // likely pressed Back on the second ("restart apps?") confirmation —
    // the hint must say so instead of repeating the generic first-visit copy.
    var attemptedKeyboardEnable by remember { mutableStateOf(false) }
    var isDefault by remember { mutableStateOf(isKeyboardDefault(context)) }
    val homeListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val versionLabel = remember {
        toBengaliDigits(
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            }.getOrDefault("")
        )
    }

    val currentContext = rememberUpdatedState(context)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2000)
            isEnabled = isKeyboardEnabled(currentContext.value)
            isDefault = isKeyboardDefault(currentContext.value)
        }
    }

    val setupDone = isEnabled && isDefault

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(modifier = Modifier.fillMaxSize().background(MockBg)) {
        LazyColumn(
            state = homeListState,
            modifier = Modifier.weight(1f).statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── brand row: logo + state pill ──
            item {
                Reveal(visible, 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MockTerra),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("বা", color = Color.White, fontSize = 15.sp, fontFamily = BanglaSerif)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("বাংলু", color = MockInk, fontSize = 22.sp, fontFamily = BanglaSerif)
                    }
                    StatusPill(setupDone)
                }
                }
            }

            // ── hero ──
            item {
                Reveal(visible, 120) {
                Text(
                    buildAnnotatedString {
                        append("শুধু ছোট হাতের ইংরেজি দিয়ে\n")
                        withStyle(SpanStyle(color = MockMustard)) { append("সব বাংলা শব্দ") }
                        append(" লিখুন")
                    },
                    color = MockInk,
                    fontSize = 32.sp,
                    lineHeight = 43.sp,
                    fontFamily = BanglaSans,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                }
            }

            // ── stepper card ──
            item {
                Reveal(visible, 240) {
                SetupStepperCard(
                    versionLabel = versionLabel,
                    isEnabled = isEnabled,
                    isDefault = isDefault,
                    attempted = attemptedKeyboardEnable,
                    onEnable = {
                        attemptedKeyboardEnable = true
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onPick = {
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .showInputMethodPicker()
                    },
                    onDone = { scope.launch { homeListState.animateScrollToItem(3) } }
                )
                }
            }

            // ── try card ──
            item {
                Reveal(visible, 360) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MockCard),
                    border = BorderStroke(1.dp, MockLine)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            "এখনই চেষ্টা করুন",
                            color = MockMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // White field, like the mock — the Banglu keyboard itself
                        // converts here; standard EditText so long-press gives
                        // cut/copy/paste out of the box.
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            factory = { viewContext ->
                                EditText(viewContext).apply {
                                    isFocusableInTouchMode = true
                                    setTextIsSelectable(true)
                                    hint = "sbadhinota লিখে দেখুন…"
                                    setText(demoInput)
                                    setSelection(text.length)
                                    textSize = 18f
                                    includeFontPadding = false
                                    gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                                    setPadding(30, 16, 30, 16)
                                    inputType = InputType.TYPE_CLASS_TEXT or
                                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                    imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                                    setTextColor(MockFieldInk.toArgb())
                                    setHintTextColor(0xFF9A94AC.toInt())
                                    background = GradientDrawable().apply {
                                        shape = GradientDrawable.RECTANGLE
                                        cornerRadius = 12 * resources.displayMetrics.density
                                        setColor(android.graphics.Color.WHITE)
                                    }
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
                                            scope.launch { homeListState.animateScrollToItem(3) }
                                        }
                                    }
                                }
                            },
                            update = { editText ->
                                if (editText.text.toString() != demoInput) {
                                    editText.setText(demoInput)
                                    editText.setSelection(editText.text.length)
                                }
                            }
                        )

                        // chips — romans worth trying (each pinned by S147)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShowcaseWords.TRY_WORDS.flatMap { it.variants }.forEach { roman ->
                                Text(
                                    roman,
                                    color = MockInk,
                                    fontSize = 11.sp,
                                    fontFamily = RomanMono,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MockCard2)
                                        .border(1.dp, MockLine, RoundedCornerShape(999.dp))
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
                }
            }

            // ── power pages: one hard-word card per swipeable page ──
            item {
                Reveal(visible, 480) {
                val pagerState = rememberPagerState(pageCount = { ShowcaseWords.FAMILIES.size })
                Column {
                    HorizontalPager(
                        state = pagerState,
                        pageSpacing = 12.dp
                    ) { page ->
                        FamilyExplorer(ShowcaseWords.FAMILIES[page])
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ShowcaseWords.FAMILIES.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .height(6.dp)
                                    .width(if (index == pagerState.currentPage) 18.dp else 6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (index == pagerState.currentPage) MockMustard
                                        else Color(0x33FFFFFF)
                                    )
                            )
                        }
                    }
                }
                }
            }

            // ── footer ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))
                        )
                    }) {
                        Text("🔒 প্রাইভেসি পলিসি · Privacy Policy", color = MockMuted, fontSize = 12.sp)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        BottomNav(context)
    }
}

/** Hosted policy — same URL as the Play Console listing entry. */
private const val PRIVACY_POLICY_URL = "https://shahabul87.github.io/banglu-privacy-policy/"

/** Tester/user feedback form (S146) — one tap from the bottom nav. */
private const val FEEDBACK_URL = "https://www.bangluweb.com/feedback"

@Composable
private fun StatusPill(setupDone: Boolean) {
    val color = if (setupDone) MockMoss else MockMustard
    Text(
        if (setupDone) "● চালু আছে" else "● এখনো চালু হয়নি",
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

// ── stepper ──────────────────────────────────────────────────────────────────

@Composable
private fun SetupStepperCard(
    versionLabel: String,
    isEnabled: Boolean,
    isDefault: Boolean,
    attempted: Boolean,
    onEnable: () -> Unit,
    onPick: () -> Unit,
    onDone: () -> Unit
) {
    val setupDone = isEnabled && isDefault
    val doneCount = 1 + (if (isEnabled) 1 else 0) + (if (isDefault) 1 else 0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MockCard),
        border = BorderStroke(1.dp, MockLine)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("তিন ধাপে চালু", color = MockMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${bnDigit(doneCount)} / ৩",
                    color = MockMuted,
                    fontSize = 10.sp,
                    fontFamily = RomanMono
                )
            }

            StepRow(
                number = "১", title = "ইনস্টল হয়েছে",
                sub = if (versionLabel.isEmpty()) "বাংলু" else "বাংলু $versionLabel",
                state = StepState.DONE, mini = { MiniToggleOn() }
            )
            StepConnector()
            StepRow(
                number = "২", title = "বাংলু চালু করুন",
                sub = "সেটিংসে টগল অন → দুটো OK",
                state = when {
                    isEnabled -> StepState.DONE
                    else -> StepState.NOW
                },
                mini = { MiniToggleGlow(on = isEnabled) }
            )
            StepConnector()
            StepRow(
                number = "৩", title = "প্রধান কীবোর্ড করুন",
                sub = "তালিকা থেকে বাংলু বেছে নিন",
                state = when {
                    setupDone -> StepState.DONE
                    isEnabled -> StepState.NOW
                    else -> StepState.DIM
                },
                mini = { MiniKeyboard() }
            )

            val btnColor = if (setupDone) MockMoss else MockTerra
            Button(
                onClick = when {
                    setupDone -> onDone
                    isEnabled -> onPick
                    else -> onEnable
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    when {
                        setupDone -> "লিখা শুরু করুন →"
                        isEnabled -> "কীবোর্ড বেছে নিন →"
                        else -> "সেটিংসে চালু করুন →"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                when {
                    setupDone -> "সেটআপ সম্পন্ন ✓ — যেকোনো অ্যাপে বাংলায় লিখুন"
                    // S55: return with the toggle still off = the user likely
                    // pressed Back on Android's second confirmation.
                    !isEnabled && attempted -> "টগল এখনো বন্ধ — দুটো নিশ্চিতকরণেই OK চাপতে হয়, আবার চেষ্টা করুন"
                    else -> "ফিরে এলে এই স্ক্রিন নিজেই পরের ধাপে যাবে"
                },
                color = MockMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

private enum class StepState { DONE, NOW, DIM }

@Composable
private fun StepRow(
    number: String,
    title: String,
    sub: String,
    state: StepState,
    mini: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state == StepState.NOW) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MockTerra.copy(alpha = 0.20f))
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            StepState.DONE -> MockMoss
                            StepState.NOW -> MockTerra
                            StepState.DIM -> Color.Transparent
                        }
                    )
                    .border(
                        2.dp,
                        when (state) {
                            StepState.DONE -> MockMoss
                            StepState.NOW -> MockTerra
                            StepState.DIM -> Color(0x33FFFFFF)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state == StepState.DONE) "✓" else number,
                    color = if (state == StepState.DIM) MockMuted else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (state == StepState.DIM) MockMuted else MockInk,
                fontSize = 15.sp,
                fontWeight = if (state == StepState.DIM) FontWeight.SemiBold else FontWeight.Bold
            )
            Text(sub, color = MockMuted, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        mini()
    }
}

@Composable
private fun StepConnector() {
    Box(
        modifier = Modifier
            .padding(start = 19.dp)
            .width(2.dp)
            .height(10.dp)
            .drawBehind {
                drawLine(
                    color = Color(0x2EFFFFFF),
                    start = Offset(1f, 0f),
                    end = Offset(1f, size.height),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
                )
            }
    )
}

@Composable
private fun MiniFrame(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 46.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MockCard2)
            .border(1.dp, MockLine, RoundedCornerShape(9.dp)),
        content = content
    )
}

@Composable
private fun MiniToggleOn() {
    MiniFrame {
        Box(Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 8.dp).size(width = 40.dp, height = 8.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x26FFFFFF)))
        Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 8.dp).size(width = 16.dp, height = 9.dp).clip(RoundedCornerShape(999.dp)).background(MockMoss))
        Box(Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 24.dp).size(width = 30.dp, height = 8.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x26FFFFFF)))
    }
}

@Composable
private fun MiniToggleGlow(on: Boolean) {
    MiniFrame {
        Box(Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 8.dp).size(width = 36.dp, height = 8.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x26FFFFFF)))
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 7.dp).size(width = 18.dp, height = 11.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (on) MockMoss else Color(0x40FFFFFF))
                .border(2.dp, if (on) MockMoss else MockTerra.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
        )
        Box(Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 26.dp).size(width = 40.dp, height = 8.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x26FFFFFF)))
        Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 26.dp).size(width = 16.dp, height = 9.dp).clip(RoundedCornerShape(999.dp)).background(MockMoss))
    }
}

@Composable
private fun MiniKeyboard() {
    MiniFrame {
        Box(Modifier.align(Alignment.BottomCenter).padding(start = 4.dp, end = 4.dp, bottom = 4.dp).fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x1FFFFFFF)))
        Box(Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 7.dp).size(14.dp).clip(CircleShape).border(2.dp, MockMustard, CircleShape))
    }
}

// ── power cards ──────────────────────────────────────────────────────────────

/** Staggered entrance used across home and onboarding. */
@Composable
private fun Reveal(visible: Boolean, delayMs: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, delayMillis = delayMs)) +
            slideInVertically(tween(500, delayMillis = delayMs)) { 40 }
    ) { content() }
}

// Bengali combining marks: vowel signs, candrabindu/anusvara/visarga,
// nukta, virama, AU-length mark. A colour-span boundary inside a grapheme
// cluster breaks Android's shaping (orphaned signs render dotted circles),
// so the highlight is snapped outward to whole clusters first.
private fun isBengaliCombining(c: Char): Boolean =
    c in '\u09BE'..'\u09CC' || c == '\u0981' || c == '\u0982' || c == '\u0983' ||
        c == '\u09BC' || c == '\u09CD' || c == '\u09D7' || c == '\u200D'

private fun highlighted(word: ShowcaseWords.Word) = buildAnnotatedString {
    val text = word.bengali
    val i = if (word.highlight.isEmpty()) -1 else text.indexOf(word.highlight)
    if (i < 0) {
        append(text)
        return@buildAnnotatedString
    }
    var start = i
    var end = i + word.highlight.length
    while (start > 0 && (isBengaliCombining(text[start]) || text[start - 1] == '\u09CD')) start--
    while (end < text.length && (isBengaliCombining(text[end]) || text[end - 1] == '\u09CD')) end++
    append(text.substring(0, start))
    withStyle(SpanStyle(color = MockMustard)) { append(text.substring(start, end)) }
    append(text.substring(end))
}

@Composable
private fun FamilyExplorer(family: ShowcaseWords.Family) {
    var selected by remember(family) { mutableStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MockCard)
            .border(1.dp, MockLine, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            family.caps.forEachIndexed { index, capWords ->
                KeyCapButton(capWords.cap, hot = index == selected) { selected = index }
            }
        }
        Text(family.tagline, color = MockMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Crossfade(targetState = selected, label = "capWords") { capIndex ->
            Column {
                family.caps[capIndex].words.forEach { word ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .drawBehind {
                                drawLine(
                                    color = Color(0x17FFFFFF),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
                                )
                            }
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            word.variants.forEach { roman ->
                                Text(
                                    roman,
                                    color = MockMustard,
                                    fontSize = 15.sp,
                                    fontFamily = RomanMono
                                )
                            }
                        }
                        Text(highlighted(word), color = MockInk, fontSize = 28.sp, fontFamily = BanglaSerif)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCapButton(label: String, hot: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 52.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hot) MockMustard else MockCard2)
            .border(1.dp, if (hot) MockCapHotBorder else MockLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (hot) MockFieldInk else MockInk,
            fontSize = if (label.length > 2) 20.sp else 26.sp,
            fontFamily = BanglaSerif
        )
    }
}

// ── bottom nav ───────────────────────────────────────────────────────────────

@Composable
private fun BottomNav(context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(Color(0x17FFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 2f)
            }
            .background(MockBg2)
            .navigationBarsPadding()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("⌂", "হোম", active = true) { }
        NavItem("📘", "শিখুন") {
            context.startActivity(Intent(context, TutorialActivity::class.java))
        }
        NavItem("⚙", "সেটিংস") {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
        NavItem("💬", "মতামত") {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(FEEDBACK_URL)))
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(icon, color = if (active) MockMustard else MockMuted, fontSize = 20.sp)
        // S162 (tester: "হোম শিখুন সেটিংস মতামত … not properly visible"):
        // 11sp muted labels were below comfortable Bengali legibility on the
        // 64dp bar — 14sp, and inactive labels lift to near-ink.
        Text(
            label,
            color = if (active) MockMustard else MockInk.copy(alpha = 0.82f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── first-run onboarding: features → mapping explorer → setup ───────────────

@Composable
private fun BangluAnimatedOnboarding(onFinish: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(0) }
    val pageCount = 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MockBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Text(
            "স্লাইড ${bnDigit(page + 1)} / ${bnDigit(pageCount)}",
            color = MockMustard,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MockMustard.copy(alpha = 0.08f))
                .border(1.dp, MockMustard.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(tween(250)) { -it / 3 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(350)) { -it / 3 } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200)))
                    }
                },
                label = "onboardingSlide"
            ) { current ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (current) {
                        0 -> OnboardingWelcomeSlide()
                        1 -> OnboardingFeatureSlide()
                        2 -> OnboardingMappingSlide()
                        else -> OnboardingSetupSlide(context)
                    }
                }
            }
        }
        // S168 (audit P3-2): a slide taller than the viewport fades out at
        // the bottom instead of cutting a card mid-title — the scroll cue.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(40.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MockBg)))
        )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pageCount) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (index == page) 18.dp else 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (index == page) MockMustard else Color(0x33FFFFFF))
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (page == pageCount - 1) "পরে করব" else "এড়িয়ে যান",
                color = MockMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onFinish)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            val lastPage = page == pageCount - 1
            Text(
                if (lastPage) "শুরু করুন" else "পরের →",
                color = if (lastPage) Color.White else MockFieldInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { if (lastPage) onFinish() else page += 1 }
                    .background(if (lastPage) MockMoss else MockMustard)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun OnboardingTitle(before: String, em: String, after: String) {
    Text(
        buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(color = MockMustard)) { append(em) }
            append(after)
        },
        color = MockInk,
        fontSize = 32.sp,
        lineHeight = 42.sp,
        fontFamily = BanglaSans,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 14.dp)
    )
}

@Composable
private fun OnboardingWelcomeSlide() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 84.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Reveal(shown, 0) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MockTerra),
                contentAlignment = Alignment.Center
            ) {
                Text("বা", color = Color.White, fontSize = 42.sp, fontFamily = BanglaSerif)
            }
        }
        Reveal(shown, 150) {
            Text(
                buildAnnotatedString {
                    append("বাংলু ফোনেটিক টাইপিং-এ\nআপনাকে ")
                    withStyle(SpanStyle(color = MockMustard)) { append("স্বাগতম") }
                },
                color = MockInk,
                fontSize = 33.sp,
                lineHeight = 46.sp,
                fontFamily = BanglaSans,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 26.dp)
            )
        }
        Reveal(shown, 300) {
            Text(
                "শুনতে যেমন, লিখতে তেমন — চলুন ঘুরে দেখি।",
                color = MockMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun OnboardingFeatureSlide() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Reveal(shown, 0) { OnboardingTitle("বাংলু টাইপিং অ্যাপের\nকিছু ", "বৈশিষ্ট্য", "") }
    Spacer(modifier = Modifier.height(16.dp))
    Reveal(shown, 140) {
        OnboardingFeatureCard(
            icon = "🔤",
            title = "বাংলা + English — দুটোই লেখা যায়",
            sub = "এক কীবোর্ডেই দুই ভাষা, মোড বদলান এক ট্যাপে"
        )
    }
    Reveal(shown, 280) {
        OnboardingFeatureCard(
            icon = "অ",
            title = "শুধু ছোট হাতের ইংরেজিতেই সব বাংলা",
            roman = "sbadhinota",
            bengali = ShowcaseWords.FEATURE_WORDS[0]
        )
    }
    Reveal(shown, 420) {
        OnboardingFeatureCard(
            icon = "✨",
            title = "স্মার্ট সাজেশন বার",
            sub = "টাইপের সাথেই সেরা শব্দ — বার থেকে বেছে নিন"
        )
    }
    Reveal(shown, 560) {
        OnboardingFeatureCard(
            icon = "🎙",
            title = "ভয়েস টাইপিং — বাংলা ও English দুটোতেই",
            sub = "বলুন, লেখা হয়ে যায় — সবকিছু অফলাইনে"
        )
    }
}

@Composable
private fun OnboardingFeatureCard(
    icon: String,
    title: String,
    roman: String? = null,
    bengali: ShowcaseWords.Word? = null,
    sub: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MockCard)
            .border(1.dp, MockLine, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MockCard2)
                .border(1.dp, MockLine, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = MockMustard, fontSize = 22.sp, fontFamily = BanglaSerif)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, color = MockInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (roman != null && bengali != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(roman, color = MockMuted, fontSize = 14.sp, fontFamily = RomanMono)
                    Text("  →  ", color = MockTerra, fontSize = 14.sp)
                    Text(highlighted(bengali), color = MockInk, fontSize = 22.sp, fontFamily = BanglaSerif)
                }
            }
            if (sub != null) {
                Text(sub, color = MockMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun OnboardingMappingSlide() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Reveal(shown, 0) { OnboardingTitle("কিছু টাইপিং উদাহরণ দেখুন — ", "কনফিউজিং শব্দ", "") }
    Reveal(shown, 120) {
        Text(
            "অক্ষরে চাপ দিন · ডানে-বাঁয়ে টেনে আরও পরিবার",
            color = MockMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
        )
    }
    val pagerState = rememberPagerState(pageCount = { ShowcaseWords.FAMILIES.size })
    HorizontalPager(state = pagerState, pageSpacing = 12.dp) { page ->
        FamilyExplorer(ShowcaseWords.FAMILIES[page])
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ShowcaseWords.FAMILIES.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(if (index == pagerState.currentPage) 18.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index == pagerState.currentPage) MockMustard else Color(0x33FFFFFF))
            )
        }
    }
}

@Composable
private fun OnboardingSetupSlide(context: Context) {
    var isEnabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var isDefault by remember { mutableStateOf(isKeyboardDefault(context)) }
    var attempted by remember { mutableStateOf(false) }
    val currentContext = rememberUpdatedState(context)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2000)
            isEnabled = isKeyboardEnabled(currentContext.value)
            isDefault = isKeyboardDefault(currentContext.value)
        }
    }
    val versionLabel = remember {
        toBengaliDigits(
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            }.getOrDefault("")
        )
    }
    OnboardingTitle("তিন ধাপে ", "চালু করুন", "")
    Spacer(modifier = Modifier.height(16.dp))
    SetupStepperCard(
        versionLabel = versionLabel,
        isEnabled = isEnabled,
        isDefault = isDefault,
        attempted = attempted,
        onEnable = {
            attempted = true
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        },
        onPick = {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        },
        onDone = { }
    )
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
