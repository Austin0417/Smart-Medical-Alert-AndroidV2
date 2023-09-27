package com.example.smartmedicalalert;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;

import java.util.List;

public class MyBluetoothDevice {
    private BluetoothDevice device;
    private List<ParcelUuid> uuids;

    public MyBluetoothDevice(BluetoothDevice device, List<ParcelUuid> uuids) {
        this.device = device;
        this.uuids = uuids;
    }

    public BluetoothDevice getDevice() { return device; }
    public List<ParcelUuid> getUuids() { return uuids; }

    public void setDevice(BluetoothDevice device) { this.device = device; }
    public void setUuids(List<ParcelUuid> uuids) { this.uuids = uuids; }

    @Override
    public boolean equals(Object o) {
        MyBluetoothDevice otherDevice = (MyBluetoothDevice) o;
        return device.equals(otherDevice.getDevice()) && (getUuids() == otherDevice.getUuids() || getUuids().equals(otherDevice.getUuids()));
    }
}
