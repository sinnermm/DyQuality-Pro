package dev.operit.dyquality;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 抖音高清/高码率无卡顿画质选择模块
 * 
 * 核心拦截原理：
 * 1. 拦截 SimVideoUrlModel 数据模型层：从数据源头重排候选流，把目标高清高码率流置顶（Index 0）。
 *    - 优势：预加载器（Preloader / DataLoader）自动按目标高清流预载缓存，划到视频时秒开不卡顿！
 * 2. 拦截 getHitBitrate() 与 setHitBitrate()：
 *    - 防止抖音内部 ABR 算法因为瞬时测速降档导致画质回退。
 * 3. 彻底放开编码孤岛：
 *    - 允许自由选择 ByteVC1(H.265)、ByteVC2(H.266) 以及 H.264，发挥现代机型芯片的最大解码画质潜力。
 */
public final class QualityModule extends XposedModule {

    private static final String TAG = "DyQuality";
    private static final String TARGET_PACKAGE = "com.ss.android.ugc.aweme";
    private static final String MODEL_CLASS = "com.ss.android.ugc.playerkit.simapicommon.model.SimVideoUrlModel";

    private volatile Context mContext;
    private volatile int mCurrentMode = QualityPolicy.MODE_ORIGIN; // 默认直接原画
    private boolean mInitialized;

    public QualityModule(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName()) || !param.isFirstPackage() || mInitialized) {
            return;
        }
        mInitialized = true;
        Log.i(TAG, "DyQuality loaded for " + param.getPackageName());

        installLifecycleHooks();
        installSimVideoUrlModelHooks(param.getClassLoader());
    }

    /**
     * 监听生命周期以获取上下文和挂载悬浮菜单
     */
    private void installLifecycleHooks() {
        try {
            hook(Application.class.getDeclaredMethod("onCreate"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            mContext = (Application) chain.getThisObject();
                            mCurrentMode = Settings.getMode(mContext);
                            Log.i(TAG, "Application attached, initial mode: " + mCurrentMode);
                        } catch (Throwable t) {
                            Log.e(TAG, "Failed in Application.onCreate", t);
                        }
                        return result;
                    }
                });

            hook(Activity.class.getDeclaredMethod("onResume"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            final Activity act = (Activity) chain.getThisObject();
                            if (mContext == null) {
                                mContext = act.getApplicationContext();
                                mCurrentMode = Settings.getMode(mContext);
                            }
                            FloatMenu.attach(act, new FloatMenu.OnModeChangedListener() {
                                @Override
                                public void onModeChanged(int newMode) {
                                    mCurrentMode = newMode;
                                    Log.i(TAG, "User switched quality mode to: " + newMode);
                                }
                            });
                        } catch (Throwable t) {
                            Log.e(TAG, "Failed in Activity.onResume", t);
                        }
                        return result;
                    }
                });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install lifecycle hooks", t);
        }
    }

    /**
     * 核心：拦截 SimVideoUrlModel 的清晰度与命中档位
     */
    private void installSimVideoUrlModelHooks(ClassLoader loader) {
        try {
            Class<?> modelClass = Class.forName(MODEL_CLASS, false, loader);
            final Field bitRatesField = findField(modelClass, "bitRate");
            final Field hitBitrateField = findField(modelClass, "hitBitrate");

            if (bitRatesField == null) {
                Log.w(TAG, "bitRate field not found in " + MODEL_CLASS);
                return;
            }

            // 1. Hook 获取清晰度列表的方法 (getBitRate / getRawBitRate)
            // 目标：将选中的最高品质流调整到 index 0，使得预加载器（Preloader）提前缓存高清流！
            for (final Method m : modelClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterTypes().length == 0 && List.class.isAssignableFrom(m.getReturnType())) {
                    hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(new XposedInterface.Hooker() {
                            @Override
                            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                Object result = chain.proceed();
                                if (result instanceof List && mCurrentMode != QualityPolicy.MODE_AUTO) {
                                    try {
                                        reorderCandidatesToTop((List<?>) result, mCurrentMode);
                                    } catch (Throwable t) {
                                        Log.w(TAG, "Error reordering bitrate list", t);
                                    }
                                }
                                return result;
                            }
                        });
                }
            }

            // 2. Hook 读取 hitBitrate 的方法 (getHitBitrate)
            // 目标：播放器请求当前播放档位时，直接返回符合目标清晰度与码率要求的最高品质流
            if (hitBitrateField != null) {
                final Class<?> bitrateType = hitBitrateField.getType();
                for (final Method m : modelClass.getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers())) continue;
                    if (m.getParameterTypes().length == 0 && m.getReturnType() == bitrateType) {
                        hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(new XposedInterface.Hooker() {
                                @Override
                                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                    Object originalHit = chain.proceed();
                                    if (mCurrentMode == QualityPolicy.MODE_AUTO) {
                                        return originalHit;
                                    }
                                    try {
                                        Object model = chain.getThisObject();
                                        Object forcedHit = resolveTargetBitrate(model, bitRatesField, originalHit);
                                        return forcedHit != null ? forcedHit : originalHit;
                                    } catch (Throwable t) {
                                        Log.w(TAG, "Error resolving hit bitrate", t);
                                        return originalHit;
                                    }
                                }
                            });
                    }
                }

                // 3. Hook 写入 hitBitrate 的方法 (setHitBitrate)
                // 目标：拦截系统内部降档调用，强制覆盖为目标高画质流
                for (final Method m : modelClass.getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1 && params[0] == bitrateType && m.getReturnType() == void.class) {
                        hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(new XposedInterface.Hooker() {
                                @Override
                                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                    Object originalArg = chain.getArg(0);
                                    if (mCurrentMode == QualityPolicy.MODE_AUTO) {
                                        return chain.proceed();
                                    }
                                    try {
                                        Object model = chain.getThisObject();
                                        Object forcedArg = resolveTargetBitrate(model, bitRatesField, originalArg);
                                        if (forcedArg != null) {
                                            Object[] newArgs = new Object[]{forcedArg};
                                            return chain.proceed(newArgs);
                                        }
                                    } catch (Throwable t) {
                                        Log.w(TAG, "Error in setHitBitrate", t);
                                    }
                                    return chain.proceed();
                                }
                            });
                    }
                }
            }

            Log.i(TAG, "SimVideoUrlModel hooks installed successfully!");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install SimVideoUrlModel hooks", t);
        }
    }

    /**
     * 将符合策略的最佳流交换到列表第一位（index 0）
     */
    @SuppressWarnings("unchecked")
    private void reorderCandidatesToTop(List<?> list, int mode) {
        if (list == null || list.size() <= 1) return;
        List<QualityPolicy.Candidate> candidates = extractCandidates(list);
        int bestIdx = QualityPolicy.choose(candidates, mode);
        if (bestIdx > 0 && bestIdx < list.size()) {
            Collections.swap((List<Object>) list, 0, bestIdx);
        }
    }

    /**
     * 从模型中找出符合当前画质策略的目标码率对象
     */
    private Object resolveTargetBitrate(Object model, Field bitRatesField, Object defaultBitrate) {
        if (model == null || bitRatesField == null) return defaultBitrate;
        try {
            Object rawList = bitRatesField.get(model);
            if (!(rawList instanceof List)) return defaultBitrate;
            List<?> list = (List<?>) rawList;
            if (list.isEmpty()) return defaultBitrate;

            List<QualityPolicy.Candidate> candidates = extractCandidates(list);
            int bestIdx = QualityPolicy.choose(candidates, mCurrentMode);
            if (bestIdx >= 0 && bestIdx < list.size()) {
                return list.get(bestIdx);
            }
        } catch (Throwable ignored) {}
        return defaultBitrate;
    }

    /**
     * 将抖音原生 BitRate 对象列表转换为纯净的 Candidate 候选对象
     */
    private List<QualityPolicy.Candidate> extractCandidates(List<?> list) {
        List<QualityPolicy.Candidate> candidates = new ArrayList<QualityPolicy.Candidate>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item == null) continue;
            int w = getInt(item, "getVideoWidth");
            int h = getInt(item, "getVideoHeight");
            int br = getInt(item, "getBitRate");
            int fps = getInt(item, "getFps");
            int codec = getInt(item, "LIZJ"); // 抖音内部 codecType 字段通常为 LIZJ (0=H264, 1=ByteVC1, 2=ByteVC2)
            candidates.add(new QualityPolicy.Candidate(i, w, h, br, codec, fps, item));
        }
        return candidates;
    }

    private static int getInt(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            m.setAccessible(true);
            Object res = m.invoke(obj);
            if (res instanceof Number) {
                return ((Number) res).intValue();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
