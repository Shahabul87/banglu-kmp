package com.banglu.engine.assist

/**
 * S98: identity assist — email/username convenience for form fields, both
 * keyboard language modes.
 *
 *  - Domain completion: the moment a typed token contains '@', the user's
 *    own domains (learned from emails they actually typed) and the common
 *    providers complete it (sham251087@ -> sham251087@gmail.com …).
 *  - Saved identities: full email addresses the user has typed are kept
 *    (most-recent first, bounded) and offered as one-tap fills in email
 *    fields.
 *
 * PRIVACY CONTRACT (non-negotiable, mirrors the S44 posture):
 *  - Passwords and OTP codes are NEVER seen by this class — the IME's
 *    sensitive-field gate drops them before any call is made, and nothing
 *    here may weaken that.
 *  - Everything is on-device, bounded, and cleared with the user's
 *    learned-word data.
 *
 * Concurrency: single engine lane (S75 law), same as the other engines.
 */
class IdentityAssist {

    private companion object {
        const val MAX_SAVED_EMAILS = 8
        const val MAX_SAVED_DOMAINS = 8
        const val MAX_TOKEN_LENGTH = 64
        val COMMON_DOMAINS = listOf(
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com",
            "icloud.com", "live.com", "protonmail.com", "aol.com",
        )
    }

    /** Most-recent first. */
    private val savedEmails = ArrayDeque<String>()
    private val savedDomains = ArrayDeque<String>()

    // ── suggestions ──────────────────────────────────────────────────────

    /** True when [token] is an in-progress or complete email-shaped entry. */
    fun isEmailLikeToken(token: String): Boolean {
        if (token.length !in 2..MAX_TOKEN_LENGTH) return false
        val at = token.lastIndexOf('@')
        if (at <= 0) return false
        val local = token.substring(0, at)
        return local.all { it.isLetterOrDigit() || it in "._-+" }
    }

    /**
     * Full-address completions for a token containing '@'. The user's own
     * domains come first, then the common providers; a partial domain
     * filters both (sham@gm -> sham@gmail.com).
     */
    /** @param includeSaved false → complete from the built-in list only
     *  (S136: identity memory switched off). */
    fun domainSuggestions(token: String, limit: Int = 3, includeSaved: Boolean = true): List<String> {
        if (!isEmailLikeToken(token) || limit <= 0) return emptyList()
        val at = token.lastIndexOf('@')
        val local = token.substring(0, at)
        val typedDomain = token.substring(at + 1).lowercase()
        val out = LinkedHashSet<String>()
        for (domain in (if (includeSaved) savedDomains.toList() else emptyList()) + COMMON_DOMAINS) {
            if (out.size >= limit) break
            if (domain.startsWith(typedDomain) && domain != typedDomain) {
                out.add("$local@$domain")
            }
        }
        return out.toList()
    }

    /** The user's saved addresses, most-recent first — the one-tap fills. */
    fun savedIdentities(limit: Int = 3): List<String> = savedEmails.take(limit)

    // ── learning ─────────────────────────────────────────────────────────

    /**
     * A committed token that may be an identity. Only COMPLETE addresses
     * (local@domain.tld) are saved; the domain feeds completion ranking.
     * The caller gates on the personal-dictionary setting and the
     * sensitive-field rules.
     */
    fun recordIdentity(tokenRaw: String) {
        val token = tokenRaw.trim()
        if (!isCompleteEmail(token)) return
        val normalized = token.lowercase()
        savedEmails.remove(normalized)
        savedEmails.addFirst(normalized)
        while (savedEmails.size > MAX_SAVED_EMAILS) savedEmails.removeLast()
        val domain = normalized.substringAfterLast('@')
        savedDomains.remove(domain)
        savedDomains.addFirst(domain)
        while (savedDomains.size > MAX_SAVED_DOMAINS) savedDomains.removeLast()
    }

    fun clear() {
        savedEmails.clear()
        savedDomains.clear()
    }

    // ── persistence (caller-owned) ───────────────────────────────────────

    /** Line format: `e<TAB>address` and `d<TAB>domain`, recency order. */
    fun serialize(): String = buildString {
        for (e in savedEmails) append("e\t").append(e).append('\n')
        for (d in savedDomains) append("d\t").append(d).append('\n')
    }

    /** Tolerant — malformed lines are skipped, never fatal. */
    fun load(data: String) {
        clear()
        for (line in data.lineSequence()) {
            val parts = line.split('\t')
            if (parts.size != 2) continue
            when (parts[0]) {
                "e" -> if (isCompleteEmail(parts[1]) && savedEmails.size < MAX_SAVED_EMAILS &&
                    parts[1] !in savedEmails
                ) savedEmails.addLast(parts[1].lowercase())
                "d" -> if (isPlausibleDomain(parts[1]) && savedDomains.size < MAX_SAVED_DOMAINS &&
                    parts[1] !in savedDomains
                ) savedDomains.addLast(parts[1].lowercase())
            }
        }
    }

    // ── internals ────────────────────────────────────────────────────────

    private fun isCompleteEmail(token: String): Boolean {
        if (token.length !in 5..MAX_TOKEN_LENGTH) return false
        val at = token.lastIndexOf('@')
        if (at <= 0 || at != token.indexOf('@')) return false
        val local = token.substring(0, at)
        val domain = token.substring(at + 1)
        if (!local.all { it.isLetterOrDigit() || it in "._-+" }) return false
        return isPlausibleDomain(domain)
    }

    private fun isPlausibleDomain(domain: String): Boolean {
        if (domain.length !in 4..40) return false
        val dot = domain.lastIndexOf('.')
        if (dot <= 0 || dot == domain.length - 1) return false
        if (domain.first() == '.' || "" in domain.split('.')) return false
        return domain.all { it.isLetterOrDigit() || it == '.' || it == '-' } &&
            domain.substring(dot + 1).length >= 2
    }
}
