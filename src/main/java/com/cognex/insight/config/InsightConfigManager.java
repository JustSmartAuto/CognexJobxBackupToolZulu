package com.cognex.insight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * 应用程序配置管理器。
 * 负责从 JAR 同目录加载和保存 JSON 配置文件。
 */
public class InsightConfigManager {

    private static final String CONFIG_FILE_NAME = "config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static AppConfig config;
    private static File configFile;

    /**
     * 获取当前配置，如果未加载则先加载。
     */
    public static synchronized AppConfig getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }

    /**
     * 从 JAR 同目录加载配置文件。
     */
    public static synchronized void load() {
        File file = getConfigFile();
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, AppConfig.class);
            } catch (Exception e) {
                System.err.println("加载配置文件失败: " + e.getMessage());
                config = new AppConfig();
            }
        } else {
            config = new AppConfig();
        }
        if (config == null) {
            config = new AppConfig();
        }
    }

    /**
     * 保存当前配置到 JAR 同目录。
     */
    public static synchronized void save() {
        if (config == null) {
            config = new AppConfig();
        }
        File file = getConfigFile();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (Exception e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置文件路径（JAR 同目录）。
     */
    private static File getConfigFile() {
        if (configFile != null) {
            return configFile;
        }
        File jarDir = getJarDirectory();
        configFile = new File(jarDir, CONFIG_FILE_NAME);
        return configFile;
    }

    /**
     * 获取 JAR 所在目录，如果无法获取则返回当前工作目录。
     */
    private static File getJarDirectory() {
        try {
            String jarPath = InsightConfigManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            if (jarFile.isFile()) {
                return jarFile.getParentFile();
            }
        } catch (Exception ignored) {
        }
        return new File(System.getProperty("user.dir"));
    }
}
