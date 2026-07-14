package com.banglu.engine

import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.types.BigramModelData
import com.banglu.engine.types.SmartDictionaryEntry
import com.banglu.engine.types.WordCategory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * JVM test implementation of DictionaryLoader that reads from the local
 * dictionary.sqlite file using JDBC.
 *
 * This enables JVM tests to access the full 480K Bengali word list,
 * frequency data, and disambiguation map -- unlocking Layers 5.5, 5.7, and 6.
 *
 * @param dbFile Path to dictionary.sqlite (defaults to repo root)
 */
class TestDictionaryLoader(
    dbFile: File = JvmSqliteDictionaryLoader.findDictionarySqlite()
) : JvmSqliteDictionaryLoader(dbFile) {
    companion object {
        fun findDictionarySqlite(): File = JvmSqliteDictionaryLoader.findDictionarySqlite()
    }
}
