package com.banglu.keyboard

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Single-source-of-truth bridge for "banglu_prefs" across the IME privacy
 * boundary.
 *
 * The keyboard (IME) process is deliberately isolated from the `:ui` process
 * (see verifyImePrivacyBoundary) so account/billing/network surfaces never
 * share a process with keystroke handling. SharedPreferences is NOT
 * multi-process safe: each process caches the file at first load and never
 * reloads, so a toggle written from `:ui` (settings, voice disclosure) was
 * invisible to the IME until its process died — and IME-side writes could
 * clobber `:ui` writes entirely. That is the bug that broke voice typing
 * ("disclosure not accepted" loop) and made settings toggles unreliable.
 *
 * This provider is hosted in the DEFAULT (keyboard) process, so the IME's
 * direct SharedPreferences instance is the one true copy and its
 * OnSharedPreferenceChangeListener fires for every `:ui` write. Activities
 * access prefs through [remoteBangluPrefs] which proxies to this provider.
 */
class BangluPrefsProvider : ContentProvider() {

    companion object {
        const val METHOD_GET_ALL = "get_all"
        const val METHOD_PUT_BATCH = "put_batch"
        /** S135 (F-001): erase learned data INSIDE the keyboard process. */
        const val METHOD_ERASE_LEARNING = "erase_learning"
        const val ERASE_SCOPE_ALL = "all"
        const val ERASE_SCOPE_IDENTITY = "identity"
        const val KEY_REMOVALS = "__removals"
        const val KEY_OK = "__ok"
        /** Written after every successful erase; the IME's preference listener
         *  rebuilds its engine on this key so the live keyboard forgets too. */
        const val KEY_LEARNING_ERASED_AT = "learning_erased_at"

        fun authority(context: Context): String = "${context.packageName}.prefs"

        internal fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        return when (method) {
            METHOD_GET_ALL -> Bundle().apply {
                for ((key, value) in prefs(context).all) {
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                        is Set<*> -> putStringArrayList(key, ArrayList(value.map { it.toString() }))
                    }
                }
            }
            METHOD_PUT_BATCH -> {
                val editor = prefs(context).edit()
                extras?.getStringArrayList(KEY_REMOVALS)?.forEach { editor.remove(it) }
                extras?.keySet()?.forEach { key ->
                    if (key == KEY_REMOVALS) return@forEach
                    when (val value = @Suppress("DEPRECATION") extras.get(key)) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is ArrayList<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
                    }
                }
                editor.apply()
                Bundle()
            }
            METHOD_ERASE_LEARNING -> Bundle().apply {
                putBoolean(KEY_OK, eraseLearning(context, arg))
            }
            else -> null
        }
    }

    /**
     * S135 (F-001, production audit): "Clear learned data" used to call
     * SmartEngineAdapter.eraseAllLearning() from the `:ui` process, where the
     * adapter's storage is never attached — the null-safe call skipped the
     * persisted keys and the settings screen still toasted success. Android
     * processes share no singleton memory, so the ONLY place an erase is real
     * is this process: the SharedPreferences instance here is the one the IME
     * writes through, and the adapter here is the one serving keystrokes.
     *
     * Runs on a binder thread; every step is durable (commit) before `true`
     * is returned. Any throwable → false, and the caller must NOT claim
     * success.
     */
    private fun eraseLearning(context: Context, scope: String?): Boolean = try {
        val storage = AndroidStorage(context.applicationContext)
        when (scope) {
            ERASE_SCOPE_IDENTITY -> {
                // Live memory + persisted blob; the IME needs no rebuild for
                // this — the identity store is not baked into the engine.
                com.banglu.engine.SmartEngineAdapter.clearIdentity()
                storage.clearIdentityUserData()
                true
            }
            else -> {
                // Persisted keys first (storage may be unattached when the IME
                // service is not running in this process), then live memory,
                // then the stamp that makes a running IME rebuild its engine.
                kotlinx.coroutines.runBlocking { storage.clearAllLearningData() }
                kotlinx.coroutines.runBlocking { com.banglu.engine.SmartEngineAdapter.eraseAllLearning() }
                prefs(context).edit().putLong(KEY_LEARNING_ERASED_AT, System.currentTimeMillis()).commit()
            }
        }
    } catch (_: Throwable) {
        false
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0
}

/**
 * `:ui`-process view of "banglu_prefs": a [SharedPreferences] adapter that
 * proxies every read/write through [BangluPrefsProvider] in the keyboard
 * process. Reads snapshot the full (small) map per call; writes batch through
 * Editor.apply/commit. Change listeners are unsupported across the boundary —
 * the settings UI owns its own state and never needs them.
 */
fun remoteBangluPrefs(context: Context): SharedPreferences = RemoteBangluPrefs(context.applicationContext)

/**
 * S135 (F-001): `:ui`-side entry point for erasing learned data. Blocking IPC
 * into the keyboard process — call off the main thread. Returns true only
 * when the keyboard process confirmed the persisted keys AND its live engine
 * state are gone; the caller shows success only on true.
 *
 * @param scope [BangluPrefsProvider.ERASE_SCOPE_ALL] or
 *   [BangluPrefsProvider.ERASE_SCOPE_IDENTITY]
 */
fun eraseLearningInKeyboardProcess(context: Context, scope: String): Boolean = try {
    val uri = Uri.parse("content://${BangluPrefsProvider.authority(context)}")
    context.applicationContext.contentResolver
        .call(uri, BangluPrefsProvider.METHOD_ERASE_LEARNING, scope, null)
        ?.getBoolean(BangluPrefsProvider.KEY_OK, false) == true
} catch (_: Exception) {
    false
}

private class RemoteBangluPrefs(private val context: Context) : SharedPreferences {

    private val uri: Uri get() = Uri.parse("content://${BangluPrefsProvider.authority(context)}")

    private fun snapshot(): Bundle = try {
        context.contentResolver.call(uri, BangluPrefsProvider.METHOD_GET_ALL, null, null) ?: Bundle()
    } catch (_: Exception) {
        Bundle()
    }

    override fun getAll(): Map<String, *> {
        val b = snapshot()
        return b.keySet().associateWith { @Suppress("DEPRECATION") b.get(it) }
    }

    override fun getString(key: String, defValue: String?): String? =
        snapshot().getString(key) ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        snapshot().getStringArrayList(key)?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int {
        val b = snapshot(); return if (b.containsKey(key)) b.getInt(key) else defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        val b = snapshot(); return if (b.containsKey(key)) b.getLong(key) else defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val b = snapshot(); return if (b.containsKey(key)) b.getFloat(key) else defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val b = snapshot(); return if (b.containsKey(key)) b.getBoolean(key) else defValue
    }

    override fun contains(key: String): Boolean = snapshot().containsKey(key)

    override fun edit(): SharedPreferences.Editor = RemoteEditor()

    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class RemoteEditor : SharedPreferences.Editor {
        private val staged = Bundle()
        private val removals = ArrayList<String>()
        private var clearFirst = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { if (value == null) removals.add(key) else staged.putString(key, value) }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { if (values == null) removals.add(key) else staged.putStringArrayList(key, ArrayList(values)) }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { staged.putInt(key, value) }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { staged.putLong(key, value) }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { staged.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { staged.putBoolean(key, value) }
        override fun remove(key: String): SharedPreferences.Editor = apply { removals.add(key) }
        override fun clear(): SharedPreferences.Editor = apply { clearFirst = true }

        override fun commit(): Boolean {
            flush(); return true
        }

        override fun apply() = flush()

        private fun flush() {
            val extras = Bundle(staged)
            val allRemovals = ArrayList(removals)
            if (clearFirst) {
                // clear = remove every existing key not being re-staged
                snapshot().keySet().forEach { if (!extras.containsKey(it)) allRemovals.add(it) }
            }
            extras.putStringArrayList(BangluPrefsProvider.KEY_REMOVALS, allRemovals)
            try {
                context.contentResolver.call(uri, BangluPrefsProvider.METHOD_PUT_BATCH, null, extras)
            } catch (_: Exception) {
                // provider unreachable (keyboard process dead mid-write) — the
                // next write retries; settings UI state is self-owned.
            }
        }
    }
}
