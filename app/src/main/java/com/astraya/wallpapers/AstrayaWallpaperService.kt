package com.astraya.wallpapers

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Plays the chosen Astraya loop on the wallpaper surface: muted, looping, filled
 * to the screen (the loops are cut at the phone's 9:20, so nothing is lost), and
 * paused whenever the wallpaper is out of sight, so it costs nothing then.
 */
class AstrayaWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = LoopEngine()

    inner class LoopEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private var player: MediaPlayer? = null
        private var holder: SurfaceHolder? = null
        private var retries = 0
        private val prefs by lazy { Choice.prefs(this@AstrayaWallpaperService) }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            release()
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            this.holder = holder
            play()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            release()
            this.holder = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            val p = player ?: return
            if (visible) p.start() else if (p.isPlaying) p.pause()
        }

        // The app wrote a new choice: switch loops in place, no system dialog.
        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            if (key == Choice.KEY_LIVE && holder != null) {
                play()
                notifyColorsChanged()
            }
        }

        // Lets Nothing OS / Material You pick the system accent from the loop.
        override fun onComputeColors(): WallpaperColors? = runCatching {
            assets.open(Choice.live(this@AstrayaWallpaperService).poster).use {
                WallpaperColors.fromBitmap(BitmapFactory.decodeStream(it))
            }
        }.getOrNull()

        private fun play() {
            val surface = holder?.surface ?: return
            release()
            val w = Choice.live(this@AstrayaWallpaperService)
            player = MediaPlayer().apply {
                assets.openFd(w.asset).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setSurface(surface)
                isLooping = true
                setVolume(0f, 0f)
                setOnPreparedListener { mp ->
                    retries = 0
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    if (isVisible) mp.start()
                }
                // A decoder hiccup must not leave a black screen: start again, a
                // few times at most, so a file that never plays cannot spin.
                setOnErrorListener { _, _, _ -> if (retries++ < 3) play(); true }
                prepareAsync()
            }
        }

        private fun release() {
            player?.run { setOnErrorListener(null); release() }
            player = null
        }
    }
}
