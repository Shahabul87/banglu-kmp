package com.banglu.keyboard

/**
 * S57: everyday Bengali quick phrases (বাক্য tab), organized by the
 * situations Bangladeshis actually chat in — greetings, check-ins, status,
 * prayer, family, festivals, congratulations/condolence, work/study, and
 * everyday acknowledgments. One tap commits the text.
 *
 * Every phrase carries Bangla + Banglish + English aliases so the emoji
 * search finds it in any script.
 */
object BanglaPhrases {

    data class Phrase(val text: String, val aliases: List<String>)

    data class Section(val title: String, val phrases: List<Phrase>)

    val sections: List<Section> = listOf(
        Section("সালাম ও শুভেচ্ছা", listOf(
            Phrase("আসসালামু আলাইকুম", listOf("assalamu alaikum", "salam", "সালাম", "hello")),
            Phrase("ওয়ালাইকুম আসসালাম", listOf("walaikum assalam", "salam reply", "সালাম")),
            Phrase("শুভ সকাল ☀️", listOf("shubho sokal", "good morning", "সকাল", "morning")),
            Phrase("শুভ দুপুর", listOf("shubho dupur", "good afternoon", "দুপুর")),
            Phrase("শুভ সন্ধ্যা", listOf("shubho sondha", "good evening", "সন্ধ্যা")),
            Phrase("শুভ রাত্রি 🌙", listOf("shubho ratri", "good night", "রাত", "night")),
            Phrase("কেমন আছেন?", listOf("kemon achen", "how are you", "কেমন")),
            Phrase("কেমন আছো?", listOf("kemon acho", "how are you", "কেমন")),
            Phrase("অনেক দিন পর!", listOf("onek din por", "long time", "অনেক দিন")),
            Phrase("আল্লাহ হাফেজ", listOf("allah hafez", "bye", "বিদায়")),
            Phrase("ভালো থেকো", listOf("bhalo theko", "valo theko", "take care", "ভালো")),
            Phrase("দেখা হবে ইনশাআল্লাহ", listOf("dekha hobe", "see you", "দেখা"))
        )),
        Section("খোঁজখবর", listOf(
            Phrase("কী অবস্থা?", listOf("ki obostha", "whats up", "অবস্থা")),
            Phrase("কী খবর?", listOf("ki khobor", "what news", "খবর")),
            Phrase("খেয়েছো?", listOf("kheyecho", "did you eat", "খাওয়া", "kheyeco")),
            Phrase("কোথায় আছো?", listOf("kothay acho", "where are you", "কোথায়")),
            Phrase("কী করছো?", listOf("ki korcho", "what are you doing", "করছো")),
            Phrase("বাসায় আছো?", listOf("basay acho", "are you home", "বাসা")),
            Phrase("শরীর কেমন?", listOf("shorir kemon", "how is health", "শরীর")),
            Phrase("সব ঠিকঠাক?", listOf("sob thikthak", "all okay", "ঠিকঠাক")),
            Phrase("বাসার সবাই কেমন আছে?", listOf("basar sobai", "family", "পরিবার"))
        )),
        Section("এখন অবস্থা", listOf(
            Phrase("আমি আসছি", listOf("ami aschi", "coming", "আসছি")),
            Phrase("রাস্তায় আছি", listOf("rastay achi", "on the way", "রাস্তা")),
            Phrase("একটু ব্যস্ত আছি", listOf("bysto achi", "busy", "ব্যস্ত", "besto")),
            Phrase("পরে ফোন দিচ্ছি", listOf("pore phone dicchi", "call later", "ফোন")),
            Phrase("পরে কথা বলি", listOf("pore kotha boli", "talk later", "কথা")),
            Phrase("৫ মিনিট লাগবে", listOf("5 minute lagbe", "panch minute", "মিনিট")),
            Phrase("পৌঁছে গেছি", listOf("pouche gechi", "reached", "পৌঁছে")),
            Phrase("বাসায় আছি", listOf("basay achi", "at home", "বাসা")),
            Phrase("ঘুমাতে যাচ্ছি", listOf("ghumate jacchi", "going to sleep", "ঘুম")),
            Phrase("খেতে যাচ্ছি", listOf("khete jacchi", "going to eat", "খাওয়া")),
            Phrase("নেটে সমস্যা করছে", listOf("net somossa", "network problem", "নেট")),
            Phrase("চার্জ নেই, পরে কথা হবে", listOf("charge nei", "low battery", "চার্জ"))
        )),
        Section("দোয়া ও ধর্ম", listOf(
            Phrase("ইনশাআল্লাহ", listOf("inshallah", "insha allah", "ইনশাআল্লাহ")),
            Phrase("আলহামদুলিল্লাহ", listOf("alhamdulillah", "আলহামদুলিল্লাহ")),
            Phrase("মাশাআল্লাহ", listOf("mashallah", "masha allah", "মাশাআল্লাহ")),
            Phrase("সুবহানাল্লাহ", listOf("subhanallah", "সুবহানাল্লাহ")),
            Phrase("আস্তাগফিরুল্লাহ", listOf("astagfirullah", "আস্তাগফিরুল্লাহ")),
            Phrase("জাযাকাল্লাহ খাইরান", listOf("jazakallah", "জাযাকাল্লাহ", "thanks islamic")),
            Phrase("দোয়া করবেন", listOf("dua korben", "pray for me", "দোয়া")),
            Phrase("আল্লাহ ভরসা", listOf("allah bhorosa", "ভরসা")),
            Phrase("নামাজ পড়েছো?", listOf("namaz porecho", "prayer", "নামাজ")),
            Phrase("আল্লাহ তোমার মঙ্গল করুন", listOf("allah mongol", "bless you", "মঙ্গল"))
        )),
        Section("ভালোবাসা ও পরিবার", listOf(
            Phrase("ভালোবাসি ❤️", listOf("bhalobashi", "valobashi", "love you", "ভালোবাসা")),
            Phrase("অনেক মিস করছি", listOf("miss korchi", "miss you", "মিস")),
            Phrase("তোমার কথা মনে পড়ছে", listOf("mone porche", "thinking of you", "মনে")),
            Phrase("সাবধানে যেও", listOf("sabdhane jeo", "go safely", "সাবধানে")),
            Phrase("সাবধানে থেকো", listOf("sabdhane theko", "stay safe", "সাবধানে")),
            Phrase("ঠিকমতো খেয়ো", listOf("thikmoto kheyo", "eat properly", "খাওয়া")),
            Phrase("নিজের যত্ন নিও", listOf("jotno nio", "take care", "যত্ন")),
            Phrase("তাড়াতাড়ি এসো", listOf("taratari esho", "come soon", "তাড়াতাড়ি")),
            Phrase("অপেক্ষায় আছি", listOf("opekkhay achi", "waiting", "অপেক্ষা"))
        )),
        Section("উৎসব", listOf(
            Phrase("ঈদ মোবারক 🌙", listOf("eid mubarak", "eid", "ঈদ")),
            Phrase("রমজান মোবারক", listOf("ramadan mubarak", "ramzan", "রমজান", "রোজা")),
            Phrase("জুমা মোবারক", listOf("jumma mubarak", "jumu'ah", "জুমা")),
            Phrase("শুভ নববর্ষ", listOf("shubho noboborsho", "new year", "pohela boishakh", "নববর্ষ", "বৈশাখ")),
            Phrase("শুভ বিজয়া", listOf("shubho bijoya", "bijoya", "বিজয়া")),
            Phrase("পূজার শুভেচ্ছা", listOf("puja", "durga puja", "পূজা")),
            Phrase("মেরি ক্রিসমাস 🎄", listOf("merry christmas", "borodin", "ক্রিসমাস", "বড়দিন")),
            Phrase("বিজয় দিবসের শুভেচ্ছা", listOf("bijoy dibosh", "victory day", "বিজয়")),
            Phrase("স্বাধীনতা দিবসের শুভেচ্ছা", listOf("shadhinota dibosh", "independence day", "স্বাধীনতা"))
        )),
        Section("অভিনন্দন ও সমবেদনা", listOf(
            Phrase("অভিনন্দন 🎉", listOf("ovinondon", "obhinondon", "congratulations", "congrats", "অভিনন্দন")),
            Phrase("শুভ জন্মদিন 🎂", listOf("shubho jonmodin", "happy birthday", "জন্মদিন")),
            Phrase("বিবাহের শুভেচ্ছা", listOf("bibaho", "wedding", "biye", "বিয়ে")),
            Phrase("অনেক শুভকামনা", listOf("shubhokamona", "best wishes", "শুভকামনা")),
            Phrase("ইন্না লিল্লাহি ওয়া ইন্না ইলাইহি রাজিউন", listOf("inna lillah", "condolence", "ইন্না লিল্লাহ")),
            Phrase("আল্লাহ তাঁকে জান্নাত দান করুন", listOf("jannat", "জান্নাত", "condolence")),
            Phrase("খুবই দুঃখজনক", listOf("dukkhojonok", "so sad", "দুঃখজনক")),
            Phrase("দ্রুত সুস্থ হয়ে উঠো", listOf("susto hoye utho", "get well soon", "সুস্থ"))
        )),
        Section("কাজ ও পড়াশোনা", listOf(
            Phrase("মিটিং-এ আছি", listOf("meeting e achi", "in a meeting", "মিটিং")),
            Phrase("ক্লাসে আছি", listOf("classe achi", "in class", "ক্লাস")),
            Phrase("অফিসে আছি", listOf("office achi", "at office", "অফিস")),
            Phrase("কাজ শেষ করে ফোন দিচ্ছি", listOf("kaj shesh", "after work", "কাজ")),
            Phrase("পরীক্ষা চলছে", listOf("porikkha cholche", "exam", "পরীক্ষা")),
            Phrase("কাল জমা দিতে হবে", listOf("kal joma", "deadline", "জমা")),
            Phrase("ফাইলটা পাঠাও", listOf("file pathao", "send the file", "ফাইল")),
            Phrase("পেয়েছি, ধন্যবাদ", listOf("peyechi", "received thanks", "পেয়েছি"))
        )),
        Section("সাধারণ", listOf(
            Phrase("ধন্যবাদ 🙏", listOf("dhonnobad", "thanks", "thank you", "ধন্যবাদ")),
            Phrase("ঠিক আছে ✅", listOf("thik ache", "ok", "okay", "ঠিক")),
            Phrase("আচ্ছা", listOf("accha", "acha", "okay", "আচ্ছা")),
            Phrase("দুঃখিত", listOf("dukkhito", "sorry", "দুঃখিত")),
            Phrase("সমস্যা নেই", listOf("somossa nei", "no problem", "সমস্যা")),
            Phrase("অবশ্যই", listOf("oboshshoi", "of course", "sure", "অবশ্যই")),
            Phrase("খুব সুন্দর!", listOf("khub sundor", "very nice", "beautiful", "সুন্দর")),
            Phrase("হাহাহা 😂", listOf("hahaha", "haha", "hasi", "হাসি", "laugh")),
            Phrase("একদম!", listOf("ekdom", "exactly", "একদম")),
            Phrase("জানি না", listOf("jani na", "dont know", "জানি")),
            Phrase("বুঝেছি", listOf("bujhechi", "bujechi", "understood", "বুঝেছি")),
            Phrase("মনে নেই", listOf("mone nei", "forgot", "মনে"))
        ))
    )

    val allPhrases: List<Phrase> by lazy { sections.flatMap { it.phrases } }

    private val phraseTexts: Set<String> by lazy { allPhrases.mapTo(HashSet()) { it.text } }

    fun isPhrase(value: String): Boolean = value in phraseTexts
}
