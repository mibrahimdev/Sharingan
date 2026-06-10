package dev.sharingan.internal

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * Holds the application context Sharingan needs for the clipboard, the share
 * chooser and the capture notification. Populated automatically by
 * [SharinganInitProvider] before `Application.onCreate` — no setup call.
 */
@SuppressLint("StaticFieldLeak") // application context only — safe to hold
internal object SharinganAndroid {
    @Volatile
    var appContext: Context? = null
}

/**
 * Zero-setup initializer: manifest-merged ContentProvider that captures the
 * application context at process start (the same trick androidx.startup uses,
 * without adding the dependency).
 */
public class SharinganInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        SharinganAndroid.appContext = context?.applicationContext
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
