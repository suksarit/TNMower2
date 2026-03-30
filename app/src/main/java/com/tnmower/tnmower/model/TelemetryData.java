package com.tnmower.tnmower.model;

public class TelemetryData {

    // =========================
    // 🔴 LIMIT (ปรับได้)
    // =========================
    private static final float MAX_VOLT = 60f;
    private static final float MAX_CURRENT = 100f;
    private static final float MAX_TEMP = 120f;

    // =========================
    // 🔴 DATA
    // =========================
    public float volt;

    public float m1;
    public float m2;
    public float m3;
    public float m4;

    public float tempL;
    public float tempR;

    // ==================================================
    // CONSTRUCTOR
    // ==================================================
    public TelemetryData(float volt,
                         float m1,
                         float m2,
                         float m3,
                         float m4,
                         float tempL,
                         float tempR) {

        // 🔴 sanitize ค่า
        this.volt = clamp(volt, 0, MAX_VOLT);

        this.m1 = clamp(m1, 0, MAX_CURRENT);
        this.m2 = clamp(m2, 0, MAX_CURRENT);
        this.m3 = clamp(m3, 0, MAX_CURRENT);
        this.m4 = clamp(m4, 0, MAX_CURRENT);

        this.tempL = clamp(tempL, -20, MAX_TEMP);
        this.tempR = clamp(tempR, -20, MAX_TEMP);
    }

    // ==================================================
    // 🔴 HELPER
    // ==================================================
    public float getAverageCurrent() {
        return (m1 + m2 + m3 + m4) * 0.25f;
    }

    public float getMaxTemp() {
        return Math.max(tempL, tempR);
    }

    // ==================================================
    // 🔴 ALERT SYSTEM (ใช้กับ UI สีแดง)
    // ==================================================
    public boolean isVoltageLow() {
        return volt < 20f;
    }

    public boolean isCurrentHigh() {
        return m1 > 60 || m2 > 60 || m3 > 60 || m4 > 60;
    }

    public boolean isTempHigh() {
        return tempL > 80 || tempR > 80;
    }

    public boolean hasError() {
        return isVoltageLow() || isCurrentHigh() || isTempHigh();
    }

    // ==================================================
    // 🔴 VALIDATION
    // ==================================================
    public boolean isValid() {

        if (Float.isNaN(volt)) return false;
        if (Float.isNaN(m1) || Float.isNaN(m2) ||
                Float.isNaN(m3) || Float.isNaN(m4)) return false;
        if (Float.isNaN(tempL) || Float.isNaN(tempR)) return false;

        return true;
    }

    // ==================================================
    // 🔴 CLAMP
    // ==================================================
    private float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
