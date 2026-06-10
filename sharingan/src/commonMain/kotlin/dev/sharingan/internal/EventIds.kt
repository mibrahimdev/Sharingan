package dev.sharingan.internal

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/** Process-unique, monotonically increasing event ids. */
@OptIn(ExperimentalAtomicApi::class)
internal object EventIds {
    private val counter = AtomicLong(0L)

    internal fun next(prefix: String): String = prefix + counter.incrementAndFetch()
}
