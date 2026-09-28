package com.cognex.parser;

import com.cognex.parser.JobxParser.ParsedCell;
import com.cognex.parser.JobxParser.ParsedJob;
import com.cognex.parser.JobxParser.ParsedSheet;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
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
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
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
 * 把 JobxParser 解析出的 ParsedJob 写成双 sheet xlsx。
 * Write a ParsedJob to a two-sheet xlsx.
 *
 * sheet1 "单元格"：列 位置 / 名称 / 值 / 表达式 / 批注，按位置排序（A0,B0,...,A1,...）。
 * sheet2 "位置布局"：按 A0~Z599 坐标还原至 Excel 单元格；图片（cell[5] saved 字节流）
 *                  检测格式后嵌入对应单元格；表达式 / 名称入批注。
 *
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
                // 值列：若是图片字节流则显示描述，否则原值 / Value column: image description if bytes form a picture, else raw
                String valueCol = c.value;
                if (c.savedBytes != null && c.savedBytes.length > 0) {
                    int picType = detectPictureType(c.savedBytes);
                    if (picType >= 0) {
                        valueCol = imageDescription(picType, c.savedBytes.length);
                    }
                    // 非图像字节流不修改值列（避免 Cognex 计数器等小 blob 干扰） / Non-image blobs left untouched
                }
                setCell(row.createCell(2), valueCol, wrapStyle);
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

            // 每个 ParsedSheet 一个"位置布局"sheet（避免多 sheet 同位置 comment 冲突）
            // One "位置布局" sheet per ParsedSheet (avoids comment collision when multiple sheets share same A0~Z599 coords)
            for (ParsedSheet sh : job.sheets) {
                addLayoutSheet(wb, sh.name, sh.cells, wrapStyle);
            }

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

    /**
     * 位置布局 sheet：按 A0~Z599 坐标还原至 Excel 单元格；图片嵌入对应单元格；表达式/名称入批注。
     * 每个源 sheet 单独创建一个 layout sheet（名为 "位置布局-{sheetName}"），避免多 sheet 同位置 comment 冲突。
     *
     * Layout sheet: restore cells by A0~Z599 coordinates; embed pictures at their cell anchors;
     * put expressions / names into cell comments. One layout sheet per source sheet
     * (named "位置布局-{sheetName}") to avoid comment collisions across sheets.
     */
    private static void addLayoutSheet(XSSFWorkbook wb, String sheetName,
                                        List<ParsedCell> cells, CellStyle borderStyle) {
        Sheet sheet = wb.createSheet(layoutSheetName(wb, sheetName));
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper helper = wb.getCreationHelper();

        for (ParsedCell c : cells) {
            int[] pos = parseLoc(c.location);
            if (pos == null) continue;
            int col = pos[0] - 1;          // 1-based → 0-based
            int rowIdx = pos[1];           // Cognex 行 n → Excel 行 n（0-based）
            Row row = sheet.getRow(rowIdx);
            if (row == null) row = sheet.createRow(rowIdx);

            Cell cell = row.getCell(col);
            if (cell == null) cell = row.createCell(col);

            // 值写入：数字按数字写，其余按字符串 / Write value: numeric as number, rest as string
            String val = c.value != null ? c.value : "";
            if (!val.isEmpty()) {
                Number num = parseNumber(val);
                if (num != null) {
                    cell.setCellValue(num.doubleValue());
                } else {
                    cell.setCellValue(val);
                }
            }
            cell.setCellStyle(borderStyle);

            // 图片嵌入 / Embed picture if savedBytes form a valid image
            if (c.savedBytes != null && c.savedBytes.length > 0) {
                int picType = detectPictureType(c.savedBytes);
                if (picType >= 0) {
                    try {
                        int picIdx = wb.addPicture(c.savedBytes, picType);
                        // XSSFClientAnchor(dx1,dy1,dx2,dy2,col1,row1,col2,row2)：0 偏移 + 起止单元格覆盖 (col, row)
                        XSSFClientAnchor anchor = new XSSFClientAnchor(
                                0, 0, 0, 0, col, rowIdx, col + 1, rowIdx + 1);
                        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
                        drawing.createPicture(anchor, picIdx);
                        // 行高加大以让图片可见 / enlarge row height so the picture is visible
                        row.setHeightInPoints(60);
                        // 列宽加宽（如果当前列窄） / widen column if currently narrow
                        if (sheet.getColumnWidth(col) < 20 * 256) {
                            sheet.setColumnWidth(col, 20 * 256);
                        }
                    } catch (Exception ignore) {
                        // 嵌入失败时静默忽略（不影响其他单元格） / silently skip on embed failure
                    }
                }
            }

            // 批注：名称 / 表达式 / Comment: name / expression
            boolean hasName = c.name != null && !c.name.isEmpty();
            boolean hasExpr = c.expression != null && !c.expression.isEmpty();
            if (hasName || hasExpr) {
                StringBuilder text = new StringBuilder();
                if (hasName) text.append("名称: ").append(c.name).append("\n");
                if (hasExpr) text.append("表达式: ").append(c.expression);
                // 用显式 col/row 的 anchor，避免 POI 默认 anchor 把所有 comment 归到 A1 引发冲突
                // Use an anchor with explicit col/row so POI does not assign every comment to A1 (would collide)
                XSSFClientAnchor cmtAnchor = new XSSFClientAnchor(
                        0, 0, 0, 0, col, rowIdx, col + 1, rowIdx + 1);
                Comment comment = drawing.createCellComment(cmtAnchor);
                comment.setString(helper.createRichTextString(text.toString()));
                comment.setAuthor(hasName ? c.name : c.location);
                cell.setCellComment(comment);
            }
        }

        // A~Z 26 列等宽（与 export/XlsxExporter.java 对齐）/ equal-width columns A~Z
        for (int i = 0; i < 26; i++) {
            // 但若图片所在列已被加宽（20*256），保留其宽度 / keep wider columns set above
            if (sheet.getColumnWidth(i) < (int) (6.5 * 256)) {
                sheet.setColumnWidth(i, (int) (6.5 * 256));
            }
        }

        // A4 纵向，自适应页宽 / A4 portrait, fit to width
        sheet.setFitToPage(true);
        PrintSetup ps = sheet.getPrintSetup();
        ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
        ps.setLandscape(false);
        ps.setFitWidth((short) 1);
        ps.setFitHeight((short) 0);
    }

    /** 生成 Excel 安全 sheet 名：前缀 "位置布局-" + 源 sheet 名（去除禁用字符并截断至 31 字符）。 */
    private static String layoutSheetName(XSSFWorkbook wb, String sheetName) {
        String base = sheetName == null ? "snippet" : sheetName;
        // Excel sheet 名禁用字符：[ ] : * ? / \  / forbidden chars
        String safe = base.replaceAll("[\\[\\]:*?/\\\\]", "_");
        String prefix = "位置布局-";
        int maxBase = 31 - prefix.length();
        if (safe.length() > maxBase) safe = safe.substring(0, maxBase);
        String name = prefix + safe;
        // 避免重名：若已存在则追加 _2 / _3 ...（实际多 sheet 同名极少见，但兜底）
        // Deduplicate: append _2/_3... if name already taken.
        int idx = 2;
        String candidate = name;
        while (wbSheetExists(wb, candidate)) {
            String suffix = "_" + idx;
            int cutLen = 31 - suffix.length();
            candidate = (name.length() > cutLen ? name.substring(0, cutLen) : name) + suffix;
            idx++;
        }
        return candidate;
    }

    private static boolean wbSheetExists(XSSFWorkbook wb, String name) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (name.equals(wb.getSheetName(i))) return true;
        }
        return false;
    }

    /**
     * 检测字节流是否为 POI 支持的图像格式；返回 POI 格式常量，非图像返回 -1。
     * Detect whether the byte stream is a POI-supported image; return POI format constant or -1 if not.
     */
    private static int detectPictureType(byte[] data) {
        if (data == null || data.length < 4) return -1;
        int b0 = data[0] & 0xFF, b1 = data[1] & 0xFF, b2 = data[2] & 0xFF, b3 = data[3] & 0xFF;
        // PNG: 89 50 4E 47
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
            return Workbook.PICTURE_TYPE_PNG;
        }
        // JPEG: FF D8 FF
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return Workbook.PICTURE_TYPE_JPEG;
        }
        // BMP/DIB: 42 4D ("BM")
        if (b0 == 0x42 && b1 == 0x4D) {
            return Workbook.PICTURE_TYPE_DIB;
        }
        // EMF: 01 00 00 00（视后续字节判断，保守返回 -1，Cognex 中极少见）
        // WMF: D7 CD C6 9A
        if (b0 == 0xD7 && b1 == 0xCD && b2 == 0xC6 && b3 == 0x9A) {
            return Workbook.PICTURE_TYPE_WMF;
        }
        // PICT: 00 11 00 00（前 512 字节头之后；不易可靠识别，保守跳过）
        return -1;
    }

    private static String imageDescription(int picType, int size) {
        String type;
        if (picType == Workbook.PICTURE_TYPE_PNG) type = "PNG";
        else if (picType == Workbook.PICTURE_TYPE_JPEG) type = "JPEG";
        else if (picType == Workbook.PICTURE_TYPE_DIB) type = "BMP";
        else if (picType == Workbook.PICTURE_TYPE_WMF) type = "WMF";
        else if (picType == Workbook.PICTURE_TYPE_EMF) type = "EMF";
        else if (picType == Workbook.PICTURE_TYPE_PICT) type = "PICT";
        else type = "未知";
        return "[" + type + " 图像 " + size + " 字节]";
    }

    /** 尝试把字符串解析为 int 或 double；失败返回 null。 / Parse as int or double; null on failure. */
    private static Number parseNumber(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            if (s.matches("-?\\d+")) {
                return Long.parseLong(s);
            }
            double d = Double.parseDouble(s);
            if (!Double.isNaN(d) && !Double.isInfinite(d)) {
                return d;
            }
        } catch (NumberFormatException ignore) {
        }
        return null;
    }
}
