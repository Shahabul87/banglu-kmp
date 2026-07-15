package com.banglu.desktop.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * শিখুন — the desktop tutorial. Same curriculum as the Android
 * TutorialActivity (typing steps + the full phonetic mapping), adapted for
 * desktop: suggestion-bar wording becomes the popup, taps become clicks, and
 * mobile-only steps (voice, emoji, toolbar, EN/BN key) are dropped in favor
 * of a keyboard-shortcuts card. Pure static content — no engine calls.
 */
private data class TutorialSection(
    val title: String,
    val subtitle: String,
    val examples: List<Pair<String, String>>,
    val tips: List<String>,
)

private val quickStart = listOf(
    TutorialSection(
        title = "১. ছোট হাতের ইংরেজিতে লিখুন",
        subtitle = "Shift ছাড়া phonetic টাইপিং — শব্দটি কার্সরেই বাংলা হয়ে গড়ে ওঠে, Space চাপলে বসে যায়।",
        examples = listOf(
            "ami" to "আমি",
            "tumi kemon acho" to "তুমি কেমন আছো",
            "ami bangla likhte chai" to "আমি বাংলা লিখতে চাই",
        ),
        tips = listOf(
            "শব্দ শেষ হলে Space চাপুন — যা দেখছেন তা-ই বসবে",
            "ভুল হলে Backspace — ইংরেজি অক্ষর ধরে মুছে যায়: kali ⌫ → kal → কাল",
            "দুইবার Space চাপলে দাঁড়ি (।) + স্পেস",
        ),
    ),
    TutorialSection(
        title = "২. পপআপ থেকে বেছে নিন",
        subtitle = "লেখার সময় শব্দের নিচে বিকল্পের তালিকা ভাসে — প্রথমটি না চাইলে অন্যটি নিন।",
        examples = listOf(
            "১-৬ চাপুন" to "সেই নম্বরের শব্দটি বসে",
            "↑ ↓ + Enter" to "তালিকায় ঘুরে বেছে নিন",
            "Esc" to "তালিকা বন্ধ — ইংরেজি শব্দ ইংরেজিই থাকবে",
        ),
        tips = listOf(
            "তালিকার শেষে ইংরেজি বানানটিও থাকে — English শব্দ এক চাপে",
            "যে বানান বেছে নেন Banglu Editor সেটা মনে রাখে, পরের বার আগে আসে",
        ),
    ),
    TutorialSection(
        title = "৩. লেখা শব্দ ঠিক করুন",
        subtitle = "পাতায় বসে যাওয়া যেকোনো বাংলা শব্দে ক্লিক করুন — বিকল্পের তালিকা আবার খুলবে।",
        examples = listOf(
            "taka" to "টাকা | তাকা",
            "dan" to "দান | ডান",
            "pore" to "পরে | পড়ে",
        ),
        tips = listOf(
            "বদলে নেওয়া বানানও শেখা হয়",
            "⌘Z চাপলে আগের অবস্থা ফিরে আসে — কমিট করা শব্দও",
        ),
    ),
    TutorialSection(
        title = "৪. চ্যাটের মতো লিখুন — শর্টকাটও বোঝে",
        subtitle = "পুরো বানান না লিখলেও চলে। চ্যাটে যেভাবে লেখেন, সেভাবেই বুঝে নেয়।",
        examples = listOf(
            "kmon" to "কেমন",
            "hm / hmm" to "হুম",
            "tmi / tmra" to "তুমি / তোমরা",
            "amr / tmr" to "আমার / তোমার",
            "issa / icca" to "ইচ্ছা",
            "korsi / korci" to "করছি",
            "hosse / somossa" to "হচ্ছে / সমস্যা",
            "golp / shobd" to "গল্প / শব্দ",
        ),
        tips = listOf(
            "চ্ছ-শব্দ ss বা cc দুইভাবেই লেখা যায়",
            "শেষের o না দিলেও চলে: golp → গল্প",
            "bujteparcina → বুঝতে পারছিনা — জোড়া লাগানো শব্দও ভাঙে",
        ),
    ),
    TutorialSection(
        title = "৫. English রেখে Bangla লিখুন",
        subtitle = "Assignment, meeting, office word জোর করে বাংলা হবে না।",
        examples = listOf(
            "ami assignment submit korbo" to "আমি assignment submit করবো",
            "meeting ache" to "meeting আছে",
        ),
        tips = listOf(
            "কোনো শব্দ ভুল করে বাংলা হলে পপআপ থেকে ইংরেজি বানানটি নিন",
        ),
    ),
)

private val phoneticMapping = listOf(
    TutorialSection(
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
            "ou / ow" to "ঔ / ৌ",
        ),
        tips = listOf("ami → আমি", "ki → কি, kii/kee → কী", "nouka → নৌকা"),
    ),
    TutorialSection(
        title = "সাধারণ ব্যঞ্জনবর্ণ",
        subtitle = "এক অক্ষরের basic mapping। একই উচ্চারণের অক্ষরে পপআপ সাহায্য করবে।",
        examples = listOf(
            "k" to "ক", "g" to "গ", "c" to "ছ", "j" to "জ / য", "z" to "য",
            "t" to "ত / ট", "d" to "দ / ড", "n" to "ন / ণ", "p" to "প", "b" to "ব",
            "m" to "ম", "r" to "র / ড়", "l" to "ল", "s" to "স / শ / ষ", "h" to "হ",
        ),
        tips = listOf("taka → টাকা, তাকা পপআপে", "dan → দান বা ডান", "c এখন ছ, ch এখন চ"),
    ),
    TutorialSection(
        title = "Aspirated ধ্বনি",
        subtitle = "h যোগ করলে aspirated ধ্বনি হয়।",
        examples = listOf(
            "kh" to "খ", "gh" to "ঘ", "ch" to "চ", "c" to "ছ",
            "jh" to "ঝ", "th" to "থ / ঠ", "dh" to "ধ / ঢ",
            "ph / f" to "ফ", "bh / v" to "ভ", "sh" to "শ / ষ / স", "rh" to "ড়",
        ),
        tips = listOf(
            "নতুন নিয়ম: c = ছ, ch = চ",
            "পুরোনো chh লেখাও অভিধান বোঝে",
            "ঠ/ঢ দরকার হলে পপআপ থেকে নিন",
        ),
    ),
    TutorialSection(
        title = "য, য়, ফলা",
        subtitle = "y, z, rr, ri দিয়ে য/য়/র-ফলা/ঋ-কার লিখুন।",
        examples = listOf(
            "y" to "য / য়",
            "z" to "য",
            "consonant + y" to "্য",
            "rr" to "্র",
            "ri (consonant-এর পরে)" to "ৃ",
            "rri" to "ঋ / ৃ",
        ),
        tips = listOf("kri → কৃ", "priyo → প্রিয়", "gyan/jnan → জ্ঞান"),
    ),
    TutorialSection(
        title = "Nasal ধ্বনি",
        subtitle = "ng এবং nasal conjunct প্রসঙ্গ দেখে বসে।",
        examples = listOf(
            "ng" to "ং / ঙ",
            "ngk" to "ঙ্ক",
            "ngg / ngo" to "ঙ্গ",
            "ngkh" to "ঙ্খ",
            "nggh" to "ঙ্ঘ",
            "nc" to "ঞ্চ",
            "nj" to "ঞ্জ",
        ),
        tips = listOf("bangla → বাংলা", "ongko → অঙ্ক", "songbad → সংবাদ"),
    ),
    TutorialSection(
        title = "বহুল ব্যবহৃত যুক্তবর্ণ",
        subtitle = "সবচেয়ে বেশি লাগে যে যুক্তবর্ণগুলো — lowercase pattern-এই হয়।",
        examples = listOf(
            "kkh / ksh" to "ক্ষ",
            "gy / jn" to "জ্ঞ",
            "kt" to "ক্ত",
            "nt" to "ন্ত",
            "nd" to "ন্দ",
            "pt" to "প্ত",
            "mb" to "ম্ব",
            "mp" to "ম্প",
            "str" to "স্ত্র",
        ),
        tips = listOf("shokti → শক্তি", "anondo → আনন্দ", "stri → স্ত্রী"),
    ),
    TutorialSection(
        title = "আরও যুক্তবর্ণ",
        subtitle = "প্রয়োজনীয় complex conjunct patterns।",
        examples = listOf(
            "ddh" to "দ্ধ",
            "dbh" to "দ্ভ",
            "mbh" to "ম্ভ",
            "nth" to "ন্থ",
            "tth" to "ত্থ",
            "ntr" to "ন্ত্র",
            "ndr" to "ন্দ্র",
            "ndh" to "ন্ধ",
        ),
        tips = listOf("buddhi → বুদ্ধি", "ondho → অন্ধ", "montro → মন্ত্র"),
    ),
    TutorialSection(
        title = "ফলা ও বিদেশি cluster",
        subtitle = "Bangla ও English মিশ্র শব্দের জন্য extra cluster support।",
        examples = listOf(
            "khy" to "খ্য", "dhy" to "ধ্য", "dhr" to "ধ্র", "bhr" to "ভ্র",
            "ghr" to "ঘ্র", "ttr" to "ট্র", "kl" to "ক্ল", "gl" to "গ্ল",
            "pl" to "প্ল", "bl" to "ব্ল", "fl" to "ফ্ল",
        ),
        tips = listOf("shongkha → সংখ্যা", "dhyan → ধ্যান", "train/tren → ট্রেন"),
    ),
    TutorialSection(
        title = "Double consonant",
        subtitle = "একই consonant দুইবার দিলে যুক্ত double form হয়।",
        examples = listOf(
            "kk" to "ক্ক", "cc" to "চ্চ", "jj" to "জ্জ", "tt" to "ত্ত",
            "dd" to "দ্দ", "nn" to "ন্ন", "mm" to "ম্ম", "ll" to "ল্ল",
            "pp" to "প্প", "bb" to "ব্ব",
        ),
        tips = listOf("uccho → উচ্চ", "onna → অন্ন"),
    ),
    TutorialSection(
        title = "Punctuation ও সংখ্যা",
        subtitle = "চেনা punctuation সরাসরি বাংলায় বসে।",
        examples = listOf(
            "." to "।",
            "Space Space" to "। (দাঁড়ি)",
            ":" to "ঃ",
            "^" to "ঁ",
            "$" to "৳",
            "0-9" to "০-৯",
        ),
        tips = listOf("দুইবার Space চাপলে দাঁড়ি + স্পেস"),
    ),
)

private val desktopKeys = TutorialSection(
    title = "কীবোর্ড শর্টকাট",
    subtitle = "Banglu Editor-এর সব কাজ কীবোর্ড থেকেই।",
    examples = listOf(
        "⌘S / ⇧⌘S" to "সেভ / নতুন নামে সেভ",
        "⌘O / ⌘N" to "খুলুন / নতুন লেখা",
        "⌘Z / ⇧⌘Z" to "আগের অবস্থা / আবার করুন",
        "⌘P" to "প্রিন্ট — Save as PDF এখানেই",
        "⇧⌘C" to "পুরো লেখা কপি",
        "⌘⇧B" to "যেকোনো অ্যাপে মিনি কনভার্টার",
    ),
    tips = listOf(
        "লেখা প্রতি ২ সেকেন্ডে নিজে নিজে সংরক্ষিত হয় — বন্ধ করলেও হারায় না",
    ),
)

/** Full-page scrollable tutorial; ✕ returns to the editor untouched. */
@Composable
fun TutorialView(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("শিখুন — যেভাবে বাংলা লিখবেন", color = Sky, fontSize = 18.sp,
                    fontFamily = BengaliFontFamily, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("✕ লেখায় ফিরুন", color = SkySoft, fontSize = 13.sp,
                    fontFamily = BengaliFontFamily,
                    modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
            }

            SectionHeading("শুরু করুন")
            quickStart.forEach { SectionCard(it) }

            SectionHeading("বর্ণ ম্যাপিং — পুরো তালিকা")
            phoneticMapping.forEach { SectionCard(it) }

            SectionHeading("ডেস্কটপ")
            SectionCard(desktopKeys)

            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, color = Green, fontSize = 14.sp, fontFamily = BengaliFontFamily,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SectionCard(section: TutorialSection) {
    Surface(Modifier.fillMaxWidth(), color = PageCard, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, color = SkySoft, fontSize = 15.sp,
                fontFamily = BengaliFontFamily, fontWeight = FontWeight.Bold)
            Text(section.subtitle, color = Muted, fontSize = 12.sp,
                fontFamily = BengaliFontFamily, lineHeight = 18.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                section.examples.forEach { (latin, bangla) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(latin, color = Sky, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(220.dp))
                        Text("→", color = Muted, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp))
                        Text(bangla, color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 15.sp, fontFamily = BengaliFontFamily)
                    }
                }
            }
            section.tips.forEach { tip ->
                Row {
                    Text("·", color = Green, fontSize = 12.sp,
                        modifier = Modifier.padding(end = 6.dp))
                    Text(tip, color = Muted, fontSize = 12.sp,
                        fontFamily = BengaliFontFamily, lineHeight = 18.sp)
                }
            }
        }
    }
}
