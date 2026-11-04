package com.astraya.wallpapers

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Plays the chosen Astraya loop, or the slideshow, on the wallpaper surface:
 * muted, looping, filled to the screen (everything is cut at the phone's 9:20,
 * so nothing is lost), and paused whenever the wallpaper is out of sight.
 */
class AstrayaWallpaperService : WallpaperService() {

    private val handler = Handler(Looper.getMainLooper())

    // The slideshow moves on while the screen is off, so the next wallpaper is
    // already showing when the phone is picked up, never swapped in front of you.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Choice.slideshow(context) && Choice.interval(context) == 0L) {
                Choice.advance(context, System.currentTimeMillis())
            }
        }
    }

    // The timed choices: checked once a minute, and only while the screen is on.
    private val tick = object : Runnable {
        override fun run() {
            val ctx = this@AstrayaWallpaperService
            val every = Choice.interval(ctx)
            val now = System.currentTimeMillis()
            if (Choice.slideshow(ctx) && every > 0 && getSystemService(PowerManager::class.java).isInteractive &&
                now - Choice.lastAdvance(ctx) >= every
            ) Choice.advance(ctx, now)
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        handler.postDelayed(tick, 60_000L)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine = LoopEngine()

    inner class LoopEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private var player: MediaPlayer? = null
        private var holder: SurfaceHolder? = null
        private var showing: String? = null
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
            show(force = true)
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

        // The app or the slideshow changed what should be on screen.
        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            if (key in Choice.shown) show()
        }

        // The user moved this wallpaper between the home and lock screens.
        override fun onWallpaperFlagsChanged(which: Int) = show()

        // Lets Nothing OS / Material You pick the system accent from what is showing.
        override fun onComputeColors(): WallpaperColors? = runCatching {
            assets.open(wanted().poster).use { WallpaperColors.fromBitmap(BitmapFactory.decodeStream(it)) }
        }.getOrNull()

        /**
         * Which screen this engine draws for. Set once for both screens, Android
         * runs one engine for the two; set separately, each gets its own engine.
         */
        private fun screen(): Screen = when (wallpaperFlags) {
            WallpaperManager.FLAG_LOCK -> Screen.LOCK
            WallpaperManager.FLAG_SYSTEM -> Screen.HOME
            else -> Screen.BOTH
        }

        /** A still on the lock screen alone shows its wordmark version. */
        private fun wanted(): Wallpaper {
            val screen = screen()
            val w = Choice.current(this@AstrayaWallpaperService, screen)
            return if (screen == Screen.LOCK) Catalog.forLock(w) else w
        }

        private fun show(force: Boolean = false) {
            val w = wanted()
            if (!force && w.clip == showing) return
            if (play(w)) { showing = w.clip; notifyColorsChanged() }
        }

        private fun play(w: Wallpaper): Boolean {
            val surface = holder?.surface ?: return false
            release()
            player = MediaPlayer().apply {
                assets.openFd(w.clip).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
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
                setOnErrorListener { _, _, _ -> if (retries++ < 3) show(force = true); true }
                prepareAsync()
            }
            return true
        }

        private fun release() {
            player?.run { setOnErrorListener(null); release() }
            player = null
        }
    }
}
