package com.tnmower.tnmower.bluetooth;

import android.app.*;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.*;

import androidx.core.app.NotificationCompat;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class BluetoothService extends Service {

    private static final String CHANNEL_ID = "TN_MOWER_BT";

    private BluetoothAdapter btAdapter;
    private BluetoothSocket socket;
    private InputStream input;
    private OutputStream output;

    private final String MAC = "00:21:13:00:00:00";

    private final UUID UUID_SPP =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final LinkedBlockingQueue<String> txQueue = new LinkedBlockingQueue<>();

    // =========================
    // STATUS TRACK
    // =========================
    private String lastStatus = "";

    // =========================
    // TIMEOUT
    // =========================
    private long lastRxTime = 0;
    private static final long RX_TIMEOUT = 3000;

    // =========================
    // LISTENER (แก้ให้รองรับครบ)
    // =========================
    public interface OnTelemetryListener {
        void onTelemetry(float volt, float currentL, float currentR, float tempL, float tempR);
    }

    private static OnTelemetryListener telemetryListener;

    public static void setTelemetryListener(OnTelemetryListener listener) {
        telemetryListener = listener;
    }

    // ==================================================
    @Override
    public void onCreate() {
        super.onCreate();

        btAdapter = BluetoothAdapter.getDefaultAdapter();

        startForegroundService();

        new Thread(this::connectionLoop).start();
        new Thread(this::txLoop).start();
    }

    // ==================================================
    private void startForegroundService() {

        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "TN Mower BT",
                    NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TN Mower")
                .setContentText("Bluetooth Running")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build();

        startForeground(1, notification);
    }

    // ==================================================
    private void connectionLoop() {

        while (running.get()) {

            long now = System.currentTimeMillis();

            if (connected.get() && (now - lastRxTime > RX_TIMEOUT)) {
                sendStatus("TIMEOUT");
                connected.set(false);
                safeClose();
            }

            if (!connected.get()) {
                connect();
            }

            SystemClock.sleep(500);
        }
    }

    // ==================================================
    private void connect() {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    sendStatus("NO_PERMISSION");
                    return;
                }
            }

            sendStatus("CONNECTING");

            btAdapter.cancelDiscovery();

            BluetoothDevice device = btAdapter.getRemoteDevice(MAC);

            socket = device.createRfcommSocketToServiceRecord(UUID_SPP);
            socket.connect();

            input = socket.getInputStream();
            output = socket.getOutputStream();

            connected.set(true);
            lastRxTime = System.currentTimeMillis();

            sendStatus("CONNECTED");

            new Thread(this::rxLoop).start();

        } catch (Exception e) {

            connected.set(false);
            sendStatus("RECONNECTING");
            safeClose();
        }
    }

    // ==================================================
    // 🔴 NEW: BINARY RX LOOP
    // ==================================================
    private void rxLoop() {

        byte[] buffer = new byte[32];

        while (running.get() && connected.get()) {

            try {

                int b = input.read();
                if (b == -1) continue;

                // 🔴 หา HEADER
                if ((b & 0xFF) != 0xAA) continue;

                // LEN
                int len = input.read();
                if (len <= 0 || len > 20) continue;

                // DATA
                int read = 0;
                while (read < len) {
                    int r = input.read(buffer, read, len - read);
                    if (r > 0) read += r;
                }

                // CRC
                int crcRx = input.read();

                // 🔴 CRC CHECK
                int crc = 0xAA ^ len;
                for (int i = 0; i < len; i++) {
                    crc ^= buffer[i];
                }

                if ((crc & 0xFF) != (crcRx & 0xFF)) continue;

                lastRxTime = System.currentTimeMillis();

                // ==================================================
                // 🔴 DECODE
                // ==================================================
                int idx = 0;

                int fault = buffer[idx++] & 0xFF;
                int sys = buffer[idx++] & 0xFF;

                int v = ((buffer[idx++] & 0xFF) << 8) | (buffer[idx++] & 0xFF);
                int iL = ((buffer[idx++] & 0xFF) << 8) | (buffer[idx++] & 0xFF);
                int iR = ((buffer[idx++] & 0xFF) << 8) | (buffer[idx++] & 0xFF);
                int tL = ((buffer[idx++] & 0xFF) << 8) | (buffer[idx++] & 0xFF);
                int tR = ((buffer[idx++] & 0xFF) << 8) | (buffer[idx++] & 0xFF);

                float volt = v / 100f;
                float curL = iL / 100f;
                float curR = iR / 100f;

                if (telemetryListener != null) {
                    telemetryListener.onTelemetry(volt, curL, curR, tL, tR);
                }

            } catch (Exception e) {

                connected.set(false);
                sendStatus("RECONNECTING");
                safeClose();
                break;
            }
        }
    }

    // ==================================================
    private void txLoop() {

        while (running.get()) {

            try {

                String msg = txQueue.take();

                if (connected.get() && output != null) {
                    output.write(msg.getBytes());
                    output.flush();
                }

            } catch (Exception ignored) {}
        }
    }

    // ==================================================
    private void sendStatus(String status) {

        if (status.equals(lastStatus)) return;

        lastStatus = status;

        Intent intent = new Intent("TNMOWER_STATUS");
        intent.putExtra("status", status);
        sendBroadcast(intent);
    }

    // ==================================================
    private void safeClose() {
        try { if (input != null) input.close(); } catch (Exception ignored) {}
        try { if (output != null) output.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    // ==================================================
    @Override
    public void onDestroy() {
        running.set(false);
        connected.set(false);
        safeClose();
        sendStatus("DISCONNECTED");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
