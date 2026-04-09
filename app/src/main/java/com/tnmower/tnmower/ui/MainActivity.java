package com.tnmower.tnmower.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tnmower.tnmower.R;
import com.tnmower.tnmower.bluetooth.BluetoothService;
import com.tnmower.tnmower.model.TelemetryData;

public class MainActivity extends AppCompatActivity {

    private static final int CONNECT_TIMEOUT = 5000;
    private static final long UI_INTERVAL = 100;

    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());

    // UI
    private TextView txtVolt, txtStatus;
    private TextView txtTempL, txtTempR;
    private TextView txtM1, txtM2, txtM3, txtM4;

    private Button btnConnect, btnDisconnect, btnStop, btnRetry;

    private String selectedMAC = "";

    private boolean connected = false;
    private boolean connecting = false;

    private boolean isReceiverRegistered = false;

    private Runnable reconnectRunnable;

    // =========================
// 🔴 STATUS RECEIVER (UI PRO)
// FILE: MainActivity.java
// SECTION: Global variable
// =========================
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            try {

                // 🔴 1. กัน Activity พัง
                if (isFinishing() || isDestroyed()) return;

                // 🔴 2. กัน intent พัง
                if (intent == null) return;

                String status = intent.getStringExtra("status");
                if (status == null) return;

                // 🔴 3. กัน view ยังไม่ bind
                if (txtStatus == null) return;

                // 🔴 4. ไม่ใช้ runOnUiThread (สำคัญมาก)
                // BroadcastReceiver = main thread อยู่แล้ว

                switch (status) {

                    case "CONNECTED":
                        connected = true;
                        connecting = false;

                        txtStatus.setText("CONNECTED");
                        txtStatus.setTextColor(Color.GREEN);
                        break;

                    case "CONNECTING":
                        txtStatus.setText("CONNECTING...");
                        txtStatus.setTextColor(Color.YELLOW);
                        break;

                    case "CONNECT_FAIL":
                    case "HANDSHAKE_FAIL":
                    case "RECONNECT_FAIL":
                    case "NO_PERMISSION":
                    case "INVALID_MAC":
                    case "CONNECT_TIMEOUT":
                    case "SOCKET_FAIL":
                    case "STREAM_FAIL":
                    case "STREAM_NULL":

                        connected = false;
                        connecting = false;

                        txtStatus.setText("ERROR: " + status);
                        txtStatus.setTextColor(Color.RED);
                        break;

                    case "DISCONNECTED":
                        connected = false;
                        connecting = false;

                        txtStatus.setText("DISCONNECTED");
                        txtStatus.setTextColor(Color.RED);
                        break;

                    case "STOPPED":
                        connected = false;
                        connecting = false;

                        txtStatus.setText("STOPPED");
                        txtStatus.setTextColor(Color.RED);
                        break;

                    default:
                        txtStatus.setText(status);
                        txtStatus.setTextColor(Color.GRAY);
                        break;
                }

                updateButtonState();

            } catch (Throwable ignored) {
                // 🔴 กัน crash 100%
            }
        }
    };

    private TelemetryData smoothData = null;
    private long lastUiUpdate = 0;
    private BluetoothService btService = null;
    private boolean isBound = false;
    // 🔴 FIX: กัน callback เก่า / race condition
    private volatile long currentSessionId = 0;

    // =========================
    // SERVICE
    // =========================
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            btService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

            isBound = false;
            btService = null;

            connected = false;
            connecting = false;

            if (isFinishing() || isDestroyed()) return;

            try {
                txtStatus.setText("SERVICE LOST");
                txtStatus.setTextColor(Color.RED);
            } catch (Throwable ignored) {
            }

            updateButtonState();
        }
    };

    // DEVICE SELECT
    // =========================
    private final ActivityResultLauncher<Intent> deviceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String mac = result.getData().getStringExtra("MAC");
                    if (mac != null) {

                        selectedMAC = mac;

                        SharedPreferences sp = getSharedPreferences("TN_MOWER", MODE_PRIVATE);
                        sp.edit().putString("MAC", mac).apply();

                        startBluetooth(mac);
                    }
                }
            });

    // =========================
    // onCreate
    // =========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // =========================
        // 🔴 FIX: RESET SERVICE กัน crash ค้าง (สำคัญมาก)
        // =========================
        try {

            // 🔴 FIX: ปิด receiver ก่อน kill service
            if (isReceiverRegistered) {
                unregisterReceiver(statusReceiver);
                isReceiverRegistered = false;
            }

            stopService(new Intent(this, BluetoothService.class));

        } catch (Exception ignored) {
        }

        setContentView(R.layout.activity_main);

        // =========================
        // 🔴 Android 13+ Notification Permission
        // =========================
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{
                        android.Manifest.permission.POST_NOTIFICATIONS
                }, 2001);
            }
        }

        requestBluetoothPermission();

        // =========================
        // 🔴 RESET STATE กัน crash loop
        // =========================
        connecting = false;
        connected = false;
        isBound = false;

        SharedPreferences sp = getSharedPreferences("TN_MOWER", MODE_PRIVATE);
        selectedMAC = sp.getString("MAC", "");

        // =========================
        // 🔴 BIND UI
        // =========================
        txtVolt = findViewById(R.id.txtVolt);
        txtStatus = findViewById(R.id.txtStatus);

        txtTempL = findViewById(R.id.txtTempL);
        txtTempR = findViewById(R.id.txtTempR);

        txtM1 = findViewById(R.id.txtM1);
        txtM2 = findViewById(R.id.txtM2);
        txtM3 = findViewById(R.id.txtM3);
        txtM4 = findViewById(R.id.txtM4);

        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnStop = findViewById(R.id.btnStop);
        btnRetry = findViewById(R.id.btnRetry);

        updateButtonState();

        // =========================
        // 🔴 STATUS เริ่มต้น
        // =========================
        if (!selectedMAC.isEmpty()) {

            txtStatus.setText("READY (PRESS CONNECT)");
            txtStatus.setTextColor(Color.GRAY);

        } else {

            txtStatus.setText("NO DEVICE");
            txtStatus.setTextColor(Color.RED);
        }

        // =========================
        // 🔴 CONNECT
        // =========================
        btnConnect.setOnClickListener(v -> {

            if (connecting || connected) return;

            if (!hasPermission()) {
                requestBluetoothPermission();
                Toast.makeText(this, "กรุณาอนุญาต Bluetooth ก่อน", Toast.LENGTH_SHORT).show();
                return;
            }

            deviceLauncher.launch(new Intent(this, DeviceListActivity.class));
        });

        // =========================
        // 🔴 DISCONNECT (FIX crash loop)
        // =========================
        btnDisconnect.setOnClickListener(v -> {

            stopReconnect();

            try {
                if (btService != null) {
                    btService.sendStop();
                }
            } catch (Exception ignored) {
            }

            // 🔴 unbind ก่อนเสมอ
            try {
                if (isBound) {
                    unbindService(serviceConnection);
                    isBound = false;
                }
            } catch (Exception ignored) {
            }

            // 🔴 kill service ทิ้งเลย
            try {
                stopService(new Intent(this, BluetoothService.class));
            } catch (Exception ignored) {
            }

            // 🔴 reset state
            connected = false;
            connecting = false;

            updateButtonState();

            txtStatus.setText("DISCONNECTED");
            txtStatus.setTextColor(Color.RED);
        });

        // =========================
        // 🔴 RETRY (FIX state ค้าง)
        // =========================
        btnRetry.setOnClickListener(v -> {

            // 🔴 reset state ให้ชัวร์
            connecting = false;
            connected = false;

            try {
                stopService(new Intent(this, BluetoothService.class));
            } catch (Exception ignored) {
            }

            txtStatus.setText("READY");
            txtStatus.setTextColor(Color.GRAY);

            updateButtonState();
        });

        // =========================
        // 🔴 STOP
        // =========================
        btnStop.setOnClickListener(v -> sendStopCommand());
    }

    // =========================
    // 🔴 สำคัญ: listener ต้องอยู่ใน onStart
    // =========================

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // =========================
        // 🔴 FIX: ปิด BroadcastReceiver
        // =========================
        try {
            if (isReceiverRegistered) {
                unregisterReceiver(statusReceiver);
                isReceiverRegistered = false;
            }
        } catch (Exception ignored) {
        }

        // =========================
        // 🔴 FIX: ตัด Telemetry listener
        // =========================
        try {
            BluetoothService.setTelemetryListener(null);
        } catch (Exception ignored) {
        }

        // =========================
        // 🔴 FIX: unbind service
        // =========================
        try {
            if (isBound) {
                unbindService(serviceConnection);
                isBound = false;
            }
        } catch (Exception ignored) {
        }

        // =========================
        // 🔴 FIX: stop service (กันค้าง)
        // =========================
        try {
            stopService(new Intent(this, BluetoothService.class));
        } catch (Exception ignored) {
        }

        // =========================
        // 🔴 RESET STATE กันเปิดใหม่พัง
        // =========================
        connected = false;
        connecting = false;
    }


    @Override
    protected void onStart() {
        super.onStart();

        // 🔴 กันกรณี Activity กำลังจะถูกปิด/ยังไม่พร้อม
        if (isFinishing() || isDestroyed()) return;

        // 🔴 ตั้ง listener แบบปลอดภัย
        BluetoothService.setTelemetryListener((flags, error, volt, m1, m2, m3, m4, tL, tR) -> {

            try {

                // 🔴 กัน callback ยิงหลัง Activity ถูกทำลาย
                if (isFinishing() || isDestroyed()) return;

                final TelemetryData raw = new TelemetryData(
                        flags, error,
                        volt, m1, m2, m3, m4,
                        tL, tR
                );

                // 🔴 บังคับเข้าฝั่ง UI thread + guard ซ้ำ
                runOnUiThread(() -> {

                    if (isFinishing() || isDestroyed()) return;

                    try {
                        updateUI(raw);
                    } catch (Throwable ignored) {
                        // 🔴 กัน UI crash ทุกกรณี
                    }
                });

            } catch (Throwable ignored) {
                // 🔴 กัน crash จาก callback ทั้งหมด
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        // =========================
        // 🔴 FIX: ตัด Telemetry callback กัน crash
        // =========================
        try {
            BluetoothService.setTelemetryListener(null);
        } catch (Exception ignored) {
        }

        // =========================
        // 🔴 FIX: ปิด BroadcastReceiver (กันยิงใส่ UI ตอนปิด)
        // =========================
        try {
            if (isReceiverRegistered) {
                unregisterReceiver(statusReceiver);
                isReceiverRegistered = false;
            }
        } catch (Exception ignored) {
        }

        // =========================
        // 🔴 FIX: หยุด reconnect handler
        // =========================
        try {
            if (reconnectRunnable != null) {
                reconnectHandler.removeCallbacks(reconnectRunnable);
                reconnectRunnable = null;
            }
        } catch (Exception ignored) {
        }

        // =========================
        // 🔴 FIX: reset state กันเพี้ยนตอนกลับเข้าใหม่
        // =========================
        connecting = false;
        connected = false;
    }

    private boolean hasPermission() {

        // =========================
        // 🔴 ANDROID 12+ (API 31+)
        // =========================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            boolean connectGranted =
                    checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED;

            boolean scanGranted =
                    checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                            == PackageManager.PERMISSION_GRANTED;

            return connectGranted && scanGranted;
        }

        // =========================
        // 🔴 ANDROID 6 - 11 (API 23-30)
        // =========================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            boolean locationGranted =
                    checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;

            return locationGranted;
        }

        // =========================
        // 🔴 ต่ำกว่า Android 6
        // =========================
        return true;
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
            }, 1001);
        }
    }

    private void startBluetooth(String mac) {

        // =========================
        // 🔴 GUARD: Activity state
        // =========================
        if (isFinishing() || isDestroyed()) return;

        // =========================
        // 🔴 TOKEN กัน callback เก่า (สำคัญมาก)
        // =========================
        final long sessionId = System.currentTimeMillis();
        currentSessionId = sessionId;

        // =========================
        // 🔴 HARD RESET (กันค้าง + กัน crash loop)
        // =========================
        try {
            if (isBound) {
                unbindService(serviceConnection);
                isBound = false;
            }
        } catch (Exception ignored) {
        }

        try {
            stopService(new Intent(this, BluetoothService.class));
        } catch (Exception ignored) {
        }

        try {
            reconnectHandler.removeCallbacksAndMessages(null);
        } catch (Exception ignored) {
        }

        // =========================
        // 🔴 VALIDATE MAC
        // =========================
        if (mac == null || mac.length() < 17) {

            txtStatus.setText("INVALID MAC");
            txtStatus.setTextColor(Color.RED);

            connecting = false;
            connected = false;

            updateButtonState();
            return;
        }

        // =========================
        // 🔴 กันกดซ้ำ
        // =========================
        if (connecting) return;

        connecting = true;
        connected = false;

        txtStatus.setText("CONNECTING...");
        txtStatus.setTextColor(Color.YELLOW);

        updateButtonState();

        // =========================
        // 🔴 START SERVICE
        // =========================
        Intent intent = new Intent(this, BluetoothService.class);
        intent.putExtra("MAC", mac);

        try {

            startService(intent);

            boolean ok = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

            if (!ok) {
                throw new RuntimeException("Bind failed");
            }

        } catch (Throwable e) {

            connecting = false;
            connected = false;

            try {
                stopService(new Intent(this, BluetoothService.class));
            } catch (Exception ignored) {
            }

            txtStatus.setText("START FAIL");
            txtStatus.setTextColor(Color.RED);

            updateButtonState();
            return;
        }

        // =========================
        // 🔴 TIMEOUT (กัน race จริง)
        // =========================
        reconnectHandler.postDelayed(() -> {

            // 🔴 กัน callback เก่า
            if (sessionId != currentSessionId) return;

            if (isFinishing() || isDestroyed()) return;

            if (connecting && !connected) {

                connecting = false;

                txtStatus.setText("TIMEOUT");
                txtStatus.setTextColor(Color.RED);

                updateButtonState();

                try {
                    if (isBound) {
                        unbindService(serviceConnection);
                        isBound = false;
                    }
                } catch (Exception ignored) {
                }

                try {
                    stopService(new Intent(this, BluetoothService.class));
                } catch (Exception ignored) {
                }
            }

        }, CONNECT_TIMEOUT);
    }

    private void stopReconnect() {

        // =========================
        // 🔴 FIX: ล้าง callback ทั้งหมด
        // =========================
        try {
            reconnectHandler.removeCallbacksAndMessages(null);
        } catch (Exception ignored) {
        }

        // =========================
        // 🔴 FIX: reset runnable
        // =========================
        reconnectRunnable = null;

        // =========================
        // 🔴 FIX: reset state กันค้าง
        // =========================
        connecting = false;

        // ❗ ไม่แตะ connected (ให้ service เป็นตัวบอก)
    }

    private void sendStopCommand() {

        // =========================
        // 🔴 GUARD: Activity state
        // =========================
        if (isFinishing() || isDestroyed()) return;

        // =========================
        // 🔴 GUARD: service state
        // =========================
        if (btService == null || !isBound) return;

        // =========================
        // 🔴 GUARD: ต้อง connected เท่านั้น
        // =========================
        if (!connected) return;

        try {

            btService.sendStop();

        } catch (Throwable e) {

            // 🔴 FIX: กัน crash ถ้า service พัง
            try {
                stopService(new Intent(this, BluetoothService.class));
            } catch (Exception ignored) {
            }

            connected = false;
            connecting = false;

            txtStatus.setText("STOP FAIL");
            txtStatus.setTextColor(Color.RED);

            updateButtonState();
        }
    }

    private void updateUI(TelemetryData raw) {

        long now = System.currentTimeMillis();
        if (now - lastUiUpdate < UI_INTERVAL) return;
        if (raw == null || !raw.isValid()) return;

        if (smoothData == null) smoothData = raw;

        smoothData.volt = smooth(raw.volt, smoothData.volt, 0.1f);

        smoothData.m1 = smooth(raw.m1, smoothData.m1, 0.2f);
        smoothData.m2 = smooth(raw.m2, smoothData.m2, 0.2f);
        smoothData.m3 = smooth(raw.m3, smoothData.m3, 0.2f);
        smoothData.m4 = smooth(raw.m4, smoothData.m4, 0.2f);

        smoothData.tempL = smooth(raw.tempL, smoothData.tempL, 0.1f);
        smoothData.tempR = smooth(raw.tempR, smoothData.tempR, 0.1f);

        lastUiUpdate = now;

        runOnUiThread(() -> {

            // 🔴 FIX: กัน crash
            if (isFinishing() || isDestroyed()) return;

            if (txtVolt == null) return;

            try {

                txtVolt.setText(getString(R.string.format_voltage, smoothData.volt));

                txtTempL.setText(getString(R.string.format_temp_l, smoothData.tempL));
                txtTempR.setText(getString(R.string.format_temp_r, smoothData.tempR));

                txtM1.setText(getString(R.string.format_m1, smoothData.m1));
                txtM2.setText(getString(R.string.format_m2, smoothData.m2));
                txtM3.setText(getString(R.string.format_m3, smoothData.m3));
                txtM4.setText(getString(R.string.format_m4, smoothData.m4));

                txtStatus.setText("RUNNING");

            } catch (Throwable ignored) {
            }
        });
    }

    private float smooth(float target, float current, float alpha) {
        return current + alpha * (target - current);
    }

    private void updateButtonState() {

        // =========================
        // 🔴 GUARD: Activity state
        // =========================
        if (isFinishing() || isDestroyed()) return;

        // =========================
        // 🔴 FIX: บังคับทำบน UI thread
        // =========================
        runOnUiThread(() -> {

            try {

                if (isFinishing() || isDestroyed()) return;

                // 🔴 normalize state กันเพี้ยน
                boolean isConnecting = connecting;
                boolean isConnected = connected;

                if (isConnecting && isConnected) {
                    // ❗ state ผิด → reset
                    isConnecting = false;
                }

                // =========================
                // 🔴 CONNECT BUTTON
                // =========================
                if (btnConnect != null) {
                    btnConnect.setEnabled(!isConnected && !isConnecting);
                }

                // =========================
                // 🔴 DISCONNECT BUTTON
                // =========================
                if (btnDisconnect != null) {
                    btnDisconnect.setEnabled(isConnected);
                }

                // =========================
                // 🔴 RETRY BUTTON
                // =========================
                if (btnRetry != null) {
                    btnRetry.setEnabled(!isConnecting && !isConnected);
                }

            } catch (Throwable ignored) {
                // 🔴 กัน crash ทุกกรณี
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isFinishing() || isDestroyed()) return;

        try {

            if (isReceiverRegistered) return;

            IntentFilter filter = new IntentFilter("TNMOWER_STATUS");

            // 🔴 FIX: ใช้ ContextCompat → Lint หาย
            ContextCompat.registerReceiver(
                    this,
                    statusReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );

            isReceiverRegistered = true;

        } catch (Throwable e) {
            isReceiverRegistered = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // =========================
        // 🔴 FIX: ป้องกัน state ไม่พร้อม
        // =========================
        if (isFinishing() || isDestroyed()) {
            // ยังต้อง cleanup ต่อด้านล่าง
        }

        // =========================
        // 🔴 FIX: ปิด BroadcastReceiver
        // =========================
        try {
            if (isReceiverRegistered) {
                unregisterReceiver(statusReceiver);
                isReceiverRegistered = false;
            }
        } catch (Throwable ignored) {
        }

        // =========================
        // 🔴 FIX: ตัด Telemetry callback กันยิงใส่ UI
        // =========================
        try {
            BluetoothService.setTelemetryListener(null);
        } catch (Throwable ignored) {
        }

        // =========================
        // 🔴 FIX: หยุด handler ทุกตัว (timeout/reconnect)
        // =========================
        try {
            reconnectHandler.removeCallbacksAndMessages(null);
        } catch (Throwable ignored) {
        }

        // =========================
        // 🔴 FIX: reset state กันเพี้ยนตอนกลับเข้าใหม่
        // =========================
        connecting = false;
        // ❗ ไม่บังคับ connected = false ที่นี่
        // ให้ Service เป็นตัวบอกสถานะจริง
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // =========================
        // 🔴 GUARD: Activity state
        // =========================
        if (isFinishing() || isDestroyed()) return;

        if (requestCode != 1001) return;

        // =========================
        // 🔴 FIX: กัน array ว่าง
        // =========================
        if (grantResults == null || grantResults.length == 0) {

            runOnUiThread(() -> {
                if (txtStatus != null) {
                    txtStatus.setText("PERMISSION ERROR");
                    txtStatus.setTextColor(Color.RED);
                }
            });

            return;
        }

        // =========================
        // 🔴 CHECK RESULT
        // =========================
        boolean granted = true;

        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }

        final boolean finalGranted = granted;

        // =========================
        // 🔴 UPDATE UI SAFE
        // =========================
        runOnUiThread(() -> {

            if (isFinishing() || isDestroyed()) return;
            if (txtStatus == null) return;

            if (finalGranted) {

                txtStatus.setText("READY");
                txtStatus.setTextColor(Color.GREEN);

            } else {

                txtStatus.setText("NO PERMISSION");
                txtStatus.setTextColor(Color.RED);

                // 🔴 reset state กันค้าง
                connecting = false;
                connected = false;

                updateButtonState();
            }
        });
    }

}