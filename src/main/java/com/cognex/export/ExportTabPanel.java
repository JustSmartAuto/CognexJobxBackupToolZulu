package com.cognex.export;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

/**
 * Jobx 导出工具标签页：对应 Go 版 CLI 导出工具的图形界面。
 * 参数（IP/HMI 端口/FTP 端口/FTPS/证书/输出目录/是否跳过表达式）
 * 收集为 {@link ExportTask.Params}，后台执行并实时输出日志。
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
    private final JButton btnStart = new JButton("开始导出");
    private final JButton btnBrowse = new JButton("浏览...");
    private final JButton btnOpenDir = new JButton("打开输出目录");
    private final JTextArea logArea = new JTextArea(10, 0);

    private volatile boolean running = false;

    public ExportTabPanel() {
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
        btnRow.add(btnStart);
        JLabel hint = new JLabel("提示：导出时会自动将相机切换为离线并逐个加载作业，全部完成后恢复原作业与在线状态。旧固件 HMI 端口通常为 8087。");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        hint.setForeground(Color.GRAY);
        btnRow.add(hint);
        actionPanel.add(btnRow, BorderLayout.NORTH);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(paramPanel, BorderLayout.NORTH);
        topPanel.add(actionPanel, BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // ===== 日志区 =====
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("导出日志"));
        mainPanel.add(logScroll, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);

        btnStart.addActionListener(e -> onStart());
        btnBrowse.addActionListener(e -> onBrowse());
        btnOpenDir.addActionListener(e -> onOpenDir());
    }

    private JLabel label(String text, Font font) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(font);
        return lbl;
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
                btnStart.setText("开始导出");
                if (resultMsg == null) {
                    log("======== 导出成功 ========");
                } else {
                    log("======== " + resultMsg + " ========");
                    JOptionPane.showMessageDialog(this, resultMsg, "导出结果", JOptionPane.WARNING_MESSAGE);
                }
            });
        }, "jobx-export").start();
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
