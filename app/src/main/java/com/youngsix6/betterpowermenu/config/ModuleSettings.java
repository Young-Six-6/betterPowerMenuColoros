package com.youngsix6.betterpowermenu.config;

/** 一次打开电源菜单所使用的不可变配置快照。 */
public final class ModuleSettings {

    public final boolean hideEmergency;
    public final ActionType upAction;
    public final ActionType downAction;
    public final int upColor;
    public final int downColor;
    public final boolean rootGranted;

    public ModuleSettings(boolean hideEmergency, ActionType upAction,
                          ActionType downAction, int upColor, int downColor,
                          boolean rootGranted) {
        this.hideEmergency = hideEmergency;
        this.rootGranted = rootGranted;
        this.upAction = allowedAction(upAction, SettingsContract.DEFAULT_UP_ACTION);
        this.downAction = allowedAction(downAction, SettingsContract.DEFAULT_DOWN_ACTION);
        this.upColor = SettingsContract.sanitizeColor(
                upColor, SettingsContract.DEFAULT_UP_COLOR);
        this.downColor = SettingsContract.sanitizeColor(
                downColor, SettingsContract.DEFAULT_DOWN_COLOR);
    }

    public static ModuleSettings defaults() {
        return new ModuleSettings(
                SettingsContract.DEFAULT_HIDE_EMERGENCY,
                SettingsContract.DEFAULT_UP_ACTION,
                SettingsContract.DEFAULT_DOWN_ACTION,
                SettingsContract.DEFAULT_UP_COLOR,
                SettingsContract.DEFAULT_DOWN_COLOR,
                SettingsContract.DEFAULT_ROOT_GRANTED);
    }

    public String summary() {
        return "hideEmergency=" + hideEmergency
                + ", up=" + upAction.key()
                + ", down=" + downAction.key()
                + ", rootGranted=" + rootGranted
                + ", upColor=#" + String.format("%08X", upColor)
                + ", downColor=#" + String.format("%08X", downColor);
    }

    private ActionType allowedAction(ActionType action, ActionType fallback) {
        ActionType resolved = action == null ? fallback : action;
        return resolved.requiresRoot() && !rootGranted ? fallback : resolved;
    }
}
