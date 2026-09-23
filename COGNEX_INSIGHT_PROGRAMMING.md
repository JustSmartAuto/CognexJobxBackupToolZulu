# Cognex In-Sight Spreadsheet Script 编程指南

> 基于 In-Sight 26.1.0 帮助文档 (`ScriptJSAPIReference.htm`) 的中文总结
> JavaScript 版本: **ECMAScript 5 (ECMA5)**

---

## 目录

1. [Script 函数概述](#1-script-函数概述)
2. [脚本基本结构](#2-脚本基本结构)
3. [生命周期方法](#3-生命周期方法)
4. [Run 方法返回值](#4-run-方法返回值)
5. [图形绘制 (Draw)](#5-图形绘制-draw)
6. [数据结构与输入输出](#6-数据结构与输入输出)
7. [模块系统](#7-模块系统)
8. [全局对象](#8-全局对象)
9. [文件系统模块](#9-文件系统模块)
10. [Shape 对象](#10-shape-对象)
11. [超时与内存限制](#11-超时与内存限制)
12. [完整示例模板](#12-完整示例模板)

---

## 1. Script 函数概述

**Script** 函数用于在电子表格单元格中执行用户自定义的 JavaScript 源代码。通过 `Edit Script Dialog` 创建和编辑脚本代码。

### 核心特性

- 支持 **1~100 个输入参数**
- 每个 Script 单元格的变量**相互独立**，作用域隔离
- 支持自定义图形绘制（类似 Image 函数）
- 支持导入外部 JS 模块（通过 `require`）
- 支持保存/加载内部状态（通过 `save`/`load` 方法）

### 内存限制

| 项目 | 限制 |
|------|------|
| 脚本内存总量 | **4 MB** |
| 栈内存 | **64 kB** |

> **注意**: 未使用的函数应删除，避免不必要的内存分配和 CPU 消耗。

---

## 2. 脚本基本结构

### 2.1 构造函数 (Constructor)

脚本必须将 `module.exports` 设置为一个**构造函数**。该函数在脚本编译后被调用（无参数），用于初始化 Tool 对象的实例。

内部变量应存储为 `this` 对象的属性，在 `run`、`save`、`load`、`draw` 函数中均可访问。

```js
"use strict";

function Script() {
    // 初始化内部变量
    this.total = 0;
    this.lines = [];
}

module.exports = Script;
```

> **注意**: 构造函数名称（如 `Script`/`Tool`）是任意的，不影响 Script 函数的运行，但在整个源代码中必须保持一致使用。

### 2.2 基本框架

```js
"use strict";

function Script() {
    // 构造函数 - 初始化内部状态
    this.myVar = 0;
}

module.exports = Script;

// ========== 必需方法 ==========

Script.prototype.run = function(arg0, arg1) {
    // 每次电子表格更新时调用
    // arg0, arg1 为输入参数
    return this.myVar;
};

// ========== 可选方法 ==========

Script.prototype.draw = function(gr) {
    // 绘制图形到用户界面
    gr.plotString("Value: " + this.myVar, 100, 200, 0xFF0000);
};

Script.prototype.save = function() {
    // 保存内部状态到 job 文件
    return { "myVar": this.myVar };
};

Script.prototype.load = function(saved) {
    // 从 job 文件恢复内部状态
    this.myVar = saved.myVar;
};
```

---

## 3. 生命周期方法

### 3.1 `run` 方法（必需）

- **调用时机**: 每次 Script 函数执行时（电子表格更新时）
- **参数**: Script 函数的输入参数，按顺序传入
- **返回值**: 可以是数字、字符串、对象、数组、Image、Binary、Blob/Edge/Histogram/Patterns 数据结构、Shape 对象等
- **异常处理**: 抛出异常会导致单元格返回 `#ERR`，错误信息可通过 `GetErrorString` 获取

```js
Script.prototype.run = function(a, b) {
    // JavaScript 函数都是变参的，不需要声明相同数量的参数
    // 也可以通过 arguments 数组访问所有参数
    if (!(a > 0))
        throw new Error("参数必须是正数");
    
    this.total += a;
    return this.total;
};
```

> **注意**: 在编辑 Script 函数或关闭 Edit Script Dialog 时，`run` 等方法可能被**多次调用**（如保存时为确保编译正确会额外执行）。

### 3.2 `draw` 方法（可选）

- **调用时机**: 需要向用户界面发送图形时（在 `run` 之后调用，但不是每次 `run` 都会触发）
- **参数**: `gr` - 图形上下文对象
- **用途**: 绘制自定义图形，如点、线、圆、字符串等

```js
Script.prototype.draw = function(gr) {
    gr.plotPoint(this.x, this.y, "Found", 0xFF00FF);
    gr.plotLine(0, 0, 100, 100, "Line", 0xFF0000);
    gr.plotString("Result", 50, 50, 0x00FF00);
};
```

> **注意**: `draw`、`save`、`load` 中抛出异常只会记录到控制台并退出该方法，不会导致 Script 函数返回 `#ERR`。

### 3.3 `save` 方法（可选）

- **调用时机**: 保存 tool block、复制到剪贴板、undo 备份时
- **返回值**: 包含需要保存的内部变量的对象

```js
Script.prototype.save = function() {
    return { "total": this.total, "avg": this.average };
};
```

### 3.4 `load` 方法（可选）

- **调用时机**: 加载 tool block、从剪贴板粘贴、undo 恢复时
- **参数**: `saved` - `save()` 返回的对象

```js
Script.prototype.load = function(saved) {
    this.total = saved.total;
    this.average = saved.avg;
};
```

---

## 4. Run 方法返回值

`run` 方法可以返回以下类型的值：

| 返回类型 | 说明 |
|---------|------|
| 数字 | 显示为数值结果 |
| `true` (1) / `false` (0) | 布尔值 |
| 字符串 | 显示为字符串结果 |
| Binary 数据结构 | 二进制数据 |
| Blob / Edge / Histogram / Patterns | 视觉数据结构 |
| Shape 对象 | 图形形状对象 |
| 对象 (Object) | 显示为 Object 数据结构 |
| 数组 (Array) | 显示为 Array 数据结构 |
| Image | 图像对象 |
| `null` / `undefined` / 无返回 | 视为空对象 |

> **注意**: 
> - 返回数字/字符串时，Script 函数直接显示该值
> - 返回对象时，显示为 Object 数据结构
> - 返回数组时，显示为 Array 数据结构

---

## 5. 图形绘制 (Draw)

`draw` 方法通过图形上下文对象 `gr` 绘制图形。图形显示在 Script 函数**第一个输入参数中的 AcquireImage 单元格**对应的图像上；如果没有图像输入参数，则显示在电子表格中**第一个 AcquireImage 单元格**的图像上。

### 5.1 颜色格式

颜色参数为 **24 位 RGB 值**，格式为 `0xRRGGBB`：

```
颜色值 = (red * 65536) + (green * 256) + blue
```

| 颜色 | 值 |
|------|-----|
| 红色 | `0xFF0000` |
| 绿色 | `0x00FF00` |
| 蓝色 | `0x0000FF` |
| 黄色 | `0xFFFF00` |
| 白色 | `0xFFFFFF` |
| 黑色 | `0x000000` |

### 5.2 绘制方法

```js
// 绘制圆弧
gr.plotArc(image, centerX, centerY, startX, startY, endX, endY, name, color [, show]);

// 绘制圆
gr.plotCircle(image, X, Y, radius, name, color [, show]);

// 绘制十字
gr.plotCross(image, X, Y, angle, width, height, name, color [, show]);

// 绘制线段
gr.plotLine(image, X0, Y0, X1, Y1, name, color [, show] [, startAdornment] [, endAdornment]);

// 绘制点
gr.plotPoint(image, X, Y, name, color [, show]);

// 绘制区域
gr.plotRegion(image, X, Y, width, height, angle, curve, name, color [, show]);

// 绘制字符串
gr.plotString(image, string, point, color, fontSize, font [, show]);
```

> **注意**: 
> - 方括号 `[]` 中的参数为可选
> - `plotRegion` 支持额外标志：
>   - `0x100`: 只绘制区域外框，无 X/Y 轴标签
>   - `0x200`: 绘制填充（实心）区域，无 X/Y 轴标签
> - Script 中的绘图方法**不需要 image 作为第一个参数**（与 Image Functions 不同）

### 5.3 绘制 Shape 对象

```js
// 绘制形状（等效于 PlotPolygon / PlotCompositeRegion）
gr.plotShape(shape, name, color [, show]);
```

> **注意**: 多边形长度超过约 4950 个点时不会显示。

---

## 6. 数据结构与输入输出

### 6.1 支持的输入类型

Script 函数的每个参数可以是以下类型之一：

- 数字
- 字符串
- 图像 (Image)
- 事件 (Event)
- Shape 对象
- Binary 数据结构
- Blob / Edge / Histogram / Patterns 数据结构
- 对象或数组（由另一个 Script 函数返回）

> **注意**:
> - 如果参数是单元格范围，会展开为范围内的各个单元格引用
> - Blob/Edge/Histogram/Patterns 对象传入 Script 后**只在 run 方法执行期间有效**。如果存储到内部变量并在 draw 中使用，run 返回后对象将失效，调用其方法会报错。

### 6.2 Blob 数据结构方法

Blob 数据结构是**只读**的，支持以下方法（对应 Vision Data Access 函数）：

| 方法 | 说明 |
|------|------|
| `blobs.getNFound()` | 返回找到的 blob 数量 |
| `blobs.getAngle(index)` | 返回 blob 质心的角度 |
| `blobs.getArea(index)` | 返回 blob 面积（像素） |
| `blobs.getCenterOfMassX(index)` | 返回质心 X 坐标 |
| `blobs.getCenterOfMassY(index)` | 返回质心 Y 坐标 |
| `blobs.getColor(index)` | 返回 blob 颜色值 (0.0=黑, 1.1=白) |
| `blobs.getElongation(index)` | 返回伸长率 |
| `blobs.getHeight(index)` | 返回 blob 高度（像素） |
| `blobs.getHoles(index)` | 返回 blob 内部孔洞数量 |
| `blobs.getMaxX(index)` | 返回 blob 最右侧 X 坐标 |
| `blobs.getMaxY(index)` | 返回 blob 最下方 Y 坐标 |
| `blobs.getMinX(index)` | 返回 blob 最左侧 X 坐标 |
| `blobs.getMinY(index)` | 返回 blob 最上方 Y 坐标 |
| `blobs.getPerimeter(index)` | 返回 blob 周长（像素） |
| `blobs.getScore(index)` | 返回 blob 分数 (0-100) |
| `blobs.getSpread(index)` | 返回 Spread 值 |
| `blobs.getThresh()` | 返回阈值 |
| `blobs.getWidth(index)` | 返回 blob 宽度（像素） |
| `blobs.getX(index)` | 返回 X 坐标 |
| `blobs.getY(index)` | 返回 Y 坐标 |
| `blobs.getPolygon(index, [show])` | 返回 blob 边界的 Polygon 数据结构 |

### 6.3 Edge 数据结构方法

| 方法 | 说明 |
|------|------|
| `edges.getNFound()` | 返回找到的边缘数量 |
| `edges.getAngle(index)` | 返回边缘角度 |
| `edges.getContrast()` | 返回平均对比度 (0-255) |
| `edges.getEdgeDistance(index)` | 返回边缘对之间的距离（像素） |
| `edges.getPosition(index)` | 返回边缘或边缘对中心的区域 X 位置 |
| `edges.getRadius(index)` | 返回圆或弧的半径（像素） |
| `edges.getScore(index)` | 返回分数 (0-100) |
| `edges.getX(index, endpoint)` | 返回 X 坐标 (endpoint: 0=上/左, 1=下/右) |
| `edges.getY(index, endpoint)` | 返回 Y 坐标 |

### 6.4 Histogram 数据结构方法

| 方法 | 说明 |
|------|------|
| `hist.getArea(index)` | 返回提取的直方图面积（像素数） |
| `hist.getValue(index)` | 返回指定 bin (0-255) 中的值数量 |

### 6.5 Patterns 数据结构方法

| 方法 | 说明 |
|------|------|
| `patterns.getNFound()` | 返回找到的匹配数量 |
| `patterns.getAngle(index)` | 返回角度值 |
| `patterns.getScore(index)` | 返回匹配分数 |
| `patterns.getX(index)` | 返回 X 坐标 |
| `patterns.getY(index)` | 返回 Y 坐标 |
| `patterns.getScale(index)` | 返回匹配大小百分比 |
| `patterns.getClutter(index)` | 返回杂波分数 |
| `patterns.getContrast(index)` | 返回对比度 |
| `patterns.getCoverage(index)` | 返回覆盖百分比 |

### 6.6 Binary 数据结构

Binary 数据（如 `BStringf` 或 `ReadDevice` 返回）传入 Script 时，数据被复制到 JavaScript **DataView** 对象中。可以读取/写入单个字节，或创建 Int32Array、Uint8Array 等视图。

```js
// 读取 Binary 数据为 Float32Array
var input = new Float32Array(data.buffer);

// 创建 14 字节的 Binary 数据
var data = new DataView(new ArrayBuffer(14));
data.setUint32(2, count);           // 大端序
data.setFloat64(6, delay, true);    // 小端序
return data;
```

---

## 7. 模块系统

### 7.1 模块加载

所有 In-Sight JavaScript 代码以**模块**形式加载，提供：
- 顶层作用域隐私保护
- 从其他模块导入单例对象
- 导出自己的 API

基于 CommonJS/NodeJS 风格，但**不保证与它们的模块兼容**。

### 7.2 require 函数

```js
var quad = require('quadratic');  // 导入模块
```

- `require` 接受模块标识符（文件名），返回导入模块的导出 API
- 如果模块无法加载，返回 `undefined`（不抛出异常）
- 如果文件名没有扩展名，自动追加 `.js`

### 7.3 module.exports

```js
module.exports = Script;  // 将 Script 构造函数导出为模块 API
```

- `module` 是一个对象，具有 `exports` 属性
- `exports` 是 `module.exports` 的初始值，作为便利变量提供
- 可以直接给 `module.exports` 赋值新对象，或给 `exports` 添加属性

### 7.4 文件路径

文件路径可以是：
- **本地文件系统**的简单文件名
- **FTP 服务器**的 URL（格式：`ftp://<user>:<password>@<host>:<port>/<url-path>`）

---

## 8. 全局对象

### 8.1 process 对象

```js
// 获取系统启动后的秒数（浮点数，亚微秒精度）
var start = process.uptime();

// 高分辨率时间测量 [秒, 纳秒]
var start = process.hrtime();
var diff = process.hrtime(start);
var elapsedSeconds = diff[0] + diff[1] / 1e+9;

// 休眠指定毫秒数
process.sleep(ms);

// 获取内存使用情况
var mem = process.memoryUsage();
// mem.heapTotal - 堆内存总量
// mem.heapUsed  - 已使用堆内存
```

### 8.2 console 对象

```js
console.log("普通消息");     // 普通文本输出到 Output 面板
console.warn("警告消息");    // 黑字黄底输出到 Output 面板
console.error("错误消息");   // 黑字红底输出到 Output 面板
```

---

## 9. 文件系统模块

通过 `require("fs")` 获取文件系统模块，所有方法名以 `Sync` 结尾（同步执行）。

```js
var fs = require("fs");
```

| 方法 | 说明 |
|------|------|
| `fs.appendFileSync(file, data)` | 追加数据到文件（不存在则创建） |
| `fs.existsSync(file)` | 返回文件是否存在 |
| `fs.readFileSync(file [, "binary"])` | 读取文件内容为 UTF-8 字符串；加 `"binary"` 返回 DataView |
| `fs.readdirSync(path)` | 读取目录内容，返回文件名数组 |
| `fs.statSync(path)` | 返回文件状态对象（含 `size` 属性） |
| `fs.unlinkSync(file)` | 删除文件（不存在则无操作） |
| `fs.writeFileSync(file, data)` | 写入文件（覆盖已有文件） |

> **注意**: 
> - 读取系统文件会抛出异常
> - 写入系统文件会抛出异常
> - 支持 `.txt`, `.htm`, `.xml`, `.js`, `.css`, `.rtf`, `.json` 等文件类型

---

## 10. Shape 对象

通过 `require('cognex')` 模块创建 Shape 对象，可作为 Script 函数的输入参数或返回值。

```js
var cognex = require('cognex');

var annulus  = new cognex.Annulus(150, 250, 25, 45);     // X, Y, innerRadius, outerRadius
var circle   = new cognex.Circle(50, 250, 30);            // X, Y, radius
var fixture  = new cognex.Fixture(230, 300, 45);          // X, Y, theta
var line     = new cognex.Line(270, 530, 290, 50);        // x0, y0, x1, y1
var point    = new cognex.Point(330, 500);                // X, Y
var region   = new cognex.Region(440, 300, 40, 50, 30, 45); // X, Y, w, h, angle, curve

// Polygon
var polygon = new cognex.Polygon();
polygon.add(340, 230);
polygon.add(390, 210);
polygon.clear();           // 移除所有点
polygon.remove(n);         // 移除指定点
polygon.set(n, x, y);      // 设置点坐标
polygon.x(n);              // 获取点 n 的 X 坐标
polygon.y(n);              // 获取点 n 的 Y 坐标
polygon.length;            // 获取点的数量
polygon.points;            // 获取/设置点坐标数组 [x0, y0, x1, y1, ...]
polygon.clone();           // 复制多边形
polygon.reserve(count);    // 预分配空间
polygon.translate(tx, ty); // 平移所有点
polygon.scale(xScale, yScale); // 缩放所有点
polygon.transform(m00, m01, m10, m11, tx, ty); // 矩阵变换
```

### Region 对象属性访问

```js
Script.prototype.run = function(image, myRegion) {
    return {
        x: myRegion.x,
        y: myRegion.y,
        width: myRegion.w,      // 注意: 用 .w 不是 .width
        height: myRegion.h,     // 注意: 用 .h 不是 .height
        angle: myRegion.angle,
        curve: myRegion.curve
    };
};
```

---

## 11. 超时与内存限制

### 11.1 超时设置

Script 函数的每个方法（constructor、run、draw、save、load）都有超时限制：

| 参数 | 默认值 | 范围 |
|------|--------|------|
| 超时时间 | 10 秒 | 1 ~ 60000 毫秒 |
| 粒度 | 1 秒 | 1 ~ 10000 毫秒 |

超时目的是防止脚本死循环锁定视觉系统，不是用于限制作业运行时间。

```js
// 修改共享的 Script 超时时间
var scriptConfig = process.binding("InSight.Camera").config.script;
scriptConfig.toolTimeout = 3000;  // 3 秒
```

### 11.2 使用 hrtime 进行精确计时

```js
var start = process.hrtime();
// ... 执行耗时操作 ...
var diff = process.hrtime(start);
var elapsedMs = diff[0] * 1000 + diff[1] / 1e+6;

if (elapsedMs > 5000) {
    // 超过 5 秒，提前退出
}
```

---

## 12. 完整示例模板

```js
"use strict";

/******************************************************************************
Summary: 两点配对脚本 - 找出 blobs 区域中最长的两条线
输入: Image(图像对象), Blobs(blobs对象)
输出: List<double>[8] - (x1, y1, x2, y2, x3, y3, x4, y4)
      x1,y1 和 x2,y2 是位置最接近的两个点
      x3,y3 是离 x1,y1 和 x2,y2 最远的点
      x4,y4 是剩余的点
******************************************************************************/

function Script() {
    this.allPoints = [];    // 存储所有 blob 中心点
    this.resultPoints = []; // 存储排序后的4个点 [p1, p2, p3, p4]
}

module.exports = Script;

// 计算两点之间的距离
function getDistance(x1, y1, x2, y2) {
    var dx = x2 - x1;
    var dy = y2 - y1;
    return Math.sqrt(dx * dx + dy * dy);
}

// 主运行函数
Script.prototype.run = function(image, blobs) {
    var nFound = blobs.getNFound();
    if (nFound < 4) {
        throw new Error("至少需要找到 4 个 blob 才能进行配对");
    }

    // 收集所有 blob 中心点
    this.allPoints = [];
    var i, j;
    for (i = 0; i < nFound; i++) {
        this.allPoints.push({
            x: blobs.getX(i),
            y: blobs.getY(i)
        });
    }

    // 如果超过 5 个点，只取前 5 个
    if (this.allPoints.length > 5) {
        this.allPoints = this.allPoints.slice(0, 5);
    }

    // 计算所有点对之间的距离，找出最短距离的一对（最接近的两个点）
    var minDist = Infinity;
    var closestPair = { idx1: 0, idx2: 1 };

    for (i = 0; i < this.allPoints.length; i++) {
        for (j = i + 1; j < this.allPoints.length; j++) {
            var p1 = this.allPoints[i];
            var p2 = this.allPoints[j];
            var dist = getDistance(p1.x, p1.y, p2.x, p2.y);
            if (dist < minDist) {
                minDist = dist;
                closestPair = { idx1: i, idx2: j };
            }
        }
    }

    // 确定4个点的索引
    var idxP1 = closestPair.idx1;
    var idxP2 = closestPair.idx2;
    var remainingIndices = [];
    for (i = 0; i < this.allPoints.length; i++) {
        if (i !== idxP1 && i !== idxP2) {
            remainingIndices.push(i);
        }
    }

    // 从剩余的两个点中，找出离 P1 和 P2 最远的点作为 P3
    var idxP3, idxP4;
    if (remainingIndices.length >= 2) {
        var idxR1 = remainingIndices[0];
        var idxR2 = remainingIndices[1];

        // 计算 R1 到 P1 和 P2 的最小距离
        var distR1ToP1 = getDistance(
            this.allPoints[idxR1].x, this.allPoints[idxR1].y,
            this.allPoints[idxP1].x, this.allPoints[idxP1].y
        );
        var distR1ToP2 = getDistance(
            this.allPoints[idxR1].x, this.allPoints[idxR1].y,
            this.allPoints[idxP2].x, this.allPoints[idxP2].y
        );
        var minDistR1 = Math.min(distR1ToP1, distR1ToP2);

        // 计算 R2 到 P1 和 P2 的最小距离
        var distR2ToP1 = getDistance(
            this.allPoints[idxR2].x, this.allPoints[idxR2].y,
            this.allPoints[idxP1].x, this.allPoints[idxP1].y
        );
        var distR2ToP2 = getDistance(
            this.allPoints[idxR2].x, this.allPoints[idxR2].y,
            this.allPoints[idxP2].x, this.allPoints[idxP2].y
        );
        var minDistR2 = Math.min(distR2ToP1, distR2ToP2);

        // 最小距离更大的那个点作为 P3（离 P1 和 P2 都更远）
        if (minDistR1 > minDistR2) {
            idxP3 = idxR1;
            idxP4 = idxR2;
        } else {
            idxP3 = idxR2;
            idxP4 = idxR1;
        }
    } else if (remainingIndices.length === 1) {
        idxP3 = remainingIndices[0];
        idxP4 = -1;
    } else {
        throw new Error("无法找到足够的点进行配对");
    }

    // 构建结果数组 [x1, y1, x2, y2, x3, y3, x4, y4]
    var result = [
        this.allPoints[idxP1].x,
        this.allPoints[idxP1].y,
        this.allPoints[idxP2].x,
        this.allPoints[idxP2].y,
        this.allPoints[idxP3].x,
        this.allPoints[idxP3].y
    ];

    if (idxP4 >= 0) {
        result.push(this.allPoints[idxP4].x);
        result.push(this.allPoints[idxP4].y);
    } else {
        result.push(0);
        result.push(0);
    }

    // 保存结果点用于绘制
    this.resultPoints = [
        this.allPoints[idxP1],
        this.allPoints[idxP2],
        this.allPoints[idxP3],
        idxP4 >= 0 ? this.allPoints[idxP4] : { x: 0, y: 0 }
    ];

    return result;
};

// 保存状态
Script.prototype.save = function() {
    return {
        "allPoints": this.allPoints,
        "resultPoints": this.resultPoints
    };
};

// 加载状态
Script.prototype.load = function(saved) {
    this.allPoints = saved.allPoints || [];
    this.resultPoints = saved.resultPoints || [];
};

// 绘制图形
Script.prototype.draw = function(gr) {
    var i;
    // 绘制所有 blob 中心点（绿色）
    for (i = 0; i < this.allPoints.length; i++) {
        gr.plotPoint(this.allPoints[i].x, this.allPoints[i].y, "Point" + i, 0x00FF00);
    }
    // 绘制 P1 到 P2 的线（红色）- 最接近的两个点
    if (this.resultPoints.length >= 2) {
        gr.plotLine(
            this.resultPoints[0].x, this.resultPoints[0].y,
            this.resultPoints[1].x, this.resultPoints[1].y,
            "ClosestPair", 0xFF0000
        );
        var midX = (this.resultPoints[0].x + this.resultPoints[1].x) / 2;
        var midY = (this.resultPoints[0].y + this.resultPoints[1].y) / 2;
        gr.plotString("Closest", midX, midY - 10, 0xFF0000);
    }
    // 绘制 P1 到 P3 的线（蓝色）
    if (this.resultPoints.length >= 3) {
        gr.plotLine(
            this.resultPoints[0].x, this.resultPoints[0].y,
            this.resultPoints[2].x, this.resultPoints[2].y,
            "P1toP3", 0x0000FF
        );
    }
    // 绘制 P2 到 P3 的线（蓝色）
    if (this.resultPoints.length >= 3) {
        gr.plotLine(
            this.resultPoints[1].x, this.resultPoints[1].y,
            this.resultPoints[2].x, this.resultPoints[2].y,
            "P2toP3", 0x0000FF
        );
    }
    // 标注 P3（黄色）- 离 P1 和 P2 最远的点
    if (this.resultPoints.length >= 3) {
        gr.plotString(
            "Farthest",
            this.resultPoints[2].x, this.resultPoints[2].y - 10, 0xFFFF00
        );
    }
};
```

---

## 附录：常用颜色速查表

| 颜色名 | RGB 值 | Hex 值 |
|--------|--------|--------|
| 红色 | (255, 0, 0) | `0xFF0000` |
| 绿色 | (0, 255, 0) | `0x00FF00` |
| 蓝色 | (0, 0, 255) | `0x0000FF` |
| 黄色 | (255, 255, 0) | `0xFFFF00` |
| 青色 | (0, 255, 255) | `0x00FFFF` |
| 品红 | (255, 0, 255) | `0xFF00FF` |
| 白色 | (255, 255, 255) | `0xFFFFFF` |
| 黑色 | (0, 0, 0) | `0x000000` |
| 橙色 | (255, 128, 32) | `0xFF8020` |

---

> 文档版本: 基于 In-Sight 26.1.0 (Build 2026 April 21, Revision: 26.1.0.133)
