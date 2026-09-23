package com.cognex.export.model;

/** 导出工具批量导出用的相机配置（含 HMI WebSocket 端口）。 */
public class ExportCamera {
    private String name = "";
    private String ip = "";
    private int wsPort = 80;          // HMI WebSocket 端口（旧固件常为 8087）
    private int ftpPort = 21;
    private String user = "admin";
    private String password = "";
    private boolean ftps = true;
    private boolean trustAllCerts = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public int getWsPort() { return wsPort; }
    public void setWsPort(int wsPort) { this.wsPort = wsPort; }

    public int getFtpPort() { return ftpPort; }
    public void setFtpPort(int ftpPort) { this.ftpPort = ftpPort; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isFtps() { return ftps; }
    public void setFtps(boolean ftps) { this.ftps = ftps; }

    public boolean isTrustAllCerts() { return trustAllCerts; }
    public void setTrustAllCerts(boolean trustAllCerts) { this.trustAllCerts = trustAllCerts; }

    @Override
    public String toString() {
        return (name == null || name.isEmpty() ? ip : name) + " (" + ip + ":" + wsPort + ")";
    }
}
