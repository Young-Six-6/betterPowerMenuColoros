package com.youngsix6.betterpowermenu.config;

import android.content.Context;
import android.database.Cursor;

import com.youngsix6.betterpowermenu.util.ModuleLog;

/** 在 SystemUI 进程内以失败安全方式读取模块设置。 */
public final class SettingsReader {

    private static final String LOG = "Settings";

    private SettingsReader() {
    }

    public static ModuleSettings read(Context context) {
        if (context == null) {
            return ModuleSettings.defaults();
        }
        try (Cursor cursor = context.getContentResolver().query(
                SettingsContract.CONFIG_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                ModuleLog.w(LOG, "设置提供器无数据，使用默认配置");
                return ModuleSettings.defaults();
            }
            ModuleSettings settings = new ModuleSettings(
                    intValue(cursor, SettingsContract.HIDE_EMERGENCY,
                            SettingsContract.DEFAULT_HIDE_EMERGENCY ? 1 : 0) != 0,
                    ActionType.fromKey(stringValue(cursor, SettingsContract.UP_ACTION),
                            SettingsContract.DEFAULT_UP_ACTION),
                    ActionType.fromKey(stringValue(cursor, SettingsContract.DOWN_ACTION),
                            SettingsContract.DEFAULT_DOWN_ACTION),
                    intValue(cursor, SettingsContract.UP_COLOR,
                            SettingsContract.DEFAULT_UP_COLOR),
                    intValue(cursor, SettingsContract.DOWN_COLOR,
                            SettingsContract.DEFAULT_DOWN_COLOR),
                    intValue(cursor, SettingsContract.ROOT_GRANTED,
                            SettingsContract.DEFAULT_ROOT_GRANTED ? 1 : 0) != 0);
            ModuleLog.i(LOG, "已载入设置: " + settings.summary());
            return settings;
        } catch (Throwable error) {
            ModuleLog.w(LOG, "读取模块设置失败，使用默认配置: "
                    + error.getClass().getSimpleName());
            return ModuleSettings.defaults();
        }
    }

    private static int intValue(Cursor cursor, String name, int fallback) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? fallback : cursor.getInt(index);
    }

    private static String stringValue(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }
}
