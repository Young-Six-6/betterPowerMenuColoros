package com.youngsix6.betterpowermenu;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.youngsix6.betterpowermenu.config.ActionType;
import com.youngsix6.betterpowermenu.config.RootAccess;
import com.youngsix6.betterpowermenu.config.SettingsContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 使用 Material Design 3 组件构建的模块设置页。 */
@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {

    private static final int[] COLOR_PALETTE = {
            0xFF63C83B, 0xFFE93D28, 0xFF2D7DFF, 0xFFFFA000,
            0xFF9C5CFF, 0xFF00AFA0, 0xFFFF5C93, 0xFFFFFFFF
    };

    private SharedPreferences preferences;
    private boolean darkMode;
    private int surfaceColor;
    private int surfaceContainerColor;
    private int surfaceContainerHighColor;
    private int onSurfaceColor;
    private int onSurfaceVariantColor;
    private int outlineVariantColor;
    private int primaryColor;
    private int onPrimaryColor;
    private int primaryContainerColor;
    private int onPrimaryContainerColor;
    private int upColor;
    private int downColor;
    private boolean rootGranted;
    private boolean rootCheckComplete;
    private volatile boolean rootCheckRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resolveThemeColors();
        configureSystemBars();
        preferences = getSharedPreferences(
                SettingsContract.PREFERENCES_NAME, Context.MODE_PRIVATE);
        upColor = SettingsContract.sanitizeColor(
                preferences.getInt(SettingsContract.UP_COLOR,
                        SettingsContract.DEFAULT_UP_COLOR),
                SettingsContract.DEFAULT_UP_COLOR);
        downColor = SettingsContract.sanitizeColor(
                preferences.getInt(SettingsContract.DOWN_COLOR,
                        SettingsContract.DEFAULT_DOWN_COLOR),
                SettingsContract.DEFAULT_DOWN_COLOR);
        rootGranted = preferences.getBoolean(
                SettingsContract.ROOT_GRANTED, SettingsContract.DEFAULT_ROOT_GRANTED);
        setContentView(createContentView());
        detectRootAsync();
    }

    private View createContentView() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(surfaceColor);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.settings_title);
        toolbar.setSubtitle(R.string.settings_subtitle);
        toolbar.setTitleTextColor(onSurfaceColor);
        toolbar.setSubtitleTextColor(onSurfaceVariantColor);
        toolbar.setBackgroundColor(surfaceColor);
        toolbar.setContentInsetsRelative(dp(20), dp(20));
        toolbar.setPadding(0, dp(8), 0, dp(8));
        toolbar.setMinimumHeight(dp(80));
        page.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        page.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(6), dp(16), dp(32));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(sectionLabel(R.string.power_menu_section));
        addSpace(content, 10);

        MaterialCardView settingsCard = createCard(surfaceContainerColor);
        LinearLayout settings = cardContent(settingsCard);
        settings.addView(createEmergencyRow());
        settings.addView(divider());

        ActionType upAction = configuredAction(
                SettingsContract.UP_ACTION, SettingsContract.DEFAULT_UP_ACTION);
        settings.addView(createActionRow(
                R.string.up_action_title, R.string.up_action_summary,
                SettingsContract.UP_ACTION, upAction));
        settings.addView(divider());

        ActionType downAction = configuredAction(
                SettingsContract.DOWN_ACTION, SettingsContract.DEFAULT_DOWN_ACTION);
        settings.addView(createActionRow(
                R.string.down_action_title, R.string.down_action_summary,
                SettingsContract.DOWN_ACTION, downAction));
        settings.addView(divider());

        settings.addView(createColorRow(
                R.string.up_color_title, R.string.up_color_summary, true));
        settings.addView(divider());
        settings.addView(createColorRow(
                R.string.down_color_title, R.string.down_color_summary, false));
        content.addView(settingsCard, matchWrap());

        addSpace(content, 26);
        content.addView(sectionLabel(R.string.root_section));
        addSpace(content, 10);
        content.addView(createRootStatusCard(), matchWrap());

        TextView hint = bodyText(getString(R.string.settings_apply_hint),
                13f, onSurfaceVariantColor);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(14), dp(24), dp(14), 0);
        content.addView(hint, matchWrap());
        applySafeInsets(page, toolbar, scrollView);
        return page;
    }

    private View createEmergencyRow() {
        LinearLayout row = baseRow();
        LinearLayout labels = labelGroup(
                R.string.hide_emergency_title, R.string.hide_emergency_summary);
        row.addView(labels, weightedWrap());

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(preferences.getBoolean(
                SettingsContract.HIDE_EMERGENCY,
                SettingsContract.DEFAULT_HIDE_EMERGENCY));
        toggle.setContentDescription(getString(R.string.hide_emergency_title));
        toggle.setOnCheckedChangeListener((button, checked) ->
                saveBoolean(SettingsContract.HIDE_EMERGENCY, checked));
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        row.addView(toggle, wrapWrap());
        return row;
    }

    private View createActionRow(int titleRes, int summaryRes,
                                 String key, ActionType selected) {
        LinearLayout row = baseRow();
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = bodyText(getString(titleRes), 16f, onSurfaceColor);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title, matchWrap());

        TextView current = bodyText(selected.displayName(),
                14f, primaryColor);
        current.setPadding(0, dp(4), 0, 0);
        current.setSingleLine(true);
        current.setEllipsize(TextUtils.TruncateAt.END);
        current.setContentDescription(getString(summaryRes));
        labels.addView(current, matchWrap());
        row.addView(labels, weightedWrap());

        TextView chevron = bodyText("›", 28f, onSurfaceVariantColor);
        chevron.setGravity(Gravity.CENTER);
        chevron.setPadding(dp(12), 0, dp(2), 0);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(34), dp(48)));
        row.setContentDescription(getString(titleRes) + "，" + selected.displayName());
        row.setOnClickListener(view -> showActionDialog(
                titleRes, key, selected, current, row));
        return row;
    }

    private void showActionDialog(int titleRes, String key, ActionType selected,
                                  TextView current, View row) {
        ActionType[] values = availableActions();
        String[] labels = new String[values.length];
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].displayName();
            if (values[i] == selected) {
                checked = i;
            }
        }

        final int checkedItem = checked;
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, checkedItem, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getListView()
                .setOnItemClickListener((parent, view, position, id) -> {
                    if (position < 0 || position >= values.length) {
                        return;
                    }
                    ActionType action = values[position];
                    saveString(key, action.key());
                    current.setText(action.displayName());
                    row.setContentDescription(getString(titleRes)
                            + "，" + action.displayName());
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private View createColorRow(int titleRes, int summaryRes, boolean up) {
        LinearLayout row = baseRow();
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = bodyText(getString(titleRes), 16f, onSurfaceColor);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title, matchWrap());

        TextView value = bodyText("", 14f, onSurfaceVariantColor);
        value.setPadding(0, dp(4), 0, 0);
        value.setContentDescription(getString(summaryRes));
        labels.addView(value, matchWrap());
        row.addView(labels, weightedWrap());

        View swatch = new View(this);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                dp(42), dp(42));
        swatchParams.setMarginStart(dp(16));
        row.addView(swatch, swatchParams);

        ColorRowBinding binding = new ColorRowBinding(value, swatch, row, titleRes);
        updateColorRow(binding, up ? upColor : downColor);
        row.setOnClickListener(view -> showColorDialog(up, binding));
        return row;
    }

    private void showColorDialog(boolean up, ColorRowBinding target) {
        int current = up ? upColor : downColor;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(6), dp(24), 0);

        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint(R.string.color_hex_hint);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setBoxCornerRadii(dp(16), dp(16), dp(16), dp(16));

        TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(String.format(Locale.US, "#%06X", current & 0x00FFFFFF));
        input.setSelection(input.length());
        inputLayout.addView(input, matchWrap());
        content.addView(inputLayout, matchWrap());

        GridLayout palette = new GridLayout(this);
        palette.setColumnCount(4);
        palette.setUseDefaultMargins(false);
        palette.setPadding(0, dp(14), 0, 0);
        for (int color : COLOR_PALETTE) {
            View swatch = new View(this);
            swatch.setBackground(colorSwatch(color, 24));
            swatch.setContentDescription(String.format(Locale.US,
                    "#%06X", color & 0x00FFFFFF));
            swatch.setOnClickListener(view -> {
                input.setText(String.format(Locale.US,
                        "#%06X", color & 0x00FFFFFF));
                input.setSelection(input.length());
                inputLayout.setError(null);
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(48);
            params.height = dp(48);
            params.setMargins(dp(7), dp(6), dp(7), dp(6));
            palette.addView(swatch, params);
        }
        content.addView(palette, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(up ? R.string.up_color_title : R.string.down_color_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    Integer color = parseOpaqueColor(input.getText() == null
                            ? null : input.getText().toString());
                    if (color == null) {
                        inputLayout.setError(getString(R.string.color_invalid));
                        return;
                    }
                    if (up) {
                        upColor = color;
                        saveInt(SettingsContract.UP_COLOR, color);
                    } else {
                        downColor = color;
                        saveInt(SettingsContract.DOWN_COLOR, color);
                    }
                    updateColorRow(target, color);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private View createRootStatusCard() {
        boolean checking = !rootCheckComplete;
        int containerColor = rootGranted && !checking
                ? primaryContainerColor : surfaceContainerHighColor;
        int foreground = rootGranted && !checking
                ? onPrimaryContainerColor : onSurfaceColor;
        MaterialCardView card = createCard(containerColor);
        LinearLayout row = baseRow();
        row.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView stateIcon = bodyText(checking ? "…" : (rootGranted ? "✓" : "!"),
                checking ? 22f : 20f,
                rootGranted && !checking ? onPrimaryColor : primaryColor);
        stateIcon.setGravity(Gravity.CENTER);
        stateIcon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        stateIcon.setBackground(colorSwatch(
                rootGranted && !checking ? primaryColor : surfaceContainerColor, 24));
        row.addView(stateIcon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = weightedWrap();
        labelParams.setMarginStart(dp(16));
        row.addView(labels, labelParams);

        int titleRes = checking ? R.string.root_checking
                : (rootGranted ? R.string.root_granted : R.string.root_not_granted);
        int summaryRes = checking ? R.string.root_checking_summary
                : (rootGranted ? R.string.root_granted_summary
                : R.string.root_not_granted_summary);
        TextView title = bodyText(getString(titleRes), 16f, foreground);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title, matchWrap());
        TextView summary = bodyText(getString(summaryRes), 13f,
                rootGranted && !checking ? onPrimaryContainerColor : onSurfaceVariantColor);
        summary.setPadding(0, dp(4), 0, 0);
        labels.addView(summary, matchWrap());

        if (rootCheckComplete && !rootGranted) {
            row.setOnClickListener(view -> {
                rootCheckComplete = false;
                setContentView(createContentView());
                detectRootAsync();
            });
        }
        card.addView(row, matchWrap());
        return card;
    }

    private ActionType configuredAction(String key, ActionType fallback) {
        ActionType action = ActionType.fromKey(preferences.getString(key, null), fallback);
        return action.requiresRoot() && !rootGranted ? fallback : action;
    }

    private ActionType[] availableActions() {
        List<ActionType> actions = new ArrayList<>();
        for (ActionType action : ActionType.values()) {
            if (!action.requiresRoot() || rootGranted) {
                actions.add(action);
            }
        }
        return actions.toArray(new ActionType[0]);
    }

    private void detectRootAsync() {
        if (rootCheckRunning) {
            return;
        }
        rootCheckRunning = true;
        Thread thread = new Thread(() -> {
            boolean granted = RootAccess.isGranted();
            preferences.edit()
                    .putBoolean(SettingsContract.ROOT_GRANTED, granted)
                    .commit();
            notifySettingsChanged();
            runOnUiThread(() -> {
                rootCheckRunning = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                rootGranted = granted;
                rootCheckComplete = true;
                setContentView(createContentView());
            });
        }, "BetterPowerMenu-RootCheck");
        thread.start();
    }

    private MaterialCardView createCard(int color) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color);
        card.setRadius(dp(28));
        card.setCardElevation(0f);
        card.setUseCompatPadding(false);
        card.setPreventCornerOverlap(true);
        card.setStrokeWidth(0);
        return card;
    }

    private LinearLayout cardContent(MaterialCardView card) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, matchWrap());
        return content;
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(80));
        row.setPadding(dp(18), dp(12), dp(18), dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        Drawable selectable = selectableBackground();
        if (selectable != null) {
            row.setBackground(selectable);
        }
        return row;
    }

    private LinearLayout labelGroup(int titleRes, int summaryRes) {
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = bodyText(getString(titleRes), 16f, onSurfaceColor);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title, matchWrap());
        TextView summary = bodyText(getString(summaryRes), 13f, onSurfaceVariantColor);
        summary.setPadding(0, dp(4), 0, 0);
        labels.addView(summary, matchWrap());
        return labels;
    }

    private TextView sectionLabel(int textRes) {
        TextView label = bodyText(getString(textRes), 14f, primaryColor);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(dp(12), 0, dp(12), 0);
        return label;
    }

    private TextView bodyText(CharSequence value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(outlineVariantColor);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMarginStart(dp(18));
        params.setMarginEnd(dp(18));
        divider.setLayoutParams(params);
        return divider;
    }

    private Drawable selectableBackground() {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, value, true)
                || value.resourceId == 0) {
            return null;
        }
        try {
            return getDrawable(value.resourceId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private GradientDrawable colorSwatch(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setSize(dp(radiusDp * 2f), dp(radiusDp * 2f));
        drawable.setStroke(dp(1), outlineVariantColor);
        return drawable;
    }

    private void updateColorRow(ColorRowBinding binding, int color) {
        int opaque = SettingsContract.sanitizeColor(color, primaryColor);
        String hex = String.format(Locale.US, "#%06X", opaque & 0x00FFFFFF);
        binding.value.setText(hex);
        binding.swatch.setBackground(colorSwatch(opaque, 21));
        binding.row.setContentDescription(getString(binding.titleRes) + "，" + hex);
    }

    private Integer parseOpaqueColor(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }
        try {
            return 0xFF000000 | (int) Long.parseLong(normalized.substring(1), 16);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void saveBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).commit();
        notifySettingsChanged();
    }

    private void saveString(String key, String value) {
        preferences.edit().putString(key, value).commit();
        notifySettingsChanged();
    }

    private void saveInt(String key, int value) {
        preferences.edit().putInt(key, value).commit();
        notifySettingsChanged();
    }

    private void notifySettingsChanged() {
        try {
            getContentResolver().notifyChange(SettingsContract.CONFIG_URI, null);
        } catch (Throwable ignored) {
            // 保存已完成；通知失败只会延迟到下次查询。
        }
    }

    private void resolveThemeColors() {
        darkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        surfaceColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurface,
                darkMode ? 0xFF111318 : 0xFFF9F9FF);
        surfaceContainerColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainer,
                darkMode ? 0xFF1D2024 : 0xFFEFF0F7);
        surfaceContainerHighColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                darkMode ? 0xFF282A2F : 0xFFE8E9F0);
        onSurfaceColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface,
                darkMode ? 0xFFE2E2E9 : 0xFF1A1B20);
        onSurfaceVariantColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                darkMode ? 0xFFC5C6D0 : 0xFF45464F);
        outlineVariantColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant,
                darkMode ? 0xFF45464F : 0xFFC5C6D0);
        primaryColor = MaterialColors.getColor(this,
                androidx.appcompat.R.attr.colorPrimary,
                darkMode ? 0xFFADC6FF : 0xFF345DA8);
        onPrimaryColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnPrimary,
                darkMode ? 0xFF002E69 : Color.WHITE);
        primaryContainerColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorPrimaryContainer,
                darkMode ? 0xFF164482 : 0xFFD8E2FF);
        onPrimaryContainerColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                darkMode ? 0xFFD8E2FF : 0xFF001A41);
    }

    private void configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(params);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!darkMode);
        controller.setAppearanceLightNavigationBars(!darkMode);
    }

    /**
     * Android 15 强制边到边后，分别取状态栏、导航栏和 DisplayCutout 的最大安全边距。
     * 横屏侧挖孔也会转换为左右内边距，不依赖厂商固定高度。
     */
    private void applySafeInsets(View page, MaterialToolbar toolbar, ScrollView scrollView) {
        final int toolbarTopPadding = dp(8);
        final int toolbarBottomPadding = dp(8);
        ViewCompat.setOnApplyWindowInsetsListener(page, (view, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars());
            Insets navigationBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());
            Insets cutout = windowInsets.getInsets(
                    WindowInsetsCompat.Type.displayCutout());

            int safeTop = Math.max(statusBars.top, cutout.top);
            int safeLeft = Math.max(Math.max(statusBars.left, navigationBars.left),
                    cutout.left);
            int safeRight = Math.max(Math.max(statusBars.right, navigationBars.right),
                    cutout.right);
            int safeBottom = Math.max(navigationBars.bottom, cutout.bottom);

            toolbar.setPadding(safeLeft, toolbarTopPadding + safeTop,
                    safeRight, toolbarBottomPadding);
            scrollView.setPadding(safeLeft, 0, safeRight, safeBottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(page);
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weightedWrap() {
        return new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ColorRowBinding {
        final TextView value;
        final View swatch;
        final View row;
        final int titleRes;

        ColorRowBinding(TextView value, View swatch, View row, int titleRes) {
            this.value = value;
            this.swatch = swatch;
            this.row = row;
            this.titleRes = titleRes;
        }
    }
}
