package com.campus.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @Description 对账任务(t_recon_task)
 * @Author u
 * @Date 2026/10/5
 */
@Data
@TableName("t_recon_task")
public class ReconTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate billDate;
    private String channel;

    private Integer platformCount;    // 平台侧笔数（接口层叫 localCount）
    private Long platformAmount;      // 平台侧金额合计，分（接口层叫 localAmount）
    private Integer channelCount;     // 渠道侧笔数
    private Long channelAmount;       // 渠道侧金额合计，分
    private Integer diffCount;        // 差异笔数

    private Integer status;           // 0进行中 1已完成 2失败
    private Long costMs;              // ★ 耗时。表里【没有】finish_time 这一列
    private String operator;          // 谁跑的
    private LocalDateTime createTime;
}
