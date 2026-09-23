package com.cognex.export.config;

import com.cognex.export.model.ExportCamera;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 导出工具的相机列表配置（export-config.json，写入 jar 所在目录）。 */
public class ExportConfigManager {
    private static final String CONFIG_FILE_NAME = "export-config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;
    private final List<ExportCamera> cameras = new ArrayList<>();

    public ExportConfigManager() {
        this.configFile = getConfigFile();
        load();
    }

    private File getConfigFile() {
        try {
            String jarPath = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            File jarDir = jarFile.getParentFile();
            if (jarDir == null || !jarDir.exists()) {
                jarDir = new File(".");
            }
            return new File(jarDir, CONFIG_FILE_NAME);
        } catch (Exception e) {
            return new File(CONFIG_FILE_NAME);
        }
    }

    public void load() {
        cameras.clear();
        if (!configFile.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            CameraList list = GSON.fromJson(reader, CameraList.class);
            if (list != null && list.cameras != null) {
                cameras.addAll(list.cameras);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "加载导出配置失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            CameraList list = new CameraList();
            list.cameras = cameras;
            GSON.toJson(list, writer);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "保存导出配置失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public List<ExportCamera> getCameras() {
        return cameras;
    }

    private static class CameraList {
        List<ExportCamera> cameras;
    }
}
