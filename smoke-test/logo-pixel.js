// ============================================================
// 像素画生成示例：把 PNG 图片采样为 64×64 单元格网格，
// 每个像素一个单元格，颜色通过 cellStyle 的 background-color 设置，
// 经 CLI 输出为 .cxdx（不连相机）。
//
// CLI:
//   java -jar jobx文件备份助手_<ts>.jar generate logo-pixel.js \
//        --format cxdx --out smoke-test/out --name logo_64x64
//
// Pixel-art demo: sample a PNG to a 64x64 cell grid colored via
// cellStyle background-color, emitted as .cxdx through the CLI.
// ============================================================

var IMG = "d:/JustStupid/jobx表格编辑器_2604270917/CognexJobxBackupToolZulu/assets/logo.png";
var SIZE = 64;        // 网格边长（像素画分辨率）/ grid edge length
var CELL_PX = 20;     // 每个单元格的正方形边长（屏幕像素，columnWidths/rowHeights 同值）

// 0-based 列号 → Excel 风格列字母：0=A ... 25=Z, 26=AA, 63=BL
function colName(idx) {
    var s = "";
    var n = idx + 1;
    while (n > 0) {
        n--;
        s = String.fromCharCode(65 + (n % 26)) + s;
        n = Math.floor(n / 26);
    }
    return s;
}

// Java(ImageIO) 侧解码缩放，QuickJS 拿到二维 RGBA 数组 pixels[y][x]=[r,g,b,a]
var pixels = jobx.loadImage(IMG, SIZE);

jobx.sheet("Logo");

// 正方形网格：列宽 = 行高 = CELL_PX 像素 / square cells via equal column/row sizes
var widths = [];
var heights = [];
for (var i = 0; i < SIZE; i++) {
    widths.push(CELL_PX);
    heights.push(CELL_PX);
}
jobx.sheetMeta("Logo", { columnWidths: widths, rowHeights: heights });

// 逐像素写单元格；表达式用空串字面量 "" 保证单元格存在，颜色走 cellStyle
var painted = 0;
for (var y = 0; y < SIZE; y++) {
    for (var x = 0; x < SIZE; x++) {
        var p = pixels[y][x];
        if (p[3] < 16) continue;  // 全透明像素留空 / skip fully transparent pixels
        var a = Math.round((p[3] / 255) * 1000) / 1000;
        var css = ".cell { background-color:rgba(" + p[0] + "," + p[1] + "," + p[2] + "," + a + "); }";
        jobx.setCell(colName(x) + y, { expression: '""', cellStyle: css });
        painted++;
    }
}

console.log("painted cells: " + painted + " / " + (SIZE * SIZE));
// 不调用 jobx.output：输出参数（--format cxdx --name logo_64x64 --out ...）由 CLI 提供
