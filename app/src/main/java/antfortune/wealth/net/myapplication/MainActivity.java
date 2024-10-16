package antfortune.wealth.net.myapplication;

import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import java.util.HashMap;
import java.util.Map;

import antfortune.wealth.net.myapplication.task.TraceTask;
import antfortune.wealth.net.myapplication.widget.CountAnimationTextView;

public class MainActivity extends AppCompatActivity implements NetworkAnalyzeListener {

    private Map<String, LinearLayout> ipViewMap = new HashMap<>(); // 用于存储每个 IP 对应的视图容器
    private Map<String, Integer> ipTestCountMap = new HashMap<>(); // 用于存储每个 IP 的测试计数
    private static final int TOTAL_TESTS = 3;  // 定义总共需要添加的测试数量（TCP, Ping, TraceRouter）

    TextView resultTextView;
    TextView tvDomainAccessTextView;
    ScrollView scrollView;
    CountAnimationTextView countAnimationTextViewLarge; // 顶部的大 CountAnimationTextView
    LinearLayout containerLayout; // Container for dynamically added views

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
        containerLayout = findViewById(R.id.dynamic_container); // 引用 dynamic_container 而不是 domain_access_container

        countAnimationTextViewLarge = findViewById(R.id.count_animation_textView_large);
        animateCountTextView(0, 80, countAnimationTextViewLarge); // 设置动画从 0 到 80

        // 初始化所有标题的点击事件
        setupToggle(rootView, R.id.tv_device_info_title, R.id.device_info);
        setupToggle(rootView, R.id.tv_domain_access_title, R.id.domain_access_container);

        // 默认展开所有视图
        expandView(findViewById(R.id.device_info));
        expandView(findViewById(R.id.domain_access_container));

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

    @Override
    public void onFailed(Exception e) {
        appendAndExpand(resultTextView, "诊断失败:" + e.getMessage(), findViewById(R.id.device_info));
        Toast.makeText(this, "诊断失败:" + e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDomainAccessUpdated(@NonNull String log) {
        appendAndExpand(tvDomainAccessTextView, log, findViewById(R.id.domain_access_container));
    }

    @Override
    public void onDeviceInfoUpdated(@NonNull String log) {
        appendAndExpand(resultTextView, log, findViewById(R.id.device_info));
    }

    private LinearLayout createIpContainer(String ip) {
        // 创建一个新的 LinearLayout 作为 IP 容器
        LinearLayout ipContainer = new LinearLayout(this);
        ipContainer.setOrientation(LinearLayout.VERTICAL);
        ipContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ipContainer.setPadding(8, 8, 8, 8);

        // 创建一个专门用于存放内容的容器
        LinearLayout contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentContainer.setVisibility(View.VISIBLE);  // 默认展开

        // 设置 IP 标题的容器，带长下划线背景
        LinearLayout ipTitleContainer = new LinearLayout(this);
        ipTitleContainer.setOrientation(LinearLayout.VERTICAL);
        ipTitleContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ipTitleContainer.setPadding(0, 0, 0, 0);  // 无内边距

        // 创建 IP 标题，带长下划线
        TextView ipTitle = new TextView(this);
        ipTitle.setText("↓ IP: " + ip);
        ipTitle.setTextSize(18);
        ipTitle.setTextColor(getResources().getColor(android.R.color.black)); // 设置文字颜色为黑色
        ipTitle.setGravity(Gravity.CENTER_VERTICAL);
        ipTitle.setPadding(16, 16, 16, 16);  // 增加内边距

        // 为 IP 添加一个 View 模拟下划线，宽度与容器一致
        View ipUnderline = new View(this);
        LinearLayout.LayoutParams underlineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2);  // 设置下划线宽度与容器宽度一致，2px 高度
        ipUnderline.setLayoutParams(underlineParams);
        ipUnderline.setBackgroundColor(getResources().getColor(android.R.color.black));  // 设置下划线颜色为黑色

        // 将 IP 标题和下划线添加到容器
        ipTitleContainer.addView(ipTitle);
        ipTitleContainer.addView(ipUnderline);

        // 为标题设置点击事件，用于展开/收起内容容器
        ipTitle.setOnClickListener(new View.OnClickListener() {
            private boolean isExpanded = true;

            @Override
            public void onClick(View v) {
                if (isExpanded) {
                    collapseView(contentContainer);  // 收起内容
                    ipTitle.setText("→ IP: " + ip);  // 更改为向右箭头表示收起状态
                } else {
                    expandView(contentContainer);    // 展开内容
                    ipTitle.setText("↓ IP: " + ip);  // 更改为向下箭头表示展开状态
                }
                isExpanded = !isExpanded;
            }
        });

        // 将 IP 标题容器和内容容器添加到 IP 容器
        ipContainer.addView(ipTitleContainer);
        ipContainer.addView(contentContainer);

        // 初始化该 IP 的测试计数为 0
        ipTestCountMap.put(ip, 0);

        // 返回 IP 容器
        return ipContainer;
    }

    private void addTitleAndLogView(LinearLayout contentContainer, String testType, String log, String ip) {
        // 创建测试类型标题
        final TextView titleView = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 8, 0, 8);  // 设置上下8dp的间隔，增加视觉间距
        titleView.setLayoutParams(titleParams);
        titleView.setPadding(24, 24, 24, 24);  // 设置更大的内边距
        titleView.setGravity(Gravity.CENTER_VERTICAL);  // 垂直居中
        titleView.setText(testType);  // 设置测试类型标题
        titleView.setTextColor(getResources().getColor(android.R.color.black));  // 设置字体颜色为黑色
        titleView.setTextSize(16);  // 设置字体大小
        titleView.setTypeface(null, Typeface.BOLD);  // 设置为粗体

        // 为测试类型添加下划线，宽度与文字长度一致
        View underlineView = new View(this);
        LinearLayout.LayoutParams underlineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 2);  // 下划线宽度与文字宽度一致
        underlineView.setLayoutParams(underlineParams);
        underlineView.setBackgroundColor(getResources().getColor(android.R.color.black));  // 设置下划线颜色为黑色

        // 创建内容 TextView
        final TextView infoView = new TextView(this);
        infoView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        infoView.setPadding(24, 24, 24, 24);  // 设置内容的内边距
        infoView.setText(log);
        infoView.setTextColor(getResources().getColor(android.R.color.black));  // 设置内容字体颜色
        infoView.setVisibility(View.VISIBLE);  // 默认显示内容

        // 将标题、下划线和内容视图添加到内容容器中
        contentContainer.addView(titleView);
        contentContainer.addView(underlineView);  // 添加下划线
        contentContainer.addView(infoView);

        // 更新该 IP 的测试计数
        int currentCount = ipTestCountMap.get(ip) + 1;
        ipTestCountMap.put(ip, currentCount);

        // 检查是否添加了所有测试（TCP, Ping, TraceRouter），如果是，则收起并更新箭头方向
        if (currentCount >= TOTAL_TESTS) {
            collapseView(contentContainer);  // 自动收起视图

            // 获取 IP 容器中的标题，并更新箭头方向
            LinearLayout ipContainer = ipViewMap.get(ip);
            TextView ipTitle = (TextView) ((LinearLayout) ipContainer.getChildAt(0)).getChildAt(0);
            ipTitle.setText("→ IP: " + ip);  // 更改为向右箭头表示收起状态
        }
    }

    // 动态创建和添加 TCP、Ping、TraceRoute 的视图
    private void addDynamicViewsForIp(String ip, String log, String testType) {
        LinearLayout ipContainer;

        // 检查该 IP 是否已有容器
        if (ipViewMap.containsKey(ip)) {
            // 如果已有容器，则直接获取
            ipContainer = ipViewMap.get(ip);
        } else {
            // 如果没有容器，则为该 IP 创建一个新的容器并添加到界面中
            ipContainer = createIpContainer(ip);
            containerLayout.addView(ipContainer);
            ipViewMap.put(ip, ipContainer);  // 记录该 IP 和对应的容器
        }

        // 获取 IP 容器中的内容容器（第二个子视图）
        LinearLayout contentContainer = (LinearLayout) ipContainer.getChildAt(1);

        // 动态添加测试类型和日志内容到内容容器
        addTitleAndLogView(contentContainer, testType, log, ip);
    }

    @Override
    public void onPingAnalysisUpdated(@NonNull String log, @NonNull String ip) {
        addDynamicViewsForIp(ip, log, "Ping 分析");
    }

    @Override
    public void onPingCompleted() {
        // Optionally handle when Ping analysis is completed
    }

    @Override
    public void onTcpTestUpdated(@NonNull String log, @NonNull String ip) {
        addDynamicViewsForIp(ip, log, "TCP 测试");
    }

    @Override
    public void onTcpTestCompleted() {
        // Optionally handle when TCP Test is completed
    }

    @Override
    public void onTraceRouterUpdated(@NonNull String log, @NonNull String ip) {
        addDynamicViewsForIp(ip, log, "TraceRouter 路由跟踪");
    }

    @Override
    public void onTraceRouterCompleted() {
        // Optionally handle when TraceRouter is completed
    }

    @Override
    public void onPingScoreReceived(int score) {
        // Handle score animation if required
    }

    @Override
    public void onTcpTestScoreReceived(int score) {
        // Handle score animation if required
    }

    @Override
    public void onTraceRouterScoreReceived(int score) {
        // Handle score animation if required
    }
}
