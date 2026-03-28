package com.tnmower.tnmower.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Set;

public class DeviceListActivity extends AppCompatActivity {

    private BluetoothAdapter btAdapter;

    private boolean itemSelected = false; // 🔴 กันกดซ้ำ

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ===============================
        // ROOT
        // ===============================
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(20, 20, 20, 20);

        // ===============================
        // TITLE
        // ===============================
        TextView title = new TextView(this);
        title.setText("เลือกอุปกรณ์ Bluetooth");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18f);
        title.setPadding(0, 0, 0, 20);

        // ===============================
        // LIST
        // ===============================
        ListView listView = new ListView(this);

        root.addView(title);
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);

        btAdapter = BluetoothAdapter.getDefaultAdapter();

        // ===============================
        // CHECK BT SUPPORT
        // ===============================
        if (btAdapter == null) {
            Toast.makeText(this, "อุปกรณ์ไม่รองรับ Bluetooth", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // ===============================
        // CHECK PERMISSION (Android 12+)
        // ===============================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        1
                );

                Toast.makeText(this, "ต้องอนุญาต Bluetooth ก่อน", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // ===============================
        // ENABLE BT
        // ===============================
        if (!btAdapter.isEnabled()) {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }

        // ===============================
        // LOAD DEVICES
        // ===============================
        Set<BluetoothDevice> paired = btAdapter.getBondedDevices();

        ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
        ArrayList<String> displayList = new ArrayList<>();

        if (paired.isEmpty()) {
            displayList.add("ไม่พบอุปกรณ์ที่จับคู่ไว้");
        } else {
            for (BluetoothDevice d : paired) {

                String name = d.getName();
                if (name == null || name.isEmpty()) {
                    name = "ไม่ทราบชื่อ";
                }

                deviceList.add(d);
                displayList.add(name + "\n" + d.getAddress());
            }
        }

        // ===============================
        // ADAPTER UI
        // ===============================
        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayList) {

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {

                        TextView tv = (TextView) super.getView(position, convertView, parent);

                        tv.setTextColor(Color.WHITE);
                        tv.setTextSize(14f);
                        tv.setPadding(24, 24, 24, 24);

                        tv.setBackgroundColor(Color.parseColor("#1E1E1E"));

                        return tv;
                    }
                };

        listView.setAdapter(adapter);

        // ===============================
        // CLICK
        // ===============================
        listView.setOnItemClickListener((parent, view, position, id) -> {

            if (deviceList.isEmpty()) return;

            if (itemSelected) return; // 🔴 กันกดซ้ำ
            itemSelected = true;

            BluetoothDevice device = deviceList.get(position);

            String mac = device.getAddress();

            Toast.makeText(this, "เลือก: " + mac, Toast.LENGTH_SHORT).show();

            Intent result = new Intent();
            result.putExtra("MAC", mac);

            setResult(RESULT_OK, result);

            finish();
        });
    }
}

