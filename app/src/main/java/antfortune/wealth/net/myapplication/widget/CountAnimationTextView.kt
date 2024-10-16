package antfortune.wealth.net.myapplication.widget

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import java.text.DecimalFormat

class CountAnimationTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatTextView(context, attrs, defStyleAttr) {

    private var isAnimating = false
    private var mCountAnimator: ValueAnimator = ValueAnimator()
    private var mCountAnimationListener: CountAnimationListener? = null
    private var mDecimalFormat: DecimalFormat? = null

    init {
        setUpAnimator()
    }

    private fun setUpAnimator() {
        mCountAnimator.addUpdateListener { animation ->
            val value = if (mDecimalFormat == null) {
                animation.animatedValue.toString()
            } else {
                mDecimalFormat!!.format(animation.animatedValue)
            }
            super.setText(value)
        }

        mCountAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                isAnimating = true
                mCountAnimationListener?.onAnimationStart(mCountAnimator.animatedValue)
            }

            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
                mCountAnimationListener?.onAnimationEnd(mCountAnimator.animatedValue)
            }

            override fun onAnimationCancel(animation: Animator) {
                // do nothing
            }

            override fun onAnimationRepeat(animation: Animator) {
                // do nothing
            }
        })
        mCountAnimator.duration = DEFAULT_DURATION
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mCountAnimator.cancel()
    }

    fun countAnimation(fromValue: Int, toValue: Int) {
        if (isAnimating) return
        mCountAnimator.setIntValues(fromValue, toValue)
        mCountAnimator.start()
    }

    fun setAnimationDuration(duration: Long): CountAnimationTextView {
        mCountAnimator.duration = duration
        return this
    }

    fun setInterpolator(value: TimeInterpolator): CountAnimationTextView {
        mCountAnimator.interpolator = value
        return this
    }

    interface CountAnimationListener {
        fun onAnimationStart(animatedValue: Any?)
        fun onAnimationEnd(animatedValue: Any?)
    }

    fun setDecimalFormat(decimalFormat: DecimalFormat?): CountAnimationTextView {
        this.mDecimalFormat = decimalFormat
        return this
    }

    fun clearDecimalFormat() {
        this.mDecimalFormat = null
    }

    fun setCountAnimationListener(listener: CountAnimationListener): CountAnimationTextView {
        this.mCountAnimationListener = listener
        return this
    }

    companion object {
        private const val DEFAULT_DURATION: Long = 1000
    }
}
