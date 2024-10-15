package antfortune.wealth.net.myapplication;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

public class NetworkAnalyzeScrollView extends ScrollView {
    private boolean isScrollingEnabled = true;

    public NetworkAnalyzeScrollView(Context context) {
        super(context);
    }

    public NetworkAnalyzeScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NetworkAnalyzeScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 根据是否启用滚动来决定是否拦截触摸事件
        return isScrollingEnabled && super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // 根据是否启用滚动来决定是否处理触摸事件
        return isScrollingEnabled && super.onTouchEvent(ev);
    }

    public void setScrollingEnabled(boolean enabled) {
        this.isScrollingEnabled = enabled;
    }
}
