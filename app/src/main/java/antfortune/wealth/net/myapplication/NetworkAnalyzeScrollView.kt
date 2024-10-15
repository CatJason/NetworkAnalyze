package antfortune.wealth.net.myapplication

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView

class NetworkAnalyzeScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var isScrollingEnabled = true

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 根据是否启用滚动来决定是否拦截触摸事件
        return isScrollingEnabled && super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // 根据是否启用滚动来决定是否处理触摸事件
        return isScrollingEnabled && super.onTouchEvent(ev)
    }

    fun setScrollingEnabled(enabled: Boolean) {
        isScrollingEnabled = enabled
    }
}
