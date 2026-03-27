package com.tnmower.tnmower.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tnmower.tnmower.R;
import com.tnmower.tnmower.bluetooth.BluetoothService;

public class MainActivity extends AppCompatActivity {

    private TextView txtStatus, txtVolt, txtCurrent, txtTemp;

    // ==================================================
    // RECEIVER: TELEMETRY
    // ==================================================
    private final BroadcastReceiver telemetryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            float volt = intent.getFloatExtra("volt", 0);
            float current = intent.getFloatExtra("current", 0);
            float temp = intent.getFloatExtra("temp", 0);

            txtVolt.setText("Volt: " + volt);
            txtCurrent.setText("Current: " + current);
            txtTemp.setText("Temp: " + temp);
        }
    };

    // ==================================================
    // RECEIVER: STATUS
    // ==================================================
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String status = intent.getStringExtra("status");
            if (status == null) return;

            txtStatus.setText("Status: " + status);
        }
    };

    // ==================================================
    // onCreate
    // ==================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        txtVolt = findViewById(R.id.txtVolt);
        txtCurrent = findViewById(R.id.txtCurrent);
        txtTemp = findViewById(R.id.txtTemp);

        Button btnConnect = findViewById(R.id.btnConnect);
        Button btnStop = findViewById(R.id.btnStop);

        // ==================================================
        // PERMISSION Android 12+
        // ==================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, 1);
            }
        }

        // ==================================================
        // Android 13 Notification
        // ==================================================
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{
                        Manifest.permission.POST_NOTIFICATIONS
                }, 2);
            }
        }

        // ==================================================
        // START SERVICE (แก้ API 26)
        // ==================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, BluetoothService.class));
        } else {
            startService(new Intent(this, BluetoothService.class));
        }

        // ==================================================
        // BUTTON CONNECT (แก้ API 26)
        // ==================================================
        btnConnect.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, BluetoothService.class));
            } else {
                startService(new Intent(this, BluetoothService.class));
            }
        });

        // ==================================================
        // BUTTON STOP
        // ==================================================
        btnStop.setOnClickListener(v -> {
            Intent intent = new Intent(this, BluetoothService.class);
            intent.putExtra("cmd", "STOP");
            startService(intent);
        });
    }

    // ==================================================
    // REGISTER RECEIVER
    // ==================================================
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter telFilter = new IntentFilter("TNMOWER_TELEMETRY");
        IntentFilter statFilter = new IntentFilter("TNMOWER_STATUS");

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                    telemetryReceiver,
                    telFilter,
                    Context.RECEIVER_NOT_EXPORTED
            );

            registerReceiver(
                    statusReceiver,
                    statFilter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(telemetryReceiver, telFilter);
            registerReceiver(statusReceiver, statFilter);
        }
    }

    // ==================================================
    // UNREGISTER RECEIVER
    // ==================================================
    @Override
    protected void onPause() {
        super.onPause();

        try { unregisterReceiver(telemetryReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }
}

