package com.banglu.winime

/** The classpath resource `generateVersionResource` writes at build time. */
private const val VERSION_RESOURCE = "/banglu-typer-version.txt"

/**
 * What version this build is.
 *
 * `jpackage.app-version` is written into `app/BangluTyper.cfg` by jpackage and
 * is the truth for an installed app. The generated resource covers a dev run
 * and the CI app image. If NEITHER answers — or either answers something that
 * is not a version — [current] is null: an app that does not know what it is
 * must never conclude that something else is newer, and the control window says
 * "অজানা" rather than inventing a number.
 *
 * Lives beside the app rather than inside `update/` because BOTH editions need
 * it — the Store edition has no updater but still shows its version.
 */
object AppVersion {
    val current: String? by lazy {
        resolve(System.getProperty("jpackage.app-version"), bundled())
    }

    internal fun resolve(jpackageProperty: String?, bundledResource: String?): String? =
        listOfNotNull(jpackageProperty, bundledResource)
            .map { it.trim() }
            .firstOrNull { Version.parse(it) != null }

    private fun bundled(): String? = runCatching {
        AppVersion::class.java.getResourceAsStream(VERSION_RESOURCE)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()
}

/**
 * Dotted-numeric version ordering, and nothing else.
 *
 * Anything that is not one-to-four ASCII numeric components — `v1.2`,
 * `1.0.3-beta`, `garbage`, an empty string, a full-width digit — fails to
 * parse, and an unparseable version is never "newer". A published manifest
 * whose version field is junk must therefore leave the user exactly where they
 * are; it must never read as an upgrade.
 */
internal object Version {
    private val SHAPE = Regex("""\d{1,9}(\.\d{1,9}){0,3}""")

    fun parse(raw: String): List<Int>? {
        val trimmed = raw.trim()
        if (!SHAPE.matches(trimmed)) return null
        return trimmed.split('.').map { it.toInt() }
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
