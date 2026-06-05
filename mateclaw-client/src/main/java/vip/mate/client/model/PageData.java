package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页数据（对齐 MyBatis-Plus IPage 序列化字段）
 */
@Data
public class PageData<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前页数据列表 */
    private List<T> records;
    /** 总记录数 */
    private long total;
    /** 每页大小 */
    private long size;
    /** 当前页码（从1开始） */
    private long current;
    /** 总页数 */
    private long pages;
}
