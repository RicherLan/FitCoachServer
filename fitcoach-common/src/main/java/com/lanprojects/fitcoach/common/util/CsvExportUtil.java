package com.lanprojects.fitcoach.common.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * 流式 CSV 导出工具。
 * <p>用 {@link Writer} 边遍历边写，避免一次性把全量行字符串拼装在内存里。
 * <p>UTF-8 + BOM（Excel 打开中文不乱码）；字段按 RFC4180 转义；
 * 对以 {@code = + - @} 开头的字段额外加单引号前缀，防止 CSV 公式注入。
 * <p>典型用法（controller 里）：
 * <pre>{@code
 * response.setContentType("text/csv; charset=UTF-8");
 * response.setHeader("Content-Disposition", "attachment; filename=\"users.csv\"");
 * try (OutputStream os = response.getOutputStream()) {
 *     CsvExportUtil.write(os, List.of("ID", "uid", "昵称"), users,
 *             u -> List.of(u.getId().toString(), u.getUid(), u.getNickname()));
 * }
 * }</pre>
 */
public final class CsvExportUtil {

    /** UTF-8 BOM —— Windows Excel 识别 UTF-8 必须的 3 字节头 */
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvExportUtil() {
    }

    /**
     * 写一段完整 CSV（含 BOM + 表头 + 数据行）。
     *
     * @param out        目标输出流；调用方负责关闭
     * @param headers    表头（按列顺序）
     * @param rows       数据源
     * @param rowMapper  把 row 转成一行字符串列表（字段数应与 headers 一致；不一致按 headers 长度兼容）
     * @param <T>        行类型
     */
    public static <T> void write(OutputStream out, List<String> headers, List<T> rows,
                                 Function<T, List<String>> rowMapper) throws IOException {
        out.write(UTF8_BOM);
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writeLine(w, headers);
        if (rows != null) {
            for (T row : rows) {
                List<String> cells = rowMapper.apply(row);
                writeLine(w, cells);
            }
        }
        w.flush();
    }

    /** 写一行（含末尾 CRLF，Excel/记事本兼容更稳） */
    public static void writeLine(Writer w, List<String> cells) throws IOException {
        if (cells == null) {
            w.write("\r\n");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
        w.write(sb.toString());
    }

    /**
     * 单元格转义。
     * <ul>
     *   <li>null → 空字符串</li>
     *   <li>以 {@code = + - @ 制表 \r} 开头：加单引号前缀（防 CSV 公式注入）</li>
     *   <li>含 {@code , " \r \n}：整体用双引号包裹，内部双引号 → 两个双引号</li>
     * </ul>
     */
    public static String escape(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty()) {
            char c = s.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
                s = "'" + s;
            }
        }
        boolean mustQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0;
        if (!mustQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
