package com.cognex.parser;

import com.cognex.parser.JobxParser.ParsedCell;
import com.cognex.parser.JobxParser.ParsedJob;
import com.cognex.parser.JobxParser.ParsedSheet;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
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

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 把 JobxParser 解析出的 ParsedJob 写成单 sheet xlsx。
 * Write a ParsedJob to a single-sheet xlsx.
 *
 * 列：位置 / 名称 / 值 / 表达式 / 批注；按位置排序（A0,B0,...,A1,...）。
 * 文件名：{源文件stem}_yyyyMMdd_HHmmss.xlsx，存源文件同目录。
 */
public class ParserXlsxExporter {

    private ParserXlsxExporter() {
    }

    /**
     * 导出 xlsx 到源文件同目录。
     * Export xlsx to the source file's parent directory.
     */
    public static File export(ParsedJob job, Date exportedAt) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        try {
            Sheet sheet = wb.createSheet("单元格");

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            // 表头样式 / header style
            CellStyle headStyle = wb.createCellStyle();
            headStyle.setFont(boldFont);
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headStyle.setFillForegroundColor(new XSSFColor(new Color(0xD9, 0xE1, 0xF2), null));
            headStyle.setAlignment(HorizontalAlignment.CENTER);
            headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(headStyle, BorderStyle.THIN);

            // 数据行样式 / data row style
            CellStyle wrapStyle = wb.createCellStyle();
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);
            wrapStyle.setWrapText(true);
            setBorder(wrapStyle, BorderStyle.THIN);

            CellStyle labelStyle = wb.createCellStyle();
            labelStyle.setFont(boldFont);

            // 顶部信息 / top info
            String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(exportedAt);
            String[][] header = {
                {"源文件", job.sourceFile.getName()},
                {"Job版本", job.meta.getOrDefault("JobVersion", "")},
                {"相机型号", job.meta.getOrDefault("CameraType", "")},
                {"固件版本", job.meta.getOrDefault("FirmwareVersion", "")},
                {"作业类型", job.meta.getOrDefault("JobType", "")},
                {"导出时间", timeStr}
            };
            for (int i = 0; i < header.length; i++) {
                Row row = sheet.createRow(i);
                Cell label = row.createCell(0);
                label.setCellValue(header[i][0]);
                label.setCellStyle(labelStyle);
                row.createCell(1).setCellValue(header[i][1]);
            }

            // 列标题行 / column header row
            int headRow = header.length;  // 0-based
            String[] cols = {"位置", "名称", "值", "表达式", "批注"};
            Row headerRow = sheet.createRow(headRow);
            for (int i = 0; i < cols.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headStyle);
            }

            // 数据行 - 收集所有 sheet 的 cell，按位置排序
            // Collect cells from all sheets, sort by location
            List<ParsedCell> all = new ArrayList<>();
            for (ParsedSheet sh : job.sheets) {
                all.addAll(sh.cells);
            }
            Collections.sort(all, new Comparator<ParsedCell>() {
                @Override
                public int compare(ParsedCell a, ParsedCell b) {
                    int[] pa = parseLoc(a.location);
                    int[] pb = parseLoc(b.location);
                    if (pa == null && pb == null) return a.location.compareTo(b.location);
                    if (pa == null) return 1;
                    if (pb == null) return -1;
                    if (pa[1] != pb[1]) return pa[1] - pb[1];  // 先行 / row first
                    return pa[0] - pb[0];                       // 再列 / then col
                }
            });

            int rowIdx = headRow;
            for (ParsedCell c : all) {
                rowIdx++;
                Row row = sheet.createRow(rowIdx);
                setCell(row.createCell(0), c.location, wrapStyle);
                setCell(row.createCell(1), c.name, wrapStyle);
                setCell(row.createCell(2), c.value, wrapStyle);
                setCell(row.createCell(3), c.expression, wrapStyle);
                setCell(row.createCell(4), c.comment, wrapStyle);
            }

            // 列宽 / column widths
            sheet.setColumnWidth(0, 10 * 256);
            sheet.setColumnWidth(1, 26 * 256);
            sheet.setColumnWidth(2, 42 * 256);
            sheet.setColumnWidth(3, 60 * 256);
            sheet.setColumnWidth(4, 30 * 256);

            // 打印区域 + 标题行重复 / print area + repeat header row
            int lastRow = Math.max(rowIdx, headRow) + 1;
            wb.setPrintArea(0, 0, cols.length - 1, 0, lastRow - 1);
            // CellRangeAddress.valueOf 的 "r:r" 形式按 1-based 行号 / 1-based row index
            sheet.setRepeatingRows(CellRangeAddress.valueOf((headRow + 1) + ":" + (headRow + 1)));

            PrintSetup ps = sheet.getPrintSetup();
            ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
            ps.setLandscape(false);  // 纵向 / portrait (POI 无 PORTRAIT 常量)

            // 输出文件 / output file
            String stem = stem(job.sourceFile.getName());
            String fileName = stem + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(exportedAt) + ".xlsx";
            File outFile = new File(job.sourceFile.getAbsoluteFile().getParentFile(), fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                wb.write(fos);
            }
            return outFile;
        } finally {
            wb.close();
        }
    }

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * 解析 "A0" / "$B$17" → [col(1-based), row]；非法返回 null。
     * Parse "A0" / "$B$17" -> [col(1-based), row]; returns null if invalid.
     */
    private static int[] parseLoc(String loc) {
        if (loc == null) return null;
        int i = 0, col = 0, row = 0;
        // 跳过 $ 与字母 / skip $ and letters
        while (i < loc.length()) {
            char ch = loc.charAt(i);
            if (ch == '$') {
                i++;
                continue;
            }
            if (ch >= 'A' && ch <= 'Z') {
                col = col * 26 + (ch - 'A') + 1;
                i++;
            } else {
                break;
            }
        }
        if (col == 0 || i == loc.length()) return null;
        for (; i < loc.length(); i++) {
            char ch = loc.charAt(i);
            if (ch == '$') continue;
            if (ch < '0' || ch > '9') return null;
            row = row * 10 + (ch - '0');
        }
        return new int[]{col, row};
    }

    private static void setCell(Cell cell, String value, CellStyle style) {
        if (value != null) cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void setBorder(CellStyle style, BorderStyle border) {
        style.setBorderTop(border);
        style.setBorderBottom(border);
        style.setBorderLeft(border);
        style.setBorderRight(border);
    }
}
