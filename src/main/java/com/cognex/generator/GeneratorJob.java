package com.cognex.generator;

import com.google.gson.JsonObject;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成器作业 POJO，对应 Job.json。
 * 顶层固定 5 个键：AcqSettings, JobSettings, JobVersion, Metadata, Sheets.
 * Sheets 写入端恒为 inline Byte[] 形式（§6.6 + §7.1 v1.3 源码确认）。
 */
public class GeneratorJob {
    public final Map<String, GeneratorSheet> sheets = new LinkedHashMap<>();
    public String jobVersion = "22.2";
    public String jobType = "Spreadsheet";
    public String cameraType = "";
    public String firmwareVersion = "";

    /** 序列化为 Job.json 明文 UTF-8 字节 / Serialize to plain Job.json UTF-8 bytes. */
    public byte[] toJobJsonBytes() throws UnsupportedEncodingException {
        return toJobJson().toString().getBytes("UTF-8");
    }

    public JsonObject toJobJson() {
        JsonObject root = new JsonObject();

        // AcqSettings / JobSettings：默认空对象（相机读取端会填默认值）
        JsonObject acq = new JsonObject();
        acq.addProperty("$type", "settings");
        root.add("AcqSettings", acq);

        JsonObject jobSettings = new JsonObject();
        jobSettings.addProperty("$type", "LigerJobSettings");
        root.add("JobSettings", jobSettings);

        root.addProperty("JobVersion", jobVersion);

        JsonObject meta = new JsonObject();
        meta.addProperty("JobType", jobType);
        if (cameraType != null && !cameraType.isEmpty()) {
            meta.addProperty("CameraType", cameraType);
        }
        if (firmwareVersion != null && !firmwareVersion.isEmpty()) {
            meta.addProperty("FirmwareVersion", firmwareVersion);
        }
        root.add("Metadata", meta);

        JsonObject sheets = new JsonObject();
        for (Map.Entry<String, GeneratorSheet> e : this.sheets.entrySet()) {
            try {
                byte[] sheetBytes = e.getValue().toSheetBytes();
                JsonObject ref = new JsonObject();
                ref.addProperty("$type", "Byte[]");
                ref.addProperty("sz", sheetBytes.length);
                ref.addProperty("base64", Base64.getEncoder().encodeToString(sheetBytes));
                sheets.add(e.getKey(), ref);
            } catch (UnsupportedEncodingException ex) {
                throw new RuntimeException(ex);
            }
        }
        root.add("Sheets", sheets);

        return root;
    }
}
