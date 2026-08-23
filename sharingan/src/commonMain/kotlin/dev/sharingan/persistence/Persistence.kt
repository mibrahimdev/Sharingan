package dev.sharingan.persistence

import dev.sharingan.Sharingan
import dev.sharingan.SharinganStore
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Hands-free persistence bootstrap. Starts the [PersistenceController] over the
 * shared [Sharingan.store] exactly once per process. Internal — the public
 * `Sharingan.configure()` entry point (and its no-op mirror) lands in a later
 * slice; until then Android's manifest-merged ContentProvider calls [start].
 */
@OptIn(ExperimentalAtomicApi::class)
internal object Persistence {
    private val started = AtomicBoolean(false)
    private var controller: PersistenceController? = null

    fun start(store: SharinganStore = Sharingan.store) {
        if (!started.compareAndSet(false, true)) return
        controller = PersistenceController(store).also { it.start() }
    }
}
