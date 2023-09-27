package com.example.smartmedicalalert;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smartmedicalalert.recycleradapter.RecyclerAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, IDeviceConnect {
    public static final int PERMISSIONS_REQUEST_CODE = 2;
    private static final long SCAN_PERIOD = 10000;

    private static final String CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
    private static final String SUBSYSTEM_CHARACTERISTIC = "cba1d466-344c-4be3-ab3f-189f80dd7518";
    private static final String SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    private boolean scanning;
    private boolean isGattConnected = false;
    private Button scanBtn;
    private Button disconnectBtn;
    private EditText search;
    private TextView esp32Info;
    private TextView deviceName;

    private Button stopBackgroundScanBtn;
    private Intent startBackgroundScan = null;

    private Handler handler = new Handler();

    private boolean isDeviceConnected = false;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt = null;
    private BluetoothGattCharacteristic mCharacteristic;
    private BluetoothGattCharacteristic subsystemCharacteristic;
    private List<MyBluetoothDevice> scannedDevices = new ArrayList<MyBluetoothDevice>();
    private Map<String, List<MyBluetoothDevice>> deviceNameMapping = new HashMap<String, List<MyBluetoothDevice>>();

    private ProgressBar progressBar;
    private RecyclerView devicesList;
    private RecyclerAdapter adapter;


    private ActivityResultLauncher<Intent> enableBluetoothResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Toast.makeText(MainActivity.this, "Bluetooth enabled, you can now begin scanning for devices", Toast.LENGTH_LONG).show();
                    }
                }
            });

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (device != null && device.getName() != null &&
            !device.getName().isEmpty()) {
                List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
                if (!scannedDevices.contains(new MyBluetoothDevice(device, uuids))) {
                    if (device.getName().equals("ESP32")) {
                        scannedDevices.add(0, new MyBluetoothDevice(device, uuids));
                    } else {
                        scannedDevices.add(new MyBluetoothDevice(device, uuids));
                    }
                    if (deviceNameMapping.containsKey(device.getName())) {
                        deviceNameMapping.get(device.getName()).add(new MyBluetoothDevice(device, uuids));
                    } else {
                        List<MyBluetoothDevice> devicesList = new ArrayList<MyBluetoothDevice>();
                        devicesList.add(new MyBluetoothDevice(device, uuids));
                        deviceNameMapping.put(device.getName().toUpperCase(), devicesList);
                    }
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(MainActivity.this, "Scan failed with error code: " + errorCode, Toast.LENGTH_LONG).show();
        }
    };

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("DEVICE CONNECT", "Connection was successful");
                switch(newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        Log.i("DEVICE CONNECT", "Connected to device. Proceeding with service discovery");
                        isDeviceConnected = true;
                        MainActivity.this.gatt = gatt;
                        editor.putString("device_address", gatt.getDevice().getAddress());
                        editor.apply();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Successfully connected to device", Toast.LENGTH_LONG).show();
                                disconnectBtn.setVisibility(View.VISIBLE);
                                deviceName.setText("Currently connected to: " + MainActivity.this.gatt.getDevice().getName());
                                if (MainActivity.this.gatt.getDevice().getName().equals("ESP32")) {
                                    devicesList.setVisibility(View.INVISIBLE);
                                    search.setVisibility(View.INVISIBLE);
                                    esp32Info.setVisibility(View.VISIBLE);
                                }
                            }
                        });
                        try {
                            Thread.sleep(600);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        gatt.requestMtu(512);
                        serviceDiscovery();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        gatt.close();
                        Log.i("DEVICE DISCONNECT", "");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Successfully disconnected from device", Toast.LENGTH_LONG).show();
                                disconnectBtn.setVisibility(View.INVISIBLE);
                                deviceName.setText("Currently connected to: ");
                            }
                        });
                        gatt = null;
                        isGattConnected = false;
                        isDeviceConnected = false;
                        break;
                }
            } else {
                Log.i("CONNECTION STATUS", "Failed to change connection status");
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                isGattConnected = true;
                BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                if (service != null) {
                    Log.i("DEVICE SERVICE", "Found service with UUID: " + service.getUuid().toString());
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    for (BluetoothGattCharacteristic c : characteristics) {
                        Log.i("CHARACTERISTIC", c.getUuid().toString());
                    }
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                    if (characteristic != null) {
                        mCharacteristic = characteristic;
                        gatt.setCharacteristicNotification(mCharacteristic, true);
                        Log.i("DEVICE CHARACTERISTIC", "Found characteristic with UUID: " + characteristic.getUuid().toString());
                        characteristic.setValue("Test Value");
                        gatt.writeCharacteristic(characteristic);
                    }
                    BluetoothGattCharacteristic subsystem = service.getCharacteristic(UUID.fromString(SUBSYSTEM_CHARACTERISTIC));
                    if (subsystem != null) {
                        subsystemCharacteristic = subsystem;

                    }
                }
            } else {
                Log.i("SERVICE DISCOVERY", "Failed to discover any services");
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            ESPData espData = parseCharacteristicNotification(characteristic.getValue());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    esp32Info.setText("Room Status: " + espData.getRoomStatus()
                            + "\nMovement Status: " + espData.getMovementStatus()
                            + "\nMotion: " + espData.isMotionDetected()
                            + "\nProximity: " + espData.isProximityDetected()
                            + "\nLight: " + espData.isLightDetected()
                            + "\nUltrasonic Baseline: " + espData.getProximityBaseline() + " cm"
                            + "\nUltrasonic Distance " + espData.getDistance() + " cm"
                            + "\nLights On Baseline: " + espData.getLightBaseline() + " lux"
                            + "\nLights Off Baseline: " + espData.getLightBaselineOff() + " lux"
                            + "\nRoom Lighting Status: " + espData.getLightingStatus()
                            + "\nLight Intensity: " + espData.getLightIntensity() + " lux"
                            + "\nVibration baseline: " + espData.getVibrationBaseline() + " m/s^2"
                            + "\nVibration Intensity: " + espData.getVibrationIntensity() + " m/s^2"
                            + "\nTotal Active Sensors: " + espData.getActiveSensors()
                            + "\n Connected Devices: " + espData.getConnectedDevices()
                            + "\nLast detected: " + espData.getLastDetected() + " minutes ago");
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("CHARACTERISTIC WRITE", "Successfully wrote to characteristic: " + new String(characteristic.getValue(), StandardCharsets.UTF_8));
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "" + new String(value, StandardCharsets.UTF_8), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("MTU REQUEST", "SUCCESS");
                gatt.discoverServices();
            } else {
                Log.i("MTU REQUEST", "Failed to request MTU");
            }
        }

        @Override
        public void onServiceChanged(@NonNull BluetoothGatt gatt) {
            super.onServiceChanged(gatt);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode) {
            case PERMISSIONS_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this,
                            "Necessary permissions granted, you can now start the scan for Bluetooth devices",
                            Toast.LENGTH_LONG).show();
                }
        }
    }

    // Prompt the user to activate Bluetooth on their device, if it isn't
    private void checkBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothResultLauncher.launch(enableBtIntent);
    }

    private void onDisconnectBtnClick() {
        editor.remove("device_address");
        editor.apply();
        esp32Info.setVisibility(View.INVISIBLE);
        disconnectBluetooth();
    }

    // Adding listeners to various UI elements
    private void addEventListeners() {
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check status of required permissions
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                            PERMISSIONS_REQUEST_CODE);
                } else {
                    launchBackgroundScan();
                }
            }
        });

        disconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDisconnectBtnClick();
            }
        });

        Drawable clearSearchDrawable = search.getCompoundDrawables()[2];
        clearSearchDrawable.setAlpha(0);

        // Listener for when the user enters and searches for a specific device name
        search.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (!search.getText().toString().isEmpty()) {
                        clearSearchDrawable.setAlpha(255);
                        String searchedDevice = search.getText().toString().toUpperCase();
                        if (deviceNameMapping.containsKey(searchedDevice)) {
                            List<MyBluetoothDevice> devices = deviceNameMapping.get(searchedDevice);
                            adapter.updateDataset(devices);
                        } else {
                            List<MyBluetoothDevice> potentialMatches = new ArrayList<MyBluetoothDevice>();
                            for (Map.Entry<String, List<MyBluetoothDevice>> entry: deviceNameMapping.entrySet()) {
                                if (entry.getKey().contains(searchedDevice)) {
                                    List<MyBluetoothDevice> match = entry.getValue();
                                    potentialMatches = Stream.concat(potentialMatches.stream(), match.stream()).collect(Collectors.toList());
                                }
                            }
                            adapter.updateDataset(potentialMatches);
                        }
                    }
                    return true;
                }
                return false;
            }
        });

        deviceName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (isDeviceConnected) {
                    // TODO Make a PopupMenu appear after a long click on the TextView
                    // PopupMenu should allow the user to view characteristic info if it's connected to a device
                    PopupMenu popupMenu = new PopupMenu(MainActivity.this, deviceName);
                    popupMenu.getMenuInflater().inflate(R.menu.device_info_menu, popupMenu.getMenu());
                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            switch(menuItem.getItemId()) {
                                case R.id.viewDeviceInfo:
                                    esp32Info.setVisibility(View.VISIBLE);
                                    devicesList.setVisibility(View.INVISIBLE);
                                    search.setVisibility(View.INVISIBLE);
                            }
                            return true;
                        }
                    });
                    popupMenu.show();
                }
                return false;
            }
        });

        // Right drawable click listener
        search.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (event.getRawX() >= (search.getRight() - clearSearchDrawable.getBounds().width())) {
                        clearSearchDrawable.setAlpha(0);
                        search.setText("");
                        adapter.updateDataset(scannedDevices);
                        return true;
                    }
                }
                return false;
            }
        });

        // Button to stop the background service scan
        stopBackgroundScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Foreground service was never started to begin with
                if (startBackgroundScan == null) {
                    return;
                }
                Toast.makeText(MainActivity.this, "Stopping continuous scan", Toast.LENGTH_LONG).show();
                MainActivity.this.stopService(new Intent(MainActivity.this, BackgroundScan.class));
                startBackgroundScan = null;
            }
        });

    }

    private void launchBackgroundScan() {
        startBackgroundScan = new Intent(this, BackgroundScan.class);
        startBackgroundScan.putExtra("service_uuid", SERVICE_UUID);
        startBackgroundScan.putExtra("characteristic_uuid", CHARACTERISTIC_UUID);
        startService(startBackgroundScan);
    }

    private void beginScan() {
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (!scannedDevices.isEmpty()) {
            scannedDevices.clear();
        }
        if (!deviceNameMapping.isEmpty()) {
            deviceNameMapping.clear();
        }
        if (!scanning) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScan();
                }
            }, SCAN_PERIOD);
        }
        scanner.startScan(scanCallback);
        if (isDeviceConnected) {
            disconnectBtn.setVisibility(View.VISIBLE);
        } else {
            disconnectBtn.setVisibility(View.INVISIBLE);
        }
        esp32Info.setVisibility(View.INVISIBLE);
        scanBtn.setVisibility(View.INVISIBLE);
        devicesList.setVisibility(View.INVISIBLE);
        search.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void stopScan() {
        scanning = false;
        scanner.stopScan(scanCallback);
        if (!scannedDevices.isEmpty()) {
            adapter.updateDataset(scannedDevices);
        }
        devicesList.setVisibility(View.VISIBLE);
        search.setVisibility(View.VISIBLE);
        scanBtn.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.INVISIBLE);
        scanBtn.setText("Rescan");
    }

    private void disconnectBluetooth() {
        if (gatt != null) {
            gatt.disconnect();
        }
    }

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

    private ESPData parseCharacteristicNotification(byte[] value) {
        String stringValue = new String(value, StandardCharsets.UTF_8);
        Log.i("NOTIFIED", stringValue);
        ESPData espData = ESPData.getInstance();
        try {
            JSONObject data = new JSONObject(stringValue);

            espData.setMovementStatus(data.getString("movement_status"));
            espData.setLastDetected(data.getInt("lastDetected"));

            espData.setMotionDetected(data.getBoolean("motion"));
            espData.setProximityDetected(data.getBoolean("proximity"));
            espData.setLightDetected(data.getBoolean("light"));

            espData.setRoomStatus(data.getBoolean("occupied"));

            espData.setLightIntensity(BigDecimal.valueOf(Math.round(data.getDouble("lightIntensity") * 100.0) / 100.0).floatValue());
            espData.setDistance(data.getInt("distance"));
            espData.setVibrationIntensity(BigDecimal.valueOf(Math.round(data.getDouble("vibrationIntensity") * 100.0) / 100.0).floatValue());
            espData.setVibrationBaseline(BigDecimal.valueOf(Math.round(data.getDouble("vibrationBaseline") * 100.0) / 100.0).floatValue());
            espData.setLightBaseline(BigDecimal.valueOf(Math.round(data.getDouble("lightBaseline") * 100.0) / 100.0).floatValue());
            espData.setProximityBaseline(data.getInt("proximityBaseline"));
            espData.setLightBaselineOff(BigDecimal.valueOf(Math.round(data.getDouble("lightOffBaseline") * 100.0) / 100.0).floatValue());
            espData.setLightingStatus(data.getString("lightingStatus"));
            espData.setConnectedDevices(data.getInt("connected_devices"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return espData;
    }

    @Override
    public void onDeviceConnect(int index) {
        BluetoothDevice targetDevice = scannedDevices.get(index).getDevice();
        Log.i("DEVICE CONNECT", targetDevice.toString());
        BluetoothDevice currentDevice = isConnected();
        if (currentDevice != null && currentDevice.getName().equals(targetDevice.getName())) {
            Toast.makeText(this, "This device is already connected!", Toast.LENGTH_LONG).show();
        } else {
            targetDevice.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
        }
    }


    // Returns the address of the previous device if the user didn't disconnect before closing the app. Returns null otherwise
    private String previousConnection() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();
        String previousDeviceAddress = sharedPreferences.getString("device_address", null);
        return previousDeviceAddress;
    }


    // This is called at start up if the user closed the app before but never disconnected from a device
    private void reconnectToGatt(String previousDeviceAddress) {
        Toast.makeText(this, "Attempting reconnection with previously connected device...", Toast.LENGTH_LONG).show();
        BluetoothAdapter adapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        try {
            BluetoothDevice connectedDevice = adapter.getRemoteDevice(previousDeviceAddress);
            connectedDevice.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Failed to reconnect to previous device. You may be out of range", Toast.LENGTH_LONG).show();
        }
    }

    private BluetoothDevice isConnected() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        List<BluetoothDevice> connectedDevices = manager.getConnectedDevices(BluetoothProfile.GATT);
        if (connectedDevices != null && !connectedDevices.isEmpty()) {
            return connectedDevices.get(0);
        } else {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        scanBtn = findViewById(R.id.startScan);
        progressBar = findViewById(R.id.progressBar);
        search = findViewById(R.id.searchDevice);
        disconnectBtn = findViewById(R.id.disconnectBtn);
        esp32Info = findViewById(R.id.esp32_info);
        deviceName = findViewById(R.id.connectedDeviceName);
        stopBackgroundScanBtn = findViewById(R.id.stopBackgroundScan);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        editor = sharedPreferences.edit();

        search.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        disconnectBtn.setVisibility(View.INVISIBLE);
        esp32Info.setVisibility(View.GONE);

        devicesList = findViewById(R.id.devicesList);
        adapter = new RecyclerAdapter(scannedDevices);
        devicesList.setLayoutManager(new LinearLayoutManager(this));
        devicesList.setAdapter(adapter);
        devicesList.setVisibility(View.INVISIBLE);

        checkBluetooth();
        addEventListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        String previousDeviceAddress = previousConnection();
        if (previousDeviceAddress != null) {
            reconnectToGatt(previousDeviceAddress);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        disconnectBluetooth();
    }

}