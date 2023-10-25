package com.example.smartmedicalalert;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.smartmedicalalert.helpers.NotificationHelper;
import com.example.smartmedicalalert.interfaces.IBackgroundScan;
import com.example.smartmedicalalert.interfaces.IRestartScan;

@SuppressLint("MissingPermission")
public class BackgroundScan extends Service implements IRestartScan {
    private String SERVICE_UUID;
    private String CHARACTERISTIC_UUID;

    public static String SERVICE_NOTIFICATION_ID = "BACKGROUND_BLE_SCAN";
    public static String SERVICE_NOTIFICATION_NAME = "ESP32 Continuous Scan";

    private Notification notification;

    private boolean isGattConnected = false;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt = null;
    private BluetoothGattCharacteristic mCharacteristic;

    private final IBinder mBinder = new MyBinder();
    private IBackgroundScan callback;


    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            callback.onDeviceScan(result);
            if (device != null && device.getName() != null && device.getName().equals("ESP32")) {
                scanner.stopScan(scanCallback);
                callback.onTargetDeviceFound(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(getApplicationContext(), "Scan failed with error code: " + errorCode, Toast.LENGTH_LONG).show();
        }
    };

    @Override
    public void restartScan() {
//        Toast.makeText(getApplicationContext(), "Resuming background scan in 2 minutes...", Toast.LENGTH_LONG).show();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanner.startScan(scanCallback);
            }
        }, 120000);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createNotificationChannel(getApplicationContext(), SERVICE_NOTIFICATION_ID, SERVICE_NOTIFICATION_NAME);
        notification = new NotificationCompat.Builder(this, SERVICE_NOTIFICATION_ID)
                .setContentTitle("Smart Medical Alert")
                .setContentText("Ongoing BLE Scan")
                .setSmallIcon(R.drawable.baseline_bluetooth_searching_24)
                .build();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private void beginScan() {
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        Toast.makeText(getApplicationContext(), "Beginning continuous scan...", Toast.LENGTH_LONG).show();
        scanner.startScan(scanCallback);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(getApplicationContext(), "Launching background Bluetooth scan...", Toast.LENGTH_LONG).show();
        SERVICE_UUID = intent.getStringExtra("service_uuid");
        CHARACTERISTIC_UUID = intent.getStringExtra("characteristic_uuid");
        if (notification != null) {
            startForeground(1, notification);
            // Start the continuous scan here
            beginScan();

        } else {
            Log.i("FOREGROUND SERVICE", "Error initializing notification object");
        }
        return START_STICKY;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        super.onUnbind(intent);
        stopForeground(STOP_FOREGROUND_REMOVE);
        Log.i("UNBIND SERVICE", "Success");
        return true;
    }

    public class MyBinder extends Binder {
        public BackgroundScan getInstance() {
            return BackgroundScan.this;
        }
    }

    public void registerClient(Activity activity) {
        if (activity instanceof MainActivity) {
            callback = (IBackgroundScan) activity;
        }
    }

    public void stopServiceScan() {
        scanner.stopScan(scanCallback);
    }
}
