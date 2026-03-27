package com.tnmower.tnmower.utils;

public class CRCUtil {

    public static String calcCRC(String data) {

        int crc = 0;

        for (int i = 0; i < data.length(); i++) {
            crc ^= data.charAt(i);
        }

        return String.format("%02X", crc);
    }
}