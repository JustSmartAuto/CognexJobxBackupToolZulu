package com.cognex.backup.config;

import com.cognex.backup.model.AppSettings;
import com.cognex.backup.model.CameraConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final String CONFIG_FILE_NAME = "backup-config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;
    private AppSettings settings;

    public ConfigManager() {
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
        if (!configFile.exists()) {
            settings = new AppSettings();
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            settings = GSON.fromJson(reader, AppSettings.class);
            if (settings == null) {
                settings = new AppSettings();
            }
            if (settings.getCameras() == null) {
                settings.setCameras(new ArrayList<>());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "加载配置文件失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            settings = new AppSettings();
        }
    }

    public void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(settings, writer);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "保存配置文件失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public List<CameraConfig> getCameras() {
        return settings.getCameras();
    }

    public void addCamera(CameraConfig camera) {
        settings.getCameras().add(camera);
        save();
    }

    public void updateCamera(int index, CameraConfig camera) {
        settings.getCameras().set(index, camera);
        save();
    }

    public void removeCamera(int index) {
        settings.getCameras().remove(index);
        save();
    }

    public String getDefaultBackupDirectory() {
        return settings.getDefaultBackupDirectory();
    }

    public void setDefaultBackupDirectory(String directory) {
        settings.setDefaultBackupDirectory(directory);
        save();
    }

    public File getConfigFilePath() {
        return configFile;
    }
}
