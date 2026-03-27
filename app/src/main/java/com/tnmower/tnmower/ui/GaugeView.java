package com.tnmower.tnmower.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class GaugeView extends View {

    private float value = 0;
    private float maxValue = 100;

    private Paint bgPaint;
    private Paint fgPaint;
    private Paint textPaint;

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);

        bgPaint = new Paint();
        bgPaint.setColor(Color.DKGRAY);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(20);
        bgPaint.setAntiAlias(true);

        fgPaint = new Paint();
        fgPaint.setColor(Color.GREEN);
        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeWidth(20);
        fgPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
    }

    // ==================================================
    // SET VALUE (แก้ให้ปลอดภัย)
    // ==================================================
    public void setValue(float value) {

        if (value < 0) value = 0;
        if (value > maxValue) value = maxValue;

        this.value = value;

        invalidate();
    }

    public void setMaxValue(float max) {
        this.maxValue = max;
    }

    // ==================================================
    // DRAW
    // ==================================================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        int size = Math.min(w, h) - 40;

        RectF rect = new RectF(
                (w - size) / 2f,
                (h - size) / 2f,
                (w + size) / 2f,
                (h + size) / 2f
        );

        // =========================
        // COLOR LOGIC (เพิ่มใหม่)
        // =========================
        float percent = value / maxValue;

        if (percent < 0.5f) {
            fgPaint.setColor(Color.GREEN);
        } else if (percent < 0.8f) {
            fgPaint.setColor(Color.YELLOW);
        } else {
            fgPaint.setColor(Color.RED);
        }

        // background arc
        canvas.drawArc(rect, 180, 180, false, bgPaint);

        // foreground arc
        float sweep = percent * 180;
        canvas.drawArc(rect, 180, sweep, false, fgPaint);

        // =========================
        // TEXT (ปรับตำแหน่ง)
        // =========================
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = h / 2f - (fm.ascent + fm.descent) / 2;

        canvas.drawText(String.valueOf((int) value), w / 2f, textY, textPaint);
    }
}

