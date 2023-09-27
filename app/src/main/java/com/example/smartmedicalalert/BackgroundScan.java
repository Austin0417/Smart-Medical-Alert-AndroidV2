package com.example.smartmedicalalert;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class BackgroundScan extends Service {
    private String SERVICE_UUID;
    private String CHARACTERISTIC_UUID;

    private String NOTIFICATION_ID = "BACKGROUND_BLE_SCAN";
    private String NOTIFICATION_NAME = "ESP32 Continuous Scan";

    private NotificationChannel channel;
    private Notification notification;

    private boolean isGattConnected = false;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt = null;
    private BluetoothGattCharacteristic mCharacteristic;


    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (device != null && device.getName() != null && device.getName().equals("ESP32")) {
                scanner.stopScan(scanCallback);
                gatt = device.connectGatt(getApplicationContext(), true, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(getApplicationContext(), "Scan failed with error code: " + errorCode, Toast.LENGTH_LONG).show();
        }
    };

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch(newState) {
                    // Called when connected to device
                    case BluetoothProfile.STATE_CONNECTED:
                        // Attempt service discovery
                        gatt.requestMtu(512);
                        serviceDiscovery();
                        break;

                    // Called when disconnecting from device successfully
                    case BluetoothProfile.STATE_DISCONNECTED:
                        isGattConnected = false;
                        gatt.close();
                        Toast.makeText(getApplicationContext(), "Terminating connection and resuming continuous scan...", Toast.LENGTH_LONG).show();
                        break;
                }
            } else {
                Toast.makeText(getApplicationContext(), "ESP32 was detected during scan, but an error was encountered while trying to connect", Toast.LENGTH_LONG).show();

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            switch(status) {
                case BluetoothGatt.GATT_SUCCESS:
                    isGattConnected = true;
                    BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                    if (service != null) {
                        mCharacteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                        if (mCharacteristic != null) {
                            // Read the characteristic value
                            gatt.readCharacteristic(mCharacteristic);
                        }
                    } else {
                        Log.i("SERVICE DISCOVERY", "Service is null");
                    }
                    break;
                case BluetoothGatt.GATT_FAILURE:
                    Log.i("ON SERVICES DISCOVERED", "Could not find any services associated with target device");
                    break;
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            switch(status) {
                case BluetoothGatt.GATT_SUCCESS:
                    Log.i("MTU REQUEST", "Success");
                    break;
                default:
                    Log.i("MTU REQUEST", "Failed");
                    break;
            }
        }

        @Override
        // After successfully reading the characteristic value, the client device should disconnect and resume the continuous scan after a brief period
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            switch(status) {
                case BluetoothGatt.GATT_SUCCESS:
                    String characteristicValue = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                    Log.i("CHARACTERISTIC READ", "Success. Value=" + characteristicValue);

                    try {
                        JSONObject json = new JSONObject(characteristicValue);
                        int lastDetected = json.getInt("lastDetected");     // do something with this value (display to the user)
                        gatt.disconnect();
                    } catch (JSONException e) {
                        e.printStackTrace();
                }
            }
        }
    };

    private void serviceDiscovery() {
        if (!isGattConnected) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (gatt != null) {
                        gatt.discoverServices();
                    }
                }
            });
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    serviceDiscovery();
                }
            }, 5000);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        channel = new NotificationChannel(NOTIFICATION_ID, NOTIFICATION_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        notification = new NotificationCompat.Builder(this, NOTIFICATION_ID)
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
        return null;
    }
}
