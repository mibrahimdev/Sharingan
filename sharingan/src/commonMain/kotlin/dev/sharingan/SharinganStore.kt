package dev.sharingan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

/**
 * In-memory ring buffer of captured [SharinganEvent]s.
 *
 * Events are kept in insertion (chronological) order; once [capacity] is
 * reached the oldest event is evicted. The store is safe to call from any
 * thread — state transitions are atomic compare-and-set updates.
 *
 * Most apps use the shared instance behind the [Sharingan] facade rather than
 * constructing their own store.
 */
public class SharinganStore(
    /** Maximum number of events retained before the oldest is evicted. */
    public val capacity: Int = DEFAULT_CAPACITY,
) {
    private val _events = MutableStateFlow<List<SharinganEvent>>(emptyList())
    private val _isRecording = MutableStateFlow(true)

    /**
     * Internal persistence seam: invoked with each accepted event after the
     * CAS append. `null` unless the flight-recorder persistence is wired up
     * (see `dev.sharingan.db.PersistenceController`). Internal — never
     * part of the public API, so :sharingan-noop mirrors nothing here.
     */
    @Volatile
    internal var onRecord: ((SharinganEvent) -> Unit)? = null

    /**
     * Internal persistence seam: invoked from [clear] so the persistence layer
     * can drop the mirrored rows on disk too. `null` unless the flight-recorder
     * persistence is wired up (see `dev.sharingan.db.PersistenceController`).
     * Internal — never part of the public API, so :sharingan-noop mirrors
     * nothing here.
     */
    @Volatile
    internal var onClear: (() -> Unit)? = null

    /** All retained events, oldest first. */
    public val events: StateFlow<List<SharinganEvent>> = _events.asStateFlow()

    /** Whether [record] currently accepts events (the REC/PAUSED toggle). */
    public val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Appends [event], evicting the oldest entry when full. Dropped while paused. */
    public fun record(event: SharinganEvent) {
        if (!_isRecording.value) return
        _events.update { current ->
            val appended = current + event
            if (appended.size > capacity) {
                appended.subList(appended.size - capacity, appended.size).toList()
            } else {
                appended
            }
        }
        onRecord?.invoke(event)
    }

    /** Pauses (`false`) or resumes (`true`) capture. Paused events are dropped, not queued. */
    public fun setRecording(recording: Boolean) {
        _isRecording.value = recording
    }

    /** Removes all retained events. */
    public fun clear() {
        _events.value = emptyList()
        onClear?.invoke()
    }

    public companion object {
        /** Default ring-buffer size; small enough to be negligible in memory. */
        public const val DEFAULT_CAPACITY: Int = 300
    }
}
