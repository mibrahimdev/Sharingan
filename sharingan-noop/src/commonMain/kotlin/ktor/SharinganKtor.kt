package dev.sharingan.ktor

import dev.sharingan.HttpLogger
import dev.sharingan.Sharingan
import dev.sharingan.SharinganStore
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin

/** Config mirror of the real plugin; values are accepted and ignored. */
public class SharinganKtorConfig {
    public var store: SharinganStore = Sharingan.store
    public var redactedHeaders: Set<String> = HttpLogger.DEFAULT_REDACTED_HEADERS
    public var captureBodies: Boolean = true
    public var maxBodyBytes: Int = 64 * 1024
}

/**
 * No-op twin of the real `SharinganKtor` plugin: installs cleanly and adds
 * zero hooks, so release builds pay nothing on the request path.
 */
public val SharinganKtor: ClientPlugin<SharinganKtorConfig> =
    createClientPlugin("SharinganKtor", ::SharinganKtorConfig) {}
