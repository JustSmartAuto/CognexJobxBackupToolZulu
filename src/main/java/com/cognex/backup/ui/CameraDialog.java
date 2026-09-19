package com.cognex.backup.ui;

import com.cognex.backup.model.CameraConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class CameraDialog extends JDialog {

    private final JTextField nameField;
    private final JTextField ipField;
    private final JTextField portField;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JTextField backupDirField;
    private final JCheckBox ftpsCheckBox;
    private final JCheckBox trustAllCertsCheckBox;
    private CameraConfig result;

    public CameraDialog(JFrame parent, CameraConfig camera) {
        super(parent, camera == null ? "添加相机" : "编辑相机", true);
        setSize(500, 400);
        setLocationRelativeTo(parent);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        nameField = new JTextField(20);
        ipField = new JTextField(20);
        portField = new JTextField("21", 20);
        usernameField = new JTextField("admin", 20);
        passwordField = new JPasswordField(20);
        backupDirField = new JTextField(20);
        ftpsCheckBox = new JCheckBox("使用 FTPS (FTP over TLS/SSL)");
        trustAllCertsCheckBox = new JCheckBox("信任所有 TLS 证书 (默认开启，用于自签名证书)");
        trustAllCertsCheckBox.setSelected(true);

        if (camera != null) {
            nameField.setText(camera.getName());
            ipField.setText(camera.getIp());
            portField.setText(String.valueOf(camera.getFtpPort()));
            usernameField.setText(camera.getFtpUsername());
            passwordField.setText(camera.getFtpPassword());
            backupDirField.setText(camera.getBackupDirectory());
            ftpsCheckBox.setSelected(camera.isFtpsEnabled());
            trustAllCertsCheckBox.setSelected(camera.isTrustAllCerts());
        }

        // FTPS checkbox change listener
        ftpsCheckBox.addActionListener(e -> trustAllCertsCheckBox.setEnabled(ftpsCheckBox.isSelected()));
        trustAllCertsCheckBox.setEnabled(ftpsCheckBox.isSelected());

        int row = 0;
        addRow(panel, gbc, row++, "相机名称:", nameField);
        addRow(panel, gbc, row++, "IP 地址:", ipField);
        addRow(panel, gbc, row++, "FTP 端口:", portField);
        addRow(panel, gbc, row++, "FTP 用户名:", usernameField);
        addRow(panel, gbc, row++, "FTP 密码:", passwordField);

        // Backup directory with browse button
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel("备份目录:"), gbc);

        JPanel dirPanel = new JPanel(new BorderLayout(5, 0));
        dirPanel.add(backupDirField, BorderLayout.CENTER);
        JButton browseBtn = new JButton("浏览...");
        browseBtn.addActionListener(e -> browseDirectory());
        dirPanel.add(browseBtn, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(dirPanel, gbc);
        row++;

        // FTPS options
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(ftpsCheckBox, gbc);
        row++;

        gbc.gridy = row;
        panel.add(trustAllCertsCheckBox, gbc);
        row++;

        // Buttons
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton okBtn = new JButton("确定");
        JButton cancelBtn = new JButton("取消");
        okBtn.addActionListener(e -> onOk());
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        panel.add(btnPanel, gbc);

        add(panel);
        getRootPane().setDefaultButton(okBtn);
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
    }

    private void browseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = backupDirField.getText();
        if (current != null && !current.trim().isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            backupDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onOk() {
        String name = nameField.getText().trim();
        String ip = ipField.getText().trim();
        String portStr = portField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String backupDir = backupDirField.getText().trim();
        boolean ftps = ftpsCheckBox.isSelected();
        boolean trustAll = trustAllCertsCheckBox.isSelected();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入相机名称", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入IP地址", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "端口号无效，请输入1-65535之间的数字", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        result = new CameraConfig(name, ip, port, username, password, backupDir, ftps, trustAll);
        dispose();
    }

    public CameraConfig getCamera() {
        return result;
    }
}
