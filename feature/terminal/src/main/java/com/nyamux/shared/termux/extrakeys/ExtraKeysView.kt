package com.nyamux.shared.termux.extrakeys

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.PopupWindow
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.google.android.material.button.MaterialButton
import com.codeforge.feature.terminal.R
import com.nyamux.shared.theme.ThemeUtils
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

open class ExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GridLayout(context, attrs) {

    interface IExtraKeysView {
        fun onExtraKeyButtonClick(view: View?, buttonInfo: ExtraKeyButton, button: MaterialButton?)
        fun performExtraKeyButtonHapticFeedback(view: View?, buttonInfo: ExtraKeyButton, button: MaterialButton?): Boolean
    }

    @JvmField
    protected var mExtraKeysViewClient: IExtraKeysView? = null

    @JvmField
    protected var mSpecialButtons: Map<SpecialButton, SpecialButtonState>? = null

    @JvmField
    protected var mSpecialButtonsKeys: Set<String>? = null

    @JvmField
    protected var mRepetitiveKeys: List<String>? = null

    @JvmField
    protected var mButtonTextColor: Int = 0

    @JvmField
    protected var mButtonActiveTextColor: Int = 0

    @JvmField
    protected var mButtonBackgroundColor: Int = 0

    @JvmField
    protected var mButtonActiveBackgroundColor: Int = 0

    @JvmField
    protected var mButtonTextAllCaps: Boolean = true

    @JvmField
    protected var mLongPressTimeout: Int = 0

    @JvmField
    protected var mLongPressRepeatDelay: Int = 0

    @JvmField
    protected var mPopupWindow: PopupWindow? = null

    @JvmField
    protected var mScheduledExecutor: ScheduledExecutorService? = null

    @JvmField
    protected var mHandler: Handler? = null

    @JvmField
    protected var mSpecialButtonsLongHoldRunnable: SpecialButtonsLongHoldRunnable? = null

    @JvmField
    protected var mLongPressCount: Int = 0

    open var extraKeysViewClient: IExtraKeysView?
        get() = mExtraKeysViewClient
        set(value) {
            mExtraKeysViewClient = value
        }

    open var repetitiveKeys: List<String>?
        get() {
            val keys = mRepetitiveKeys ?: return null
            return keys.stream().map { java.lang.String(it) as String }.collect(Collectors.toList())
        }
        set(value) {
            mRepetitiveKeys = value
        }

    open var specialButtons: Map<SpecialButton, SpecialButtonState>?
        get() = mSpecialButtons?.toMap()
        set(value) {
            mSpecialButtons = value
            mSpecialButtonsKeys = value?.keys?.stream()?.map { it.key }?.collect(Collectors.toSet())
        }

    open val specialButtonsKeys: Set<String>?
        get() {
            val keys = mSpecialButtonsKeys ?: return null
            return keys.stream().map { java.lang.String(it) as String }.collect(Collectors.toSet())
        }

    open var buttonTextColor: Int
        get() = mButtonTextColor
        set(value) {
            mButtonTextColor = value
        }

    open var buttonActiveTextColor: Int
        get() = mButtonActiveTextColor
        set(value) {
            mButtonActiveTextColor = value
        }

    open var buttonBackgroundColor: Int
        get() = mButtonBackgroundColor
        set(value) {
            mButtonBackgroundColor = value
        }

    open var buttonActiveBackgroundColor: Int
        get() = mButtonActiveBackgroundColor
        set(value) {
            mButtonActiveBackgroundColor = value
        }

    open var buttonTextAllCaps: Boolean
        get() = mButtonTextAllCaps
        set(value) {
            mButtonTextAllCaps = value
        }

    open var longPressTimeout: Int
        get() = mLongPressTimeout
        set(value) {
            if (value in MIN_LONG_PRESS_DURATION..MAX_LONG_PRESS_DURATION) {
                mLongPressTimeout = value
            } else {
                mLongPressTimeout = FALLBACK_LONG_PRESS_DURATION
            }
        }

    open var longPressRepeatDelay: Int
        get() = mLongPressRepeatDelay
        set(value) {
            if (value in MIN_LONG_PRESS__REPEAT_DELAY..MAX_LONG_PRESS__REPEAT_DELAY) {
                mLongPressRepeatDelay = value
            } else {
                mLongPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY
            }
        }

    init {
        repetitiveKeys = ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS
        specialButtons = getDefaultSpecialButtons(this)

        setButtonColors(
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_TEXT_COLOR, DEFAULT_BUTTON_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_TEXT_COLOR, DEFAULT_BUTTON_ACTIVE_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_BACKGROUND_COLOR, DEFAULT_BUTTON_BACKGROUND_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR, DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR)
        )

        longPressTimeout = ViewConfiguration.getLongPressTimeout()
        longPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY
    }

    open fun setButtonColors(buttonTextColor: Int, buttonActiveTextColor: Int, buttonBackgroundColor: Int, buttonActiveBackgroundColor: Int) {
        mButtonTextColor = buttonTextColor
        mButtonActiveTextColor = buttonActiveTextColor
        mButtonBackgroundColor = buttonBackgroundColor
        mButtonActiveBackgroundColor = buttonActiveBackgroundColor
    }

    open fun getDefaultSpecialButtons(extraKeysView: ExtraKeysView): Map<SpecialButton, SpecialButtonState> {
        val map = HashMap<SpecialButton, SpecialButtonState>()
        map[SpecialButton.CTRL] = SpecialButtonState(extraKeysView)
        map[SpecialButton.ALT] = SpecialButtonState(extraKeysView)
        map[SpecialButton.SHIFT] = SpecialButtonState(extraKeysView)
        map[SpecialButton.FN] = SpecialButtonState(extraKeysView)
        return map
    }

    private fun getThemedContext(): Context {
        return androidx.appcompat.view.ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    open fun reload(extraKeysInfo: ExtraKeysInfo?, heightPx: Float) {
        if (extraKeysInfo == null) return

        mSpecialButtons?.values?.forEach { state ->
            state.buttons = ArrayList()
        }

        removeAllViews()

        val buttons = extraKeysInfo.matrix

        rowCount = buttons.size
        columnCount = maximumLength(buttons as Array<Array<*>>)

        for (row in buttons.indices) {
            for (col in buttons[row].indices) {
                val buttonInfo = buttons[row][col]

                val button: MaterialButton
                if (isSpecialButton(buttonInfo)) {
                    button = createSpecialButton(buttonInfo.key, true) ?: return
                } else {
                    button = MaterialButton(getThemedContext(), null, android.R.attr.buttonBarButtonStyle)
                }

                button.text = buttonInfo.display
                button.setTextColor(mButtonTextColor)
                button.isAllCaps = mButtonTextAllCaps
                button.setPadding(0, 0, 0, 0)

                button.setOnClickListener { view ->
                    performExtraKeyButtonHapticFeedback(view, buttonInfo, button)
                    onAnyExtraKeyButtonClick(view, buttonInfo, button)
                }

                button.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            view.setBackgroundColor(mButtonActiveBackgroundColor)
                            startScheduledExecutors(view, buttonInfo, button)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (buttonInfo.popup != null) {
                                if (mPopupWindow == null && event.y < 0) {
                                    stopScheduledExecutors()
                                    view.setBackgroundColor(mButtonBackgroundColor)
                                    showPopup(view, buttonInfo.popup)
                                }
                                if (mPopupWindow != null && event.y > 0) {
                                    view.setBackgroundColor(mButtonActiveBackgroundColor)
                                    dismissPopup()
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            view.setBackgroundColor(mButtonBackgroundColor)
                            stopScheduledExecutors()
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            view.setBackgroundColor(mButtonBackgroundColor)
                            stopScheduledExecutors()
                            if (mLongPressCount == 0 || mPopupWindow != null) {
                                if (mPopupWindow != null) {
                                    dismissPopup()
                                    if (buttonInfo.popup != null) {
                                        onAnyExtraKeyButtonClick(view, buttonInfo.popup, button)
                                    }
                                } else {
                                    view.performClick()
                                }
                            }
                            true
                        }

                        else -> true
                    }
                }

                val param = LayoutParams()
                param.width = 0
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                    param.height = (heightPx + 0.5f).toInt()
                } else {
                    param.height = 0
                }
                param.setMargins(0, 0, 0, 0)
                param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1f)
                param.rowSpec = GridLayout.spec(row, GridLayout.FILL, 1f)
                button.layoutParams = param

                addView(button)
            }
        }
    }

    open fun onExtraKeyButtonClick(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        mExtraKeysViewClient?.onExtraKeyButtonClick(view, buttonInfo, button)
    }

    open fun performExtraKeyButtonHapticFeedback(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        if (mExtraKeysViewClient != null) {
            if (mExtraKeysViewClient!!.performExtraKeyButtonHapticFeedback(view, buttonInfo, button)) return
        }

        if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {
            if (Build.VERSION.SDK_INT >= 28) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                if (Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }
    }

    open fun onAnyExtraKeyButtonClick(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        if (isSpecialButton(buttonInfo)) {
            if (mLongPressCount > 0) return
            val state = mSpecialButtons?.get(SpecialButton.valueOf(buttonInfo.key)) ?: return

            state.setIsActive(!state.isActive)
            if (!state.isActive) state.setIsLocked(false)
        } else {
            onExtraKeyButtonClick(view, buttonInfo, button)
        }
    }

    open fun startScheduledExecutors(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
        stopScheduledExecutors()
        mLongPressCount = 0
        if (mRepetitiveKeys != null && mRepetitiveKeys!!.contains(buttonInfo.key)) {
            mScheduledExecutor = Executors.newSingleThreadScheduledExecutor()
            mScheduledExecutor!!.scheduleWithFixedDelay({
                mLongPressCount++
                onExtraKeyButtonClick(view, buttonInfo, button)
            }, mLongPressTimeout.toLong(), mLongPressRepeatDelay.toLong(), TimeUnit.MILLISECONDS)
        } else if (isSpecialButton(buttonInfo)) {
            val state = mSpecialButtons?.get(SpecialButton.valueOf(buttonInfo.key)) ?: return
            if (mHandler == null) mHandler = Handler(Looper.getMainLooper())
            mSpecialButtonsLongHoldRunnable = SpecialButtonsLongHoldRunnable(state)
            mHandler!!.postDelayed(mSpecialButtonsLongHoldRunnable!!, mLongPressTimeout.toLong())
        }
    }

    open fun stopScheduledExecutors() {
        if (mScheduledExecutor != null) {
            mScheduledExecutor!!.shutdownNow()
            mScheduledExecutor = null
        }

        if (mSpecialButtonsLongHoldRunnable != null && mHandler != null) {
            mHandler!!.removeCallbacks(mSpecialButtonsLongHoldRunnable!!)
            mSpecialButtonsLongHoldRunnable = null
        }
    }

    inner class SpecialButtonsLongHoldRunnable(val mState: SpecialButtonState) : Runnable {
        override fun run() {
            mState.setIsLocked(!mState.isActive)
            mState.setIsActive(!mState.isActive)
            mLongPressCount++
        }
    }

    internal fun showPopup(view: View, extraButton: ExtraKeyButton) {
        val width = view.measuredWidth
        val height = view.measuredHeight
        val button: MaterialButton
        if (isSpecialButton(extraButton)) {
            button = createSpecialButton(extraButton.key, false) ?: return
        } else {
            button = MaterialButton(getThemedContext(), null, android.R.attr.buttonBarButtonStyle)
            button.setTextColor(mButtonTextColor)
        }
        button.text = extraButton.display
        button.isAllCaps = mButtonTextAllCaps
        button.setPadding(0, 0, 0, 0)
        button.minHeight = 0
        button.minWidth = 0
        button.minimumWidth = 0
        button.minimumHeight = 0
        button.width = width
        button.height = height
        button.setBackgroundColor(mButtonActiveBackgroundColor)
        val popupWindow = PopupWindow(this)
        popupWindow.width = ViewGroup.LayoutParams.WRAP_CONTENT
        popupWindow.height = ViewGroup.LayoutParams.WRAP_CONTENT
        popupWindow.contentView = button
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = false
        popupWindow.showAsDropDown(view, 0, -2 * height)
        mPopupWindow = popupWindow
    }

    open fun dismissPopup() {
        mPopupWindow?.contentView = null
        mPopupWindow?.dismiss()
        mPopupWindow = null
    }

    open fun isSpecialButton(button: ExtraKeyButton): Boolean {
        return mSpecialButtonsKeys?.contains(button.key) == true
    }

    @Nullable
    open fun readSpecialButton(specialButton: SpecialButton?, autoSetInActive: Boolean): Boolean? {
        val state = mSpecialButtons?.get(specialButton) ?: return null

        if (!state.isCreated || !state.isActive) return false

        if (autoSetInActive && !state.isLocked) state.setIsActive(false)

        return true
    }

    open fun createSpecialButton(buttonKey: String?, needUpdate: Boolean): MaterialButton? {
        val state = mSpecialButtons?.get(SpecialButton.valueOf(buttonKey)) ?: return null
        state.setIsCreated(true)
        val button = MaterialButton(getThemedContext(), null, android.R.attr.buttonBarButtonStyle)
        button.setTextColor(if (state.isActive) mButtonActiveTextColor else mButtonTextColor)
        if (needUpdate) {
            state.buttons.add(button)
        }
        return button
    }

    companion object {
        @JvmField
        val ATTR_BUTTON_TEXT_COLOR = R.attr.extraKeysButtonTextColor

        @JvmField
        val ATTR_BUTTON_ACTIVE_TEXT_COLOR = R.attr.extraKeysButtonActiveTextColor

        @JvmField
        val ATTR_BUTTON_BACKGROUND_COLOR = R.attr.extraKeysButtonBackgroundColor

        @JvmField
        val ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR = R.attr.extraKeysButtonActiveBackgroundColor

        const val DEFAULT_BUTTON_TEXT_COLOR = -0x1 // 0xFFFFFFFF
        const val DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = -0x7f2116 // 0xFF80DEEA
        const val DEFAULT_BUTTON_BACKGROUND_COLOR = 0x00000000
        const val DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = -0x808081 // 0xFF7F7F7F

        const val MIN_LONG_PRESS_DURATION = 200
        const val MAX_LONG_PRESS_DURATION = 3000
        const val FALLBACK_LONG_PRESS_DURATION = 400

        const val MIN_LONG_PRESS__REPEAT_DELAY = 5
        const val MAX_LONG_PRESS__REPEAT_DELAY = 2000
        const val DEFAULT_LONG_PRESS_REPEAT_DELAY = 80

        @JvmStatic
        fun maximumLength(matrix: Array<out Array<*>>): Int {
            var m = 0
            for (row in matrix) m = Math.max(m, row.size)
            return m
        }
    }
}
