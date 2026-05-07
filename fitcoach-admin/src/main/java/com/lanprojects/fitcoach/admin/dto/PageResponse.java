package com.lanprojects.fitcoach.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 后台分页响应统一壳。
 * <p>
 * 直接用 Spring 的 {@code Page} 序列化前端虽然能用，但前端拿到的字段一大堆
 * （pageable / sort / numberOfElements / first / last 等），过于啰嗦且字段名不直观。
 * 这里收敛到 4 个核心字段：page / size / total / records。
 *
 * @param <T> 记录元素类型
 */
@Data
@AllArgsConstructor
public class PageResponse<T> {
    /** 当前页码（从 1 开始，与前端 antd Table 习惯对齐） */
    private int page;
    /** 每页条数 */
    private int size;
    /** 总条数 */
    private long total;
    /** 当前页数据 */
    private List<T> records;

    /**
     * 把 Spring {@code Page<E>} 转成 {@code PageResponse<T>}，T 由 mapper 提供。
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getNumber() + 1,   // Spring 0-based → 前端 1-based
                page.getSize(),
                page.getTotalElements(),
                page.getContent().stream().map(mapper).toList()
        );
    }
}
