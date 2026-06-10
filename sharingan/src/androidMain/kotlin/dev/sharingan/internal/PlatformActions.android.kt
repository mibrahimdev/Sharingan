package dev.sharingan.internal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

internal actual fun copyToClipboard(text: String) {
    val context = SharinganAndroid.appContext ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Sharingan log", text))
}

internal actual fun shareText(text: String) {
    val context = SharinganAndroid.appContext ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Share Sharingan log").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
