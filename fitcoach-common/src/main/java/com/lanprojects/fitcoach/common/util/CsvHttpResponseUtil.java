package com.lanprojects.fitcoach.common.util;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * 把 CSV 流式写到 {@link HttpServletResponse}，统一设置响应头。
 * <p>文件名格式：{@code <prefix>_<yyyyMMdd_HHmmss>.csv}，并按 RFC5987 编码（{@code filename*=UTF-8''}），
 * Chrome/Edge/Safari 全部支持中文文件名。
 */
public final class CsvHttpResponseUtil {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private CsvHttpResponseUtil() {
    }

    /**
     * 写一段 CSV 到响应流。
     *
     * @param response  目标响应，方法内部会设置 Content-Type / Content-Disposition
     * @param prefix    文件名前缀（不含扩展），最终为 {@code <prefix>_<ts>.csv}
     * @param headers   表头
     * @param rows      数据行
     * @param mapper    row → 单元格列表
     */
    public static <T> void write(HttpServletResponse response, String prefix, List<String> headers,
                                 List<T> rows, Function<T, List<String>> mapper) throws IOException {
        String ts = LocalDateTime.now(ZoneId.systemDefault()).format(FILE_TS);
        String fileName = prefix + "_" + ts + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        try (OutputStream os = response.getOutputStream()) {
            CsvExportUtil.write(os, headers, rows, mapper);
        }
    }
}
