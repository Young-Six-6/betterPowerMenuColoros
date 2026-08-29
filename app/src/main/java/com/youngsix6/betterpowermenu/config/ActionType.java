package com.youngsix6.betterpowermenu.config;

/** 增强滑条可分配的动作。持久化使用稳定 key，避免显示文字变化破坏配置。 */
public enum ActionType {
    LOCK("lock", "锁屏", false),
    SCREEN_OFF("screen_off", "息屏", false),
    DO_NOT_DISTURB("do_not_disturb", "免打扰", false),
    SOFT_RESTART("soft_restart", "软重启", false),
    AIRPLANE("airplane", "飞行模式", false),
    REBOOT_RECOVERY("reboot_recovery", "重启到 Recovery", true),
    REBOOT_EDL("reboot_edl", "重启到 EDL", true),
    REBOOT_BOOTLOADER("reboot_bootloader", "重启到 Fastboot", true);

    private final String key;
    private final String displayName;
    private final boolean requiresRoot;

    ActionType(String key, String displayName, boolean requiresRoot) {
        this.key = key;
        this.displayName = displayName;
        this.requiresRoot = requiresRoot;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public boolean requiresRoot() {
        return requiresRoot;
    }

    public static ActionType fromKey(String key, ActionType fallback) {
        // v1.1 及更早版本保存的是 silent，升级后无感迁移为免打扰。
        if ("silent".equals(key)) {
            return DO_NOT_DISTURB;
        }
        if (key != null) {
            for (ActionType type : values()) {
                if (type.key.equals(key)) {
                    return type;
                }
            }
        }
        return fallback;
    }
}
