package com.campus.card.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Description 终端刷卡消费请求
 * @Author u
 * @Date 2026/10/7
 */
@Data
public class ConsumeReq {

    /** 终端本地生成的幂等号，同一个号重复发只扣一次 */
    @NotBlank(message = "缺少终端请求号")
    @Size(max = 40, message = "终端请求号过长（最长 40 位）")
    private String requestNo;

    @NotBlank(message = "缺少卡号")
    private String cardNo;

    /** 消费金额，单位：分，正数 */
    @NotNull(message = "消费金额不能为空")
    @Min(value = 1, message = "消费金额必须大于 0")
    private Long amount;

    /** 终端号，如 CANTEEN-03 */
    @NotBlank(message = "缺少终端号")
    private String terminalNo;
}
