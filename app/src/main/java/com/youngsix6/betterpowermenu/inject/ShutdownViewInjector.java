package com.youngsix6.betterpowermenu.inject;

import android.content.Context;
import android.graphics.RectF;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.youngsix6.betterpowermenu.action.ActionExecutor;
import com.youngsix6.betterpowermenu.action.PowerMenuCloser;
import com.youngsix6.betterpowermenu.action.RootActionClient;
import com.youngsix6.betterpowermenu.config.ActionType;
import com.youngsix6.betterpowermenu.config.ModuleSettings;
import com.youngsix6.betterpowermenu.config.SettingsReader;
import com.youngsix6.betterpowermenu.ui.PowerMenuAnimationPack;
import com.youngsix6.betterpowermenu.ui.SideSwipeBarView;
import com.youngsix6.betterpowermenu.util.ModuleLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 将原版滑条左移，并在右侧注入一根快捷滑条。 */
public final class ShutdownViewInjector {

    private static final String LOG = "Inject";
    private static final String FIELD_CONTAINER = "mOplusShutdownViewContainer";
    private static final String TARGET_CLASS =
            "com.oplus.systemui.shutdown.ShutdownViewControl";
    private static final String ORIGINAL_VIEW_CLASS =
            "com.oplus.systemui.shutdown.OplusShutdownView";
    private static final String EXTRA_BAR_OFFSET =
            "com.youngsix6.betterpowermenu.extra.BAR_OFFSET";
    private static final String EXTRA_HIDE_EMERGENCY =
            "com.youngsix6.betterpowermenu.extra.HIDE_EMERGENCY";

    /** 仅设置到模块自己创建的 View 上，不覆盖 SystemUI 的 tag。 */
    private static final String TAG_EXTRA_BAR =
            "com.youngsix6.betterpowermenu:extra_bar:v4";

    private static boolean sInstalled;
    private static boolean sOriginalViewHooksInstalled;

    private ShutdownViewInjector() {
    }

    /** 注册 Hook；返回是否至少成功 Hook 到一个同名方法。 */
    public static synchronized boolean install(ClassLoader classLoader) {
        if (sInstalled) {
            ModuleLog.d(LOG, "注入 Hook 已安装，忽略重复请求");
            return true;
        }

        try {
            Class<?> targetClass = XposedHelpers.findClassIfExists(TARGET_CLASS, classLoader);
            if (targetClass == null) {
                ModuleLog.w(LOG, "未找到目标类: " + TARGET_CLASS);
                return false;
            }

            sOriginalViewHooksInstalled = installOriginalViewHooks(classLoader);

            int initHookCount = 0;
            try {
                Set<XC_MethodHook.Unhook> initHooks = XposedBridge.hookAllMethods(
                        targetClass,
                        "initShutdownView",
                        new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                ModuleLog.w(LOG, "initShutdownView 原方法失败，跳过注入",
                                        param.getThrowable());
                                return;
                            }
                            if (param.thisObject == null) {
                                ModuleLog.w(LOG, "initShutdownView 返回后 thisObject 为空");
                                return;
                            }
                            injectSafely(param.thisObject);
                        }
                        });
                initHookCount = initHooks == null ? 0 : initHooks.size();
            } catch (Throwable initHookError) {
                ModuleLog.w(LOG, "initShutdownView Hook 不可用，继续尝试 getter 备用路径",
                        initHookError);
            }

            // 备用路径：原版对话框会调用 getter。即使初始化方法签名或名称变化，
            // 只要 getter 仍在，就可从原方法结果安全取得容器。
            int getterHookCount = 0;
            try {
                Set<XC_MethodHook.Unhook> getterHooks = XposedBridge.hookAllMethods(
                        targetClass,
                        "getShutdownViewContainer",
                        new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                ModuleLog.d(LOG, "容器 getter 原方法失败，跳过备用注入");
                                return;
                            }
                            Object result = param.getResult();
                            if (result instanceof ViewGroup) {
                                injectContainerSafely((ViewGroup) result);
                            } else if (param.thisObject != null) {
                                injectSafely(param.thisObject);
                            }
                        }
                        });
                getterHookCount = getterHooks == null ? 0 : getterHooks.size();
            } catch (Throwable getterHookError) {
                ModuleLog.w(LOG, "getShutdownViewContainer Hook 不可用", getterHookError);
            }

            if (initHookCount + getterHookCount == 0) {
                ModuleLog.w(LOG, "目标类存在，但初始化方法和容器 getter 均未找到");
                return false;
            }

            sInstalled = true;
            ModuleLog.i(LOG, "注入 Hook 注册成功: class=" + TARGET_CLASS
                    + ", initHooks=" + initHookCount
                    + ", getterHooks=" + getterHookCount
                    + ", originalViewHooks=" + sOriginalViewHooksInstalled);
            return true;
        } catch (Throwable t) {
            ModuleLog.e(LOG, "注入 Hook 注册失败", t);
            return false;
        }
    }

    /**
     * 只移动原版关机条的绘制坐标，不平移整个 OplusShutdownView，
     * 因此紧急呼叫和手动锁定等系统元素仍保持原位。
     */
    private static boolean installOriginalViewHooks(ClassLoader classLoader) {
        try {
            Class<?> viewClass = XposedHelpers.findClassIfExists(
                    ORIGINAL_VIEW_CLASS, classLoader);
            if (viewClass == null) {
                ModuleLog.w(LOG, "未找到原版关机 View 类，无法安全拆分位置");
                return false;
            }

            Set<XC_MethodHook.Unhook> barHooks = XposedBridge.hookAllMethods(
                    viewClass, "drawBar", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                float offset = originalBarOffset(param.thisObject);
                                if (Math.abs(offset) < 0.5f) {
                                    return;
                                }
                                int width = XposedHelpers.getIntField(
                                        param.thisObject, "mOplusShutdownViewWidth");
                                param.setObjectExtra("bpm_original_width", width);
                                XposedHelpers.setIntField(param.thisObject,
                                        "mOplusShutdownViewWidth",
                                        Math.max(1, width + Math.round(offset * 2f)));
                            } catch (Throwable error) {
                                ModuleLog.e(LOG, "调整原版滑条绘制坐标失败", error);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object original = param.getObjectExtra("bpm_original_width");
                            if (!(original instanceof Integer)) {
                                return;
                            }
                            try {
                                XposedHelpers.setIntField(param.thisObject,
                                        "mOplusShutdownViewWidth", (Integer) original);
                            } catch (Throwable error) {
                                ModuleLog.e(LOG, "恢复原版 View 宽度失败", error);
                            }
                        }
                    });

            Set<XC_MethodHook.Unhook> emergencyHooks = XposedBridge.hookAllMethods(
                    viewClass, "drawEmergencyBar", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                float offset = originalBarOffset(param.thisObject);
                                if (Math.abs(offset) < 0.5f) {
                                    return;
                                }
                                Object value = XposedHelpers.getObjectField(
                                        param.thisObject, "mBarRectF");
                                if (!(value instanceof RectF)) {
                                    return;
                                }
                                RectF rect = (RectF) value;
                                param.setObjectExtra("bpm_bar_left", rect.left);
                                param.setObjectExtra("bpm_bar_right", rect.right);
                                rect.offset(-offset, 0f);
                            } catch (Throwable error) {
                                ModuleLog.e(LOG, "临时恢复紧急呼叫中央坐标失败", error);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object left = param.getObjectExtra("bpm_bar_left");
                            Object right = param.getObjectExtra("bpm_bar_right");
                            if (!(left instanceof Float) || !(right instanceof Float)) {
                                return;
                            }
                            try {
                                Object value = XposedHelpers.getObjectField(
                                        param.thisObject, "mBarRectF");
                                if (value instanceof RectF) {
                                    ((RectF) value).left = (Float) left;
                                    ((RectF) value).right = (Float) right;
                                }
                            } catch (Throwable error) {
                                ModuleLog.e(LOG, "恢复原版滑条矩形失败", error);
                            }
                        }
                    });

            Set<XC_MethodHook.Unhook> emergencyVisibilityHooks =
                    XposedBridge.hookAllMethods(viewClass, "isShowEmergency",
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    try {
                                        Object hidden = XposedHelpers.getAdditionalInstanceField(
                                                param.thisObject, EXTRA_HIDE_EMERGENCY);
                                        if (Boolean.TRUE.equals(hidden)) {
                                            param.setResult(false);
                                        }
                                    } catch (Throwable error) {
                                        ModuleLog.e(LOG, "读取紧急呼叫设置失败，保留原版结果", error);
                                    }
                                }
                            });

            boolean success = barHooks != null && !barHooks.isEmpty()
                    && emergencyHooks != null && !emergencyHooks.isEmpty()
                    && emergencyVisibilityHooks != null
                    && !emergencyVisibilityHooks.isEmpty();
            if (!success) {
                ModuleLog.w(LOG, "原版 View Hook 不完整，为避免移动紧急呼叫将禁用注入");
            }
            return success;
        } catch (Throwable error) {
            ModuleLog.e(LOG, "安装原版 View 分离 Hook 失败", error);
            return false;
        }
    }

    private static float originalBarOffset(Object view) {
        Object value = XposedHelpers.getAdditionalInstanceField(view, EXTRA_BAR_OFFSET);
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }

    private static void injectSafely(Object control) {
        try {
            ViewGroup container = resolveContainer(control);
            if (container == null) {
                ModuleLog.w(LOG, "未取得关机界面 ViewGroup，跳过本次注入");
                return;
            }

            injectContainerSafely(container);
        } catch (Throwable t) {
            ModuleLog.e(LOG, "解析关机界面容器失败", t);
        }
    }

    private static void injectContainerSafely(ViewGroup container) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            try {
                boolean posted = container.post(() -> injectIntoContainerSafely(container));
                if (posted) {
                    ModuleLog.d(LOG, "当前非主线程，已将注入切换到 UI 线程");
                } else {
                    ModuleLog.w(LOG, "无法投递到 UI 线程，跳过本次注入");
                }
            } catch (Throwable postError) {
                ModuleLog.e(LOG, "切换到 UI 线程失败，跳过本次注入", postError);
            }
            return;
        }
        injectIntoContainerSafely(container);
    }

    private static void injectIntoContainerSafely(ViewGroup container) {
        try {
            injectIntoContainer(container);
        } catch (Throwable t) {
            ModuleLog.e(LOG, "注入快捷滑条失败，SystemUI 原界面保持可用", t);
        }
    }

    /**
     * 优先使用目标类公开 getter，字段名变化时更耐受；再兼容当前 ROM 的已知字段。
     */
    private static ViewGroup resolveContainer(Object control) {
        Throwable knownGetterError = null;
        try {
            Object value = XposedHelpers.callMethod(control, "getShutdownViewContainer");
            if (value instanceof ViewGroup) {
                ModuleLog.d(LOG, "通过 getShutdownViewContainer() 取得容器");
                return (ViewGroup) value;
            }
            if (value != null) {
                ModuleLog.w(LOG, "getShutdownViewContainer() 返回非 ViewGroup: "
                        + value.getClass().getName());
            }
        } catch (Throwable getterError) {
            knownGetterError = getterError;
        }

        Throwable knownFieldError = null;
        try {
            Field field = XposedHelpers.findField(control.getClass(), FIELD_CONTAINER);
            Object value = field.get(control);
            if (value instanceof ViewGroup) {
                ModuleLog.d(LOG, "通过字段 " + FIELD_CONTAINER + " 取得容器");
                return (ViewGroup) value;
            }
            if (value != null) {
                ModuleLog.w(LOG, FIELD_CONTAINER + " 不是 ViewGroup: "
                        + value.getClass().getName());
            }
        } catch (Throwable fieldError) {
            knownFieldError = fieldError;
        }

        // 小版本改名兼容：只考察名称明确包含 shutdown 且形似 getter/容器字段的成员。
        // 多个候选时宁可放弃，避免把控件加到错误 ViewGroup。
        ViewGroup compatible = findCompatibleContainer(control);
        if (compatible != null) {
            return compatible;
        }

        ModuleLog.w(LOG, "未找到可信关机容器: getterError="
                + simpleError(knownGetterError) + ", fieldError="
                + simpleError(knownFieldError));
        return null;
    }

    private static ViewGroup findCompatibleContainer(Object control) {
        LinkedHashSet<ViewGroup> candidates = new LinkedHashSet<>();
        Class<?> current = control.getClass();
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (method.getParameterTypes().length != 0
                        || !name.startsWith("get")
                        || !name.contains("shutdown")
                        || (!name.contains("container") && !name.contains("view"))) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(control);
                    if (value instanceof ViewGroup) {
                        candidates.add((ViewGroup) value);
                    }
                } catch (Throwable ignored) {
                    // 单个候选失败不影响继续寻找；最后统一安全退出。
                }
            }

            for (Field field : current.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("shutdown")
                        || (!name.contains("container") && !name.contains("view"))) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(control);
                    if (value instanceof ViewGroup) {
                        candidates.add((ViewGroup) value);
                    }
                } catch (Throwable ignored) {
                    // 同上。
                }
            }
            current = current.getSuperclass();
        }

        if (candidates.size() == 1) {
            ViewGroup result = candidates.iterator().next();
            ModuleLog.i(LOG, "使用兼容成员发现关机容器: "
                    + result.getClass().getName());
            return result;
        }
        if (candidates.size() > 1) {
            ModuleLog.w(LOG, "发现多个可能的关机容器，为避免错误注入已放弃: count="
                    + candidates.size());
        }
        return null;
    }

    /** 直接向目标 ConstraintLayout 加入一根居中约束的增强条；失败时恢复原版位置。 */
    private static void injectIntoContainer(ViewGroup container) throws Throwable {
        Context context = container.getContext();
        if (context == null) {
            ModuleLog.w(LOG, "容器 Context 为空，跳过注入");
            return;
        }

        if (!sOriginalViewHooksInstalled) {
            ModuleLog.w(LOG, "原版滑条分离 Hook 不可用，保留系统原始界面");
            return;
        }

        View originalBarView = findOriginalShutdownView(container, context);
        if (originalBarView == null) {
            ModuleLog.w(LOG, "未找到原版 OplusShutdownView，为避免出现三条或错位已放弃注入");
            return;
        }

        ModuleSettings settings = SettingsReader.read(context);
        try {
            PowerMenuAnimationPack.apply(container, context);
        } catch (Throwable animationError) {
            ModuleLog.w(LOG, "模块动画包加载异常，继续使用系统原版动画: "
                    + animationError.getClass().getSimpleName());
        }
        XposedHelpers.setAdditionalInstanceField(
                originalBarView, EXTRA_HIDE_EMERGENCY, settings.hideEmergency);
        originalBarView.invalidate();

        View existing = container.findViewWithTag(TAG_EXTRA_BAR);
        if (existing instanceof SideSwipeBarView) {
            configureExtraBar((SideSwipeBarView) existing, context, settings);
            ModuleLog.i(LOG, "已刷新现有增强滑条设置: " + settings.summary());
            return;
        } else if (existing != null) {
            ModuleLog.w(LOG, "发现未知类型的同标签 View，拒绝重复注入: "
                    + existing.getClass().getName());
            return;
        }

        int barWidth = SideSwipeBarView.preferredWidth(context);
        int preferredHeight = SideSwipeBarView.preferredHeight(context);
        int safeHeight = Math.max(dp(context, 220),
                context.getResources().getDisplayMetrics().heightPixels - dp(context, 24));
        int barHeight = Math.min(preferredHeight, safeHeight);
        int screenWidth = Math.max(container.getWidth(),
                context.getResources().getDisplayMetrics().widthPixels);
        int desiredOffset = (barWidth / 2) + dp(context, 16);
        int maxOffset = Math.max(0, (screenWidth - barWidth - dp(context, 24)) / 2);
        int dualOffset = Math.min(desiredOffset, maxOffset);
        if (dualOffset < barWidth / 2) {
            ModuleLog.w(LOG, "可用宽度不足以安全放置双滑条，跳过注入: width="
                    + screenWidth + ", barWidth=" + barWidth);
            return;
        }

        ViewGroup.LayoutParams extraBarParams = createCenteredLayoutParams(
                container, barWidth, barHeight);
        if (extraBarParams == null) {
            ModuleLog.w(LOG, "无法创建与目标容器兼容的居中布局参数，放弃注入");
            return;
        }

        // 只新增一根：原版负责重启/关机，增强条动作由设置页决定。
        SideSwipeBarView extraBar = createExtraBar(context, settings);
        extraBar.setTag(TAG_EXTRA_BAR);
        extraBar.setElevation(dp(context, 2));
        extraBar.setTranslationX(dualOffset);

        OriginalLayoutShift originalShift = shiftOriginalLayout(
                container, context, originalBarView, -dualOffset);

        try {
            container.addView(extraBar, extraBarParams);
        } catch (Throwable addError) {
            originalShift.restore();
            if (extraBar.getParent() == container) {
                container.removeView(extraBar);
            }
            throw addError;
        }

        // 布局完成后校验未平移前的基准中心，防止约束失效后再次挤到左上角。
        extraBar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            private boolean checked;

            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (checked) {
                    return;
                }
                checked = true;
                extraBar.removeOnLayoutChangeListener(this);
                int actualWidth = right - left;
                int actualHeight = bottom - top;
                int containerWidth = container.getWidth();
                int containerHeight = container.getHeight();
                float baseCenterX = (left + right) / 2f;
                float baseCenterY = (top + bottom) / 2f;
                float expectedCenterX = containerWidth / 2f;
                float expectedCenterY = containerHeight / 2f;
                int tolerance = dp(context, 3);
                boolean valid = actualWidth > 0 && actualHeight > 0
                        && Math.abs(actualWidth - barWidth) <= tolerance
                        && Math.abs(actualHeight - barHeight) <= tolerance
                        && containerWidth > 0 && containerHeight > 0
                        && Math.abs(baseCenterX - expectedCenterX) <= tolerance
                        && Math.abs(baseCenterY - expectedCenterY) <= tolerance;
                if (!valid) {
                    try {
                        if (extraBar.getParent() == container) {
                            container.removeView(extraBar);
                        }
                        originalShift.restore();
                    } catch (Throwable rollbackError) {
                        ModuleLog.e(LOG, "布局校验失败后的回滚异常", rollbackError);
                    }
                    ModuleLog.w(LOG, "增强条未被居中约束，已回滚: bar="
                            + actualWidth + "x" + actualHeight + "@(" + left + "," + top
                            + "), baseCenter=" + Math.round(baseCenterX) + "x"
                            + Math.round(baseCenterY) + ", container=" + containerWidth
                            + "x" + containerHeight);
                } else {
                    ModuleLog.i(LOG, "双滑条布局校验成功: originalCenterOffset="
                            + (-dualOffset) + ", extraCenterOffset=" + dualOffset
                            + ", bar=" + actualWidth + "x" + actualHeight
                            + ", container=" + containerWidth + "x" + containerHeight);
                }
            }
        });

        ModuleLog.d(LOG, "双滑条布局已提交，等待布局校验: originalOffset=" + (-dualOffset)
                + ", extraOffset=" + dualOffset + ", container="
                + container.getClass().getName() + ", size=" + container.getWidth()
                + "x" + container.getHeight());
    }

    private static SideSwipeBarView createExtraBar(Context context, ModuleSettings settings) {
        SideSwipeBarView bar = new SideSwipeBarView(
                context, SideSwipeBarView.Direction.RIGHT,
                settings.upAction.displayName(), settings.downAction.displayName(),
                iconFor(settings.upAction), iconFor(settings.downAction),
                settings.upColor, settings.downColor);
        bar.setId(View.generateViewId());
        configureExtraBar(bar, context, settings);
        return bar;
    }

    private static void configureExtraBar(SideSwipeBarView bar, Context context,
                                          ModuleSettings settings) {
        bar.configureActions(
                settings.upAction.displayName(), settings.downAction.displayName(),
                iconFor(settings.upAction), iconFor(settings.downAction),
                settings.upColor, settings.downColor);
        bar.setOnTriggerListener((direction, slideDown) -> {
            ActionType action = slideDown ? settings.downAction : settings.upAction;
            if (action.requiresRoot()) {
                ModuleLog.i(LOG, "增强滑条已提交异步 Root 动作: action=" + action.key());
                RootActionClient.executeAsync(context, action, success -> {
                    ModuleLog.i(LOG, "增强滑条 Root 动作完成: action="
                            + action.key() + ", success=" + success);
                    if (success) {
                        PowerMenuCloser.dismiss(context);
                    }
                });
                return;
            }
            boolean success = ActionExecutor.execute(context, action);
            ModuleLog.i(LOG, "增强滑条动作完成: action="
                    + action.key() + ", success=" + success);
            if (success) {
                PowerMenuCloser.dismiss(context);
            }
        });
    }

    private static SideSwipeBarView.IconType iconFor(ActionType action) {
        switch (action) {
            case LOCK:
                return SideSwipeBarView.IconType.LOCK;
            case SCREEN_OFF:
                return SideSwipeBarView.IconType.POWER;
            case DO_NOT_DISTURB:
                return SideSwipeBarView.IconType.DO_NOT_DISTURB;
            case SOFT_RESTART:
                return SideSwipeBarView.IconType.RESTART;
            case REBOOT_RECOVERY:
            case REBOOT_EDL:
            case REBOOT_BOOTLOADER:
                return SideSwipeBarView.IconType.RESTART;
            case AIRPLANE:
            default:
                return SideSwipeBarView.IconType.AIRPLANE;
        }
    }

    private static View findOriginalShutdownView(ViewGroup container, Context context) {
        int id = resourceId(context, "oplus_shutdown_view", "id");
        if (id != 0) {
            View byId = container.findViewById(id);
            if (isOriginalShutdownView(byId)) {
                return byId;
            }
        }
        return findOriginalShutdownViewRecursive(container);
    }

    private static View findOriginalShutdownViewRecursive(View view) {
        if (isOriginalShutdownView(view)) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View result = findOriginalShutdownViewRecursive(group.getChildAt(i));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static boolean isOriginalShutdownView(View view) {
        return view != null && view.getClass().getName().endsWith(".OplusShutdownView");
    }

    /** 同步移动原版自绘 View 与四个独立 Lottie 图标，并保存回滚位置。 */
    private static OriginalLayoutShift shiftOriginalLayout(ViewGroup container, Context context,
                                                           View originalView, float offsetX) {
        OriginalLayoutShift shift = new OriginalLayoutShift();
        shift.setOriginalBarOffset(originalView, offsetX);
        String[] siblingIds = {
                "reboot_animation_view",
                "global_actions_arrow_up_view",
                "global_actions_arrow_down_view",
                "shutdown_animation_view"
        };
        for (String name : siblingIds) {
            int id = resourceId(context, name, "id");
            if (id == 0) {
                continue;
            }
            View sibling = container.findViewById(id);
            if (sibling != null) {
                shift.add(sibling, offsetX);
            }
        }
        return shift;
    }

    /** 使用容器现有子 View 的真实 LayoutParams 类型，把增强条约束到父容器中心。 */
    private static ViewGroup.LayoutParams createCenteredLayoutParams(ViewGroup container,
                                                                      int width, int height) {
        try {
            ViewGroup.LayoutParams sample = null;
            for (int i = 0; i < container.getChildCount(); i++) {
                sample = container.getChildAt(i).getLayoutParams();
                if (sample != null) {
                    break;
                }
            }
            if (sample == null) {
                if (container instanceof FrameLayout) {
                    return new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
                }
                return null;
            }

            Class<?> lpClass = sample.getClass();
            Object object = lpClass.getConstructor(int.class, int.class).newInstance(
                    width, height);
            if (!(object instanceof ViewGroup.LayoutParams)) {
                return null;
            }

            String className = lpClass.getName();
            if (className.contains("ConstraintLayout$LayoutParams")) {
                boolean constrained = setIntFieldIfPresent(lpClass, object, "startToStart", 0)
                        & setIntFieldIfPresent(lpClass, object, "endToEnd", 0)
                        & setIntFieldIfPresent(lpClass, object, "topToTop", 0)
                        & setIntFieldIfPresent(lpClass, object, "bottomToBottom", 0);
                if (!constrained) {
                    ModuleLog.w(LOG, "ConstraintLayout 参数字段不完整，拒绝冒险注入: "
                            + className);
                    return null;
                }
            } else if (object instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) object).gravity = Gravity.CENTER;
            } else {
                // 不认识的 ViewGroup 若没有明确居中语义，宁可不显示。
                ModuleLog.w(LOG, "不支持的容器 LayoutParams，拒绝可能的左上角注入: "
                        + className);
                return null;
            }
            return (ViewGroup.LayoutParams) object;
        } catch (Throwable layoutError) {
            ModuleLog.e(LOG, "创建目标容器 LayoutParams 失败", layoutError);
            return null;
        }
    }

    private static boolean setIntFieldIfPresent(Class<?> type, Object target,
                                                String name, int value) {
        try {
            Field field = type.getField(name);
            field.setInt(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int resourceId(Context context, String name, String type) {
        try {
            int id = context.getResources().getIdentifier(
                    name, type, context.getPackageName());
            if (id == 0) {
                id = context.getResources().getIdentifier(
                        name, type, "com.android.systemui");
            }
            return id;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static final class OriginalLayoutShift {
        private final List<View> views = new ArrayList<>();
        private final List<Float> originalTranslations = new ArrayList<>();
        private boolean restored;
        private View originalBarView;
        private Object originalBarOffset;

        void setOriginalBarOffset(View view, float offsetX) {
            originalBarView = view;
            originalBarOffset = XposedHelpers.getAdditionalInstanceField(
                    view, EXTRA_BAR_OFFSET);
            XposedHelpers.setAdditionalInstanceField(view, EXTRA_BAR_OFFSET, offsetX);
            view.invalidate();
        }

        void add(View view, float deltaX) {
            views.add(view);
            originalTranslations.add(view.getTranslationX());
            view.setTranslationX(view.getTranslationX() + deltaX);
        }

        void restore() {
            if (restored) {
                return;
            }
            restored = true;
            for (int i = 0; i < views.size(); i++) {
                views.get(i).setTranslationX(originalTranslations.get(i));
            }
            if (originalBarView != null) {
                if (originalBarOffset == null) {
                    XposedHelpers.removeAdditionalInstanceField(
                            originalBarView, EXTRA_BAR_OFFSET);
                } else {
                    XposedHelpers.setAdditionalInstanceField(
                            originalBarView, EXTRA_BAR_OFFSET, originalBarOffset);
                }
                originalBarView.invalidate();
            }
        }
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String simpleError(Throwable throwable) {
        return throwable == null ? "none" : throwable.getClass().getSimpleName();
    }
}
