package com.campus.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Data
@TableName("t_card_account")
public class CardAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String cardNo; //卡号
    private String studentNo; //学号
    private Long balance; //余额
    private Integer version; //乐观锁版本
    private Integer status; //1 正常 0 冻结
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
