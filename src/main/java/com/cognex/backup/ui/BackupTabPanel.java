package com.cognex.backup.ui;

import com.cognex.backup.config.ConfigManager;
import com.cognex.backup.ftp.FtpClient;
import com.cognex.backup.model.CameraConfig;
import com.cognex.backup.util.Icons;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Jobx 备份工具标签页：原 MainFrame 的全部备份功能，
 * 重构为 JPanel 以嵌入多标签主窗口。
 */
public class BackupTabPanel extends JPanel {

    private final JFrame parentFrame;
    private final ConfigManager configManager;
    private final FtpClient ftpClient;
    private DefaultTableModel tableModel;
    private JTable cameraTable;
    private JTextArea logArea;

    public BackupTabPanel(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        this.configManager = new ConfigManager();
        this.ftpClient = new FtpClient();

        initUI();
        refreshTable();
    }

    private void initUI() {
        // Main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top: Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton addBtn = new JButton("添加相机");
        JButton editBtn = new JButton("编辑相机");
        JButton delBtn = new JButton("删除相机");
        JButton backupBtn = new JButton("立即备份");
        JButton backupAllBtn = new JButton("备份全部");
        JButton openDirBtn = new JButton("打开备份目录");
        JButton aboutBtn = new JButton("关于");

        Icons.setIcon(addBtn, FontAwesomeSolid.PLUS, 14);
        Icons.setIcon(editBtn, FontAwesomeSolid.PENCIL_ALT, 14);
        Icons.setIcon(delBtn, FontAwesomeSolid.TRASH, 14);
        Icons.setIcon(backupBtn, FontAwesomeSolid.SAVE, 14);
        Icons.setIcon(backupAllBtn, FontAwesomeSolid.CLONE, 14);
        Icons.setIcon(openDirBtn, FontAwesomeSolid.FOLDER_OPEN, 14);
        Icons.setIcon(aboutBtn, FontAwesomeSolid.INFO_CIRCLE, 14);

        addBtn.addActionListener(this::onAddCamera);
        editBtn.addActionListener(this::onEditCamera);
        delBtn.addActionListener(this::onDeleteCamera);
        backupBtn.addActionListener(this::onBackup);
        backupAllBtn.addActionListener(this::onBackupAll);
        openDirBtn.addActionListener(this::onOpenBackupDir);
        aboutBtn.addActionListener(this::onAbout);

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(delBtn);
        buttonPanel.add(new JSeparator(SwingConstants.VERTICAL));
        buttonPanel.add(backupBtn);
        buttonPanel.add(backupAllBtn);
        buttonPanel.add(openDirBtn);
        buttonPanel.add(new JSeparator(SwingConstants.VERTICAL));
        buttonPanel.add(aboutBtn);

        mainPanel.add(buttonPanel, BorderLayout.NORTH);

        // Center: Camera table
        String[] columns = {"相机名称", "IP地址", "FTP端口", "FTP用户名", "FTPS", "备份目录"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        cameraTable = new JTable(tableModel);
        cameraTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cameraTable.setRowHeight(28);
        JScrollPane tableScroll = new JScrollPane(cameraTable);
        mainPanel.add(tableScroll, BorderLayout.CENTER);

        // 说明文本（带 info 图标）
        JLabel hint = new JLabel("提示：先在表格中选择相机，再执行 立即备份/编辑/删除；备份全部 将依次备份所有相机。留空的备份目录默认保存在 jar 目录下 backups/。");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        hint.setForeground(Color.GRAY);
        Icons.setHintIcon(hint);
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        hintPanel.add(hint);

        // Bottom: Log area
        logArea = new JTextArea(8, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("日志"));

        // hint 放日志区上方
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(hintPanel, BorderLayout.NORTH);
        southPanel.add(logScroll, BorderLayout.CENTER);
        mainPanel.add(southPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (CameraConfig cam : configManager.getCameras()) {
            tableModel.addRow(new Object[]{
                    cam.getName(),
                    cam.getIp(),
                    cam.getFtpPort(),
                    cam.getFtpUsername(),
                    cam.isFtpsEnabled() ? "是" : "否",
                    cam.getBackupDirectory()
            });
        }
    }

    private void log(String message) {
        logArea.append("[" + java.time.LocalDateTime.now().toString().replace("T", " ") + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void onAddCamera(ActionEvent e) {
        CameraDialog dialog = new CameraDialog(parentFrame, null);
        dialog.setVisible(true);
        CameraConfig camera = dialog.getCamera();
        if (camera != null) {
            configManager.addCamera(camera);
            refreshTable();
            log("添加相机: " + camera.getName() + " (" + camera.getIp() + ")" +
                (camera.isFtpsEnabled() ? " [FTPS]" : ""));
        }
    }

    private void onEditCamera(ActionEvent e) {
        int row = cameraTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个相机", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CameraConfig existing = configManager.getCameras().get(row);
        CameraDialog dialog = new CameraDialog(parentFrame, existing);
        dialog.setVisible(true);
        CameraConfig camera = dialog.getCamera();
        if (camera != null) {
            configManager.updateCamera(row, camera);
            refreshTable();
            log("更新相机: " + camera.getName());
        }
    }

    private void onDeleteCamera(ActionEvent e) {
        int row = cameraTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个相机", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CameraConfig camera = configManager.getCameras().get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要删除相机 '" + camera.getName() + "' 吗？",
                "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            configManager.removeCamera(row);
            refreshTable();
            log("删除相机: " + camera.getName());
        }
    }

    private void onBackup(ActionEvent e) {
        int row = cameraTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个相机", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CameraConfig camera = configManager.getCameras().get(row);
        doBackup(camera);
    }

    private void onBackupAll(ActionEvent e) {
        if (configManager.getCameras().isEmpty()) {
            JOptionPane.showMessageDialog(this, "相机列表为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new Thread(() -> {
            for (CameraConfig camera : configManager.getCameras()) {
                doBackup(camera);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }).start();
    }

    private void doBackup(CameraConfig camera) {
        log("开始备份: " + camera.getName() + " (" + camera.getIp() + ")" +
            (camera.isFtpsEnabled() ? " [FTPS]" : " [FTP]"));
        new Thread(() -> {
            FtpClient.BackupResult result = ftpClient.backupJobx(camera);
            SwingUtilities.invokeLater(() -> {
                if (result.success) {
                    log("✓ " + camera.getName() + ": " + result.message + " -> " + result.backupPath);
                    JOptionPane.showMessageDialog(this,
                            camera.getName() + " 备份成功!\n" + result.message + "\n路径: " + result.backupPath,
                            "备份成功", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    log("✗ " + camera.getName() + ": " + result.message);
                    // Show detailed error in a scrollable dialog for multi-line messages
                    showErrorDialog(camera.getName() + " 备份失败", result.message);
                }
            });
        }).start();
    }

    private void showErrorDialog(String title, String message) {
        JTextArea textArea = new JTextArea(message);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 250));
        JOptionPane.showMessageDialog(this, scrollPane, title, JOptionPane.ERROR_MESSAGE);
    }

    private void onOpenBackupDir(ActionEvent e) {
        int row = cameraTable.getSelectedRow();
        String path;
        if (row >= 0) {
            CameraConfig camera = configManager.getCameras().get(row);
            path = camera.getBackupDirectory();
            if (path == null || path.trim().isEmpty()) {
                path = configManager.getConfigFilePath().getParent();
            }
        } else {
            path = configManager.getConfigFilePath().getParent();
        }
        try {
            Desktop.getDesktop().open(new File(path));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "无法打开目录: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onAbout(ActionEvent e) {
        AboutDialog dialog = new AboutDialog(parentFrame);
        dialog.setVisible(true);
    }
}
