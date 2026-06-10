package dev.sharingan

import dev.sharingan.internal.EventIds
import dev.sharingan.internal.currentTimeMillis

/**
 * Records [MqttEvent]s into a [SharinganStore].
 *
 * Sharingan is client-agnostic for MQTT: call these methods from your MQTT
 * client's callbacks (one line per hook). See the project README for adapter
 * recipes.
 */
public class MqttLogger(private val store: SharinganStore) {

    /** Records an outgoing publish. */
    public fun publish(
        topic: String,
        payload: String?,
        qos: Int = 0,
        retained: Boolean = false,
        error: String? = null,
    ) {
        record(MqttDirection.PUBLISH, topic, payload, qos, retained, error)
    }

    /** Records a message received on a subscribed topic. */
    public fun received(
        topic: String,
        payload: String?,
        qos: Int = 0,
        retained: Boolean = false,
    ) {
        record(MqttDirection.RECEIVE, topic, payload, qos, retained, error = null)
    }

    /** Records an established subscription. [topicFilter] may contain wildcards. */
    public fun subscribed(topicFilter: String, qos: Int = 0) {
        record(MqttDirection.SUBSCRIBE, topicFilter, payload = null, qos = qos, retained = false, error = null)
    }

    private fun record(
        direction: MqttDirection,
        topic: String,
        payload: String?,
        qos: Int,
        retained: Boolean,
        error: String?,
    ) {
        store.record(
            MqttEvent(
                id = EventIds.next("mqtt-"),
                timestampMillis = currentTimeMillis(),
                direction = direction,
                topic = topic,
                qos = qos,
                retained = retained,
                payload = payload,
                payloadSizeBytes = payload?.encodeToByteArray()?.size?.toLong(),
                error = error,
            )
        )
    }
}
