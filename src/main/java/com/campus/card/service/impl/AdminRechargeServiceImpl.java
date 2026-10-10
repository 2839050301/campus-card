package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.card.common.BizException;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.mapper.RechargeOrderMapper;
import com.campus.card.mapper.StatMapper;
import com.campus.card.service.AdminRechargeService;
import com.campus.card.service.NotifyRecordService;
import com.campus.card.service.PayCallbackService;
import com.campus.card.vo.RechargeOrderVO;
import com.campus.card.vo.TodayStatVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Description
 * @Author u
 * @Date 2026/10/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRechargeServiceImpl implements AdminRechargeService {
    private final StatMapper statMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final NotifyRecordService notifyRecordService;
    private final PayCallbackService payCallbackService;
    private static final String TRADE_SUCCESS = "SUCCESS";

    @Override
    public TodayStatVO today() {
        TodayStatVO vo = statMapper.selectToday();
        long total = vo.getOrderCount();
        long paid = vo.getPaidCount();
        double successRate = (total == 0) ? 0.0 : Math.round(paid * 1000.0 / total) / 10.0;
        vo.setSuccessRate(successRate);
        return vo;
    }

    @Override
    public IPage<RechargeOrderVO> page(long current, long size, Integer status, String payMethod, String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String method = (payMethod == null || payMethod.isBlank()) ? null : payMethod.trim();
        return rechargeOrderMapper.selectAdminPage(new Page<>(current, size), status, method, kw);
    }


    @Override
    public RechargeOrderVO retryNotify(String orderNo) {
        RechargeOrderVO vo = rechargeOrderMapper.selectAdminVoByOrderNo(orderNo);
        if (vo == null) {
            throw new BizException("充值单不存在");
        }
        if (vo.getStatus() == 0 || vo.getStatus() != OrderStatusConstant.PAID) {
            throw new BizException("只有支付成功的单子才需要通知");
        }
        int rows = notifyRecordService.requeue(orderNo);
        if (rows == 0) {
            log.warn("充值单已成功但没有对应的通知记录，orderNo={}", orderNo);
            throw new BizException("这单没有通知记录，无法重发");
        }
        return rechargeOrderMapper.selectAdminVoByOrderNo(vo.getOrderNo());
    }

    @Override
    public RechargeOrderVO mockPaid(String orderNo, String operator) {
        RechargeOrderVO before = rechargeOrderMapper.selectAdminVoByOrderNo(orderNo);
        if (before == null) {
            throw new BizException("充值单不存在");
        }
        if (before.getStatus() != null && before.getStatus() == OrderStatusConstant.PAID) {
            throw new BizException("该单已经是支付成功状态");
        }
        if (before.getStatus() != null && before.getStatus() == OrderStatusConstant.CLOSED) {
            throw new BizException("该单已关闭，不能补单");
        }
        payCallbackService.handlePayCallback(orderNo, TRADE_SUCCESS, "","人工补单 by " + operator);
        return rechargeOrderMapper.selectAdminVoByOrderNo(orderNo);
    }


}
