package com.tnmower.tnmower.model;

public class TelemetryData {

    // 🔴 Voltage
    public float volt;

    // 🔴 Current 4 มอเตอร์
    public float m1;
    public float m2;
    public float m3;
    public float m4;

    // 🔴 Temperature
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

        this.volt = volt;

        this.m1 = m1;
        this.m2 = m2;
        this.m3 = m3;
        this.m4 = m4;

        this.tempL = tempL;
        this.tempR = tempR;
    }

    // ==================================================
    // HELPER (ใช้กับ gauge กลาง)
    // ==================================================
    public float getAverageCurrent() {
        return (m1 + m2 + m3 + m4) * 0.25f;
    }

    public float getMaxTemp() {
        return Math.max(tempL, tempR);
    }
}