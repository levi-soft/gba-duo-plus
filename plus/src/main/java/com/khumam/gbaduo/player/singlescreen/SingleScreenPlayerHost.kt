package com.khumam.gbaduo.player.singlescreen

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ProgressBar
import com.khumam.gbaduo.R
import com.khumam.gbaduo.companion.CompanionController
import com.khumam.gbaduo.companion.android.ComposeHostOwner
import com.khumam.gbaduo.companion.android.companionView
import com.khumam.gbaduo.player.singlescreen.SingleScreenLayout.Control
import com.khumam.gbaduo.player.singlescreen.SingleScreenLayout.ScreenMode
import kotlin.math.roundToInt

/** One Activity, one core and one live companion; resizing never reparents the GL surface. */
internal class SingleScreenPlayerHost(
    context: Context,
    gameSurface: View,
    private val onKeys: (Int) -> Unit,
    onMenu: () -> Unit,
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    private val onFastForward: (Boolean) -> Unit = {},
) : ViewGroup(context) {
    private val gameFrame = FrameLayout(context)
    private val companionFrame = FrameLayout(context)
    private val owner = ComposeHostOwner()
    private var skin = SingleScreenSkin(resources.displayMetrics.density)
    private val themePreferences = context.getSharedPreferences("single_screen_theme", Context.MODE_PRIVATE)
    internal var darkTheme = themePreferences.getBoolean("dark", false)
        private set
    private val displayPreferences = context.getSharedPreferences("single_screen_display", Context.MODE_PRIVATE)
    internal var screenMode = ScreenMode.entries.firstOrNull { it.name == displayPreferences.getString("screen_mode", null) }
        ?: if (displayPreferences.getBoolean("swapped", false)) ScreenMode.COMPANION_FIRST else ScreenMode.GAME_FIRST
        private set
    internal val screensSwapped get() = screenMode == ScreenMode.COMPANION_FIRST
    internal val companionVisible get() = companionFrame.visibility == VISIBLE
    private var companionAttached = false
    private val held = HashMap<Control, Int>()
    private val controls = Control.entries.associateWith { control ->
        TouchControlView(context, control, { keys ->
            held[control] = keys
            onKeys(held.values.fold(0) { a, b -> a or b })
        }, when (control) {
            Control.QUICK_SAVE -> onQuickSave
            Control.QUICK_LOAD -> onQuickLoad
            Control.FAST_FORWARD -> ::toggleFastForward
            Control.THEME -> ::toggleTheme
            Control.SWAP -> ::swapScreens
            else -> onMenu
        })
    }
    private val loading = label(context.getString(R.string.ss_loading_game))
    private val progress = ProgressBar(context).apply {
        translationY = -32 * resources.displayMetrics.density
        indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.LTGRAY)
    }
    private var gameAspect = 1.5f
    private var portraitTopInset = 0f
    internal var arrangement = SingleScreenLayout.arrange(1f, 1f, 1f)
        private set

    init {
        // A plain Activity has no root LifecycleOwner. WindowRecomposer looks at
        // this root, so owning only the nested ComposeView is insufficient.
        owner.attach(this)
        setWillNotDraw(false)
        keepScreenOn = true
        isMotionEventSplittingEnabled = true
        gameFrame.addView(gameSurface, FrameLayout.LayoutParams(-1, -1))
        gameFrame.addView(loading, FrameLayout.LayoutParams(-1, -1))
        val progressSize = (24 * resources.displayMetrics.density).roundToInt()
        gameFrame.addView(progress, FrameLayout.LayoutParams(progressSize, progressSize, Gravity.CENTER))
        companionFrame.addView(label(context.getString(R.string.ss_loading_companion)), FrameLayout.LayoutParams(-1, -1))
        addView(gameFrame)
        addView(companionFrame)
        controls.forEach { (control, view) -> view.isEnabled = control in listOf(Control.MENU, Control.THEME, Control.SWAP); addView(view) }
        applyScreenMode()
        applyTheme()
    }

    fun setGameAspect(ratio: Float) {
        if (ratio > 0 && ratio.isFinite() && ratio != gameAspect) { gameAspect = ratio; requestLayout() }
    }

    fun gameLoaded() {
        loading.visibility = GONE
        progress.visibility = GONE
        controls.values.forEach { it.isEnabled = true }
    }

    fun showCompanion(controller: CompanionController) {
        check(!companionAttached)
        companionFrame.removeAllViews()
        companionFrame.addView(companionView(context, owner, controller), FrameLayout.LayoutParams(-1, -1))
        companionAttached = true
    }

    fun releaseControls() {
        controls.values.forEach { it.release() }
        held.clear()
        onKeys(0)
        controls[Control.FAST_FORWARD]?.let {
            if (it.isActivated) { it.isActivated = false; it.invalidate(); onFastForward(false) }
        }
    }

    private fun toggleFastForward() {
        controls.getValue(Control.FAST_FORWARD).let {
            it.isActivated = !it.isActivated
            it.invalidate()
            onFastForward(it.isActivated)
        }
    }

    private fun toggleTheme() {
        darkTheme = !darkTheme
        themePreferences.edit().putBoolean("dark", darkTheme).apply()
        applyTheme()
    }

    private fun swapScreens() {
        releaseControls()
        screenMode = screenMode.next()
        displayPreferences.edit().putString("screen_mode", screenMode.name).putBoolean("swapped", screensSwapped).apply()
        applyScreenMode()
        requestLayout()
    }

    private fun applyScreenMode() {
        companionFrame.visibility = if (screenMode == ScreenMode.GAME_ONLY) GONE else VISIBLE
        controls.getValue(Control.SWAP).contentDescription = when (screenMode) {
            ScreenMode.GAME_FIRST -> "Game first. Swap to companion first"
            ScreenMode.COMPANION_FIRST -> "Companion first. Swap to game only"
            ScreenMode.GAME_ONLY -> "Game only. Swap to game and companion"
        }
    }

    private fun applyTheme() {
        skin.darkTheme = darkTheme
        controls.values.forEach { it.darkTheme = darkTheme }
        invalidate()
    }

    fun close() {
        releaseControls()
        owner.destroy()
    }

    internal fun controlView(control: Control): TouchControlView = controls.getValue(control)

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        setPadding(0, 0, 0, 0)
        val cutoutTop = insets.displayCutout?.safeInsetTop?.toFloat() ?: 0f
        if (cutoutTop != portraitTopInset) {
            portraitTopInset = cutoutTop
            requestLayout()
        }
        if (insets.isVisible(WindowInsets.Type.systemBars())) post {
            val activity = context as? android.app.Activity
            if (activity != null && !activity.isDestroyed && activity.hasWindowFocus()) SingleScreenWindows.hideBars(activity)
        }
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        releaseControls()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        skin = SingleScreenSkin(resources.displayMetrics.density)
        applyTheme()
        releaseControls()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        arrangement = SingleScreenLayout.arrange(w.toFloat(), h.toFloat(), resources.displayMetrics.density,
            gameAspect, portraitTopInset, screensSwapped, gameOnly = screenMode == ScreenMode.GAME_ONLY)
        fun measure(view: View, rect: SingleScreenLayout.Rect) = view.measure(
            MeasureSpec.makeMeasureSpec(rect.width.roundToInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(rect.height.roundToInt(), MeasureSpec.EXACTLY))
        measure(gameFrame, arrangement.game)
        // Keep the hidden companion's last viewport and composition intact.
        if (companionVisible) measure(companionFrame, arrangement.companion)
        controls.forEach { (control, view) -> measure(view, arrangement.controls.getValue(control)) }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        fun place(view: View, rect: SingleScreenLayout.Rect) {
            val x = paddingLeft + rect.x.roundToInt()
            val y = paddingTop + rect.y.roundToInt()
            view.layout(x, y, x + view.measuredWidth, y + view.measuredHeight)
        }
        place(gameFrame, arrangement.game)
        if (companionVisible) place(companionFrame, arrangement.companion)
        controls.forEach { (control, view) -> place(view, arrangement.controls.getValue(control)) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        skin.draw(canvas, arrangement)
    }

    private fun label(value: String) = TextView(context).apply {
        text = value
        setBackgroundColor(Color.rgb(27, 32, 34))
        setTextColor(Color.LTGRAY)
        gravity = Gravity.CENTER
        textSize = 14f
    }
}
