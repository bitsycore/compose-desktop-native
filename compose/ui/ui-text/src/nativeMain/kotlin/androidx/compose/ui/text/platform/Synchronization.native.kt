@file:Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")
@file:JvmName("SynchronizationKt")

/*
 * Copyright 2024 The Android Open Source Project
 * (verbatim copy of upstream Synchronization.skiko.kt for K2 diag test)
 */

// VENDOR-REIMPL: compose/ui/ui-text/src/skikoMain/kotlin/androidx/compose/ui/text/platform/Synchronization.skiko.kt @ v1.12.0
// Project REIMPLEMENTATION, not a derived copy: same package + signatures,
// different body (the port replaces upstream's skiko scene/platform layer).
// Upstream edits to the counterpart do NOT need reconciling here - re-check
// only if the expect/actual SIGNATURES change.

package androidx.compose.ui.text.platform

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName

@PublishedApi
internal actual class SynchronizedObject : kotlinx.atomicfu.locks.SynchronizedObject()

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun makeSynchronizedObject(ref: Any?) = SynchronizedObject()

@PublishedApi
@Suppress("BanInlineOptIn")
@OptIn(ExperimentalContracts::class)
internal actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return kotlinx.atomicfu.locks.synchronized(lock, block)
}
