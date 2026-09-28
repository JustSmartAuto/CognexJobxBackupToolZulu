// Jobx 生成器冒烟测试脚本：存在检测 / Presence Detection
// Build cells via jobx.* API, expect .jobx/.cxdx/.xlsx to be generated.

// 1. 选择/创建 sheet
jobx.sheet("Inspection");

// 2. 写入单元格表达式（Cognex In-Sight 表达式语法）
jobx.setCell("A0", "AcquireImage()");
jobx.setCell("B0", "Presence(A0, 0.5)");
jobx.setCell("C0", "IF(B0>0,\"OK\",\"NG\")");

// 3. 带名称/批注/样式写一个单元格
jobx.setCell("C0", {
  expression: "IF(B0>0,\"OK\",\"NG\")",
  name: "Result",
  comment: "Pass when presence > 0",
  cellStyle: ".cell { background-color:rgba(0,128,0,1.0); color:rgba(255,255,255,1.0); }"
});

// 4. 设置作业元数据
jobx.meta({ JobVersion: "22.2", JobType: "Spreadsheet" });

// 5. 添加 PatMax 工具引用（已知问题：saved 留 null）
jobx.setCell("D0", "PatMax(A0, \"model1\")");
jobx.log("presence.js script done");
