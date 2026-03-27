package com.tnmower.tnmower.model;

public class TelemetryData {

    public float volt;
    public float current;
    public float temp;

    public TelemetryData(float volt, float current, float temp) {
        this.volt = volt;
        this.current = current;
        this.temp = temp;
    }
}