package com.campus.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description  对账差异明细(t_recon_diff)
 * @Author u
 * @Date 2026/10/5
 */
@Data
@TableName("t_recon_diff")
public class ReconDiff {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;              // 属于哪次对账
    private String orderNo;           // 平台单号。CHANNEL_ONLY 时无平台单，填 ""
    private String channelOrderNo;    // 渠道单号。LOCAL_ONLY 时渠道无此单，填 ""
    private String diffType;          // 五选一，见 ReconConstant.DIFF_*

    private Long platformAmount;      // 平台侧金额，分（接口层叫 localAmount）
    private Long channelAmount;       // 渠道侧金额，分

    private String remark;            // ★ 系统给的解释（告诉人这行是什么意思、该怎么办）
    private Integer handled;          // 0未处理 1已核销
    private String handleRemark;      // ★ 人写的处理说明
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
