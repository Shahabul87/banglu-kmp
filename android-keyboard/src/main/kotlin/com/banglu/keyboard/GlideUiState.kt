package com.banglu.keyboard

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.banglu.engine.glide.GlidePoint

/**
 * S163: the one object the service and the keyboard view share for glide
 * typing. The service owns the instance (stable across recompositions —
 * S94 law); the view feeds the trail and reads the flags.
 */
class GlideUiState(
    /** Gate evaluated at gesture start: switch on, field/mode eligible. */
    val enabledProvider: () -> Boolean,
    /** Called on finger-up with the armed path, in grid units. */
    val onComplete: (List<GlidePoint>) -> Unit,
) {
    /** Recent path points, grid units; the SERVICE clears after decode. */
    val trail = mutableStateListOf<GlidePoint>()

    /** True from arming until finger-up: key popups must not fire. */
    val active = mutableStateOf(false)

    /** Decode found nothing — the view flashes the trail red, then clears. */
    val failFlash = mutableStateOf(false)
}
