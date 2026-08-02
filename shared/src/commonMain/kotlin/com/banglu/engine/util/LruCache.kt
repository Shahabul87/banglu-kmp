package com.banglu.engine.util

/**
 * S45: multiplatform LRU map replacing JVM-only access-ordered LinkedHashMap
 * (removeEldestEntry is not available on Kotlin/JS). Common LinkedHashMap
 * preserves insertion order on every target; access order is emulated by
 * re-inserting on get. Same bounded-memo semantics the engine caches had.
 *
 * S66: every method takes the instance lock. Access-order emulation makes
 * even [get] a structural mutation (remove + re-insert), and on Android the
 * engine caches are hit from several concurrent Dispatchers.Default
 * conversion jobs at once — unsynchronized, lost-update races let the map
 * drift past [maxSize] for the life of the process (observed as progressive
 * heap growth → GC thrash → the "keyboard hangs after a while" reports).
 * On Kotlin/JS runSynchronized is a no-op, so single-threaded JS pays nothing.
 */
class LruCache<K, V>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()

    operator fun get(key: K): V? = runSynchronized(this) {
        val value = map.remove(key) ?: return@runSynchronized null
        map[key] = value // re-insert = most recently used
        value
    }

    operator fun set(key: K, value: V): Unit = runSynchronized(this) {
        map.remove(key)
        map[key] = value
        if (map.size > maxSize) {
            val eldest = map.keys.first()
            map.remove(eldest)
        }
    }

    fun remove(key: K): V? = runSynchronized(this) { map.remove(key) }

    fun clear(): Unit = runSynchronized(this) { map.clear() }

    val size: Int get() = runSynchronized(this) { map.size }
}
