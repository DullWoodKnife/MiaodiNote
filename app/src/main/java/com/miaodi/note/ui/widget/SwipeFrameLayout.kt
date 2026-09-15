package com.miaodi.note.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 支持左右滑动手势的 FrameLayout。
 * 用于“编辑器设置”面板的三个标签页滑动切换。
 * 仅拦截明显的横向滑动，纵向滑动及子控件（SeekBar/CheckBox）点击不受影响。
 */
class SwipeFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                tracking = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    // 明显的横向滑动才拦截，避免影响纵向滚动与控件操作
                    if (abs(dx) > touchSlop * 2 && abs(dx) > abs(dy) * 2) {
                        tracking = false
                        return true
                    }
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                tracking = false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > touchSlop * 4 && abs(dx) > abs(dy) * 2) {
                    if (dx < 0) onSwipeLeft?.invoke() else onSwipeRight?.invoke()
                }
            }
        }
        return true
    }
}
