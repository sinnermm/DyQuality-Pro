package dev.operit.dyquality;

import android.content.Context;
import android.content.SharedPreferences;

public final class Settings {
    private static final String PREF_NAME = "dy_quality_settings";
    private static final String KEY_MODE = "quality_mode";

    public static int getMode(Context context) {
        if (context == null) return QualityPolicy.MODE_ORIGIN;
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            int m = sp.getInt(KEY_MODE, QualityPolicy.MODE_ORIGIN); // 默认直接原画
            if (m >= 0 && m < QualityPolicy.LABELS.length) {
                return m;
            }
        } catch (Throwable ignored) {}
        return QualityPolicy.MODE_ORIGIN;
    }

    public static void setMode(Context context, int mode) {
        if (context == null) return;
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putInt(KEY_MODE, mode).apply();
        } catch (Throwable ignored) {}
    }
}
