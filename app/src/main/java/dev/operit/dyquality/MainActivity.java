package dev.operit.dyquality;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {

    private TextView mCurrentStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF121214);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp2px(24), dp2px(36), dp2px(24), dp2px(36));

        // 标题
        TextView title = new TextView(this);
        title.setText("抖音画质选择 Pro");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(Color.WHITE);
        title.getPaint().setFakeBoldText(true);
        layout.addView(title);

        TextView subTitle = new TextView(this);
        subTitle.setText("支持 1K / 2K / 满血原画 · 兼顾高画质与流畅度");
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subTitle.setTextColor(0xFF888899);
        subTitle.setPadding(0, dp2px(4), 0, dp2px(24));
        layout.addView(subTitle);

        // 当前模式状态卡片
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1E1E24);
        cardBg.setCornerRadius(dp2px(12));
        card.setBackground(cardBg);
        card.setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16));

        TextView cardTitle = new TextView(this);
        cardTitle.setText("当前默认播放画质");
        cardTitle.setTextColor(0xFFAAAAAA);
        cardTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        card.addView(cardTitle);

        mCurrentStatusText = new TextView(this);
        mCurrentStatusText.setText(QualityPolicy.LABELS[Settings.getMode(this)]);
        mCurrentStatusText.setTextColor(0xFF4E95FF);
        mCurrentStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        mCurrentStatusText.getPaint().setFakeBoldText(true);
        mCurrentStatusText.setPadding(0, dp2px(6), 0, 0);
        card.addView(mCurrentStatusText);

        layout.addView(card);

        // 模式切换按钮列表
        TextView btnSection = new TextView(this);
        btnSection.setText("快速切换画质模式");
        btnSection.setTextColor(Color.WHITE);
        btnSection.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnSection.setPadding(0, dp2px(28), 0, dp2px(12));
        layout.addView(btnSection);

        for (int i = 0; i < QualityPolicy.LABELS.length; i++) {
            final int modeIndex = i;
            Button btn = new Button(this);
            btn.setText(QualityPolicy.LABELS[i]);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            btn.setGravity(Gravity.CENTER);
            
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(0xFF262830);
            btnBg.setCornerRadius(dp2px(8));
            btn.setBackground(btnBg);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp2px(48)
            );
            lp.bottomMargin = dp2px(10);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Settings.setMode(MainActivity.this, modeIndex);
                    mCurrentStatusText.setText(QualityPolicy.LABELS[modeIndex]);
                    Toast.makeText(MainActivity.this, "已保存: " + QualityPolicy.LABELS[modeIndex], Toast.LENGTH_SHORT).show();
                }
            });
            layout.addView(btn);
        }

        // 使用说明与流畅度指南
        TextView guideTitle = new TextView(this);
        guideTitle.setText("使用说明与防卡顿原理");
        guideTitle.setTextColor(Color.WHITE);
        guideTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        guideTitle.setPadding(0, dp2px(24), 0, dp2px(8));
        layout.addView(guideTitle);

        TextView guide = new TextView(this);
        guide.setText(
            "1. LSPosed 框架配置：\n" +
            "   - 在 LSPosed 管理器中启用本模块，勾选「抖音 (com.ss.android.ugc.aweme)」。\n" +
            "   - 推荐关闭其他可能冲突的强制画质模块。\n\n" +
            "2. 抖音内悬浮快捷球：\n" +
            "   - 进入抖音后，屏幕左侧会出现可拖动的「画质」小胶囊按钮，可随时点击切换画质。\n\n" +
            "3. 为什么高清不卡顿？\n" +
            "   - 核心重构了数据模型层（SimVideoUrlModel），优先把目标高清档位排在首位；\n" +
            "   - 预加载器（Preloader）在滑到视频前直接预缓存高清切片，从根源消除起播缓冲；\n" +
            "   - 全面支持 ByteVC1 / ByteVC2 / H.264，释放现代芯片的满血硬件解码能力。\n\n" +
            "4. 遇到弱网或老旧机型提示：\n" +
            "   - 若处于电梯、地铁等极弱网环境，原画/2K可能会因下行带宽不足短暂缓冲，可一键切换回「1K」或「自动」。"
        );
        guide.setTextColor(0xFF9999AA);
        guide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        guide.setLineSpacing(dp2px(3), 1.0f);
        layout.addView(guide);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private int dp2px(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }
}
