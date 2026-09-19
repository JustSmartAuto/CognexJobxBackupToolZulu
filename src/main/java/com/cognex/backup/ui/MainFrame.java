package com.cognex.backup.ui;

import com.cognex.export.ExportTabPanel;
import com.cognex.insight.ui.panel.EditorTabPanel;
import com.cognex.backup.util.AppIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 主窗口：JTabbedPane 承载三个工具标签页。
 * 1. Jobx 备份工具（原备份功能）
 * 2. Jobx 导出工具（移植自 Go 版导出工具：FTP 枚举 + CogSocket 读单元格 + xlsx）
 * 3. Jobx 编辑器（移植自 java-insight-hmi：实时图像/表格编辑/QuickJS 脚本）
 */
public class MainFrame extends JFrame {

    private EditorTabPanel editorTab;

    public MainFrame() {
        setTitle("Cognex Jobx 工具箱 - 备份 / 导出 / 编辑");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 860);
        setMinimumSize(new Dimension(1024, 700));
        setLocationRelativeTo(null);

        initUI();
        AppIcons.applyTo(this);

        // 关闭窗口前保存编辑器配置并断开 HMI 连接
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    if (editorTab != null) {
                        editorTab.shutdown();
                    }
                } catch (Exception ignored) {
                }
                dispose();
                System.exit(0);
            }
        });
    }

    private void initUI() {
        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        BackupTabPanel backupTab = new BackupTabPanel(this);
        ExportTabPanel exportTab = new ExportTabPanel();
        tabbedPane.addTab("Jobx 备份工具", backupTab);
        tabbedPane.addTab("Jobx 导出工具", exportTab);

        // 编辑器标签页构造失败（如 QuickJS 本地库不兼容）时降级为错误提示页，
        // 不影响备份与导出标签页
        try {
            editorTab = new EditorTabPanel(this);
            tabbedPane.addTab("Jobx 编辑器", editorTab);
        } catch (Throwable t) {
            editorTab = null;
            JPanel errorPanel = new JPanel(new BorderLayout());
            JTextArea msg = new JTextArea("Jobx 编辑器初始化失败：\n"
                    + (t.getMessage() != null ? t.getMessage() : t.toString())
                    + "\n\n备份与导出工具仍可正常使用。");
            msg.setEditable(false);
            msg.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            msg.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            errorPanel.add(msg, BorderLayout.CENTER);
            tabbedPane.addTab("Jobx 编辑器", errorPanel);
        }

        // 标签页带快捷键：Ctrl+1/2/3
        tabbedPane.setMnemonicAt(0, '1');
        tabbedPane.setMnemonicAt(1, '2');
        tabbedPane.setMnemonicAt(2, '3');

        add(tabbedPane, BorderLayout.CENTER);
    }
}
