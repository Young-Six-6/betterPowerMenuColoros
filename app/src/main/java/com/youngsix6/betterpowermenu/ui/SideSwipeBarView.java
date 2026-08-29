package com.youngsix6.betterpowermenu.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;

import com.youngsix6.betterpowermenu.util.ModuleLog;

/**
 * 仿 OPlus 原版关机滑条的侧边快捷控件。
 *
 * <p>尺寸和颜色优先从当前 SystemUI 的 {@code oplus_*} 资源动态读取，
 * 资源被小幅调整时会自动跟随；资源不存在时使用原版参数的保守回退值。</p>
 */
public class SideSwipeBarView extends View {

    private static final String LOG = "SideBar";

    private static final float FALLBACK_BAR_WIDTH_DP = 78f;
    private static final float FALLBACK_BAR_HEIGHT_DP = 300f;
    private static final float FALLBACK_BAR_RADIUS_DP = 45f;
    private static final float FALLBACK_HANDLER_WIDTH_DP = 48f;
    private static final float FALLBACK_HANDLER_HOT_ZONE_DP = 78f;
    private static final float FALLBACK_ACTION_ICON_WIDTH_DP = 24f;
    private static final float FALLBACK_ACTION_ICON_OFFSET_DP = 24f;
    private static final float FALLBACK_TEXT_OFFSET_DP = 32f;
    private static final float FALLBACK_TEXT_HEIGHT_DP = 20f;
    private static final float FALLBACK_TEXT_SIZE_DP = 14f;

    private static final int FALLBACK_BAR_COLOR = 0xCCFFFFFF;
    private static final int FALLBACK_HANDLER_COLOR = Color.WHITE;
    private static final int FALLBACK_UP_COLOR = 0xFF63C83B;
    private static final int FALLBACK_DOWN_COLOR = 0xFFE93D28;
    private static final int FALLBACK_TEXT_COLOR = Color.WHITE;

    /** 与原版 enter 动画一致。 */
    private static final long ENTER_DURATION_MS = 400L;

    public enum Direction { LEFT, RIGHT }

    public enum IconType { LOCK, POWER, AIRPLANE, DO_NOT_DISTURB, RESTART }

    public interface OnTriggerListener {
        void onTriggered(Direction direction, boolean slideDown);
    }

    private final Direction mDirection;
    private String mUpLabel;
    private String mDownLabel;
    private IconType mUpIcon;
    private IconType mDownIcon;

    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHandlerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBarRect = new RectF();
    private final RectF mDrawBarRect = new RectF();
    private final RectF mIconRect = new RectF();
    private final Path mIconPath = new Path();

    private final float mDensity;
    private final float mConfiguredBarWidth;
    private final float mConfiguredBarHeight;
    private final float mBarRadius;
    private final float mHandlerWidth;
    private final float mHandlerHotZone;
    private final float mActionIconWidth;
    private final float mActionIconOffset;
    private final float mTextOffset;
    private final float mTextHeight;
    private final int mBarColor;
    private final int mHandlerColor;
    private int mUpColor;
    private int mDownColor;
    private final int mTextColor;

    private float mCenterX;
    private float mCenterY;
    private float mOffset;
    private float mDownY;
    private float mStartOffset;
    private float mMaxOffset;
    private float mEnterFraction = 1f;
    private float mArrowPhase;
    private boolean mPressed;
    private boolean mTriggered;

    private OnTriggerListener mListener;
    private ValueAnimator mReturnAnimator;
    private ValueAnimator mEnterAnimator;
    private ValueAnimator mArrowAnimator;

    public SideSwipeBarView(Context context, Direction direction,
                            String upLabel, String downLabel,
                            IconType upIcon, IconType downIcon) {
        this(context, direction, upLabel, downLabel, upIcon, downIcon, 0, 0);
    }

    public SideSwipeBarView(Context context, Direction direction,
                            String upLabel, String downLabel,
                            IconType upIcon, IconType downIcon,
                            int upColor, int downColor) {
        super(context);
        mDirection = direction;
        mUpLabel = upLabel;
        mDownLabel = downLabel;
        mUpIcon = upIcon;
        mDownIcon = downIcon;
        mDensity = getResources().getDisplayMetrics().density;

        mConfiguredBarWidth = boundedDimension("oplus_bar_width",
                FALLBACK_BAR_WIDTH_DP, 56f, 120f);
        mConfiguredBarHeight = boundedDimension("oplus_bar_height",
                FALLBACK_BAR_HEIGHT_DP, 220f, 420f);
        mBarRadius = boundedDimension("oplus_default_bar_radius",
                FALLBACK_BAR_RADIUS_DP, 20f, 60f);
        mHandlerWidth = boundedDimension("oplus_handler_width",
                FALLBACK_HANDLER_WIDTH_DP, 36f, 72f);
        mHandlerHotZone = boundedDimension("oplus_handler_hot_zone",
                FALLBACK_HANDLER_HOT_ZONE_DP, 56f, 120f);
        mActionIconWidth = boundedDimension("oplus_reboot_width",
                FALLBACK_ACTION_ICON_WIDTH_DP, 16f, 36f);
        mActionIconOffset = boundedDimension("oplus_reboot_offset",
                FALLBACK_ACTION_ICON_OFFSET_DP, 12f, 48f);
        mTextOffset = boundedDimension("oplus_reboot_text_offset",
                FALLBACK_TEXT_OFFSET_DP, 20f, 48f);
        mTextHeight = boundedDimension("oplus_reboot_text_height",
                FALLBACK_TEXT_HEIGHT_DP, 14f, 32f);

        mBarColor = color("oplus_bar_color", FALLBACK_BAR_COLOR);
        mHandlerColor = color("oplus_handler_color", FALLBACK_HANDLER_COLOR);
        mUpColor = upColor == 0 ? color("oplus_reboot_color", FALLBACK_UP_COLOR)
                : opaque(upColor);
        mDownColor = downColor == 0 ? color("oplus_shutdown_color", FALLBACK_DOWN_COLOR)
                : opaque(downColor);
        mTextColor = color("oplus_shutdown_text_color", FALLBACK_TEXT_COLOR);

        initPaints();
        ModuleLog.d(LOG, "原版样式参数: bar=" + Math.round(mConfiguredBarWidth)
                + "x" + Math.round(mConfiguredBarHeight)
                + ", handler=" + Math.round(mHandlerWidth)
                + ", threshold=" + Math.round((mConfiguredBarHeight / 2f)
                - (mActionIconWidth / 2f) - mActionIconOffset));
        setClickable(true);
        setFocusable(true);
        setContentDescription("上滑" + mUpLabel + "，下滑" + mDownLabel);
    }

    public static int preferredWidth(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        float value = resolveDimension(context, "oplus_handler_hot_zone",
                FALLBACK_HANDLER_HOT_ZONE_DP);
        return Math.round(clamp(value, 56f * density, 120f * density));
    }

    public static int preferredHeight(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        float barHeight = clamp(resolveDimension(context, "oplus_bar_height",
                FALLBACK_BAR_HEIGHT_DP), 220f * density, 420f * density);
        float textOffset = clamp(resolveDimension(context, "oplus_reboot_text_offset",
                FALLBACK_TEXT_OFFSET_DP), 20f * density, 48f * density);
        return Math.round(barHeight + (textOffset * 2f));
    }

    private void initPaints() {
        mBarPaint.setStyle(Paint.Style.FILL);
        mBarPaint.setColor(mBarColor);

        mProgressPaint.setStyle(Paint.Style.FILL);

        mHandlerPaint.setStyle(Paint.Style.FILL);
        mHandlerPaint.setColor(mHandlerColor);

        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setColor(mTextColor);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(boundedDimension("oplus_textsize_on_shutdown_bar",
                FALLBACK_TEXT_SIZE_DP, 10f, 20f));

        mIconPaint.setStyle(Paint.Style.STROKE);
        mIconPaint.setStrokeWidth(dp(2f));
        mIconPaint.setStrokeCap(Paint.Cap.ROUND);
        mIconPaint.setStrokeJoin(Paint.Join.ROUND);

        mArrowPaint.setStyle(Paint.Style.STROKE);
        mArrowPaint.setColor(color("oplus_road_color", 0x33FFFFFF));
        mArrowPaint.setStrokeWidth(boundedDimension(
                "oplus_road_line_width", 1.6f, 0.5f, 4f));
        mArrowPaint.setStrokeCap(Paint.Cap.ROUND);
        mArrowPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setOnTriggerListener(OnTriggerListener listener) {
        mListener = listener;
    }

    /** 复用同一个 SystemUI 容器时刷新动作与颜色，无需重建或重复平移原版布局。 */
    public void configureActions(String upLabel, String downLabel,
                                 IconType upIcon, IconType downIcon,
                                 int upColor, int downColor) {
        mUpLabel = upLabel == null ? "" : upLabel;
        mDownLabel = downLabel == null ? "" : downLabel;
        mUpIcon = upIcon == null ? IconType.POWER : upIcon;
        mDownIcon = downIcon == null ? IconType.POWER : downIcon;
        mUpColor = opaque(upColor);
        mDownColor = opaque(downColor);
        setContentDescription("上滑" + mUpLabel + "，下滑" + mDownLabel);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(Math.max(mConfiguredBarWidth, mHandlerHotZone));
        int desiredHeight = Math.round(mConfiguredBarHeight + (mTextOffset * 2f));
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mCenterX = w / 2f;
        mCenterY = h / 2f;

        // 小屏或异常布局时允许缩短轨道，但不让任何坐标变成负数。
        float availableHeight = Math.max(0f, h - (mTextOffset * 2f));
        float actualBarHeight = Math.min(mConfiguredBarHeight, availableHeight);
        float actualBarWidth = Math.min(mConfiguredBarWidth, Math.max(0f, w));
        mBarRect.set(mCenterX - (actualBarWidth / 2f),
                mCenterY - (actualBarHeight / 2f),
                mCenterX + (actualBarWidth / 2f),
                mCenterY + (actualBarHeight / 2f));

        // 原版阈值：(barHeight / 2) - (iconWidth / 2) - rebootOffset。
        mMaxOffset = Math.max(0f, (actualBarHeight / 2f)
                - (mActionIconWidth / 2f) - mActionIconOffset);
        mOffset = clamp(mOffset, -mMaxOffset, mMaxOffset);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBarRect.isEmpty()) {
            return;
        }

        float dragFraction = mMaxOffset <= 0f ? 0f
                : Math.min(1f, Math.abs(mOffset) / mMaxOffset);
        float visualFraction = clamp(mEnterFraction, 0f, 1f);

        // 原版入场时通过 barHeightDelta 让轨道由短变长。
        mDrawBarRect.set(mBarRect);
        float enterInset = mBarRect.height() * 0.35f * (1f - visualFraction);
        if ((enterInset * 2f) < mDrawBarRect.height()) {
            mDrawBarRect.inset(0f, enterInset);
        }

        mBarPaint.setAlpha(Math.round(255f * 0.20f * visualFraction));
        canvas.drawRoundRect(mDrawBarRect, mBarRadius, mBarRadius, mBarPaint);
        drawProgress(canvas, dragFraction, visualFraction);

        float topAlpha = mOffset > 0f ? 1f - dragFraction : 1f;
        float bottomAlpha = mOffset < 0f ? 1f - dragFraction : 1f;
        drawActionText(canvas, true, topAlpha * visualFraction);
        drawActionText(canvas, false, bottomAlpha * visualFraction);
        drawActionIcon(canvas, true, topAlpha * visualFraction);
        drawActionIcon(canvas, false, bottomAlpha * visualFraction);
        drawRoadArrows(canvas, dragFraction, visualFraction);
        drawHandler(canvas, dragFraction, visualFraction);
    }

    private void drawProgress(Canvas canvas, float dragFraction, float visualFraction) {
        if (dragFraction <= 0f) {
            mProgressPaint.setShader(null);
            return;
        }

        int selectedColor = mOffset < 0f ? mUpColor : mDownColor;
        int strong = withAlpha(selectedColor,
                Math.round(210f * dragFraction * visualFraction));
        int transparent = withAlpha(selectedColor, 0);
        float handleY = mCenterY + mOffset;
        LinearGradient gradient;
        if (mOffset < 0f) {
            gradient = new LinearGradient(0f, mDrawBarRect.top, 0f, handleY,
                    strong, transparent, Shader.TileMode.CLAMP);
        } else {
            gradient = new LinearGradient(0f, handleY, 0f, mDrawBarRect.bottom,
                    transparent, strong, Shader.TileMode.CLAMP);
        }
        mProgressPaint.setShader(gradient);
        canvas.drawRoundRect(mDrawBarRect, mBarRadius, mBarRadius, mProgressPaint);
        mProgressPaint.setShader(null);
    }

    private void drawActionText(Canvas canvas, boolean top, float alpha) {
        mTextPaint.setAlpha(Math.round(127f * clamp(alpha, 0f, 1f)));
        Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
        float areaTop = top ? mDrawBarRect.top - mTextOffset
                : mDrawBarRect.bottom + mTextOffset - mTextHeight;
        float baseline = areaTop + (mTextHeight / 2f)
                - ((metrics.ascent + metrics.descent) / 2f);
        canvas.drawText(top ? mUpLabel : mDownLabel, mCenterX, baseline, mTextPaint);
    }

    private void drawActionIcon(Canvas canvas, boolean top, float alpha) {
        float centerDistance = Math.max(mActionIconWidth / 2f,
                (mDrawBarRect.height() / 2f) - mActionIconOffset - (mActionIconWidth / 2f));
        float centerY = mCenterY + (top ? -centerDistance : centerDistance);
        int baseColor = top ? mUpColor : mDownColor;
        mIconPaint.setColor(baseColor);
        mIconPaint.setAlpha(Math.round(230f * clamp(alpha, 0f, 1f)));
        drawIcon(canvas, top ? mUpIcon : mDownIcon, mCenterX, centerY,
                mActionIconWidth);
    }

    private void drawIcon(Canvas canvas, IconType type, float cx, float cy, float size) {
        float half = size / 2f;
        float strokeInset = dp(2f);
        mIconPath.reset();
        switch (type) {
            case LOCK: {
                mIconRect.set(cx - half * 0.55f, cy - half * 0.05f,
                        cx + half * 0.55f, cy + half * 0.70f);
                canvas.drawRoundRect(mIconRect, dp(2f), dp(2f), mIconPaint);
                mIconRect.set(cx - half * 0.38f, cy - half * 0.72f,
                        cx + half * 0.38f, cy + half * 0.18f);
                // 正向 180° 才是锁梁的上半圆；原先负角度会画到下方。
                canvas.drawArc(mIconRect, 180f, 180f, false, mIconPaint);
                break;
            }
            case POWER: {
                mIconRect.set(cx - half + strokeInset, cy - half + strokeInset,
                        cx + half - strokeInset, cy + half - strokeInset);
                canvas.drawArc(mIconRect, -52f, 284f, false, mIconPaint);
                canvas.drawLine(cx, cy - half, cx, cy - dp(1f), mIconPaint);
                break;
            }
            case AIRPLANE: {
                mIconPath.moveTo(cx - half * 0.85f, cy + half * 0.10f);
                mIconPath.lineTo(cx - half * 0.15f, cy - half * 0.12f);
                mIconPath.lineTo(cx + half * 0.65f, cy - half * 0.78f);
                mIconPath.lineTo(cx + half * 0.85f, cy - half * 0.68f);
                mIconPath.lineTo(cx + half * 0.25f, cy + half * 0.05f);
                mIconPath.lineTo(cx + half * 0.62f, cy + half * 0.55f);
                mIconPath.lineTo(cx + half * 0.42f, cy + half * 0.66f);
                mIconPath.lineTo(cx - half * 0.05f, cy + half * 0.30f);
                mIconPath.lineTo(cx - half * 0.48f, cy + half * 0.62f);
                mIconPath.lineTo(cx - half * 0.62f, cy + half * 0.52f);
                mIconPath.lineTo(cx - half * 0.32f, cy + half * 0.14f);
                mIconPath.close();
                Paint.Style oldStyle = mIconPaint.getStyle();
                mIconPaint.setStyle(Paint.Style.FILL);
                canvas.drawPath(mIconPath, mIconPaint);
                mIconPaint.setStyle(oldStyle);
                break;
            }
            case DO_NOT_DISTURB: {
                // 以传入中心点对称构造铃铛，避免圆弧包围盒带来的视觉偏移。
                mIconPath.moveTo(cx - half * 0.56f, cy + half * 0.28f);
                mIconPath.cubicTo(cx - half * 0.38f, cy + half * 0.02f,
                        cx - half * 0.40f, cy - half * 0.25f,
                        cx - half * 0.24f, cy - half * 0.43f);
                mIconPath.cubicTo(cx - half * 0.12f, cy - half * 0.58f,
                        cx + half * 0.12f, cy - half * 0.58f,
                        cx + half * 0.24f, cy - half * 0.43f);
                mIconPath.cubicTo(cx + half * 0.40f, cy - half * 0.25f,
                        cx + half * 0.38f, cy + half * 0.02f,
                        cx + half * 0.56f, cy + half * 0.28f);
                canvas.drawPath(mIconPath, mIconPaint);
                canvas.drawLine(cx - half * 0.56f, cy + half * 0.28f,
                        cx + half * 0.56f, cy + half * 0.28f, mIconPaint);
                canvas.drawArc(cx - half * 0.18f, cy + half * 0.20f,
                        cx + half * 0.18f, cy + half * 0.62f,
                        0f, 180f, false, mIconPaint);
                canvas.drawLine(cx - half * 0.70f, cy - half * 0.70f,
                        cx + half * 0.70f, cy + half * 0.70f, mIconPaint);
                break;
            }
            case RESTART: {
                mIconRect.set(cx - half + strokeInset, cy - half + strokeInset,
                        cx + half - strokeInset, cy + half - strokeInset);
                canvas.drawArc(mIconRect, -70f, 292f, false, mIconPaint);
                mIconPath.moveTo(cx + half * 0.22f, cy - half * 0.82f);
                mIconPath.lineTo(cx + half * 0.78f, cy - half * 0.74f);
                mIconPath.lineTo(cx + half * 0.62f, cy - half * 0.22f);
                Paint.Style oldStyle = mIconPaint.getStyle();
                mIconPaint.setStyle(Paint.Style.FILL);
                canvas.drawPath(mIconPath, mIconPaint);
                mIconPaint.setStyle(oldStyle);
                break;
            }
        }
    }

    private void drawRoadArrows(Canvas canvas, float dragFraction, float visualFraction) {
        float directionAlpha = 1f - (dragFraction * 0.85f);
        float gap = dp(15f);
        float start = dp(36f);
        float halfWidth = dp(4f);
        float halfHeight = dp(2.5f);

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                float pulse = 1f - Math.abs((((mArrowPhase + (i * 0.22f)) % 1f) * 2f) - 1f);
                int alpha = Math.round(150f * (0.25f + (0.75f * pulse))
                        * directionAlpha * visualFraction);
                mArrowPaint.setAlpha(alpha);
                float cy = mCenterY + (side * (start + (i * gap)));
                if (side < 0) {
                    canvas.drawLine(mCenterX - halfWidth, cy + halfHeight,
                            mCenterX, cy - halfHeight, mArrowPaint);
                    canvas.drawLine(mCenterX, cy - halfHeight,
                            mCenterX + halfWidth, cy + halfHeight, mArrowPaint);
                } else {
                    canvas.drawLine(mCenterX - halfWidth, cy - halfHeight,
                            mCenterX, cy + halfHeight, mArrowPaint);
                    canvas.drawLine(mCenterX, cy + halfHeight,
                            mCenterX + halfWidth, cy - halfHeight, mArrowPaint);
                }
            }
        }
    }

    private void drawHandler(Canvas canvas, float dragFraction, float visualFraction) {
        float scale = (0.8f + (0.2f * visualFraction))
                * (1f + (0.14f * dragFraction));
        float radius = (mHandlerWidth / 2f) * scale;
        mHandlerPaint.setAlpha(Math.round(255f * 0.85f * visualFraction));
        canvas.drawCircle(mCenterX, mCenterY + mOffset, radius, mHandlerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isEnabled() || mMaxOffset <= 0f
                        || Math.abs(event.getX() - mCenterX) > (mHandlerHotZone / 2f)) {
                    return false;
                }
                cancelReturnAnimation();
                mPressed = true;
                mTriggered = false;
                mDownY = event.getY();
                mStartOffset = mOffset;
                requestParentIntercept(false);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!mPressed) {
                    return false;
                }
                mOffset = clamp(mStartOffset + event.getY() - mDownY,
                        -mMaxOffset, mMaxOffset);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (!mPressed) {
                    return false;
                }
                requestParentIntercept(true);
                performClick();
                boolean slideDown = mOffset > 0f;
                boolean triggered = Math.abs(mOffset) >= (mMaxOffset - dp(1f));
                if (triggered && !mTriggered) {
                    mTriggered = true;
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    ModuleLog.i(LOG, "原版阈值手势触发: direction=" + mDirection
                            + ", slideDown=" + slideDown
                            + ", offset=" + Math.round(mOffset)
                            + ", threshold=" + Math.round(mMaxOffset));
                    if (mListener != null) {
                        try {
                            mListener.onTriggered(mDirection, slideDown);
                        } catch (Throwable callbackError) {
                            ModuleLog.e(LOG, "动作回调异常: direction=" + mDirection,
                                    callbackError);
                        }
                    }
                }
                mPressed = false;
                animateBack(triggered ? 160L : 260L);
                return true;

            case MotionEvent.ACTION_CANCEL:
                requestParentIntercept(true);
                mPressed = false;
                animateBack(260L);
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private void animateBack(long duration) {
        cancelReturnAnimation();
        if (!isAttachedToWindow() || Math.abs(mOffset) < 0.5f
                || !ValueAnimator.areAnimatorsEnabled()) {
            mOffset = 0f;
            invalidate();
            return;
        }

        mReturnAnimator = ValueAnimator.ofFloat(mOffset, 0f);
        mReturnAnimator.setDuration(duration);
        mReturnAnimator.setInterpolator(new DecelerateInterpolator());
        mReturnAnimator.addUpdateListener(animation -> {
            mOffset = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mReturnAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == mReturnAnimator) {
                    mOffset = 0f;
                    mReturnAnimator = null;
                    invalidate();
                }
            }
        });
        mReturnAnimator.start();
    }

    private void startVisualAnimations() {
        cancelVisualAnimations();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            mEnterFraction = 1f;
            mArrowPhase = 0.5f;
            invalidate();
            return;
        }

        mEnterFraction = 0f;
        mEnterAnimator = ValueAnimator.ofFloat(0f, 1f);
        mEnterAnimator.setDuration(ENTER_DURATION_MS);
        mEnterAnimator.setInterpolator(new PathInterpolator(0f, 0f, 0.1f, 1f));
        mEnterAnimator.addUpdateListener(animation -> {
            mEnterFraction = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mEnterAnimator.start();

        mArrowAnimator = ValueAnimator.ofFloat(0f, 1f);
        mArrowAnimator.setDuration(1100L);
        mArrowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mArrowAnimator.setRepeatMode(ValueAnimator.RESTART);
        mArrowAnimator.addUpdateListener(animation -> {
            mArrowPhase = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mArrowAnimator.start();
    }

    private void cancelReturnAnimation() {
        ValueAnimator animator = mReturnAnimator;
        if (animator != null) {
            mReturnAnimator = null;
            animator.cancel();
        }
    }

    private void cancelVisualAnimations() {
        if (mEnterAnimator != null) {
            mEnterAnimator.cancel();
            mEnterAnimator = null;
        }
        if (mArrowAnimator != null) {
            mArrowAnimator.cancel();
            mArrowAnimator = null;
        }
    }

    private void requestParentIntercept(boolean allowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!allowIntercept);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startVisualAnimations();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelReturnAnimation();
        cancelVisualAnimations();
        mPressed = false;
        mOffset = 0f;
        mEnterFraction = 1f;
        super.onDetachedFromWindow();
    }

    public Direction getDirection() {
        return mDirection;
    }

    private float dimension(String name, float fallbackDp) {
        return resolveDimension(getContext(), name, fallbackDp);
    }

    private float boundedDimension(String name, float fallbackDp,
                                   float minDp, float maxDp) {
        return clamp(dimension(name, fallbackDp), dp(minDp), dp(maxDp));
    }

    private int color(String name, int fallback) {
        return resolveColor(getContext(), name, fallback);
    }

    private float dp(float value) {
        return value * mDensity;
    }

    private static float resolveDimension(Context context, String name, float fallbackDp) {
        if (context == null) {
            return fallbackDp;
        }
        try {
            Resources resources = context.getResources();
            int id = resourceId(resources, context, name, "dimen");
            if (id != 0) {
                return resources.getDimension(id);
            }
            return fallbackDp * resources.getDisplayMetrics().density;
        } catch (Throwable ignored) {
            return fallbackDp * context.getResources().getDisplayMetrics().density;
        }
    }

    private static int resolveColor(Context context, String name, int fallback) {
        if (context == null) {
            return fallback;
        }
        try {
            Resources resources = context.getResources();
            int id = resourceId(resources, context, name, "color");
            return id == 0 ? fallback : resources.getColor(id, context.getTheme());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(clampInt(alpha, 0, 255), Color.red(color),
                Color.green(color), Color.blue(color));
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int resourceId(Resources resources, Context context,
                                  String name, String type) {
        String packageName = context.getPackageName();
        int id = resources.getIdentifier(name, type, packageName);
        if (id == 0 && !"com.android.systemui".equals(packageName)) {
            id = resources.getIdentifier(name, type, "com.android.systemui");
        }
        if (id == 0 && !"com.oplus.systemui".equals(packageName)) {
            id = resources.getIdentifier(name, type, "com.oplus.systemui");
        }
        return id;
    }
}
