package com.campus.card.service;

import com.campus.card.dto.ConsumeReq;
import com.campus.card.vo.ConsumeVO;

/**
 * @Description
 * @Author u
 * @Date 2026/10/7
 */
public interface TerminalService {
    /**
     * 刷卡消费：同一个请求号只会真的扣一次
     * @param req 终端请求（请求号 / 卡号 / 金额 / 终端号）
     * @return 消费结果（含是否重复）
     */
    ConsumeVO consume(ConsumeReq req);
}
