# Cognex In-Sight `.jobx` 作业文件二进制结构说明

> 样本：`天窗程序模板.jobx`（932,864 字节，Cognex InSight 8000/9000 系列相机导出）
> 对照样本：`Xavier标准作业模块.cxdx`（64,000 字节，同族 `.cxdx` 格式）
> 配套数据：`天窗程序模板.jobx_20260918_100758.xlsx`（406 个单元格，304 条表达式）
> 反查依据：本仓库 `src/main/java/com/cognex/export/ExportTask.java`、`XlsxExporter.java`

文档约定：
- **置信度标记**：`[已确认]` = 经多份样本/源码交叉验证；`[推测]` = 单样本归纳、缺乏旁证；`[未知]` = 未解之谜。
- 偏移以文件绝对字节地址计；十六进制标注为 `0x........`。
- 字节序：多字节整数一律 **小端 (LE)**，IEEE-754 double 为 8 字节小端。

---

## 1. 总览

`.jobx` 文件本质是 **Cognex Job Package 对象容器**：由若干个命名对象块顺序串联而成，块之间以 0x00 填充对齐。每个对象块由固定 0x200 字节的对象头 + 变长内容区组成。容器尾部为 JSON 配置与签名对象。

样本 `天窗程序模板.jobx` 含 **9 个对象块**：

| # | 起始偏移 | 对象名 | 类型 | 内容性质 |
|---|----------|--------|------|----------|
| 0 | `0x000000` | `data/d91c21b0066c0b16ea47a61720cef56a00ea89bcc0fcc92222b5b5abf369ecf7` | 作业数据块 1 | TLV 记录流 |
| 1 | `0x01da00` | `data/58da31519c43668fb66486150b871c3b2912a2eb7d41df68f2dcbd15dd27faaf` | 作业数据块 2 | 含大量像素/数据 |
| 2 | `0x099000` | `data/0fe15ae58e16be54828bbeb58fd82395f748adbe3164b5f10a77ed9368de63eb` | 作业数据块 3 | TLV 记录流 |
| 3 | `0x0d6400` | `sheets/740d18f56f109d7bb7ba3847186a978cecccde6eb6b7e05f6ee9e7703c65801a` | 电子表格 | **编码内容**（见 §6） |
| 4 | `0x0e0e00` | `computeResourceOrchestrator/2b21a621b575d64ec415c10500c2822145e6b3b27d2d93ec470bac90158190a6` | JSON 引用 | `{"slots":[]}` |
| 5 | `0x0e1200` | `JobValidationSet/c47ed44cb6354f5322b7973a7eec3d81730d48ed951bc707e67589d910148240` | JSON 引用 | ValidationSet 配置 |
| 6 | `0x0e1600` | `EdgeAgentAdapterConfig/5d27fbcb9d078f46052e992e3a26047cc9c0715f460054cdece6c0e77c9bb220` | JSON 引用 | EdgeAgent 配置 |
| 7 | `0x0e1a00` | `Job.json` | 主作业配置 | JSON 文本（明文） |
| 8 | `0x0e3400` | `Job.json.sig` | 数字签名 | Base64 签名 |

文件末尾从 `0x0e3c00` 起至 `0x0e3c00 + 0x400 = 0xe4000`（即 932,864）为全 0x00 填充至 4KB 对齐边界。

### 1.1 对象命名约定

- `data/<sha256>` —— 内部数据块（≥1 个），存储单元格值、表达式、图像等。多个 `data/` 对象存在说明数据被分块。
- `sheets/<sha256>` —— 电子表格对象，承载单元格表达式、值、名称等核心逻辑。
- `computeResourceOrchestrator/<sha256>`、`JobValidationSet/<sha256>`、`EdgeAgentAdapterConfig/<sha256>` —— 资源/校验/适配器子配置，通过 `Job.json` 中 `{"$type":"FileRef","id":"<name>"}` 引用。
- `Job.json` —— 主作业清单，明文 JSON。
- `Job.json.sig` —— `Job.json` 内容的签名，Base64 编码。

### 1.2 cxdx 对照样本

`Xavier标准作业模块.cxdx` 是同族格式（cxdx = Cognex 代码片段包），仅含 4 个对象：

| # | 起始偏移 | 对象名 | size 字段 |
|---|----------|--------|-----------|
| 0 | `0x000000` | `version` | `1` |
| 1 | `0x000400` | `range` | `7` |
| 2 | `0x000800` | `snippet.json` | `163534` |
| 3 | `0x00f200` | `snippet.json.sig` | `54` |

cxdx 前缀为 `version`（不是 `data/`），结构更简单，但对象头布局与 jobx 完全一致。

---

## 2. 对象头固定布局（0x200 字节）`[已确认]`

每个对象块开头 0x200 字节是固定结构头：

```
偏移      长度    字段               说明
0x00      变长    name               ASCII 对象名，以 0x00 终止；剩余字节补 0
                                    （最长实测 0x5f 字符，对应 70 字节 hash 名）
0x45      0x1f    reserved           全 0 填充（实测无任何非零字节）
0x64      4+     "664\0"             固定版本标记（"664" + NULL）
0x6c      4+     "0\0"               ASCII "0" + NULL  [推测: 某种 subtype/flag]
0x7c      变长    size_ascii          ASCII 十进制数字串 + NULL
                                    （推测: 对象内容的某种长度/计数，§4）
0x88      4+     "0\0"               ASCII "0" + NULL
0x94      变长    seq_ascii           ASCII 6位数字串 + NULL
                                    （推测: 序列号/时间戳，§4.2）
0x9b      3      " 0\0"              ASCII " 0"（含前导空格）+ NULL [推测: 状态位]
0xa0      0x60   padding            全 0 填充至偏移 0x100
0x100     0x100  reserved2          全 0 填充至偏移 0x200（仅 data/* 对象；
                                    sheets/JSON 对象常从 0x100 起即有内容）
0x200     ---    content_start      真正的内容数据起点
```

> 注：`0x100..0x200` 区段在 `data/*` 对象中恒为 0，在 `sheets/<hash>`、JSON 对象中可能从 0x100 起即出现内容字节。这表明对象头实际长度可能依对象类型而不同（`data/*` 用 0x200 头；其他类型用 0x100 头）。`[推测]`

### 2.1 对象头 hexdump 示例（对象 #0，作业数据块 1）

```
00000000: 64 61 74 61 2f 64 39 31 63 32 31 62 30 30 36 36   data/d91c21b0066
00000010: 63 30 62 31 36 65 61 34 37 61 36 31 37 32 30 63   c0b16ea47a61720c
...（hash 共 64 个 hex 字符）...
00000040: 39 65 63 66 37 00 00 00 00 00 00 00 00 00 00 00   9ecf7...........   ← name 终止于 0x45
00000050: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ................   ← reserved
00000060: 00 00 00 00 36 36 34 00 00 00 00 00 30 00 00 00   ....664.....0...   ← "664\0" + "0\0"
00000070: 00 00 00 00 00 00 00 00 00 00 00 00 33 35 33 31   ............3531   ← size_ascii
00000080: 34 30 00 00 00 00 00 00 30 00 00 00 00 00 00 00   40......0.......   ← "353140\0" + "0\0"
00000090: 00 00 00 00 30 31 33 35 31 35 00 20 30 00 00 00   ....013515. 0....   ← "013515\0" + " 0\0"
000000a0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ................   ← 0xa0..0x200 全 0
...
00000200: 8a 0b 03 20 00 00 00 80 81 02 03 20 14 00 00 80   ... ....... ....   ← content_start
```

### 2.2 对象头字段对照（9 个对象实测值）

| # | name | 0x64 | 0x6c | 0x7c (size) | 0x88 | 0x94 (seq) | 0x9b |
|---|------|------|------|-------------|------|------------|------|
| 0 | `data/d91c21b0...` | 664 | 0 | 353140 | 0 | 013515 | ` 0` |
| 1 | `data/58da3151...` | 664 | 0 | 1731033 | 0 | 013416 | ` 0` |
| 2 | `data/0fe15ae5...` | 664 | 0 | 750637 | 0 | 013540 | ` 0` |
| 3 | `sheets/740d18f5...` | 664 | 0 | 123554 | 0 | 014034 | ` 0` |
| 4 | `computeResourceOrchestrator/...` | 664 | 0 | 14 | 0 | 017163 | ` 0` |
| 5 | `JobValidationSet/...` | 664 | 0 | 276 | 0 | 014767 | ` 0` |
| 6 | `EdgeAgentAdapterConfig/...` | 664 | 0 | 125 | 0 | 016365 | ` 0` |
| 7 | `Job.json` | 664 | 0 | 13260 | 0 | 003057 | ` 0` |
| 8 | `Job.json.sig` | 664 | 0 | 54 | 0 | 003415 | ` 0` |

所有对象的 `0x64`/`0x6c`/`0x88`/`0x9b` 字段恒为 `664`/`0`/`0`/` 0`，唯独 `0x7c`（size）和 `0x94`（seq）有差异。

---

## 3. 对象内容区布局

### 3.1 内容区起点 `[已确认]`

- `data/*` 对象：内容起点 = `obj_start + 0x200`（即对象头 0x200 字节）
- `sheets/*` 对象、JSON 对象、`.sig` 对象：内容起点 = `obj_start + 0x100`（对象头 0x100 字节）
- 内容区到下一个对象起点之间为 0x00 填充。

### 3.2 内容区到下一对象的间距（实测）

| 对象 | 内容起点 | 下一对象起点 | 间距字节 | 非零字节 | size 字段 | 比例 |
|------|----------|--------------|----------|----------|-----------|------|
| data/d91c21b0 | 0x000200 | 0x01da00 | 121,088 | 72,954 | 353,140 | 2.92 |
| data/58da3151 | 0x01da00+0x200 | 0x099000 | 505,088 | 489,315 | 1,731,033 | 3.43 |
| data/0fe15ae5 | 0x099000+0x200 | 0x0d6400 | 250,624 | 188,873 | 750,637 | 3.00 |
| sheets/740d18f5 | 0x0d6600 | 0x0e0e00 | 43,008 | 42,402 | 123,554 | 2.86 |
| computeResourceOrchestrator | 0x0e0f00 | 0x0e1200 | 768 | 12 | 14 | 0.02 |
| JobValidationSet | 0x0e1300 | 0x0e1600 | 768 | 190 | 276 | 0.36 |
| EdgeAgentAdapterConfig | 0x0e1700 | 0x0e1a00 | 768 | 85 | 125 | 0.16 |
| Job.json | 0x0e1b00 | 0x0e3400 | 6,400 | 5,808 | 13,260 | 2.07 |
| Job.json.sig | 0x0e3500 | EOF+对齐 | 1,792 | 44 | 54 | 0.03 |

`size` 字段与内容字节数**无简单线性关系**（比例 0.02 ~ 3.43），语义见 §4.1。

---

## 4. 对象头元数据字段语义

### 4.1 `0x7c` size 字段 `[未知]`

- 看似是 ASCII 十进制整数，但与内容字节数比例不固定。
- 对 JSON 对象（Job.json、ValidationSet、EdgeAgent）观察：
  - Job.json 实际 JSON 字符数 5,808，size=13,260，比例 ≈ 2.28
  - JobValidationSet JSON 字符数 190，size=276，比例 ≈ 1.45
  - EdgeAgent JSON 字符数 85，size=125，比例 ≈ 1.47
  - computeResourceOrchestrator JSON `{"slots":[]}` = 11 字符，size=14
- 推测 `size` 是 **JSON 序列化后某种"token 计数"或包含元数据后的扩展大小**，而非字节数。也可能是 *未压缩前的字节数*，而容器内存储的是压缩形式（但 JSON 对象的明文 ASCII 表明无压缩）。`[未知]`

### 4.2 `0x94` seq 字段 `[推测]`

- 6 位 ASCII 数字，范围 `003057` ~ `017163`。
- 对象 0~3（data/sheets）的 seq 在 `013XXX` ~ `017XXX`；对象 7~8（Job.json/sig）的 seq 在 `0030XX` ~ `0034XX`。
- 推测：**某种内部序列号或时间戳**（MMDDhhmm 截断？版本号？）。cxdx 样本中类似字段为 `002164` ~ `004365`，与 jobx 数值范围重叠。
- 同一文件内不同对象 seq 数值差异较大，说明它**不是简单的全局版本号**，而是每个对象独立维护的字段。`[推测]`

### 4.3 `0x9b` " 0" 字段 `[推测]`

- 固定 ASCII `" 0"`（一个空格 + `0` + NULL）。
- 全部 9 个对象相同，cxdx 4 个对象也相同。
- 推测：某个布尔/状态标志位的字符串表示，恒为 `0`。`[推测]`

### 4.4 `0x64` "664" 标记 `[已确认]`

- 固定字符串 `"664"` + NULL，9 个对象全部一致，cxdx 4 个对象也一致。
- **用于对象头定位**：扫描文件中所有 `664\x00` 模式，向前回退 0x64 字节即为对象起点。本文件即用此方法定位全部 9 个对象。

### 4.5 `0x6c` "0" 与 `0x88` "0" `[已确认]`

- 恒为 `"0"` + NULL。
- 推测：可能是对象状态/版本/标志位，恒为 0。

---

## 5. `data/*` 对象内容：TLV 记录流 `[推测]`

`data/*` 对象内容（自 `obj_start+0x200` 起）呈现重复的 8 字节 tag 头 + 后续数据的形式。

### 5.1 头部前 0x100 字节 hexdump（data/d91c21b0）

```
00000200: 8a 0b 03 20 00 00 00 80 81 02 03 20 14 00 00 80   ... ....... ....
00000210: 00 00 00 00 00 00 e0 3f 00 00 00 00 00 00 e0 3f   .......?.......?
00000220: 01 00 00 00 82 02 03 60 04 00 00 80 01 00 00 00   .......`........
00000230: 82 02 03 20 00 00 00 80 ff 7f 11 40 00 d6 01 00   ... .......@....
00000240: 50 77 1b dd 68 14 8c 40 4a 80 6c 61 b2 b3 37 40   Pw..h..@J.la..7@
00000250: 00 00 00 00 00 00 14 c0 00 00 00 00 00 00 2c c0   ..............,.
00000260: 4e 2c 9f 5e 08 27 8d 40 73 4d 0f fd 33 ba 37 40   N,.^.'.@sM..3.7@
```

### 5.2 8 字节 tag 头格式（推测）

```
偏移  字节   字段           说明
0     1      tag1           类型/类别高字节
1     1      tag2           类型/类别低字节
2     1      0x03           固定 marker（实测全部为 0x03）
3     1      type           子类型/数据类型指示
4     4      val4           4 字节小端整数；最高字节恒为 0x80
                            低 3 字节为数值（如 0x14 = 20，0x04 = 4）
```

7 个匹配的 tag 头（在 `0x200..0x1e8ba` 范围内）：

| 偏移 | bytes | tag1 | tag2 | type | val4 |
|------|-------|------|------|------|------|
| 0x000200 | `8a 0b 03 20 00 00 00 80` | 8a | 0b | 20 | 0x80000000 |
| 0x000208 | `81 02 03 20 14 00 00 80` | 81 | 02 | 20 | 0x80000014 |
| 0x000224 | `82 02 03 60 04 00 00 80` | 82 | 02 | 60 | 0x80000004 |
| 0x000230 | `82 02 03 20 00 00 00 80` | 82 | 02 | 20 | 0x80000000 |
| 0x00be61 | `19 21 03 60 57 73 40 80` | 19 | 21 | 60 | 0x80407357 |
| 0x01d21f | `40 98 03 53 e9 8a e9 80` | 40 | 98 | 53 | 0x80e98ae9 |
| 0x01d84c | `71 0d 03 20 04 00 00 80` | 71 | 0d | 20 | 0x80000004 |

> 全段仅 7 处精确匹配该 8 字节模板，说明 **data/* 对象的内部结构远比简单 TLV 复杂**——大量数据是连续 double 数组、整数数组等"裸数据"段。`[推测]`

### 5.3 跟随数据的类型

tag 头之后的数据可能是：

| C 类型 | 字节数 | 实测例 |
|--------|--------|--------|
| IEEE-754 double LE | 8 | 0x210: `00 00 00 00 00 00 e0 3f` = 0.5 |
| int32 LE | 4 | 0x220: `01 00 00 00` = 1 |
| double 对 | 16 | 0x240..0x24f: 898.551, 23.7019 |
| 连续 double 数组 | N×8 | 0x250 起的 `-0.25, -0.75, ...` 系列 |

### 5.4 主数据段（图像/像素数据）

`data/58da3151` 对象内 `0x1e8ba..0x5d769`（257,711 字节）是文件最大的非零段，字节值集中在 `0x05..0x14` 范围（小灰度值），符合 **8-bit 灰度图像数据** 特征。这与导出 xlsx 中 `A0` 单元格表达式 `AcquireImage()` 一致——`data/*` 对象承载相机采集的原始图像像素。`[已确认]`

第二大段 `0x99eba..0xaecfa`（85,568 字节）字节值集中在 `0x6b..0xbc`，类似另一幅图像或同图像的另一个通道。`[推测]`

---

## 6. `sheets/<hash>` 对象：编码内容 `[已确认-加密]`

### 6.1 核心结论

**电子表格（单元格名、值、表达式、位置）在 `sheets/<hash>` 对象内不以明文 ASCII/UTF-16 存储**。

验证方法（用 xlsx 反查）：
- xlsx 中 165 个不同表达式（如 `AcquireImage()`、`FormatInputBuffer("il:~s34:~s34")`、`Concatenate(A27,"\",...)`、`ListBox("Auto","Debug")`），全部在 .jobx 二进制中 **ASCII 模式未找到**，UTF-16LE 模式也未找到。
- 125 个不同值（含中文 `❒Image`、`1.结果`、`总结果`、`Pin1 XDistance` 等），ASCII 找到 18 处但均为巧合的字节组合（如 `Images` 出现在 `Job.json` 中，而非 sheets）。

### 6.2 sheets 内容字节统计

- 内容区 `0x0d6600..0x0e0e00`，长 43,008 字节，非零 42,402 字节。
- 前 16 字节：`09 b9 2b 5a 0b eb 6a 0c 48 b9 5c 46 17 fe 7b 0c`
- 字节频率分布**相当均匀**（top 10 字节各占 2.9%~4.2%），无明显偏置：

| 字节 | 占比 |
|------|------|
| 0x02 | 4.2% |
| 0x5e | 4.1% |
| 0xb7 | 4.1% |
| 0x23 | 4.0% |
| 0x0c | 3.7% |
| 0xb9 | 3.7% |
| 0x2d | 3.7% |
| 0x50 | 3.6% |
| 0x42 | 3.0% |
| 0x1e | 2.9% |

### 6.3 编码/加密特征 `[推测]`

- **非压缩**：zlib、raw deflate、gzip 均解压失败。
- **非单字节 XOR**：遍历 0~255 个 key，无一能在前 4KB 中产生 `Acquire` 子串。
- **字节均匀分布**符合**强加密/密文**特征，或基于密钥流的流密码。
- 注意 `0x5e` 与 `0xb7` 是按位取反关系（`0x5e ^ 0xff = 0xa1`，不直接是 `0xb7`），不存在简单的位翻转关系。
- 头部字段 `size=123554` 与内容字节数 43,008 之比 ≈ 2.86，与 data/* 对象的比例（2.92、3.43、3.00）接近，可能 size 字段在所有对象中是统一的"扩展后字符/token 数"度量。`[推测]`

**未解之谜**：sheets 对象的具体加密算法和密钥来源未确认。可能依赖相机固件密钥或与对象名 `<hash>` 派生的密钥。需要逆向 Cognex InSight 固件或 `.jobx` 加载器（在相机内部）才能进一步确认。

---

## 7. JSON 对象内容 `[已确认]`

### 7.1 Job.json（对象 #7）

- 内容区 `0x0e1b00..0x0e3400`（6,400 字节），其中明文 JSON 占 5,808 字节，剩余为 0x00 填充。
- JSON 起点：`0x0e1c00`（对象头偏移 0x100 处）。
- 内容是单行 JSON，根对象含字段：`AcqSettings`、`EdgeAgentAdapterConfig`（FileRef）、`JobSettings`、`JobValidationSet`（FileRef）、`JobVersion`（`"24.4"`）、`Metadata`（`CameraType: IS8905M`，`FirmwareVersion: 26.1.0 (3930)`，`JobType: Spreadsheet`）、`PerDeviceAcqSettings`、`Sheets.Inspection`（FileRef → `sheets/740d18f5...`）、`computeResourceOrchestrator`（FileRef）。
- 子对象通过 `{"$type":"FileRef","id":"<objname>"}` 引用其他对象，正是容器格式的设计动机。

### 7.2 computeResourceOrchestrator（对象 #4）

JSON 内容仅 11 字符：`{"slots":[]}`，位于 `0x0e1000` 起。

### 7.3 JobValidationSet（对象 #5）

JSON 约 190 字符：
```json
{"$type":"ValidationSet","cleanupActions":[],"dataRoot":null,"name":"",
 "variants":[{"$type":"ValidationVariant","id":0,"name":"Job Tests",
              "preConditions":[],"records":[],"setupActions":[]}]}
```

### 7.4 EdgeAgentAdapterConfig（对象 #6）

JSON 约 76 字符：
```json
{"$type":"EdgeAgentAdapterConfig","metrics":[],"sendMetricsWithoutAcquisition":false}
```

---

## 8. `.sig` 签名对象 `[已确认]`

### 8.1 Job.json.sig（对象 #8）

- 内容区 `0x0e3500..0x0e3c00`（1,792 字节），但仅 44 字节非零。
- 签名正文：`0x0e3600` 起 40 字节 ASCII：`685o4vhjkBCxoj1DwcGD80kS8kS8O71Zv5c7r5u1cvI=`
- Base64 解码得 **30 字节**：`eb ce 68 e2 f8 63 90 10 b1 a2 3d 43 c1 c1 83 f3 49 12 f2 44 bc 3b bd 59 bf 97 3b af 9b b5`
- 30 字节 ≠ 标准 HMAC-SHA256（32 字节），也不是 RSA 签名（典型 128/256 字节）。`[推测]` 可能是某种截断的 MAC 或自定义签名算法。

### 8.2 cxdx 的 snippet.json.sig

cxdx 中 `snippet.json.sig` 对象内容：`5V/MPh/VX5SgbQ61pVGtpTEu6A5teK6zmyRSyBlb5SI=`（44 字符 Base64，解码 32 字节）—— **32 字节正好是 SHA-256 摘要长度**。这与 jobx 的 30 字节签名不一致。`[推测]` 两种签名算法不同。

---

## 9. cxdx 格式对照 `[已确认]`

`Xavier标准作业模块.cxdx`（64,000 字节）：

| # | obj_start | name | size | seq |
|---|-----------|------|------|-----|
| 0 | 0x000000 | `version` | 1 | 002547 |
| 1 | 0x000400 | `range` | 7 | 002164 |
| 2 | 0x000800 | `snippet.json` | 163534 | 004121 |
| 3 | 0x00f200 | `snippet.json.sig` | 54 | 004365 |

cxdx 头部前缀为 `version`（ASCII `76 65 72 73 69 6f 6e 00`），不是 `data/<hash>`。对象头布局完全相同（0x64 处 `664` 标记，0x7c 处 size，0x94 处 seq）。cxdx 中 `snippet.json`（size=163534）和 `snippet.json.sig` 的关系对应 jobx 中 `Job.json` 和 `Job.json.sig`，表明这是 **Cognex 对象容器的标准签名机制**。

---

## 10. 用导出 xlsx 反查 .jobx 的方法

仓库导出工具（`ExportTask.java` + `XlsxExporter.java`）通过 CogSocket HMI 协议**在线**读取作业单元格，而非直接解析 .jobx 二进制：

1. `ExportTask.run()` 通过 FTPS 枚举相机 .jobx → `loadJob(name)` 加载 → `getLatestResult()` 拿 `cells`（含 `location`/`name`/`type`/`data`/`expression`）→ `getCellExpression(location)` 逐个取表达式。
2. `XlsxExporter.exportCells()` 把 cells 排序后写入 sheet "单元格"（列：位置/名称/类型/值/表达式）与 sheet "位置布局"（A0~Z599 坐标还原，表达式入批注）。

xlsx 列含义对照：

| xlsx 列 | 字段含义 | CogSocket JSON 字段 |
|---------|----------|---------------------|
| 位置 | 单元格坐标（如 `A0`、`$B$17`） | `location` |
| 名称 | 单元格显示名 | `name` |
| 类型 | 结果类型（`HmiStringResult`/`HmiFloatResult`/`HmiListBoxResult` 等） | `$type` |
| 值 | 运行时结果 | `data` |
| 表达式 | 计算公式 | `expression`（需单独 `getCellExpression` 调用） |

**反查二进制结论**：xlsx 中的字符串（表达式、中文值）**无法在 .jobx 文件明文中直接找到**，因为它们存储在 `sheets/<hash>` 对象的**加密/编码内容**中。要解析 .jobx 单元格内容，必须：
- 要么逆向 Cognex 相机固件中的解密逻辑；
- 要么通过 CogSocket HMI 协议在线获取（即现有导出工具的做法）；
- 要么从相机固件中提取密钥/算法。

---

## 11. 文件总体布局图

```
┌────────────────────────────────────────────────────────────────────────┐
│ Offset       内容                                                      │
├────────────────────────────────────────────────────────────────────────┤
│ 0x000000     [对象0] data/d91c21b0...作业数据块1 (TLV记录+元数据)       │
│   0x000200     └ content start                                         │
│   0x001e8ba    └ 主数据段1（图像/像素，~258KB）                          │
│ 0x01da00     [对象1] data/58da3151...作业数据块2 (~505KB，含主图像)     │
│ 0x099000     [对象2] data/0fe15ae5...作业数据块3 (~251KB)              │
│ 0x0d6400     [对象3] sheets/740d18f5...电子表格（加密内容，~43KB）      │
│ 0x0e0e00     [对象4] computeResourceOrchestrator/... (JSON 11字符)     │
│ 0x0e1200     [对象5] JobValidationSet/... (JSON ~190字符)               │
│ 0x0e1600     [对象6] EdgeAgentAdapterConfig/... (JSON ~76字符)          │
│ 0x0e1a00     [对象7] Job.json (JSON 5808字符)                           │
│ 0x0e3400     [对象8] Job.json.sig (Base64 40字符 → 30字节签名)          │
│ 0x0e3c00     全 0x00 填充至 4KB 边界 = 0x0e4000 (=932,864)              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 12. 未解之谜清单

1. **sheets 对象加密算法**：43KB 内容字节分布均匀，非压缩非单字节 XOR。需固件逆向。
2. **size 字段语义**：比例 0.02~3.43，与字节数无简单关系，可能是 token 数或解压后字节数。
3. **seq 字段语义**：6 位数字串，每个对象独立，疑似序列号/时间戳但语义不明。
4. **`data/*` 对象内部 TLV 结构的完整 schema**：仅识别出 8 字节 tag 头（`tag1 tag2 03 type val4`，末字节 0x80）和跟随数据类型（double/int32），完整字段含义未确认。
5. **Job.json.sig 30 字节签名算法**：与 cxdx 32 字节签名长度不同，可能不同算法。
6. **对象头 `0x100..0x200` 区段差异**：`data/*` 对象恒为 0，其他对象从 0x100 起即有数据，暗示对象头长度可能依类型变化（0x100 vs 0x200）。
7. **多个 `data/*` 对象的分工**：data 块 1/2/3 各自承载什么子集的单元格/图像/数据，未与 xlsx 单元格一一对应。

---

## 13. 复现脚本说明

分析过程使用的 Python 脚本（运行时已删除，可从本仓库 git 历史或本地缓存恢复）：

1. **`_analyze.py`** — 基础 hexdump、ASCII/UTF-16 字符串提取、xlsx dump、非零段统计。
2. **`_analyze2.py`** — 修正 xlsx 读取（跳过前 5 行表头）、定位所有 `data/` hash、对象头字段对照。
3. **`_analyze3.py`** — 用 xlsx 表达式/值字符串在二进制中反查、尝试 zlib/deflate/gzip 解压各段。
4. **`_analyze4.py`** — 通过 `664\0` 标记扫描全部对象头、解码 `Job.json.sig` 的 Base64、扫描 cxdx 对比。
5. **`_analyze5.py`** — 验证 size 字段语义、sheets 对象深入、Job.json 内容提取。
6. **`_analyze6.py`** — sheets 单字节 XOR 解密尝试、字节频率分布、压缩格式 magic 检测。

### 关键代码片段

**对象头扫描**（核心定位算法）：

```python
def find_object_headers(data):
    objs = []
    i = 0
    while True:
        j = data.find(b"664\x00", i)
        if j < 0:
            break
        obj_start = j - 0x64              # 0x64 处是 "664\0"
        if obj_start < 0:
            i = j + 1
            continue
        end_name = data.find(b"\x00", obj_start)
        name = data[obj_start:end_name].decode("ascii", "replace")
        objs.append((obj_start, j, name))
        i = j + 1
    return objs
```

**xlsx 单元格读取**（跳过表头）：

```python
def read_xlsx_cells(path):
    wb = openpyxl.load_workbook(path, data_only=False)
    ws = wb["单元格"]
    rows = list(ws.iter_rows(values_only=True))
    headers = rows[4]                     # 第 5 行（0-based 第 4 行）是列标题
    cells = []
    for row in rows[5:]:                  # 第 6 行起为数据
        if row[0] is None and row[1] is None:
            continue
        cells.append(dict(zip(headers, row)))
    return cells
```

**xlsx 反查二进制**（验证表达式不在明文中）：

```python
for expr in {c["表达式"] for c in cells if c.get("表达式")}:
    if data.find(expr.encode("utf-8")) >= 0:    # ASCII
        ...
    if data.find(expr.encode("utf-16-le")) >= 0:  # UTF-16LE
        ...
```

---

## 14. 置信度总结

### 已确认（高置信度）
- 文件是 9 个对象块串联的对象容器格式；
- 对象头 0x200 字节固定布局，0x64 处 `664\0` 是定位锚点；
- 元数据字段布局（name/664/0/size/0/seq/" 0"）；
- JSON 对象内容为明文 ASCII，子对象通过 `FileRef` 引用；
- `.sig` 对象为 Base64 编码签名；
- cxdx 是同族格式（4 个对象，对象头布局一致）；
- **xlsx 中的表达式/中文值在 .jobx 二进制中无法直接找到明文**——这是最重要的反查结论；
- 表达式等数据存在于 `sheets/<hash>` 对象的内容区，但内容区**被编码/加密**。

### 推测（中置信度）
- `data/*` 对象内 8 字节 tag 头 + 跟随数据的 TLV 模式；
- 主数据段 `0x1e8ba..0x5d769` 是图像像素数据（与 `AcquireImage()` 表达式呼应）；
- `0x94` 处 6 位数字是某种序列号/时间戳；
- 对象头长度依类型变化（`data/*` 用 0x200，其他用 0x100）。

### 未知（低置信度）
- sheets 对象的具体加密算法与密钥来源；
- `size` 字段的真实语义；
- `data/*` 对象 TLV 字段的完整 schema；
- 30 字节签名的算法与公钥验证流程；
- 多个 `data/*` 对象的分工细节。

---

**文档版本**：1.0
**生成时间**：2026-09-23
**样本**：`天窗程序模板.jobx`（932,864 字节）、`Xavier标准作业模块.cxdx`（64,000 字节）
**反查依据**：`天窗程序模板.jobx_20260918_100758.xlsx`（406 单元格，304 表达式）、`src/main/java/com/cognex/export/*.java`
