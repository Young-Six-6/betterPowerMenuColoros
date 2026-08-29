# LSPosed 模块入口类必须保留（防止被混淆移除）
-keep class com.youngsix6.betterpowermenu.XposedInit {
    public *;
}
-keep interface de.robv.android.xposed.** { *; }
-keep class de.robv.android.xposed.** { *; }
