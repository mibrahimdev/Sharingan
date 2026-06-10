package dev.sharingan.internal

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.timeIntervalSince1970

internal actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

private val clockFormatter: NSDateFormatter by lazy {
    NSDateFormatter().apply {
        dateFormat = "HH:mm:ss.SSS"
        locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    }
}

internal actual fun formatClockTime(epochMillis: Long): String =
    clockFormatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
