package com.banglu.desktop.editor

import com.banglu.engine.JvmSqliteDictionaryLoader
import com.banglu.engine.JvmSqlitePhoneticIndexStore
import com.banglu.engine.SmartEngineAdapter
import java.io.File

/** Real engine on the repo-root dictionary — initialized once per test JVM. */
object TestEngine {
    val facade: EngineFacade by lazy {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.setPhoneticIndex(JvmSqlitePhoneticIndexStore(dbFile()))
        RealEngineFacade
    }

    fun dbFile(): File =
        File("../dictionary.sqlite").takeIf(File::exists)
            ?: JvmSqliteDictionaryLoader.findDictionarySqlite()
}
