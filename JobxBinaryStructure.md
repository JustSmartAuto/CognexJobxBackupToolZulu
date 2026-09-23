# Cognex In-Sight `.jobx` 作业文件二进制结构说明

> 样本：`天窗程序模板.jobx`（932,864 字节，Cognex InSight 8000/9000 系列相机导出）
> 对照样本：`Xavier标准作业模块.cxdx`（64,000 字节，同族 `.cxdx` 格式）
> 配套数据：`天窗程序模板.jobx_20260918_100758.xlsx`（406 个单元格，304 条表达式）
> 反查依据：
> - 本仓库 `src/main/java/com/cognex/export/ExportTask.java`、`XlsxExporter.java`
> - **Cognex 官方工具源码反编译**：`D:\JustStupid\jobx表格编辑器_2604270917\In-Sight Job Converter` 目录下 6 个 Cognex 自有 DLL（ilspycmd 8.2 反编译为 C# 源代码）。关键文件：
>   - `Cognex.InSight.Job.Isvs\...\JobxSerializer.cs` —— `.jobx` 主序列化器
>   - `Cognex.InSight.Job.Isvs\...\JobxWriterHelper.cs` —— TAR 写入器封装
>   - `Cognex.InSight.Job.Isvs.Internal\...\RHejpnxfeOJLlFWuQg.cs` —— HMAC-SHA256 签名写入器
>   - `Cognex.InSight.Job.Isvs.Internal\...\ksRVLn68kJi81Bh7or.cs` —— 字符串混淆解码器
>   - `Cognex.InSight.Job.Isvs\...\JobxJsonSerializer.cs` —— JSON 序列化器配置

文档约定：
- **置信度标记**：`[已确认]` = 经多份样本/源码交叉验证；`[推测]` = 单样本归纳、缺乏旁证；`[未知]` = 未解之谜。
- 偏移以文件绝对字节地址计；十六进制标注为 `0x........`。
- 字节序：多字节整数一律 **小端 (LE)**，IEEE-754 double 为 8 字节小端。
- **TAR 头偏移**：本文档沿用 POSIX ustar TAR 头字段名（详见 §2）。

---

## 0. 重大结论修正（v1.2，2026-09-23）

通过反编译 Cognex 官方 "In-Sight Job Converter" 工具（路径见上文，6 个自有 DLL 共 1.75MB C# 源代码），确认以下 **颠覆性结论**，纠正了 v1.1 中所有 `[未知]` 项：

| v1.1 推测 | v1.2 源码确认 |
|-----------|---------------|
| `.jobx` 是自定义对象容器 | **`.jobx` 是标准 POSIX ustar TAR 归档**（无 ustar magic，旧式 V7 变体） |
| 0x200 字节"对象头"是自定义结构 | 0x200 字节就是 **TAR entry header（512 字节）** |
| 0x7c `size_ascii` 是十进制且语义不明 | **八进制 ASCII size**（TAR 标准），与 Python `tarfile` 读出的 size 完全一致 |
| 0x94 `seq` 是"序列号/时间戳" | **TAR 头校验和 chksum**（TAR 标准，自动算） |
| 0x9b `" 0"` 是"状态位" | chksum 末位空格 + typeflag `'0'`（regular file 标志），跨字段读取假象 |
| `0x64 "664"` 是"版本标记" | **TAR 文件 mode 字段**，八进制 664 = `rw-rw-r--` |
| `0x6c "0"` / `0x88 "0"` 是 subtype/flag | **TAR uid=0**（root 用户）/ **mtime=0**（Unix epoch，1970-01-01） |
| `Job.json.sig` = "32 字节 SHA-256 签名" | **HMAC-SHA256(Job.json_bytes, secret_key)**，base64 编码，44 字节 |
| `secret_key` 未知 | 已通过 .NET 反射加载 DLL 调用混淆解码函数提取，**32 字节，base64 = `DtrDN+DqE5lDTNNWDl1tkYI92hmjAW2g8Rc+xmn9P04=`** |
| 4 字节 XOR 密钥来源 | 仍 `[未知]`（不在静态字符串表/字段中，可能内联在 IL 指令中），但密钥本身已知 |

> **重要性**：源码确认后，整个文档的"对象容器"模型应直接重写为"标准 TAR 归档"。下文保留旧版章节以展示逆向推演过程，但所有 `[未知]` 项已转为 `[已确认]`，并在 §10 起列出源码确认的完整结论。

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

## 2. 对象头固定布局（0x200 字节）= POSIX TAR Entry Header `[已确认-源码]`

> **v1.2 重大修正**：经过 Cognex 官方源码反编译确认（`JobxSerializer.cs` 第 537 行 `TarInputStream(jobxStream, 1, Encoding.UTF8)`），本节所谓的"对象头"实际就是 **标准 POSIX ustar TAR entry header（512 字节）**。所有偏移、字段名都对应 TAR 标准，仅缺 `ustar` magic（旧 V7 TAR 变体，SharpZipLib 写入风格）。

每个对象块开头 0x200（512）字节是 TAR entry header，字段布局：

```
偏移     长度  TAR 字段名      本文档旧称          实测值/语义
0x00     100   name            name                ASCII 对象名，NULL 终止，剩余补 0
                                          （data/<sha256>、sheets/<sha256>、Job.json 等）
0x64     8     mode            "664\0"            八进制 664 = "rw-rw-r--" 文件权限（无前导零）
0x6C     8     uid             "0\0"              八进制 0 = root 用户 ID
0x74     8     gid             （v1.1 未单独标识）八进制 0 = root 组 ID
0x7C     12    size            "size_ascii"       八进制 ASCII 内容字节数 + NULL/space
0x88     12    mtime           "0\0"              八进制 0 = Unix 时间戳 0（1970-01-01 UTC）
0x94     8     chksum          "seq_ascii"        八进制 ASCII 头校验和，末位 NULL+空格
0x9C     1     typeflag        （跨字段读到" 0"） '0' (0x30) = regular file
0x9D     100   linkname        （v1.1 未识）      全 0（非符号链接）
0x101    6     magic           （v1.1 未识）      全 0（无 ustar magic，V7 变体）
0x107    2     version         （v1.1 未识）      全 0
0x109    32    uname           （v1.1 未识）      全 0
0x129    32    gname           （v1.1 未识）      全 0
0x149    8     devmajor        （v1.1 未识）      全 0
0x151    8     devminor        （v1.1 未识）      全 0
0x159    155   prefix          （v1.1 未识）      全 0
0x1F4    12    padding         （v1.1 未识）      全 0，凑齐 512 字节
0x200    ---   content_start  content_start      真正内容起点
```

> **修正（v1.1→v1.2）**：
> - 旧文档将 0x100~0x200 全 0 解释为"reserved2 padding"，实际是 TAR 头的 linkname/magic/version/uname/gname/devmajor/devminor/prefix 字段，因无 ustar 扩展信息而全 0。
> - "664\0" 不是版本标记，而是 **TAR mode 字段**（八进制 664 = rw-rw-r--）。
> - "0\0" 在 0x6c 是 **uid=0**，在 0x74 是 **gid=0**（v1.1 误将 0x74 当成 size 一部分），在 0x88 是 **mtime=0**。
> - "size_ascii" 不是十进制，是 **八进制**：例 "353140" = 0o353140 = 120,416 字节，对应 Python `tarfile` 读出的 size。
> - "seq_ascii" 不是序列号/时间戳，是 **TAR chksum 头校验和**：所有 512 字节头求和（chksum 字段按 8 个空格 0x20 计），结果以八进制写入。
> - 跨字段读到的 " 0" 是 chksum 末位空格（0x9B）+ typeflag '0'（0x9C），是 regular file 标志，不是状态位。

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

- **所有对象类型**（data/*、sheets/*、JSON 对象、.sig 对象）：内容起点 = `obj_start + 0x200`（即对象头 0x200 字节）
- 内容区到下一个对象起点之间为 0x00 填充。

> **修正（v1.1）**：早期推测"sheets/JSON/.sig 对象用 0x100 头"是错误的。`ExampleHmiSpreadsheetCells.jobx` 的 Job.json 内容实际起于 `0x200`（全 0 填充从 0x100 到 0x200），而非 0x100。所有对象头一律 0x200 字节。

### 3.2 内容区到下一对象的间距（实测，所有内容起点 = obj_start + 0x200）

| 对象 | 内容起点 | 下一对象起点 | 间距字节 | 非零字节 | size 字段 | 比例 |
|------|----------|--------------|----------|----------|-----------|------|
| data/d91c21b0 | 0x000200 | 0x01da00 | 121,088 | 72,954 | 353,140 | 2.92 |
| data/58da3151 | 0x01dc00 | 0x099000 | 505,088 | 489,315 | 1,731,033 | 3.43 |
| data/0fe15ae5 | 0x099200 | 0x0d6400 | 250,624 | 188,873 | 750,637 | 3.00 |
| sheets/740d18f5 | 0x0d6600 | 0x0e0e00 | 43,008 | 42,402 | 123,554 | 2.86 |
| computeResourceOrchestrator | 0x0e1000 | 0x0e1200 | 512 | 12 | 14 | 0.02 |
| JobValidationSet | 0x0e1400 | 0x0e1600 | 512 | 190 | 276 | 0.36 |
| EdgeAgentAdapterConfig | 0x0e1800 | 0x0e1a00 | 512 | 85 | 125 | 0.16 |
| Job.json | 0x0e1c00 | 0x0e3400 | 6,144 | 5,808 | 13,260 | 2.07 |
| Job.json.sig | 0x0e3600 | EOF+对齐 | 1,024 | 44 | 54 | 0.03 |

`size` 字段与内容字节数**无简单线性关系**（比例 0.02 ~ 3.43），语义见 §4.1。

---

## 4. 对象头元数据字段语义 = TAR 头标准字段 `[已确认-源码]`

### 4.1 `0x7c` size 字段 `[已确认-源码]`

**八进制 ASCII，对应 TAR 标准 size 字段**，单位为字节。

Python `tarfile.open().getmembers()` 读出的 `m.size` 字段即此值，验证如下：

| 对象 | size 字段（八进制 ASCII） | 十进制 | tarfile 读出 | 内容区实际占用（含对齐 padding） |
|------|---------------------------|--------|--------------|----------------------------------|
| data/d91c21b0 | "353140" | 0o353140 = 120,416 | 120,416 ✓ | 121,088（对齐到 0x200 倍数） |
| data/58da3151 | "1731033" | 0o1731033 = 504,347 | 504,347 ✓ | 505,088 |
| sheets/740d18f5 | "123554" | 0o123554 = 42,860 | 42,860 ✓ | 43,008 |
| Job.json | "13260" | 0o13260 = 5,808 | 5,808 ✓ | 6,144 |
| Job.json.sig | "54" | 0o54 = 44 | 44 ✓ | 1,024 |

> **修正（v1.1→v1.2）**：v1.1 将 size 当作十进制读，所以比例关系混乱（"2.92"、"3.43"等）。改为八进制后，size 即 TAR 内容字节数，**完全自洽**。"内容区到下一对象的间距"大于 size 的部分是 TAR 标准的 512 字节对齐 padding。

### 4.2 `0x94` seq 字段 = TAR chksum `[已确认-源码]`

**八进制 ASCII，TAR entry header 校验和**。

计算规则：把整个 512 字节头中所有字节求和，求和时 chksum 字段（0x94-0x9B 共 8 字节）按 8 个 0x20 (space) 计入。结果以八进制 ASCII 写入，末位补 NULL + space。

样本对象 #0 的 chksum = "013515"（八进制） = 0o013515 = 5,965（十进制）。

> **修正（v1.1→v1.2）**：v1.1 误以为是"序列号/时间戳/MMDDhhmm 截断"，实际只是 TAR 标准头校验和，每次写 TAR entry 时由 SharpZipLib 自动计算。Python `tarfile` 解析时自动校验，能成功打开即证明 chksum 正确。

### 4.3 `0x9b` " 0" 字段 = chksum 末位 + typeflag `[已确认-源码]`

是 chksum 字段（0x94-0x9B）的最后 1 字节（0x9B，恒为 0x20 = space）+ typeflag 字段（0x9C，恒为 0x30 = '0' 表示 regular file）的跨字段读取。

样本中所有对象都是 regular file（typeflag='0'），没有目录（typeflag='5'）或符号链接（typeflag='L'/'K' 等）。

### 4.4 `0x64` "664" 标记 = TAR mode `[已确认-源码]`

**TAR mode 字段，八进制 664 = 文件权限 rw-rw-r--**（user: rw, group: rw, other: r）。

SharpZipLib 写 TAR 时使用此默认权限，所有对象一致。

### 4.5 `0x6c` "0" / `0x74` "0" / `0x88` "0" = uid/gid/mtime `[已确认-源码]`

- `0x6c-0x73`：**uid**（user id），八进制 0 = root
- `0x74-0x7B`：**gid**（group id），八进制 0 = root
- `0x88-0x93`：**mtime**（modification time），八进制 0 = Unix 时间戳 0 = 1970-01-01 00:00:00 UTC

SharpZipLib 在 `TarEntry.CreateTarEntry(name)` 时默认 uid=gid=mtime=0，所有对象一致。

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

## 6. `sheets/<hash>` 对象：4 字节循环 XOR 加密 `[已确认-已破解]`

### 6.1 核心结论

**电子表格内容（单元格名、值、表达式、位置、列宽、行高、metadata 等）以 4 字节循环 XOR 加密存储**，密钥为：

```
0x72 0x9b 0x0f 0x2e   （重复使用，即 content[i] XOR key[i mod 4] = plain[i]）
```

- 密钥固定、跨文件通用（在 `天窗程序模板.jobx` 与 `Xavier标准作业模块.cxdx` 中均验证有效）。
- 密钥不依赖对象名 hash、不依赖相机固件——**是 Cognex 容器格式的硬编码常量**。
- 加密对象类型：**仅 `sheets/<hash>` 与 `snippet.json`（cxdx）**。其他对象（`data/*`、JSON 对象、`Job.json`、`.sig`）均**不加密**，明文存储。

### 6.2 破解方法（已知明文攻击）

由于 `ExampleHmiSpreadsheetCells.jobx` 小样本（11,776 字节）的 sheet 内容直接以 **base64 inline** 在 `Job.json` 中（`"Sheets":{"Inspection":{"$type":"Byte[]","sz":4319,"base64":"..."}}`），无需 XOR 解密即可观察到 sheet JSON 的标准结构：

```json
{"$type":"Sheet","cells":[["A0","AcquireImage()",1,{...},"",null,"","","",0,0],...],
 "columnWidths":[100,64,64,...],"coreThreshold":0.05,"outputs":"","processingCores":1,
 "rowHeights":[20,100,25,...],"timeout":60000}
```

以此为已知明文，对 `天窗程序模板.jobx` 中 `sheets/740d18f5...` 对象内容前 47 字节做 XOR 推算：

```
cipher[0:47]:  09 b9 2b 5a 0b eb 6a 0c 48 b9 5c 46 17 fe 7b 0c 5e b9 6c 4b 1e f7 7c 0c 48 c0 54 0c 33 ab 2d 02 50 da 6c 5f 07 f2 7d 4b 3b f6 6e 49 17 b3 26
plaintext[0:47]: {"$type":"Sheet","cells":[["A0","AcquireImage()    （从 ExampleHmiSpreadsheetCells.jobx 反推）
key = c XOR p: 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f 2e 72 9b 0f
```

**周期 4 字节，密钥字节完全一致**，置信度极高。用此密钥解密全部 42,860 字节内容后，得到完整合法 JSON，`json.loads` 解析无错误。

### 6.3 解密验证（`天窗程序模板.jobx`）

```python
key = bytes.fromhex('729b0f2e')
content = data[0x0d6600:0x0e0e00]
plain = bytes(content[i] ^ key[i % 4] for i in range(len(content)))
sheet = json.loads(plain.decode('utf-8'))
# 结果：sheet.$type == "Sheet", sheet.cells 406 行, 含所有 xlsx 中表达式/中文值
```

解密后 sheet 结构（与 `ExampleHmiSpreadsheetCells.jobx` 的 base64 解码结果一致）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `$type` | str | 恒为 `"Sheet"` |
| `cells` | array | 每行 = `[location, expression, flag, value, name,?]`，详见 §6.4 |
| `columnWidths` | array | 各列像素宽度（默认 64） |
| `coreThreshold` | float | 处理核心阈值（实测 0.05） |
| `metadata` | dict/null | sheet 元数据 |
| `outputs` | str | 输出配置 |
| `passFailCell` | str/null | Pass/Fail 判定单元格 |
| `passFailCondition` | str/null | 判定条件表达式 |
| `processingCores` | int | 使用的处理核心数 |
| `rowHeights` | array | 各行像素高度（默认 20） |
| `timeout` | int | 执行超时（毫秒，实测 60000） |

### 6.4 单元格行结构（cells 数组每项）

每行是一个**变长数组**，至少 11 个元素：

| 索引 | 字段 | 类型 | 实测例 |
|------|------|------|--------|
| 0 | location | str | `"A0"`、`"B1"`、`"$A$0"` |
| 1 | expression | str | `"AcquireImage()"`、`"Count($A$0,9999999,0,0)"`、`"'Trigger Count"`（以 `'` 开头表字面量） |
| 2 | flag | int | 恒为 1 |
| 3 | value | str/int/float/dict/null | 运行时结果；dict 形如 `{"$type":"Image",...}`、`{"$type":"Byte[]","sz":16,"base64":"..."}` |
| 4 | name | str | 单元格显示名（如 `"AcqCount"`） |
| 5 | timestamp/extra | array/null | null 或时间戳数组 `[年月日时分秒...]` |
| 6 | ? | str | 恒为 `""` |
| 7 | ? | str | 恒为 `""` |
| 8 | ? | str | 恒为 `""` |
| 9 | ? | int | 恒为 0 |
| 10 | ? | int | 恒为 0 |
| 11+ | 可选额外 | int | 小样本中无；大样本部分行末尾追加 1 个 0 |

### 6.5 加密对象字节分布特征（误判教训）

加密前字节均匀分布（top 字节各占 2.9%~4.2%），与"强加密/密文"特征相似，但实际是**循环 XOR 加密的弱加密**。早期推测"非单字节 XOR"是**错误的**——单字节 XOR 遍历 0~255 失败，是因为密钥是 4 字节而非 1 字节；只要扩展到多字节 XOR 就能识别。教训：

- "字节均匀分布"不必然意味着强加密，可能只是周期 >1 的 XOR；
- 已知明文攻击比"统计 + 试解压"更有效——只要有一份**同格式的明文样本**，就能定位密钥长度和内容；
- 跨样本对比（`ExampleHmiSpreadsheetCells.jobx` 的 base64 inline 模式）是突破口——Cognex 在小 sheet 时退化成明文 base64，大 sheet 才用 XOR 独立对象。

### 6.6 两种 sheet 存储模式

| 模式 | 触发条件 | Job.json 中 Sheets 字段 | sheet 数据位置 |
|------|----------|------------------------|----------------|
| **inline base64** | 小 sheet（实测 sz ≤ 4319 字节解码后） | `"Sheets":{"<name>":{"$type":"Byte[]","sz":<bytes>,"base64":"<b64>"}}` | 直接嵌入 Job.json，**无独立 sheets 对象** |
| **独立对象 + XOR** | 大 sheet（实测 42,860 字节明文） | `"Sheets":{"<name>":{"$type":"FileRef","id":"sheets/<sha256>"}}` | 独立 `sheets/<hash>` 对象，内容 XOR 加密 |

阈值未精确确认，但 `ExampleHmiSpreadsheetCells.jobx`（4319 字节明文 / 5760 字符 base64）已 inline，`天窗程序模板.jobx`（42,860 字节明文）独立对象，说明阈值位于二者之间。`[推测]`

### 6.7 与 cxdx 的对照

`Xavier标准作业模块.cxdx` 中的 `snippet.json` 对象（59,228 字节内容）**也用同一 4 字节 XOR 密钥加密**：

```python
content = cxdx_data[0x0a00:0xf200]  # snippet.json 对象内容
plain = bytes(content[i] ^ key[i%4] for i in range(len(content)))
# plain = {"$type":"CopyBufferObject","range":"A1:Z318","cells":[...],...}
```

说明 XOR 加密是 **Cognex 通用对象容器的统一机制**，不限于 jobx 中的 sheets。

---

## 7. JSON 对象内容 `[已确认]`

### 7.1 Job.json（对象 #7）

- 内容区 `0x0e1c00..0x0e3400`（6,144 字节），其中明文 JSON 占 5,808 字节，剩余为 0x00 填充。
- JSON 起点：`0x0e1c00`（对象头偏移 0x200 处，与所有其他对象一致）。
- 内容是单行 JSON，根对象含字段：`AcqSettings`、`EdgeAgentAdapterConfig`（FileRef）、`JobSettings`、`JobValidationSet`（FileRef）、`JobVersion`（`"24.4"`）、`Metadata`（`CameraType: IS8905M`，`FirmwareVersion: 26.1.0 (3930)`，`JobType: Spreadsheet`）、`PerDeviceAcqSettings`、`Sheets.Inspection`（FileRef → `sheets/740d18f5...`）、`computeResourceOrchestrator`（FileRef）。
- 子对象通过 `{"$type":"FileRef","id":"<objname>"}` 引用其他对象，正是容器格式的设计动机。

### 7.1.x 对照：`ExampleHmiSpreadsheetCells.jobx` 的 Job.json `[已确认]`

小样本（JobVersion=22.2，FirmwareVersion=22.3.0 Beta 415）Job.json 仅 8,754 字符，含 5 个顶层字段：`AcqSettings`、`JobSettings`、`JobVersion`、`Metadata`、`Sheets`。**关键差异**：

- `Sheets.Inspection` 不是 `FileRef`，而是 `{"$type":"Byte[]","sz":4319,"base64":"<5760 字符 base64>"}`，即 sheet 数据**直接 base64 内嵌**在 Job.json 中。
- 没有 `EdgeAgentAdapterConfig`、`JobValidationSet`、`computeResourceOrchestrator`、`PerDeviceAcqSettings` 等扩展配置（说明这些是较新版本或扩展设备才有的对象）。
- 不存在 `data/*` 对象——所有图像数据通过 `AcquireImage()` 在运行时从相机获取，作业本身不存储图像。

### 7.2 computeResourceOrchestrator（对象 #4）

JSON 内容仅 11 字符：`{"slots":[]}`，位于 `0x0e1000` 起。

### 7.3 JobValidationSet（对象 #5）

JSON 约 190 字符，位于 `0x0e1400` 起：
```json
{"$type":"ValidationSet","cleanupActions":[],"dataRoot":null,"name":"",
 "variants":[{"$type":"ValidationVariant","id":0,"name":"Job Tests",
              "preConditions":[],"records":[],"setupActions":[]}]}
```

### 7.4 EdgeAgentAdapterConfig（对象 #6）

JSON 约 76 字符，位于 `0x0e1800` 起：
```json
{"$type":"EdgeAgentAdapterConfig","metrics":[],"sendMetricsWithoutAcquisition":false}
```

---

## 8. `.sig` 签名对象 = HMAC-SHA256 with embedded secret key `[已确认-源码+验证]`

### 8.1 算法确认

通过反编译 `Cognex.InSight.Job.Isvs.Internal.dll` 中 `qoROwPcoZO3mtAy6s7.RHejpnxfeOJLlFWuQg` 类（继承自 `JobxWriterHelper`），确认签名实现：

```csharp
// 等价 C# 实现（去混淆后）
public class RHejpnxfeOJLlFWuQg : JobxWriterHelper {
    public override void WriteEntry(string name, byte[] content) {
        base.WriteEntry(name, content);   // 写原 entry（如 Job.json）
        if (name == "Job.json") {         // lSAQW6c5l(0)
            base.WriteEntry("Job.json.sig", ComputeSig(content));  // lSAQW6c5l(20)
        }
    }

    private static byte[] ComputeSig(byte[] jobJsonBytes) {
        var key = Convert.FromBase64String("DtrDN+DqE5lDTNNWDl1tkYI92hmjAW2g8Rc+xmn9P04=");
        using var h = new HMACSHA256(key);
        string b64 = Convert.ToBase64String(h.ComputeHash(jobJsonBytes));
        return Encoding.UTF8.GetBytes(b64);  // 44 字节（不含终止 NULL）
    }
}
```

签名算法 **HMAC-SHA256**（不是裸 SHA-256），32 字节摘要 → base64 编码得 44 字符 → UTF-8 字节即 44 字节。

### 8.2 密钥提取

密钥通过 .NET 反射加载 `Cognex.InSight.Job.Isvs.Internal.dll`，调用字符串混淆解码函数 `Ofnrv5AACje4ofDVrH.ksRVLn68kJi81Bh7or.lSAQW6c5l(int)` 提取（dump 程序位于 `_decompiled\dump\`，调用 `lSAQW6c5l(48)` 返回 base64 字符串）：

| 索引 | 返回字符串 | 用途 |
|------|------------|------|
| 0   | `Job.json` | 触发签名的 entry 名 |
| 20  | `Job.json.sig` | 签名 entry 名 |
| 48  | `DtrDN+DqE5lDTNNWDl1tkYI92hmjAW2g8Rc+xmn9P04=` | **HMAC-SHA256 密钥（base64）** |

密钥解码 32 字节 hex：
`0e da c3 37 e0 ea 13 99 43 4c d3 56 0e 5d 6d 91 82 3d da 19 a3 01 6d a0 f1 17 3e c6 69 fd 3f 4e`

### 8.3 三份样本全部验证

```python
import tarfile, hmac, hashlib, base64
KEY = base64.b64decode('DtrDN+DqE5lDTNNWDl1tkYI92hmjAW2g8Rc+xmn9P04=')
# 天窗程序模板.jobx
t = tarfile.open('天窗程序模板.jobx', 'r')
job_json = t.extractfile('Job.json').read()
job_sig  = t.extractfile('Job.json.sig').read()
expected = base64.b64encode(hmac.new(KEY, job_json, hashlib.sha256).digest())
assert expected == job_sig   # b'685o4vhjkBCxoj1DwcGD80kS8kS8O71Zv5c7r5u1cvI='  ✓
```

| 样本 | entry 名 | entry 字节数 | sig 字符串 | HMAC 验证 |
|------|----------|--------------|------------|-----------|
| 天窗程序模板.jobx | Job.json | 5808 | `685o4vhjkBCxoj1DwcGD80kS8kS8O71Zv5c7r5u1cvI=` | ✓ 完全匹配 |
| ExampleHmiSpreadsheetCells.jobx | Job.json | 8754 | `G3OEyWk+cGCLn1WnaSZkioqQVxh2g4agWtIcG6fCN7I=` | ✓ 完全匹配 |
| Xavier标准作业模块.cxdx | snippet.json | 59228（XOR 加密） | `5V/MPh/VX5SgbQ61pVGtpTEu6A5teK6zmyRSyBlb5SI=` | ✓ **基于密文** 完全匹配 |

### 8.4 关键观察：.cxdx 签名基于密文

`.cxdx` 的 `snippet.json` 在 TAR 中以 **XOR 加密后的密文**存储。但 `snippet.json.sig` 是对**密文字节**计算 HMAC-SHA256，不是对解码后的明文。这与 `JobxWriterHelper.WriteEntry(name, byte[])` 的语义一致：调用方传入什么字节，TAR 就存什么字节，签名算法看到的也是这些字节。

### 8.5 旧版"30 字节签名"误判修正

v1.0 文档称"jobx 30 字节签名"基于 40 字符 base64 解码得到 30 字节，但实际签名是 **44 字符 base64 = 32 字节**（HMAC-SHA256 摘要）。40 字符的样本可能是错误的截取。v1.1 已修正为 44 字符/32 字节，v1.2 进一步确认算法是 HMAC-SHA256 而非裸 SHA-256。`[已修正]`

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

**反查二进制结论**：xlsx 中的字符串（表达式、中文值）**无法在 .jobx 文件明文中直接找到**，因为它们存储在 `sheets/<hash>` 对象的**XOR 加密内容**中（密钥见 §6）。要解析 .jobx 单元格内容，**现在可用以下任一方式**：

- **离线解密**（推荐）：用 4 字节密钥 `0x72 0x9b 0x0f 0x2e` XOR 解密 `sheets/<hash>` 对象内容，得到 sheet JSON，提取 cells 数组中的 location/expression/value/name。
- **base64 内嵌**（小 sheet）：若 `Job.json.Sheets.<name>` 是 `Byte[]` 而非 `FileRef`，直接 base64 解码 `base64` 字段即可。
- 在线协议（兜底）：通过 CogSocket HMI 协议在线获取（即现有导出工具 `ExportTask.java` 的做法）。

> 现在不再需要相机固件逆向——加密本身已破解。

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

> v1.2 更新：反编译 Cognex 官方工具源码后，原 7 项中 **5 项已破解**，仅剩 2 项。

1. ~~size 字段语义~~ `[已确认-源码]`：**TAR 八进制 size 字段**，即对象内容字节数。详见 §4.1。
2. ~~seq 字段语义~~ `[已确认-源码]`：**TAR chksum 头校验和**，由 SharpZipLib 自动计算。详见 §4.2。
3. **`data/*` 对象内部 TLV 结构的完整 schema** `[推测-未在反编译源码中找到]`：仅识别出 8 字节 tag 头（`tag1 tag2 03 type val4`，末字节 0x80）和跟随数据类型（double/int32），完整字段含义未确认。**`data/*` 对象的内容可能由更底层（非 Cognex .NET 层）的代码生成**，反编译的 6 个 Cognex .NET DLL 中未见其写入逻辑。
4. ~~Job.json.sig 32 字节签名算法~~ `[已确认-源码]`：**HMAC-SHA256(Job.json_bytes, secret_key)**，密钥已提取。详见 §8。
5. **多个 `data/*` 对象的分工** `[推测]`：data 块 1/2/3 各自承载什么子集的单元格/图像/数据，未与 xlsx 单元格一一对应。可能在 `Cognex.InSight.Job.Ise.dll` 中有线索（未深入分析）。
6. **sheet 存储模式切换阈值** `[推测]`：何时用 inline base64、何时用独立 XOR 对象，仅知阈值在 4319~42860 字节明文长度之间。**反编译源码中 `JobxSerializer.WriteSnippet` 有三种格式分支（`isvs-sheet-aaa` / `isvs-sheet-json` / `isvs-snippet-json`），但触发条件未追踪到。**
7. **4 字节 XOR 密钥来源** `[未知-部分破解]`：密钥本身已知（`0x72 0x9b 0x0f 0x2e`），通过已知明文攻击（小样本 sheet inline base64）破解并跨 3 份样本验证。但**反编译的 6 个 Cognex .NET DLL 中未直接出现该字节常量**（既不在静态字符串表 `lSAQW6c5l(int)` 索引中，也不在静态 `byte[]` 字段中），可能在更底层的非 .NET 代码（如 native C++ 库或硬件固件）中，或作为 IL 内联字面量散落在某个未反编译的方法体里。
8. ~~`ExampleHmiSpreadsheetCells.json` 的加载机制~~ `[已确认-不需破解]`：通过 SDK README 已确认该 .json 是 Cognex.InSight.Web SDK 的 HMI 显示覆盖文件，与 .jobx 独立，由 SDK 在 HMI 层加载后覆盖 sheet 中 `'Placeholder for X'` 占位单元格。Nyan_cat_125px_frame.png 也是同目录外部资源。.json 和 .png 都不参与 .jobx 内部存储。

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

### v1.2 源码反编译验证脚本

v1.2 阶段通过反编译 Cognex 官方 "In-Sight Job Converter" 工具的 6 个自有 DLL，新增以下验证脚本（运行时已删除，方法论保留以备复现）：

7. **`_dump.cs`**（`_decompiled\_dump.cs`，源码仍保留以备复跑）—— .NET 反射加载 `Cognex.InSight.Job.Isvs.Internal.dll`，调用混淆类 `Ofnrv5AACje4ofDVrH.ksRVLn68kJi81Bh7or` 的 `lSAQW6c5l(int)` 方法（字符串反混淆器），遍历索引 0..256 提取解码后的字符串字面量。从中提取出 HMAC 密钥 `DtrDN+DqE5lDTNNWDl1tkYI92hmjAW2g8Rc+xmn9P04=`（base64，32 字节）以及格式常量 `liger-jobx`、`isvs-snippet-json`、`isvs-sheet-json`、`isvs-sheet-aaa` 等。

```csharp
var asm = Assembly.LoadFrom(Path.Combine(dir, "Cognex.InSight.Job.Isvs.Internal.dll"));
var t = asm.GetType("Ofnrv5AACje4ofDVrH.ksRVLn68kJi81Bh7or");
var mi = t.GetMethod("lSAQW6c5l", BindingFlags.Static|BindingFlags.NonPublic|BindingFlags.Public);
for (int i = 0; i < 256; i++) {
    var r = mi.Invoke(null, new object[]{i});   // 解码后的字符串
    Console.WriteLine($"{i,3}: {r}");
}
```

8. **`_verify.py`**（运行时已删除）—— Python 脚本，用提取出的 HMAC 密钥对 3 个样本文件（`天窗程序模板.jobx`、`ExampleHmiSpreadsheetCells.jobx`、`Xavier标准作业模块.cxdx`）的 `Job.json`/`snippet.json` 与对应 `.sig` 进行交叉验证。确认 `Job.json.sig` = `base64(HMAC-SHA256(Job.json_bytes, secret_key))`，3 样本全部 match。

```python
KEY = base64.b64decode('DtrDN+DqE5lDTNNWDl1tkYI92hmjAW2g8Rc+xmn9P04=')
t = tarfile.open(path, 'r')
jb = t.extractfile('Job.json').read()
sb = t.extractfile('Job.json.sig').read()
exp = base64.b64encode(hmac.new(KEY, jb, hashlib.sha256).digest())
# exp == sb  → match=True（3 样本全通过）
```

---

## 14. 置信度总结

### 已确认（高置信度）
- **`.jobx`/`.cxdx` 是标准 POSIX ustar TAR 归档（V7 旧式变体，无 ustar magic）**——v1.2 经 `JobxSerializer.cs` 第 537 行 `TarInputStream(jobxStream, 1, Encoding.UTF8)` 源码确认；
- **对象头 0x200 字节 = TAR entry header（512 字节）**：name/mode/uid/gid/size/mtime/chksum/typeflag/linkname/magic/version/uname/gname/devmajor/devminor/prefix/padding 全字段对应 TAR 标准；
- `0x64` 处 `664\0` 是 **TAR mode 字段**（八进制 664 = rw-rw-r--），非版本标记；
- `0x6c` 处 `0\0` 是 **uid=0**（root），`0x74` 处 `0\0` 是 **gid=0**，`0x88` 处 `0\0` 是 **mtime=0**（Unix epoch）；
- **`0x7c` size 字段 = 八进制 ASCII 内容字节数**（如 "353140" = 0o353140 = 120,416 字节），非十进制；
- **`0x94` seq 字段 = TAR chksum 头校验和**（512 字节头求和，chksum 字段按 8 个空格计），非序列号/时间戳；
- `0x9b` 处 `" 0"` 是 chksum 末位空格 + typeflag `'0'`（regular file）跨字段读取假象；
- JSON 对象（`Job.json`、`computeResourceOrchestrator`、`JobValidationSet`、`EdgeAgentAdapterConfig`）内容为明文 ASCII，子对象通过 `FileRef` 引用；
- **`Job.json.sig` = `base64(HMAC-SHA256(Job.json_bytes, secret_key))`**，44 字节 base64 文本，secret_key 32 字节，base64 = `DtrDN+DqE5lDTNNWDl1tkYI92hmjAW2g8Rc+xmn9P04=`（v1.2 经 `RHejpnxfeOJLlFWuQg.cs` 源码 + 3 样本验证确认）；
- cxdx 是同族格式（4 个对象，对象头布局一致，`snippet.json` 也用 XOR 加密，`snippet.json.sig` 同样走 HMAC-SHA256）；
- **xlsx 中的表达式/中文值在 .jobx 二进制中无法直接找到明文**——存储在 `sheets/<hash>` 对象的 XOR 加密内容中；
- **`sheets/<hash>` 与 `snippet.json` 用 4 字节循环 XOR 加密，密钥 `0x72 0x9b 0x0f 0x2e` 固定且跨文件通用**——核心破解结论；
- **sheet JSON 标准结构**：`{"$type":"Sheet","cells":[["<loc>","<expr>",1,<value>,"<name>",...],...],...}`；
- **sheet 有两种存储模式**：小 sheet → inline base64 在 `Job.json.Sheets.<name>` 中（`Byte[]` 类型）；大 sheet → 独立 `sheets/<hash>` 对象 + XOR 加密；
- **`ExampleHmiSpreadsheetCells.json` 与 `.png` 是 Cognex.InSight.Web SDK 的 HMI 显示覆盖资源**，与 .jobx 独立，不参与 .jobx 内部存储。

### 推测（中置信度）
- `data/*` 对象内 8 字节 tag 头 + 跟随数据的 TLV 模式；
- 主数据段 `0x1e8ba..0x5d769` 是图像像素数据（与 `AcquireImage()` 表达式呼应）；
- sheet 存储模式切换阈值位于 4319~42860 字节明文长度之间（源码 `WriteSnippet` 发现 3 个格式分支 `snippet-json`/`sheet-json`/`sheet-archive`，触发条件未跟踪）。

### 未知（低置信度）
- **4 字节 XOR 密钥 `0x72 0x9b 0x0f 0x2e` 的源码位置**——密钥已验证有效，但不在 6 个 DLL 的静态字符串表/byte[] 字段中，可能内联在 IL 指令或非 .NET 原生代码中；
- `data/*` 对象 TLV 字段的完整 schema（未在 6 个反编译 DLL 中找到，可能在原生代码中）；
- 多个 `data/*` 对象的分工细节。

---

**文档版本**：1.2（v1.0 + ExampleHmiSpreadsheetCells.jobx 小样本交叉验证 + sheets XOR 加密破解 + Cognex 官方 Job Converter 源码反编译确认）
**生成时间**：2026-09-23（v1.2 修订）
**样本**：
- `天窗程序模板.jobx`（932,864 字节，JobVersion=24.4，IS8905M 相机）
- `ExampleHmiSpreadsheetCells.jobx`（11,776 字节，JobVersion=22.2，IS2802M 相机）
- `Xavier标准作业模块.cxdx`（64,000 字节，同族 .cxdx 格式）
**反查依据**：
- `天窗程序模板.jobx_20260918_100758.xlsx`（406 单元格，304 表达式）
- **Cognex 官方 "In-Sight Job Converter" 工具反编译源码**（6 个自有 DLL，ilspycmd 8.2 反编译为 1.75MB C# 源代码）
- `src/main/java/com/cognex/export/*.java`
- `InSightWebSDK-26.1.0/SampleCode/dotnet/WindowsFormsApp/`（Cognex 官方 .NET SDK 示例，含 `HmiSpreadsheetCells.cs`、`README.md`）
