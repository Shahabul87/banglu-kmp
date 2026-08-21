package com.banglu.winime

import com.banglu.engine.JvmSqliteDictionaryLoader
import com.banglu.engine.JvmSqlitePhoneticIndexStore
import com.banglu.engine.SmartEngineAdapter
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory

/** One shared full-engine boot for the whole test suite (real dictionary). */
object TestEngine {
    // createTempDir() is deprecated; kotlin.io.path.createTempDirectory keeps
    // test output free of deprecation warnings.
    val storageDir: File = createTempDirectory(prefix = "banglu-win-test").toFile()
    private var booted = false

    @Synchronized
    fun boot() {
        if (booted) return
        runBlocking {
            SmartEngineAdapter.initializeSync()
            val db = JvmSqliteDictionaryLoader.findDictionarySqlite()
            val store = JvmSqlitePhoneticIndexStore(db)
            check(store.isAvailable) { "dictionary.sqlite rejected: ${db.absolutePath}" }
            SmartEngineAdapter.setPhoneticIndex(store)
            SmartEngineAdapter.initialize(WinStorage(storageDir), JvmSqliteDictionaryLoader(db))
        }
        booted = true
    }
}
