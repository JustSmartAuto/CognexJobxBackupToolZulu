package com.cognex.insight.ui.dialog;

import com.cognex.insight.model.CameraInfo;

import javax.swing.*;
import java.awt.*;

public class CameraInfoDialog extends JDialog {
    public CameraInfoDialog(java.awt.Window parent, CameraInfo info) {
        super(parent, "相机信息", ModalityType.APPLICATION_MODAL);
        setSize(380, 280);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        textArea.setEditable(false);
        textArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        if (info != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("名称: ").append(info.hostName).append("\n");
            sb.append("IP 地址: ").append(info.ipAddress).append("\n");
            sb.append("型号: ").append(info.modelNumber).append("\n");
            sb.append("MAC: ").append(info.macAddress).append("\n");
            sb.append("序列号: ").append(info.serialNumber).append("\n");
            sb.append("固件版本: ").append(info.firmwareVersion).append("\n");
            sb.append("HMI API: ").append(info.apiVersion).append("\n");
            textArea.setText(sb.toString());
        } else {
            textArea.setText("无相机信息");
        }

        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, BorderLayout.CENTER);

        JButton btnOk = new JButton("确定");
        btnOk.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btnOk.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(btnOk);
        add(btnPanel, BorderLayout.SOUTH);
    }
}
