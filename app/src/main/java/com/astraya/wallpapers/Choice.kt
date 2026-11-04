package com.astraya.wallpapers

import android.content.Context
import android.content.SharedPreferences

/**
 * Which loop the live wallpaper plays. Kept in device-protected storage, because
 * after a restart the lock screen draws the wallpaper before the first unlock,
 * when ordinary app storage is still encrypted.
 */
object Choice {
    const val KEY_LIVE = "live"
    private const val KEY_SETUP_PENDING = "setupPending"

    fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("choice", Context.MODE_PRIVATE)

    fun live(context: Context): Wallpaper =
        Catalog.byId(prefs(context).getString(KEY_LIVE, null)) ?: Catalog.setupLive

    fun setLive(context: Context, w: Wallpaper) = prefs(context).edit().putString(KEY_LIVE, w.id).apply()

    /** Set while the system's "Set wallpaper" screen is open during the one-tap setup. */
    fun setupPending(context: Context) = prefs(context).getBoolean(KEY_SETUP_PENDING, false)
    fun setSetupPending(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SETUP_PENDING, on).apply()
}
