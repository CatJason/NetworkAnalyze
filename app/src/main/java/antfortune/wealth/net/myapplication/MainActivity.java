package antfortune.wealth.net.myapplication;

import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import antfortune.wealth.net.myapplication.task.TraceTask;
import antfortune.wealth.net.myapplication.widget.CountAnimationTextView;

public class MainActivity extends AppCompatActivity implements NetworkAnalyzeListener {
    TextView resultTextView;
    TextView tvDomainAccessTextView;
    ScrollView scrollView;
    CountAnimationTextView countAnimationTextViewLarge; // 顶部的大 CountAnimationTextView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View rootView = getLayoutInflater().inflate(R.layout.activity_main, null);
        setContentView(rootView);

        resultTextView = findViewById(R.id.tv_device_info);
        resultTextView.setText("");
        tvDomainAccessTextView = findViewById(R.id.tv_domain_access);
        tvDomainAccessTextView.setText("");

        scrollView = findViewById(R.id.scrollView);

        countAnimationTextViewLarge = findViewById(R.id.count_animation_textView_large);
        animateCountTextView(0, 80, countAnimationTextViewLarge); // 设置动画从 0 到 80

        // 初始化所有标题的点击事件
        setupToggle(rootView, R.id.tv_device_info_title, R.id.device_info);
        setupToggle(rootView, R.id.tv_ping_analysis_title, R.id.ping_analysis_container);
        setupToggle(rootView, R.id.tv_tcp_test_title, R.id.tcp_test_container);
        setupToggle(rootView, R.id.tv_domain_access_title, R.id.domain_access_container);
        setupToggle(rootView, R.id.tv_tracerouter_title, R.id.trace_router_container);

        // 默认展开所有视图
        expandView(findViewById(R.id.device_info));
        expandView(findViewById(R.id.ping_analysis_container));
        expandView(findViewById(R.id.tcp_test_container));
        expandView(findViewById(R.id.domain_access_container));
        expandView(findViewById(R.id.trace_router_container));

        // 初始态设置标题为 Loading...
        showLoading(findViewById(R.id.tv_ping_analysis_title));
        showLoading(findViewById(R.id.tv_tcp_test_title));
        showLoading(findViewById(R.id.tv_tracerouter_title));

        new TraceTask(this, MainActivity.this).doTask();
    }

    // 为顶部的 CountAnimationTextView 设置从 0 到 80 的动画
    private void animateCountTextView(int start, int end, final TextView textView) {
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.setDuration(3000); // 动画持续时间 3 秒
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                textView.setText(valueAnimator.getAnimatedValue().toString());
            }
        });
        animator.start();
    }

    private void setupToggle(View rootView, int titleId, int containerId) {
        TextView titleView = rootView.findViewById(titleId);
        final View containerView = rootView.findViewById(containerId);

        // 初始化时根据视图的状态设置箭头方向
        if (containerView.getVisibility() == View.VISIBLE) {
            titleView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.arrow_down, 0, 0, 0);
        } else {
            titleView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.arrow_right, 0, 0, 0);
        }

        titleView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (containerView.getVisibility() == View.VISIBLE) {
                    collapseView(containerView);
                    titleView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.arrow_right, 0, 0, 0);  // 折叠时使用向右箭头
                } else {
                    expandView(containerView);
                    titleView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.arrow_down, 0, 0, 0);  // 展开时使用向下箭头
                }
            }
        });
    }

    private void expandView(final View view) {
        view.setVisibility(View.VISIBLE);

        view.measure(View.MeasureSpec.makeMeasureSpec(((View) view.getParent()).getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int targetHeight = view.getMeasuredHeight();

        ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.height = (int) animation.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        animator.setDuration(300);
        animator.start();
    }

    private void collapseView(final View view) {
        final int initialHeight = view.getMeasuredHeight();

        ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.height = (int) animation.getAnimatedValue();
                view.setLayoutParams(layoutParams);

                if ((int) animation.getAnimatedValue() == 0) {
                    view.setVisibility(View.GONE);
                }
            }
        });
        animator.setDuration(300);
        animator.start();
    }

    // 在文本更新后自动展开视图以显示所有内容
    private void appendAndExpand(TextView textView, String text, View containerView) {
        textView.append(text);
        expandView(containerView);  // 在内容增加后，动态调整视图高度并展开
    }

    private void showLoading(TextView titleView) {
        titleView.setText(titleView.getText().toString() + " (Loading...)");
    }

    private void hideLoading(TextView titleView) {
        String originalText = titleView.getText().toString().replace(" (Loading...)", "");
        titleView.setText(originalText);
    }

    @Override
    public void onFailed(Exception e) {
        appendAndExpand(resultTextView, "诊断失败:" + e.getMessage(), findViewById(R.id.device_info));
        Toast.makeText(this, "诊断失败:" + e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDeviceInfoUpdated(@NonNull String log) {
        appendAndExpand(resultTextView, log, findViewById(R.id.device_info));
    }

    @Override
    public void onDomainAccessUpdated(@NonNull String log) {
        appendAndExpand(tvDomainAccessTextView, log, findViewById(R.id.domain_access_container));
    }

    @Override
    public void onPingAnalysisUpdated(@NonNull String log) {
        LinearLayout container = findViewById(R.id.ping_analysis_container);
        addDynamicTitleAndInfo("Ping Analysis", log, container, getResources().getColor(android.R.color.holo_green_light));
        expandView(container);  // Optionally expand the container after adding content
    }

    @Override
    public void onTcpTestUpdated(@NonNull String log) {
        LinearLayout container = findViewById(R.id.tcp_test_container);
        addDynamicTitleAndInfo("TCP Test", log, container, getResources().getColor(android.R.color.holo_red_light));
        expandView(container);  // Optionally expand the container after adding content
    }

    @Override
    public void onTraceRouterUpdated(@NonNull String log) {
        LinearLayout container = findViewById(R.id.trace_router_container);
        addDynamicTitleAndInfo("TraceRouter", log, container, getResources().getColor(android.R.color.holo_purple));
        expandView(container);  // Optionally expand the container after adding content
    }

    @Override
    public void onPingCompleted() {
        hideLoading(findViewById(R.id.tv_ping_analysis_title));
    }

    @Override
    public void onTcpTestCompleted() {
        hideLoading(findViewById(R.id.tv_tcp_test_title));
    }

    @Override
    public void onPingScoreReceived(int score) {
        CountAnimationTextView countAnimationPing = findViewById(R.id.count_animation_ping_analysis);
        animateCountTextView(0, score, countAnimationPing); // 设置从 0 到 score 的动画
    }

    @Override
    public void onTcpTestScoreReceived(int score) {
        CountAnimationTextView countAnimationTcp = findViewById(R.id.count_animation_tcp_test);
        animateCountTextView(0, score, countAnimationTcp);
    }

    @Override
    public void onTraceRouterScoreReceived(int score) {
        CountAnimationTextView countAnimationTraceRouter = findViewById(R.id.count_animation_tracerouter);
        animateCountTextView(0, score, countAnimationTraceRouter);
    }

    @Override
    public void onTraceRouterCompleted() {
        hideLoading(findViewById(R.id.tv_tracerouter_title));
    }

    private void addDynamicTitleAndInfo(final String titleText, final String log, final LinearLayout container, int backgroundColor) {
        // 获取屏幕宽度并计算一半的宽度
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int halfScreenWidth = screenWidth / 2;

        // Create the title TextView
        final TextView titleView = new TextView(this);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                halfScreenWidth, ViewGroup.LayoutParams.WRAP_CONTENT));  // 设置宽度为屏幕的一半
        titleView.setPadding(8, 8, 8, 8);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setText(titleText);
        titleView.setTextColor(getResources().getColor(android.R.color.white));
        titleView.setBackgroundColor(backgroundColor);  // Set dynamic background color
        titleView.setTextSize(16);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.arrow_down, 0, 0, 0);
        final TextView infoView = new TextView(this);
        infoView.setLayoutParams(new LinearLayout.LayoutParams(
                halfScreenWidth, ViewGroup.LayoutParams.WRAP_CONTENT));  // 设置宽度为屏幕的一半
        infoView.setPadding(8, 8, 8, 8);
        infoView.setText(log);
        infoView.setVisibility(View.VISIBLE);  // Start with the info section visible
        titleView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (infoView.getVisibility() == View.VISIBLE) {
                    collapseView(infoView);  // Collapse the info section
                    titleView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.arrow_right, 0, 0, 0);  // Set arrow to right
                } else {
                    expandView(infoView);  // Expand the info section
                    titleView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.arrow_down, 0, 0, 0);  // Set arrow to down
                }
            }
        });
        container.addView(titleView);
        container.addView(infoView);
    }
}
