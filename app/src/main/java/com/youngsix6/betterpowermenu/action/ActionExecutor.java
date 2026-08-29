package com.youngsix6.betterpowermenu.action;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;

import com.youngsix6.betterpowermenu.config.ActionType;
import com.youngsix6.betterpowermenu.util.ModuleLog;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** 在 SystemUI 进程内执行快捷动作，并将所有失败限制在模块内部。 */
public final class ActionExecutor {

    private static final String LOG = "Action";
    private static final int FALLBACK_GO_TO_SLEEP_REASON_POWER_BUTTON = 4;
    private static final long SYSTEM_UI_RESTART_DELAY_MS = 450L;

    private ActionExecutor() {
    }

    /** 从设置所选动作统一分派，未知值严格按失败处理。 */
    public static boolean execute(Context context, ActionType action) {
        if (action == null) {
            ModuleLog.w(LOG, "拒绝执行空动作");
            return false;
        }
        switch (action) {
            case LOCK:
                return lockScreen(context);
            case SCREEN_OFF:
                return turnScreenOff(context);
            case DO_NOT_DISTURB:
                return toggleDoNotDisturb(context);
            case SOFT_RESTART:
                return softRestartSystemUi(context);
            case AIRPLANE:
                return toggleAirplaneMode(context);
            case REBOOT_RECOVERY:
            case REBOOT_EDL:
            case REBOOT_BOOTLOADER:
                ModuleLog.w(LOG, "Root 重启动作必须通过异步通道执行: " + action.key());
                return false;
            default:
                ModuleLog.w(LOG, "未支持的动作: " + action);
                return false;
        }
    }

    /** 仅关屏（等效电源键短按）。 */
    public static boolean turnScreenOff(Context context) {
        ModuleLog.i(LOG, "开始执行: screen_off");
        if (context == null) {
            ModuleLog.w(LOG, "screen_off 失败: Context 为空");
            return false;
        }

        try {
            PowerManager powerManager =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                ModuleLog.w(LOG, "screen_off 失败: PowerManager 为空");
                return false;
            }
            boolean success = invokeGoToSleep(powerManager, resolvePowerButtonReason());
            ModuleLog.i(LOG, "执行结束: screen_off, success=" + success);
            return success;
        } catch (Throwable t) {
            ModuleLog.e(LOG, "screen_off 发生未预期异常", rootCause(t));
            return false;
        }
    }

    /** 切换系统免打扰，不改动铃声/振动档位，避免与实体音量键状态不一致。 */
    public static boolean toggleDoNotDisturb(Context context) {
        ModuleLog.i(LOG, "开始执行: do_not_disturb");
        if (context == null) {
            ModuleLog.w(LOG, "do_not_disturb 失败: Context 为空");
            return false;
        }

        try {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) {
                ModuleLog.w(LOG, "do_not_disturb 失败: NotificationManager 为空");
                return false;
            }

            int current = notificationManager.getCurrentInterruptionFilter();
            if (current == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                ModuleLog.w(LOG, "do_not_disturb 失败: 当前中断过滤器未知");
                return false;
            }
            int next = current == NotificationManager.INTERRUPTION_FILTER_ALL
                    ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    : NotificationManager.INTERRUPTION_FILTER_ALL;

            notificationManager.setInterruptionFilter(next);
            int actual = notificationManager.getCurrentInterruptionFilter();
            boolean success = actual == next;
            ModuleLog.i(LOG, "执行结束: do_not_disturb, from=" + current
                    + ", requested=" + next + ", actual=" + actual
                    + ", success=" + success);
            return success;
        } catch (SecurityException securityError) {
            ModuleLog.e(LOG, "do_not_disturb 权限不足", securityError);
            return false;
        } catch (Throwable t) {
            ModuleLog.e(LOG, "do_not_disturb 发生未预期异常", rootCause(t));
            return false;
        }
    }

    /** 立即锁屏；优先调用窗口管理服务，失败时再尝试 ROM 扩展和休眠降级。 */
    public static boolean lockScreen(Context context) {
        ModuleLog.i(LOG, "开始执行: lock_screen");
        if (context == null) {
            ModuleLog.w(LOG, "lock_screen 失败: Context 为空");
            return false;
        }

        Throwable windowManagerError = null;
        try {
            Class<?> globalClass = Class.forName("android.view.WindowManagerGlobal");
            Method getter = globalClass.getDeclaredMethod("getWindowManagerService");
            getter.setAccessible(true);
            Object windowManager = getter.invoke(null);
            if (windowManager != null) {
                Method lockNow = findMethod(windowManager.getClass(), "lockNow", Bundle.class);
                lockNow.invoke(windowManager, new Object[]{null});
                ModuleLog.i(LOG, "执行结束: lock_screen, route=WindowManager.lockNow, success=true");
                return true;
            }
        } catch (Throwable t) {
            windowManagerError = rootCause(t);
            ModuleLog.d(LOG, "WindowManager.lockNow 不可用: "
                    + windowManagerError.getClass().getSimpleName());
        }

        try {
            Object keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                Method lock = findMethod(keyguardManager.getClass(), "lock");
                lock.invoke(keyguardManager);
                ModuleLog.i(LOG, "执行结束: lock_screen, route=KeyguardManager.lock, success=true");
                return true;
            }
        } catch (Throwable keyguardError) {
            ModuleLog.d(LOG, "KeyguardManager.lock 不可用: "
                    + rootCause(keyguardError).getClass().getSimpleName());
        }

        // 最后降级为休眠；是否立即要求凭据由用户的系统锁屏策略决定。
        try {
            PowerManager powerManager =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean success = powerManager != null
                    && invokeGoToSleep(powerManager, resolvePowerButtonReason());
            if (success) {
                ModuleLog.i(LOG, "执行结束: lock_screen, route=goToSleep_fallback, success=true");
            } else {
                ModuleLog.w(LOG, "执行结束: lock_screen, success=false");
            }
            return success;
        } catch (Throwable t) {
            Throwable cause = rootCause(t);
            if (windowManagerError != null) {
                cause.addSuppressed(windowManagerError);
            }
            ModuleLog.e(LOG, "lock_screen 所有执行路径均失败", cause);
            return false;
        }
    }

    /** 切换飞行模式；优先调用系统服务，失败后兼容设置项 + 广播方案。 */
    public static boolean toggleAirplaneMode(Context context) {
        ModuleLog.i(LOG, "开始执行: airplane_mode");
        if (context == null) {
            ModuleLog.w(LOG, "airplane_mode 失败: Context 为空");
            return false;
        }

        int current;
        try {
            current = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0);
        } catch (Throwable t) {
            ModuleLog.e(LOG, "airplane_mode 读取当前状态失败", rootCause(t));
            return false;
        }
        boolean enable = current != 1;

        try {
            Object connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                Method setter = findMethod(connectivity.getClass(),
                        "setAirplaneMode", boolean.class);
                setter.invoke(connectivity, enable);
                ModuleLog.i(LOG, "执行结束: airplane_mode, route=ConnectivityManager"
                        + ", from=" + current + ", to=" + (enable ? 1 : 0)
                        + ", success=true");
                return true;
            }
        } catch (Throwable serviceError) {
            ModuleLog.d(LOG, "ConnectivityManager.setAirplaneMode 不可用: "
                    + rootCause(serviceError).getClass().getSimpleName());
        }

        try {
            int next = enable ? 1 : 0;
            boolean stored = Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, next);
            if (!stored) {
                ModuleLog.w(LOG, "airplane_mode 设置项写入返回 false");
                return false;
            }

            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", enable);
            context.sendBroadcast(intent);
            ModuleLog.i(LOG, "执行结束: airplane_mode, route=settings_broadcast"
                    + ", from=" + current + ", to=" + next + ", success=true");
            return true;
        } catch (SecurityException securityError) {
            ModuleLog.e(LOG, "airplane_mode 权限不足；设置项可能已部分更新", securityError);
            return false;
        } catch (Throwable t) {
            ModuleLog.e(LOG, "airplane_mode 兼容路径失败；设置项可能已部分更新",
                    rootCause(t));
            return false;
        }
    }

    /**
     * 软重启仅结束当前 com.android.systemui 进程，由系统看门狗自动拉起。
     * 延迟执行，为关闭电源菜单和绘制反馈留出时间。
     */
    public static boolean softRestartSystemUi(Context context) {
        ModuleLog.i(LOG, "开始执行: soft_restart_systemui");
        if (context == null || !"com.android.systemui".equals(context.getPackageName())) {
            ModuleLog.w(LOG, "拒绝软重启: 当前 Context 不是 SystemUI");
            return false;
        }
        try {
            int pid = Process.myPid();
            boolean posted = new Handler(Looper.getMainLooper()).postDelayed(() -> {
                ModuleLog.i(LOG, "执行结束: soft_restart_systemui, pid=" + pid);
                Process.killProcess(pid);
            }, SYSTEM_UI_RESTART_DELAY_MS);
            if (!posted) {
                ModuleLog.w(LOG, "soft_restart_systemui 任务提交失败");
            }
            return posted;
        } catch (Throwable error) {
            ModuleLog.e(LOG, "soft_restart_systemui 失败", error);
            return false;
        }
    }

    private static int resolvePowerButtonReason() {
        try {
            Field field = PowerManager.class.getDeclaredField(
                    "GO_TO_SLEEP_REASON_POWER_BUTTON");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Throwable t) {
            ModuleLog.d(LOG, "未读取到休眠原因常量，使用兼容值 "
                    + FALLBACK_GO_TO_SLEEP_REASON_POWER_BUTTON);
            return FALLBACK_GO_TO_SLEEP_REASON_POWER_BUTTON;
        }
    }

    /** 依次兼容常见的三参数、双参数和单参数隐藏 API。 */
    private static boolean invokeGoToSleep(PowerManager powerManager, int reason) {
        long uptime = SystemClock.uptimeMillis();
        Throwable lastError = null;

        try {
            Method method = findMethod(PowerManager.class, "goToSleep",
                    long.class, int.class, int.class);
            method.invoke(powerManager, uptime, reason, 0);
            ModuleLog.d(LOG, "goToSleep 使用三参数签名");
            return true;
        } catch (Throwable t) {
            lastError = rootCause(t);
        }

        try {
            Method method = findMethod(PowerManager.class, "goToSleep",
                    long.class, int.class);
            method.invoke(powerManager, uptime, reason);
            ModuleLog.d(LOG, "goToSleep 使用双参数签名");
            return true;
        } catch (Throwable t) {
            lastError = rootCause(t);
        }

        try {
            Method method = findMethod(PowerManager.class, "goToSleep", long.class);
            method.invoke(powerManager, uptime);
            ModuleLog.d(LOG, "goToSleep 使用单参数签名");
            return true;
        } catch (Throwable t) {
            lastError = rootCause(t);
        }

        ModuleLog.e(LOG, "goToSleep 所有已知签名均不可用", lastError);
        return false;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Throwable rootCause(Throwable throwable) {
        if (throwable instanceof InvocationTargetException
                && ((InvocationTargetException) throwable).getTargetException() != null) {
            return ((InvocationTargetException) throwable).getTargetException();
        }
        return throwable;
    }
}
