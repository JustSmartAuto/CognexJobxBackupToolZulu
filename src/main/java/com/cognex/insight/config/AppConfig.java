package com.cognex.insight.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 应用程序配置数据模型。
 * 包含视图配置、连接配置、脚本配置和主题设置。
 */
public class AppConfig {

    private String theme = "light";
    private ViewConfig viewConfig = new ViewConfig();
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private ScriptConfig scriptConfig = new ScriptConfig();

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public ViewConfig getViewConfig() {
        return viewConfig;
    }

    public void setViewConfig(ViewConfig viewConfig) {
        this.viewConfig = viewConfig;
    }

    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    public ScriptConfig getScriptConfig() {
        return scriptConfig;
    }

    public void setScriptConfig(ScriptConfig scriptConfig) {
        this.scriptConfig = scriptConfig;
    }

    /**
     * 视图配置
     */
    public static class ViewConfig {
        private boolean showImage = true;
        private boolean showSpreadsheet = true;
        private boolean showScriptEditor = true;
        private Set<String> visibleColumns = new HashSet<>(Arrays.asList("位置", "名称", "类型", "数值", "表达式", "错误"));

        public boolean isShowImage() {
            return showImage;
        }

        public void setShowImage(boolean showImage) {
            this.showImage = showImage;
        }

        public boolean isShowSpreadsheet() {
            return showSpreadsheet;
        }

        public void setShowSpreadsheet(boolean showSpreadsheet) {
            this.showSpreadsheet = showSpreadsheet;
        }

        public boolean isShowScriptEditor() {
            return showScriptEditor;
        }

        public void setShowScriptEditor(boolean showScriptEditor) {
            this.showScriptEditor = showScriptEditor;
        }

        public Set<String> getVisibleColumns() {
            return visibleColumns;
        }

        public void setVisibleColumns(Set<String> visibleColumns) {
            this.visibleColumns = visibleColumns;
        }
    }

    /**
     * 连接配置
     */
    public static class ConnectionConfig {
        private String address = "127.0.0.1:57789";
        private String username = "admin";
        private String password = "";

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /**
     * 脚本配置
     */
    public static class ScriptConfig {
        private String scriptText = "";
        private boolean lineWrap = false;

        public String getScriptText() {
            return scriptText;
        }

        public void setScriptText(String scriptText) {
            this.scriptText = scriptText;
        }

        public boolean isLineWrap() {
            return lineWrap;
        }

        public void setLineWrap(boolean lineWrap) {
            this.lineWrap = lineWrap;
        }
    }
}
