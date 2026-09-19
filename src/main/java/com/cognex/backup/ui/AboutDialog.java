package com.cognex.backup.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class AboutDialog extends JDialog {

    public AboutDialog(JFrame parent) {
        super(parent, "关于", true);
        setSize(480, 400);
        setLocationRelativeTo(parent);
        setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Cognex Jobx 备份工具", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 20));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Content
        JTextArea contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setBackground(panel.getBackground());
        contentArea.setText(
                "版本: 1.1.0\n" +
                "\n" +
                "本工具用于 Cognex In-Sight 智能相机的 Jobx 文件备份。\n" +
                "支持 FTP 和 FTPS (FTP over TLS/SSL) 两种协议，\n" +
                "可自动下载相机中的 jobx 作业文件到本地备份。\n" +
                "\n" +
                "主要特性:\n" +
                "  • FTP / FTPS 双协议支持\n" +
                "  • 自动信任自签名 TLS 证书\n" +
                "  • 批量备份多台相机\n" +
                "  • 友好的中文错误提示\n" +
                "\n" +
                "技术栈:\n" +
                "  • Java 8\n" +
                "  • FlatLaf 主题\n" +
                "  • Apache Commons Net (FTP/FTPS)\n" +
                "  • Gson (JSON 配置)\n" +
                "\n" +
                "© 2026 Cognex Backup Tool"
        );
        panel.add(new JScrollPane(contentArea), BorderLayout.CENTER);

        // Bottom buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton usageBtn = new JButton("使用说明");
        JButton closeBtn = new JButton("关闭");

        usageBtn.addActionListener(e -> showUsage());
        closeBtn.addActionListener(e -> dispose());

        btnPanel.add(usageBtn);
        btnPanel.add(closeBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        add(panel);
        getRootPane().setDefaultButton(closeBtn);
    }

    private void showUsage() {
        UsageDialog usageDialog = new UsageDialog((JFrame) getParent());
        usageDialog.setVisible(true);
    }
}
