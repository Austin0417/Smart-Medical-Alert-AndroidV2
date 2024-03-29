package com.example.smartmedicalalert;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;

import java.util.List;

@SuppressLint("MissingPermission")
public class MyBluetoothDevice {
    private BluetoothDevice device = null;
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
        if (getUuids() == null || otherDevice.getUuids() == null) {
            return device.equals(otherDevice.getDevice());
        }
        return device.equals(otherDevice.getDevice()) && getUuids() != null && otherDevice.getUuids() != null && getUuids().equals(otherDevice.getUuids());
    }
}
