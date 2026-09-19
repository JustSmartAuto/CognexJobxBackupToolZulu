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
