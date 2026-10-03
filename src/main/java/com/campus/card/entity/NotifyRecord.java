package com.campus.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description
 * @Author u
 * @Date 2026/10/3
 */
@Data
@TableName("t_notify_record")
public class NotifyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;         // 关联充值单
    private String notifyUrl;       // 学校一卡通系统的回调地址
    private String requestBody;     // 发出去的报文体
    private String responseBody;    // 对方返回
    private Integer status;         // 0待通知 1成功 2失败
    private Integer notifyTimes;    // 已经投了几次
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;



}
