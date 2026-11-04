package com.astraya.wallpapers

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The gallery. "Set up everything" gives a living home screen and a still lock
 * screen with one system tap; each tile sets just that wallpaper.
 */
class MainActivity : Activity() {

    private val wm by lazy { WallpaperManager.getInstance(this) }
    private val ours by lazy { ComponentName(this, AstrayaWallpaperService::class.java) }
    private lateinit var status: TextView
    private val liveTiles = mutableMapOf<String, View>()

    // Brand colours, as in the game's emblem (brand/astraya-emblem.svg).
    private val bg = Color.parseColor("#0A0A0C")
    private val ink = Color.parseColor("#ECE0C3")
    private val gold = Color.parseColor("#C9AD77")
    private val muted = Color.parseColor("#9B927F")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(40))
        }
        col.addView(ImageView(this).apply {
            setImageResource(R.drawable.wordmark)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)))

        status = text("", 14f, muted).also { it.setPadding(0, dp(16), 0, dp(12)) }
        col.addView(status)

        col.addView(Button(this).apply {
            text = "Set up everything"
            isAllCaps = false
            textSize = 17f
            setTextColor(bg)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { setColor(gold); cornerRadius = dp(14).toFloat() }
            setOnClickListener { setUp() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        col.addView(text(
            "Home screen: VESPER, moving. Lock screen: The Birth, still, with the wordmark. " +
                "Android asks you once to confirm the live wallpaper: tap Set, then Home screen.",
            13f, muted,
        ).also { it.setPadding(0, dp(10), 0, dp(8)) })

        section(col, "Live wallpapers", "Tap to play it. Keep this app installed: the loop plays from it.")
        grid(col, Catalog.live) { applyLive(it) }
        section(col, "Lock screen", "Stills with the ASTRAYA wordmark, placed clear of the clock.")
        grid(col, Catalog.lock) { askStill(it, lock = true) }
        section(col, "Home screen", "Clean stills. These replace a live home wallpaper.")
        grid(col, Catalog.home) { askStill(it, lock = false) }
        col.addView(text(
            "Stills stay even if you uninstall this app. You can delete the downloaded APK file any time.",
            12f, muted,
        ).also { it.setPadding(0, dp(28), 0, 0) })

        // Android 15+ draws every app edge to edge: keep the content clear of the
        // status bar and the gesture bar.
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); fitsSystemWindows = true; addView(col) })
    }

    override fun onResume() {
        super.onResume()
        // Back from the system's "Set wallpaper" screen during the setup.
        if (Choice.setupPending(this)) {
            Choice.setSetupPending(this, false)
            if (homeIsOurs()) finishSetup()
            else toast("The live wallpaper wasn't set. Tap Set up everything to try again.")
        }
        refresh()
    }

    // --- actions -------------------------------------------------------------

    private fun setUp() {
        Choice.setLive(this, Catalog.setupLive)
        if (homeIsOurs()) { finishSetup(); return }
        Choice.setSetupPending(this, true)
        openLivePicker()
    }

    /** The lock still goes on AFTER the live wallpaper, so it wins on the lock screen. */
    private fun finishSetup() = setStill(Catalog.setupLock, WallpaperManager.FLAG_LOCK) {
        AlertDialog.Builder(this)
            .setTitle("All set")
            .setMessage(
                "Home screen: ${Catalog.setupLive.title}, live.\nLock screen: ${Catalog.setupLock.title}.\n\n" +
                    "You can delete the downloaded APK file now. Keep this app: the live wallpaper plays from it."
            )
            .setPositiveButton("Done", null)
            .show()
    }

    private fun applyLive(w: Wallpaper) {
        Choice.setLive(this, w)
        if (homeIsOurs() || lockIsOurs()) { toast("${w.title} is playing"); refresh() }
        else openLivePicker()
    }

    private fun openLivePicker() {
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ours)
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun askStill(w: Wallpaper, lock: Boolean) {
        val (one, both) = if (lock) "Lock screen" to "Lock and home screens" else "Home screen" to "Home and lock screens"
        val first = if (lock) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        AlertDialog.Builder(this)
            .setTitle(w.title)
            .setItems(arrayOf(one, both)) { _, which ->
                val flags = if (which == 0) first else WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
                setStill(w, flags) { toast("${w.title} set"); refresh() }
            }
            .show()
    }

    /** Decoding a 1260x2800 still takes a moment: off the main thread. */
    private fun setStill(w: Wallpaper, flags: Int, done: () -> Unit) {
        Thread {
            val result = runCatching { assets.open(w.asset).use { wm.setStream(it, null, true, flags) } }
            runOnUiThread {
                result.onSuccess { done() }.onFailure { toast("Couldn't set ${w.title}: ${it.message}") }
            }
        }.start()
    }

    // --- state ---------------------------------------------------------------

    private fun homeIsOurs() = wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)?.component == ours
    private fun lockIsOurs() = wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component == ours

    private fun refresh() {
        val live = Choice.live(this)
        val active = homeIsOurs() || lockIsOurs()
        val where = listOfNotNull("home".takeIf { homeIsOurs() }, "lock".takeIf { lockIsOurs() }).joinToString(" and ")
        status.text = if (active) "Now playing ${live.title} on the $where screen." else "No Astraya live wallpaper set yet."
        liveTiles.forEach { (id, v) -> v.background = frame(active && id == live.id) }
    }

    // --- views ---------------------------------------------------------------

    private fun section(parent: LinearLayout, title: String, note: String) {
        parent.addView(text(title, 18f, ink, bold = true).also { it.setPadding(0, dp(28), 0, dp(2)) })
        parent.addView(text(note, 12f, muted).also { it.setPadding(0, 0, 0, dp(10)) })
    }

    private fun grid(parent: LinearLayout, items: List<Wallpaper>, onTap: (Wallpaper) -> Unit) {
        val cols = 3
        val gap = dp(10)
        val tileW = (resources.displayMetrics.widthPixels - dp(40) - gap * (cols - 1)) / cols
        items.chunked(cols).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEachIndexed { i, w ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    background = frame(false)
                    setOnClickListener { onTap(w) }
                }
                val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true }
                tile.addView(img, LinearLayout.LayoutParams(tileW - dp(6), (tileW - dp(6)) * 20 / 9))
                tile.addView(text(w.title + if (w.isLive) "  ●" else "", 12f, ink).also {
                    it.setPadding(dp(2), dp(6), 0, dp(4)); it.maxLines = 1
                })
                Thread {
                    val bmp = runCatching { assets.open(w.poster).use(BitmapFactory::decodeStream) }.getOrNull()
                    runOnUiThread { img.setImageBitmap(bmp) }
                }.start()
                if (w.isLive) liveTiles[w.id] = tile
                row.addView(tile, LinearLayout.LayoutParams(tileW, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (i > 0) marginStart = gap
                })
            }
            parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = gap
            })
        }
    }

    private fun frame(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setStroke(dp(if (selected) 2 else 1), if (selected) gold else Color.parseColor("#2A2830"))
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.START
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
