package com.stone.persistent.recyclerview.library

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.viewpager2.widget.ViewPager2

/**
 * 内层的RecyclerView
 */
class ChildRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr) {

    private var parentRecyclerView: ParentRecyclerView? = null

    private val mTouchSlop: Int
    private var downX: Float = 0f
    private var downY: Float = 0f

    private var dragState: Int = DRAG_IDLE

    companion object {
        private const val DRAG_IDLE = 0
        private const val DRAG_VERTICAL = 1
        private const val DRAG_HORIZONTAL = 2
    }

    init {
        val configuration = ViewConfiguration.get(context)
        mTouchSlop = configuration.scaledTouchSlop
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)

        // 是否已经停止scrolling
        if (state == SCROLL_STATE_IDLE) {
            // 这里是考虑到当整个childRecyclerView被detach之后，及时上报parentRecyclerView
            val velocityY = getVelocityY()
            if (velocityY < 0 && getListScrollY() == 0) {
                parentRecyclerView?.fling(0, velocityY)
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            // ACTION_DOWN 触摸按下，保存临时变量
            dragState = DRAG_IDLE
            downX = ev.rawX
            downY = ev.rawY
            this.stopFling()
        }
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * 这段逻辑主要是RecyclerView最底部，垂直上拉后居然还能左右滑动，不能忍
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_MOVE) {
            // ACTION_MOVE 判定垂直还是水平滑动
            if (dragState == DRAG_IDLE) {
                val xDistance = Math.abs(ev.rawX - downX)
                val yDistance = Math.abs(ev.rawY - downY)

                if (xDistance > yDistance && xDistance > mTouchSlop) {
                    // 水平滑动
                    dragState = DRAG_HORIZONTAL
                } else if (yDistance > xDistance && yDistance > mTouchSlop) {
                    // 垂直滑动
                    dragState = DRAG_VERTICAL
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findParentRecyclerView()
    }

    private fun findParentRecyclerView() {
        var viewPager2: ViewPager2? = null
        var lastTraverseView: View = this

        var parentView = this.parent as View
        while (parentView != null) {
            val parentClassName = parentView::class.java.canonicalName
            if ("androidx.viewpager2.widget.ViewPager2.RecyclerViewImpl" == parentClassName) {
                // 此处将ChildRecyclerView保存到ViewPager2.currentItem的tag中
                if (lastTraverseView != this) {
                    lastTraverseView.setTag(R.id.tag_saved_child_recycler_view, this)
                }
            } else if (parentView is ViewPager2) {
                // 碰到了ViewPager2，需要上报给ParentRecyclerView
                viewPager2 = parentView
            } else if (parentView is ParentRecyclerView) {
                parentView.setInnerViewPager(viewPager2)
                parentView.setChildPagerContainer(lastTraverseView)
                this.parentRecyclerView = parentView
                return
            }

            lastTraverseView = parentView
            parentView = parentView.parent as View
        }
    }
}