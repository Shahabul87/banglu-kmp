package com.banglu.engine.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * S66: dispatcher for learned-word / bigram persistence writes.
 *
 * On JVM/Android this is a single-lane IO dispatcher: AndroidStorage
 * re-parses and re-serializes the full prefs blob (500/300/800-line caps) on
 * every save, and running that on Dispatchers.Default let a burst of commits
 * occupy the same ~4-thread pool the latency-critical conversion path needs.
 * Single-lane also serializes the read-modify-write blobs naturally.
 * On JS there is no thread pool to protect — Default is fine.
 */
internal expect val persistenceDispatcher: CoroutineDispatcher
