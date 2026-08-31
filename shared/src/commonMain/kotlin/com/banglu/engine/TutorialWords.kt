package com.banglu.engine

/**
 * S157: the tutorial's letter-card curriculum — every letter of the
 * alphabet with hard, conjunct-heavy example words, each carrying the
 * verified roman, its syllable split (concatenation == roman, asserted by
 * the pin wall), optional alternate spellings, the conjunct highlight, and
 * for twin words the engine's REAL primary (pore -> পরে with পড়ে on the
 * strip). Mined from the full dictionary (3,346 engine-verified words,
 * S157 bulk probe) and hand-curated. S157TutorialWordsJvmTest pins EVERY
 * (variant, word) pair on the real dictionary: the tutorial can never
 * advertise a conversion the engine gets wrong.
 */
object TutorialWords {
    data class Word(
        val bengali: String,
        val roman: String,
        val split: List<String>,
        val alts: List<String> = emptyList(),
        val highlight: String = "",
        val twinPrimary: String = "",
        val twinNote: String = ""
    )
    data class CapWords(val cap: String, val words: List<Word>)
    data class Family(val title: String, val tagline: String, val caps: List<CapWords>)

    val FAMILIES: List<Family> = listOf(
        Family("স্বরবর্ণ", "স্বরবর্ণ দিয়ে শুরু — ছোটহাতের ইংরেজিতেই কঠিন সব শব্দ", listOf(
            CapWords("অ", listOf(
                Word("অত্যাধুনিক", "ottadhunik", listOf("ot", "ta", "dhu", "nik"), listOf("otyadhunik"), "ত্যা"),
                Word("অভিজ্ঞতা", "obhiggota", listOf("o", "bhig", "go", "ta"), listOf("oviggota"), "জ্ঞ"),
                Word("অবশ্যই", "oboshshoi", listOf("o", "bosh", "shoi"), listOf("obosshoi"), "শ্যই"),
                Word("অন্য", "onno", listOf("on", "no"), listOf("onyo"), "ন্য"),
                Word("অন্যান্য", "onnanno", listOf("on", "nan", "no"), emptyList(), "ন্যান্য"),
                Word("অর্থনৈতিক", "orthonoitik", listOf("or", "tho", "noi", "tik"), emptyList(), "র্থ"),
                Word("অঞ্চল", "onchol", listOf("on", "chol"), emptyList(), "ঞ্চ"),
                Word("অপেক্ষা", "opekkha", listOf("o", "pek", "kha"), emptyList(), "ক্ষ"),
                Word("অক্ষর", "okkhor", listOf("ok", "khor"), emptyList(), "ক্ষ"),
                Word("অস্ত্র", "ostro", listOf("os", "tro"), emptyList(), "স্ত্র")
            )),
            CapWords("আ", listOf(
                Word("আশ্চর্য", "ashchorjo", listOf("ash", "chor", "jo"), listOf("ascorjo"), "শ্চর্য"),
                Word("আত্মবিশ্বাস", "atmobishshash", listOf("at", "mo", "bish", "shash"), listOf("attobishshash"), "ত্ম"),
                Word("আন্তর্জাতিক", "antorjatik", listOf("an", "tor", "ja", "tik"), emptyList(), "ন্তর্জ"),
                Word("আচ্ছা", "accha", listOf("ac", "cha"), emptyList(), "চ্ছ"),
                Word("আবিষ্কার", "abishkar", listOf("a", "bish", "kar"), emptyList(), "ষ্ক"),
                Word("আক্রমণ", "akromon", listOf("a", "kro", "mon"), emptyList(), "ক্র"),
                Word("আন্দোলন", "andolon", listOf("an", "do", "lon"), emptyList(), "ন্দ"),
                Word("আকৃষ্ট", "akrishto", listOf("a", "krish", "to"), emptyList(), "কৃষ্ট"),
                Word("আনন্দ", "anondo", listOf("a", "non", "do"), emptyList(), "ন্দ")
            )),
            CapWords("ই", listOf(
                Word("ইচ্ছা", "iccha", listOf("ic", "cha"), listOf("issa"), "চ্ছ"),
                Word("ইত্যাদি", "ittadi", listOf("it", "ta", "di"), listOf("ityadi"), "ত্যা"),
                Word("ইতিমধ্যে", "itimoddhe", listOf("i", "ti", "mod", "dhe"), listOf("itimodhye"), "ধ্যে"),
                Word("ইঞ্জিন", "injin", listOf("in", "jin"), emptyList(), "ঞ্জ"),
                Word("ইজ্জত", "ijjot", listOf("ij", "jot"), emptyList(), "জ্জ"),
                Word("ইশারা", "ishara", listOf("i", "sha", "ra"), emptyList(), "")
            )),
            CapWords("ঈ", listOf(
                Word("ঈদ", "eid", listOf("eid"), emptyList(), "ঈ"),
                Word("ঈশ্বর", "ishshor", listOf("ish", "shor"), listOf("ishwor"), "শ্ব"),
                Word("ঈর্ষা", "irsha", listOf("ir", "sha"), emptyList(), "র্ষ"),
                Word("ঈগল", "igol", listOf("i", "gol"), emptyList(), "ঈ", "ইগল", "igol → প্রথমে ইগল, সাজেশন বারে ঈগল ✓"),
                Word("ঈমান", "iman", listOf("i", "man"), emptyList(), "ঈ", "ইমান", "iman → প্রথমে ইমান, সাজেশন বারে ঈমান ✓")
            )),
            CapWords("উ", listOf(
                Word("উজ্জ্বল", "ujjol", listOf("uj", "jol"), listOf("ujjbol"), "জ্জ্ব"),
                Word("উদ্দেশ্য", "uddesho", listOf("ud", "de", "sho"), listOf("uddeshsho"), "দ্দেশ্য"),
                Word("উচ্চ", "uccho", listOf("uc", "cho"), listOf("ucco"), "চ্চ"),
                Word("উত্তর", "uttor", listOf("ut", "tor"), emptyList(), "ত্ত"),
                Word("উল্লেখ", "ullekh", listOf("ul", "lekh"), emptyList(), "ল্ল"),
                Word("উন্নয়ন", "unnoyon", listOf("un", "no", "yon"), emptyList(), "ন্ন"),
                Word("উদ্ধার", "uddhar", listOf("ud", "dhar"), emptyList(), "দ্ধ"),
                Word("উপস্থিত", "uposthit", listOf("u", "pos", "thit"), emptyList(), "স্থ")
            )),
            CapWords("ঊ", listOf(
                Word("ঊর্ধ্ব", "urdho", listOf("ur", "dho"), listOf("urdhbo"), "র্ধ্ব"),
                Word("ঊনবিংশ", "unobingsho", listOf("u", "no", "bing", "sho"), emptyList(), "ংশ"),
                Word("ঊষা", "usha", listOf("u", "sha"), emptyList(), "ঊ", "উষা", "usha → প্রথমে উষা, সাজেশন বারে ঊষা ✓"),
                Word("ঊর্মি", "urmi", listOf("ur", "mi"), emptyList(), "র্মি")
            )),
            CapWords("ঋ", listOf(
                Word("ঋণ", "rin", listOf("rin"), listOf("rrin"), "ঋ"),
                Word("ঋতু", "ritu", listOf("ri", "tu"), listOf("rritu"), "ঋ"),
                Word("ঋষি", "rishi", listOf("ri", "shi"), listOf("rrishi"), "ঋ"),
                Word("ঋণাত্মক", "rinatmok", listOf("ri", "nat", "mok"), emptyList(), "ত্ম")
            )),
            CapWords("এ", listOf(
                Word("এক্ষুনি", "ekkhuni", listOf("ek", "khu", "ni"), emptyList(), "ক্ষ"),
                Word("একসঙ্গে", "eksonge", listOf("ek", "son", "ge"), listOf("ekshonge"), "ঙ্গ"),
                Word("একত্রিত", "ekotrito", listOf("e", "ko", "tri", "to"), emptyList(), "ত্রি"),
                Word("এজন্য", "ejonno", listOf("e", "jon", "no"), emptyList(), "ন্য"),
                Word("একমাত্র", "ekmatro", listOf("ek", "ma", "tro"), emptyList(), "ত্র"),
                Word("এগিয়ে", "egiye", listOf("e", "gi", "ye"), emptyList(), "গিয়ে")
            )),
            CapWords("ঐ", listOf(
                Word("ঐতিহ্য", "oitijjo", listOf("oi", "tij", "jo"), listOf("oitihyo"), "হ্য"),
                Word("ঐক্য", "oikko", listOf("oik", "ko"), listOf("oikyo"), "ক্য"),
                Word("ঐচ্ছিক", "oicchik", listOf("oic", "chik"), emptyList(), "চ্ছ"),
                Word("ঐকমত্য", "oikomotto", listOf("oi", "ko", "mot", "to"), emptyList(), "ত্য"),
                Word("ঐশ্বরিক", "oishshorik", listOf("oish", "sho", "rik"), emptyList(), "শ্ব")
            )),
            CapWords("ও", listOf(
                Word("ওষুধ", "oshudh", listOf("o", "shudh"), emptyList(), "ষ"),
                Word("ওস্তাদ", "ostad", listOf("os", "tad"), emptyList(), "স্ত"),
                Word("ওলন্দাজ", "olondaj", listOf("o", "lon", "daj"), emptyList(), "ন্দ"),
                Word("ওজন", "ojon", listOf("o", "jon"), emptyList(), "")
            )),
            CapWords("ঔ", listOf(
                Word("ঔষধ", "oushodh", listOf("ou", "shodh"), emptyList(), "ষ"),
                Word("ঔপন্যাসিক", "ouponnashik", listOf("ou", "pon", "na", "shik"), listOf("ouponnasik"), "ন্যা"),
                Word("ঔপনিবেশিক", "ouponibeshik", listOf("ou", "po", "ni", "be", "shik"), emptyList(), "ঔ"),
                Word("ঔদ্ধত্য", "ouddhotto", listOf("oud", "dhot", "to"), emptyList(), "দ্ধত্য")
            ))
        )),
        Family("ক-বর্গ", "k থেকে ক্ষ — এক কি-তেই কঠিন যুক্তবর্ণ", listOf(
            CapWords("ক", listOf(
                Word("কিন্তু", "kintu", listOf("kin", "tu"), emptyList(), "ন্তু"),
                Word("ক্ষেত্রে", "kkhetre", listOf("kkhe", "tre"), emptyList(), "ক্ষে"),
                Word("কেন্দ্র", "kendr", listOf("kendr"), emptyList(), "ন্দ্র"),
                Word("ক্ষমতা", "kkhomota", listOf("kkho", "mo", "ta"), emptyList(), "ক্ষ"),
                Word("ক্ষমা", "kkhoma", listOf("kkho", "ma"), emptyList(), "ক্ষ"),
                Word("ক্লাব", "klab", listOf("klab"), emptyList(), "ক্লা"),
                Word("ক্রিকেটার", "kriketar", listOf("kri", "ke", "tar"), emptyList(), "ক্রি"),
                Word("কৃতজ্ঞতা", "kritoggota", listOf("kri", "tog", "go", "ta"), emptyList(), "জ্ঞ")
            )),
            CapWords("খ", listOf(
                Word("খণ্ড", "khond", listOf("khond"), emptyList(), "ণ্ড"),
                Word("খ্রিস্টান", "khristan", listOf("khri", "stan"), emptyList(), "খ্রি"),
                Word("খ্যাতি", "khyati", listOf("khya", "ti"), emptyList(), "খ্যা"),
                Word("খ্যাতনামা", "khyatonama", listOf("khya", "to", "na", "ma"), emptyList(), "খ্যা"),
                Word("খন্দকার", "khondokar", listOf("khon", "do", "kar"), emptyList(), "ন্দ"),
                Word("খ্রিষ্টাব্দে", "khrishtabde", listOf("khrish", "tab", "de"), emptyList(), "খ্রি")
            )),
            CapWords("গ", listOf(
                Word("গ্রহণ", "grohon", listOf("gro", "hon"), emptyList(), "গ্র"),
                Word("গল্প", "golp", listOf("golp"), emptyList(), "ল্প"),
                Word("গ্রাম", "gram", listOf("gram"), emptyList(), "গ্রা"),
                Word("গ্রুপ", "grup", listOf("grup"), emptyList(), "গ্রু"),
                Word("গ্রিক", "grik", listOf("grik"), emptyList(), "গ্রি"),
                Word("গ্রেপ্তার", "greptar", listOf("grep", "tar"), emptyList(), "গ্রে"),
                Word("গ্যাস", "gyas", listOf("gyas"), emptyList(), "গ্যা")
            )),
            CapWords("ঘ", listOf(
                Word("ঘণ্টা", "ghonta", listOf("ghon", "ta"), emptyList(), "ণ্টা"),
                Word("ঘ্রাণ", "ghran", listOf("ghran"), emptyList(), "ঘ্রা"),
                Word("ঘুমন্ত", "ghumont", listOf("ghu", "mont"), emptyList(), "ন্ত"),
                Word("ঘর্ষণ", "ghorshon", listOf("ghor", "shon"), emptyList(), "র্ষ"),
                Word("ঘোষণাপত্র", "ghoshonapotr", listOf("gho", "sho", "na", "potr"), emptyList(), "ত্র")
            )),
            CapWords("ঙ", listOf(
                Word("অঙ্ক", "ongko", listOf("ong", "ko"), emptyList(), "ঙ্ক"),
                Word("সঙ্গে", "songe", listOf("son", "ge"), emptyList(), "ঙ্গে"),
                Word("ব্যাঙ", "byang", listOf("byang"), emptyList(), "ব্যা"),
                Word("আঙুল", "angul", listOf("a", "ngul"), emptyList(), ""),
                Word("লাঙল", "langol", listOf("la", "ngol"), emptyList(), ""),
                Word("ভঙ্গি", "bhongi", listOf("bhon", "gi"), emptyList(), "ঙ্গি", "ভগ্নি", "bhongi → প্রথমে ভগ্নি, সাজেশন বারে ভঙ্গি ✓")
            ))
        )),
        Family("চ-বর্গ", "c মানে ছ, ch মানে চ — বাংলুর নিজের নিয়ম", listOf(
            CapWords("চ", listOf(
                Word("চিত্র", "chitr", listOf("chitr"), emptyList(), "ত্র"),
                Word("চেষ্টা", "cheshta", listOf("chesh", "ta"), emptyList(), "ষ্টা"),
                Word("চলচ্চিত্র", "choloccitr", listOf("cho", "loc", "citr"), emptyList(), "চ্চি"),
                Word("চিন্তা", "chinta", listOf("chin", "ta"), emptyList(), "ন্তা"),
                Word("চট্টগ্রাম", "chottogram", listOf("chot", "to", "gram"), emptyList(), "ট্ট"),
                Word("চুক্তি", "chukti", listOf("chuk", "ti"), emptyList(), "ক্তি"),
                Word("চিহ্নিত", "chihnit", listOf("chih", "nit"), emptyList(), "হ্নি")
            )),
            CapWords("ছ", listOf(
                Word("ছাত্র", "chhatr", listOf("chhatr"), emptyList(), "ত্র"),
                Word("ছোট্ট", "chhott", listOf("chhott"), emptyList(), "ট্ট"),
                Word("ছোটগল্প", "chhotogolp", listOf("chho", "to", "golp"), emptyList(), "ল্প"),
                Word("ছিন্ন", "chhinn", listOf("chhinn"), emptyList(), "ন্ন"),
                Word("ছিদ্র", "chhidr", listOf("chhidr"), emptyList(), "দ্র"),
                Word("ছন্দ", "chhond", listOf("chhond"), emptyList(), "ন্দ")
            )),
            CapWords("জ", listOf(
                Word("জন্ম", "jonm", listOf("jonm"), emptyList(), "ন্ম"),
                Word("জার্মান", "jarman", listOf("jar", "man"), emptyList(), "র্মা"),
                Word("জনপ্রিয়", "jonopriy", listOf("jo", "no", "priy"), emptyList(), "প্রি"),
                Word("জ্ঞান", "gyan", listOf("gyan"), listOf("jnan"), "জ্ঞা"),
                Word("জিজ্ঞেস", "jigyes", listOf("ji", "gyes"), emptyList(), "জ্ঞে"),
                Word("জিজ্ঞাসা", "jigyasa", listOf("ji", "gya", "sa"), emptyList(), "জ্ঞা")
            )),
            CapWords("ঝ", listOf(
                Word("মাঝেমধ্যে", "majhemodhye", listOf("ma", "jhe", "mo", "dhye"), emptyList(), "ধ্যে"),
                Word("ঝিল্লি", "jhilli", listOf("jhi", "lli"), emptyList(), "ল্লি"),
                Word("ঝাড়খণ্ড", "jharokhond", listOf("jha", "ro", "khond"), emptyList(), "ণ্ড"),
                Word("ঝুলন্ত", "jhulont", listOf("jhu", "lont"), emptyList(), "ন্ত"),
                Word("ঝর্ণা", "jhorna", listOf("jhor", "na"), emptyList(), "র্ণা"),
                Word("নির্ঝর", "nirjhor", listOf("nir", "jhor"), emptyList(), "র্ঝ")
            )),
            CapWords("ঞ", listOf(
                Word("অঞ্চল", "onchol", listOf("on", "chol"), emptyList(), "ঞ্চ"),
                Word("বিজ্ঞান", "bigyan", listOf("big", "yan"), emptyList(), "জ্ঞা"),
                Word("বর্ষপঞ্জি", "borshoponji", listOf("bor", "sho", "pon", "ji"), emptyList(), "র্ষ"),
                Word("অভিজ্ঞতা", "obhiggota", listOf("o", "bhig", "go", "ta"), emptyList(), "জ্ঞ"),
                Word("পদার্থবিজ্ঞান", "podarthobigyan", listOf("po", "dar", "tho", "bi", "gyan"), emptyList(), "র্থ")
            ))
        )),
        Family("ট-বর্গ", "ইংরেজি ধাঁচের শব্দও বাংলায় — ট্রেন থেকে টেমপ্লেট", listOf(
            CapWords("ট", listOf(
                Word("ট্রেন", "tren", listOf("tren"), emptyList(), "ট্রে"),
                Word("ট্রাম্প", "tramp", listOf("tramp"), emptyList(), "ট্রা"),
                Word("টেস্ট", "test", listOf("test"), emptyList(), "স্ট"),
                Word("ট্রেনিং", "trening", listOf("tre", "ning"), emptyList(), "ট্রে"),
                Word("টার্গেট", "target", listOf("tar", "get"), emptyList(), "র্গে"),
                Word("ট্রাক", "trak", listOf("trak"), emptyList(), "ট্রা")
            )),
            CapWords("ঠ", listOf(
                Word("ঠান্ডা", "thanda", listOf("than", "da"), emptyList(), "ন্ডা"),
                Word("কণ্ঠ", "konth", listOf("konth"), emptyList(), "ণ্ঠ"),
                Word("পুনর্গঠন", "punorgothon", listOf("pu", "nor", "go", "thon"), emptyList(), "র্গ"),
                Word("ঠাট্টা", "thatta", listOf("that", "ta"), emptyList(), "ট্টা"),
                Word("ঠ্যালা", "thyala", listOf("thya", "la"), emptyList(), "ঠ্যা")
            )),
            CapWords("ড", listOf(
                Word("ডিসেম্বর", "disembor", listOf("di", "sem", "bor"), emptyList(), "ম্ব"),
                Word("ডাক্তার", "daktar", listOf("dak", "tar"), emptyList(), "ক্তা"),
                Word("ডিগ্রি", "digri", listOf("di", "gri"), emptyList(), "গ্রি"),
                Word("ডক্টর", "doktor", listOf("dok", "tor"), emptyList(), "ক্ট"),
                Word("ড্রাগন", "dragon", listOf("dra", "gon"), emptyList(), "ড্রা"),
                Word("ড্রাইভ", "draibh", listOf("draibh"), emptyList(), "ড্রা"),
                Word("ডান", "dan", listOf("dan"), emptyList(), "")
            )),
            CapWords("ঢ", listOf(
                Word("ঢাকা", "dhaka", listOf("dha", "ka"), emptyList(), ""),
                Word("ঢেউ", "dheu", listOf("dheu"), emptyList(), ""),
                Word("ঢোল", "dhol", listOf("dhol"), emptyList(), ""),
                Word("ঢুকে", "dhuke", listOf("dhu", "ke"), emptyList(), ""),
                Word("ঢাকাকেন্দ্রিক", "dhakakendrik", listOf("dha", "ka", "ken", "drik"), emptyList(), "ন্দ্রি")
            )),
            CapWords("ণ", listOf(
                Word("দক্ষিণ", "dokkhin", listOf("dok", "khin"), emptyList(), "ণ"),
                Word("প্রমাণ", "proman", listOf("pro", "man"), emptyList(), "ণ"),
                Word("বর্ণনা", "bornona", listOf("bor", "no", "na"), emptyList(), "ণ"),
                Word("নির্মাণ", "nirman", listOf("nir", "man"), emptyList(), "ণ"),
                Word("নিয়ন্ত্রণ", "niyontron", listOf("ni", "yon", "tron"), emptyList(), "ণ"),
                Word("প্রাণ", "pran", listOf("pran"), emptyList(), "ণ")
            ))
        )),
        Family("ত-বর্গ", "t দিয়ে ত-ও, ট-ও — বাংলু শব্দ দেখে বোঝে", listOf(
            CapWords("ত", listOf(
                Word("ত্যাগ", "tyag", listOf("tyag"), emptyList(), "ত্যা"),
                Word("তাপমাত্রা", "tapomatra", listOf("ta", "po", "ma", "tra"), emptyList(), "ত্রা"),
                Word("তদন্ত", "todont", listOf("to", "dont"), emptyList(), "ন্ত"),
                Word("তুর্কি", "turki", listOf("tur", "ki"), emptyList(), "র্কি"),
                Word("তরঙ্গ", "torongo", listOf("to", "ro", "ngo"), emptyList(), "ঙ্গ"),
                Word("ত্রিপুরা", "tripura", listOf("tri", "pu", "ra"), emptyList(), "ত্রি"),
                Word("তৃপ্তি", "tripti", listOf("trip", "ti"), emptyList(), "প্তি"),
                Word("তাকা", "taka", listOf("ta", "ka"), emptyList(), "", "টাকা", "taka → প্রথমে টাকা, সাজেশন বারে তাকা ✓")
            )),
            CapWords("থ", listOf(
                Word("থাম্ব", "thamb", listOf("thamb"), emptyList(), "ম্ব"),
                Word("থ্রি", "thri", listOf("thri"), emptyList(), "থ্রি"),
                Word("থাইল্যান্ড", "thailyand", listOf("thai", "lyand"), emptyList(), "ল্যা"),
                Word("থার্ড", "thard", listOf("thard"), emptyList(), "র্ড"),
                Word("থ্রিলার", "thrilar", listOf("thri", "lar"), emptyList(), "থ্রি"),
                Word("থাপ্পড়", "thappor", listOf("thap", "por"), emptyList(), "প্প")
            )),
            CapWords("দ", listOf(
                Word("দ্বারা", "dbara", listOf("dba", "ra"), emptyList(), "দ্বা"),
                Word("দ্রুত", "drut", listOf("drut"), emptyList(), "দ্রু"),
                Word("দার্শনিক", "darshonik", listOf("dar", "sho", "nik"), emptyList(), "র্শ"),
                Word("দর্শন", "dorshon", listOf("dor", "shon"), emptyList(), "র্শ"),
                Word("দুর্বল", "durbol", listOf("dur", "bol"), emptyList(), "র্ব"),
                Word("দুঃখ", "dukkho", listOf("duk", "kho"), emptyList(), "")
            )),
            CapWords("ধ", listOf(
                Word("ধন্যবাদ", "dhonyobad", listOf("dho", "nyo", "bad"), emptyList(), "ন্য"),
                Word("ধর্ম", "dhorm", listOf("dhorm"), emptyList(), "র্ম"),
                Word("ধ্বংস", "dhbongs", listOf("dhbongs"), emptyList(), "ধ্ব"),
                Word("ধ্বনি", "dhboni", listOf("dhbo", "ni"), emptyList(), "ধ্ব"),
                Word("ধাক্কা", "dhakka", listOf("dhak", "ka"), emptyList(), "ক্কা"),
                Word("ধ্রুবক", "dhrubok", listOf("dhru", "bok"), emptyList(), "ধ্রু"),
                Word("ধ্যান", "dhyan", listOf("dhyan"), emptyList(), "ধ্যা")
            )),
            CapWords("ন", listOf(
                Word("নভেম্বর", "nobhembor", listOf("no", "bhem", "bor"), emptyList(), "ম্ব"),
                Word("নির্মাণ", "nirman", listOf("nir", "man"), emptyList(), "র্মা"),
                Word("নির্বাচন", "nirbachon", listOf("nir", "ba", "chon"), emptyList(), "র্বা"),
                Word("নির্দিষ্ট", "nirdisht", listOf("nir", "disht"), emptyList(), "র্দি"),
                Word("নিরাপত্তা", "nirapotta", listOf("ni", "ra", "pot", "ta"), emptyList(), "ত্তা"),
                Word("নিয়ন্ত্রণ", "niyontron", listOf("ni", "yon", "tron"), emptyList(), "ন্ত্র"),
                Word("নাম্বার", "nambar", listOf("nam", "bar"), emptyList(), "ম্বা")
            ))
        )),
        Family("প-বর্গ", "ph-ই ফ, bh-ই ভ — চাপ নেই", listOf(
            CapWords("প", listOf(
                Word("প্রথম", "prothom", listOf("pro", "thom"), emptyList(), "প্র"),
                Word("প্রায়", "pray", listOf("pray"), emptyList(), "প্রা"),
                Word("পর্যন্ত", "poryont", listOf("po", "ryont"), emptyList(), "র্য"),
                Word("প্রধান", "prodhan", listOf("pro", "dhan"), emptyList(), "প্র"),
                Word("পুরস্কার", "puroskar", listOf("pu", "ro", "skar"), emptyList(), "স্কা"),
                Word("প্লিজ", "plij", listOf("plij"), emptyList(), "প্লি"),
                Word("পছন্দ", "pochhond", listOf("po", "chhond"), emptyList(), "ন্দ"),
                Word("প্রশ্ন", "proshno", listOf("prosh", "no"), emptyList(), "প্র")
            )),
            CapWords("ফ", listOf(
                Word("ফেব্রুয়ারি", "phebruyari", listOf("phe", "bru", "ya", "ri"), emptyList(), "ব্রু"),
                Word("ফ্রান্স", "frans", listOf("frans"), emptyList(), "ফ্রা"),
                Word("ফার্সি", "pharsi", listOf("phar", "si"), emptyList(), "র্সি"),
                Word("ফ্লাইট", "flait", listOf("flait"), emptyList(), "ফ্লা"),
                Word("ফ্রি", "fri", listOf("fri"), emptyList(), "ফ্রি"),
                Word("ফিল্ম", "philm", listOf("philm"), emptyList(), "ল্ম")
            )),
            CapWords("ব", listOf(
                Word("বিশ্ববিদ্যালয়", "bissobiddaloy", listOf("bis", "so", "bid", "da", "loy"), listOf("bissobiddaloi", "bisbobidyaloy", "bishwabiddaloy"), "শ্ব"),
                Word("বৃষ্টি", "brishti", listOf("brish", "ti"), listOf("bristi"), "ষ্টি"),
                Word("ব্যবহার", "byobohar", listOf("byo", "bo", "har"), emptyList(), "ব্য"),
                Word("বিভিন্ন", "bibhinn", listOf("bi", "bhinn"), emptyList(), "ন্ন"),
                Word("বিশ্বাস", "bishwas", listOf("bi", "shwas"), emptyList(), "শ্বা"),
                Word("ব্যবস্থা", "byobostha", listOf("byo", "bo", "stha"), emptyList(), "ব্য"),
                Word("ব্যক্তিগত", "bektigoto", listOf("bek", "ti", "go", "to"), emptyList(), "ব্য"),
                Word("বন্ধ", "bondh", listOf("bondh"), emptyList(), "ন্ধ")
            )),
            CapWords("ভ", listOf(
                Word("ভিন্ন", "bhinn", listOf("bhinn"), emptyList(), "ন্ন"),
                Word("ভিত্তি", "bhitti", listOf("bhit", "ti"), emptyList(), "ত্তি"),
                Word("ভর্তি", "bhorti", listOf("bhor", "ti"), emptyList(), "র্তি"),
                Word("ভ্রমণ", "bhromon", listOf("bhro", "mon"), emptyList(), "ভ্র"),
                Word("ভবিষ্যৎ", "bhobishyot", listOf("bho", "bi", "shyot"), emptyList(), "ষ্য"),
                Word("ভ্যান", "bhyan", listOf("bhyan"), emptyList(), "ভ্যা")
            )),
            CapWords("ম", listOf(
                Word("মধ্যে", "modhye", listOf("mo", "dhye"), emptyList(), "ধ্যে"),
                Word("মার্কিন", "markin", listOf("mar", "kin"), emptyList(), "র্কি"),
                Word("মাধ্যমে", "madhyome", listOf("ma", "dhyo", "me"), emptyList(), "ধ্য"),
                Word("মৃত্যু", "mrittu", listOf("mrit", "tu"), listOf("mrittyu"), "ত্যু"),
                Word("মন্ত্রণালয়", "montronaloy", listOf("mon", "tro", "na", "loy"), emptyList(), "ন্ত্র"),
                Word("মাত্র", "matr", listOf("matr"), emptyList(), "ত্র"),
                Word("মোহাম্মদ", "mohammod", listOf("mo", "ham", "mod"), emptyList(), "ম্ম"),
                Word("মুক্ত", "mukt", listOf("mukt"), emptyList(), "ক্ত")
            ))
        )),
        Family("অন্তঃস্থ ও হ", "j দিয়ে য, r-এ র-ফলা — ফলার জাদু", listOf(
            CapWords("য", listOf(
                Word("যুদ্ধ", "juddho", listOf("jud", "dho"), emptyList(), "দ্ধ"),
                Word("যন্ত্র", "jontro", listOf("jon", "tro"), emptyList(), "ন্ত্র"),
                Word("যথেষ্ট", "jotheshto", listOf("jo", "thesh", "to"), emptyList(), "ষ্ট"),
                Word("যাত্রা", "jatra", listOf("ja", "tra"), emptyList(), "ত্রা"),
                Word("যুক্তরাষ্ট্র", "juktorashtro", listOf("juk", "to", "rash", "tro"), emptyList(), "ষ্ট্র"),
                Word("যত্ন", "jotno", listOf("jot", "no"), emptyList(), "ত্ন"),
                Word("যোগ্যতা", "joggota", listOf("jog", "go", "ta"), emptyList(), "গ্য"),
                Word("যন্ত্রণা", "jontrona", listOf("jon", "tro", "na"), emptyList(), "ন্ত্র", "যন্ত্রনা", "jontrona → ণ না ন? দুটোই সাজেশন বারে ✓")
            )),
            CapWords("র", listOf(
                Word("রাষ্ট্র", "rashtro", listOf("rash", "tro"), listOf("rastro"), "ষ্ট্র"),
                Word("রক্ষা", "rokkha", listOf("rok", "kha"), emptyList(), "ক্ষা"),
                Word("রাস্তা", "rasta", listOf("ra", "sta"), emptyList(), "স্তা"),
                Word("রক্ত", "rokt", listOf("rokt"), emptyList(), "ক্ত"),
                Word("রেকর্ড", "rekord", listOf("re", "kord"), emptyList(), "র্ড"),
                Word("রান্না", "ranna", listOf("ran", "na"), emptyList(), "ন্না")
            )),
            CapWords("ল", listOf(
                Word("লক্ষ", "lokkh", listOf("lokkh"), emptyList(), "ক্ষ"),
                Word("লর্ড", "lord", listOf("lord"), emptyList(), "র্ড"),
                Word("লম্বা", "lomba", listOf("lom", "ba"), emptyList(), "ম্বা"),
                Word("লন্ডন", "london", listOf("lon", "don"), emptyList(), "ন্ড"),
                Word("লক্ষণ", "lokkhon", listOf("lok", "khon"), emptyList(), "ক্ষ"),
                Word("লজ্জা", "lojja", listOf("loj", "ja"), emptyList(), "জ্জা"),
                Word("লাইসেন্স", "laisens", listOf("lai", "sens"), emptyList(), "ন্স")
            )),
            CapWords("হ", listOf(
                Word("হ্যালো", "hyalo", listOf("hya", "lo"), emptyList(), "হ্যা"),
                Word("হিন্দু", "hindu", listOf("hin", "du"), emptyList(), "ন্দু"),
                Word("হত্যা", "hotya", listOf("ho", "tya"), emptyList(), "ত্যা"),
                Word("হিন্দি", "hindi", listOf("hin", "di"), emptyList(), "ন্দি"),
                Word("হ্রাস", "hras", listOf("hras"), emptyList(), "হ্রা"),
                Word("হ্রদ", "hrod", listOf("hrod"), emptyList(), "হ্র"),
                Word("হৃদয়", "hridoy", listOf("hri", "doy"), emptyList(), "")
            ))
        )),
        Family("উষ্ম বর্ণ", "একটা s — তিনটা অক্ষর, বানান বাংলুর কাজ", listOf(
            CapWords("শ", listOf(
                Word("শিক্ষা", "shikkha", listOf("shik", "kha"), emptyList(), "ক্ষা"),
                Word("শব্দ", "shobd", listOf("shobd"), emptyList(), "ব্দ"),
                Word("শক্তি", "shokti", listOf("shok", "ti"), emptyList(), "ক্তি"),
                Word("শুধুমাত্র", "shudhumatr", listOf("shu", "dhu", "matr"), emptyList(), "ত্র"),
                Word("শিল্প", "shilp", listOf("shilp"), emptyList(), "ল্প"),
                Word("শিক্ষক", "shikkhok", listOf("shik", "khok"), emptyList(), "ক্ষ"),
                Word("শান্তি", "shanti", listOf("shan", "ti"), emptyList(), "ন্তি"),
                Word("শ্রদ্ধা", "sroddha", listOf("srod", "dha"), emptyList(), "শ্র")
            )),
            CapWords("ষ", listOf(
                Word("চেষ্টা", "cheshta", listOf("chesh", "ta"), emptyList(), "ষ্টা"),
                Word("সৃষ্টি", "srishti", listOf("srish", "ti"), emptyList(), "ষ্টি"),
                Word("অপেক্ষা", "opekkha", listOf("o", "pek", "kha"), emptyList(), "ক্ষা"),
                Word("ক্ষমতা", "kkhomota", listOf("kkho", "mo", "ta"), emptyList(), "ক্ষ"),
                Word("পরিষ্কার", "porishkar", listOf("po", "rish", "kar"), emptyList(), "ষ্কা"),
                Word("ষড়যন্ত্র", "shorojontro", listOf("sho", "ro", "jon", "tro"), emptyList(), "ন্ত্র")
            )),
            CapWords("স", listOf(
                Word("স্যার", "syar", listOf("syar"), emptyList(), "স্যা"),
                Word("সম্পর্কে", "somporke", listOf("som", "por", "ke"), emptyList(), "ম্প"),
                Word("সমস্যা", "somosya", listOf("so", "mo", "sya"), listOf("somossa"), "স্যা"),
                Word("স্থান", "sthan", listOf("sthan"), emptyList(), "স্থা"),
                Word("সুন্দর", "sundor", listOf("sun", "dor"), emptyList(), "ন্দ"),
                Word("সমস্ত", "somost", listOf("so", "most"), emptyList(), "স্ত"),
                Word("স্কুল", "skul", listOf("skul"), emptyList(), "স্কু"),
                Word("স্বাস্থ্য", "shastho", listOf("shas", "tho"), listOf("sbastho"), "স্থ্য")
            ))
        )),
        Family("বিশেষ বর্ণ", "ড় ঢ় য় ৎ ং ঁ — বিশেষ অক্ষরও সহজে", listOf(
            CapWords("ড়", listOf(
                Word("বাড়ি", "bari", listOf("ba", "ri"), emptyList(), "ড়"),
                Word("গাড়ি", "gari", listOf("ga", "ri"), emptyList(), "ড়"),
                Word("ছাড়া", "chhara", listOf("chha", "ra"), emptyList(), "ড়"),
                Word("পড়াশোনা", "porashona", listOf("po", "ra", "sho", "na"), emptyList(), "ড়"),
                Word("তাড়াতাড়ি", "taratari", listOf("ta", "ra", "ta", "ri"), emptyList(), "ড়"),
                Word("পড়ে", "pore", listOf("po", "re"), emptyList(), "ড়", "পরে", "pore → প্রথমে পরে, সাজেশন বারে পড়ে ✓")
            )),
            CapWords("ঢ়", listOf(
                Word("আষাঢ়", "asharh", listOf("a", "sharh"), emptyList(), "ঢ়"),
                Word("গাঢ়", "garho", listOf("gar", "ho"), emptyList(), "ঢ়")
            )),
            CapWords("য়", listOf(
                Word("সময়", "somoy", listOf("so", "moy"), emptyList(), "য়"),
                Word("মেয়ে", "meye", listOf("me", "ye"), emptyList(), "য়"),
                Word("বিষয়", "bishoy", listOf("bi", "shoy"), emptyList(), "য়"),
                Word("প্রয়োজন", "proyojon", listOf("pro", "yo", "jon"), emptyList(), "য়"),
                Word("বিদ্যালয়", "biddaloy", listOf("bid", "da", "loy"), emptyList(), "য়"),
                Word("ছেলেমেয়ে", "chhelemeye", listOf("chhe", "le", "me", "ye"), emptyList(), "য়")
            )),
            CapWords("ৎ", listOf(
                Word("অর্থাৎ", "orthat", listOf("or", "that"), emptyList(), "ৎ"),
                Word("চমৎকার", "chomotokar", listOf("cho", "mo", "to", "kar"), emptyList(), "ৎ"),
                Word("উৎস", "utso", listOf("ut", "so"), emptyList(), "ৎ"),
                Word("বিদ্যুৎ", "bidyut", listOf("bi", "dyut"), emptyList(), "ৎ"),
                Word("উৎপন্ন", "utoponn", listOf("u", "to", "ponn"), emptyList(), "ৎ"),
                Word("সাক্ষাৎ", "sakkhat", listOf("sak", "khat"), emptyList(), "ৎ")
            )),
            CapWords("ং", listOf(
                Word("এবং", "ebong", listOf("e", "bong"), emptyList(), "ং"),
                Word("বাংলা", "bangla", listOf("bang", "la"), emptyList(), "ং"),
                Word("সংখ্যা", "songkhya", listOf("song", "khya"), emptyList(), "ং"),
                Word("ব্যাংক", "byangk", listOf("byangk"), emptyList(), "ং"),
                Word("প্রশংসা", "proshongsa", listOf("pro", "shong", "sa"), emptyList(), "ং"),
                Word("ধ্বংস", "dhbongs", listOf("dhbongs"), emptyList(), "ং")
            )),
            CapWords("ঃ", listOf(
                Word("নিঃশ্বাস", "nishshash", listOf("nish", "shash"), listOf("nisshash"), "ঃ"),
                Word("নিঃসন্দেহে", "nissondehe", listOf("nis", "son", "de", "he"), listOf("nihsondehe"), "ঃ"),
                Word("দুঃসংবাদ", "dussongbad", listOf("dus", "song", "bad"), emptyList(), "ঃ"),
                Word("দুঃসাহস", "dussahosh", listOf("dus", "sa", "hosh"), emptyList(), "ঃ"),
                Word("পুনঃপ্রকাশ", "punoprokash", listOf("pu", "no", "pro", "kash"), emptyList(), "ঃ")
            )),
            CapWords("ঁ", listOf(
                Word("চাঁদ", "chad", listOf("chad"), listOf("chand"), "ঁ"),
                Word("দাঁত", "dat", listOf("dat"), listOf("dant"), "ঁ"),
                Word("পাঁচ", "pach", listOf("pach"), listOf("panch"), "ঁ"),
                Word("হাঁটা", "hata", listOf("ha", "ta"), emptyList(), "ঁ"),
                Word("দাঁড়িয়ে", "dariye", listOf("da", "ri", "ye"), emptyList(), "ঁ"),
                Word("পৌঁছে", "pouche", listOf("pou", "che"), emptyList(), "ঁ")
            ))
        ))
    )

    /** every advertised word, for the pin wall and any UI count. */
    val ALL_WORDS: List<Word> = FAMILIES.flatMap { f -> f.caps.flatMap { it.words } }
}
