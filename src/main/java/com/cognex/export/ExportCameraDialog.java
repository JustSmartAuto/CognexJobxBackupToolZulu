package com.cognex.export;

import com.cognex.export.model.ExportCamera;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** 新增/编辑导出相机对话框。 */
public class ExportCameraDialog extends JDialog {

    private boolean confirmed = false;
    private final JTextField txtName = new JTextField(10);
    private final JTextField txtIp = new JTextField(12);
    private final JTextField txtWsPort = new JTextField("80", 5);
    private final JTextField txtUser = new JTextField("admin", 8);
    private final JPasswordField txtPassword = new JPasswordField(8);
    private final JTextField txtFtpPort = new JTextField("21", 5);
    private final JCheckBox chkFtps = new JCheckBox("使用 FTPS", true);
    private final JCheckBox chkTrustAll = new JCheckBox("信任所有 TLS 证书", true);

    public ExportCameraDialog(Window owner, ExportCamera camera) {
        super(owner, camera == null ? "新增相机" : "编辑相机", ModalityType.APPLICATION_MODAL);
        initUI();
        if (camera != null) {
            txtName.setText(camera.getName());
            txtIp.setText(camera.getIp());
            txtWsPort.setText(String.valueOf(camera.getWsPort()));
            txtUser.setText(camera.getUser());
            txtPassword.setText(camera.getPassword());
            txtFtpPort.setText(String.valueOf(camera.getFtpPort()));
            chkFtps.setSelected(camera.isFtps());
            chkTrustAll.setSelected(camera.isTrustAllCerts());
        }
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        int row = 0;
        addRow(content, gbc, row++, "名称:", txtName, "相机 IP:", txtIp);
        addRow(content, gbc, row++, "HMI 端口:", txtWsPort, "FTP 端口:", txtFtpPort);
        addRow(content, gbc, row++, "用户名:", txtUser, "密码:", txtPassword);
        gbc.gridy = row;
        gbc.gridx = 1;
        content.add(chkFtps, gbc);
        gbc.gridx = 3;
        content.add(chkTrustAll, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton btnOk = new JButton("确定");
        JButton btnCancel = new JButton("取消");
        btnPanel.add(btnOk);
        btnPanel.add(btnCancel);

        JPanel root = new JPanel(new BorderLayout());
        root.add(content, BorderLayout.CENTER);
        root.add(btnPanel, BorderLayout.SOUTH);
        setContentPane(root);

        btnOk.addActionListener(e -> {
            if (txtIp.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入相机 IP", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            confirmed = true;
            dispose();
        });
        btnCancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(btnOk);
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label1, JComponent c1, String label2, JComponent c2) {
        gbc.gridy = row;
        gbc.gridx = 0;
        panel.add(new JLabel(label1), gbc);
        gbc.gridx = 1;
        panel.add(c1, gbc);
        gbc.gridx = 2;
        panel.add(new JLabel(label2), gbc);
        gbc.gridx = 3;
        panel.add(c2, gbc);
    }

    /** 用户点确定返回 true，此时可通过 fillCamera 取回编辑后的值。 */
    public boolean isConfirmed() {
        return confirmed;
    }

    public void fillCamera(ExportCamera camera) {
        camera.setName(txtName.getText().trim());
        camera.setIp(txtIp.getText().trim());
        camera.setWsPort(parsePort(txtWsPort.getText().trim(), 80, "HMI 端口"));
        camera.setFtpPort(parsePort(txtFtpPort.getText().trim(), 21, "FTP 端口"));
        camera.setUser(txtUser.getText().trim());
        camera.setPassword(new String(txtPassword.getPassword()));
        camera.setFtps(chkFtps.isSelected());
        camera.setTrustAllCerts(chkTrustAll.isSelected());
    }

    private static int parsePort(String text, int defaultValue, String label) {
        if (text.isEmpty()) {
            return defaultValue;
        }
        int port = Integer.parseInt(text);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(label + " 必须在 1~65535 之间");
        }
        return port;
    }
}
