package dev.sharingan.db

import android.content.Context

public object SharinganDbContext {
    @Volatile internal var appContext: Context? = null
    public fun install(context: Context) { appContext = context.applicationContext }
}
