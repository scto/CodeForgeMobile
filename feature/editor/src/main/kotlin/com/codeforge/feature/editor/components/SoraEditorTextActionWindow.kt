/**
 * Modul: :feature:editor
 * CodeForge Editor Text Action Window component (ported from sora-editor app sample)
 */
package com.codeforge.feature.editor.components

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import io.github.rosemoe.sora.event.ColorSchemeUpdateEvent
import io.github.rosemoe.sora.event.EditorFocusChangeEvent
import io.github.rosemoe.sora.event.EditorReleaseEvent
import io.github.rosemoe.sora.event.EventManager
import io.github.rosemoe.sora.event.HandleStateChangeEvent
import io.github.rosemoe.sora.event.InterceptTarget
import io.github.rosemoe.sora.event.LongPressEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorTouchEventHandler
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import io.github.rosemoe.sora.widget.component.EditorBuiltinComponent
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class SoraEditorTextActionWindow(
    soraEditor: CodeEditor,
    private val onMoreClicked: (() -> Unit)? = null
) : EditorPopupWindow(soraEditor, FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED), View.OnClickListener, EditorBuiltinComponent {

    private val targetEditor: CodeEditor = soraEditor
    private val selectAllBtn: ImageButton
    private val cutBtn: ImageButton
    private val copyBtn: ImageButton
    private val pasteBtn: ImageButton
    private val longSelectBtn: ImageButton
    private val moreBtn: ImageButton
    private val rootView: LinearLayout
    private val handler: EditorTouchEventHandler = targetEditor.eventHandler
    private val eventManager: EventManager = targetEditor.createSubEventManager()

    private var lastScroll: Long = 0
    private var lastPosition: Int = -1
    private var lastCause: Int = 0
    private var isWindowEnabled: Boolean = true

    init {
        val context = targetEditor.context
        val dp = targetEditor.dpUnit

        rootView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
        }

        fun createButton(iconResId: Int, contentDesc: String): ImageButton {
            return ImageButton(context).apply {
                setImageResource(iconResId)
                contentDescription = contentDesc
                setBackgroundResource(android.R.color.transparent)
                val paddingPx = (6 * dp).toInt()
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                layoutParams = LinearLayout.LayoutParams(
                    (36 * dp).toInt(),
                    (36 * dp).toInt()
                )
                setOnClickListener(this@SoraEditorTextActionWindow)
            }
        }

        selectAllBtn = createButton(android.R.drawable.ic_menu_edit, "Select All")
        cutBtn = createButton(android.R.drawable.ic_menu_close_clear_cancel, "Cut")
        copyBtn = createButton(android.R.drawable.ic_menu_share, "Copy")
        pasteBtn = createButton(android.R.drawable.ic_menu_add, "Paste")
        longSelectBtn = createButton(android.R.drawable.ic_menu_view, "Long Select")
        moreBtn = createButton(android.R.drawable.ic_menu_more, "More Actions")

        rootView.addView(selectAllBtn)
        rootView.addView(cutBtn)
        rootView.addView(copyBtn)
        rootView.addView(pasteBtn)
        rootView.addView(longSelectBtn)
        rootView.addView(moreBtn)

        moreBtn.visibility = if (onMoreClicked != null) View.VISIBLE else View.GONE

        applyColorScheme()
        setContentView(rootView)
        setSize(0, (48 * dp).toInt())
        getPopup().animationStyle = io.github.rosemoe.sora.R.style.text_action_popup_animation

        subscribeEvents()
    }

    private fun applyColorScheme() {
        val dp = targetEditor.dpUnit
        val gd = GradientDrawable().apply {
            cornerRadius = 6 * dp
            setColor(targetEditor.colorScheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND))
        }
        rootView.background = gd

        val iconColor = targetEditor.colorScheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR)
        val filter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)

        listOf(selectAllBtn, cutBtn, copyBtn, pasteBtn, longSelectBtn, moreBtn).forEach { button ->
            button.colorFilter = filter
        }
    }

    private fun subscribeEvents() {
        eventManager.subscribeAlways(SelectionChangeEvent::class.java) { onSelectionChange(it) }
        eventManager.subscribeAlways(ScrollEvent::class.java) { onEditorScroll(it) }
        eventManager.subscribeAlways(HandleStateChangeEvent::class.java) { onHandleStateChange(it) }
        eventManager.subscribeAlways(LongPressEvent::class.java) { onEditorLongPress(it) }
        eventManager.subscribeAlways(EditorFocusChangeEvent::class.java) { onEditorFocusChange(it) }
        eventManager.subscribeAlways(EditorReleaseEvent::class.java) { onEditorRelease(it) }
        eventManager.subscribeAlways(ColorSchemeUpdateEvent::class.java) { applyColorScheme() }
    }

    private fun onEditorRelease(event: EditorReleaseEvent) {
        isEnabled = false
    }

    private fun onEditorLongPress(event: LongPressEvent) {
        if (targetEditor.cursor.isSelected && lastCause == SelectionChangeEvent.CAUSE_SEARCH) {
            val idx = event.index
            if (idx >= targetEditor.cursor.left && idx <= targetEditor.cursor.right) {
                lastCause = 0
                displayWindow()
            }
            event.intercept(InterceptTarget.TARGET_EDITOR)
        }
    }

    private fun onEditorFocusChange(event: EditorFocusChangeEvent) {
        if (!event.isGainFocus) {
            dismiss()
        }
    }

    private fun onEditorScroll(event: ScrollEvent) {
        val last = lastScroll
        lastScroll = System.currentTimeMillis()
        if (lastScroll - last < DELAY && lastCause != SelectionChangeEvent.CAUSE_SEARCH) {
            postDisplay()
        }
    }

    private fun onHandleStateChange(event: HandleStateChangeEvent) {
        if (event.isHeld) {
            postDisplay()
        }
        if (!event.editor.cursor.isSelected &&
            event.handleType == HandleStateChangeEvent.HANDLE_TYPE_INSERT &&
            !event.isHeld
        ) {
            displayWindow()
            targetEditor.postDelayedInLifecycle(object : Runnable {
                override fun run() {
                    if (!targetEditor.eventHandler.shouldDrawInsertHandle() && !targetEditor.cursor.isSelected) {
                        dismiss()
                    } else if (!targetEditor.cursor.isSelected) {
                        targetEditor.postDelayedInLifecycle(this, CHECK_INTERVAL)
                    }
                }
            }, CHECK_INTERVAL)
        }
    }

    private fun onSelectionChange(event: SelectionChangeEvent) {
        if (handler.hasAnyHeldHandle()) return
        lastCause = event.cause
        if (event.isSelected) {
            if (event.cause != SelectionChangeEvent.CAUSE_SEARCH) {
                targetEditor.postInLifecycle { displayWindow() }
            } else {
                dismiss()
            }
            lastPosition = -1
        } else {
            var show = false
            if (event.cause == SelectionChangeEvent.CAUSE_TAP &&
                event.left.index == lastPosition &&
                !isShowing &&
                !targetEditor.text.isInBatchEdit &&
                targetEditor.isEditable
            ) {
                targetEditor.postInLifecycle { displayWindow() }
                show = true
            } else {
                dismiss()
            }
            lastPosition = if (event.cause == SelectionChangeEvent.CAUSE_TAP && !show) {
                event.left.index
            } else {
                -1
            }
        }
    }

    override fun isEnabled(): Boolean = isWindowEnabled

    override fun setEnabled(enabled: Boolean) {
        isWindowEnabled = enabled
        eventManager.isEnabled = enabled
        if (!enabled) {
            dismiss()
        }
    }

    private fun postDisplay() {
        if (!isShowing) return
        dismiss()
        if (!targetEditor.cursor.isSelected) return

        targetEditor.postDelayedInLifecycle(object : Runnable {
            override fun run() {
                if (!handler.hasAnyHeldHandle() &&
                    !targetEditor.snippetController.isInSnippet() &&
                    System.currentTimeMillis() - lastScroll > DELAY &&
                    targetEditor.scroller.isFinished
                ) {
                    displayWindow()
                } else {
                    targetEditor.postDelayedInLifecycle(this, DELAY)
                }
            }
        }, DELAY)
    }

    private fun selectTop(rect: RectF): Int {
        val rowHeight = targetEditor.rowHeight
        return if (rect.top - rowHeight * 1.5f > height) {
            (rect.top - rowHeight * 1.5f - height).toInt()
        } else {
            (rect.bottom + rowHeight / 2f).toInt()
        }
    }

    fun displayWindow() {
        updateBtnState()
        val cursor = targetEditor.cursor
        val top = if (cursor.isSelected) {
            val leftRect = targetEditor.leftHandleDescriptor.position
            val rightRect = targetEditor.rightHandleDescriptor.position
            Math.min(selectTop(leftRect), selectTop(rightRect))
        } else {
            selectTop(targetEditor.insertHandleDescriptor.position)
        }.coerceIn(0, (targetEditor.height - height - 5).coerceAtLeast(0))

        val handleLeftX = targetEditor.getOffset(cursor.leftLine, cursor.leftColumn)
        val handleRightX = targetEditor.getOffset(cursor.rightLine, cursor.rightColumn)
        val panelX = ((handleLeftX + handleRightX) / 2f - rootView.measuredWidth / 2f).toInt()
        setLocationAbsolutely(panelX, top)
        show()
    }

    private fun updateBtnState() {
        pasteBtn.isEnabled = targetEditor.hasClip()
        copyBtn.visibility = if (targetEditor.cursor.isSelected) View.VISIBLE else View.GONE
        pasteBtn.visibility = if (targetEditor.isEditable) View.VISIBLE else View.GONE
        cutBtn.visibility = if (targetEditor.cursor.isSelected && targetEditor.isEditable) View.VISIBLE else View.GONE
        longSelectBtn.visibility = if (!targetEditor.cursor.isSelected && targetEditor.isEditable) View.VISIBLE else View.GONE
        moreBtn.visibility = if (onMoreClicked != null) View.VISIBLE else View.GONE

        rootView.measure(
            View.MeasureSpec.makeMeasureSpec(1000000, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(100000, View.MeasureSpec.AT_MOST)
        )
        setSize(Math.min(rootView.measuredWidth, (targetEditor.dpUnit * 230).toInt()), height)
    }

    override fun show() {
        if (!isWindowEnabled || targetEditor.snippetController.isInSnippet() || !targetEditor.hasFocus() || targetEditor.isInMouseMode) {
            return
        }
        super.show()
    }

    override fun onClick(v: View?) {
        when (v) {
            selectAllBtn -> targetEditor.selectAll()
            cutBtn -> if (targetEditor.cursor.isSelected) targetEditor.cutText()
            copyBtn -> {
                targetEditor.copyText()
                targetEditor.setSelection(targetEditor.cursor.rightLine, targetEditor.cursor.rightColumn)
            }
            pasteBtn -> {
                targetEditor.pasteText()
                targetEditor.setSelection(targetEditor.cursor.rightLine, targetEditor.cursor.rightColumn)
            }
            longSelectBtn -> targetEditor.beginLongSelect()
            moreBtn -> onMoreClicked?.invoke()
        }
        dismiss()
    }

    companion object {
        private const val DELAY = 200L
        private const val CHECK_INTERVAL = 100L
    }
}
