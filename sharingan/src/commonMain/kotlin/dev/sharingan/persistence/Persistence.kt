package dev.sharingan.persistence

import dev.sharingan.Sharingan
import dev.sharingan.SharinganStore

/**
 * Hands-free persistence bootstrap. Starts the [PersistenceController] over the
 * shared [Sharingan.store] exactly once per process. Internal — the public
 * `Sharingan.configure()` entry point (and its no-op mirror) lands in a later
 * slice; until then Android's manifest-merged ContentProvider calls [start].
 */
internal object Persistence {
    private var controller: PersistenceController? = null

    fun start(store: SharinganStore = Sharingan.store) {
        if (controller != null) return
        controller = PersistenceController(store, DriverFactory().create()).also { it.start() }
    }
}
