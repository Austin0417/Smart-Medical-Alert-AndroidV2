package com.example.smartmedicalalert;

public class ESPData {

    private static ESPData espData = new ESPData();
    private int lastDetected;
    private String movementStatus;

    private boolean motionDetected;
    private boolean proximityDetected;
    private boolean lightDetected;
    private boolean roomStatus;

    private float lightIntensity;
    private int distance;
    private float vibrationIntensity;
    private float vibrationBaseline;
    private float lightBaseline;
    private int proximityBaseline;
    private float lightBaselineOff;
    private String lightingStatus;

    private int connectedDevices;

    private ESPData() {}

    public static ESPData getInstance() { return espData;}
    public void setLastDetected(int lastDetected) { this.lastDetected = lastDetected; }
    public void setMovementStatus(String movementStatus) { this.movementStatus = movementStatus; }
    public void setMotionDetected(boolean motionDetected) { this.motionDetected = motionDetected; }
    public void setProximityDetected(boolean proximityDetected) { this.proximityDetected = proximityDetected; }
    public void setLightDetected(boolean lightDetected) { this.lightDetected = lightDetected; }
    public void setRoomStatus(boolean roomStatus) { this.roomStatus = roomStatus; }

    public void setLightIntensity(float lightIntensity) { this.lightIntensity = lightIntensity; }
    public void setDistance(int distance) { this.distance = distance; }
    public void setVibrationIntensity(float vibrationIntensity) { this.vibrationIntensity = vibrationIntensity; }
    public void setVibrationBaseline(float vibrationBaseline) { this.vibrationBaseline = vibrationBaseline; }
    public void setLightBaseline(float lightBaseline) { this.lightBaseline = lightBaseline; }
    public void setProximityBaseline(int proximityBaseline) { this.proximityBaseline = proximityBaseline; }
    public void setLightBaselineOff(float lightBaselineOff) { this.lightBaselineOff = lightBaselineOff; }
    public void setLightingStatus(String lightingStatus) { this.lightingStatus = lightingStatus; }

    public void setConnectedDevices(int connectedDevices) { this.connectedDevices = connectedDevices; }
    public int getLastDetected() { return lastDetected; }
    public String getMovementStatus() { return movementStatus; }
    public boolean isMotionDetected() { return motionDetected; }
    public boolean isProximityDetected() { return proximityDetected; }
    public boolean isLightDetected() { return lightDetected; }
    public boolean isRoomOccupied() { return roomStatus; }
    public float getLightIntensity() { return lightIntensity; }
    public int getDistance() { return distance; }
    public float getVibrationIntensity() { return vibrationIntensity; }
    public float getVibrationBaseline() { return vibrationBaseline; }
    public float getLightBaseline() { return lightBaseline; }
    public int getProximityBaseline() { return proximityBaseline; }
    public float getLightBaselineOff() { return lightBaselineOff; }
    public String getLightingStatus() { return lightingStatus; }

    public int getConnectedDevices() { return connectedDevices; }

    public int getActiveSensors() {
        int res = 0;
        if (motionDetected) {
            res++;
        }
        if (proximityDetected) {
            res++;
        }
        if (lightDetected) {
            res++;
        }
        return res;
    }
    public String getRoomStatus() {
        String res = (roomStatus) ? ("Occupied") : ("Unoccupied");
        return res;
    }
}
