package com.youngsix6.betterpowermenu.action;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.youngsix6.betterpowermenu.config.ActionType;
import com.youngsix6.betterpowermenu.config.SettingsContract;
import com.youngsix6.betterpowermenu.util.ModuleLog;

/** 从 SystemUI 调用模块自身进程中的受限 Root 重启通道。 */
public final class RootActionClient {

    private static final String LOG = "RootAction";

    private RootActionClient() {
    }

    public interface Callback {
        void onComplete(boolean success);
    }

    /** Provider 最长可能等待数秒，必须在后台调用，绝不阻塞 SystemUI 主线程。 */
    public static void executeAsync(Context context, ActionType action, Callback callback) {
        Context safeContext = context == null ? null : context.getApplicationContext();
        if (safeContext == null) {
            safeContext = context;
        }
        Context finalContext = safeContext;
        try {
            Thread worker = new Thread(() -> {
                boolean success = executeBlocking(finalContext, action);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onComplete(success);
                    }
                });
            }, "BetterPowerMenu-RootAction");
            worker.start();
        } catch (Throwable error) {
            ModuleLog.e(LOG, "无法启动 Root 动作线程", error);
            if (callback != null) {
                callback.onComplete(false);
            }
        }
    }

    private static boolean executeBlocking(Context context, ActionType action) {
        if (context == null || action == null || !action.requiresRoot()) {
            return false;
        }
        try {
            Bundle result = context.getContentResolver().call(
                    SettingsContract.CONFIG_URI,
                    SettingsContract.METHOD_ROOT_REBOOT,
                    action.key(), null);
            boolean success = result != null
                    && result.getBoolean(SettingsContract.RESULT_SUCCESS, false);
            ModuleLog.i(LOG, "Root 动作返回: action=" + action.key()
                    + ", success=" + success);
            return success;
        } catch (Throwable error) {
            ModuleLog.e(LOG, "Root 动作调用失败: " + action.key(), error);
            return false;
        }
    }
}
