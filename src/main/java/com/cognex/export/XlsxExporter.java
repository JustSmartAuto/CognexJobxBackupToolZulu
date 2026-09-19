package com.cognex.export;

import com.cognex.insight.model.CellResult;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 将单个 jobx 的单元格数据导出为适合 A4 纵向打印的 .xlsx 文件。
 * 布局对应 Go 版 ExportCells：sheet "单元格"（表头信息 + 列标题行重复打印）
 * 与 sheet "位置布局"（A0~Z599 坐标还原，表达式入批注，自适应页宽）。
 */
public class XlsxExporter {

    /**
     * 生成 xlsx，返回文件路径。
     *
     * @param outDir     输出目录
     * @param cameraIP   相机 IP
     * @param jobxName   jobx 名称
     * @param cells      单元格结果（已按位置排序）
     * @param expressions 位置 -> 表达式
     * @param exportedAt 导出时间
     */
    public static String exportCells(File outDir, String cameraIP, String jobxName,
                                     List<CellResult> cells, Map<String, String> expressions,
                                     Date exportedAt) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        try {
            Sheet sheet = wb.createSheet("单元格");

            CreationHelper createHelper = wb.getCreationHelper();

            // 样式：加粗字体
            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            // 样式：列标题行（加粗 + 浅蓝填充 + 居中 + 边框）
            CellStyle headStyle = wb.createCellStyle();
            headStyle.setFont(boldFont);
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(0xD9, 0xE1, 0xF2), null));
            headStyle.setAlignment(HorizontalAlignment.CENTER);
            headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(headStyle, BorderStyle.THIN);

            // 样式：数据行（顶端对齐 + 自动换行 + 底边框）
            CellStyle wrapStyle = wb.createCellStyle();
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);
            wrapStyle.setWrapText(true);
            wrapStyle.setBorderBottom(BorderStyle.THIN);

            // 样式：布局 sheet 网格边框
            CellStyle borderStyle = wb.createCellStyle();
            setBorder(borderStyle, BorderStyle.THIN);
            borderStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // 打印表头信息
            String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(exportedAt);
            CellStyle labelStyle = wb.createCellStyle();
            labelStyle.setFont(boldFont);
            setHeader(sheet, labelStyle, createHelper, cameraIP, jobxName, timeStr);

            // 列标题行（第5行，0-based 行4）
            int headRow = 4;
            String[] headers = {"位置", "名称", "类型", "值", "表达式"};
            Row headerRow = sheet.createRow(headRow);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headStyle);
            }

            // 数据行
            int rowIdx = headRow;
            for (CellResult c : cells) {
                rowIdx++;
                Row row = sheet.createRow(rowIdx);
                setCellValue(row.createCell(0), c.location, wrapStyle);
                setCellValue(row.createCell(1), c.name, wrapStyle);
                setCellValue(row.createCell(2), c.type, wrapStyle);
                setCellValue(row.createCell(3), dataString(c), wrapStyle);
                setCellValue(row.createCell(4), expressions.get(c.location), wrapStyle);
            }

            // 列宽（近似对应 Go 版的字符宽度）
            sheet.setColumnWidth(0, 10 * 256);
            sheet.setColumnWidth(1, 26 * 256);
            sheet.setColumnWidth(2, 26 * 256);
            sheet.setColumnWidth(3, 42 * 256);
            sheet.setColumnWidth(4, 60 * 256);

            // 页面设置：A4 纵向 + 打印区域 + 标题行重复
            int lastRow = Math.max(rowIdx, headRow) + 1;
            wb.setPrintArea(0, 0, 4, 0, lastRow - 1);
            sheet.setRepeatingRows(CellRangeAddress.valueOf("5:5"));
            PrintSetup ps = sheet.getPrintSetup();
            ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
            ps.setLandscape(false);   // 纵向（portrait）

            // 第二个 sheet：位置布局
            addLayoutSheet(wb, cells, expressions, borderStyle);

            // 保存
            String fileName = jobxName + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(exportedAt) + ".xlsx";
            File outFile = new File(outDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                wb.write(fos);
            }
            return outFile.getAbsolutePath();
        } finally {
            wb.close();
        }
    }

    private static void setHeader(Sheet sheet, CellStyle labelStyle, CreationHelper helper,
                                  String cameraIP, String jobxName, String timeStr) {
        String[][] header = {
            {"相机IP", cameraIP},
            {"Jobx名称", jobxName},
            {"导出时间", timeStr}
        };
        for (int i = 0; i < header.length; i++) {
            Row row = sheet.createRow(i);
            Cell label = row.createCell(0);
            label.setCellValue(header[i][0]);
            label.setCellStyle(labelStyle);
            row.createCell(1).setCellValue(header[i][1]);
        }
    }

    /** 位置布局 sheet：值入格、表达式/名称/类型入批注、有内容的单元格加边框、自适应页宽。 */
    private static void addLayoutSheet(XSSFWorkbook wb, List<CellResult> cells,
                                       Map<String, String> expressions, CellStyle borderStyle) {
        Sheet sheet = wb.createSheet("位置布局");
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper helper = wb.getCreationHelper();

        for (CellResult c : cells) {
            int[] pos = parseLocation(c.location);
            if (pos == null) continue;
            int col = pos[0] - 1;          // 转 0-based
            int rowIdx = pos[1];           // Cognex 行 n -> Excel 行 n+1（0-based 即 n）
            Row row = sheet.getRow(rowIdx);
            if (row == null) row = sheet.createRow(rowIdx);

            Cell cell = row.getCell(col);
            if (cell == null) cell = row.createCell(col);

            // 值直接写入：数字按数字写，其余按字符串
            String val = dataString(c);
            if (!val.isEmpty()) {
                Number num = parseNumber(val);
                if (num != null) {
                    cell.setCellValue(num.doubleValue());
                } else {
                    cell.setCellValue(val);
                }
            }
            cell.setCellStyle(borderStyle);

            // 批注承载 名称/类型/表达式
            String expr = expressions.get(c.location);
            boolean hasName = c.name != null && !c.name.isEmpty();
            boolean hasType = c.type != null && !c.type.isEmpty();
            if (!hasName && !hasType && (expr == null || expr.isEmpty())) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            if (hasName) text.append("名称: ").append(c.name).append("\n");
            if (hasType) text.append("类型: ").append(c.type).append("\n");
            if (expr != null && !expr.isEmpty()) text.append("表达式: ").append(expr);
            String author = hasName ? c.name : c.location;

            Comment comment = drawing.createCellComment(helper.createClientAnchor());
            comment.setString(helper.createRichTextString(text.toString()));
            comment.setAuthor(author);
            cell.setCellComment(comment);
        }

        // A~Z 等宽窄列
        for (int i = 0; i < 26; i++) {
            sheet.setColumnWidth(i, (int) (6.5 * 256));
        }

        // A4 纵向，缩放适合页宽（fitToWidth=1, fitToHeight=0 允许多页纵向）
        sheet.setFitToPage(true);
        PrintSetup ps = sheet.getPrintSetup();
        ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
        ps.setLandscape(false);   // 纵向（portrait）
        ps.setFitWidth((short) 1);
        ps.setFitHeight((short) 0);
    }

    /** 解析 Cognex 单元格位置（如 "A0"、"BC12"）为 [列号(1起), 行号]；非法返回 null。 */
    static int[] parseLocation(String loc) {
        if (loc == null) return null;
        int i = 0, col = 0, row = 0;
        while (i < loc.length() && loc.charAt(i) >= 'A' && loc.charAt(i) <= 'Z') {
            col = col * 26 + (loc.charAt(i) - 'A') + 1;
            i++;
        }
        if (col == 0 || i == loc.length()) return null;
        for (; i < loc.length(); i++) {
            char ch = loc.charAt(i);
            if (ch < '0' || ch > '9') return null;
            row = row * 10 + (ch - '0');
        }
        return new int[]{col, row};
    }

    /** 尝试把字符串解析为 int 或 double；失败返回 null。 */
    static Number parseNumber(String s) {
        if (s == null) return null;
        try {
            if (s.matches("-?\\d+")) {
                return Long.parseLong(s);
            }
            double d = Double.parseDouble(s);
            if (!Double.isNaN(d) && !Double.isInfinite(d)) {
                return d;
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String dataString(CellResult c) {
        return c.data != null ? c.data.toString() : "";
    }

    private static void setCellValue(Cell cell, String value, CellStyle style) {
        if (value != null) {
            cell.setCellValue(value);
        }
        cell.setCellStyle(style);
    }

    private static void setBorder(CellStyle style, BorderStyle border) {
        style.setBorderTop(border);
        style.setBorderBottom(border);
        style.setBorderLeft(border);
        style.setBorderRight(border);
    }
}
