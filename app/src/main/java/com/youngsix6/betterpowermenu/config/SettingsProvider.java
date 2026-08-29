package com.youngsix6.betterpowermenu.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/**
 * 向 SystemUI 提供只读、非敏感的模块设置。写入只能在模块自身进程内完成。
 */
public final class SettingsProvider extends ContentProvider {

    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String[] COLUMNS = {
            SettingsContract.HIDE_EMERGENCY,
            SettingsContract.UP_ACTION,
            SettingsContract.DOWN_ACTION,
            SettingsContract.UP_COLOR,
            SettingsContract.DOWN_COLOR,
            SettingsContract.ROOT_GRANTED
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        enforceAllowedCaller();
        if (uri == null || !SettingsContract.CONFIG_URI.equals(uri)) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        Context context = getContext();
        if (context == null) {
            return new MatrixCursor(COLUMNS, 0);
        }
        SharedPreferences preferences = context.getSharedPreferences(
                SettingsContract.PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean rootGranted = preferences.getBoolean(
                SettingsContract.ROOT_GRANTED, SettingsContract.DEFAULT_ROOT_GRANTED);
        ActionType upAction = ActionType.fromKey(
                preferences.getString(SettingsContract.UP_ACTION, null),
                SettingsContract.DEFAULT_UP_ACTION);
        ActionType downAction = ActionType.fromKey(
                preferences.getString(SettingsContract.DOWN_ACTION, null),
                SettingsContract.DEFAULT_DOWN_ACTION);
        if (!rootGranted && upAction.requiresRoot()) {
            upAction = SettingsContract.DEFAULT_UP_ACTION;
        }
        if (!rootGranted && downAction.requiresRoot()) {
            downAction = SettingsContract.DEFAULT_DOWN_ACTION;
        }

        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        cursor.addRow(new Object[]{
                preferences.getBoolean(SettingsContract.HIDE_EMERGENCY,
                        SettingsContract.DEFAULT_HIDE_EMERGENCY) ? 1 : 0,
                upAction.key(),
                downAction.key(),
                SettingsContract.sanitizeColor(
                        preferences.getInt(SettingsContract.UP_COLOR,
                                SettingsContract.DEFAULT_UP_COLOR),
                        SettingsContract.DEFAULT_UP_COLOR),
                SettingsContract.sanitizeColor(
                        preferences.getInt(SettingsContract.DOWN_COLOR,
                                SettingsContract.DEFAULT_DOWN_COLOR),
                        SettingsContract.DEFAULT_DOWN_COLOR),
                rootGranted ? 1 : 0
        });
        cursor.setNotificationUri(context.getContentResolver(), SettingsContract.CONFIG_URI);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        enforceAllowedCaller();
        return "vnd.android.cursor.item/vnd." + SettingsContract.AUTHORITY + ".config";
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        enforceAllowedCaller();
        if (!SettingsContract.METHOD_ROOT_REBOOT.equals(method)) {
            throw new IllegalArgumentException("Unsupported method: " + method);
        }
        ActionType action = ActionType.fromKey(arg, null);
        boolean success = action != null && action.requiresRoot()
                && RootAccess.reboot(action);
        Bundle result = new Bundle();
        result.putBoolean(SettingsContract.RESULT_SUCCESS, success);
        return result;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new SecurityException("Settings provider is read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new SecurityException("Settings provider is read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new SecurityException("Settings provider is read-only");
    }

    private void enforceAllowedCaller() {
        Context context = getContext();
        int callingUid = Binder.getCallingUid();
        if (callingUid == Process.myUid() || callingUid == Process.SYSTEM_UID) {
            return;
        }
        PackageManager packageManager = context == null ? null : context.getPackageManager();
        String[] packages = packageManager == null ? null
                : packageManager.getPackagesForUid(callingUid);
        if (packages != null) {
            for (String packageName : packages) {
                if (SYSTEM_UI_PACKAGE.equals(packageName)) {
                    return;
                }
            }
        }
        throw new SecurityException("Caller is not SystemUI: uid=" + callingUid);
    }
}
