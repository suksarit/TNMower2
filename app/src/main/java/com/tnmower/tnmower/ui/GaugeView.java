package com.tnmower.tnmower.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class GaugeView extends View {

    private float value = 0f;
    private float maxValue = 100f;

    private int gaugeColor = Color.GREEN;
    private boolean useAutoColor = true;

    private String unit = "";

    private Paint bgPaint;
    private Paint fgPaint;
    private Paint textPaint;

    private RectF rect = new RectF();

    private float strokeWidth = 18f;

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.DKGRAY);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeCap(Paint.Cap.ROUND);

        fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    // ==================================================
    // SET VALUE
    // ==================================================
    public void setValue(float v) {

        if (Float.isNaN(v) || Float.isInfinite(v)) return;

        if (v < 0) v = 0;
        if (v > maxValue) v = maxValue;

        if (Math.abs(this.value - v) < 0.01f) return;

        this.value = v;

        // 🔴 ใช้ postInvalidate กัน thread crash
        postInvalidate();
    }

    public void setMaxValue(float max) {
        if (max <= 0) return;
        this.maxValue = max;
    }

    public void setColor(int color) {
        this.gaugeColor = color;
        this.useAutoColor = false;
        invalidate();
    }

    public void setAutoColor(boolean enable) {
        this.useAutoColor = enable;
        invalidate();
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    // ==================================================
    // SIZE CHANGED (scale UI อัตโนมัติ)
    // ==================================================
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        int size = Math.min(w, h);

        // 🔴 scale stroke
        strokeWidth = size * 0.08f;
        bgPaint.setStrokeWidth(strokeWidth);
        fgPaint.setStrokeWidth(strokeWidth);

        // 🔴 scale text
        textPaint.setTextSize(size * 0.22f);
    }

    // ==================================================
    // DRAW
    // ==================================================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        if (w == 0 || h == 0) return;

        int padding = (int)(strokeWidth);
        int size = Math.min(w, h) - padding * 2;

        rect.set(
                (w - size) / 2f,
                (h - size) / 2f,
                (w + size) / 2f,
                (h + size) / 2f
        );

        float percent = (maxValue == 0) ? 0 : (value / maxValue);
        percent = Math.max(0f, Math.min(percent, 1f));

        // ==================================================
        // COLOR
        // ==================================================
        if (useAutoColor) {
            if (percent < 0.5f) {
                fgPaint.setColor(Color.GREEN);
            } else if (percent < 0.8f) {
                fgPaint.setColor(Color.YELLOW);
            } else {
                fgPaint.setColor(Color.RED);
            }
        } else {
            fgPaint.setColor(gaugeColor);
        }

        // background
        canvas.drawArc(rect, 180, 180, false, bgPaint);

        // foreground
        canvas.drawArc(rect, 180, percent * 180f, false, fgPaint);

        // ==================================================
        // TEXT
        // ==================================================
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = h / 2f - (fm.ascent + fm.descent) / 2;

        String text;

        if (unit == null || unit.isEmpty()) {
            text = String.format(Locale.US, "%.0f", value);
        } else {
            text = String.format(Locale.US, "%.1f %s", value, unit);
        }

        canvas.drawText(text, w / 2f, textY, textPaint);
    }
}