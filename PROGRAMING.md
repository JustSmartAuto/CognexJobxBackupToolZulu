# Jobx 编辑器脚本编程指南（PROGRAMING.md）

本文档介绍 **Jobx 编辑器**标签页中内置的 JavaScript（QuickJS）脚本功能：如何用脚本读写 Cognex In-Sight 相机电子表格的单元格、触发作业、切换在线 / 实时模式。

- 脚本引擎：QuickJS（`cn.net.zhijian.quickjs:0.2.2`）
- 入口：主窗口 → **Jobx 编辑器**标签页 → 右下角「JavaScript 脚本编辑器」面板
- 相关源码：[JsScriptEngine.java](src/main/java/com/cognex/insight/script/JsScriptEngine.java)、[SpreadsheetJsApi.java](src/main/java/com/cognex/insight/script/SpreadsheetJsApi.java)、[ScriptEditorPanel.java](src/main/java/com/cognex/insight/ui/panel/ScriptEditorPanel.java)

---

## 1. 运行环境要求

| 项目 | 要求 |
| --- | --- |
| JDK / JRE | **JDK 19 或更高版本**（QuickJS 本地库内部使用了 `Thread.threadId()`，JDK 17/8 下无法初始化） |
| 相机连接 | 调用 `spreadsheet.*` API 前必须先在编辑器顶部成功连接相机（`ws://IP:端口/ws`） |
| 纯 JS 调试 | 不调用 `spreadsheet` 的普通 JavaScript（如 `console.log(1+1)`）在未连接时也可通过交互式输入框执行 |

> 低版本 JDK 下运行脚本会得到友好错误提示「QuickJS 脚本引擎需要 JDK 19 或更高版本……」，**备份、导出、HMI 手动编辑等其他功能不受影响**。

QuickJS 原生支持 **ES2020** 语法（`let` / `const`、箭头函数、模板字符串、解构、`for...of`、可选链 `?.`、`Map/Set`、`Promise` 等）。但脚本运行在一个**没有浏览器 / 没有事件循环**的嵌入式环境中：

- ❌ 没有 `window`、`document`、DOM
- ❌ 没有 `fetch`、`XMLHttpRequest`、`setTimeout`、`setInterval`
- ❌ 没有 `require` / 模块加载、没有文件系统
- ✅ 有 `Math`、`JSON`、`Date`、`Array` 等标准内置对象，以及 `console`
- ⚠️ 全部代码**同步执行**；即使写了 `Promise.then(...)`，回调也不会在脚本结束后继续被调度，请按同步方式编写

## 2. 脚本编辑器界面

| 区域 / 按钮 | 说明 |
| --- | --- |
| 编辑器 | 多行 JS 编辑区，带语法高亮、代码折叠；内容会随界面配置**自动保存**到 jar 所在目录的 `config.json`，下次启动恢复 |
| **运行 (F5)** | 执行编辑器中的整段脚本（快捷键 **F5**）；仅在已连接相机时可用 |
| 清空输出 | 清空「输出」区文本 |
| 重置环境 | 销毁并重建 JS 上下文，所有全局变量 / 函数被清空（见 [§6](#6-执行模型与上下文生命周期)） |
| 自动换行 | 切换编辑器与交互区的自动换行 |
| 输出 | 显示 `console` 输出、返回值（`[结果]`）与错误（`[错误]`） |
| 交互式脚本 | 单行 / 多行速算输入框：**Enter 执行**，**Shift+Enter 换行**；适合临时试验一两行代码 |

## 3. 全局对象 `spreadsheet`

脚本环境自动注入唯一的全局对象 **`spreadsheet`**，每个方法对应一次 CogSocket HMI 请求，**同步阻塞**等待响应，单次操作超时 **10 秒**，失败时抛出 `Error`，可用 `try/catch` 捕获。

### 3.1 方法一览

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `spreadsheet.getCellValue(cell, default?)` | `cell`：单元格字符串，如 `"A0"`；`default`：可选，读取失败 / 为空时的兜底值 | `number`（整数或浮点）/ `string` / 兜底值 | 查询单元格当前值 |
| `spreadsheet.setCellValue(cell, value)` | `cell`：单元格；`value`：数字或字符串 | 无 | 设置单元格值 |
| `spreadsheet.getCellExpression(cell, default?)` | `cell`；`default` 可选 | `string`（表达式文本）/ 兜底值 | 读取单元格表达式；接口为空时自动回退到结果查询 |
| `spreadsheet.setCellExpression(cell, expr)` | `cell`；`expr`：表达式字符串，如 `"AcqImage(1)"` | 无 | 设置单元格表达式 |
| `spreadsheet.trigger()` | 无 | 无 | 手动触发相机执行一次作业 |
| `spreadsheet.setOnline(online)` | `online`：**布尔** `true` / `false` | 无 | 切换相机在线（Soft Online）状态 |
| `spreadsheet.setLiveMode(live)` | `live`：**布尔** `true` / `false` | 无 | 切换实时模式 |

未连接相机时调用任何方法都会抛出 `Error: 未连接到相机`。

### 3.2 `console` 输出

输出区会捕获以下调用（每次执行前自动清空上次的缓冲，不会累积）：

```javascript
console.log("普通信息");      // 直接输出
console.info("普通信息");     // 同上
console.debug("调试");        // 加 [debug] 前缀
console.warn("警告");         // 加 [warn] 前缀
console.error("错误");        // 加 [error] 前缀
```

整段脚本执行结束后，输出区还会打印最后一个表达式的结果（`[结果] ...`）；对象会以 JSON 文本显示，无返回值时显示 `undefined`。

## 4. 重要的类型转换规则

脚本桥接层（Java 侧）会对参数 / 返回值做自动转换，编写时需注意：

1. **`setOnline` / `setLiveMode` 只认真布尔值**。底层用 `Boolean.parseBoolean(...)` 解析，因此必须传 `true` / `false`；传 `1` / `0` 会被当成字符串 `"1"` / `"0"`，**两者都解析为 `false`**。
   ```javascript
   spreadsheet.setOnline(true);    // ✅ 上线
   spreadsheet.setOnline(false);   // ✅ 离线
   spreadsheet.setLiveMode(1);     // ❌ 等价于 false
   ```
2. **`setCellValue` 的数字字符串会被转成数字**：整数文本转 `int`，含小数点的转 `double`，无法解析为数字的才按字符串发送。即无法用 `setCellValue("A0", "123")` 强制写入文本 `"123"`，实际写入的是数字 `123`。
3. **`getCellValue` 的返回值是动态类型**：单元格内容像整数就返回整数，像小数就返回浮点，否则返回字符串；需要字符串时请自行 `String(value)`，需要数字时用 `Number(value)` 规范化。
4. 单元格名一律使用**字符串**并带双引号：`"B17"`、`"AC5"`；不要写成 `B17`（会被当成 JS 变量）。

## 5. 相机状态约束（与界面操作相同）

- **在线状态或有 In-Sight Explorer 编辑器附着时，加载作业 / 修改单元格会失败**。批量写入前建议先离线，写完再恢复在线（见 **7.4 节**）。
- `trigger()` 是手动触发一次作业采集；触发后作业内部各单元格的更新由相机完成，脚本紧接着读到的值可能仍是上一拍的结果——需要逐拍取数时，请在**每次 `trigger()` 后做轮询读取并自行判断数据是否变化**（本环境没有 `sleep`，可用带时间戳的紧凑轮询循环，但应设置最大次数避免死循环）。
- HMI 端口：新固件 **80**，旧固件 **8087**，模拟器为随机端口。

## 6. 执行模型与上下文生命周期

- 编辑器与交互输入框**共享同一个 QuickJS 上下文**：在交互框定义的变量、函数，之后 F5 运行的整段脚本可以直接使用，反之亦然。
- 上下文在以下情况被**重建**（变量全部丢失）：
  - 点击「**重置环境**」
  - 重新连接 / 断开相机（`spreadsheet` 对象会绑定到新连接）
- 因为顶层 `let` / `const` 声明在同一上下文内持续存在，**重复运行含 `let x = ...` 的脚本会报「redeclaration of let x」**，两种处理方式：
  - 点击「重置环境」后再运行；
  - 把顶层声明改为 `var` 或函数内局部变量，便于反复 F5。
- 每次执行的 `console` 缓冲相互独立；上下文内变量不隔离。

## 7. 常用示例

### 7.1 读取并打印单元格

```javascript
let value = spreadsheet.getCellValue("A3");
console.log("A3 = " + value + "  (" + typeof value + ")");
```

带兜底值，避免单元格为空时拿到 `null`：

```javascript
let passCount = spreadsheet.getCellValue("C10", 0);
console.log("合格数: " + passCount);
```

### 7.2 写入值与表达式

```javascript
// 写入数值（离线状态下才会成功）
spreadsheet.setCellValue("D2", 42);
spreadsheet.setCellValue("D3", 3.14);

// 写入 / 修改表达式
spreadsheet.setCellExpression("E5", "D2+D3");

// 回读验证
console.log("E5 表达式 = " + spreadsheet.getCellExpression("E5"));
console.log("E5 值 = " + spreadsheet.getCellValue("E5"));
```

### 7.3 批量巡检一段单元格区域

```javascript
// 列字母转 0 基序号：A->0, B->1 ...
function colIndex(name) {
    return name.charCodeAt(0) - 65;
}

let rows = [];
for (let r = 0; r < 10; r++) {
    let cell = "B" + r;
    let v = spreadsheet.getCellValue(cell, "");
    rows.push(cell + "=" + v);
}
console.log(rows.join(", "));
```

### 7.4 安全范式：离线 → 写入 → 恢复在线

```javascript
// 注意：脚本 API 没有提供“查询当前是否在线”的方法，
// 请根据工具栏上的在线状态自行决定写完后是否恢复在线。
try {
    spreadsheet.setOnline(false);   // 先离线，保证写入不被拒绝

    spreadsheet.setCellValue("D2", 100);
    spreadsheet.setCellExpression("E5", "D2*2");

    spreadsheet.trigger();          // 可选：离线状态下触发一次，验证结果
    console.log("E5 = " + spreadsheet.getCellValue("E5"));
} catch (e) {
    console.error("操作失败: " + e.message);
} finally {
    spreadsheet.setOnline(true);    // 按需恢复在线
}
```

### 7.5 触发 + 轮询等待结果变化

```javascript
function waitChange(cell, oldValue, maxTries) {
    for (let i = 0; i < maxTries; i++) {
        let v = String(spreadsheet.getCellValue(cell));
        if (v !== String(oldValue)) return v;
    }
    throw new Error("等待 " + cell + " 更新超时");
}

let before = spreadsheet.getCellValue("C1");
spreadsheet.trigger();
let after = String(waitChange("C1", before, 200));
console.log("触发后 C1: " + before + " -> " + after);
```

### 7.6 纯 JavaScript 试验（无需相机）

在交互式输入框中直接输入，Enter 执行：

```javascript
JSON.stringify({ a: Math.round(Math.PI * 100) / 100, b: [1, 2, 3].map(x => x * x) })
```

## 8. 错误处理与 FAQ

**Q：运行时报「QuickJS 脚本引擎需要 JDK 19 或更高版本」？**
A：当前 Java 版本过低。用 JDK 19+（本机可用 JDK 25）运行 jar 即可；备份 / 导出 / 手动 HMI 编辑在 JDK 8+ 仍可正常使用。

**Q：报「未连接到相机」？**
A：`spreadsheet` 的所有方法都依赖 HMI 连接，请先在编辑器顶部填写地址端口、用户名密码并点击「连接」，状态变为已连接后再运行。

**Q：报「设置单元格值失败 / 设置单元格表达式失败」？**
A：相机处于在线状态、或有 In-Sight Explorer 正在附着编辑时会拒绝修改。先 `spreadsheet.setOnline(false)`（或点工具栏「在线/离线」），确认没有 Explorer 打开该作业后再写。

**Q：重复按 F5 报 `redeclaration of let x`？**
A：同一上下文里顶层 `let/const` 不能重复声明。点「重置环境」，或把 `let x` 改成 `var x` / 包进函数。

**Q：脚本会卡住界面吗？**
A：不会。脚本在后台线程执行，输出通过事件线程回写；但每个相机操作最多同步等待 10 秒，脚本里大量串行调用时请耐心等待运行结束。

**Q：脚本文本会保存吗？**
A：编辑器内容与「自动换行」开关随界面配置保存到 jar 所在目录的 `config.json`；输出区内容不保存。注意该文件明文保存相机连接信息，请勿外发。

**Q：能调用自定义 Java 类 / 导入库吗？**
A：不能。脚本只能使用标准 JS 内置对象与本文档列出的 `spreadsheet`、`console`。

---

## 9. Jobx 生成器（不连相机，纯 JS → .jobx / .cxdx / .xlsx）

第 5 个标签页「Jobx 生成器」用 JavaScript（QuickJS）+ 内置 `jobx` API 离线构建单元格 / sheet，运行（**F5**）后自动写出 .jobx / .cxdx / .xlsx 三类文件。**不需要连接相机**，便于 LLM 自动脚本编程。

- 入口：主窗口 → **Jobx 生成器**标签页
- 相关源码：[GeneratorTabPanel.java](src/main/java/com/cognex/generator/GeneratorTabPanel.java)、[GeneratorJsApi.java](src/main/java/com/cognex/generator/GeneratorJsApi.java)、[GeneratorScriptEngine.java](src/main/java/com/cognex/generator/GeneratorScriptEngine.java)、[JobxWriter.java](src/main/java/com/cognex/generator/JobxWriter.java)
- 二进制格式：见 [JobxBinaryStructure.md](JobxBinaryStructure.md)

### 9.1 运行环境要求

| 项目 | 要求 |
| --- | --- |
| JDK / JRE | **JDK 19 或更高版本**（与编辑器一致，QuickJS 本地库内部用 `Thread.threadId()`） |
| 相机连接 | **不需要** |
| 输出目录 | GUI 默认 jar 目录下 `generated/`；CLI 默认脚本同目录 |

> 低版本 JDK 下脚本功能给出友好错误「QuickJS 脚本引擎需要 JDK 19 或更高版本……」，**备份 / 导出 / 解析 / HMI 编辑等其他功能不受影响**。

环境约束与编辑器相同（[§1](#1-运行环境要求)）：无 `window` / `document` / `fetch` / `setTimeout`；标准 `Math` / `JSON` / `Date` / `Array` / `console` 可用；同步执行。

### 9.2 全局对象 `jobx`

脚本环境自动注入全局对象 **`jobx`**，方法返回确认字符串或值对象。脚本结束若未显式调用 `jobx.output()`，自动按 `format="all"` 写出三种格式。

#### 9.2.1 方法一览

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `jobx.sheet(name)` | `name`：sheet 名 | `string`（如 `"sheet: Inspection (cells=4)"`） | 切换当前 sheet；不存在则创建 |
| `jobx.sheets()` | 无 | `string[]` | 所有 sheet 名数组 |
| `jobx.setCell(loc, propsOrExpr)` | `loc`：单元格字符串（如 `"A0"`）；`propsOrExpr`：字符串=表达式，或对象（含 `expression` / `name` / `value` / `comment` / `cellStyle` / `graphicsStyle` / `condition` / `input` / `output` / `ipProtected` 任意子集） | `string`（如 `"set A0 @Inspection"`） | 写单元格；位置不存在则创建 |
| `jobx.getCell(loc)` | `loc` | `object` / `null` | 返回 `{location, expression, value, name, comment, cellStyle}`；不存在返回 `null` |
| `jobx.getCells()` | 无 | `object[]` | 当前 sheet 全部单元格对象数组 |
| `jobx.meta(obj)` | `obj`：含 `JobVersion` / `JobType` / `CameraType` / `FirmwareVersion` 任意子集 | `string` | 设置 Job 元数据（写入 Job.json Metadata） |
| `jobx.sheetMeta(name, obj)` | `name`；`obj`：含 `timeout` / `coreThreshold` / `processingCores` / `outputs` / `columnWidths[]` / `rowHeights[]` 任意子集 | `string` | 设置 sheet 元数据 |
| `jobx.load(file)` | `file`：`.jobx` / `.cxdx` 绝对路径 | `string`（如 `"loaded: 2 sheets, 406 cells from 天窗程序模板.jobx"`） | **从已有作业加载为模板**：清空当前模型并填入解析出的 sheet/cell；saved 字段被丢弃（恒 null） |
| `jobx.loadImage(file, size)` | `file`：图片绝对路径（PNG/JPG/BMP/非动画 GIF）；`size`：输出边长像素数（1~1024），图片会缩放为 `size×size` | `number[][][]`：`pixels[y][x]=[r,g,b,a]`（各分量 0~255） | 由 Java ImageIO 侧解码图片（QuickJS 无文件系统/图像能力）；配合 `setCell` 的 `cellStyle` 可生成像素画 sheet（见 §9.6.5） |
| `jobx.output(opts)` | `opts`：`{format, outDir, baseName, noSig}` 任意子集 | `string` | 显式设置输出参数；不调用则使用 CLI / GUI 默认值，format 默认 `"all"` |
| `jobx.log(msg)` | `msg`：任意 | `null` | 等同 `console.log`，写标准输出 |

#### 9.2.2 单元格 12 元素语义

`setCell` 写入的 cell 在 Job.json 中按官方 12 元素数组顺序存储（详见 [JobxBinaryStructure.md §6.4](JobxBinaryStructure.md)）：

| 索引 | 字段 | `setCell` props key | 默认值 |
| --- | --- | --- | --- |
| 0 | location | （由 `loc` 第一参数填入） | 必填 |
| 1 | expression | `expression` | `""` |
| 2 | condition | `condition` | `"1"`（正常） |
| 3 | value | `value` | `null`（运行时由相机填） |
| 4 | name | `name` | `""` |
| 5 | saved | （**恒为 null，不可设置**） | `null` |
| 6 | cellStyle | `cellStyle` | `""` |
| 7 | graphicsStyle | `graphicsStyle` | `""` |
| 8 | comment | `comment` | `""` |
| 9 | input | `input` | `0` |
| 10 | output | `output` | `0` |
| 11 | ipProtected | `ipProtected` | `0` |

`cellStyle` 格式（IsvsCellStyleSerializer）：多个 CSS 块以空格分隔，每块形如 `.cell { background-color:rgba(0,128,0,1.0); color:rgba(255,255,255,1.0); }`。POI 颜色无 alpha 通道，导出 xlsx 时 A 被忽略。

### 9.3 已知问题：训练型工具的 saved 字段

`PatMax` / `Caliper` / `CalibrateGrid` 等**需要训练状态**的视觉工具，其训练态以 `data/<sha256>` 对象形式存储在 .jobx 中，由 cell[5] `saved` 字段以 `{"$type":"FileRef","id":"data/<hash>"}` 引用（详见 [§5](JobxBinaryStructure.md)）。

本生成器**不连相机、无法在相机侧运行训练**，因此：
- ✅ 表达式可正常写入：`jobx.setCell("D0", "PatMax(A0, \"model1\")")`
- ❌ `saved` 字段**恒为 null**（用户要求，无法设置）
- ⚠️ 装入相机后，这些 cell 可能无法直接运行，需在 In-Sight Explorer 中**重新执行训练**才能正常工作

非训练型工具（`AcquireImage` / `Presence` / `IF` / `Count` 等纯函数型）不受此限制，生成的 .jobx 装入相机即可运行。

### 9.4 输出文件名规则

与解析器导出 xlsx 一致（[ParserXlsxExporter.java:188](src/main/java/com/cognex/parser/ParserXlsxExporter.java#L188)）：

```
{baseName}_yyyyMMdd_HHmmss.{ext}
```

- `baseName` 默认：CLI = 脚本 stem；GUI = `"generated"`
- `outDir` 默认：CLI = 脚本父目录；GUI = jar 目录下 `generated/`
- `format` 取值：`jobx` | `cxdx` | `xlsx` | `all`（默认 `all`）
- `noSig` 默认 `false`（**默认写 `.sig`** HMAC-SHA256 签名；`true` 跳过）

签名算法（详见 [§8](JobxBinaryStructure.md)）：HMAC-SHA256，base64 key `DtrDN+DqE5lDTNNWDl1tkYI92hmjAW2g8Rc+xmn9P04=`；.jobx 签名明文 Job.json 字节，.cxdx 签名 snippet.json 密文（XOR 后）字节。

### 9.5 CLI 用法

```
java -jar jobx文件备份助手_<时间戳>.jar generate <script.js> [--out <dir>] [--format <fmt>] [--name <base>] [--no-sig]
```

| 参数 | 说明 |
| --- | --- |
| `<script.js>` | 生成器 JS 脚本路径 |
| `--out <dir>` / `-o` | 输出目录（默认脚本同目录） |
| `--format <fmt>` / `-f` | 输出格式：`jobx` / `cxdx` / `xlsx` / `all`（默认 `all`） |
| `--name <base>` / `-n` | 输出文件名前缀（默认脚本 stem） |
| `--no-sig` | 不写 .sig 签名文件（默认写 HMAC-SHA256 签名） |

CLI 参数仅作为**默认值**：脚本中若调用 `jobx.output({...})` 则覆盖 CLI 参数；不调用则使用 CLI 参数。

退出码：0=成功；1=脚本或文件生成失败（多格式时部分失败也返回 1，但成功部分仍写出）；2=参数错误。

### 9.6 示例

#### 9.6.1 存在检测（最小可运行示例）

```javascript
// presence.js — Jobx 生成器示例：存在检测 / Presence Detection
jobx.sheet("Inspection");
jobx.setCell("A0", "AcquireImage()");
jobx.setCell("B0", "Presence(A0, 0.5)");
jobx.setCell("C0", "IF(B0>0,\"OK\",\"NG\")");

// 带名称/批注/样式写单元格
jobx.setCell("C0", {
  expression: "IF(B0>0,\"OK\",\"NG\")",
  name: "Result",
  comment: "Pass when presence > 0",
  cellStyle: ".cell { background-color:rgba(0,128,0,1.0); color:rgba(255,255,255,1.0); }"
});

jobx.meta({ JobVersion: "22.2", JobType: "Spreadsheet" });
```

CLI 运行：

```
java -jar jobx文件备份助手_<时间戳>.jar generate presence.js --format all
# 输出：
#   presence_<ts>.jobx（默认含 .sig 签名）
#   presence_<ts>.cxdx
#   presence_<ts>.xlsx
```

#### 9.6.2 从已有 .jobx 加载为模板

在已有作业基础上修改部分单元格（保留原 sheet 名、表达式、样式）：

```javascript
// modify-template.js — 加载相机导出的 .jobx 模板，修改阈值后重新生成
jobx.load("D:/templates/天窗程序模板.jobx");
console.log("加载完成: " + jobx.sheets().join(", "));

jobx.sheet("Inspection");
// 把 B0 阈值从 0.5 改为 0.8
let old = jobx.getCell("B0");
console.log("原 B0 表达式: " + old.expression);
jobx.setCell("B0", "Presence(A0, 0.8)");

// 仅输出 .jobx，便于直接装回相机
jobx.output({ format: "jobx", baseName: "天窗程序模板_阈值0.8" });
```

> **注意**：`load` 加载的作业若含 `PatMax` / `Caliper` 等训练型 cell，它们的 `saved` 字段会被丢弃；重新装入相机后这些工具需重新训练。

#### 9.6.3 自定义输出参数

```javascript
// custom-output.js — 显式指定输出目录 / 文件名 / 不写签名
jobx.sheet("Inspection");
jobx.setCell("A0", "AcquireImage()");
jobx.setCell("B0", "Count(A0, 100, 0, 0)");

jobx.output({
  format: "all",          // jobx | cxdx | xlsx | all
  outDir: "D:/generated", // 自定义目录
  baseName: "count_demo", // 自定义文件名前缀
  noSig: false             // true=不写 .sig
});
```

#### 9.6.4 纯 JavaScript 试验（与编辑器 §7.6 一致）

不调用 `jobx.*` 时不会写出任何文件：

```javascript
// 仅做数学计算，验证 QuickJS 环境
console.log(JSON.stringify({ pi: Math.PI, sum: [1, 2, 3].reduce((a, b) => a + b, 0) }));
```

#### 9.6.5 图片 → 像素画 cxdx（cellStyle 着色）

把 PNG 采样为 N×N 单元格，每个像素一个单元格，颜色写入 cellStyle 的 `background-color`；列宽/行高设成相同像素值得到正方形网格。完整脚本见 [smoke-test/logo-pixel.js](smoke-test/logo-pixel.js)：

```javascript
var SIZE = 64;
var pixels = jobx.loadImage("D:/path/to/logo.png", SIZE);  // pixels[y][x]=[r,g,b,a]
jobx.sheet("Logo");
jobx.sheetMeta("Logo", { columnWidths: new Array(SIZE).fill(20),
                         rowHeights:   new Array(SIZE).fill(20) });
for (var y = 0; y < SIZE; y++) {
    for (var x = 0; x < SIZE; x++) {
        var p = pixels[y][x];
        if (p[3] < 16) continue;  // 全透明像素留空
        var css = ".cell { background-color:rgba(" + p[0] + "," + p[1] + "," + p[2]
                + "," + (Math.round(p[3] / 255 * 1000) / 1000) + "); }";
        jobx.setCell(colName(x) + y, { expression: '""', cellStyle: css });
    }
}
```

CLI 生成（脚本不调用 `jobx.output`，输出参数全部走命令行）：

```bash
java -jar jobx文件备份助手_<时间戳>.jar generate logo-pixel.js --format cxdx --out out --name logo_64x64
# -> out/logo_64x64_yyyyMMdd_HHmmss.cxdx（含 snippet.json.sig，range A1:BL64，4096 个带色单元格）
```

### 9.7 错误处理与 FAQ

**Q：运行时报「QuickJS 脚本引擎需要 JDK 19 或更高版本」？**
A：用 JDK 19+（本机可用 JDK 25）运行 jar；其他功能在 JDK 8+ 仍可用。

**Q：生成的 .jobx 装入相机后 `PatMax` / `Caliper` 报「未训练」？**
A：已知问题，详见 [§9.3](#93-已知问题训练型工具的-saved-字段)。本工具无法离线生成训练态；需在相机侧 In-Sight Explorer 重新训练这些 cell。

**Q：`jobx.load()` 加载后 saved 字段丢失？**
A：同上，`saved` 恒为 null。装入相机后训练型工具需重训。

**Q：CLI 的 `--format` 与脚本 `jobx.output({format:...})` 冲突？**
A：脚本调用 `jobx.output()` 时以脚本设置为准；不调用时使用 CLI 参数（默认 `all`）。

**Q：输出文件名规则？**
A：`{base}_yyyyMMdd_HHmmss.{ext}`，与解析器导出 xlsx 一致。`base` 默认脚本 stem（CLI）或 `"generated"`（GUI）。

**Q：脚本会保存吗？**
A：GUI 编辑器内容自动保存到 jar 目录下 `generator-script.js`（明文，无相机信息）。CLI 不保存脚本，仅执行。

**Q：能调用自定义 Java 类 / 导入库吗？**
A：不能。脚本只能使用标准 JS 内置对象与本文档列出的 `jobx`、`console`。
