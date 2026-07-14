package com.banglu.engine.util

/** S45: JVM/Android lock; no-op on single-threaded JS. */
internal expect fun <T> runSynchronized(lock: Any, block: () -> T): T
