package com.cognex.export;

import com.cognex.backup.ftp.FtpClient;
import com.cognex.backup.model.CameraConfig;
import com.cognex.insight.cogsocket.InSightConnection;
import com.cognex.insight.model.CellResult;
import com.cognex.insight.model.HmiSessionInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 导出任务：对应 Go 版导出工具的完整流程。
 * 1. FTPS 递归枚举相机 .jobx；2. CogSocket 连接 + HMI 登录；
 * 3. 记录当前作业与 softOnline（在线则切离线）；4. 逐个 loadJob、
 * 读取单元格值与表达式、生成 xlsx；5. 恢复原始作业与在线状态。
 * 全部工作在后台线程执行，通过 log 回调输出进度。
 */
public class ExportTask {

    /** 导出参数 */
    public static class Params {
        public String ip;
        public int wsPort = 80;          // HMI WebSocket 端口（旧固件常为 8087）
        public String user = "admin";
        public String password = "";
        public int ftpPort = 21;
        public boolean ftps = true;
        public boolean trustAllCerts = true;
        public String outRoot;           // 输出根目录（null -> jar 目录/exports）
        public boolean noExpr = false;   // 不获取表达式（加快速度）
    }

    private final Params params;
    private final Consumer<String> log;

    public ExportTask(Params params, Consumer<String> log) {
        this.params = params;
        this.log = log;
    }

    /** 执行导出，返回错误信息；成功返回 null。 */
    public String run() {
        InSightConnection connection = new InSightConnection();
        try {
            // 1. 枚举 .jobx
            log.accept("通过 " + (params.ftps ? "FTPS" : "FTP") + " 枚举相机 " + params.ip + " 上的 .jobx 文件...");
            CameraConfig cam = new CameraConfig();
            cam.setIp(params.ip);
            cam.setFtpPort(params.ftpPort);
            cam.setFtpUsername(params.user);
            cam.setFtpPassword(params.password);
            cam.setFtpsEnabled(params.ftps);
            cam.setTrustAllCerts(params.trustAllCerts);

            List<String> jobxFiles = new FtpClient().listJobxFiles(cam);
            if (jobxFiles == null) {
                return "FTP 连接或登录失败，无法枚举 .jobx 文件";
            }
            if (jobxFiles.isEmpty()) {
                return "相机上未找到任何 .jobx 文件";
            }
            log.accept("找到 " + jobxFiles.size() + " 个 jobx 文件");
            List<RemoteJobx> jobxList = new ArrayList<>();
            for (String p : jobxFiles) {
                String name = p.substring(p.lastIndexOf('/') + 1);
                jobxList.add(new RemoteJobx(p, name));
                log.accept("  - " + p);
            }

            // 2. CogSocket 连接 + HMI 登录
            log.accept("连接 CogSocket: ws://" + params.ip + ":" + params.wsPort + "/ws");
            HmiSessionInfo sessionInfo = new HmiSessionInfo(Collections.singletonList("A0:Z599"));
            sessionInfo.ignoreEditorAttached = true;
            sessionInfo.enableQueuedResults = true;
            sessionInfo.includeCustomView = false;
            await(connection.connect(params.ip + ":" + params.wsPort, params.user, params.password, sessionInfo));
            log.accept("HMI 登录成功");

            // 3. 记录当前作业与在线状态
            String origJob = connection.getJobName();
            if (origJob != null && !origJob.isEmpty()) {
                log.accept("相机当前作业: " + origJob);
            }
            boolean wasSoftOnline = connection.isSoftOnline();
            if (wasSoftOnline) {
                log.accept("相机当前在线，切换为离线以允许加载作业...");
                try {
                    await(connection.setSoftOnline(false));
                } catch (Exception e) {
                    log.accept("切换离线失败（继续尝试）: " + e.getMessage());
                }
                sleep(500);
            }

            // 4. 输出目录 {out}/{yyyyMMddHHmmss}
            Date now = new Date();
            File outRoot = params.outRoot != null && !params.outRoot.trim().isEmpty()
                    ? new File(params.outRoot.trim())
                    : new File(jarDirectory(), "exports");
            File outDir = new File(outRoot, new SimpleDateFormat("yyyyMMddHHmmss").format(now));
            if (!outDir.exists() && !outDir.mkdirs()) {
                return "创建输出目录失败: " + outDir.getAbsolutePath();
            }

            int failed = 0;
            for (int i = 0; i < jobxList.size(); i++) {
                RemoteJobx jf = jobxList.get(i);
                log.accept("处理 [" + (i + 1) + "/" + jobxList.size() + "] " + jf.name + " ...");
                try {
                    exportOneJobx(connection, jf, outDir, now);
                } catch (Exception e) {
                    log.accept("导出 " + jf.name + " 失败: " + e.getMessage());
                    failed++;
                }
            }

            // 5. 恢复原始作业与在线状态
            if (origJob != null && !origJob.isEmpty()) {
                String origName = origJob.startsWith("/") ? origJob.substring(1) : origJob;
                log.accept("恢复相机原始作业: " + origName);
                try {
                    await(connection.loadJob(origName));
                    waitJobLoaded(connection, 60000);
                } catch (Exception e) {
                    log.accept("恢复原始作业失败: " + e.getMessage());
                }
            }
            if (wasSoftOnline) {
                log.accept("恢复相机在线状态");
                try {
                    await(connection.setSoftOnline(true));
                } catch (Exception e) {
                    log.accept("恢复在线状态失败: " + e.getMessage());
                }
            }

            if (failed > 0) {
                return "完成，但 " + failed + "/" + jobxList.size() + " 个 jobx 导出失败。输出目录: " + outDir.getAbsolutePath();
            }
            log.accept("全部完成，共导出 " + jobxList.size() + " 个 xlsx，输出目录: " + outDir.getAbsolutePath());
            return null;
        } catch (Exception e) {
            return "导出失败: " + (e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            try {
                connection.disconnect().get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    /** 加载一个 jobx，读取单元格值+表达式，生成 xlsx。 */
    private void exportOneJobx(InSightConnection connection, RemoteJobx jf, File outDir, Date startTime) throws Exception {
        // loadJob
        await(connection.loadJob(jf.name));
        waitJobLoaded(connection, 90000);

        // getLatestResult -> cells
        JsonObject result = await(connection.getLatestResult());
        List<CellResult> cells = parseCells(result);
        sortCells(cells);
        if (cells.isEmpty()) {
            log.accept("  " + jf.name + " 无单元格结果");
        }
        log.accept("  " + jf.name + " 共 " + cells.size() + " 个单元格");

        // 逐个获取表达式（单个失败不中断）
        Map<String, String> expressions = new HashMap<>();
        if (!params.noExpr) {
            for (int k = 0; k < cells.size(); k++) {
                CellResult c = cells.get(k);
                if (c.location == null || c.location.isEmpty()) continue;
                try {
                    String expr = await(connection.getCellExpression(c.location));
                    if (expr != null && !expr.isEmpty()) {
                        expressions.put(c.location, expr);
                    }
                } catch (Exception ignored) {
                    // 单个失败不中断
                }
                if ((k + 1) % 50 == 0) {
                    log.accept("  表达式进度: " + (k + 1) + "/" + cells.size());
                    sleep(50);
                }
            }
            log.accept("  获取到 " + expressions.size() + " 个表达式");
        }

        String outPath = XlsxExporter.exportCells(outDir, params.ip, jf.name, cells, expressions, new Date());
        log.accept("  已保存: " + outPath);
        // 保持与枚举时间戳一致：文件名时间戳使用导出时刻由 XlsxExporter 生成即可
    }

    private List<CellResult> parseCells(JsonObject result) {
        List<CellResult> cells = new ArrayList<>();
        if (result == null || !result.has("cells") || !result.get("cells").isJsonArray()) {
            return cells;
        }
        JsonArray arr = result.getAsJsonArray("cells");
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            JsonObject obj = e.getAsJsonObject();
            CellResult cr = new CellResult();
            cr.type = obj.has("$type") ? obj.get("$type").getAsString() : "";
            cr.location = obj.has("location") ? obj.get("location").getAsString() : "";
            cr.name = obj.has("name") ? obj.get("name").getAsString() : "";
            cr.error = obj.has("error") && obj.get("error").getAsBoolean();
            if (obj.has("data") && !obj.get("data").isJsonNull()) {
                JsonElement data = obj.get("data");
                cr.data = data.isJsonPrimitive() ? data.getAsString() : data.toString();
            } else {
                cr.data = "";
            }
            if (obj.has("expression") && !obj.get("expression").isJsonNull()) {
                cr.expression = obj.get("expression").getAsString();
            }
            cells.add(cr);
        }
        return cells;
    }

    /** 轮询 jobLoading 直到为 false（~300ms 间隔）。 */
    private void waitJobLoaded(InSightConnection connection, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            if (!connection.isJobLoading()) return;
            if (System.currentTimeMillis() > deadline) {
                throw new Exception("等待作业加载超时");
            }
            sleep(300);
        }
    }

    /** 按电子表格位置排序（先列后行，与 Cognex 一致）。 */
    static void sortCells(List<CellResult> cells) {
        cells.sort(new Comparator<CellResult>() {
            @Override
            public int compare(CellResult a, CellResult b) {
                int[] pa = XlsxExporter.parseLocation(a.location);
                int[] pb = XlsxExporter.parseLocation(b.location);
                if (pa == null && pb == null) return 0;
                if (pa == null) return 1;
                if (pb == null) return -1;
                if (pa[0] != pb[0]) return Integer.compare(pa[0], pb[0]);
                return Integer.compare(pa[1], pb[1]);
            }
        });
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(120, TimeUnit.SECONDS);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static File jarDirectory() {
        try {
            String jarPath = ExportTask.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            if (jarFile.isFile()) {
                return jarFile.getParentFile();
            }
        } catch (Exception ignored) {
        }
        return new File(System.getProperty("user.dir"));
    }

    private static class RemoteJobx {
        final String remotePath;
        final String name;
        RemoteJobx(String remotePath, String name) {
            this.remotePath = remotePath;
            this.name = name;
        }
    }
}
