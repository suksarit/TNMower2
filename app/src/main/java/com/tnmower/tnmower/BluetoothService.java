package com.tnmower.tnmower;

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

    private int seq = 0;

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
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && intent.hasExtra("cmd")) {

            String cmd = intent.getStringExtra("cmd");

            if ("STOP".equals(cmd)) {
                sendPacket("CMD", "STOP");
            }
        }

        return START_STICKY;
    }

    // ==================================================
    private void startForegroundService() {

        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // 🔴 FIX API 26+
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

            if (!connected.get()) {
                connect();
            }

            SystemClock.sleep(2000);
        }
    }

    // ==================================================
    private void connect() {

        try {

            // 🔴 FIX Android 12+ permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {

                    sendStatus("NO_PERMISSION");
                    return;
                }
            }

            btAdapter.cancelDiscovery();

            BluetoothDevice device = btAdapter.getRemoteDevice(MAC);

            socket = device.createRfcommSocketToServiceRecord(UUID_SPP);
            socket.connect();

            input = socket.getInputStream();
            output = socket.getOutputStream();

            connected.set(true);

            sendStatus("CONNECTED");

            new Thread(this::rxLoop).start();

        } catch (SecurityException se) {

            connected.set(false);
            sendStatus("NO_PERMISSION");
            safeClose();

        } catch (Exception e) {

            connected.set(false);
            sendStatus("DISCONNECTED");
            safeClose();
        }
    }

    // ==================================================
    private void rxLoop() {

        StringBuilder buffer = new StringBuilder();

        while (running.get() && connected.get()) {

            try {

                int c = input.read();

                if (c == -1) continue;

                buffer.append((char) c);

                if (c == '>') {
                    handlePacket(buffer.toString());
                    buffer.setLength(0);
                }

            } catch (Exception e) {

                connected.set(false);
                sendStatus("DISCONNECTED");
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
    private void handlePacket(String packet) {

        if (!packet.startsWith("<") || !packet.endsWith(">")) return;

        try {

            packet = packet.substring(1, packet.length() - 1);

            String[] parts = packet.split(",");

            if (parts.length < 4) return;

            String raw = parts[0] + "," + parts[1] + "," + parts[2];

            if (!calcCRC(raw).equals(parts[3])) return;

            String type = parts[1];
            String data = parts[2];

            if (type.equals("TEL")) {

                Intent intent = new Intent("TNMOWER_TELEMETRY");
                intent.putExtra("data", data);
                sendBroadcast(intent);
            }

        } catch (Exception ignored) {}
    }

    // ==================================================
    private void sendPacket(String type, String data) {

        seq++;

        String raw = seq + "," + type + "," + data;
        String crc = calcCRC(raw);

        String packet = "<" + raw + "," + crc + ">";
        txQueue.offer(packet);
    }

    // ==================================================
    private void sendStatus(String status) {

        Intent intent = new Intent("TNMOWER_STATUS");
        intent.putExtra("status", status);
        sendBroadcast(intent);
    }

    // ==================================================
    private String calcCRC(String data) {

        int crc = 0;

        for (int i = 0; i < data.length(); i++) {
            crc ^= data.charAt(i);
        }

        return String.format("%02X", crc);
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
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}