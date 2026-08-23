package com.banglu.winime

/**
 * The tray balloon for a found update (user request, 2026-08-23: the offer
 * used to live only inside the control window, so a user who never opened it
 * never learned an update existed).
 *
 * Once per version, PERSISTED (`WinPrefs.updateNoticeVersion`): the app
 * starts with Windows at every login, and the automatic check runs on every
 * start, so a session-scoped gate would balloon the user daily for the same
 * version — exactly the nagging the updater's failure posture forbids. The
 * control window's offer row remains visible regardless; the balloon is only
 * the "come look" signal, shown one time.
 *
 * Pure text and a pure gate, edition-independent (the Store build compiles
 * this too but never announces — no updater, no [UpdateStatus.offeredVersion]).
 */
object UpdateNotice {
    const val TITLE = "বাংলু টাইপার — নতুন সংস্করণ"

    fun shouldAnnounce(offered: String?, alreadyAnnounced: String?): Boolean =
        !offered.isNullOrBlank() && offered != alreadyAnnounced

    /**
     * A tray balloon is not clickable through to the installer, so the text
     * itself says where the one-click install lives.
     */
    fun message(version: String): String =
        "নতুন সংস্করণ $version এসেছে। বাংলু টাইপারের উইন্ডো খুলে এক ক্লিকে ইনস্টল করুন — " +
            "অ্যাপ নিজেই বন্ধ হয়ে নতুন সংস্করণে চালু হবে।"
}
