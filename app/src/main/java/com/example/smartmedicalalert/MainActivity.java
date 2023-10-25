package com.example.smartmedicalalert;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
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

import com.example.smartmedicalalert.helpers.NotificationHelper;
import com.example.smartmedicalalert.interfaces.IBackgroundScan;
import com.example.smartmedicalalert.interfaces.IDeviceConnect;
import com.example.smartmedicalalert.interfaces.MenuItemListener;
import com.example.smartmedicalalert.recycleradapter.RecyclerAdapter;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, IDeviceConnect, IBackgroundScan {
    public static final int PERMISSIONS_REQUEST_CODE = 2;

    private static final String CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
    private static final String SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";

    public static String ALERT_NOTIFICATION_ID = "ALERT_NOTIFICATION";
    public static String ALERT_NOTIFICATION_NAME = "Emergency Alert";

    private boolean isGattConnected = false;
    private Button scanBtn;
    private Button disconnectBtn;
    private EditText search;
    private TextView deviceName;
    private DrawerLayout drawerLayout;
    private NavigationView drawerOptions;
    private MenuItemVisibilityHandler menuHandler;

    private Button stopBackgroundScanBtn;
    private Intent startBackgroundScan = null;

    private Handler handler = new Handler();

    private boolean isDeviceConnected = false;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt gatt = null;
    private BluetoothGattCharacteristic mCharacteristic;
    private List<MyBluetoothDevice> scannedDevices = new ArrayList<MyBluetoothDevice>();
    private Map<String, List<MyBluetoothDevice>> deviceNameMapping = new HashMap<String, List<MyBluetoothDevice>>();

    private ProgressBar progressBar;
    private RecyclerView devicesList;
    private RecyclerAdapter adapter;

    private BackgroundScan serviceInstance;


    private ActivityResultLauncher<Intent> enableBluetoothResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Toast.makeText(MainActivity.this, "Bluetooth enabled, you can now begin scanning for devices", Toast.LENGTH_LONG).show();
                    }
                }
            });


    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BackgroundScan.MyBinder binder = (BackgroundScan.MyBinder) service;
            serviceInstance = binder.getInstance();
            serviceInstance.registerClient(MainActivity.this);
            Log.i("SERVICE BIND", "Success");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i("SERVICE BIND", "Disconnected");
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
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Successfully connected to device", Toast.LENGTH_LONG).show();
                                disconnectBtn.setVisibility(View.VISIBLE);
                                deviceName.setText("Currently connected to: " + MainActivity.this.gatt.getDevice().getName());
                                if (MainActivity.this.gatt.getDevice().getName().equals("ESP32")) {
                                    devicesList.setVisibility(View.INVISIBLE);
                                    search.setVisibility(View.INVISIBLE);
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
                        gatt.readCharacteristic(mCharacteristic);
                    }
                }
            } else {
                Log.i("SERVICE DISCOVERY", "Failed to discover any services");
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("CHARACTERISTIC WRITE", "Successfully wrote to characteristic: " + new String(characteristic.getValue(), StandardCharsets.UTF_8));
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String characteristicValue = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                Log.i("CHARACTERISTIC READ", "Success. Value=" + characteristicValue);

                try {
                    JSONObject json = new JSONObject(characteristicValue);
                    int lastDetected = json.getInt("lastDetected");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Alert: No motion detected for " + lastDetected, Toast.LENGTH_LONG).show();
                        }
                    });
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.putExtra("prompt_rescan", true);
                    PendingIntent pIntent = PendingIntent.getActivity(getApplicationContext(), 5, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    showAlertNotification(MainActivity.this, lastDetected, pIntent);
                    gatt.disconnect();      // disconnect after sending the alert notification
                    stopBackgroundScan();     // restarts the background scan
                } catch (JSONException e) {
                    e.printStackTrace();
                }
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


    private void addDeviceToList(BluetoothDevice device, List<ParcelUuid> uuids) {
        if (device != null && device.getName() != null &&
                !device.getName().isEmpty()) {
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
                adapter.updateDataset(scannedDevices);
            }
        }
    }

    @Override
    public void onDeviceScan(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
        addDeviceToList(device, uuids);
    }

    @Override
    // This will be called when the ESP32 detects an emergency
    public void onTargetDeviceFound(BluetoothDevice device) {
        gatt = device.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

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
        disconnectBluetooth();
    }

    // Adding listeners to various UI elements
    private void addEventListeners() {
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                launchBackgroundScan();
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
                stopBackgroundScan();
            }
        });

    }

    private void launchBackgroundScan() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                    PERMISSIONS_REQUEST_CODE);
        } else {
            startBackgroundScan = new Intent(this, BackgroundScan.class);
            startBackgroundScan.putExtra("service_uuid", SERVICE_UUID);
            startBackgroundScan.putExtra("characteristic_uuid", CHARACTERISTIC_UUID);
            startService(startBackgroundScan);
            bindService(startBackgroundScan, mConnection, Context.BIND_AUTO_CREATE);
            devicesList.setVisibility(View.VISIBLE);
            stopBackgroundScanBtn.setVisibility(View.VISIBLE);
            drawerOptions.getMenu().getItem(1).setVisible(true);
        }
    }

    private void restartBackgroundService() {
        stopBackgroundScan();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                launchBackgroundScan();
            }
        }, 120000);

    }

    private void stopBackgroundScan() {
        // Foreground service was never started to begin with
        if (startBackgroundScan == null) {
            return;
        }
        Toast.makeText(MainActivity.this, "Stopping continuous scan", Toast.LENGTH_LONG).show();
        if (serviceInstance != null) {
            serviceInstance.stopServiceScan();
        }
        stopService(new Intent(MainActivity.this, BackgroundScan.class));
        unbindService(mConnection);
        startBackgroundScan = null;
        clearScanData();
    }

    private void promptRescan() {
        new AlertDialog.Builder(this)
                .setTitle("Continuous background scan")
                .setMessage("Would you like to re-enable bluetooth scanning in the background?")
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearScanData();
                        launchBackgroundScan();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .create().show();
    }

    private void disconnectBluetooth() {
        if (gatt != null) {
            gatt.disconnect();
        }
    }

    private void clearScanData() {
        scannedDevices.clear();
        deviceNameMapping.clear();
        adapter.updateDataset(scannedDevices);
        devicesList.setVisibility(View.INVISIBLE);
        stopBackgroundScanBtn.setVisibility(View.INVISIBLE);
    }

    private void serviceDiscovery() {
        if (!isGattConnected && isDeviceConnected) {
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

    public static void showAlertNotification(Context context, int lastDetected, PendingIntent pIntent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_NOTIFICATION_ID)
                .setSmallIcon(R.drawable.baseline_crisis_alert_24)
                .setContentTitle("Smart Medical Alert System")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("ALERT: No motion detected for " + lastDetected + " minutes. Consider checking up on the room"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentText("ALERT: No motion detected for " + lastDetected + " minutes. Consider checking up on the room")
                .setAutoCancel(true)
                .setVibrate(new long[]{500, 500, 500, 500})
                .setContentIntent(pIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(2, builder.build());
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


    private BluetoothDevice isConnected() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        List<BluetoothDevice> connectedDevices = manager.getConnectedDevices(BluetoothProfile.GATT);
        if (connectedDevices != null && !connectedDevices.isEmpty()) {
            return connectedDevices.get(0);
        } else {
            return null;
        }
    }

    private void setViewReferences() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        scanBtn = findViewById(R.id.startScan);
        progressBar = findViewById(R.id.progressBar);
        search = findViewById(R.id.searchDevice);
        drawerLayout = findViewById(R.id.drawerLayout);
        drawerOptions = findViewById(R.id.right_drawer);
        disconnectBtn = findViewById(R.id.disconnectBtn);
        deviceName = findViewById(R.id.connectedDeviceName);
        stopBackgroundScanBtn = findViewById(R.id.stopBackgroundScan);

        search.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        disconnectBtn.setVisibility(View.INVISIBLE);
        stopBackgroundScanBtn.setVisibility(View.INVISIBLE);

        devicesList = findViewById(R.id.devicesList);
        adapter = new RecyclerAdapter(scannedDevices);
        devicesList.setLayoutManager(new LinearLayoutManager(this));
        devicesList.setAdapter(adapter);
        devicesList.setVisibility(View.INVISIBLE);
    }

    private void initializeNavigationView() {
        drawerOptions.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.option_scan:
                        Log.i("NAVIGATION VIEW", "START SCAN");
                        launchBackgroundScan();
                        return true;
                    case R.id.option_disconnect:
                        Log.i("NAVIGATION VIEW", "DISCONNECT");
                        onDisconnectBtnClick();
                        return true;
                    case R.id.option_stop_background_scan:
                        Log.i("NAVIGATION VIEW", "STOP BACKGROUND SCAN");
                        stopBackgroundScan();
                        return true;
                    default:
                        return false;
                }
            }
        });

        // Initially set these menu options to false because the user hasn't connected to a device or started the background scan yet
        drawerOptions.getMenu().getItem(1).setVisible(false);
        MenuItem disconnectOption = drawerOptions.getMenu().getItem(2).setVisible(false);
        menuHandler = new MenuItemVisibilityHandler(disconnectOption);
        menuHandler.setMenuItemVisibilityListener(new MenuItemListener() {
            @Override
            public void onDeviceConnectionChanged(boolean connected) {
                disconnectOption.setVisible(connected);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_with_drawer);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        if (getIntent() != null && getIntent().getBooleanExtra("prompt_rescan", false)) {
            promptRescan();
        }
        NotificationHelper.createNotificationChannel(this, ALERT_NOTIFICATION_ID, ALERT_NOTIFICATION_NAME);

        setViewReferences();
        initializeNavigationView();
        checkBluetooth();
        addEventListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
        disconnectBluetooth();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceInstance != null) {
            unbindService(mConnection);
        }
    }

}