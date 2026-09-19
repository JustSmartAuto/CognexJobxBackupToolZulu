package com.cognex.insight.ui.panel;

import com.cognex.insight.cogsocket.InSightConnection;
import com.cognex.insight.config.AppConfig;
import com.cognex.insight.config.InsightConfigManager;
import com.cognex.insight.model.HmiSessionInfo;
import com.cognex.insight.ui.dialog.CameraInfoDialog;
import com.cognex.insight.ui.dialog.SetCellDialog;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 编辑器标签页：原 java-insight-hmi 主窗口内容整合为一个 JPanel，
 * 菜单栏操作改为工具栏按钮（触发/在线/实时/单元格操作/导出 XML/主题切换/列显示）。
 */
public class EditorTabPanel extends JPanel {
    private final JFrame parentFrame;
    private InSightConnection connection;
    private ConnectionPanel connectionPanel;
    private ImageDisplayPanel imagePanel;
    private SpreadsheetPanel spreadsheetPanel;
    private ScriptEditorPanel scriptEditorPanel;
    private StatusBarPanel statusBar;
    private JTextArea txtMessages;

    // 工具栏按钮
    private JButton btnTrigger;
    private JButton btnOnline;
    private JButton btnLiveMode;
    private JButton btnSetCellValue;
    private JButton btnSetCellExpression;
    private JButton btnGetCellExpression;
    private JButton btnExportXml;
    private JButton btnAboutCamera;
    private JCheckBox chkShowImage;
    private JCheckBox chkShowSpreadsheet;
    private JCheckBox chkShowScriptEditor;
    private JButton btnColumns;
    private JButton btnTheme;

    private Map<String, JCheckBoxMenuItem> columnMenuItems = new HashMap<>();

    private boolean showSpreadsheet = true;
    private boolean showImage = true;
    private boolean showScriptEditor = true;

    public EditorTabPanel(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        setLayout(new BorderLayout());

        initToolBar();
        initComponents();
        initConnection();
        loadConfig();
    }

    /** 窗口关闭时由主窗口调用，保存配置并断开连接。 */
    public void shutdown() {
        saveConfig();
        try {
            connection.disconnect().get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private void initToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        btnTrigger = toolBtn("手动触发", e -> onTrigger());
        btnTrigger.setEnabled(false);
        btnOnline = toolBtn("在线/离线", e -> onToggleOnline());
        btnOnline.setEnabled(false);
        btnLiveMode = toolBtn("实时模式", e -> onToggleLiveMode());
        btnLiveMode.setEnabled(false);
        btnSetCellValue = toolBtn("设置单元格值", e -> onSetCellValue());
        btnSetCellValue.setEnabled(false);
        btnSetCellExpression = toolBtn("设置单元格表达式", e -> onSetCellExpression());
        btnSetCellExpression.setEnabled(false);
        btnGetCellExpression = toolBtn("获取单元格表达式", e -> onGetCellExpression());
        btnGetCellExpression.setEnabled(false);
        btnExportXml = toolBtn("导出为 XML", e -> onExportXml());
        btnExportXml.setEnabled(false);
        btnAboutCamera = toolBtn("关于相机", e -> onAbout());
        btnAboutCamera.setEnabled(false);
        btnTheme = toolBtn("深色主题", e -> {
            boolean dark = !(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
            setTheme(dark);
            btnTheme.setText(dark ? "浅色主题" : "深色主题");
            saveConfig();
        });

        // 视图开关
        chkShowImage = new JCheckBox("图像", true);
        chkShowSpreadsheet = new JCheckBox("电子表格", true);
        chkShowScriptEditor = new JCheckBox("脚本编辑器", true);
        for (JCheckBox chk : new JCheckBox[]{chkShowImage, chkShowSpreadsheet, chkShowScriptEditor}) {
            chk.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        }
        chkShowImage.addActionListener(e -> {
            showImage = chkShowImage.isSelected();
            updateLayout();
            saveConfig();
        });
        chkShowSpreadsheet.addActionListener(e -> {
            showSpreadsheet = chkShowSpreadsheet.isSelected();
            updateLayout();
            saveConfig();
        });
        chkShowScriptEditor.addActionListener(e -> {
            showScriptEditor = chkShowScriptEditor.isSelected();
            updateLayout();
            saveConfig();
        });

        // 列显示弹出菜单
        btnColumns = toolBtn("列显示", e -> {
            JPopupMenu popup = new JPopupMenu();
            for (String col : spreadsheetPanel.getAvailableColumns()) {
                popup.add(columnMenuItems.get(col));
            }
            popup.show(btnColumns, 0, btnColumns.getHeight());
        });

        toolBar.add(btnTrigger);
        toolBar.add(btnOnline);
        toolBar.add(btnLiveMode);
        toolBar.addSeparator();
        toolBar.add(btnSetCellValue);
        toolBar.add(btnSetCellExpression);
        toolBar.add(btnGetCellExpression);
        toolBar.addSeparator();
        toolBar.add(btnExportXml);
        toolBar.add(btnAboutCamera);
        toolBar.addSeparator();
        toolBar.add(new JLabel(" 视图: "));
        toolBar.add(chkShowImage);
        toolBar.add(chkShowSpreadsheet);
        toolBar.add(chkShowScriptEditor);
        toolBar.add(btnColumns);
        toolBar.addSeparator();
        toolBar.add(btnTheme);

        add(toolBar, BorderLayout.NORTH);
    }

    private JButton toolBtn(String text, java.awt.event.ActionListener action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btn.addActionListener(action);
        return btn;
    }

    private void initComponents() {
        // 连接面板
        connectionPanel = new ConnectionPanel();
        connectionPanel.btnConnect.addActionListener(e -> onConnect());
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(connectionPanel, BorderLayout.CENTER);
        add(northPanel, BorderLayout.NORTH);

        // 中心分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(600);
        splitPane.setResizeWeight(0.6);

        imagePanel = new ImageDisplayPanel();
        spreadsheetPanel = new SpreadsheetPanel();
        spreadsheetPanel.setCallbacks(
            (cell, value) -> connection.setCellValue(cell, value)
                .thenRun(() -> logMessage("已设置 " + cell + " = " + value))
                .exceptionally(ex -> {
                    showError(new Exception("设置单元格值失败: " + ex.getMessage()));
                    return null;
                }),
            (cell, expr) -> connection.setCellExpression(cell, expr)
                .thenRun(() -> logMessage("已设置 " + cell + " 表达式 = " + expr))
                .exceptionally(ex -> {
                    showError(new Exception("设置表达式失败: " + ex.getMessage()));
                    return null;
                }),
            () -> "",
            () -> connection.canEditCells(),
            () -> connection.isFullAccess(),
            this::logMessage,
            msg -> showError(new Exception(msg))
        );
        scriptEditorPanel = new ScriptEditorPanel(null);

        splitPane.setLeftComponent(imagePanel);

        // 右侧垂直分割：电子表格在上，脚本编辑器在下
        JSplitPane rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplitPane.setDividerLocation(300);
        rightSplitPane.setResizeWeight(0.5);
        rightSplitPane.setTopComponent(spreadsheetPanel);
        rightSplitPane.setBottomComponent(scriptEditorPanel);

        splitPane.setRightComponent(rightSplitPane);
        add(splitPane, BorderLayout.CENTER);

        // 底部消息区域
        txtMessages = new JTextArea(4, 0);
        txtMessages.setFont(new Font("Consolas", Font.PLAIN, 11));
        txtMessages.setEditable(false);
        JScrollPane msgScroll = new JScrollPane(txtMessages);
        msgScroll.setBorder(BorderFactory.createTitledBorder("消息日志"));
        add(msgScroll, BorderLayout.SOUTH);

        // 状态栏（放在消息日志下方的包裹面板中）
        JPanel bottomPanel = new JPanel(new BorderLayout());
        remove(msgScroll);
        bottomPanel.add(msgScroll, BorderLayout.CENTER);
        statusBar = new StatusBarPanel();
        bottomPanel.add(statusBar, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        // 列显示菜单项（配置加载时同步选中状态）
        for (String col : spreadsheetPanel.getAvailableColumns()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(col, true);
            item.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            item.addActionListener(e -> {
                spreadsheetPanel.setColumnVisible(col, item.isSelected());
                saveConfig();
            });
            columnMenuItems.put(col, item);
        }
    }

    private void initConnection() {
        connection = new InSightConnection();
        connection.onConnectedChanged = this::updateUIState;
        connection.onConnectingChanged = this::updateUIState;
        connection.onStateChanged = this::updateUIState;
        connection.onLiveModeChanged = this::updateUIState;
        connection.onJobLoadingChanged = this::updateUIState;
        connection.onEditorAttachedChanged = this::updateUIState;
        connection.onResultsChanged = this::onResultsChanged;
        connection.onPreviewMessage = this::logMessage;
        connection.onError = this::showError;
    }

    private void loadConfig() {
        try {
            InsightConfigManager.load();
            AppConfig config = InsightConfigManager.getConfig();

            AppConfig.ConnectionConfig conn = config.getConnectionConfig();
            if (conn != null) {
                connectionPanel.setAddress(conn.getAddress());
                connectionPanel.setUsername(conn.getUsername());
                connectionPanel.setPassword(conn.getPassword());
            }

            AppConfig.ViewConfig view = config.getViewConfig();
            if (view != null) {
                showImage = view.isShowImage();
                showSpreadsheet = view.isShowSpreadsheet();
                showScriptEditor = view.isShowScriptEditor();
                chkShowImage.setSelected(showImage);
                chkShowSpreadsheet.setSelected(showSpreadsheet);
                chkShowScriptEditor.setSelected(showScriptEditor);
                updateLayout();

                Set<String> visibleCols = view.getVisibleColumns();
                if (visibleCols != null && !visibleCols.isEmpty()) {
                    spreadsheetPanel.setVisibleColumns(visibleCols);
                    for (Map.Entry<String, JCheckBoxMenuItem> entry : columnMenuItems.entrySet()) {
                        entry.getValue().setSelected(visibleCols.contains(entry.getKey()));
                    }
                }
            }

            boolean dark = "dark".equals(config.getTheme());
            setTheme(dark);
            btnTheme.setText(dark ? "浅色主题" : "深色主题");

            AppConfig.ScriptConfig script = config.getScriptConfig();
            if (script != null) {
                if (script.getScriptText() != null && !script.getScriptText().isEmpty()) {
                    scriptEditorPanel.setScriptText(script.getScriptText());
                }
                scriptEditorPanel.setLineWrap(script.isLineWrap());
            }
        } catch (Exception e) {
            System.err.println("加载配置失败: " + e.getMessage());
        }
    }

    private void saveConfig() {
        try {
            AppConfig config = InsightConfigManager.getConfig();

            AppConfig.ConnectionConfig conn = config.getConnectionConfig();
            conn.setAddress(connectionPanel.getAddress());
            conn.setUsername(connectionPanel.getUsername());
            conn.setPassword(connectionPanel.getPassword());

            AppConfig.ViewConfig view = config.getViewConfig();
            view.setShowImage(showImage);
            view.setShowSpreadsheet(showSpreadsheet);
            view.setShowScriptEditor(showScriptEditor);
            view.setVisibleColumns(spreadsheetPanel.getVisibleColumns());

            config.setTheme(UIManager.getLookAndFeel() instanceof FlatDarkLaf ? "dark" : "light");

            AppConfig.ScriptConfig script = config.getScriptConfig();
            script.setScriptText(scriptEditorPanel.getScriptText());
            script.setLineWrap(scriptEditorPanel.isLineWrap());

            InsightConfigManager.save();
        } catch (Exception e) {
            System.err.println("保存配置失败: " + e.getMessage());
        }
    }

    private void onConnect() {
        if (connection.isConnected()) {
            onDisconnect();
            return;
        }

        String address = connectionPanel.txtAddress.getText().trim();
        String username = connectionPanel.txtUsername.getText().trim();
        String password = new String(connectionPanel.txtPassword.getPassword());

        if (address.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入地址和端口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        connectionPanel.btnConnect.setEnabled(false);
        connectionPanel.btnConnect.setText("连接中...");

        HmiSessionInfo sessionInfo = new HmiSessionInfo(
            Collections.singletonList("A0:Z599")
        );
        sessionInfo.sheetName = "Inspection";
        sessionInfo.enableQueuedResults = true;
        sessionInfo.includeCustomView = true;

        connection.connect(address, username, password, sessionInfo)
            .thenRun(() -> SwingUtilities.invokeLater(() -> {
                connectionPanel.btnConnect.setText("断开");
                connectionPanel.btnConnect.setEnabled(true);
                logMessage("连接成功: " + address);
            }))
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    connectionPanel.btnConnect.setText("连接");
                    connectionPanel.btnConnect.setEnabled(true);
                    showError(new Exception("连接失败: " + ex.getMessage()));
                });
                return null;
            });
    }

    private void onDisconnect() {
        connection.disconnect().thenRun(() -> SwingUtilities.invokeLater(() -> {
            connectionPanel.btnConnect.setText("连接");
            imagePanel.clearImage();
            spreadsheetPanel.clear();
            logMessage("已断开连接");
        }));
    }

    private void onTrigger() {
        connection.manualTrigger()
            .thenRun(() -> logMessage("手动触发已发送"))
            .exceptionally(ex -> {
                showError(new Exception("触发失败: " + ex.getMessage()));
                return null;
            });
    }

    private void onToggleOnline() {
        boolean next = !connection.isSoftOnline();
        connection.setSoftOnline(next)
            .thenRun(() -> logMessage(next ? "已设置为在线" : "已设置为离线"))
            .exceptionally(ex -> {
                showError(new Exception("设置在线状态失败: " + ex.getMessage()));
                return null;
            });
    }

    private void onToggleLiveMode() {
        boolean next = !connection.isLiveMode();
        connection.setLiveMode(next)
            .thenRun(() -> logMessage(next ? "实时模式已开启" : "实时模式已关闭"))
            .exceptionally(ex -> {
                showError(new Exception("设置实时模式失败: " + ex.getMessage()));
                return null;
            });
    }

    private void onSetCellValue() {
        SetCellDialog dlg = new SetCellDialog(window(), "设置单元格值", "单元格", "值");
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            String cell = dlg.txtCell.getText().trim();
            String value = dlg.txtValue.getText().trim();
            Object val = tryParseNumber(value);
            connection.setCellValue(cell, val)
                .thenRun(() -> logMessage("已设置 " + cell + " = " + val))
                .exceptionally(ex -> {
                    showError(new Exception("设置单元格值失败: " + ex.getMessage()));
                    return null;
                });
        }
    }

    private void onSetCellExpression() {
        SetCellDialog dlg = new SetCellDialog(window(), "设置单元格表达式", "单元格", "表达式");
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            String cell = dlg.txtCell.getText().trim();
            String expr = dlg.txtValue.getText().trim();
            connection.setCellExpression(cell, expr)
                .thenRun(() -> logMessage("已设置 " + cell + " 表达式 = " + expr))
                .exceptionally(ex -> {
                    showError(new Exception("设置表达式失败: " + ex.getMessage()));
                    return null;
                });
        }
    }

    private void onGetCellExpression() {
        String cell = JOptionPane.showInputDialog(this, "请输入单元格位置:", "A3");
        if (cell != null && !cell.trim().isEmpty()) {
            connection.getCellExpression(cell.trim())
                .thenAccept(expr -> SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "单元格 " + cell + " 的表达式:\n" + expr, "表达式", JOptionPane.INFORMATION_MESSAGE)))
                .exceptionally(ex -> {
                    showError(new Exception("获取表达式失败: " + ex.getMessage()));
                    return null;
                });
        }
    }

    private void onAbout() {
        new CameraInfoDialog(window(), connection.getCameraInfo()).setVisible(true);
    }

    private java.awt.Window window() {
        Window w = SwingUtilities.getWindowAncestor(this);
        return w != null ? w : parentFrame;
    }

    private void onExportXml() {
        List<com.cognex.insight.model.CellResult> cells = spreadsheetPanel.getCellResults();
        if (cells == null || cells.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有可导出的电子表格数据", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File jarDir = getJarDirectory();
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String fileName = "spreadsheet_" + timestamp + ".xml";
        File defaultFile = new File(jarDir, fileName);

        JFileChooser chooser = new JFileChooser(jarDir);
        chooser.setSelectedFile(defaultFile);
        chooser.setFileFilter(new FileNameExtensionFilter("XML 文件 (*.xml)", "xml"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xml")) {
            file = new File(file.getAbsolutePath() + ".xml");
        }

        try {
            exportCellsToXml(cells, file, timestamp);
            logMessage("已导出 XML: " + file.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "导出成功:\n" + file.getAbsolutePath(), "导出完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError(new Exception("导出 XML 失败: " + ex.getMessage()));
        }
    }

    private File getJarDirectory() {
        try {
            String jarPath = EditorTabPanel.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            if (jarFile.isFile()) {
                return jarFile.getParentFile();
            }
        } catch (Exception ignored) {
        }
        return new File(System.getProperty("user.dir"));
    }

    private void exportCellsToXml(List<com.cognex.insight.model.CellResult> cells, File file, String timestamp) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<Spreadsheet timestamp=\"" + escapeXml(timestamp) + "\" ");
            writer.write("cellCount=\"" + cells.size() + "\" ");
            writer.write("source=\"" + escapeXml(connection.getIpAddress()) + "\"\n");
            writer.write("          jobName=\"" + escapeXml(connection.getJobName()) + "\">\n");

            for (com.cognex.insight.model.CellResult cr : cells) {
                String loc = cr.location != null ? cr.location : "";
                String type = cr.type != null ? cr.type : "";
                String name = cr.name != null ? cr.name : "";
                String data = cr.data != null ? cr.data.toString() : "";
                String expr = cr.expression != null ? cr.expression : "";

                writer.write("  <Cell location=\"" + escapeXml(loc) + "\" ");
                writer.write("type=\"" + escapeXml(type) + "\" ");
                writer.write("error=\"" + cr.error + "\">\n");
                if (!name.isEmpty()) {
                    writer.write("    <Name>" + escapeXml(name) + "</Name>\n");
                }
                writer.write("    <Data>" + escapeXml(data) + "</Data>\n");
                if (!expr.isEmpty()) {
                    writer.write("    <Expression>" + escapeXml(expr) + "</Expression>\n");
                }
                writer.write("  </Cell>\n");
            }

            writer.write("</Spreadsheet>\n");
        }
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    private void onResultsChanged() {
        JsonObject results = connection.getLastResults();
        if (results == null) return;

        java.util.List<String> cellLocations = new java.util.ArrayList<>();
        if (results.has("cells") && results.get("cells").isJsonArray()) {
            JsonArray cells = results.getAsJsonArray("cells");
            for (JsonElement e : cells) {
                if (e.isJsonObject()) {
                    JsonObject obj = e.getAsJsonObject();
                    if (obj.has("location")) {
                        cellLocations.add(obj.get("location").getAsString());
                    }
                }
            }
        }

        if (!cellLocations.isEmpty()) {
            connection.fetchAllExpressions(cellLocations)
                .thenAccept(expressions -> SwingUtilities.invokeLater(() ->
                    spreadsheetPanel.updateResults(results, expressions)))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> spreadsheetPanel.updateResults(results));
                    return null;
                });
        } else {
            spreadsheetPanel.updateResults(results);
        }

        if (results.has("acqImageView")) {
            JsonObject acqView = results.getAsJsonObject("acqImageView");
            if (acqView.has("layers") && acqView.get("layers").isJsonArray()) {
                JsonArray layers = acqView.getAsJsonArray("layers");
                for (JsonElement layer : layers) {
                    if (layer.isJsonObject()) {
                        JsonObject lo = layer.getAsJsonObject();
                        if (lo.has("$type") && "ImageLayer".equals(lo.get("$type").getAsString())) {
                            if (lo.has("url")) {
                                String url = "http://" + connection.getIpAddress() + lo.get("url").getAsString();
                                loadImageAsync(url);
                            }
                            break;
                        }
                    }
                }
            }
        }

        statusBar.setJobName(connection.getJobName());
        if (connection.getCameraInfo() != null) {
            statusBar.setCameraInfo(connection.getCameraInfo().modelNumber);
        }
    }

    private void loadImageAsync(String url) {
        CompletableFuture.runAsync(() -> {
            try {
                BufferedImage img = ImageIO.read(new URL(url));
                if (img != null) {
                    SwingUtilities.invokeLater(() -> imagePanel.setImage(img));
                }
            } catch (Exception e) {
                System.err.println("加载图像失败: " + e.getMessage());
            }
        });
    }

    private void updateUIState() {
        boolean connected = connection.isConnected();
        boolean connecting = connection.isConnecting();
        boolean busy = connection.isJobLoading() || connection.isEditorAttached();
        boolean offline = connected && !busy && !connection.isOnline();

        connectionPanel.btnConnect.setEnabled(!connecting);
        connectionPanel.btnConnect.setText(connected ? "断开" : "连接");

        if (connected) {
            String stateText = connection.isOnline() ? "在线" : "离线";
            if (connection.isEditorAttached()) stateText = "编辑器已连接, " + stateText;
            connectionPanel.lblState.setText(stateText);
            connectionPanel.lblState.setForeground(connection.isOnline() ? new Color(0, 128, 0) : Color.ORANGE);
            statusBar.setStatus(stateText);
        } else {
            connectionPanel.lblState.setText(connecting ? "连接中..." : "未连接");
            connectionPanel.lblState.setForeground(Color.RED);
            statusBar.setStatus(connecting ? "连接中..." : "未连接");
        }

        btnTrigger.setEnabled(connected && !busy);
        btnOnline.setEnabled(connected && !busy);
        btnOnline.setText(connection.isOnline() ? "离线" : "在线");
        btnLiveMode.setEnabled(offline);
        btnLiveMode.setText(connection.isLiveMode() ? "关闭实时模式" : "实时模式");
        btnAboutCamera.setEnabled(connected);
        btnSetCellValue.setEnabled(connected && connection.canEditCells());
        btnSetCellExpression.setEnabled(connected && connection.canEditCells());
        btnGetCellExpression.setEnabled(connected);
        btnExportXml.setEnabled(connected);
        if (scriptEditorPanel != null) {
            scriptEditorPanel.setEnabled(connected);
            scriptEditorPanel.setConnection(connection);
        }
    }

    private void updateLayout() {
        if (imagePanel != null) imagePanel.setVisible(showImage);
        if (spreadsheetPanel != null) spreadsheetPanel.setVisible(showSpreadsheet);
        if (scriptEditorPanel != null) scriptEditorPanel.setVisible(showScriptEditor);
        revalidate();
        repaint();
    }

    private void logMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            txtMessages.append(msg + "\n");
            txtMessages.setCaretPosition(txtMessages.getDocument().getLength());
        });
    }

    private void showError(Exception ex) {
        SwingUtilities.invokeLater(() -> {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            logMessage("错误: " + msg);
            JOptionPane.showMessageDialog(this, msg, "错误", JOptionPane.ERROR_MESSAGE);
        });
    }

    private Object tryParseNumber(String s) {
        try {
            if (s.contains(".")) {
                return Double.parseDouble(s);
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private void setTheme(boolean dark) {
        try {
            if (dark) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            // 更新整个顶层窗口（含其他标签页）
            for (Window w : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(w);
            }
        } catch (Exception ex) {
            showError(new Exception("切换主题失败: " + ex.getMessage()));
        }
    }
}
