package com.youngsix6.betterpowermenu.config;

import android.net.Uri;

/** 模块设置的唯一协议定义。 */
public final class SettingsContract {

    public static final String AUTHORITY = "com.youngsix6.betterpowermenu.settings";
    public static final Uri CONFIG_URI = Uri.parse("content://" + AUTHORITY + "/config");
    public static final String PREFERENCES_NAME = "module_settings";

    public static final String HIDE_EMERGENCY = "hide_emergency";
    public static final String UP_ACTION = "up_action";
    public static final String DOWN_ACTION = "down_action";
    public static final String UP_COLOR = "up_color";
    public static final String DOWN_COLOR = "down_color";
    public static final String ROOT_GRANTED = "root_granted";

    public static final String METHOD_ROOT_REBOOT = "root_reboot";
    public static final String RESULT_SUCCESS = "success";

    public static final boolean DEFAULT_HIDE_EMERGENCY = false;
    public static final ActionType DEFAULT_UP_ACTION = ActionType.AIRPLANE;
    public static final ActionType DEFAULT_DOWN_ACTION = ActionType.DO_NOT_DISTURB;
    public static final int DEFAULT_UP_COLOR = 0xFF63C83B;
    public static final int DEFAULT_DOWN_COLOR = 0xFFE93D28;
    public static final boolean DEFAULT_ROOT_GRANTED = false;

    private SettingsContract() {
    }

    /** 自定义颜色始终强制为不透明，避免误设透明后让用户误以为滑条失效。 */
    public static int sanitizeColor(int color, int fallback) {
        int rgb = color & 0x00FFFFFF;
        if (rgb == 0 && color == 0) {
            return fallback;
        }
        return 0xFF000000 | rgb;
    }
}
