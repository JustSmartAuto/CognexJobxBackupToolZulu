package com.cognex.backup.ftp;

import com.cognex.backup.model.CameraConfig;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import javax.net.ssl.*;
import java.io.*;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FtpClient {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int DATA_TIMEOUT = 30000;

    public static class BackupResult {
        public final boolean success;
        public final String message;
        public final String backupPath;

        public BackupResult(boolean success, String message, String backupPath) {
            this.success = success;
            this.message = message;
            this.backupPath = backupPath;
        }
    }

    public BackupResult backupJobx(CameraConfig camera) {
        FTPClient ftp = createFtpClient(camera);
        ftp.setConnectTimeout(CONNECT_TIMEOUT);
        ftp.setDataTimeout(DATA_TIMEOUT);
        // Use UTF-8 so Chinese file names are not garbled
        ftp.setControlEncoding("UTF-8");
        ftp.setAutodetectUTF8(true);

        String backupDir = determineBackupDirectory(camera);
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String targetDir = backupDir + File.separator + camera.getName() + File.separator + timestamp;

        try {
            // Connect
            ftp.connect(camera.getIp(), camera.getFtpPort());
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                return new BackupResult(false, "FTP服务器拒绝连接 (code: " + reply + ")", null);
            }

            // If FTPS, perform TLS handshake
            if (ftp instanceof FTPSClient) {
                FTPSClient ftps = (FTPSClient) ftp;
                ftps.execPBSZ(0);
                ftps.execPROT("P");
            }

            // Login
            if (!ftp.login(camera.getFtpUsername(), camera.getFtpPassword())) {
                ftp.logout();
                ftp.disconnect();
                return new BackupResult(false, "FTP登录失败，请检查用户名和密码", null);
            }

            // Configure transfer
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.enterLocalPassiveMode();
            // Ask the server to use UTF-8 for file names (RFC 2640)
            try {
                if (FTPReply.isPositiveCompletion(ftp.sendCommand("OPTS UTF8", "ON"))) {
                    ftp.setControlEncoding("UTF-8");
                }
            } catch (IOException ignored) {
                // Server doesn't support OPTS UTF8; keep the configured encoding
            }

            // Create local backup directory
            File localDir = new File(targetDir);
            if (!localDir.exists() && !localDir.mkdirs()) {
                return new BackupResult(false, "无法创建本地备份目录: " + targetDir, null);
            }

            // Download jobx files recursively from root
            String remotePath = "/";
            int downloaded = downloadDirectory(ftp, remotePath, localDir);

            ftp.logout();

            if (downloaded == 0) {
                return new BackupResult(false, "未找到任何jobx文件", null);
            }

            return new BackupResult(true,
                    "备份成功，共下载 " + downloaded + " 个文件",
                    targetDir);

        } catch (IOException e) {
            String errorMsg = translateExceptionMessage(e.getMessage());
            return new BackupResult(false, errorMsg, null);
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 仅递归列举相机上的 .jobx 文件（不下载），供导出工具使用。
     * 返回相对相机根目录的远程路径列表；失败返回 null。
     */
    public java.util.List<String> listJobxFiles(CameraConfig camera) {
        FTPClient ftp = createFtpClient(camera);
        ftp.setConnectTimeout(CONNECT_TIMEOUT);
        ftp.setDataTimeout(DATA_TIMEOUT);
        ftp.setControlEncoding("UTF-8");
        ftp.setAutodetectUTF8(true);
        try {
            ftp.connect(camera.getIp(), camera.getFtpPort());
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                return null;
            }
            if (ftp instanceof FTPSClient) {
                FTPSClient ftps = (FTPSClient) ftp;
                ftps.execPBSZ(0);
                ftps.execPROT("P");
            }
            if (!ftp.login(camera.getFtpUsername(), camera.getFtpPassword())) {
                ftp.logout();
                ftp.disconnect();
                return null;
            }
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.enterLocalPassiveMode();
            try {
                if (FTPReply.isPositiveCompletion(ftp.sendCommand("OPTS UTF8", "ON"))) {
                    ftp.setControlEncoding("UTF-8");
                }
            } catch (IOException ignored) {
            }
            java.util.List<String> out = new java.util.ArrayList<>();
            collectJobx(ftp, "/", out);
            java.util.Collections.sort(out);
            ftp.logout();
            return out;
        } catch (IOException e) {
            return null;
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void collectJobx(FTPClient ftp, String remotePath, java.util.List<String> out) throws IOException {
        FTPFile[] files = ftp.listFiles(remotePath);
        if (files == null) {
            return;
        }
        for (FTPFile file : files) {
            if (file == null) continue;
            String name = file.getName();
            if (".".equals(name) || "..".equals(name)) continue;
            String childPath = remotePath.endsWith("/") ? remotePath + name : remotePath + "/" + name;
            if (file.isDirectory()) {
                // 某些目录无权限，跳过
                try {
                    collectJobx(ftp, childPath, out);
                } catch (IOException ignored) {
                }
            } else if (name.toLowerCase().endsWith(".jobx")) {
                out.add(childPath);
            }
        }
    }

    private FTPClient createFtpClient(CameraConfig camera) {
        if (!camera.isFtpsEnabled()) {
            return new FTPClient();
        }

        FTPSClient ftpsClient;
        if (camera.isTrustAllCerts()) {
            // Create a trust manager that trusts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };

            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                ftpsClient = new FTPSClient(sslContext);
                // Disable hostname verification
                ftpsClient.setHostnameVerifier((hostname, session) -> true);
                return ftpsClient;
            } catch (Exception e) {
                // Fallback to default FTPS client if custom SSLContext fails
                return new FTPSClient();
            }
        } else {
            return new FTPSClient();
        }
    }

    private String translateExceptionMessage(String original) {
        if (original == null) {
            return "备份失败: 未知网络错误";
        }
        String lower = original.toLowerCase();
        if (lower.contains("connection closed without indication")) {
            return "备份失败: 连接被服务器意外关闭。可能原因：\n" +
                   "1. 相机未启用 FTP/FTPS 服务\n" +
                   "2. 连接端口不正确（Cognex 默认 FTP:21, FTPS:990）\n" +
                   "3. 相机要求 FTPS 加密连接，请在相机配置中勾选\"使用 FTPS\"\n" +
                   "4. 网络或防火墙阻止了连接\n" +
                   "原始错误: " + original;
        }
        if (lower.contains("connection refused") || lower.contains("connection refused")) {
            return "备份失败: 连接被拒绝。请检查 IP 地址和端口是否正确，以及相机是否在线。\n原始错误: " + original;
        }
        if (lower.contains("connection timed out") || lower.contains("timeout")) {
            return "备份失败: 连接超时。请检查网络是否通畅，相机是否可达。\n原始错误: " + original;
        }
        if (lower.contains("unknown host") || lower.contains("unreachable")) {
            return "备份失败: 无法连接到相机主机。请检查 IP 地址是否正确。\n原始错误: " + original;
        }
        if (lower.contains("ssl") || lower.contains("tls") || lower.contains("handshake")) {
            return "备份失败: TLS/SSL 握手失败。请检查 FTPS 配置是否正确，或尝试勾选\"信任所有 TLS 证书\"。\n原始错误: " + original;
        }
        if (lower.contains("login") || lower.contains("authentication")) {
            return "备份失败: 认证失败。请检查用户名和密码是否正确。\n原始错误: " + original;
        }
        return "备份失败: " + original;
    }

    private String determineBackupDirectory(CameraConfig camera) {
        String dir = camera.getBackupDirectory();
        if (dir == null || dir.trim().isEmpty()) {
            try {
                String jarPath = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
                File jarFile = new File(jarPath);
                File jarDir = jarFile.getParentFile();
                if (jarDir != null && jarDir.exists()) {
                    dir = jarDir.getAbsolutePath();
                } else {
                    dir = new File(".").getAbsolutePath();
                }
            } catch (Exception e) {
                dir = new File(".").getAbsolutePath();
            }
        }
        return dir;
    }

    private int downloadDirectory(FTPClient ftp, String remotePath, File localDir) throws IOException {
        int count = 0;
        FTPFile[] files = ftp.listFiles(remotePath);

        if (files == null || files.length == 0) {
            return 0;
        }

        for (FTPFile file : files) {
            if (file == null) continue;
            String name = file.getName();
            if (".".equals(name) || "..".equals(name)) continue;

            String remoteFilePath = remotePath.endsWith("/")
                    ? remotePath + name
                    : remotePath + "/" + name;

            if (file.isDirectory()) {
                File subDir = new File(localDir, name);
                if (!subDir.exists() && !subDir.mkdirs()) {
                    continue;
                }
                count += downloadDirectory(ftp, remoteFilePath, subDir);
            } else {
                // Only download .jobx files and related metadata
                if (name.toLowerCase().endsWith(".jobx") || name.toLowerCase().endsWith(".jobx.sig")) {
                    File localFile = new File(localDir, name);
                    if (downloadFile(ftp, remoteFilePath, localFile)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean downloadFile(FTPClient ftp, String remotePath, File localFile) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(localFile))) {
            return ftp.retrieveFile(remotePath, out);
        } catch (IOException e) {
            return false;
        }
    }
}
