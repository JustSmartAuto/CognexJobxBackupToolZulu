package com.cognex.insight.model;

import com.google.gson.JsonObject;

public class CameraInfo {
    public String hostName;
    public String ipAddress;
    public String modelNumber;
    public String macAddress;
    public String serialNumber;
    public String firmwareVersion;
    public String apiVersion;
    public String[] capabilities;
    public boolean usesXYCoordinates;

    public CameraInfo(JsonObject info) {
        if (info == null) return;
        this.hostName = getString(info, "name");
        this.ipAddress = getString(info, "ipAddress");
        this.modelNumber = getString(info, "model");
        this.macAddress = getString(info, "macID");
        this.serialNumber = getString(info, "serial");
        this.firmwareVersion = getString(info, "firmwareVersion");
        this.apiVersion = getString(info, "hmiProtocolVersion");
    }

    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }
}
