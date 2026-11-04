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
 * The app, top to bottom: the one-tap slideshow, the ready-made ones, your own
 * (same on both screens, or different home and lock), then single wallpapers.
 */
class MainActivity : Activity() {

    private val wm by lazy { WallpaperManager.getInstance(this) }
    private val ours by lazy { ComponentName(this, AstrayaWallpaperService::class.java) }
    private lateinit var status: TextView
    private lateinit var homeListButton: TextView
    private lateinit var lockListButton: TextView
    private val liveTiles = mutableMapOf<String, View>()
    private val chipRows = mutableListOf<() -> Unit>()

    // Brand colours, as in the game's emblem (brand/astraya-emblem.svg).
    private val bg = Color.parseColor("#0A0A0C")
    private val ink = Color.parseColor("#ECE0C3")
    private val gold = Color.parseColor("#C9AD77")
    private val muted = Color.parseColor("#9B927F")
    private val line = Color.parseColor("#2A2830")

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
        status = text("", 14f, muted).also { it.setPadding(0, dp(16), 0, dp(14)) }
        col.addView(status)

        // 1. One tap.
        col.addView(goldButton("Set Astraya Mix") { usePreset(Catalog.presets.first()) }, full(dp(58)))
        col.addView(text(
            "Every wallpaper in turn on the home and lock screens, a new one each time the screen turns off. " +
                "Android asks you to confirm once: tap Set, then Home and lock screens.",
            13f, muted,
        ).also { it.setPadding(0, dp(10), 0, 0) })

        // 2. Ready-made slideshows.
        section(col, "Slideshows", "One tap each.")
        Catalog.presets.forEach { p -> col.addView(card(p.name, p.note) { usePreset(p) }, full().apply { bottomMargin = dp(8) }) }

        // 3. Your own.
        section(col, "Make your own", "Pick what plays, and where.")
        chips(col, listOf("Same on both screens", "Different home and lock"), { if (Choice.separate(this)) 1 else 0 }) {
            Choice.setSeparate(this, it == 1)
        }
        homeListButton = card("", "") { pickList(Screen.HOME) }.also { col.addView(it, full().apply { bottomMargin = dp(8) }) }
        lockListButton = card("", "") { pickList(Screen.LOCK) }.also { col.addView(it, full().apply { bottomMargin = dp(8) }) }
        col.addView(text("Change to the next one", 12f, muted).also { it.setPadding(0, dp(6), 0, dp(6)) })
        chips(col, Choice.intervals.map { it.second }, { Choice.intervals.indexOfFirst { it.first == Choice.interval(this) } }) {
            Choice.setInterval(this, Choice.intervals[it].first)
        }
        col.addView(goldButton("Apply my slideshow") { Choice.startSlideshow(this); apply() }, full(dp(52)).apply { topMargin = dp(6) })

        // 4. Single wallpapers.
        section(col, "One moving wallpaper", "Tap to play just that one. Keep this app installed: it plays from here.")
        grid(col, Catalog.live) { playOne(it) }
        section(col, "Lock screen stills", "With the ASTRAYA wordmark, clear of the clock.")
        grid(col, Catalog.lock) { askStill(it, lock = true) }
        section(col, "Home screen stills", "Clean. These replace a moving home screen.")
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
        continueApplying()
        refresh()
    }

    // --- applying -------------------------------------------------------------

    private fun usePreset(p: Preset) {
        Choice.usePreset(this, p)
        apply()
    }

    /**
     * Puts the wallpaper where the current choice needs it. The same slideshow on
     * both screens is one system tap; different home and lock is two, because
     * Android only gives each screen its own copy when they are set one by one.
     */
    private fun apply() {
        if (!Choice.separate(this) || !Choice.slideshow(this)) {
            if (homeIsOurs() && lockIsOurs()) { done(); return }
            Choice.setPending(this, "both")
            toast("Tap Set, then Home and lock screens")
        } else {
            Choice.setPending(this, "home")
            toast("Step 1 of 2: tap Set, then Home screen")
        }
        openLivePicker()
    }

    /** Back from the system's "Set wallpaper" screen. */
    private fun continueApplying() {
        when (Choice.pending(this)) {
            "both" -> {
                Choice.setPending(this, null)
                if (homeIsOurs() || lockIsOurs()) done() else notSet()
            }
            "home" -> {
                if (!homeIsOurs()) { Choice.setPending(this, null); notSet(); return }
                Choice.setPending(this, "lock")
                AlertDialog.Builder(this)
                    .setTitle("Step 2 of 2")
                    .setMessage("Now the lock screen: tap Set, then Lock screen.")
                    .setPositiveButton("Continue") { _, _ -> openLivePicker() }
                    .setNegativeButton("Skip") { _, _ -> Choice.setPending(this, null); refresh() }
                    .setCancelable(false)
                    .show()
            }
            "lock" -> {
                Choice.setPending(this, null)
                if (lockIsOurs()) done() else notSet()
            }
        }
    }

    private fun done() {
        refresh()
        AlertDialog.Builder(this)
            .setTitle("All set")
            .setMessage(status.text.toString() + "\n\nYou can delete the downloaded APK file now. Keep this app: the wallpaper plays from it.")
            .setPositiveButton("Done", null)
            .show()
    }

    private fun notSet() = toast("The wallpaper wasn't set. Tap the button again to retry.")

    private fun playOne(w: Wallpaper) {
        Choice.setLive(this, w)
        if (homeIsOurs() || lockIsOurs()) { toast("${w.title} is playing"); refresh() }
        else { Choice.setPending(this, "both"); toast("Tap Set, then pick the screens"); openLivePicker() }
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

    /** Tick exactly what goes in a slideshow. */
    private fun pickList(screen: Screen) {
        val all = Catalog.all
        val chosen = Choice.list(this, screen).map { it.id }.toSet()
        val checked = BooleanArray(all.size) { all[it].id in chosen }
        val names = all.map { it.title + if (it.isLive) "  · moving" else "  · still" }.toTypedArray()
        val title = when {
            !Choice.separate(this) -> "Both screens"
            screen == Screen.LOCK -> "Lock screen"
            else -> "Home screen"
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Save") { _, _ ->
                val list = all.filterIndexed { i, _ -> checked[i] }
                if (list.isEmpty()) toast("Pick at least one") else { Choice.setList(this, screen, list); refresh() }
            }
            .setNeutralButton("All") { _, _ -> Choice.setList(this, screen, all); refresh() }
            .setNegativeButton("Cancel", null)
            .show()
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

    // --- state ----------------------------------------------------------------

    private fun homeIsOurs() = wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)?.component == ours
    private fun lockIsOurs() = wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component == ours

    private fun refresh() {
        val separate = Choice.separate(this)
        val slideshow = Choice.slideshow(this)
        val next = Choice.intervals.first { it.first == Choice.interval(this) }.let {
            if (it.first == 0L) "a new one each time the screen turns off" else it.second.lowercase()
        }
        val home = homeIsOurs()
        val lock = lockIsOurs()
        status.text = when {
            !home && !lock -> "No Astraya wallpaper playing yet."
            !slideshow -> "Playing ${Choice.live(this).title} on the ${where(home, lock)} screen."
            separate -> "Slideshow: home ${Choice.list(this, Screen.HOME).size}, lock ${Choice.list(this, Screen.LOCK).size} wallpapers, $next."
            else -> "Slideshow on the ${where(home, lock)} screen: ${Choice.list(this, Screen.HOME).size} wallpapers, $next."
        }
        setCard(homeListButton, if (separate) "Home screen" else "Both screens", summary(Screen.HOME))
        setCard(lockListButton, "Lock screen", summary(Screen.LOCK))
        lockListButton.visibility = if (separate) View.VISIBLE else View.GONE
        val live = Choice.live(this)
        liveTiles.forEach { (id, v) -> v.background = frame((home || lock) && !slideshow && id == live.id) }
        chipRows.forEach { it() }
    }

    private fun where(home: Boolean, lock: Boolean) = listOfNotNull("home".takeIf { home }, "lock".takeIf { lock }).joinToString(" and ")

    private fun summary(screen: Screen): String {
        val list = Choice.list(this, screen)
        val moving = list.count { it.isLive }
        return "${list.size} chosen: $moving moving, ${list.size - moving} still. Tap to change."
    }

    // --- views ----------------------------------------------------------------

    private fun section(parent: LinearLayout, title: String, note: String) {
        parent.addView(text(title, 18f, ink, bold = true).also { it.setPadding(0, dp(30), 0, dp(2)) })
        parent.addView(text(note, 12f, muted).also { it.setPadding(0, 0, 0, dp(10)) })
    }

    /** A tappable row: a title line and a note line. */
    private fun card(title: String, note: String, onTap: () -> Unit) = TextView(this).apply {
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = frame(false)
        setOnClickListener { onTap() }
        setCard(this, title, note)
    }

    private fun setCard(v: TextView, title: String, note: String) {
        val s = android.text.SpannableStringBuilder(title).apply {
            setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, length, 0)
            setSpan(android.text.style.ForegroundColorSpan(ink), 0, length, 0)
            val start = length
            append("\n").append(note)
            setSpan(android.text.style.ForegroundColorSpan(muted), start, length, 0)
            setSpan(android.text.style.RelativeSizeSpan(0.85f), start, length, 0)
        }
        v.text = s
        v.textSize = 15f
    }

    /** A row of choices; the selected one is outlined in gold. */
    private fun chips(parent: LinearLayout, labels: List<String>, selected: () -> Int, onPick: (Int) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = labels.mapIndexed { i, label ->
            TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener { onPick(i); refresh() }
            }.also {
                row.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = dp(8)
                })
            }
        }
        chipRows += {
            val sel = selected()
            buttons.forEachIndexed { i, b -> b.background = frame(i == sel); b.setTextColor(if (i == sel) gold else ink) }
        }
        parent.addView(row, full().apply { bottomMargin = dp(8) })
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
                val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                tile.addView(img, LinearLayout.LayoutParams(tileW - dp(6), (tileW - dp(6)) * 20 / 9))
                tile.addView(text(w.title, 12f, ink).also { it.setPadding(dp(2), dp(6), 0, dp(4)); it.maxLines = 1 })
                Thread {
                    val bmp = runCatching { assets.open(w.poster).use(BitmapFactory::decodeStream) }.getOrNull()
                    runOnUiThread { img.setImageBitmap(bmp) }
                }.start()
                if (w.isLive) liveTiles[w.id] = tile
                row.addView(tile, LinearLayout.LayoutParams(tileW, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (i > 0) marginStart = gap
                })
            }
            parent.addView(row, full().apply { bottomMargin = gap })
        }
    }

    private fun goldButton(label: String, onTap: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 17f
        setTextColor(bg)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { setColor(gold); cornerRadius = dp(14).toFloat() }
        setOnClickListener { onTap() }
    }

    private fun frame(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setStroke(dp(if (selected) 2 else 1), if (selected) gold else line)
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.START
    }

    private fun full(height: Int = LinearLayout.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
