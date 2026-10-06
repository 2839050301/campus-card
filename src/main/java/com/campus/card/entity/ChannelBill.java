package com.campus.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @Description 渠道账单（t_channel_bill）—— 对账的「另一边」
 * @Author u
 * @Date 2026/10/5
 */
@Data
@TableName("t_channel_bill")
public class ChannelBill {
    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate billDate;       // 账单日期
    private String channel;           // WECHAT / ALIPAY / UNIONPAY
    private String channelOrderNo;    // 渠道侧单号（对账的关联键）
    private Long amount;              // 单位：分
    private String tradeStatus;       // SUCCESS / REFUND / CLOSED
    private LocalDateTime createTime;
}
