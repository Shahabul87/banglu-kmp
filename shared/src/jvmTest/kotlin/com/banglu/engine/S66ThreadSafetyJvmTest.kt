package com.banglu.engine

import com.banglu.engine.util.LruCache
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S66 regression (tester round: "keyboard hangs after some time of use").
 *
 * Root cause: LruCache emulated access order by remove+re-insert, making
 * even get() a structural mutation — with zero synchronization while the IME
 * runs 7+ concurrent Dispatchers.Default conversion jobs against the shared
 * engine caches. Lost-update races let the 2000-entry word cache drift past
 * its cap for the life of the process (heap creep → GC thrash → freeze), or
 * corrupt the LinkedHashMap outright. Same class of race on userBigrams
 * (background persistence writes vs prediction reads).
 *
 * These tests hammer both structures from many threads; before the S66 lock
 * they fail intermittently with ConcurrentModificationException /
 * NoSuchElementException or a size beyond maxSize. With the lock they must
 * be deterministic.
 */
class S66ThreadSafetyJvmTest {

    @Test
    fun lruCacheSurvivesConcurrentMixedLoadAndStaysBounded() {
        val maxSize = 64
        val cache = LruCache<String, Int>(maxSize)
        val threads = 8
        val opsPerThread = 40_000
        val keySpace = 200 // > maxSize so eviction churns constantly
        val failure = AtomicReference<Throwable?>(null)
        val start = CountDownLatch(1)

        val workers = (0 until threads).map { t ->
            thread(start = true) {
                try {
                    start.await()
                    var x = t * 31 + 7
                    repeat(opsPerThread) { i ->
                        x = x * 1103515245 + 12345
                        val key = "k${(x ushr 8) % keySpace}"
                        when (i % 3) {
                            0 -> cache[key] = i
                            1 -> cache[key]
                            else -> if (i % 17 == 0) cache.remove(key) else cache[key]
                        }
                    }
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                }
            }
        }
        start.countDown()
        workers.forEach { it.join() }

        failure.get()?.let { throw AssertionError("concurrent LruCache op threw", it) }
        assertTrue(
            cache.size <= maxSize,
            "cache drifted past its cap: size=${cache.size}, maxSize=$maxSize"
        )
    }

    @Test
    fun lruCacheStillBehavesAsLruSingleThreaded() {
        val cache = LruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2
        assertEquals(1, cache["a"]) // touch a → b is now eldest
        cache["c"] = 3              // evicts b
        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
        assertEquals(2, cache.size)
    }

    @Test
    fun userBigramRecordAndPredictDoNotRaceEachOther() {
        val engine = SmartEngine()
        val prevWords = listOf("আমি", "তুমি", "সে", "আমরা")
        val nextWords = (0 until 40).map { "শব্দ$it" }
        val failure = AtomicReference<Throwable?>(null)
        val start = CountDownLatch(1)

        val writers = (0 until 4).map { t ->
            thread(start = true) {
                try {
                    start.await()
                    repeat(10_000) { i ->
                        engine.recordUserBigram(
                            prevWords[(t + i) % prevWords.size],
                            nextWords[i % nextWords.size]
                        )
                    }
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                }
            }
        }
        val readers = (0 until 4).map { t ->
            thread(start = true) {
                try {
                    start.await()
                    repeat(10_000) { i ->
                        engine.getNextWordPredictions(prevWords[(t + i) % prevWords.size], limit = 5)
                    }
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                }
            }
        }
        start.countDown()
        (writers + readers).forEach { it.join() }

        failure.get()?.let { throw AssertionError("bigram record/predict race threw", it) }
        // Sanity: recorded pairs are actually retrievable afterwards.
        repeat(3) { engine.recordUserBigram("আমি", "ভাত") }
        assertTrue(engine.getNextWordPredictions("আমি", limit = 10).isNotEmpty())
    }
}
