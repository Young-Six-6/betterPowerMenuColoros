package com.youngsix6.betterpowermenu.config;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 仅供模块自身进程使用的 Root 检测与固定重启命令执行器。 */
public final class RootAccess {

    private static final String TAG = "BetterPowerMenu.Root";
    private static final long CHECK_TIMEOUT_SECONDS = 15L;
    private static final long REBOOT_TIMEOUT_SECONDS = 3L;

    private RootAccess() {
    }

    /** 执行 id 并且确认返回 uid=0，避免仅凭 su 文件存在产生误判。 */
    public static boolean isGranted() {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", "id")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy();
                return false;
            }
            String output = readSmallOutput(process.getInputStream());
            return process.exitValue() == 0 && output.contains("uid=0");
        } catch (Throwable error) {
            Log.d(TAG, "Root 未授权或 su 不可用: " + error.getClass().getSimpleName());
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /** 只接受枚举中的三种 Root 重启目标，不拼接任何外部命令文本。 */
    public static boolean reboot(ActionType action) {
        String command = commandFor(action);
        if (command == null) {
            return false;
        }
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(REBOOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy();
                Log.w(TAG, "Root 重启命令超时: " + action.key());
                return false;
            }
            boolean success = process.exitValue() == 0;
            if (!success) {
                Log.w(TAG, "Root 重启命令失败: " + action.key()
                        + ", exit=" + process.exitValue());
            }
            return success;
        } catch (Throwable error) {
            Log.e(TAG, "Root 重启命令异常: " + action.key(), error);
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String commandFor(ActionType action) {
        if (action == ActionType.REBOOT_RECOVERY) {
            return "/system/bin/setprop sys.powerctl reboot,recovery";
        }
        if (action == ActionType.REBOOT_EDL) {
            return "/system/bin/setprop sys.powerctl reboot,edl";
        }
        if (action == ActionType.REBOOT_BOOTLOADER) {
            return "/system/bin/setprop sys.powerctl reboot,bootloader";
        }
        return null;
    }

    private static String readSmallOutput(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int remaining = 2048;
        while (remaining > 0) {
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count < 0) {
                break;
            }
            output.write(buffer, 0, count);
            remaining -= count;
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
