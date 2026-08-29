package com.youngsix6.betterpowermenu;

import android.os.Build;

import com.youngsix6.betterpowermenu.action.PowerMenuCloser;
import com.youngsix6.betterpowermenu.inject.ShutdownViewInjector;
import com.youngsix6.betterpowermenu.util.ModuleLog;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed 模块入口（关机增强）。
 *
 * <p>注入目标：OPlus / ColorOS SystemUI（{@code com.android.systemui}）。</p>
 *
 * <p>当前功能：
 * <ol>
 *   <li>验证 hook 链路：{@code OplusGlobalActionsDialog.showOrHideDialog} 打日志；</li>
 *   <li>双滑条布局：{@link ShutdownViewInjector} 只左移原版关机/重启滑条，
 *       紧急呼叫保持居中，并在右侧新增可配置滑条。</li>
 * </ol></p>
 */
public class XposedInit implements IXposedHookLoadPackage {

    private static final String LOG = "Entry";

    /** 目标进程：OPlus SystemUI */
    private static final String TARGET_PACKAGE = "com.android.systemui";

    /** 目标类：OPlus 关机/重启对话框（长按电源键显示） */
    private static final String TARGET_CLASS =
            "com.oplus.systemui.shutdown.OplusGlobalActionsDialog";

    private static boolean sInitialized;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        // com.android.systemui 可能存在冒号后缀子进程，只在主进程安装 Hook。
        if (lpparam.processName != null && !TARGET_PACKAGE.equals(lpparam.processName)) {
            ModuleLog.d(LOG, "跳过 SystemUI 子进程: " + lpparam.processName);
            return;
        }

        synchronized (XposedInit.class) {
            if (sInitialized) {
                ModuleLog.d(LOG, "入口已初始化，忽略重复回调");
                return;
            }
            sInitialized = true;
        }

        ModuleLog.i(LOG, "开始初始化: package=" + lpparam.packageName
                + ", process=" + lpparam.processName
                + ", sdk=" + Build.VERSION.SDK_INT
                + ", classLoader=" + lpparam.classLoader);

        // 1. 验证 hook 链路（长按电源 → 关机界面显示）
        boolean dialogHookInstalled = hookShowOrHideDialog(lpparam.classLoader);

        // 2. 调整原版位置并注入一根增强滑条（核心功能）
        boolean injectorInstalled = ShutdownViewInjector.install(lpparam.classLoader);

        ModuleLog.i(LOG, "初始化完成: dialogHook=" + dialogHookInstalled
                + ", injectorHook=" + injectorInstalled);
    }

    /**
     * 验证 hook：{@code OplusGlobalActionsDialog.showOrHideDialog(boolean, boolean)}。
     *
     * <p>该方法在长按电源键、SystemUI 收到 {@code showGlobalActionsMenu()} 后触发，
     * 是关机滑条界面的统一入口（见 {@code GlobalActionsImpl} 调用链）。</p>
     */
    private boolean hookShowOrHideDialog(ClassLoader classLoader) {
        try {
            Class<?> targetClass = XposedHelpers.findClassIfExists(TARGET_CLASS, classLoader);
            if (targetClass == null) {
                ModuleLog.w(LOG, "未找到关机对话框类: " + TARGET_CLASS);
                return false;
            }

            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    targetClass,
                    "showOrHideDialog",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            PowerMenuCloser.remember(param.thisObject);
                            ModuleLog.i(LOG, "关机界面调用: keyguardShowing="
                                    + booleanArg(param.args, 0) + ", deviceProvisioned="
                                    + booleanArg(param.args, 1) + ", argCount="
                                    + (param.args == null ? 0 : param.args.length));
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                ModuleLog.w(LOG, "关机界面原方法抛出异常", param.getThrowable());
                            } else {
                                ModuleLog.d(LOG, "关机界面调用完成");
                            }
                        }
                    });

            if (hooks == null || hooks.isEmpty()) {
                ModuleLog.w(LOG, "类存在但未找到方法: " + TARGET_CLASS + "#showOrHideDialog");
                return false;
            }
            ModuleLog.i(LOG, "Hook 注册成功: " + TARGET_CLASS
                    + "#showOrHideDialog, overloads=" + hooks.size());
            return true;
        } catch (Throwable t) {
            ModuleLog.e(LOG, "关机对话框 Hook 注册失败", t);
            return false;
        }
    }

    private static String booleanArg(Object[] args, int index) {
        if (args == null || index < 0 || index >= args.length) {
            return "n/a";
        }
        Object value = args[index];
        return value instanceof Boolean ? value.toString() : "unexpected(" 
                + (value == null ? "null" : value.getClass().getSimpleName()) + ")";
    }
}
