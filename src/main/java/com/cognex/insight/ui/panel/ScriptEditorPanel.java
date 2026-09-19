package com.cognex.insight.ui.panel;

import com.cognex.insight.cogsocket.InSightConnection;
import com.cognex.insight.script.JsScriptEngine;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;

/**
 * Script editor panel with RSyntaxTextArea for JavaScript (QuickJS) scripting.
 * Provides syntax highlighting, execute/run functionality, output display,
 * and an interactive JavaScript script input area.
 */
public class ScriptEditorPanel extends JPanel {

    private RSyntaxTextArea textArea;
    private RSyntaxTextArea interactiveArea;
    private JTextArea outputArea;
    private JButton btnRun;
    private JButton btnClear;
    private JButton btnReset;
    private JToggleButton btnWrap;
    private JsScriptEngine engine;
    private InSightConnection connection;

    public ScriptEditorPanel(InSightConnection connection) {
        this.connection = connection;
        this.engine = new JsScriptEngine(connection);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("JavaScript 脚本编辑器 (QuickJS)"));

        // Toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        btnRun = new JButton("运行 (F5)");
        btnRun.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btnRun.addActionListener(e -> runScript());
        btnRun.setEnabled(false);

        btnClear = new JButton("清空输出");
        btnClear.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btnClear.addActionListener(e -> outputArea.setText(""));

        btnReset = new JButton("重置环境");
        btnReset.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btnReset.addActionListener(e -> {
            try {
                engine.reset();
                appendOutput("JS 环境已重置\n");
            } catch (Exception ex) {
                appendOutput("[错误] " + ex.getMessage() + "\n");
            }
        });

        btnWrap = new JToggleButton("自动换行");
        btnWrap.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btnWrap.addActionListener(e -> {
            textArea.setLineWrap(btnWrap.isSelected());
            interactiveArea.setLineWrap(btnWrap.isSelected());
        });

        toolBar.add(btnRun);
        toolBar.addSeparator();
        toolBar.add(btnClear);
        toolBar.add(btnReset);
        toolBar.addSeparator();
        toolBar.add(btnWrap);

        add(toolBar, BorderLayout.NORTH);

        // Main vertical split: editor+output on top, interactive on bottom
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplitPane.setResizeWeight(0.75);

        // Editor + Output split pane
        JSplitPane editorOutputSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        editorOutputSplitPane.setResizeWeight(0.65);

        // Editor
        textArea = new RSyntaxTextArea(10, 60);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        textArea.setTabSize(4);
        textArea.setCaretPosition(0);
        textArea.requestFocusInWindow();
        textArea.setMarkOccurrences(true);
        textArea.setClearWhitespaceLinesEnabled(false);
        textArea.setEOLMarkersVisible(false);
        textArea.setWhitespaceVisible(false);

        // Default script template
        textArea.setText(
            "// JavaScript 脚本示例：读写电子表格单元格\n" +
            "// spreadsheet.setCellValue(\"A3\", 1)\n" +
            "// let value = spreadsheet.getCellValue(\"A3\")\n" +
            "// console.log(\"A3 = \" + value)\n" +
            "\n" +
            "// 更多 API:\n" +
            "// spreadsheet.setCellExpression(\"A3\", \"1+1\")\n" +
            "// let expr = spreadsheet.getCellExpression(\"A3\")\n" +
            "// spreadsheet.trigger()\n" +
            "// spreadsheet.setOnline(true)\n" +
            "// spreadsheet.setLiveMode(true)\n"
        );

        // Key binding for F5
        InputMap im = textArea.getInputMap();
        ActionMap am = textArea.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "runScript");
        am.put("runScript", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runScript();
            }
        });

        RTextScrollPane editorScroll = new RTextScrollPane(textArea);
        editorScroll.setBorder(BorderFactory.createTitledBorder("编辑器"));
        editorOutputSplitPane.setTopComponent(editorScroll);

        // Output area
        outputArea = new JTextArea(4, 0);
        outputArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("输出"));
        editorOutputSplitPane.setBottomComponent(outputScroll);

        mainSplitPane.setTopComponent(editorOutputSplitPane);

        // Interactive JavaScript script area
        interactiveArea = new RSyntaxTextArea(3, 60);
        interactiveArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        interactiveArea.setCodeFoldingEnabled(false);
        interactiveArea.setAntiAliasingEnabled(true);
        interactiveArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        interactiveArea.setTabSize(4);
        interactiveArea.setMarkOccurrences(true);
        interactiveArea.setClearWhitespaceLinesEnabled(false);
        interactiveArea.setEOLMarkersVisible(false);
        interactiveArea.setWhitespaceVisible(false);
        interactiveArea.setLineWrap(true);
        interactiveArea.setWrapStyleWord(true);

        // Key binding for interactive area: Enter to execute, Shift+Enter for new line
        InputMap iim = interactiveArea.getInputMap();
        ActionMap iam = interactiveArea.getActionMap();
        iim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "executeInteractive");
        iam.put("executeInteractive", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeInteractive();
            }
        });
        // Shift+Enter inserts newline
        iim.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insertNewline");
        iam.put("insertNewline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                interactiveArea.insert("\n", interactiveArea.getCaretPosition());
            }
        });

        RTextScrollPane interactiveScroll = new RTextScrollPane(interactiveArea);
        interactiveScroll.setBorder(BorderFactory.createTitledBorder("JavaScript 交互式脚本 (Enter 执行, Shift+Enter 换行)"));
        mainSplitPane.setBottomComponent(interactiveScroll);

        add(mainSplitPane, BorderLayout.CENTER);
    }

    private void runScript() {
        String script = textArea.getText();
        if (script.trim().isEmpty()) {
            appendOutput("[错误] 脚本为空\n");
            return;
        }

        appendOutput("[运行] ----------\n");
        btnRun.setEnabled(false);

        SwingWorker<JsScriptEngine.ScriptResult, Void> worker = new SwingWorker<JsScriptEngine.ScriptResult, Void>() {
            @Override
            protected JsScriptEngine.ScriptResult doInBackground() {
                return engine.execute(script, true);
            }

            @Override
            protected void done() {
                try {
                    JsScriptEngine.ScriptResult result = get();
                    if (!result.output.isEmpty()) {
                        appendOutput(result.output);
                    }
                    if (result.success) {
                        appendOutput("[结果] " + result.result + "\n");
                        appendOutput("[完成] 执行成功\n\n");
                    } else {
                        appendOutput("[错误] " + result.result + "\n\n");
                    }
                } catch (Exception e) {
                    appendOutput("[错误] " + e.getMessage() + "\n\n");
                } finally {
                    btnRun.setEnabled(connection != null && connection.isConnected());
                }
            }
        };
        worker.execute();
    }

    private void executeInteractive() {
        String script = interactiveArea.getText().trim();
        if (script.isEmpty()) {
            return;
        }

        appendOutput("[交互] > " + script.replace("\n", "\n       ") + "\n");
        interactiveArea.setText("");

        SwingWorker<JsScriptEngine.ScriptResult, Void> worker = new SwingWorker<JsScriptEngine.ScriptResult, Void>() {
            @Override
            protected JsScriptEngine.ScriptResult doInBackground() {
                return engine.execute(script);
            }

            @Override
            protected void done() {
                try {
                    JsScriptEngine.ScriptResult result = get();
                    if (!result.output.isEmpty()) {
                        appendOutput(result.output);
                    }
                    if (result.success) {
                        appendOutput("[结果] " + result.result + "\n\n");
                    } else {
                        appendOutput("[错误] " + result.result + "\n\n");
                    }
                } catch (Exception e) {
                    appendOutput("[错误] " + e.getMessage() + "\n\n");
                }
            }
        };
        worker.execute();
    }

    private void appendOutput(String text) {
        // 确保文本使用 UTF-8 编码，替换可能的乱码字符
        String safeText = text;
        if (safeText != null) {
            // 将文本转为字节再按 UTF-8 解码，确保编码一致性
            safeText = new String(safeText.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        final String finalText = safeText;
        SwingUtilities.invokeLater(() -> {
            outputArea.append(finalText);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    public void setConnection(InSightConnection connection) {
        this.connection = connection;
        // 释放旧引擎并重建（连接变化后旧的 spreadsheet API 持有过期连接）
        if (this.engine != null) {
            this.engine.close();
        }
        this.engine = new JsScriptEngine(connection);
        boolean connected = connection != null && connection.isConnected();
        btnRun.setEnabled(connected);
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        boolean connected = connection != null && connection.isConnected();
        btnRun.setEnabled(enabled && connected);
    }

    // Configuration getters/setters

    public String getScriptText() {
        return textArea.getText();
    }

    public void setScriptText(String text) {
        textArea.setText(text);
    }

    public boolean isLineWrap() {
        return btnWrap.isSelected();
    }

    public void setLineWrap(boolean wrap) {
        btnWrap.setSelected(wrap);
        textArea.setLineWrap(wrap);
        interactiveArea.setLineWrap(wrap);
    }
}
