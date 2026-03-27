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

public class MainActivity extends AppCompatActivity {

    private GaugeView gaugeVolt, gaugeCurrent, gaugeTemp;

    private TextView txtVolt, txtCurrent, txtTemp, txtStatus;

    // 🔴 เปลี่ยนเป็น 4 มอเตอร์
    private TextView txtM1, txtM2, txtM3, txtM4;
    private TextView txtTempL, txtTempR;

    private Button btnConnect, btnDisconnect, btnStop;

    private static final int REQ_BT = 100;
    private String selectedMAC = "";

    private float targetVolt = 0, targetCurrent = 0, targetTemp = 0;
    private float displayVolt = 0, displayCurrent = 0, displayTemp = 0;

    // 🔴 current 4 ตัว
    private float m1 = 0, m2 = 0, m3 = 0, m4 = 0;
    private float tempL = 0, tempR = 0;

    private final Handler handler = new Handler();
    private boolean isLoopRunning = false;

    private boolean fault = false;
    private boolean warning = false;

    private boolean isReceiverRegistered = false;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            updateStatusText(status);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sp = getSharedPreferences("TN_MOWER", MODE_PRIVATE);
        selectedMAC = sp.getString("MAC", "");

        gaugeVolt = findViewById(R.id.gaugeVolt);
        gaugeCurrent = findViewById(R.id.gaugeCurrent);
        gaugeTemp = findViewById(R.id.gaugeTemp);

        txtVolt = findViewById(R.id.txtVolt);
        txtCurrent = findViewById(R.id.txtCurrent);
        txtTemp = findViewById(R.id.txtTemp);
        txtStatus = findViewById(R.id.txtStatus);

        // 🔴 ใหม่
        txtM1 = findViewById(R.id.txtM1);
        txtM2 = findViewById(R.id.txtM2);
        txtM3 = findViewById(R.id.txtM3);
        txtM4 = findViewById(R.id.txtM4);

        txtTempL = findViewById(R.id.txtTempL);
        txtTempR = findViewById(R.id.txtTempR);

        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnStop = findViewById(R.id.btnStop);

        txtStatus.setText(getString(R.string.status_disconnected));

        gaugeVolt.setMaxValue(30);
        gaugeCurrent.setMaxValue(50);
        gaugeTemp.setMaxValue(120);

        gaugeVolt.setUnit("V");
        gaugeCurrent.setUnit("A");
        gaugeTemp.setUnit("°C");

        gaugeVolt.setAutoColor(false);
        gaugeVolt.setColor(Color.GREEN);

        gaugeCurrent.setAutoColor(true);
        gaugeTemp.setAutoColor(true);

        btnConnect.setOnClickListener(v -> {
            if (selectedMAC.isEmpty()) {
                startActivityForResult(
                        new Intent(this, DeviceListActivity.class), REQ_BT);
            } else {
                startBluetooth(selectedMAC);
            }
        });

        btnDisconnect.setOnClickListener(v -> {
            stopService(new Intent(this, BluetoothService.class));
            updateStatusText("DISCONNECTED");
        });

        btnStop.setOnClickListener(v -> sendStopCommand());

        // 🔴 เปลี่ยนเป็นรับ 4 ค่า
        BluetoothService.setTelemetryListener((volt, c1, c2, c3, c4, tL, tR) -> {

            fault = false;
            warning = false;

            m1 = c1;
            m2 = c2;
            m3 = c3;
            m4 = c4;

            tempL = tL;
            tempR = tR;

            float currentAvg = (c1 + c2 + c3 + c4) * 0.25f;
            float tempMax = Math.max(tL, tR);

            if (volt < 0 || volt > 30) fault = true;
            if (currentAvg < 0 || currentAvg > 50) fault = true;

            if (tempMax > 120) fault = true;
            else if (tempMax > 100) warning = true;

            if (!fault) {
                targetVolt = clamp(volt, 0, 30);
                targetCurrent = clamp(currentAvg, 0, 50);
                targetTemp = clamp(tempMax, 0, 120);
            }

            runOnUiThread(() -> {
                updateStatusUI();
                updateMotorText();
            });
        });

        startSmoothLoop();

        if (!selectedMAC.isEmpty()) {
            startBluetooth(selectedMAC);
        }
    }

    // 🔴 แสดง 4 มอเตอร์
    private void updateMotorText() {
        txtM1.setText(String.format("M1: %.2f A", m1));
        txtM2.setText(String.format("M2: %.2f A", m2));
        txtM3.setText(String.format("M3: %.2f A", m3));
        txtM4.setText(String.format("M4: %.2f A", m4));

        txtTempL.setText(String.format("L: %.0f °C", tempL));
        txtTempR.setText(String.format("R: %.0f °C", tempR));
    }

    private void updateGauge() {
        gaugeVolt.setValue(displayVolt);
        gaugeCurrent.setValue(displayCurrent);
        gaugeTemp.setValue(displayTemp);

        txtVolt.setText(String.format("%.2f V", displayVolt));
        txtCurrent.setText(String.format("%.2f A", displayCurrent));
        txtTemp.setText(String.format("%.0f °C", displayTemp));
    }

    private void startBluetooth(String mac) {
        selectedMAC = mac;
        getSharedPreferences("TN_MOWER", MODE_PRIVATE)
                .edit().putString("MAC", mac).apply();

        Intent intent = new Intent(this, BluetoothService.class);
        intent.putExtra("MAC", mac);
        startService(intent);

        updateStatusText("CONNECTING");
    }

    private void updateStatusText(String status) {
        runOnUiThread(() -> txtStatus.setText(status));
    }

    private void updateStatusUI() {
        if (fault) txtStatus.setTextColor(Color.RED);
        else if (warning) txtStatus.setTextColor(Color.YELLOW);
        else txtStatus.setTextColor(Color.GREEN);
    }

    private void startSmoothLoop() {
        if (isLoopRunning) return;
        isLoopRunning = true;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (!isLoopRunning) return;

                float alpha = 0.1f;

                displayVolt += (targetVolt - displayVolt) * alpha;
                displayCurrent += (targetCurrent - displayCurrent) * alpha;
                displayTemp += (targetTemp - displayTemp) * alpha;

                updateGauge();

                handler.postDelayed(this, 16);
            }
        }, 100);
    }

    private void stopSmoothLoop() {
        isLoopRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void sendStopCommand() {
        try {
            Intent intent = new Intent(this, BluetoothService.class);
            intent.putExtra("cmd", "STOP");
            startService(intent);
        } catch (Exception ignored) {}
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSmoothLoop();

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
        stopSmoothLoop();

        if (isReceiverRegistered) {
            unregisterReceiver(statusReceiver);
            isReceiverRegistered = false;
        }
    }
}
