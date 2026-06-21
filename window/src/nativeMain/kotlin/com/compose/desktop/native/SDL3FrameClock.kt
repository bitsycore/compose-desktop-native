package com.compose.desktop.native

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.channels.Channel
import sdl3.SDL_GetTicksNS

internal class SDL3FrameClock : MonotonicFrameClock {
    private val frameCh = Channel<Long>(Channel.CONFLATED)

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        frameCh.receive()
        return onFrame(currentTimeNanos())
    }

    fun sendFrame() {
        frameCh.trySend(currentTimeNanos())
    }

    private fun currentTimeNanos(): Long = SDL_GetTicksNS().toLong()
}