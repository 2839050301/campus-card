package com.campus.card.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
public class PageResult<T> {
    private Long total;
    private Long pages;
    private Long current;
    private Long size;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r=new PageResult<T>();
        r.total = page.getTotal();
        r.pages = page.getPages();
        r.current = page.getCurrent();
        r.size = page.getSize();
        r.records = page.getRecords();
        return r;
    }
    public static <E,T> PageResult<T> of(IPage<E> page, Function<E,T> mapper) {
        PageResult<T> r=new PageResult<T>();
        r.total = page.getTotal();
        r.pages = page.getPages();
        r.current = page.getCurrent();
        r.size = page.getSize();
        r.records=page.getRecords().stream().map(mapper).toList();
        return r;
    }
}
