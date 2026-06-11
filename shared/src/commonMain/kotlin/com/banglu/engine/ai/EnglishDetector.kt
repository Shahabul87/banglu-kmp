package com.banglu.engine.ai

/**
 * English Word Detector — Prevents mangled transliteration of English words.
 *
 * When Bengali users type English words in Bangla mode (computer, possible, keyboard),
 * the phonetic engine would produce garbled Bengali output. This detector identifies
 * common English words so the engine can route them to an English-pronunciation layer.
 *
 * Detection methods:
 * 1. Known English word set (~500 common words used in Bengali text)
 * 2. Regex pattern heuristics (URLs, file extensions, abbreviations)
 * 3. English suffix heuristics with Bengali phonetic guard
 *
 * Ported from web: src/engine/smart/ai/EnglishDetector.ts
 */
object EnglishDetector {

    // ── Known English words commonly typed within Bengali text ──────────

    private val ENGLISH_WORDS: Set<String> = setOf(
        // --- Tech / Computing ---
        "computer", "laptop", "desktop", "tablet", "keyboard", "mouse", "monitor",
        "screen", "display", "printer", "scanner", "router", "modem", "server",
        "software", "hardware", "firmware", "database", "internet", "website",
        "browser", "google", "facebook", "youtube", "instagram", "twitter",
        "whatsapp", "telegram", "email", "gmail", "outlook", "download", "upload",
        "online", "offline", "wifi", "bluetooth", "android", "windows", "linux",
        "apple", "samsung", "pixel", "chrome", "firefox", "safari",
        "app", "application", "update", "install", "delete", "backup", "restore",
        "password", "username", "login", "logout", "signup", "account", "profile",
        "file", "folder", "document", "image", "video", "audio", "photo",
        "copy", "paste", "cut", "undo", "redo", "save", "print", "share",
        "search", "filter", "sort", "select", "click", "scroll", "zoom",
        "code", "program", "debug", "compile", "deploy", "test", "build",
        "html", "css", "javascript", "python", "react", "node", "typescript",
        "api", "url", "http", "https", "json", "xml", "sql",
        "frontend", "backend", "fullstack", "framework", "library",
        "github", "gitlab", "docker", "cloud", "aws", "azure",
        "ai", "chatgpt", "claude",

        // --- Common adjectives/descriptors ---
        "possible", "impossible", "available", "important", "different", "special",
        "simple", "complex", "complete", "perfect", "correct", "wrong",
        "good", "bad", "best", "worst", "great", "nice", "fine",
        "easy", "hard", "difficult", "fast", "slow", "quick",
        "new", "old", "big", "small", "long", "short",
        "first", "last", "next", "previous", "current", "final",
        "open", "close", "start", "stop", "begin", "end",
        "true", "false", "yes", "no", "ok", "okay",
        "free", "paid", "premium", "basic", "advanced", "pro",
        "public", "private", "secure", "safe",
        "active", "inactive", "enabled", "disabled",
        "valid", "invalid", "required", "optional",
        "successful", "failed", "pending", "approved", "rejected",

        // --- Business / Work ---
        "office", "meeting", "project", "team", "manager", "boss",
        "company", "business", "market", "product", "service", "customer",
        "report", "presentation", "schedule", "deadline", "budget",
        "salary", "payment", "invoice", "receipt", "bill",
        "portfolio", "investment", "finance", "economy", "stock", "loan", "insurance",
        "interview", "resume", "job", "career", "promotion",
        "training", "workshop", "seminar", "conference",

        // --- Education ---
        "school", "college", "university", "student", "teacher", "professor",
        "class", "course", "lecture", "exam", "result",
        "degree", "diploma", "certificate", "admission", "scholarship",
        "assignment", "homework", "thesis", "research",
        "laboratory", "campus",

        // --- Transportation ---
        "bus", "train", "car", "bike", "taxi", "uber", "rickshaw",
        "flight", "airport", "station", "ticket", "booking",
        "road", "highway", "bridge", "parking",
        "honeymoon", "wedding", "anniversary", "tour", "travel", "trip",
        "vacation", "holiday", "resort", "beach", "honeymooners",

        // --- Food (English names used in Bengali) ---
        "pizza", "burger", "sandwich", "pasta", "noodles", "cake", "chocolate",
        "coffee", "tea", "juice", "water", "milk", "cream", "ice",
        "chicken", "fish", "meat", "rice", "bread", "butter",
        "restaurant", "hotel", "cafe", "menu", "order", "delivery",

        // --- Health ---
        "doctor", "hospital", "clinic", "medicine", "injection",
        "surgery", "treatment", "therapy",
        "covid", "virus", "infection", "fever", "cold", "cough",
        "vaccine", "dose", "immunity", "health", "fitness",

        // --- Entertainment ---
        "movie", "film", "song", "music", "drama", "series",
        "game", "play", "sport", "cricket", "football", "tennis",
        "concert", "show", "live", "stream", "channel",
        "netflix", "spotify", "amazon", "prime",

        // --- Common English words in daily Bengali usage ---
        "time", "date", "day", "week", "month", "year",
        "morning", "evening", "night", "today", "tomorrow", "yesterday",
        "phone", "call", "message", "text", "chat", "voice",
        "number", "address", "name", "age", "birthday",
        "money", "cash", "card", "bank", "atm", "transaction",
        "price", "cost", "discount", "offer", "deal", "sale",
        "size", "color", "style", "design", "brand", "model",
        "problem", "solution", "issue", "error", "bug", "fix",
        "help", "support", "guide", "tutorial", "manual",
        "plan", "goal", "target", "strategy", "policy",
        "news", "media", "press", "article", "blog", "post",
        "group", "member", "admin", "user", "guest", "visitor",
        "list", "table", "chart", "graph", "map", "form",
        "link", "page", "site", "home", "about", "contact",
        "setting", "settings", "option", "options", "preference",
        "notification", "alert", "warning", "info", "detail", "details",
        "version", "release", "feature", "change", "improvement",
        "like", "love", "comment", "reply", "follow", "subscribe",
        "thank", "thanks", "sorry", "please", "welcome", "hello", "bye",
        "sir", "madam", "mr", "mrs", "miss", "dr",

        // --- Plural/verb forms of common words ---
        "computers", "phones", "messages", "emails", "files", "photos",
        "videos", "updates", "options", "settings", "features", "changes",
        "problems", "solutions", "results", "reports", "documents",
        "keyboards", "meetings", "projects", "payments", "orders",

        // --- Bengali-English loanword bases: Bengali pronunciation should be primary,
        //     but the raw English token must remain available as an alternate suggestion.
        "practice", "practise", "discussion", "complain", "confuse", "justify",
        "accident", "content", "inbox", "status", "hostel", "dictionary",
        "bread", "butter", "cheese", "dinner", "pocket", "skirt", "tops",
        "scooter", "liter", "litre", "meter", "metre", "percent", "percentage",
        "packet", "minute", "club", "party", "politics", "cycle", "kg", "kilo",
        "tshirt"
    )

    // ── Regex patterns that strongly suggest English text ───────────────

    private val ENGLISH_PATTERNS: List<Regex> = listOf(
        Regex("^https?://"),       // URLs
        Regex("^www\\."),          // Web addresses
        Regex("\\.\\w{2,4}$"),     // File extensions (.pdf, .docx, .jpg)
        Regex("[A-Z]{2,}"),        // Consecutive capitals (API, URL, etc.)
        Regex("\\d+[a-z]+\\d+")   // Mixed alphanumeric (3rd, 4pm)
    )

    // ── English suffixes for heuristic detection ───────────────────────

    private val ENGLISH_SUFFIXES: List<String> = listOf(
        "tion", "sion", "ment", "ness", "able", "ible",
        "ful", "less", "ous", "ive", "ing", "ght"
    )

    // ── Consonant cluster regex for Bengali phonetic guard ─────────────

    private val ALL_ALPHA = Regex("^[a-z]*$", RegexOption.IGNORE_CASE)
    private val FOUR_PLUS_CONSONANTS = Regex("[bcdfghjklmnpqrstvwxyz]{4,}", RegexOption.IGNORE_CASE)

    /**
     * Check if a word is likely English (should not be transliterated).
     *
     * The caller should pass the ORIGINAL input (preserving case) so that
     * pattern checks for uppercase abbreviations and URLs work correctly.
     * The word-set lookup is done case-insensitively.
     *
     * @param word The input word to check (original case preserved)
     * @return true if the word is likely English
     */
    fun isEnglish(word: String): Boolean {
        val lower = word.lowercase()

        // 1. Check known English words
        if (isKnownEnglishWord(lower)) return true

        // 2. Check English patterns (use original word for case-sensitive patterns)
        if (isStrongEnglishPattern(word)) return true

        // 3. Heuristic: words ending in common English suffixes
        //    Only when the word does NOT look like valid Bengali phonetic input
        if (isHeuristicEnglishWord(lower)) return true

        return false
    }

    fun isKnownEnglishWord(word: String): Boolean = word.lowercase() in ENGLISH_WORDS

    fun isStrongEnglishPattern(word: String): Boolean =
        ENGLISH_PATTERNS.any { it.containsMatchIn(word) }

    fun isHeuristicEnglishWord(word: String): Boolean {
        val lower = word.lowercase()
        if (lower.length <= 5) return false
        return ENGLISH_SUFFIXES.any { suffix ->
            lower.endsWith(suffix) && !looksLikeBengaliPhonetic(lower)
        }
    }

    /**
     * Quick check if a word looks like valid Bengali phonetic input.
     *
     * Bengali phonetic words typically use simpler consonant clusters.
     * Returns true when the word is all-alpha AND does NOT have 4+
     * consecutive consonants (which would be impossible in Bengali phonetics).
     *
     * This is the guard that prevents false positives: a word like "management"
     * has the suffix "-ment" but also looks like Bengali phonetic, so the suffix
     * heuristic would NOT fire. A word like "brightness" has "-ness" and also
     * "ghtn" (4 consonants), so it does NOT look Bengali and the suffix fires.
     */
    private fun looksLikeBengaliPhonetic(word: String): Boolean {
        return ALL_ALPHA.matches(word) && !FOUR_PLUS_CONSONANTS.containsMatchIn(word)
    }
}
