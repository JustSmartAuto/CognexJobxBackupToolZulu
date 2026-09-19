package com.cognex.insight.ui.panel;

import javax.swing.*;
import java.awt.*;

public class ConnectionPanel extends JPanel {
    public final JTextField txtAddress;
    public final JTextField txtUsername;
    public final JPasswordField txtPassword;
    public final JButton btnConnect;
    public final JLabel lblState;

    public ConnectionPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 5));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel lblAddress = new JLabel("地址和端口:");
        lblAddress.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        txtAddress = new JTextField("127.0.0.1:57789", 14);
        txtAddress.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        JLabel lblUser = new JLabel("用户名:");
        lblUser.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        txtUsername = new JTextField("admin", 8);
        txtUsername.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        JLabel lblPass = new JLabel("密码:");
        lblPass.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        txtPassword = new JPasswordField("", 8);
        txtPassword.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        btnConnect = new JButton("连接");
        btnConnect.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btnConnect.setPreferredSize(new Dimension(80, 26));

        lblState = new JLabel("未连接");
        lblState.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        lblState.setForeground(Color.RED);
        lblState.setPreferredSize(new Dimension(120, 20));
        lblState.setHorizontalAlignment(SwingConstants.RIGHT);

        add(lblAddress);
        add(txtAddress);
        add(lblUser);
        add(txtUsername);
        add(lblPass);
        add(txtPassword);
        add(btnConnect);
        add(Box.createHorizontalStrut(20));
        add(lblState);
    }

    public String getAddress() {
        return txtAddress.getText();
    }

    public void setAddress(String address) {
        txtAddress.setText(address);
    }

    public String getUsername() {
        return txtUsername.getText();
    }

    public void setUsername(String username) {
        txtUsername.setText(username);
    }

    public String getPassword() {
        return new String(txtPassword.getPassword());
    }

    public void setPassword(String password) {
        txtPassword.setText(password);
    }
}
