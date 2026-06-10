package dev.sharingan

/** Direction of an MQTT event relative to this device. */
public enum class MqttDirection {
    /** This device published a message. */
    PUBLISH,

    /** This device received a message on a subscribed topic. */
    RECEIVE,

    /** This device established a subscription. */
    SUBSCRIBE,
}

/**
 * A captured MQTT message or subscription.
 *
 * For [MqttDirection.SUBSCRIBE] events, [topic] holds the topic *filter*
 * (it may contain `+`/`#` wildcards) and [payload] is `null`.
 */
public data class MqttEvent(
    override val id: String,
    override val timestampMillis: Long,
    public val direction: MqttDirection,
    public val topic: String,
    public val qos: Int = 0,
    public val retained: Boolean = false,
    public val payload: String? = null,
    public val payloadSizeBytes: Long? = null,
    override val error: String? = null,
) : SharinganEvent
