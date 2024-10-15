package antfortune.wealth.net.myapplication;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import antfortune.wealth.net.myapplication.service.NetworkAnalyzeListener;
import antfortune.wealth.net.myapplication.task.TraceTask;

public class MainActivity extends AppCompatActivity implements NetworkAnalyzeListener {
    TextView resultTextView;
    TextView pingTextView;
    TextView tcpTextView;
    TextView tvDomainAccessTextView;
    TextView tvTraceRouter;
    NetworkAnalyzeScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View rootView = getLayoutInflater().inflate(R.layout.activity_main, null);
        setContentView(rootView);

        resultTextView = findViewById(R.id.tv_device_info);
        resultTextView.setText("");
        pingTextView = findViewById(R.id.tv_ping_analysis);
        pingTextView.setText("");
        tcpTextView = findViewById(R.id.tv_tcp_test);
        tcpTextView.setText("");
        tvDomainAccessTextView = findViewById(R.id.tv_domain_access);
        tvDomainAccessTextView.setText("");
        tvTraceRouter = findViewById(R.id.tv_trace_router);
        tvTraceRouter.setText("");

        scrollView = findViewById(R.id.scrollView);

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
        appendAndExpand(pingTextView, log, findViewById(R.id.ping_analysis_container));
    }

    @Override
    public void onTcpTestUpdated(@NonNull String log) {
        appendAndExpand(tcpTextView, log, findViewById(R.id.tcp_test_container));
    }

    @Override
    public void onTraceRouterUpdated(@NonNull String log) {
        appendAndExpand(tvTraceRouter, log, findViewById(R.id.trace_router_container));
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
    public void onTraceRouterCompleted() {
        hideLoading(findViewById(R.id.tv_tracerouter_title));
    }
}
