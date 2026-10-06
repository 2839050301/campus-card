package com.campus.card.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @Description 对账任务 · 给前端的形状
 * @Author u
 * @Date 2026/10/5
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReconTaskVO {
    private Long id;
    private LocalDate billDate;
    private String channel;

    /** 平台侧笔数（数据库列 platform_count） */
    private Integer localCount;
    /** 平台侧金额合计，分（数据库列 platform_amount） */
    private Long localAmount;
    private Integer channelCount;
    private Long channelAmount;
    private Integer diffCount;

    private Integer status;

    private LocalDateTime createTime;
    /**
     *  表里没有这一列，是 Service 里用 create_time + cost_ms 现算出来的。
     */
    private LocalDateTime finishTime;

    private String operator;
}
