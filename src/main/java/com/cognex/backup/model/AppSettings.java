package com.cognex.backup.model;

import java.util.ArrayList;
import java.util.List;

public class AppSettings {
    private List<CameraConfig> cameras = new ArrayList<>();
    private String defaultBackupDirectory = "";

    public List<CameraConfig> getCameras() {
        return cameras;
    }

    public void setCameras(List<CameraConfig> cameras) {
        this.cameras = cameras;
    }

    public String getDefaultBackupDirectory() {
        return defaultBackupDirectory;
    }

    public void setDefaultBackupDirectory(String defaultBackupDirectory) {
        this.defaultBackupDirectory = defaultBackupDirectory;
    }
}
