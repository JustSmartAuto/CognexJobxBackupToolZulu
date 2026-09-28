package com.cognex.parser;

import com.cognex.backup.util.Icons;
import com.cognex.parser.JobxParser.ParsedJob;
import com.cognex.parser.JobxParser.ParsedSheet;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 第4标签页：Jobx 解析器。
 * 4th tab: Jobx Parser.
 *
 * 功能：拖入或打开 .jobx / .cxdx 文件 → 离线解析 → 导出 xlsx（带时间戳后缀）到源文件同目录。
 * 不需要连接相机 / Pure offline parsing; no camera connection required.
 */
public class ParserTabPanel extends JPanel {

    private final JFrame owner;
    private JTextArea logArea;  // 在 buildCenter() 中初始化 / initialized in buildCenter()
    private File lastDir;  // 上次打开目录，下次默认进入 / remember last dir for next open

    public ParserTabPanel(JFrame owner) {
        super(new BorderLayout());
        this.owner = owner;
        this.lastDir = null;

        buildToolbar();
        buildCenter();
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    private void buildToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton openBtn = new JButton("打开 jobx/cxdx 文件");
        openBtn.setToolTipText("选择本地 .jobx 或 .cxdx 文件，自动导出为同目录下带时间戳的 xlsx");
        Icons.setIcon(openBtn, FontAwesomeSolid.FOLDER_OPEN, 14);
        openBtn.addActionListener(this::onOpen);
        toolbar.add(openBtn);

        toolbar.addSeparator();

        JButton clearBtn = new JButton("清空日志");
        Icons.setIcon(clearBtn, FontAwesomeSolid.ERASER, 14);
        clearBtn.addActionListener(e -> logArea.setText(""));
        toolbar.add(clearBtn);

        toolbar.addSeparator();

        JLabel hint = new JLabel(" 也可将 .jobx / .cxdx 文件直接拖入下方区域");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        Icons.setHintIcon(hint);
        toolbar.add(hint);

        add(toolbar, BorderLayout.NORTH);
    }

    private void buildCenter() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setText("提示：点击左上角\"打开 jobx/cxdx 文件\"，或将 .jobx / .cxdx 文件拖入本区域。\n"
                + "解析完成后会在源文件同目录生成 原文件名_yyyyMMdd_HHmmss.xlsx。\n\n");

        JScrollPane scroll = new JScrollPane(logArea);
        // 拖拽支持 / drag-drop support
        scroll.setTransferHandler(new FileDropHandler());
        logArea.setTransferHandler(new FileDropHandler());
        add(scroll, BorderLayout.CENTER);
    }

    private void onOpen(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(lastDir);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Cognex 作业文件 (*.jobx, *.cxdx)", "jobx", "cxdx"));
        chooser.setMultiSelectionEnabled(true);
        int ret = chooser.showOpenDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION) return;
        File[] files = chooser.getSelectedFiles();
        if (files == null || files.length == 0) return;
        if (files[0].getParentFile() != null) lastDir = files[0].getParentFile();
        processFiles(files);
    }

    private void processFiles(File[] files) {
        Thread t = new Thread(() -> {
            for (File f : files) {
                processOne(f);
            }
            log("---- 全部完成 ----");
        }, "JobxParser-Worker");
        t.setDaemon(true);
        t.start();
    }

    private void processOne(File f) {
        log("开始处理: " + f.getAbsolutePath());
        try {
            ParsedJob job = JobxParser.parse(f);
            int total = 0;
            for (ParsedSheet sh : job.sheets) total += sh.cells.size();
            StringBuilder sb = new StringBuilder();
            sb.append("  解析完成: ").append(job.sheets.size()).append(" 个 sheet");
            if (!job.meta.isEmpty()) {
                sb.append("；元数据: ");
                boolean first = true;
                for (java.util.Map.Entry<String, String> e : job.meta.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(e.getKey()).append("=").append(e.getValue());
                    first = false;
                }
            }
            sb.append("，共 ").append(total).append(" 个单元格");
            log(sb.toString());

            File out = ParserXlsxExporter.export(job, new Date());
            log("  已导出: " + out.getAbsolutePath());
        } catch (Exception ex) {
            log("  失败: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ======================== 拖拽 / Drag-drop ========================

    private class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                Transferable t = support.getTransferable();
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                List<File> accepted = new ArrayList<>();
                for (File f : files) {
                    String n = f.getName().toLowerCase();
                    if (n.endsWith(".jobx") || n.endsWith(".cxdx")) {
                        accepted.add(f);
                    }
                }
                if (accepted.isEmpty()) {
                    log("拖入失败: 未识别到 .jobx / .cxdx 文件");
                    return false;
                }
                if (accepted.get(0).getParentFile() != null) {
                    lastDir = accepted.get(0).getParentFile();
                }
                processFiles(accepted.toArray(new File[0]));
                return true;
            } catch (Exception ex) {
                log("拖入处理失败: " + ex.getMessage());
                return false;
            }
        }
    }
}
