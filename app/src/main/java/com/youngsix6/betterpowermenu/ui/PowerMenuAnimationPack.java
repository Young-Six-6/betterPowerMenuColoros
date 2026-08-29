package com.youngsix6.betterpowermenu.ui;

import android.content.Context;
import android.content.res.AssetManager;
import android.view.View;
import android.view.ViewGroup;

import com.youngsix6.betterpowermenu.util.ModuleLog;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import de.robv.android.xposed.XposedHelpers;

/**
 * 将模块 assets/powermenu_animations 中的 Lottie 包应用到原版四个动画 View。
 * 包缺失、JSON 非法或 ROM View 类型变化时保留系统原版动画。
 */
public final class PowerMenuAnimationPack {

    private static final String LOG = "AnimationPack";
    private static final String MODULE_PACKAGE = "com.youngsix6.betterpowermenu";
    private static final String ASSET_DIR = "powermenu_animations/";
    private static final String EXTRA_APPLIED =
            "com.youngsix6.betterpowermenu.extra.ANIMATION_PACK_APPLIED";
    private static final int MAX_ASSET_BYTES = 256 * 1024;

    private static volatile boolean sLoadAttempted;
    private static volatile Pack sPack;

    private PowerMenuAnimationPack() {
    }

    public static void apply(ViewGroup container, Context systemUiContext) {
        if (container == null || systemUiContext == null
                || Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(
                container, EXTRA_APPLIED))) {
            return;
        }

        Pack pack = loadPack(systemUiContext);
        if (pack == null) {
            // 本次 SystemUI 生命周期不再重复尝试；系统原版资源保持不变。
            XposedHelpers.setAdditionalInstanceField(container, EXTRA_APPLIED, true);
            return;
        }

        int applied = 0;
        applied += applyOne(container, systemUiContext,
                "reboot_animation_view", pack.rebootJson, "reboot");
        applied += applyOne(container, systemUiContext,
                "shutdown_animation_view", pack.shutdownJson, "shutdown");
        applied += applyOne(container, systemUiContext,
                "global_actions_arrow_up_view", pack.arrowJson, "arrow");
        applied += applyOne(container, systemUiContext,
                "global_actions_arrow_down_view", pack.arrowJson, "arrow");
        XposedHelpers.setAdditionalInstanceField(container, EXTRA_APPLIED, true);

        if (applied == 4) {
            ModuleLog.i(LOG, "模块动画包应用完成: name=" + pack.name
                    + ", views=" + applied);
        } else {
            ModuleLog.w(LOG, "模块动画包仅部分应用，其余保留系统原版: name="
                    + pack.name + ", views=" + applied + "/4");
        }
    }

    private static int applyOne(ViewGroup container, Context context,
                                String idName, String json, String cacheName) {
        if (json == null) {
            return 0;
        }
        try {
            int id = context.getResources().getIdentifier(
                    idName, "id", "com.android.systemui");
            View view = id == 0 ? null : container.findViewById(id);
            if (view == null || !view.getClass().getName().endsWith("LottieAnimationView")) {
                return 0;
            }
            XposedHelpers.callMethod(view, "setAnimationFromJson", json,
                    "betterpowermenu:" + cacheName);
            return 1;
        } catch (Throwable error) {
            ModuleLog.w(LOG, "替换动画失败，保留系统原版: view=" + idName
                    + ", error=" + error.getClass().getSimpleName());
            return 0;
        }
    }

    private static Pack loadPack(Context systemUiContext) {
        if (sLoadAttempted) {
            return sPack;
        }
        synchronized (PowerMenuAnimationPack.class) {
            if (sLoadAttempted) {
                return sPack;
            }
            sLoadAttempted = true;
            try {
                Context moduleContext = systemUiContext.createPackageContext(
                        MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
                AssetManager assets = moduleContext.getAssets();
                JSONObject manifest = new JSONObject(readAsset(assets, "pack.json"));
                if (manifest.optInt("format", 0) != 1) {
                    throw new IllegalArgumentException("unsupported pack format");
                }

                String name = manifest.optString("name", "unnamed");
                String reboot = validatedAnimation(assets,
                        safeFileName(manifest.getString("reboot")));
                String shutdown = validatedAnimation(assets,
                        safeFileName(manifest.getString("shutdown")));
                String arrow = validatedAnimation(assets,
                        safeFileName(manifest.getString("arrow")));
                sPack = new Pack(name, reboot, shutdown, arrow);
                ModuleLog.i(LOG, "模块动画包读取成功: name=" + name);
            } catch (Throwable error) {
                ModuleLog.w(LOG, "模块动画包不可用，继续使用系统原版: "
                        + error.getClass().getSimpleName());
                sPack = null;
            }
            return sPack;
        }
    }

    private static String validatedAnimation(AssetManager assets, String fileName)
            throws Exception {
        String json = readAsset(assets, fileName);
        JSONObject animation = new JSONObject(json);
        if (!animation.has("v") || !animation.has("layers")
                || animation.optInt("w", 0) <= 0 || animation.optInt("h", 0) <= 0) {
            throw new IllegalArgumentException("invalid Lottie structure: " + fileName);
        }
        return json;
    }

    private static String safeFileName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid animation file name");
        }
        return value;
    }

    private static String readAsset(AssetManager assets, String fileName) throws Exception {
        try (InputStream input = assets.open(ASSET_DIR + fileName);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_ASSET_BYTES) {
                    throw new IllegalArgumentException("animation asset too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static final class Pack {
        final String name;
        final String rebootJson;
        final String shutdownJson;
        final String arrowJson;

        Pack(String name, String rebootJson, String shutdownJson, String arrowJson) {
            this.name = name;
            this.rebootJson = rebootJson;
            this.shutdownJson = shutdownJson;
            this.arrowJson = arrowJson;
        }
    }
}
