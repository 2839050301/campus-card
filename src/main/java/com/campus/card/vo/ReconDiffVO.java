package com.campus.card.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * @Description 对账差异 给前端的形状
 * @Author u
 * @Date 2026/10/5
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReconDiffVO {
    private Long id;
    private Long taskId;

    /** 平台单号。CHANNEL_ONLY 时是空串，前端会显示（渠道无此单） */
    private String orderNo;
    private String channelOrderNo;

    private String diffType;
    /** 平台侧金额，分（数据库列 platform_amount） */
    private Long localAmount;
    private Long channelAmount;

    private Integer handled;
    private String handleRemark;
    /** 系统给的说明 */
    private String remark;

}
