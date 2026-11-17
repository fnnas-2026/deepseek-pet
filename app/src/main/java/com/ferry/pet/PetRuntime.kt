package com.ferry.pet

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow

data class RuntimeState(val running: Boolean = false, val paused: Boolean = false,
                        val error: String? = null, val companions: Int = 0,
                        val modules: List<String> = emptyList())

object PetRuntime {
    val state = MutableStateFlow(RuntimeState())
    val draws = AtomicLong()
    val windowCount = AtomicInteger()
    val windowOps = AtomicLong()
    val bubbles = AtomicLong()
    val staleFrames = AtomicLong()
    val lastPhrase = AtomicReference("")
    val audioPlays = AtomicLong()
    val audioError = MutableStateFlow(false)
}
