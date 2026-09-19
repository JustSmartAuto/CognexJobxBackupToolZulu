package com.cognex.backup.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class UsageDialog extends JDialog {

    public UsageDialog(JFrame parent) {
        super(parent, "使用说明", true);
        setSize(680, 600);
        setLocationRelativeTo(parent);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel("软件使用说明", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);

        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setContentType("text/html");
        textPane.setText(getHtmlContent());
        textPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textPane);
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(closeBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        add(panel);
        getRootPane().setDefaultButton(closeBtn);
    }

    private String getHtmlContent() {
        return "<html>" +
                "<body style='font-family: Microsoft YaHei, SimSun, sans-serif; font-size: 13px; line-height: 1.6;'>" +
                "<h2 style='color: #2c3e50;'>一、快速开始</h2>" +
                "<ol>" +
                "<li>点击 <b>【添加相机】</b> 按钮，填写相机信息</li>" +
                "<li>选中相机，点击 <b>【立即备份】</b> 开始备份</li>" +
                "<li>备份文件将保存到指定目录的 <b>{相机名称}/{yyyyMMddHHmmss}/</b> 下</li>" +
                "</ol>" +

                "<h2 style='color: #2c3e50;'>二、相机配置说明</h2>" +
                "<table border='1' cellpadding='6' cellspacing='0' style='border-collapse: collapse; width: 100%;'>" +
                "<tr style='background-color: #f0f0f0;'><th>字段</th><th>说明</th><th>默认值</th></tr>" +
                "<tr><td>相机名称</td><td>自定义标识名称，用于备份文件夹命名</td><td>-</td></tr>" +
                "<tr><td>IP 地址</td><td>相机的网络 IP 地址</td><td>-</td></tr>" +
                "<tr><td>FTP 端口</td><td>相机 FTP 服务端口。FTP 默认 21，FTPS 默认 990</td><td>21</td></tr>" +
                "<tr><td>FTP 用户名</td><td>登录 FTP 的用户名</td><td>admin</td></tr>" +
                "<tr><td>FTP 密码</td><td>登录 FTP 的密码</td><td>空</td></tr>" +
                "<tr><td>备份目录</td><td>本地保存备份的根目录，留空则使用 JAR 同目录</td><td>JAR 同目录</td></tr>" +
                "<tr><td>使用 FTPS</td><td>启用 FTP over TLS/SSL 加密连接</td><td>否</td></tr>" +
                "<tr><td>信任所有 TLS 证书</td><td>信任自签名/不受信任的证书（FTPS 模式下有效）</td><td>是</td></tr>" +
                "</table>" +

                "<h2 style='color: #2c3e50;'>三、按钮功能说明</h2>" +
                "<ul>" +
                "<li><b>添加相机</b> - 新增一台相机配置</li>" +
                "<li><b>编辑相机</b> - 修改选中相机的配置</li>" +
                "<li><b>删除相机</b> - 删除选中相机的配置</li>" +
                "<li><b>立即备份</b> - 备份选中相机的 jobx 文件</li>" +
                "<li><b>备份全部</b> - 依次备份所有配置的相机</li>" +
                "<li><b>打开备份目录</b> - 用文件管理器打开备份文件夹</li>" +
                "</ul>" +

                "<h2 style='color: #2c3e50;'>四、备份文件结构</h2>" +
                "<pre style='background-color: #f5f5f5; padding: 10px; border-radius: 4px;'>" +
                "备份目录/\n" +
                "├── 相机名称A/\n" +
                "│   └── 20260604213000/\n" +
                "│       ├── job.jobx\n" +
                "│       └── job.jobx.sig\n" +
                "└── 相机名称B/\n" +
                "    └── 20260604213500/\n" +
                "        ├── job.jobx\n" +
                "        └── job.jobx.sig\n" +
                "</pre>" +

                "<h2 style='color: #2c3e50;'>五、FTP / FTPS 配置指南</h2>" +
                "<ul>" +
                "<li><b>普通 FTP（默认）</b>：不加密传输，端口通常为 <b>21</b></li>" +
                "<li><b>FTPS（推荐）</b>：TLS/SSL 加密传输，端口通常为 <b>990</b> 或 <b>21</b></li>" +
                "<li>Cognex 相机若启用了 <b>FTP over SSL/TLS</b>，请勾选 <b>【使用 FTPS】</b></li>" +
                "<li>相机使用自签名证书时，保持 <b>【信任所有 TLS 证书】</b> 勾选即可正常连接</li>" +
                "</ul>" +

                "<h2 style='color: #2c3e50;'>六、注意事项</h2>" +
                "<ul>" +
                "<li>配置文件 <b>backup-config.json</b> 与 JAR 文件放在同一目录，会自动创建</li>" +
                "<li>仅下载 <b>.jobx</b> 和 <b>.jobx.sig</b> 文件，其他文件被过滤</li>" +
                "<li>备份过程中请勿关闭程序，等待提示完成</li>" +
                "<li>建议定期清理旧备份以释放磁盘空间</li>" +
                "</ul>" +

                "<h2 style='color: #2c3e50;'>七、常见问题</h2>" +
                "<p><b>Q: 提示 \"Connection closed without indication\" 怎么办？</b><br>" +
                "A: 这通常意味着相机端主动关闭了连接。请检查：<br>" +
                "&nbsp;&nbsp;1. 相机是否已启用 FTP/FTPS 服务<br>" +
                "&nbsp;&nbsp;2. 端口是否正确（FTPS 常用 990）<br>" +
                "&nbsp;&nbsp;3. 相机是否要求加密连接，尝试勾选 <b>【使用 FTPS】</b><br>" +
                "&nbsp;&nbsp;4. 网络/防火墙是否阻止了连接</p>" +
                "<p><b>Q: 提示 TLS/SSL 握手失败怎么办？</b><br>" +
                "A: 请确认勾选了 <b>【信任所有 TLS 证书】</b>，或检查相机的 TLS 配置。</p>" +
                "<p><b>Q: 登录失败怎么办？</b><br>" +
                "A: 请确认 FTP 用户名和密码正确，Cognex 相机默认用户名为 admin，密码可能为空。</p>" +
                "<p><b>Q: 备份目录留空会保存到哪里？</b><br>" +
                "A: 会自动保存到 JAR 文件所在的目录。</p>" +

                "</body></html>";
    }
}
