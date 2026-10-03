package com.campus.card.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Description
 * @Author u
 * @Date 2026/10/3
 */
@Data
public class PayCallbackVO {
    private String orderNo;


    /**
     * 处理完之后订单的最新状态
     */
    private Integer status;

    private Boolean repeat;

    /**
     * 给别人看的结果
     */
    private String message;
}
