package com.tnmower.tnmower.utils;

public class CRCUtil {

    public static int crc16(byte[] data, int len) {

        int crc = 0xFFFF;

        for (int i = 0; i < len; i++) {
            crc = crc16Update(crc, data[i] & 0xFF);
        }

        return crc & 0xFFFF;
    }

    public static int crc16Update(int crc, int data) {

        crc ^= data;

        for (int i = 0; i < 8; i++) {
            if ((crc & 1) != 0) {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc = crc >> 1;
            }
        }

        return crc & 0xFFFF;
    }
}