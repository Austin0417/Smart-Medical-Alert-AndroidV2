package com.example.smartmedicalalert;



import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;

import org.json.JSONObject;

public interface IBackgroundScan {
    public void onDeviceScan(ScanResult result);

    // Found ESP32
    public void onTargetDeviceFound(BluetoothDevice targetDevice);
}
