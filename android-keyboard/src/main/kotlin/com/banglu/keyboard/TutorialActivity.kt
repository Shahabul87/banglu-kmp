package com.banglu.keyboard

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private var TutorialPrimary = Color(0xFF0A84FF)
private var TutorialSuccess = Color(0xFF34C759)
private var TutorialCoral = Color(0xFFFF7A59)
private var TutorialGreen = Color(0xFF34C759)
private var TutorialBlack = Color(0xFFF6F7FB)
private var TutorialDark = Color(0xFFEFF3F8)
private var TutorialCard = Color(0xFFFFFFFF)
private var TutorialBorder = Color(0xFFE4E8F0)
private var TutorialMuted = Color(0xFF6B7280)
private var TutorialText = Color(0xFF111827)

private fun applyTutorialPalette(dark: Boolean) {
    if (dark) {
        TutorialPrimary = Color(0xFF64D2FF)
        TutorialSuccess = Color(0xFF30D158)
        TutorialCoral = Color(0xFFFF7A59)
        TutorialGreen = Color(0xFF30D158)
        TutorialBlack = Color(0xFF080D16)
        TutorialDark = Color(0xFF111827)
        TutorialCard = Color(0xFF182235)
        TutorialBorder = Color(0xFF27364F)
        TutorialMuted = Color(0xFFA8B3C7)
        TutorialText = Color(0xFFF8FAFC)
    } else {
        TutorialPrimary = Color(0xFF0A84FF)
        TutorialSuccess = Color(0xFF34C759)
        TutorialCoral = Color(0xFFFF7A59)
        TutorialGreen = Color(0xFF34C759)
        TutorialBlack = Color(0xFFF6F7FB)
        TutorialDark = Color(0xFFEFF3F8)
        TutorialCard = Color(0xFFFFFFFF)
        TutorialBorder = Color(0xFFE4E8F0)
        TutorialMuted = Color(0xFF6B7280)
        TutorialText = Color(0xFF111827)
    }
}

class TutorialActivity : ComponentActivity() {
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
        setBangluContent { BangluTutorialScreen(onBack = { finish() }) }
    }
}

private data class TutorialStep(
    val title: String,
    val subtitle: String,
    val examples: List<Pair<String, String>>,
    val tips: List<String>
)

private val typingSteps = listOf(
    TutorialStep(
        title = "১. ছোট হাতের ইংরেজিতে লিখুন",
        subtitle = "Shift ছাড়া Bangla phonetic টাইপিং শুরু করুন।",
        examples = listOf("ami" to "আমি", "tumi kemon acho" to "তুমি কেমন আছো", "ami bangla likhte chai" to "আমি বাংলা লিখতে চাই"),
        tips = listOf("শব্দ শেষ হলে Space চাপুন", "লাইভ প্রিভিউ দেখে নিশ্চিত হন", "ভুল হলে Backspace চাপুন")
    ),
    TutorialStep(
        title = "২. কঠিন একই-শব্দের অক্ষর",
        subtitle = "ত/ট, দ/ড, কি/কী, চ/ছ Banglu নিজে র‍্যাঙ্ক করে।",
        examples = listOf("taka" to "টাকা", "dorja" to "দরজা", "ki" to "কি / কী", "c" to "ছ", "ch" to "চ"),
        tips = listOf("সঠিক শব্দ editor-এ যাবে", "বিকল্পগুলো suggestion bar-এ থাকবে", "একবার বেছে নিলে Banglu মনে রাখবে")
    ),
    TutorialStep(
        title = "৩. সাজেশন থেকে বেছে নিন",
        subtitle = "প্রথম suggestion না চাইলে পাশের শব্দে ট্যাপ করুন।",
        examples = listOf("taka" to "টাকা | তাকা", "dan" to "দান | ডান", "pore" to "পরে | পড়ে"),
        tips = listOf("ট্যাপ করা বানান পরের বার আগে আসবে", "ভুল অটোকারেক্ট হলে ↶ চিপ ট্যাপ করুন — আগের লেখা ফিরে আসবে", "Learning বন্ধ করলে শেখা বন্ধ হবে")
    ),
    TutorialStep(
        title = "৪. চ্যাটের মতো লিখুন — শর্টকাটও বোঝে",
        subtitle = "পুরো বানান না লিখলেও চলে। চ্যাটে যেভাবে লেখেন, Banglu সেভাবেই বোঝে।",
        examples = listOf(
            "kmon" to "কেমন",
            "hm / hmm" to "হুম",
            "ok" to "ওকে",
            "tmi / tmra" to "তুমি / তোমরা",
            "amr / tmr" to "আমার / তোমার",
            "issa / icca" to "ইচ্ছা",
            "korsi / korci" to "করছি",
            "hosse / somossa" to "হচ্ছে / সমস্যা",
            "golp / shobd" to "গল্প / শব্দ"
        ),
        tips = listOf("চ্ছ-শব্দ ss বা cc দুইভাবেই লেখা যায়", "শেষের o না দিলেও চলে: golp → গল্প", "bujteparcina → বুঝতে পারছিনা — জোড়া লাগানো শব্দও ভাঙে")
    ),
    TutorialStep(
        title = "৫. English রেখে Bangla লিখুন",
        subtitle = "Assignment, WhatsApp, office word জোর করে বাংলা বানাবে না।",
        examples = listOf("ami assignment submit korbo" to "আমি assignment submit করবো", "meeting ache" to "meeting আছে"),
        tips = listOf("English mode দরকার হলে EN চাপুন", "আবার BN চাপলে Banglu mode", "Space দিয়ে দ্রুত মিশ্র লেখা লিখুন")
    )
)

private val featureSteps = listOf(
    TutorialStep(
        title = "৬. Voice typing",
        subtitle = "Mic চাপুন, বাংলায় বলুন, Banglu editor-এ লিখে দেবে।",
        examples = listOf("আমি কাল ঢাকা যাব" to "আমি কাল ঢাকা যাব।", "pause" to "কমা বা দাঁড়ি"),
        tips = listOf("ছোট বিরতিতে কমা", "বড় বিরতিতে দাঁড়ি", "থামান বা বাতিল বোতাম ব্যবহার করুন")
    ),
    TutorialStep(
        title = "৭. Voice লেখা মুছুন",
        subtitle = "Voice শেষ হলে suggestion bar-এ ভয়েস মুছুন দেখা যাবে।",
        examples = listOf("ভয়েস মুছুন" to "শেষ voice sentence delete"),
        tips = listOf("লম্বা sentence হলেও শেষ voice অংশ মুছবে", "Backspace long press করলে word delete", "Voice active থাকলে suggestion লুকানো থাকে")
    ),
    TutorialStep(
        title = "৮. Emoji ও expression",
        subtitle = "Toolbar খুলে emoji panel ব্যবহার করুন।",
        examples = listOf("😊" to "Emoji panel", "⌄" to "Keyboard dismiss"),
        tips = listOf("তিন ডট চাপলে toolbar খুলবে", "Emoji থেকে back দিলে keyboard-এ ফিরবে", "বাংলা chat দ্রুত হবে")
    ),
    TutorialStep(
        title = "৯. Toolbar, cursor ও delete",
        subtitle = "Settings, mic, emoji, cursor movement সব keyboard থেকেই।",
        examples = listOf("Space swipe" to "cursor move", "Backspace hold" to "word delete"),
        tips = listOf("Toolbar-এর gear থেকে settings", "Space bar swipe করে cursor সরান", "Long press backspace দ্রুত delete")
    ),
    TutorialStep(
        title = "১০. Settings ও privacy",
        subtitle = "নিজের typing style, height, theme, learning control করুন।",
        examples = listOf("ব্যবহার শেখা" to "on/off", "ব্যক্তিগত অভিধান" to "sync-ready", "লাইট মোড" to "low resource"),
        tips = listOf("Learning control আপনার হাতে", "কম RAM ফোনে Lite mode দিন", "Theme, number row, key preview বদলাতে পারবেন")
    )
)

private val phoneticMappingSteps = listOf(
    TutorialStep(
        title = "স্বরবর্ণ ও কার",
        subtitle = "শব্দের শুরুতে vowel হলে পূর্ণ স্বরবর্ণ, consonant-এর পরে হলে কার বসে।",
        examples = listOf(
            "a / aa" to "আ / া",
            "i" to "ই / ি",
            "ii / ee" to "ঈ / ী",
            "u" to "উ / ু",
            "uu / oo" to "ঊ / ূ",
            "e" to "এ / ে",
            "oi" to "ঐ / ৈ",
            "o" to "ও / ো / অ",
            "ou / ow" to "ঔ / ৌ"
        ),
        tips = listOf("ami → আমি", "ki → কি, kii/kee → কী", "nouka → নৌকা")
    ),
    TutorialStep(
        title = "সাধারণ ব্যঞ্জনবর্ণ",
        subtitle = "এক অক্ষরের basic mapping। একই উচ্চারণের অক্ষরে suggestion সাহায্য করবে।",
        examples = listOf(
            "k" to "ক", "g" to "গ", "c" to "ছ", "j" to "জ / য", "z" to "য",
            "t" to "ত / ট", "d" to "দ / ড", "n" to "ন / ণ", "p" to "প", "b" to "ব",
            "m" to "ম", "r" to "র / ড়", "l" to "ল", "s" to "স / শ / ষ", "h" to "হ"
        ),
        tips = listOf("taka → টাকা বা তাকা suggestion", "dan → দান বা ডান", "c এখন ছ, ch এখন চ")
    ),
    TutorialStep(
        title = "Aspirated consonant",
        subtitle = "h যোগ করলে aspirated ধ্বনি হয়।",
        examples = listOf(
            "kh" to "খ", "gh" to "ঘ", "ch" to "চ", "c" to "ছ",
            "jh" to "ঝ", "th" to "থ / ঠ", "dh" to "ধ / ঢ",
            "ph / f" to "ফ", "bh / v" to "ভ", "sh" to "শ / ষ / স", "rh" to "ড়"
        ),
        tips = listOf("নতুন Banglu rule: c = ছ, ch = চ", "পুরোনো chh input dictionary বুঝতে পারে, কিন্তু user tutorial-এ c ব্যবহার করুন", "ঠ/ঢ দরকার হলে suggestion বা long press ব্যবহার করুন")
    ),
    TutorialStep(
        title = "য, য়, ফলা",
        subtitle = "y, z, rr, ri দিয়ে য/য়/র-ফলা/ঋ-কার লিখুন।",
        examples = listOf(
            "y" to "য / য়",
            "z" to "য",
            "consonant + y" to "্য",
            "rr" to "্র",
            "ri after consonant" to "ৃ",
            "rri" to "ঋ / ৃ"
        ),
        tips = listOf("kri → কৃ", "priyo → প্রিয়", "gyan/jnan → জ্ঞান")
    ),
    TutorialStep(
        title = "Nasal sound",
        subtitle = "ng এবং nasal conjunct engine context দেখে বসায়।",
        examples = listOf(
            "ng" to "ং / ঙ",
            "ngk" to "ঙ্ক",
            "ngg / ngo" to "ঙ্গ",
            "ngkh" to "ঙ্খ",
            "nggh" to "ঙ্ঘ",
            "ngm" to "ঙ্ম",
            "nc" to "ঞ্চ",
            "nj" to "ঞ্জ"
        ),
        tips = listOf("bangla → বাংলা", "ongko → অঙ্ক", "songbad → সংবাদ")
    ),
    TutorialStep(
        title = "Common conjunct",
        subtitle = "সবচেয়ে বেশি ব্যবহৃত যুক্তবর্ণগুলো lowercase pattern দিয়ে লিখুন।",
        examples = listOf(
            "kkh / ksh" to "ক্ষ",
            "gy / jn" to "জ্ঞ",
            "kt" to "ক্ত",
            "nt" to "ন্ত",
            "nd" to "ন্দ",
            "pt" to "প্ত",
            "mb" to "ম্ব",
            "mp" to "ম্প",
            "str" to "স্ত্র"
        ),
        tips = listOf("shokti → শক্তি", "anondo → আনন্দ", "stri → স্ত্রী")
    ),
    TutorialStep(
        title = "More conjunct",
        subtitle = "প্রয়োজনীয় complex conjunct patterns।",
        examples = listOf(
            "ddh" to "দ্ধ",
            "dbh" to "দ্ভ",
            "dgh" to "দ্ঘ",
            "mbh" to "ম্ভ",
            "nth" to "ন্থ",
            "tth" to "ত্থ",
            "ntr" to "ন্ত্র",
            "ndr" to "ন্দ্র",
            "ndh" to "ন্ধ"
        ),
        tips = listOf("buddhi → বুদ্ধি", "ondho → অন্ধ", "montro → মন্ত্র")
    ),
    TutorialStep(
        title = "ফলা ও বিদেশি cluster",
        subtitle = "Bangla ও English মিশ্র শব্দের জন্য extra cluster support।",
        examples = listOf(
            "khy" to "খ্য", "dhy" to "ধ্য", "dhr" to "ধ্র", "bhr" to "ভ্র",
            "ghr" to "ঘ্র", "ttr" to "ট্র", "kl" to "ক্ল", "gl" to "গ্ল",
            "pl" to "প্ল", "bl" to "ব্ল", "fl" to "ফ্ল"
        ),
        tips = listOf("shongkha → সংখ্যা", "dhyan → ধ্যান", "train/tren → ট্রেন")
    ),
    TutorialStep(
        title = "Double consonant",
        subtitle = "একই consonant দুইবার দিলে যুক্ত double form হয়।",
        examples = listOf(
            "kk" to "ক্ক", "cc" to "চ্চ", "jj" to "জ্জ", "tt" to "ত্ত",
            "dd" to "দ্দ", "nn" to "ন্ন", "mm" to "ম্ম", "ll" to "ল্ল",
            "pp" to "প্প", "bb" to "ব্ব"
        ),
        tips = listOf("uccho → উচ্চ", "onna → অন্ন", "kotha spelling dictionary দিয়ে rank হবে")
    ),
    TutorialStep(
        title = "Punctuation ও number",
        subtitle = "Keyboard punctuation সরাসরি Bengali punctuation-এ map হয়।",
        examples = listOf(
            "." to "।",
            "Space Space" to "। (দাঁড়ি)",
            "," to "কমা — স্পেসবারের পাশেই",
            ":" to "ঃ",
            "^" to "ঁ",
            "$" to "৳",
            "0-9" to "০-৯"
        ),
        tips = listOf("শব্দের পর কমা দিলে আগের স্পেস নিজেই সরে যায়: কথা, ", "দুইবার Space চাপলে দাঁড়ি + স্পেস", "English mode-এ punctuation সরাসরি থাকবে")
    ),
    TutorialStep(
        title = "Dictionary ranking",
        subtitle = "একই phonetic থেকে একাধিক বাংলা হলে dictionary/context সবচেয়ে কাছেরটি editor-এ দেয়।",
        examples = listOf(
            "taka" to "টাকা আগে, তাকা suggestion",
            "dorja" to "দরজা",
            "pore" to "পরে / পড়ে",
            "ki" to "কি / কী",
            "koy" to "কয় / কই"
        ),
        tips = listOf("আপনি যে suggestion ট্যাপ করেন Banglu সেটা শেখে", "ভুল হলে suggestion থেকে সঠিক বানান বেছে নিন")
    )
)

@Composable
private fun BangluTutorialScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { remoteBangluPrefs(context) }
    val themeMode = prefs.getString("theme", "dark") ?: "dark"
    val systemDark = isSystemInDarkTheme()
    val darkTheme = themeMode == "dark" || themeMode == "amoled" || (themeMode == "auto" && systemDark)
    applyTutorialPalette(darkTheme)
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
                    if (darkTheme) listOf(Color(0xFF080D16), TutorialBlack, TutorialDark)
                    else listOf(Color(0xFFF8FAFF), TutorialBlack, TutorialDark)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { TutorialHeader(onBack) }
            item { SectionTitle("প্রথমে টাইপিং শিখুন", "Typing tutorial") }
            typingSteps.forEach { step ->
                item { TutorialStepCard(step) }
            }
            item { SectionTitle("পূর্ণ phonetic mapping", "How to write every sound") }
            phoneticMappingSteps.forEach { step ->
                item { TutorialStepCard(step) }
            }
            item { SectionTitle("এরপর সব feature", "Feature guide") }
            featureSteps.forEach { step ->
                item { TutorialStepCard(step) }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TutorialHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(TutorialCard)
                .border(1.dp, TutorialBorder, CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = TutorialPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Image(
            painter = painterResource(id = R.drawable.banglu_logo),
            contentDescription = "Banglu logo",
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("Banglu Guide", color = TutorialPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("টাইপিং আগে, তারপর সব feature", color = TutorialMuted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SectionTitle(bengali: String, english: String) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(bengali, color = TutorialSuccess, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(english, color = TutorialMuted, fontSize = 15.sp)
    }
}

@Composable
private fun TutorialStepCard(step: TutorialStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TutorialCard),
        border = BorderStroke(1.dp, TutorialBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(step.title, color = TutorialPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(step.subtitle, color = TutorialText, fontSize = 16.sp, lineHeight = 23.sp)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                step.examples.forEach { (input, output) ->
                    ExampleRow(input, output)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                step.tips.forEach { tip ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•", color = TutorialGreen, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(tip, color = TutorialMuted, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleRow(input: String, output: String) {
    val darkTheme = TutorialBlack == Color(0xFF080D16)
    val rowBackground = if (darkTheme) Color(0xFF101A2A) else Color(0xFFF3F7FF)
    val rowBorder = if (darkTheme) TutorialPrimary.copy(alpha = 0.26f) else TutorialPrimary.copy(alpha = 0.14f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBackground)
            .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mappings must never truncate — a tutorial row that reads
        // "tumi kemon ac… → তুমি কেমন আ…" teaches nothing. Wrap instead.
        Text(input, color = TutorialText, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text("→", color = TutorialCoral, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Text(output, color = TutorialSuccess, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}
