package com.youngsix6.betterpowermenu.action;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.youngsix6.betterpowermenu.util.ModuleLog;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XposedHelpers;

/** 保存当前原版对话框的弱引用，并在增强动作成功后退出电源菜单。 */
public final class PowerMenuCloser {

    private static final String LOG = "Dismiss";
    private static volatile WeakReference<Object> sDialog = new WeakReference<>(null);

    private PowerMenuCloser() {
    }

    public static void remember(Object dialog) {
        if (dialog != null) {
            sDialog = new WeakReference<>(dialog);
        }
    }

    public static void dismiss(Context context) {
        Runnable operation = () -> dismissOnMainThread(context);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            operation.run();
        } else {
            new Handler(Looper.getMainLooper()).post(operation);
        }
    }

    private static void dismissOnMainThread(Context context) {
        Object dialog = sDialog.get();
        if (dialog != null) {
            try {
                XposedHelpers.callMethod(dialog, "dismissDialog");
                ModuleLog.i(LOG, "增强动作完成，已调用原版 dismissDialog");
                return;
            } catch (Throwable firstError) {
                try {
                    XposedHelpers.callMethod(dialog, "dismissGlobalActionsMenu");
                    ModuleLog.i(LOG, "增强动作完成，已调用 dismissGlobalActionsMenu");
                    return;
                } catch (Throwable secondError) {
                    ModuleLog.w(LOG, "原版关闭方法不可用，尝试关闭系统对话框广播: "
                            + secondError.getClass().getSimpleName());
                }
            }
        }

        if (context == null) {
            ModuleLog.w(LOG, "无法关闭电源菜单: Context 为空");
            return;
        }
        try {
            context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            ModuleLog.i(LOG, "已发送关闭系统对话框广播");
        } catch (Throwable error) {
            ModuleLog.e(LOG, "关闭电源菜单失败", error);
        }
    }
}
