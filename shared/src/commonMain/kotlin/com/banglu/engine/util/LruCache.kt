package com.banglu.engine.util

/**
 * S45: multiplatform LRU map replacing JVM-only access-ordered LinkedHashMap
 * (removeEldestEntry is not available on Kotlin/JS). Common LinkedHashMap
 * preserves insertion order on every target; access order is emulated by
 * re-inserting on get. Same bounded-memo semantics the engine caches had.
 */
class LruCache<K, V>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()

    operator fun get(key: K): V? {
        val value = map.remove(key) ?: return null
        map[key] = value // re-insert = most recently used
        return value
    }

    operator fun set(key: K, value: V) {
        map.remove(key)
        map[key] = value
        if (map.size > maxSize) {
            val eldest = map.keys.first()
            map.remove(eldest)
        }
    }

    fun remove(key: K): V? = map.remove(key)

    fun clear() = map.clear()
    val size: Int get() = map.size
}
