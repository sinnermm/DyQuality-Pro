package dev.operit.dyquality;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

public final class FloatMenu {

    public interface OnModeChangedListener {
        void onModeChanged(int newMode);
    }

    private static final Map<Activity, WeakReference<View>> sButtonCache = new WeakHashMap<Activity, WeakReference<View>>();

    public static void attach(final Activity activity, final OnModeChangedListener listener) {
        if (activity == null || activity.isFinishing()) return;

        WeakReference<View> ref = sButtonCache.get(activity);
        if (ref != null && ref.get() != null) {
            return; // 已经挂载
        }

        final ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        final TextView btn = new TextView(activity);
        btn.setText("画质");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);

        // 半透明圆角药丸背景
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC1E1E24);
        bg.setCornerRadius(dp2px(activity, 16));
        bg.setStroke(dp2px(activity, 1), 0x55FFFFFF);
        btn.setBackground(bg);

        int paddingH = dp2px(activity, 10);
        int paddingV = dp2px(activity, 6);
        btn.setPadding(paddingH, paddingV, paddingH, paddingV);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        params.leftMargin = dp2px(activity, 12);
        btn.setLayoutParams(params);

        // 触摸拖动与点击判断
        btn.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private float startTranslationX, startTranslationY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startTranslationX = v.getTranslationX();
                        startTranslationY = v.getTranslationY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                        }
                        v.setTranslationX(startTranslationX + dx);
                        v.setTranslationY(startTranslationY + dy);
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            showQualityDialog(activity, listener);
                        } else {
                            // 边缘吸附效果
                            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                            float currentX = v.getX();
                            float targetTranslationX = (currentX < screenWidth / 2f) 
                                    ? 0 
                                    : (screenWidth - v.getWidth() - dp2px(activity, 24));
                            v.animate().translationX(targetTranslationX).setDuration(200).start();
                        }
                        return true;
                }
                return false;
            }
        });

        decorView.post(new Runnable() {
            @Override
            public void run() {
                if (!activity.isFinishing()) {
                    decorView.addView(btn);
                    sButtonCache.put(activity, new WeakReference<View>(btn));
                }
            }
        });
    }

    private static void showQualityDialog(final Activity activity, final OnModeChangedListener listener) {
        final int currentMode = Settings.getMode(activity);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("选择视频播放画质");

        builder.setSingleChoiceItems(QualityPolicy.LABELS, currentMode, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (which != currentMode) {
                    Settings.setMode(activity, which);
                    if (listener != null) {
                        listener.onModeChanged(which);
                    }
                    Toast.makeText(activity, "已切换为: " + QualityPolicy.LABELS[which] + "\n(滑动到下一条视频生效)", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("取消", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private static int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()
        );
    }
}
