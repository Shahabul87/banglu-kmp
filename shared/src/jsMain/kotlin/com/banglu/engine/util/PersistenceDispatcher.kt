package com.banglu.engine.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val persistenceDispatcher: CoroutineDispatcher = Dispatchers.Default
