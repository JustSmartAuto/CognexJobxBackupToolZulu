package com.cognex.export;

import com.cognex.export.config.ExportConfigManager;
import com.cognex.export.model.ExportCamera;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Jobx 导出工具标签页：对应 Go 版 CLI 导出工具的图形界面。
 * 支持两种模式：
 * 1. 手动参数模式：表单填写单个相机的连接参数，点“手动导出”；
 * 2. 相机列表批量模式：左侧维护相机列表（export-config.json，自动保存），
 *    勾选多台相机后点“批量导出所选相机”，逐台执行并分别输出到
 *    输出目录/相机名称/时间戳/ 下，单台失败不中断其余相机。
 */
public class ExportTabPanel extends JPanel {

    private final JTextField txtIp = new JTextField("192.168.1.75", 12);
    private final JTextField txtWsPort = new JTextField("80", 5);
    private final JTextField txtUser = new JTextField("admin", 8);
    private final JPasswordField txtPassword = new JPasswordField("", 8);
    private final JTextField txtFtpPort = new JTextField("21", 5);
    private final JCheckBox chkFtps = new JCheckBox("使用 FTPS", true);
    private final JCheckBox chkTrustAll = new JCheckBox("信任所有 TLS 证书", true);
    private final JCheckBox chkNoExpr = new JCheckBox("跳过表达式（更快）", false);
    private final JTextField txtOutDir = new JTextField("", 24);
    private final JButton btnStart = new JButton("手动导出");
    private final JButton btnBatch = new JButton("批量导出所选相机");
    private final JButton btnBrowse = new JButton("浏览...");
    private final JButton btnOpenDir = new JButton("打开输出目录");
    private final JTextArea logArea = new JTextArea(10, 0);

    private final ExportConfigManager exportConfig = new ExportConfigManager();
    private final DefaultListModel<ExportCamera> cameraListModel = new DefaultListModel<>();
    private final JList<ExportCamera> cameraList = new JList<>(cameraListModel);

    private volatile boolean running = false;

    public ExportTabPanel() {
        for (ExportCamera c : exportConfig.getCameras()) {
            cameraListModel.addElement(c);
        }
        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // ===== 参数区 =====
        JPanel paramPanel = new JPanel(new GridBagLayout());
        paramPanel.setBorder(new TitledBorder("相机连接参数"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        Font font = new Font("Microsoft YaHei", Font.PLAIN, 12);
        for (JComponent c : new JComponent[]{txtIp, txtWsPort, txtUser, txtPassword, txtFtpPort,
                chkFtps, chkTrustAll, chkNoExpr, txtOutDir, btnBrowse, btnOpenDir}) {
            c.setFont(font);
        }
        setIcon(btnBrowse, FontAwesomeSolid.FOLDER_OPEN, 14);
        setIcon(btnOpenDir, FontAwesomeSolid.EXTERNAL_LINK_ALT, 14);

        // 第一行：IP / HMI 端口 / 用户名 / 密码
        gbc.gridy = 0;
        gbc.gridx = 0;
        paramPanel.add(label("相机 IP:", font), gbc);
        gbc.gridx = 1;
        paramPanel.add(txtIp, gbc);
        gbc.gridx = 2;
        paramPanel.add(label("HMI 端口:", font), gbc);
        gbc.gridx = 3;
        paramPanel.add(txtWsPort, gbc);
        gbc.gridx = 4;
        paramPanel.add(label("用户名:", font), gbc);
        gbc.gridx = 5;
        paramPanel.add(txtUser, gbc);
        gbc.gridx = 6;
        paramPanel.add(label("密码:", font), gbc);
        gbc.gridx = 7;
        paramPanel.add(txtPassword, gbc);

        // 第二行：FTP 端口 / FTPS / 证书 / 跳过表达式
        gbc.gridy = 1;
        gbc.gridx = 0;
        paramPanel.add(label("FTP 端口:", font), gbc);
        gbc.gridx = 1;
        paramPanel.add(txtFtpPort, gbc);
        gbc.gridx = 2;
        paramPanel.add(chkFtps, gbc);
        gbc.gridx = 3;
        paramPanel.add(chkTrustAll, gbc);
        gbc.gridx = 4;
        gbc.gridx = 5;
        paramPanel.add(chkNoExpr, gbc);

        // 第三行：输出目录
        gbc.gridy = 2;
        gbc.gridx = 0;
        paramPanel.add(label("输出目录:", font), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 5;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        paramPanel.add(txtOutDir, gbc);
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridx = 6;
        paramPanel.add(btnBrowse, gbc);
        gbc.gridx = 7;
        paramPanel.add(btnOpenDir, gbc);

        // ===== 按钮行 + 提示 =====
        JPanel actionPanel = new JPanel(new BorderLayout(10, 5));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        btnStart.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        btnBatch.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        setIcon(btnStart, FontAwesomeSolid.FILE_EXPORT, 16);
        setIcon(btnBatch, FontAwesomeSolid.DOWNLOAD, 16);
        btnRow.add(btnStart);
        btnRow.add(btnBatch);
        JLabel hint = new JLabel("批量导出时逐台执行，输出到 输出目录/相机名称/时间戳/，单台失败不影响其余相机。");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        hint.setForeground(Color.GRAY);
        setHintIcon(hint);
        btnRow.add(hint);
        actionPanel.add(btnRow, BorderLayout.NORTH);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(paramPanel, BorderLayout.NORTH);
        topPanel.add(actionPanel, BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // ===== 相机列表（批量导出） =====
        JPanel cameraPanel = new JPanel(new BorderLayout(5, 5));
        cameraPanel.setBorder(new TitledBorder("相机列表（批量导出，可多选）"));
        cameraList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        cameraList.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        cameraList.setVisibleRowCount(6);
        cameraPanel.add(new JScrollPane(cameraList), BorderLayout.CENTER);

        JPanel cameraBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton btnAdd = new JButton("新增");
        JButton btnEdit = new JButton("编辑");
        JButton btnRemove = new JButton("删除");
        JButton btnSelectAll = new JButton("全选");
        setIcon(btnAdd, FontAwesomeSolid.PLUS, 14);
        setIcon(btnEdit, FontAwesomeSolid.PENCIL_ALT, 14);
        setIcon(btnRemove, FontAwesomeSolid.TRASH, 14);
        setIcon(btnSelectAll, FontAwesomeSolid.CHECK_SQUARE, 14);
        for (JButton b : new JButton[]{btnAdd, btnEdit, btnRemove, btnSelectAll}) {
            b.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            cameraBtnRow.add(b);
        }
        cameraPanel.add(cameraBtnRow, BorderLayout.SOUTH);
        mainPanel.add(cameraPanel, BorderLayout.CENTER);

        // ===== 日志区 =====
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("导出日志"));
        logScroll.setPreferredSize(new Dimension(0, 220));
        mainPanel.add(logScroll, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);

        btnStart.addActionListener(e -> onStart());
        btnBatch.addActionListener(e -> onBatchExport());
        btnBrowse.addActionListener(e -> onBrowse());
        btnOpenDir.addActionListener(e -> onOpenDir());
        btnAdd.addActionListener(e -> onAddCamera());
        btnEdit.addActionListener(e -> onEditCamera());
        btnRemove.addActionListener(e -> onRemoveCamera());
        btnSelectAll.addActionListener(e -> cameraList.setSelectionInterval(0, cameraListModel.size() - 1));
        cameraList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onEditCamera();
                }
            }
        });
    }

    private JLabel label(String text, Font font) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(font);
        return lbl;
    }

    /** 给按钮加 ikonli 图标（默认取按钮前景色，随主题/禁用态变色）。 */
    private static void setIcon(AbstractButton button, Ikon ikon, int size) {
        FontIcon icon = FontIcon.of(ikon, size);
        icon.setIconColor(UIManager.getColor("Button.foreground"));
        button.setIcon(icon);
    }

    /** 给说明文本加灰色 info 图标。 */
    private static void setHintIcon(JLabel hint) {
        FontIcon icon = FontIcon.of(FontAwesomeSolid.INFO_CIRCLE, 14);
        icon.setIconColor(hint.getForeground());
        hint.setIcon(icon);
    }

    private void onBrowse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = txtOutDir.getText().trim();
        if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtOutDir.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onOpenDir() {
        String path = txtOutDir.getText().trim();
        if (path.isEmpty()) {
            path = new File(jarDirectory(), "exports").getAbsolutePath();
        }
        File dir = new File(path);
        if (!dir.exists()) {
            // 尚未导出过时先给出默认位置提示
            JOptionPane.showMessageDialog(this,
                    "目录尚不存在: " + dir.getAbsolutePath(), "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "无法打开目录: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onStart() {
        if (running) {
            return;
        }

        ExportTask.Params params;
        try {
            params = collectParams();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "参数错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        running = true;
        btnStart.setEnabled(false);
        btnBatch.setEnabled(false);
        btnStart.setText("导出中...");
        log("======== 开始导出 ========");

        new Thread(() -> {
            ExportTask task = new ExportTask(params, msg -> SwingUtilities.invokeLater(() -> log(msg)));
            String error;
            try {
                error = task.run();
            } catch (Exception ex) {
                error = "导出异常: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
            final String resultMsg = error;
            SwingUtilities.invokeLater(() -> {
                running = false;
                btnStart.setEnabled(true);
                btnBatch.setEnabled(true);
                btnStart.setText("手动导出");
                if (resultMsg == null) {
                    log("======== 导出成功 ========");
                } else {
                    log("======== " + resultMsg + " ========");
                    JOptionPane.showMessageDialog(this, resultMsg, "导出结果", JOptionPane.WARNING_MESSAGE);
                }
            });
        }, "jobx-export").start();
    }

    // ===== 相机列表管理 =====

    private void onAddCamera() {
        ExportCameraDialog dialog = new ExportCameraDialog(SwingUtilities.getWindowAncestor(this), null);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        ExportCamera camera = new ExportCamera();
        try {
            dialog.fillCamera(camera);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "参数错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        cameraListModel.addElement(camera);
        exportConfig.getCameras().add(camera);
        exportConfig.save();
    }

    private void onEditCamera() {
        int index = cameraList.getSelectedIndex();
        if (index < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一台相机", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ExportCamera camera = cameraListModel.getElementAt(index);
        ExportCameraDialog dialog = new ExportCameraDialog(SwingUtilities.getWindowAncestor(this), camera);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        try {
            dialog.fillCamera(camera);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "参数错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        exportConfig.save();
        cameraListModel.setElementAt(camera, index); // 刷新显示
    }

    private void onRemoveCamera() {
        List<ExportCamera> selected = cameraList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "确定删除选中的 " + selected.size() + " 台相机？", "确认", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) {
            return;
        }
        for (ExportCamera c : selected) {
            cameraListModel.removeElement(c);
            exportConfig.getCameras().remove(c);
        }
        exportConfig.save();
    }

    // ===== 批量导出 =====

    private void onBatchExport() {
        if (running) {
            return;
        }
        List<ExportCamera> selected = cameraList.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先在相机列表中选择要导出的相机（可按住 Ctrl/Shift 多选）",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        running = true;
        btnStart.setEnabled(false);
        btnBatch.setEnabled(false);
        btnBatch.setText("批量导出中...");
        final List<ExportCamera> cameras = selected;
        log("======== 开始批量导出，共 " + cameras.size() + " 台相机 ========");

        new Thread(() -> {
            int success = 0;
            int failed = 0;
            String outRoot = txtOutDir.getText().trim();
            boolean noExpr = chkNoExpr.isSelected();
            for (int i = 0; i < cameras.size(); i++) {
                ExportCamera camera = cameras.get(i);
                ExportTask.Params p = buildParamsFromCamera(camera, outRoot, noExpr);
                SwingUtilities.invokeLater(() -> log("-------- [" + (cameras.indexOf(camera) + 1) + "/" + cameras.size() + "] "
                        + (camera.getName().isEmpty() ? camera.getIp() : camera.getName()) + " --------"));
                ExportTask task = new ExportTask(p, msg -> SwingUtilities.invokeLater(() -> log(msg)));
                String error;
                try {
                    error = task.run();
                } catch (Exception ex) {
                    error = "导出异常: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
                }
                if (error == null) {
                    success++;
                } else {
                    failed++;
                    final String failMsg = error;
                    SwingUtilities.invokeLater(() -> log("  该相机导出失败: " + failMsg));
                }
            }
            final int s = success;
            final int f = failed;
            SwingUtilities.invokeLater(() -> {
                running = false;
                btnStart.setEnabled(true);
                btnBatch.setEnabled(true);
                btnBatch.setText("批量导出所选相机");
                log("======== 批量导出结束：成功 " + s + " 台，失败 " + f + " 台 ========");
                if (f > 0) {
                    JOptionPane.showMessageDialog(this,
                            "批量导出完成：成功 " + s + " 台，失败 " + f + " 台（详见日志）",
                            "导出结果", JOptionPane.WARNING_MESSAGE);
                }
            });
        }, "jobx-export-batch").start();
    }

    /** 由相机配置构造导出参数，输出目录为 输出根目录/相机名称。 */
    private ExportTask.Params buildParamsFromCamera(ExportCamera camera, String outRoot, boolean noExpr) {
        ExportTask.Params p = new ExportTask.Params();
        p.ip = camera.getIp();
        p.wsPort = camera.getWsPort();
        p.user = camera.getUser().isEmpty() ? "admin" : camera.getUser();
        p.password = camera.getPassword();
        p.ftpPort = camera.getFtpPort();
        p.ftps = camera.isFtps();
        p.trustAllCerts = camera.isTrustAllCerts();
        p.noExpr = noExpr;
        String cameraDir = camera.getName().isEmpty() ? camera.getIp() : camera.getName();
        if (outRoot != null && !outRoot.trim().isEmpty()) {
            p.outRoot = new File(outRoot.trim(), cameraDir).getAbsolutePath();
        }
        return p;
    }

    private ExportTask.Params collectParams() {
        String ip = txtIp.getText().trim();
        if (ip.isEmpty()) {
            throw new IllegalArgumentException("请输入相机 IP");
        }
        ExportTask.Params p = new ExportTask.Params();
        p.ip = ip;
        p.wsPort = parsePort(txtWsPort.getText().trim(), 80, "HMI 端口");
        p.ftpPort = parsePort(txtFtpPort.getText().trim(), 21, "FTP 端口");
        p.user = txtUser.getText().trim();
        if (p.user.isEmpty()) {
            p.user = "admin";
        }
        p.password = new String(txtPassword.getPassword());
        p.ftps = chkFtps.isSelected();
        p.trustAllCerts = chkTrustAll.isSelected();
        p.noExpr = chkNoExpr.isSelected();
        p.outRoot = txtOutDir.getText().trim();
        return p;
    }

    private static int parsePort(String text, int defaultValue, String label) {
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            int port = Integer.parseInt(text);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(label + " 必须在 1~65535 之间");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " 不是有效端口号");
        }
    }

    private void log(String msg) {
        logArea.append("[" + java.time.LocalDateTime.now().toString().replace("T", " ") + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static File jarDirectory() {
        try {
            String jarPath = ExportTabPanel.class.getProtectionDomain()
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
