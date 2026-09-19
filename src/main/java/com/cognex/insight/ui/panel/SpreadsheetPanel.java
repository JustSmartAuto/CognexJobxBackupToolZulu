package com.cognex.insight.ui.panel;

import com.cognex.insight.cogsocket.InSightConnection;
import com.cognex.insight.model.CellResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SpreadsheetPanel extends JPanel {
    private static final String[] ALL_COLUMNS = {"位置", "名称", "类型", "数值", "表达式", "错误"};
    private static final int[] DEFAULT_WIDTHS = {60, 120, 100, 120, 200, 40};

    private JTable table;
    private DefaultTableModel model;
    private java.util.List<CellResult> cellResults = new ArrayList<>();
    private Set<String> visibleColumns = new HashSet<>(Arrays.asList(ALL_COLUMNS));
    private Map<String, Integer> columnIndexMap = new HashMap<>();

    // Callbacks for cell editing
    private BiConsumer<String, Object> onSetCellValue;
    private BiConsumer<String, String> onSetCellExpression;
    private Supplier<String> onGetCellExpression;
    private Supplier<Boolean> canEditCells;
    private Supplier<Boolean> isFullAccess;
    private Consumer<String> onLogMessage;
    private Consumer<String> onShowError;

    public SpreadsheetPanel() {
        setLayout(new BorderLayout());
        setBackground(SystemColor.control);

        // Build column index map
        for (int i = 0; i < ALL_COLUMNS.length; i++) {
            columnIndexMap.put(ALL_COLUMNS[i], i);
        }

        model = new DefaultTableModel(ALL_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        table.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(true);
        table.setGridColor(new Color(200, 200, 200));

        // Add double-click listener for cell editing
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < cellResults.size()) {
                        handleCellDoubleClick(row);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("电子表格结果"));
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setCallbacks(
            BiConsumer<String, Object> onSetCellValue,
            BiConsumer<String, String> onSetCellExpression,
            Supplier<String> onGetCellExpression,
            Supplier<Boolean> canEditCells,
            Supplier<Boolean> isFullAccess,
            Consumer<String> onLogMessage,
            Consumer<String> onShowError) {
        this.onSetCellValue = onSetCellValue;
        this.onSetCellExpression = onSetCellExpression;
        this.onGetCellExpression = onGetCellExpression;
        this.canEditCells = canEditCells;
        this.isFullAccess = isFullAccess;
        this.onLogMessage = onLogMessage;
        this.onShowError = onShowError;
    }

    /**
     * 设置列的可见性。
     *
     * @param column  列名
     * @param visible 是否可见
     */
    public void setColumnVisible(String column, boolean visible) {
        if (!columnIndexMap.containsKey(column)) {
            return;
        }
        if (visible) {
            visibleColumns.add(column);
        } else {
            visibleColumns.remove(column);
        }
        applyColumnVisibility();
    }

    /**
     * 批量设置可见列。
     *
     * @param columns 可见列名集合
     */
    public void setVisibleColumns(Set<String> columns) {
        visibleColumns.clear();
        for (String col : ALL_COLUMNS) {
            if (columns != null && columns.contains(col)) {
                visibleColumns.add(col);
            }
        }
        applyColumnVisibility();
    }

    /**
     * 获取当前可见列集合。
     */
    public Set<String> getVisibleColumns() {
        return new HashSet<>(visibleColumns);
    }

    /**
     * 获取所有可用列名。
     */
    public String[] getAvailableColumns() {
        return ALL_COLUMNS.clone();
    }

    /**
     * 应用列可见性到表格。
     */
    private void applyColumnVisibility() {
        for (int i = 0; i < ALL_COLUMNS.length; i++) {
            String colName = ALL_COLUMNS[i];
            TableColumn column = table.getColumnModel().getColumn(i);
            if (visibleColumns.contains(colName)) {
                column.setMinWidth(15);
                column.setMaxWidth(Integer.MAX_VALUE);
                column.setPreferredWidth(DEFAULT_WIDTHS[i]);
                column.setWidth(DEFAULT_WIDTHS[i]);
                column.setResizable(true);
            } else {
                column.setMinWidth(0);
                column.setMaxWidth(0);
                column.setWidth(0);
                column.setResizable(false);
            }
        }
        revalidate();
        repaint();
    }

    private void handleCellDoubleClick(int row) {
        if (canEditCells == null || !canEditCells.get()) {
            if (onShowError != null) {
                onShowError.accept("无法编辑单元格: 相机必须处于离线状态且编辑器未连接。");
            }
            return;
        }

        CellResult cell = cellResults.get(row);
        String cellLocation = cell.location;

        // Show popup menu to choose edit value or expression
        JPopupMenu popup = new JPopupMenu();
        popup.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        JMenuItem menuEditValue = new JMenuItem("编辑数值...");
        menuEditValue.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        menuEditValue.addActionListener(e -> editCellValue(cellLocation, cell.data));

        JMenuItem menuEditExpression = new JMenuItem("编辑表达式...");
        menuEditExpression.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        menuEditExpression.addActionListener(e -> editCellExpression(cellLocation));
        // Only full access can edit expressions
        menuEditExpression.setEnabled(isFullAccess != null && isFullAccess.get());

        popup.add(menuEditValue);
        popup.add(menuEditExpression);

        // Show popup at mouse location
        Rectangle rect = table.getCellRect(row, 0, true);
        popup.show(table, rect.x + rect.width / 2, rect.y + rect.height / 2);
    }

    private void editCellValue(String cellLocation, Object currentValue) {
        String current = currentValue != null ? currentValue.toString() : "";
        String input = JOptionPane.showInputDialog(
            SwingUtilities.getWindowAncestor(this),
            "设置单元格 " + cellLocation + " 的数值:",
            current
        );
        if (input != null && onSetCellValue != null) {
            Object value = tryParseNumber(input);
            onSetCellValue.accept(cellLocation, value);
        }
    }

    private void editCellExpression(String cellLocation) {
        if (onGetCellExpression == null) return;

        String currentExpr = onGetCellExpression.get();
        if (currentExpr == null) currentExpr = "";

        String input = JOptionPane.showInputDialog(
            SwingUtilities.getWindowAncestor(this),
            "设置单元格 " + cellLocation + " 的表达式:",
            currentExpr
        );
        if (input != null && onSetCellExpression != null) {
            onSetCellExpression.accept(cellLocation, input);
        }
    }

    private Object tryParseNumber(String s) {
        try {
            if (s.contains(".")) {
                return Double.parseDouble(s);
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    public void clear() {
        cellResults.clear();
        model.setRowCount(0);
    }

    public void updateResults(JsonObject results) {
        updateResults(results, null);
    }

    /**
     * 更新电子表格结果，支持传入表达式映射。
     *
     * @param results   HmiResult JSON 对象
     * @param expressions Map<cellLocation, expression>，可为 null
     */
    public void updateResults(JsonObject results, java.util.Map<String, String> expressions) {
        cellResults.clear();
        model.setRowCount(0);

        if (results == null || !results.has("cells")) {
            return;
        }

        JsonElement cellsElement = results.get("cells");
        if (!cellsElement.isJsonArray()) {
            return;
        }

        JsonArray cells = cellsElement.getAsJsonArray();
        for (JsonElement e : cells) {
            if (!e.isJsonObject()) continue;
            JsonObject obj = e.getAsJsonObject();
            CellResult cr = new CellResult();
            cr.type = obj.has("$type") ? obj.get("$type").getAsString() : "";
            cr.location = obj.has("location") ? obj.get("location").getAsString() : "";
            cr.name = obj.has("name") ? obj.get("name").getAsString() : "";
            cr.error = obj.has("error") && obj.get("error").getAsBoolean();
            // 优先使用传入的表达式映射，其次从 JSON 中解析
            if (expressions != null && expressions.containsKey(cr.location)) {
                cr.expression = expressions.get(cr.location);
            } else {
                cr.expression = obj.has("expression") ? obj.get("expression").getAsString() : "";
            }
            if (obj.has("data") && !obj.get("data").isJsonNull()) {
                JsonElement data = obj.get("data");
                if (data.isJsonPrimitive()) {
                    cr.data = data.getAsString();
                } else {
                    cr.data = data.toString();
                }
            } else {
                cr.data = "";
            }
            cellResults.add(cr);
            model.addRow(new Object[]{cr.location, cr.name, cr.type, cr.data, cr.expression, cr.error ? "是" : "否"});
        }
        // 确保列可见性已应用
        applyColumnVisibility();
    }

    public String getSelectedCellLocation() {
        int row = table.getSelectedRow();
        if (row >= 0 && row < cellResults.size()) {
            return cellResults.get(row).location;
        }
        return null;
    }

    public java.util.List<CellResult> getCellResults() {
        return cellResults;
    }
}
