package com.youngsix6.betterpowermenu.util;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/**
 * 模块统一日志入口。
 *
 * <p>普通 logcat 使用分组件 TAG；关键状态和异常同时写入 LSPosed 日志，
 * 便于 SystemUI 重启后定位 Hook、注入和动作执行问题。</p>
 */
public final class ModuleLog {

    private static final String PREFIX = "BetterPowerMenu";

    private ModuleLog() {
    }

    public static void d(String component, String message) {
        Log.d(tag(component), message);
    }

    public static void i(String component, String message) {
        Log.i(tag(component), message);
        bridge("I", component, message, null);
    }

    public static void w(String component, String message) {
        Log.w(tag(component), message);
        bridge("W", component, message, null);
    }

    public static void w(String component, String message, Throwable throwable) {
        Log.w(tag(component), message, throwable);
        bridge("W", component, message, throwable);
    }

    public static void e(String component, String message, Throwable throwable) {
        Log.e(tag(component), message, throwable);
        bridge("E", component, message, throwable);
    }

    private static String tag(String component) {
        return PREFIX + "." + component;
    }

    private static void bridge(String level, String component, String message,
                               Throwable throwable) {
        try {
            String line = PREFIX + "/" + level + "/" + component + ": " + message;
            if (throwable != null) {
                line += "\n" + Log.getStackTraceString(throwable);
            }
            XposedBridge.log(line);
        } catch (Throwable ignored) {
            // 日志组件绝不能影响 SystemUI 主流程。
        }
    }
}
