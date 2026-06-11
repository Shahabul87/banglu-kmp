package com.banglu.keyboard

object EmojiData {
    data class EmojiCategory(
        val name: String,
        val icon: String,
        val emojis: List<String>,
        val isTextIcon: Boolean = false
    )

    data class TextSticker(
        val text: String,
        val aliases: List<String>
    )

    data class GifSticker(
        val text: String,
        val assetName: String,
        val fallbackText: String,
        val aliases: List<String>
    )

    val textStickers = listOf(
        TextSticker("ধন্যবাদ 🙏", listOf("thanks", "thank you", "dhonnobad", "ধন্যবাদ")),
        TextSticker("ভালো আছি 😊", listOf("bhalo", "fine", "ভালো")),
        TextSticker("আসসালামু আলাইকুম", listOf("salam", "assalamu", "আসসালামু")),
        TextSticker("ওয়ালাইকুম সালাম", listOf("walaikum", "salam", "সালাম")),
        TextSticker("শুভ সকাল ☀️", listOf("morning", "sokal", "সকাল")),
        TextSticker("শুভ রাত 🌙", listOf("night", "raat", "রাত")),
        TextSticker("অনেক ভালো লাগলো ❤️", listOf("love", "valo", "bhalo", "bhalobasha", "ভালোবাসা")),
        TextSticker("ইনশাআল্লাহ", listOf("inshallah", "ইনশাআল্লাহ")),
        TextSticker("আলহামদুলিল্লাহ", listOf("alhamdulillah", "আলহামদুলিল্লাহ")),
        TextSticker("ঠিক আছে ✅", listOf("ok", "okay", "thik", "ঠিক")),
        TextSticker("পরে কথা বলি", listOf("later", "pore", "কথা")),
        TextSticker("আমি আসছি", listOf("coming", "aschi", "আসছি")),
        TextSticker("কোথায় আছো?", listOf("where", "kothay", "কোথায়")),
        TextSticker("কি করছো?", listOf("what", "korcho", "করছো")),
        TextSticker("দুঃখিত", listOf("sorry", "dukkhito", "দুঃখিত")),
        TextSticker("অভিনন্দন 🎉", listOf("congrats", "congratulations", "অভিনন্দন")),
        TextSticker("শুভ জন্মদিন 🎂", listOf("birthday", "jonmodin", "জন্মদিন")),
        TextSticker("সাবধানে থেকো", listOf("safe", "sabdhane", "সাবধানে")),
        TextSticker("খুব সুন্দর!", listOf("nice", "sundor", "সুন্দর")),
        TextSticker("হাহাহা 😂", listOf("haha", "hasi", "hashi", "laugh", "হাসি"))
    )

    val gifStickers = listOf(
        GifSticker("হাসির GIF 😂", "laugh", "হাহাহা 😂", listOf("gif", "haha", "hasi", "hashi", "laugh", "হাসি")),
        GifSticker("ধন্যবাদ GIF 🙏", "thanks", "ধন্যবাদ 🙏", listOf("gif", "thanks", "dhonnobad", "ধন্যবাদ")),
        GifSticker("ভালোবাসা GIF ❤️", "love", "ভালোবাসা ❤️", listOf("gif", "love", "valo", "bhalo", "bhalobasha", "ভালোবাসা")),
        GifSticker("রাগ GIF 😡", "angry", "রাগ লাগছে 😡", listOf("gif", "angry", "rag", "raag", "রাগ")),
        GifSticker("দুঃখ GIF 😢", "sad", "মন খারাপ 😢", listOf("gif", "sad", "dukkho", "dukho", "mon kharap", "দুঃখ")),
        GifSticker("অভিনন্দন GIF 🎉", "congrats", "অভিনন্দন 🎉", listOf("gif", "congrats", "ovinondon", "obhinondon", "অভিনন্দন")),
        GifSticker("শুভ জন্মদিন GIF 🎂", "birthday", "শুভ জন্মদিন 🎂", listOf("gif", "birthday", "jonmodin", "জন্মদিন")),
        GifSticker("ওকে GIF ✅", "ok", "ঠিক আছে ✅", listOf("gif", "ok", "okay", "thik", "ঠিক")),
        GifSticker("ওয়াও GIF 🤩", "wow", "ওয়াও 🤩", listOf("gif", "wow", "অসাধারণ")),
        GifSticker("ফায়ার GIF 🔥", "fire", "দারুণ 🔥", listOf("gif", "fire", "দারুণ"))
    )

    private val aliases = mapOf(
        "হাসি" to listOf("\uD83D\uDE02", "\uD83D\uDE0A", "\uD83D\uDE00", "\uD83E\uDD23"),
        "hasi" to listOf("\uD83D\uDE02", "\uD83D\uDE0A", "\uD83D\uDE00", "\uD83E\uDD23"),
        "hashi" to listOf("\uD83D\uDE02", "\uD83D\uDE0A", "\uD83D\uDE00", "\uD83E\uDD23"),
        "ভালোবাসা" to listOf("\u2764\uFE0F", "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83E\uDD70"),
        "bhalobasha" to listOf("\u2764\uFE0F", "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83E\uDD70"),
        "valobasha" to listOf("\u2764\uFE0F", "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83E\uDD70"),
        "হার্ট" to listOf("\u2764\uFE0F", "\uD83D\uDC96", "\uD83D\uDC94"),
        "দুঃখ" to listOf("\uD83D\uDE22", "\uD83D\uDE2D", "\uD83D\uDE14"),
        "রাগ" to listOf("\uD83D\uDE21", "\uD83D\uDE20", "\uD83E\uDD2C"),
        "rag" to listOf("\uD83D\uDE21", "\uD83D\uDE20", "\uD83E\uDD2C"),
        "raag" to listOf("\uD83D\uDE21", "\uD83D\uDE20", "\uD83E\uDD2C"),
        "ধন্যবাদ" to listOf("\uD83D\uDE4F", "\uD83E\uDD32", "\uD83E\uDD1D"),
        "ok" to listOf("\uD83D\uDC4C", "\u2705", "\uD83D\uDC4D"),
        "okay" to listOf("\uD83D\uDC4C", "\u2705", "\uD83D\uDC4D"),
        "love" to listOf("\u2764\uFE0F", "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83E\uDD70"),
        "laugh" to listOf("\uD83D\uDE02", "\uD83E\uDD23", "\uD83D\uDE04"),
        "sad" to listOf("\uD83D\uDE22", "\uD83D\uDE2D", "\uD83D\uDE14"),
        "angry" to listOf("\uD83D\uDE21", "\uD83D\uDE20", "\uD83E\uDD2C"),
        "thanks" to listOf("\uD83D\uDE4F", "\uD83E\uDD32", "\uD83E\uDD1D"),
        "fire" to listOf("\uD83D\uDD25"),
        "birthday" to listOf("\uD83C\uDF82", "\uD83C\uDF89", "\uD83C\uDF81"),
        "food" to listOf("\uD83C\uDF55", "\u2615", "\uD83C\uDF54"),
        "money" to listOf("\uD83D\uDCB0", "\uD83D\uDCB3", "\uD83D\uDCB8")
    )

    val categories = listOf(
        EmojiCategory("Frequent", "...", listOf(
            "\uD83D\uDE02","\u2764\uFE0F","\uD83D\uDE0D","\uD83E\uDD23","\uD83D\uDE0A","\uD83D\uDE4F","\uD83D\uDE18","\uD83D\uDE2D",
            "\uD83D\uDC4D","\uD83D\uDC4C","\uD83D\uDC4F","\uD83D\uDD25","\uD83E\uDD70","\uD83D\uDE01","\uD83D\uDE05","\uD83D\uDE0E",
            "\uD83D\uDE22","\uD83D\uDE21","\uD83E\uDD14","\uD83D\uDE33","\uD83D\uDC96","\uD83D\uDC94","\uD83D\uDC90","\uD83C\uDF89",
            "\uD83C\uDF82","\uD83C\uDF81","\uD83C\uDF39","\uD83C\uDF3A","\uD83D\uDCAF","\u2705","\u274C","\u26A0\uFE0F",
            "\uD83D\uDC40","\uD83D\uDC8B","\uD83D\uDE4C","\uD83E\uDD1D","\u270C\uFE0F","\uD83E\uDD1E","\uD83D\uDCAA","\uD83E\uDEE1",
            "\uD83D\uDE09","\uD83D\uDE0B","\uD83D\uDE1C","\uD83E\uDD2A","\uD83E\uDD17","\uD83E\uDD2D","\uD83E\uDD2B","\uD83D\uDE44",
            "\uD83D\uDE34","\uD83E\uDD75","\uD83E\uDD76","\uD83E\uDD73","\uD83E\uDD2C","\uD83D\uDC80","\uD83D\uDCA9","\uD83E\uDD21",
            "\uD83C\uDF55","\u2615","\uD83C\uDF54","\uD83C\uDF1F","\uD83C\uDF08","\uD83D\uDCA1","\uD83D\uDD14","\uD83D\uDCB0"
        ), isTextIcon = true),
        EmojiCategory("Stickers", "ST", textStickers.map { it.text }, isTextIcon = true),
        EmojiCategory("GIF", "GIF", gifStickers.map { it.text }, isTextIcon = true),
        EmojiCategory("Smileys", "\uD83D\uDE0A", listOf(
            "\uD83D\uDE00","\uD83D\uDE03","\uD83D\uDE04","\uD83D\uDE01","\uD83D\uDE06","\uD83D\uDE05","\uD83E\uDD23","\uD83D\uDE02","\uD83D\uDE42","\uD83D\uDE43",
            "\uD83D\uDE09","\uD83D\uDE0A","\uD83D\uDE07","\uD83E\uDD70","\uD83D\uDE0D","\uD83E\uDD29","\uD83D\uDE18","\uD83D\uDE17","\uD83D\uDE1A","\uD83D\uDE19",
            "\uD83E\uDD72","\uD83D\uDE0B","\uD83D\uDE1B","\uD83D\uDE1C","\uD83E\uDD2A","\uD83D\uDE1D","\uD83E\uDD17","\uD83E\uDD2D","\uD83E\uDEE2","\uD83E\uDEE3",
            "\uD83E\uDD2B","\uD83E\uDD14","\uD83E\uDEE1","\uD83E\uDD10","\uD83E\uDD28","\uD83D\uDE10","\uD83D\uDE11","\uD83D\uDE36","\uD83E\uDEE5","\uD83D\uDE0F",
            "\uD83D\uDE12","\uD83D\uDE44","\uD83D\uDE2C","\uD83E\uDD25","\uD83D\uDE0C","\uD83D\uDE14","\uD83D\uDE2A","\uD83E\uDD24","\uD83D\uDE34","\uD83D\uDE37",
            "\uD83E\uDD12","\uD83E\uDD15","\uD83E\uDD22","\uD83E\uDD2E","\uD83E\uDD75","\uD83E\uDD76","\uD83E\uDD74","\uD83D\uDE35","\uD83E\uDD2F","\uD83E\uDD20",
            "\uD83E\uDD73","\uD83E\uDD78","\uD83D\uDE0E","\uD83E\uDD13","\uD83E\uDDD0","\uD83D\uDE15","\uD83E\uDEE4","\uD83D\uDE1F","\uD83D\uDE41","\uD83D\uDE2E",
            "\uD83D\uDE2F","\uD83D\uDE32","\uD83D\uDE33","\uD83E\uDD7A","\uD83E\uDD79","\uD83D\uDE26","\uD83D\uDE27","\uD83D\uDE28","\uD83D\uDE30","\uD83D\uDE25",
            "\uD83D\uDE22","\uD83D\uDE2D","\uD83D\uDE31","\uD83D\uDE16","\uD83D\uDE23","\uD83D\uDE1E","\uD83D\uDE13","\uD83D\uDE29","\uD83D\uDE2B","\uD83E\uDD71",
            "\uD83D\uDE24","\uD83D\uDE21","\uD83D\uDE20","\uD83E\uDD2C","\uD83D\uDE08","\uD83D\uDC7F","\uD83D\uDC80","\u2620\uFE0F","\uD83D\uDCA9","\uD83E\uDD21",
            "\uD83D\uDC79","\uD83D\uDC7A","\uD83D\uDC7B","\uD83D\uDC7D","\uD83D\uDC7E","\uD83E\uDD16","\uD83D\uDE3A","\uD83D\uDE38","\uD83D\uDE39","\uD83D\uDE3B",
            "\uD83D\uDE3C","\uD83D\uDE3D","\uD83D\uDE40","\uD83D\uDE3F","\uD83D\uDE3E"
        )),
        EmojiCategory("Gestures", "\uD83D\uDC4B", listOf(
            "\uD83D\uDC4B","\uD83E\uDD1A","\uD83D\uDD90\uFE0F","\u270B","\uD83D\uDD96","\uD83E\uDEF1","\uD83E\uDEF2","\uD83E\uDEF3","\uD83E\uDEF4","\uD83D\uDC4C",
            "\uD83E\uDD0C","\uD83E\uDD0F","\u270C\uFE0F","\uD83E\uDD1E","\uD83E\uDEF0","\uD83E\uDD1F","\uD83E\uDD18","\uD83E\uDD19","\uD83D\uDC48","\uD83D\uDC49",
            "\uD83D\uDC46","\uD83D\uDD95","\uD83D\uDC47","\u261D\uFE0F","\uD83E\uDEF5","\uD83D\uDC4D","\uD83D\uDC4E","\u270A","\uD83D\uDC4A","\uD83E\uDD1B",
            "\uD83E\uDD1C","\uD83D\uDC4F","\uD83D\uDE4C","\uD83E\uDEF6","\uD83D\uDC50","\uD83E\uDD32","\uD83E\uDD1D","\uD83D\uDE4F","\u270D\uFE0F","\uD83D\uDC85",
            "\uD83E\uDD33","\uD83D\uDCAA","\uD83E\uDDBE","\uD83E\uDDBF","\uD83E\uDDB5","\uD83E\uDDB6","\uD83D\uDC42","\uD83E\uDDBB","\uD83D\uDC43","\uD83E\uDDE0",
            "\uD83E\uDEC0","\uD83E\uDEC1","\uD83E\uDDB7","\uD83E\uDDB4","\uD83D\uDC40","\uD83D\uDC41\uFE0F","\uD83D\uDC45","\uD83D\uDC44","\uD83E\uDEE6","\uD83D\uDC8B"
        )),
        EmojiCategory("People", "\uD83D\uDC68", listOf(
            "\uD83D\uDC76","\uD83D\uDC67","\uD83E\uDDD2","\uD83D\uDC66","\uD83D\uDC69","\uD83E\uDDD1","\uD83D\uDC68","\uD83D\uDC69\u200D\uD83E\uDDB1","\uD83E\uDDD1\u200D\uD83E\uDDB1","\uD83D\uDC68\u200D\uD83E\uDDB1",
            "\uD83D\uDC69\u200D\uD83E\uDDB0","\uD83E\uDDD1\u200D\uD83E\uDDB0","\uD83D\uDC68\u200D\uD83E\uDDB0","\uD83D\uDC71\u200D\u2640\uFE0F","\uD83D\uDC71","\uD83D\uDC71\u200D\u2642\uFE0F","\uD83D\uDC69\u200D\uD83E\uDDB3","\uD83E\uDDD1\u200D\uD83E\uDDB3","\uD83D\uDC68\u200D\uD83E\uDDB3","\uD83D\uDC69\u200D\uD83E\uDDB2",
            "\uD83E\uDDD1\u200D\uD83E\uDDB2","\uD83D\uDC68\u200D\uD83E\uDDB2","\uD83E\uDDD4\u200D\u2640\uFE0F","\uD83E\uDDD4","\uD83E\uDDD4\u200D\u2642\uFE0F","\uD83D\uDC75","\uD83E\uDDD3","\uD83D\uDC74","\uD83D\uDC72","\uD83D\uDC73\u200D\u2640\uFE0F",
            "\uD83D\uDC73","\uD83D\uDC73\u200D\u2642\uFE0F","\uD83E\uDDD5","\uD83D\uDC6E\u200D\u2640\uFE0F","\uD83D\uDC6E","\uD83D\uDC6E\u200D\u2642\uFE0F","\uD83D\uDC77\u200D\u2640\uFE0F","\uD83D\uDC77","\uD83D\uDC77\u200D\u2642\uFE0F","\uD83D\uDC82\u200D\u2640\uFE0F",
            "\uD83D\uDC82","\uD83D\uDC82\u200D\u2642\uFE0F","\uD83D\uDD75\uFE0F\u200D\u2640\uFE0F","\uD83D\uDD75\uFE0F","\uD83D\uDD75\uFE0F\u200D\u2642\uFE0F","\uD83D\uDC69\u200D\u2695\uFE0F","\uD83E\uDDD1\u200D\u2695\uFE0F","\uD83D\uDC68\u200D\u2695\uFE0F","\uD83D\uDC69\u200D\uD83C\uDF3E","\uD83E\uDDD1\u200D\uD83C\uDF3E"
        )),
        EmojiCategory("Animals", "\uD83D\uDC31", listOf(
            "\uD83D\uDC36","\uD83D\uDC31","\uD83D\uDC2D","\uD83D\uDC39","\uD83D\uDC30","\uD83E\uDD8A","\uD83D\uDC3B","\uD83D\uDC3C","\uD83D\uDC3B\u200D\u2744\uFE0F","\uD83D\uDC28",
            "\uD83D\uDC2F","\uD83E\uDD81","\uD83D\uDC2E","\uD83D\uDC37","\uD83D\uDC3D","\uD83D\uDC38","\uD83D\uDC35","\uD83D\uDE48","\uD83D\uDE49","\uD83D\uDE4A",
            "\uD83D\uDC12","\uD83D\uDC14","\uD83D\uDC27","\uD83D\uDC26","\uD83D\uDC24","\uD83D\uDC23","\uD83D\uDC25","\uD83E\uDD86","\uD83E\uDD85","\uD83E\uDD89",
            "\uD83E\uDD87","\uD83D\uDC3A","\uD83D\uDC17","\uD83D\uDC34","\uD83E\uDD84","\uD83D\uDC1D","\uD83E\uDEB1","\uD83D\uDC1B","\uD83E\uDD8B","\uD83D\uDC0C",
            "\uD83D\uDC1E","\uD83D\uDC1C","\uD83E\uDEB0","\uD83E\uDEB2","\uD83E\uDEB3","\uD83E\uDD9F","\uD83E\uDD97","\uD83D\uDD77\uFE0F","\uD83D\uDD78\uFE0F","\uD83E\uDD82",
            "\uD83D\uDC22","\uD83D\uDC0D","\uD83E\uDD8E","\uD83E\uDD96","\uD83E\uDD95","\uD83D\uDC19","\uD83E\uDD91","\uD83E\uDD90","\uD83E\uDD9E","\uD83E\uDD80",
            "\uD83D\uDC21","\uD83D\uDC20","\uD83D\uDC1F","\uD83D\uDC2C","\uD83D\uDC33","\uD83D\uDC0B","\uD83E\uDD88","\uD83D\uDC0A","\uD83D\uDC05","\uD83D\uDC06"
        )),
        EmojiCategory("Food", "\uD83C\uDF55", listOf(
            "\uD83C\uDF4F","\uD83C\uDF4E","\uD83C\uDF50","\uD83C\uDF4A","\uD83C\uDF4B","\uD83C\uDF4C","\uD83C\uDF49","\uD83C\uDF47","\uD83C\uDF53","\uD83E\uDED0",
            "\uD83C\uDF48","\uD83C\uDF52","\uD83C\uDF51","\uD83E\uDD6D","\uD83C\uDF4D","\uD83E\uDD65","\uD83E\uDD5D","\uD83C\uDF45","\uD83C\uDF46","\uD83E\uDD51",
            "\uD83E\uDD66","\uD83E\uDD6C","\uD83E\uDD52","\uD83C\uDF36\uFE0F","\uD83E\uDED1","\uD83C\uDF3D","\uD83E\uDD55","\uD83E\uDED2","\uD83E\uDDC4","\uD83E\uDDC5",
            "\uD83E\uDD54","\uD83C\uDF60","\uD83E\uDED8","\uD83E\uDD50","\uD83C\uDF5E","\uD83E\uDD56","\uD83E\uDD68","\uD83E\uDDC0","\uD83E\uDD5A","\uD83C\uDF73",
            "\uD83E\uDDC8","\uD83E\uDD5E","\uD83E\uDDC7","\uD83E\uDD53","\uD83E\uDD69","\uD83C\uDF57","\uD83C\uDF56","\uD83E\uDDB4","\uD83C\uDF2D","\uD83C\uDF54",
            "\uD83C\uDF5F","\uD83C\uDF55","\uD83E\uDED3","\uD83E\uDD6A","\uD83E\uDD59","\uD83E\uDDC6","\uD83C\uDF2E","\uD83C\uDF2F","\uD83E\uDED4","\uD83E\uDD57"
        )),
        EmojiCategory("Activities", "\u26BD", listOf(
            "\u26BD","\uD83C\uDFC0","\uD83C\uDFC8","\u26BE","\uD83E\uDD4E","\uD83C\uDFBE","\uD83C\uDFD0","\uD83C\uDFC9","\uD83E\uDD4F","\uD83C\uDFB1",
            "\uD83E\uDE80","\uD83C\uDFD3","\uD83C\uDFF8","\uD83E\uDD4A","\uD83E\uDD4B","\uD83E\uDD45","\u26F3","\u26F8\uFE0F","\uD83C\uDFA3","\uD83E\uDD3F",
            "\uD83C\uDFAF","\uD83E\uDE81","\uD83E\uDE83","\uD83E\uDE84","\uD83C\uDFAE","\uD83D\uDD79\uFE0F","\uD83C\uDFB2","\u265F\uFE0F","\uD83E\uDDE9","\uD83C\uDFA8",
            "\uD83D\uDDBC\uFE0F","\uD83C\uDFAD","\uD83E\uDE70","\uD83E\uDE71","\uD83C\uDFAB","\uD83C\uDFAA","\uD83E\uDD39","\uD83E\uDD38","\uD83E\uDD3C","\uD83E\uDD3D",
            "\uD83E\uDD3E","\uD83C\uDFC7","\uD83E\uDDD8","\uD83C\uDFC4","\uD83C\uDFCA","\uD83E\uDDD7","\uD83D\uDEB4","\uD83D\uDEB5","\uD83C\uDFCB\uFE0F","\uD83C\uDFC6",
            "\uD83E\uDD47","\uD83E\uDD48","\uD83E\uDD49","\uD83C\uDF96\uFE0F","\uD83C\uDFC5","\uD83C\uDF97\uFE0F","\uD83C\uDF9F\uFE0F","\uD83C\uDFAC","\uD83C\uDFA4","\uD83C\uDFA7"
        )),
        EmojiCategory("Travel", "\u2708\uFE0F", listOf(
            "\uD83D\uDE97","\uD83D\uDE95","\uD83D\uDE99","\uD83D\uDE8C","\uD83D\uDE8E","\uD83C\uDFCE\uFE0F","\uD83D\uDE93","\uD83D\uDE91","\uD83D\uDE92","\uD83D\uDE90",
            "\uD83D\uDEFB","\uD83D\uDE9A","\uD83D\uDE9B","\uD83D\uDE9C","\uD83C\uDFCD\uFE0F","\uD83D\uDEF5","\uD83D\uDEB2","\uD83D\uDEF4","\uD83D\uDEF9","\uD83D\uDEFC",
            "\uD83D\uDE8F","\uD83D\uDEE3\uFE0F","\uD83D\uDEE4\uFE0F","\uD83D\uDEDE","\u26FD","\uD83D\uDEDE","\uD83D\uDEA8","\uD83D\uDEA5","\uD83D\uDEA6","\uD83D\uDED1",
            "\uD83D\uDEA7","\u2693","\uD83D\uDEDF","\u26F5","\uD83D\uDEF6","\uD83D\uDEA4","\uD83D\uDEF3\uFE0F","\u26F4\uFE0F","\uD83D\uDEE5\uFE0F","\uD83D\uDEA2",
            "\u2708\uFE0F","\uD83D\uDEE9\uFE0F","\uD83D\uDEEB","\uD83D\uDEEC","\uD83E\uDE82","\uD83D\uDCBA","\uD83D\uDE81","\uD83D\uDE9F","\uD83D\uDEA0","\uD83D\uDEA1",
            "\uD83C\uDFE0","\uD83C\uDFE1","\uD83C\uDFE2","\uD83C\uDFE3","\uD83C\uDFE4","\uD83C\uDFE5","\uD83C\uDFE6","\uD83C\uDFE8","\uD83C\uDFE9","\uD83C\uDFEA"
        )),
        EmojiCategory("Objects", "\uD83D\uDCA1", listOf(
            "\u231A","\uD83D\uDCF1","\uD83D\uDCF2","\uD83D\uDCBB","\u2328\uFE0F","\uD83D\uDDA5\uFE0F","\uD83D\uDDA8\uFE0F","\uD83D\uDDB1\uFE0F","\uD83D\uDDB2\uFE0F","\uD83D\uDD79\uFE0F",
            "\uD83D\uDDDC\uFE0F","\uD83D\uDCBD","\uD83D\uDCBE","\uD83D\uDCBF","\uD83D\uDCC0","\uD83D\uDCFC","\uD83D\uDCF7","\uD83D\uDCF8","\uD83D\uDCF9","\uD83C\uDFA5",
            "\uD83D\uDCFD\uFE0F","\uD83C\uDF9E\uFE0F","\uD83D\uDCDE","\u260E\uFE0F","\uD83D\uDCDF","\uD83D\uDCE0","\uD83D\uDCFA","\uD83D\uDCFB","\uD83C\uDF99\uFE0F","\uD83C\uDF9A\uFE0F",
            "\uD83C\uDF9B\uFE0F","\uD83E\uDDED","\u23F1\uFE0F","\u23F2\uFE0F","\u23F0","\uD83D\uDD70\uFE0F","\u231B","\u23F3","\uD83D\uDCE1","\uD83D\uDD0B",
            "\uD83E\uDEAB","\uD83D\uDD0C","\uD83D\uDCA1","\uD83D\uDD26","\uD83D\uDD6F\uFE0F","\uD83E\uDDEF","\uD83D\uDEE2\uFE0F","\uD83D\uDCB8","\uD83D\uDCB5","\uD83D\uDCB4",
            "\uD83D\uDCB6","\uD83D\uDCB7","\uD83E\uDE99","\uD83D\uDCB0","\uD83D\uDCB3","\uD83D\uDC8E","\u2696\uFE0F","\uD83E\uDE9C","\uD83E\uDDF0","\uD83E\uDE9B"
        )),
        EmojiCategory("Symbols", "\u2764\uFE0F", listOf(
            "\u2764\uFE0F","\uD83E\uDDE1","\uD83D\uDC9B","\uD83D\uDC9A","\uD83D\uDC99","\uD83D\uDC9C","\uD83D\uDDA4","\uD83E\uDD0D","\uD83E\uDD0E","\uD83D\uDC94",
            "\u2764\uFE0F\u200D\uD83D\uDD25","\u2764\uFE0F\u200D\uD83E\uDE79","\u2763\uFE0F","\uD83D\uDC95","\uD83D\uDC9E","\uD83D\uDC93","\uD83D\uDC97","\uD83D\uDC96","\uD83D\uDC98","\uD83D\uDC9D",
            "\uD83D\uDC9F","\u262E\uFE0F","\u271D\uFE0F","\u262A\uFE0F","\uD83D\uDD49\uFE0F","\u2638\uFE0F","\u2721\uFE0F","\uD83D\uDD2F","\uD83D\uDD4E","\u262F\uFE0F",
            "\u2626\uFE0F","\uD83D\uDED0","\u26CE","\u2648","\u2649","\u264A","\u264B","\u264C","\u264D","\u264E",
            "\u264F","\u2650","\u2651","\u2652","\u2653","\uD83C\uDD94","\u269B\uFE0F","\uD83C\uDE51","\u2622\uFE0F","\u2623\uFE0F",
            "\uD83D\uDCF4","\uD83D\uDCF3","\uD83C\uDE36","\uD83C\uDE1A","\uD83C\uDE38","\uD83C\uDE3A","\uD83C\uDE37\uFE0F","\u2734\uFE0F","\uD83C\uDD9A","\uD83D\uDCAE"
        )),
        EmojiCategory("Flags", "\uD83C\uDFC1", listOf(
            "\uD83C\uDFC1","\uD83D\uDEA9","\uD83C\uDF8C","\uD83C\uDFF4","\uD83C\uDFF3\uFE0F","\uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08","\uD83C\uDFF3\uFE0F\u200D\u26A7\uFE0F","\uD83C\uDFF4\u200D\u2620\uFE0F",
            "\uD83C\uDDE7\uD83C\uDDE9","\uD83C\uDDEE\uD83C\uDDF3","\uD83C\uDDFA\uD83C\uDDF8","\uD83C\uDDEC\uD83C\uDDE7","\uD83C\uDDE8\uD83C\uDDE6","\uD83C\uDDE6\uD83C\uDDFA","\uD83C\uDDEF\uD83C\uDDF5","\uD83C\uDDF0\uD83C\uDDF7","\uD83C\uDDE8\uD83C\uDDF3","\uD83C\uDDEB\uD83C\uDDF7",
            "\uD83C\uDDE9\uD83C\uDDEA","\uD83C\uDDEE\uD83C\uDDF9","\uD83C\uDDEA\uD83C\uDDF8","\uD83C\uDDE7\uD83C\uDDF7","\uD83C\uDDF2\uD83C\uDDFD","\uD83C\uDDF7\uD83C\uDDFA","\uD83C\uDDF8\uD83C\uDDE6","\uD83C\uDDE6\uD83C\uDDEA","\uD83C\uDDF9\uD83C\uDDF7","\uD83C\uDDF5\uD83C\uDDF0",
            "\uD83C\uDDF2\uD83C\uDDFE","\uD83C\uDDF8\uD83C\uDDEC","\uD83C\uDDF9\uD83C\uDDED","\uD83C\uDDFB\uD83C\uDDF3","\uD83C\uDDEE\uD83C\uDDE9","\uD83C\uDDF5\uD83C\uDDED","\uD83C\uDDF3\uD83C\uDDEC","\uD83C\uDDEA\uD83C\uDDEC","\uD83C\uDDFF\uD83C\uDDE6","\uD83C\uDDF0\uD83C\uDDEA"
        ))
    )

    fun search(query: String, limit: Int = 96): List<String> {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return emptyList()

        val results = linkedSetOf<String>()
        textStickers
            .filter { sticker ->
                sticker.text.lowercase().contains(key) ||
                    sticker.aliases.any { alias -> alias.lowercase().contains(key) || key.contains(alias.lowercase()) }
            }
            .forEach { results.add(it.text) }

        gifStickers
            .filter { sticker ->
                sticker.text.lowercase().contains(key) ||
                    sticker.aliases.any { alias -> alias.lowercase().contains(key) || key.contains(alias.lowercase()) }
            }
            .forEach { results.add(it.text) }

        aliases
            .filterKeys { alias -> alias.lowercase().contains(key) || key.contains(alias.lowercase()) }
            .values
            .flatten()
            .forEach { results.add(it) }

        categories
            .filter { it.name.lowercase().contains(key) }
            .flatMap { it.emojis }
            .forEach { results.add(it) }

        if (results.isEmpty()) {
            categories.flatMap { it.emojis }
                .filter { it == query.trim() }
                .forEach { results.add(it) }
        }

        return results.take(limit)
    }

    fun isTextSticker(value: String): Boolean = textStickers.any { it.text == value }

    fun isGifSticker(value: String): Boolean = gifStickers.any { it.text == value }

    fun gifStickerFor(value: String): GifSticker? = gifStickers.firstOrNull { it.text == value }
}
