package com.banglu.engine.util

internal actual fun <T> runSynchronized(lock: Any, block: () -> T): T = synchronized(lock, block)
