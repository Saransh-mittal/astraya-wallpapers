package com.astraya.wallpapers

/** One wallpaper the app ships. Every file lives in the APK's assets. */
data class Wallpaper(
    val id: String,
    val title: String,
    /** live/<id>.mp4 for a loop, stills/<id>.jpg|png for a still. */
    val asset: String,
) {
    val isLive get() = asset.startsWith("live/")
    val poster get() = "posters/$id.jpg"
}

object Catalog {
    private fun live(id: String, title: String) = Wallpaper(id, title, "live/$id.mp4")
    private fun still(id: String, title: String, ext: String = "jpg") = Wallpaper(id, title, "stills/$id.$ext")

    /** Game footage first, then the paintings brought to life. */
    val live = listOf(
        live("astraya-live-vesper", "VESPER"),
        live("astraya-live-sword", "The Source Blade"),
        live("astraya-live-ember", "Ember Forged"),
        live("astraya-live-knight", "The Knight"),
        live("astraya-cinema-birth-lock", "The Birth"),
        live("astraya-cinema-journey", "The Journey"),
        live("astraya-cinema-shattering", "The Shattering"),
        live("astraya-cinema-ashen", "The Ashen God"),
        live("astraya-cinema-warden", "The Iron Warden"),
        live("astraya-cinema-awakening", "Awakening"),
    )

    /** With the wordmark, placed clear of the clock and the fingerprint. */
    val lock = listOf(
        still("astraya-birth-lock", "The Birth"),
        still("astraya-dots-birth-lock", "Nothing edition", "png"),
        still("astraya-ashen-lock", "The Ashen God"),
        still("astraya-journey-lock", "The Journey"),
        still("astraya-vesper-lock", "VESPER"),
        still("astraya-awakening-lock", "Awakening"),
        still("astraya-shattering-lock", "The Shattering"),
        still("astraya-warden-lock", "The Iron Warden"),
        still("astraya-ember-lock", "Ember Forged"),
        still("astraya-crimson-lock", "The Crimson Blade"),
    )

    /** Clean, nothing under the app icons. */
    val home = listOf(
        still("astraya-birth", "The Birth"),
        still("astraya-dots-birth", "Nothing edition", "png"),
        still("astraya-vesper", "VESPER"),
        still("astraya-journey", "The Journey"),
        still("astraya-awakening", "Awakening"),
        still("astraya-shattering", "The Shattering"),
        still("astraya-warden", "The Iron Warden"),
        still("astraya-ember", "Ember Forged"),
        still("astraya-crimson", "The Crimson Blade"),
        still("astraya-ashen", "The Ashen God"),
    )

    /** What "Set up everything" applies: a living home, a still lock screen. */
    val setupLive = live.first()
    val setupLock = lock.first()

    fun byId(id: String?) = live.firstOrNull { it.id == id }
}
