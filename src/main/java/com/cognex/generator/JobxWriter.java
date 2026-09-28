package com.cognex.generator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 把 GeneratorJob / GeneratorSheet 写成 .jobx / .cxdx 文件。
 * Write GeneratorJob / GeneratorSheet to .jobx / .cxdx files.
 *
 * 二进制格式（详见 JobxBinaryStructure.md）：
 * - POSIX V7 TAR（无 ustar magic），512 字节头，内容 512 字节对齐
 * - .jobx = TAR{ Job.json, [Job.json.sig] }
 * - .cxdx = TAR{ version="1", range="A1:Z<maxRow>", snippet.json(XOR), [snippet.json.sig] }
 * - XOR 密钥：0x72 0x9B 0x0F 0x2E（§6.1）
 * - .sig = HMAC-SHA256（base64 key）→ base64 → UTF-8 字节（§8.1/§8.2）
 *   * .jobx 签名明文 Job.json 字节
 *   * .cxdx 签名 snippet.json 密文（XOR 后）字节
 */
public class JobxWriter {

    // XOR 混淆密钥 / XOR obfuscation key (Cognex DeobfuscateBytes, §6.1)
    private static final byte[] XOR_KEY = { 0x72, (byte) 0x9B, 0x0F, 0x2E };
    // HMAC-SHA256 密钥（base64，§8.2）
    private static final String HMAC_KEY_BASE64 = "DtrDN+DqE5lDTNNWDl1tkYI92hmjAW2g8Rc+xmn9P04=";

    private JobxWriter() {
    }

    /** 写 .jobx；withSig=true 时同时写 Job.json.sig。 */
    public static File writeJobx(GeneratorJob job, File outFile, boolean withSig) throws IOException {
        byte[] jobJsonBytes;
        try {
            jobJsonBytes = job.toJobJsonBytes();
        } catch (Exception e) {
            throw new IOException("构建 Job.json 失败: " + e.getMessage(), e);
        }
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建输出目录: " + parent);
        }
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            writeTarEntry(fos, "Job.json", jobJsonBytes);
            if (withSig) {
                byte[] sig = computeSig(jobJsonBytes);
                writeTarEntry(fos, "Job.json.sig", sig);
            }
            writeEndOfArchive(fos);
        }
        return outFile;
    }

    /** 写 .cxdx；withSig=true 时同时写 snippet.json.sig。 */
    public static File writeCxdx(GeneratorSheet sheet, File outFile, boolean withSig) throws IOException {
        String range = computeRange(sheet);
        byte[] plainBytes;
        try {
            // snippet.json 顶层格式：{"$type":"CopyBufferObject","range":"A1:Z<n>","cells":[...],...}
            plainBytes = sheet.toSnippetBytes(range);
        } catch (Exception e) {
            throw new IOException("构建 snippet.json 失败: " + e.getMessage(), e);
        }
        byte[] cipherBytes = xor(plainBytes);

        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建输出目录: " + parent);
        }
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            writeTarEntry(fos, "version", "1".getBytes(StandardCharsets.UTF_8));
            writeTarEntry(fos, "range", range.getBytes(StandardCharsets.UTF_8));
            writeTarEntry(fos, "snippet.json", cipherBytes);
            if (withSig) {
                byte[] sig = computeSig(cipherBytes);
                writeTarEntry(fos, "snippet.json.sig", sig);
            }
            writeEndOfArchive(fos);
        }
        return outFile;
    }

    // ======================== TAR 写入 ========================

    /** 写单个 TAR 条目（头 + 内容 + 对齐填充）。公开供 i18n 字节级回写复用。 */
    public static void writeTarEntry(OutputStream out, String name, byte[] content) throws IOException {
        byte[] header = makeTarHeader(name, content.length);
        out.write(header);
        out.write(content);
        int pad = (512 - (content.length % 512)) % 512;
        if (pad > 0) {
            out.write(new byte[pad]);
        }
    }

    /** 写两块 512 字节全 0 结束标记。公开供 i18n 字节级回写复用。 */
    public static void writeEndOfArchive(OutputStream out) throws IOException {
        // 两块 512 字节全 0 = TAR 结束标记 / Two 512-byte zero blocks = end-of-archive marker
        out.write(new byte[512]);
        out.write(new byte[512]);
    }

    /**
     * 构造 512 字节 POSIX V7 TAR 头。
     * 字段布局（§2 / §4）：
     *   name(100) | mode(8) | uid(8) | gid(8) | size(12) | mtime(12) | chksum(8) | typeflag(1) | linkname(100) | …padding
     * mode/uid/gid/mtime/size 用 octal+NUL 格式；chksum 用 6 位 octal+NUL+space。
     */
    private static byte[] makeTarHeader(String name, int size) {
        byte[] h = new byte[512];

        // name (offset 0, 100 bytes)：ASCII + NUL 终止
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, h, 0, Math.min(nameBytes.length, 99));

        // mode (offset 100, 8 bytes)：octal 664
        putOctal(h, 100, 8, 0664);
        // uid (offset 108, 8 bytes)：0
        putOctal(h, 108, 8, 0);
        // gid (offset 116, 8 bytes)：0
        putOctal(h, 116, 8, 0);
        // size (offset 124, 12 bytes)：八进制内容字节数
        putOctal(h, 124, 12, size);
        // mtime (offset 136, 12 bytes)：0
        putOctal(h, 136, 12, 0);

        // chksum 占位：8 个 0x20 (space) for sum computation
        for (int i = 148; i < 156; i++) h[i] = ' ';

        // typeflag (offset 156, 1 byte)：'0' = regular file
        h[156] = '0';

        // linkname/magic/version/uname/gname/devmajor/devminor/prefix/padding: 全 0（V7 变体，无 ustar magic）

        // 计算校验和：所有 512 字节求和（chksum 字段按 8 个空格计）
        long sum = 0;
        for (byte b : h) sum += (b & 0xFF);

        // 写入 chksum：6 位零填充八进制 + NUL + space
        String chk = Long.toOctalString(sum);
        while (chk.length() < 6) chk = "0" + chk;
        byte[] chkBytes = chk.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(chkBytes, 0, h, 148, 6);
        h[154] = 0;
        h[155] = ' ';

        return h;
    }

    /**
     * 在 TAR 头指定偏移写入八进制 ASCII。
     * 格式：octal_string + NUL + 零填充（fieldLen 范围内）
     */
    private static void putOctal(byte[] h, int off, int fieldLen, long value) {
        String s = Long.toOctalString(value);
        byte[] sb = s.getBytes(StandardCharsets.US_ASCII);
        int len = Math.min(sb.length, fieldLen - 1);  // 留 1 字节给 NUL 终止
        System.arraycopy(sb, 0, h, off, len);
        h[off + len] = 0;  // NUL 终止
        // 剩余字节在 new byte[512] 初始化时已为 0
    }

    // ======================== XOR / HMAC ========================

    /** 4 字节循环 XOR（对称，加解密同一函数）。公开供 i18n 回写复用。 */
    public static byte[] xor(byte[] data) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ XOR_KEY[i & 3]);
        }
        return out;
    }

    /**
     * 计算 HMAC-SHA256 签名（§8.1）：
     *   digest = HMAC-SHA256(HMAC_KEY, data)
     *   sig_bytes = UTF-8( base64(digest) )   // 44 字节
     * 公开供 i18n 重签复用。
     */
    public static byte[] computeSig(byte[] data) throws IOException {
        try {
            byte[] key = Base64.getDecoder().decode(HMAC_KEY_BASE64);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(data);
            String b64 = Base64.getEncoder().encodeToString(digest);
            return b64.getBytes(StandardCharsets.UTF_8);  // 44 字节
        } catch (Exception e) {
            throw new IOException("计算 HMAC-SHA256 签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算 cxdx range 字符串（§9 + §6.7）：
     * 扫描所有单元格的 location，求 max 列字母 + max 行号，输出 "A1:<col><row>"。
     * 无单元格时返回 "A1:Z1"。
     */
    private static String computeRange(GeneratorSheet sheet) {
        int maxCol = 0;  // A=0
        int maxRow = 0;
        for (String loc : sheet.cells.keySet()) {
            int[] rc = parseLoc(loc);
            if (rc == null) continue;
            if (rc[0] > maxCol) maxCol = rc[0];
            if (rc[1] > maxRow) maxRow = rc[1];
        }
        String col = colLetter(maxCol);
        // cxdx range 用 1-based 行号；A1:Z<maxRow+1>
        return "A1:" + col + (maxRow + 1);
    }

    /** 解析 "A0" / "$B$17" → [col(0-based), row(0-based)]；非法返回 null。 */
    private static int[] parseLoc(String loc) {
        if (loc == null || loc.isEmpty()) return null;
        String s = loc.replace("$", "");
        int i = 0;
        while (i < s.length() && Character.isLetter(s.charAt(i))) i++;
        if (i == 0 || i == s.length()) return null;
        try {
            String colPart = s.substring(0, i).toUpperCase();
            int col = 0;
            for (char c : colPart.toCharArray()) {
                col = col * 26 + (c - 'A' + 1);
            }
            col -= 1;  // 0-based
            int row = Integer.parseInt(s.substring(i));
            return new int[]{col, row};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 0-based 列号 → 字母（0=A, 25=Z, 26=AA ...） */
    private static String colLetter(int idx) {
        StringBuilder sb = new StringBuilder();
        int n = idx + 1;
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('A' + (n % 26)));
            n /= 26;
        }
        return sb.toString();
    }
}
