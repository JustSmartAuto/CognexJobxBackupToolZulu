package com.cognex.insight.ui.panel;

import javax.swing.*;
import java.awt.*;

public class StatusBarPanel extends JPanel {
    private JLabel lblStatus;
    private JLabel lblJobName;
    private JLabel lblCameraInfo;

    public StatusBarPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createLoweredBevelBorder());
        setPreferredSize(new Dimension(0, 28));

        lblStatus = new JLabel("未连接");
        lblStatus.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        lblStatus.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        lblJobName = new JLabel("");
        lblJobName.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        lblJobName.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        lblJobName.setHorizontalAlignment(SwingConstants.CENTER);

        lblCameraInfo = new JLabel("");
        lblCameraInfo.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        lblCameraInfo.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        lblCameraInfo.setHorizontalAlignment(SwingConstants.RIGHT);

        add(lblStatus, BorderLayout.WEST);
        add(lblJobName, BorderLayout.CENTER);
        add(lblCameraInfo, BorderLayout.EAST);
    }

    public void setStatus(String status) {
        lblStatus.setText(status);
    }

    public void setJobName(String name) {
        lblJobName.setText(name != null ? name : "");
    }

    public void setCameraInfo(String info) {
        lblCameraInfo.setText(info != null ? info : "");
    }
}
