package com.cognex.backup.model;

public class CameraConfig {
    private String name;
    private String ip;
    private int ftpPort;
    private String ftpUsername;
    private String ftpPassword;
    private String backupDirectory;
    private boolean ftpsEnabled;
    private boolean trustAllCerts;

    public CameraConfig() {
        this.ftpPort = 21;
        this.ftpUsername = "admin";
        this.ftpPassword = "";
        this.ftpsEnabled = false;
        this.trustAllCerts = true;
    }

    public CameraConfig(String name, String ip, int ftpPort, String ftpUsername, String ftpPassword, String backupDirectory) {
        this.name = name;
        this.ip = ip;
        this.ftpPort = ftpPort;
        this.ftpUsername = ftpUsername;
        this.ftpPassword = ftpPassword;
        this.backupDirectory = backupDirectory;
        this.ftpsEnabled = false;
        this.trustAllCerts = true;
    }

    public CameraConfig(String name, String ip, int ftpPort, String ftpUsername, String ftpPassword,
                        String backupDirectory, boolean ftpsEnabled, boolean trustAllCerts) {
        this.name = name;
        this.ip = ip;
        this.ftpPort = ftpPort;
        this.ftpUsername = ftpUsername;
        this.ftpPassword = ftpPassword;
        this.backupDirectory = backupDirectory;
        this.ftpsEnabled = ftpsEnabled;
        this.trustAllCerts = trustAllCerts;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public int getFtpPort() { return ftpPort; }
    public void setFtpPort(int ftpPort) { this.ftpPort = ftpPort; }

    public String getFtpUsername() { return ftpUsername; }
    public void setFtpUsername(String ftpUsername) { this.ftpUsername = ftpUsername; }

    public String getFtpPassword() { return ftpPassword; }
    public void setFtpPassword(String ftpPassword) { this.ftpPassword = ftpPassword; }

    public String getBackupDirectory() { return backupDirectory; }
    public void setBackupDirectory(String backupDirectory) { this.backupDirectory = backupDirectory; }

    public boolean isFtpsEnabled() { return ftpsEnabled; }
    public void setFtpsEnabled(boolean ftpsEnabled) { this.ftpsEnabled = ftpsEnabled; }

    public boolean isTrustAllCerts() { return trustAllCerts; }
    public void setTrustAllCerts(boolean trustAllCerts) { this.trustAllCerts = trustAllCerts; }

    @Override
    public String toString() {
        return name + " (" + ip + ")";
    }
}
