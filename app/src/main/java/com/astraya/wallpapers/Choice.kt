package com.astraya.wallpapers

import android.content.Context
import android.content.SharedPreferences

/** Which screen a wallpaper engine is drawing for. */
enum class Screen { HOME, LOCK, BOTH }

/**
 * What the live wallpaper plays: one chosen loop, or a slideshow — the same on
 * both screens, or one for home and another for lock. Kept in device-protected
 * storage, because after a restart the lock screen draws the wallpaper before the
 * first unlock, when ordinary app storage is still encrypted.
 */
object Choice {
    const val KEY_LIVE = "live"
    const val KEY_SLIDESHOW = "slideshow"
    const val KEY_SEPARATE = "separate"
    const val KEY_HOME_LIST = "homeList"
    const val KEY_LOCK_LIST = "lockList"
    const val KEY_INDEX_HOME = "indexHome"
    const val KEY_INDEX_LOCK = "indexLock"
    const val KEY_INTERVAL = "interval"
    private const val KEY_LAST_ADVANCE = "lastAdvance"
    private const val KEY_PENDING = "pending"

    /** The keys that change what an engine should be showing. */
    val shown = setOf(KEY_LIVE, KEY_SLIDESHOW, KEY_SEPARATE, KEY_HOME_LIST, KEY_LOCK_LIST, KEY_INDEX_HOME, KEY_INDEX_LOCK)

    /** When the slideshow moves on. 0 = each time the screen turns off. */
    val intervals = listOf(0L to "Screen off", 10 * 60_000L to "Every 10 min", 60 * 60_000L to "Every hour")

    fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("choice", Context.MODE_PRIVATE)

    // --- one loop -------------------------------------------------------------

    fun live(context: Context): Wallpaper =
        Catalog.byId(prefs(context).getString(KEY_LIVE, null)) ?: Catalog.live.first()

    /** Playing one loop ends the slideshow. */
    fun setLive(context: Context, w: Wallpaper) =
        prefs(context).edit().putString(KEY_LIVE, w.id).putBoolean(KEY_SLIDESHOW, false).apply()

    // --- slideshow ------------------------------------------------------------

    fun slideshow(context: Context) = prefs(context).getBoolean(KEY_SLIDESHOW, false)
    fun separate(context: Context) = prefs(context).getBoolean(KEY_SEPARATE, false)
    fun setSeparate(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SEPARATE, on).apply()

    /** HOME is also the list used on both screens when they are the same. */
    fun list(context: Context, screen: Screen): List<Wallpaper> {
        val key = if (screen == Screen.LOCK) KEY_LOCK_LIST else KEY_HOME_LIST
        val ids = prefs(context).getString(key, null)?.split(',')
        return ids?.mapNotNull(Catalog::byAnyId)?.takeIf { it.isNotEmpty() } ?: Catalog.presets.first().home
    }

    fun setList(context: Context, screen: Screen, list: List<Wallpaper>) = prefs(context).edit()
        .putString(if (screen == Screen.LOCK) KEY_LOCK_LIST else KEY_HOME_LIST, list.joinToString(",") { it.id })
        .apply()

    fun interval(context: Context) = prefs(context).getLong(KEY_INTERVAL, 0L)
    fun setInterval(context: Context, ms: Long) = prefs(context).edit().putLong(KEY_INTERVAL, ms).apply()

    /** Turns the slideshow on with a ready-made set. */
    fun usePreset(context: Context, p: Preset) {
        prefs(context).edit()
            .putBoolean(KEY_SLIDESHOW, true)
            .putBoolean(KEY_SEPARATE, p.lock != null)
            .putString(KEY_HOME_LIST, p.home.joinToString(",") { it.id })
            .putString(KEY_LOCK_LIST, (p.lock ?: p.home).joinToString(",") { it.id })
            .apply()
    }

    fun startSlideshow(context: Context) = prefs(context).edit().putBoolean(KEY_SLIDESHOW, true).apply()

    /** The wallpaper an engine drawing for [screen] should show now. */
    fun current(context: Context, screen: Screen): Wallpaper {
        if (!slideshow(context)) return live(context)
        val lockList = screen == Screen.LOCK && separate(context)
        val list = list(context, if (lockList) Screen.LOCK else Screen.HOME)
        val i = prefs(context).getInt(if (lockList) KEY_INDEX_LOCK else KEY_INDEX_HOME, 0)
        return list[Math.floorMod(i, list.size)]
    }

    /**
     * Moves both slideshows on by one. The home and lock screens can each run their
     * own copy of the wallpaper service, and both hear the screen turn off, so a
     * second call within a few seconds of the first is ignored.
     */
    fun advance(context: Context, now: Long) {
        val p = prefs(context)
        if (now - p.getLong(KEY_LAST_ADVANCE, 0L) < 3_000L) return
        p.edit()
            .putInt(KEY_INDEX_HOME, p.getInt(KEY_INDEX_HOME, 0) + 1)
            .putInt(KEY_INDEX_LOCK, p.getInt(KEY_INDEX_LOCK, 0) + 1)
            .putLong(KEY_LAST_ADVANCE, now)
            .apply()
    }

    fun lastAdvance(context: Context) = prefs(context).getLong(KEY_LAST_ADVANCE, 0L)

    // --- the system "Set wallpaper" screen ------------------------------------

    /** Which step of applying is waiting on the system screen: "both", "home", "lock" or null. */
    fun pending(context: Context) = prefs(context).getString(KEY_PENDING, null)
    fun setPending(context: Context, step: String?) = prefs(context).edit().putString(KEY_PENDING, step).apply()
}
