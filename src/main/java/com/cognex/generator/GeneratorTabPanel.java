package com.cognex.generator;

import com.cognex.backup.util.Icons;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 第5标签页：Jobx 生成器。
 * 5th tab: Jobx Generator.
 *
 * 通过 JavaScript 编写视觉逻辑（jobx.* API 构建单元格/sheet），生成 .jobx / .cxdx / .xlsx。
 * 不需要连接相机 / No camera connection required.
 *
 * 已知问题：PatMax/Caliper 等训练型工具表达式可写入，但 saved 字段恒为 null，
 *           装入相机后这些 cell 可能无法直接运行（需在相机侧重新训练）。
 */
public class GeneratorTabPanel extends JPanel {

    private final JFrame owner;
    private RSyntaxTextArea textArea;
    private JTextArea logArea;
    private GeneratorScriptEngine engine;
    private File scriptFile;  // 持久化脚本文本 / persisted script text
    private File lastDir;     // 文件选择器上次目录 / file chooser last dir

    private static final String DEFAULT_SCRIPT =
            "// Jobx 生成器示例：存在检测（Presence Detection）\n" +
            "// 通过 JS 构建 cell/sheet，运行（F5）后自动生成 .jobx / .cxdx / .xlsx。\n" +
            "// 输出默认在 jar 目录下 generated/ 子目录，文件名 {base}_yyyyMMdd_HHmmss.<ext>。\n\n" +
            "// 1. 选择/创建 sheet（不存在则自动创建）\n" +
            "jobx.sheet(\"Inspection\");\n\n" +
            "// 2. 写入单元格表达式（Cognex In-Sight 表达式语法）\n" +
            "// A0: 采集图像\n" +
            "jobx.setCell(\"A0\", \"AcquireImage()\");\n" +
            "// B0: 在 A0 上做存在检测，阈值 0.5\n" +
            "jobx.setCell(\"B0\", \"Presence(A0, 0.5)\");\n" +
            "// C0: 输出 OK/NG 判定\n" +
            "jobx.setCell(\"C0\", \"IF(B0>0,\\\"OK\\\",\\\"NG\\\")\");\n\n" +
            "// 3. 可选：带名称/批注/样式写单元格\n" +
            "// jobx.setCell(\"C0\", {\n" +
            "//   expression: \"IF(B0>0,\\\"OK\\\",\\\"NG\\\")\",\n" +
            "//   name: \"Result\",\n" +
            "//   comment: \"Pass when presence > 0\",\n" +
            "//   cellStyle: \".cell { background-color:rgba(0,128,0,1.0); color:rgba(255,255,255,1.0); }\"\n" +
            "// });\n\n" +
            "// 4. 可选：设置作业元数据\n" +
            "// jobx.meta({ JobVersion: \"22.2\", JobType: \"Spreadsheet\" });\n\n" +
            "// 5. 可选：从已有 .jobx/.cxdx 加载为模板（在已有 cell 基础上修改）\n" +
            "// jobx.load(\"D:/path/to/template.jobx\");\n\n" +
            "// 6. 可选：自定义输出参数（不调用则使用默认 format=\"all\"）\n" +
            "// jobx.output({ format: \"all\", outDir: \"D:/out\", baseName: \"presence\", noSig: false });\n" +
            "//    format: \"jobx\" | \"cxdx\" | \"xlsx\" | \"all\"\n";

    public GeneratorTabPanel(JFrame owner) {
        super(new BorderLayout());
        this.owner = owner;
        this.scriptFile = new File(getJarDir(), "generator-script.js");
        this.lastDir = null;
        initUI();
    }

    private void initUI() {
        buildToolbar();
        buildCenter();
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    private void buildToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton openBtn = new JButton("打开脚本");
        openBtn.setToolTipText("加载 .js 脚本到编辑器");
        Icons.setIcon(openBtn, FontAwesomeSolid.FOLDER_OPEN, 14);
        openBtn.addActionListener(this::onOpenScript);
        toolbar.add(openBtn);

        JButton saveBtn = new JButton("保存脚本");
        saveBtn.setToolTipText("将编辑器内容另存为 .js 文件");
        Icons.setIcon(saveBtn, FontAwesomeSolid.SAVE, 14);
        saveBtn.addActionListener(this::onSaveScript);
        toolbar.add(saveBtn);

        toolbar.addSeparator();

        JButton runBtn = new JButton("运行 (F5)");
        runBtn.setToolTipText("执行脚本，自动生成 .jobx/.cxdx/.xlsx 到 jar 目录下 generated/");
        Icons.setIcon(runBtn, FontAwesomeSolid.PLAY, 14);
        runBtn.addActionListener(e -> runScript());
        toolbar.add(runBtn);

        JButton resetBtn = new JButton("重置环境");
        resetBtn.setToolTipText("销毁 JS 上下文，丢弃所有变量/函数");
        Icons.setIcon(resetBtn, FontAwesomeSolid.SYNC, 14);
        resetBtn.addActionListener(e -> {
            if (engine != null) {
                engine.reset();
                log("JS 环境已重置");
            }
        });
        toolbar.add(resetBtn);

        toolbar.addSeparator();

        JButton clearBtn = new JButton("清空日志");
        Icons.setIcon(clearBtn, FontAwesomeSolid.ERASER, 14);
        clearBtn.addActionListener(e -> logArea.setText(""));
        toolbar.add(clearBtn);

        toolbar.addSeparator();

        JLabel hint = new JLabel(" 已知问题：PatMax/Caliper 等训练型工具表达式可写，但 saved 字段恒 null");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        hint.setForeground(new Color(180, 100, 0));
        Icons.setHintIcon(hint);
        toolbar.add(hint);

        add(toolbar, BorderLayout.NORTH);
    }

    private void buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.65);

        // 编辑器 / editor
        textArea = new RSyntaxTextArea(12, 80);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        textArea.setTabSize(4);
        textArea.setMarkOccurrences(true);
        textArea.setEOLMarkersVisible(false);
        textArea.setWhitespaceVisible(false);

        // 加载持久化脚本或默认模板 / load persisted script or default template
        String initialScript = loadScript();
        textArea.setText(initialScript);
        textArea.setCaretPosition(0);

        // F5 运行快捷键 / F5 to run
        InputMap im = textArea.getInputMap();
        ActionMap am = textArea.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "runScript");
        am.put("runScript", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { runScript(); }
        });

        RTextScrollPane editorScroll = new RTextScrollPane(textArea);
        editorScroll.setBorder(BorderFactory.createTitledBorder("JavaScript 脚本编辑器 (QuickJS)"));
        split.setTopComponent(editorScroll);

        // 日志 / log
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setText("提示：编辑脚本后按 F5 运行，自动生成 .jobx / .cxdx / .xlsx。\n"
                + "默认输出目录: jar 目录下 generated/；文件名规则 {base}_yyyyMMdd_HHmmss.<ext>。\n\n");

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("输出"));
        split.setBottomComponent(logScroll);

        add(split, BorderLayout.CENTER);
    }

    // ======================== 脚本运行 ========================

    private void runScript() {
        String script = textArea.getText();
        // 后台执行 / run on background thread
        Thread t = new Thread(() -> {
            try {
                if (engine == null) {
                    File outDir = new File(getJarDir(), "generated");
                    engine = new GeneratorScriptEngine(outDir, "generated", this::log);
                }
                log("---- 开始执行 ----");
                GeneratorScriptEngine.ScriptResult r = engine.execute(script);
                if (!r.output.isEmpty()) log(r.output);
                log(r.success ? ("[成功] " + r.message) : ("[错误] " + r.message));
            } catch (Throwable t2) {
                log("[异常] " + t2.getClass().getSimpleName() + ": " + t2.getMessage());
            }
        }, "Generator-Script-Worker");
        t.setDaemon(true);
        t.start();
    }

    // ======================== 脚本持久化 ========================

    private String loadScript() {
        if (!scriptFile.exists()) return DEFAULT_SCRIPT;
        try (FileInputStream fis = new FileInputStream(scriptFile)) {
            byte[] data = new byte[(int) scriptFile.length()];
            int off = 0;
            while (off < data.length) {
                int n = fis.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            return new String(data, 0, off, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return DEFAULT_SCRIPT;
        }
    }

    /** 关闭窗口时调用，持久化脚本到 jar 目录 / Called on window close, persists script. */
    public void saveScriptOnExit() {
        saveScriptTo(scriptFile);
    }

    private void saveScriptTo(File f) {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(textArea.getText().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log("脚本保存失败: " + e.getMessage());
        }
    }

    // ======================== 文件选择 ========================

    private void onOpenScript(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(lastDir);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "JavaScript 脚本 (*.js)", "js"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (f == null) return;
        try {
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            textArea.setText(new String(data, StandardCharsets.UTF_8));
            textArea.setCaretPosition(0);
            lastDir = f.getParentFile();
            log("已加载: " + f.getAbsolutePath());
        } catch (IOException ex) {
            log("加载失败: " + ex.getMessage());
        }
    }

    private void onSaveScript(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(lastDir);
        chooser.setSelectedFile(new File("generator-script.js"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "JavaScript 脚本 (*.js)", "js"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (f == null) return;
        if (!f.getName().toLowerCase().endsWith(".js")) {
            f = new File(f.getParentFile(), f.getName() + ".js");
        }
        saveScriptTo(f);
        log("已保存: " + f.getAbsolutePath());
        lastDir = f.getParentFile();
    }

    // ======================== 工具 ========================

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /** 获取 jar 所在目录（用于配置与默认输出）。 */
    private File getJarDir() {
        try {
            String jarPath = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            File jarDir = jarFile.getParentFile();
            if (jarDir == null || !jarDir.exists()) return new File(".");
            return jarDir;
        } catch (Exception e) {
            return new File(".");
        }
    }
}
