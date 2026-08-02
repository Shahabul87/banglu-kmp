package com.banglu.engine.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
internal actual val persistenceDispatcher: CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism(1)
