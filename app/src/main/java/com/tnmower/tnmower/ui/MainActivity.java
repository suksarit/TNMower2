package com.tnmower.tnmower.ui;

import android.content.*;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.tnmower.tnmower.R;
import com.tnmower.tnmower.bluetooth.BluetoothService;
import com.tnmower.tnmower.model.TelemetryData;

public class MainActivity extends AppCompatActivity {

    private TextView txtVolt, txtStatus;
    private TextView txtTempL, txtTempR;
    private TextView txtM1, txtM2, txtM3, txtM4;

    private Button btnConnect, btnDisconnect, btnStop;

    private static final int REQ_BT = 100;
    private String selectedMAC = "";

    private boolean connected = false;
    private boolean connecting = false;

    private Handler reconnectHandler = new Handler();
    private Runnable reconnectRunnable;

    private static final int RECONNECT_DELAY = 3000;
    private static final int CONNECT_TIMEOUT = 5000;

    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT = 5;

    private boolean isReceiverRegistered = false;

    private Vibrator vibrator;

    // 🔴 NEW (ลด lag)
    private TelemetryData lastData = null;
    private long lastUiUpdate = 0;
    private static final long UI_INTERVAL = 100;

    // 🔴 NEW (bind service)
    private BluetoothService btService = null;
    private boolean isBound = false;

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
        }
    };

    // =========================
    // RECEIVER
    // =========================
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String status = intent.getStringExtra("status");

            if ("CONNECTED".equals(status)) {
                connected = true;
                connecting = false;
                reconnectAttempts = 0;
                stopReconnect();
            }

            if ("DISCONNECTED".equals(status)) {
                connected = false;
                connecting = false;
                scheduleReconnect();
            }

            updateStatusText(status);
            updateButtonState();
        }
    };

    // =========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        SharedPreferences sp = getSharedPreferences("TN_MOWER", MODE_PRIVATE);
        selectedMAC = sp.getString("MAC", "");

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

        updateButtonState();

        btnConnect.setOnClickListener(v -> {
            if (connecting || connected) return;

            reconnectAttempts = 0;

            if (selectedMAC.isEmpty()) {
                startActivityForResult(
                        new Intent(this, DeviceListActivity.class), REQ_BT);
            } else {
                startBluetooth(selectedMAC);
            }
        });

        btnDisconnect.setOnClickListener(v -> {
            stopReconnect();
            stopService(new Intent(this, BluetoothService.class));

            connected = false;
            connecting = false;

            txtStatus.setText(R.string.status_disconnected);
            txtStatus.setTextColor(Color.RED);

            updateButtonState();
        });

        btnStop.setOnClickListener(v -> sendStopCommand());

        // 🔴 TELEMETRY
        BluetoothService.setTelemetryListener((volt, m1, m2, m3, m4, tL, tR) -> {

            TelemetryData data = new TelemetryData(
                    volt, m1, m2, m3, m4, tL, tR
            );

            updateUI(data);
        });

        if (!selectedMAC.isEmpty()) {
            startBluetooth(selectedMAC);
        }
    }

    // =========================
    // 🔴 BIND SERVICE
    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    // =========================
    private void updateUI(TelemetryData data) {

        long now = System.currentTimeMillis();

        if (now - lastUiUpdate < UI_INTERVAL) return;
        if (!data.isValid()) return;

        if (lastData != null) {
            if (Math.abs(lastData.volt - data.volt) < 0.1f &&
                    Math.abs(lastData.getAverageCurrent() - data.getAverageCurrent()) < 0.1f &&
                    Math.abs(lastData.getMaxTemp() - data.getMaxTemp()) < 1f) {
                return;
            }
        }

        lastData = data;
        lastUiUpdate = now;

        runOnUiThread(() -> {

            txtVolt.setText(String.format("%.1f V", data.volt));

            txtTempL.setText(String.format("L: %.1f °C", data.tempL));
            txtTempR.setText(String.format("R: %.1f °C", data.tempR));

            txtM1.setText(String.format("M1: %.1f A", data.m1));
            txtM2.setText(String.format("M2: %.1f A", data.m2));
            txtM3.setText(String.format("M3: %.1f A", data.m3));
            txtM4.setText(String.format("M4: %.1f A", data.m4));

            setColorSmart(txtVolt, data.volt, 20, 24);

            setColorSmart(txtTempL, data.tempL, 60, 80);
            setColorSmart(txtTempR, data.tempR, 60, 80);

            setColorSmart(txtM1, data.m1, 20, 30);
            setColorSmart(txtM2, data.m2, 20, 30);
            setColorSmart(txtM3, data.m3, 20, 30);
            setColorSmart(txtM4, data.m4, 20, 30);

            if (data.hasError()) {
                vibrateAlert();
            }
        });
    }

    private void setColorSmart(TextView tv, float value, float warn, float danger) {

        if (value >= danger) {
            tv.setTextColor(Color.RED);
        } else if (value >= warn) {
            tv.setTextColor(0xFFFFA500);
        } else {
            tv.setTextColor(Color.WHITE);
        }
    }

    private void vibrateAlert() {
        if (vibrator == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(200);
        }
    }

    private void startBluetooth(String mac) {

        if (connecting) return;

        connecting = true;
        connected = false;

        txtStatus.setText(R.string.status_connecting);
        txtStatus.setTextColor(Color.YELLOW);

        updateButtonState();

        Intent intent = new Intent(this, BluetoothService.class);
        intent.putExtra("MAC", mac);
        startService(intent);

        new Handler().postDelayed(() -> {
            if (!connected) {
                connecting = false;

                txtStatus.setText(R.string.status_failed);
                txtStatus.setTextColor(Color.RED);

                scheduleReconnect();
                updateButtonState();
            }
        }, CONNECT_TIMEOUT);
    }

    private void scheduleReconnect() {

        if (reconnectAttempts >= MAX_RECONNECT) return;

        reconnectAttempts++;

        reconnectRunnable = () -> {
            if (!connected && !connecting) {
                startBluetooth(selectedMAC);
            }
        };

        reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY);
    }

    private void stopReconnect() {
        if (reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }
    }

    // 🔴 STOP ถูกต้องระดับระบบ
    private void sendStopCommand() {

        if (btService != null && isBound) {
            btService.sendStop();
        }
    }

    private void updateButtonState() {
        btnConnect.setEnabled(!connected && !connecting);
        btnDisconnect.setEnabled(connected);
    }

    private void updateStatusText(String status) {
        txtStatus.setText(getString(R.string.status_prefix, status));
        txtStatus.setTextColor(status.contains("CONNECTED") ? Color.GREEN : Color.RED);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter("TNMOWER_STATUS");

            ContextCompat.registerReceiver(
                    this,
                    statusReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );

            isReceiverRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isReceiverRegistered) {
            unregisterReceiver(statusReceiver);
            isReceiverRegistered = false;
        }
    }
}
