package com.campus.card.service.impl;

import com.campus.card.common.BizException;
import com.campus.card.constant.LimitConstant;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.entity.AccountFlow;
import com.campus.card.entity.CardAccount;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.mapper.AccountFlowMapper;
import com.campus.card.mapper.CardAccountMapper;
import com.campus.card.mapper.RechargeOrderMapper;
import com.campus.card.service.NotifyRecordService;
import com.campus.card.service.PayCallbackService;
import com.campus.card.util.OrderNoGenerator;
import com.campus.card.vo.PayCallbackVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @Description
 * @Author u
 * @Date 2026/10/3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayCallbackServiceImpl implements PayCallbackService {

    private static final String RESULT_SUCCESS = "SUCCESS";

    private final RechargeOrderMapper rechargeOrderMapper;
    private final CardAccountMapper cardAccountMapper;
    private final AccountFlowMapper accountFlowMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final NotifyRecordService notifyRecordService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayCallbackVO handlePayCallback(String orderNo, String result,String remark) throws BizException {


        // 锁订单行
        RechargeOrder order = rechargeOrderMapper.lockBYOrderNo(orderNo);
        if (order == null) {
            throw new BizException("充值单不存在：" + orderNo);
        }

        // 幂等：渠道没收到 200 会反复重投，这里的单已经付过了，直接吃掉
        if (order.getStatus() != null && order.getStatus() == OrderStatusConstant.PAID) {
            log.info("回调重复投递，直接返回orderNo={}", orderNo);
            return of(order, true, "回调重复投递，本次未重复入账");
        }
        if (order.getStatus() != null && order.getStatus() == OrderStatusConstant.CLOSED) {
            log.warn("回调落在已关闭的单上，忽略orderNo={}", orderNo);
            return of(order, true, "订单已关闭，回调忽略");
        }

        //失败分支：渠道明说没支付成功
        if (!RESULT_SUCCESS.equals(result)) {
            order.setStatus(OrderStatusConstant.FAILED);
            order.setPayTime(LocalDateTime.now());
            order.setChannelOrderNo(channelOrderNoOf(orderNo));
            order.setRemark("渠道返回：用户取消支付");
            rechargeOrderMapper.updateById(order);
            log.info("支付失败已落库 orderNo={}", orderNo);
            return of(order, false, "渠道返回支付失败");
        }

        //锁账户行
        CardAccount account=cardAccountMapper.lockByCardNo(order.getCardNo());


        // 单日余额校验
        Long paidToday = rechargeOrderMapper.sumPaidAmount(order.getStudentNo(), order.getBillDate());
        long already = paidToday == null ? 0 : paidToday;
        if (already + order.getAmount() > LimitConstant.DAILY_LIMIT) {
            order.setStatus(OrderStatusConstant.CLOSED);
            order.setCloseTime(LocalDateTime.now());
            order.setChannelOrderNo(channelOrderNoOf(orderNo));
            order.setRemark("超出单日累计充值额度（上限" + (LimitConstant.DAILY_LIMIT / 100)
                    + "元），已关闭待退款");
            rechargeOrderMapper.updateById(order);
            log.error("超单日限额，订单已关闭待人工退款 orderNo={} studentNo={} 已付={}分 本笔={}分",
                    orderNo, order.getStudentNo(), already, order.getAmount());

            return of(order, false, "超出单日累计充值额度，订单已关闭，请联系管理员退款");
        }
        //加余额
        int added = cardAccountMapper.addBalance(order.getCardNo(), order.getAmount());
        if (added != 1) {
            throw new BizException("余额更新失败，cardNo=" + order.getCardNo());
        }
        long balanceAfter =account.getBalance()+order.getAmount();

        //写流水
        AccountFlow flow = new AccountFlow();
        flow.setFlowNo(orderNoGenerator.generateOrderNo("F"));
        flow.setCardNo(order.getCardNo());
        flow.setStudentNo(order.getStudentNo());
        flow.setOrderNo(order.getOrderNo());
        flow.setFlowType("RECHARGE");
        flow.setAmount(order.getAmount());
        flow.setBalanceAfter(balanceAfter);
        flow.setRemark("一卡通充值 · " + payMethodName(order.getPayMethod()));
        accountFlowMapper.insert(flow);

        // ⑨改订单状态：成功
        order.setStatus(OrderStatusConstant.PAID);
        order.setPayTime(LocalDateTime.now());
        order.setChannelOrderNo(channelOrderNoOf(orderNo));
        order.setRemark(remark);
        rechargeOrderMapper.updateById(order);

        //写通知记录(status=0 待通知)
        notifyRecordService.createPending(order);

        log.info("入账成功 orderNo={} cardNo={} amount={}分 balanceAfter={}分",
                order.getOrderNo(), order.getCardNo(), order.getAmount(), balanceAfter);

        return of(order, false, "入账成功");

    }


    private PayCallbackVO of(RechargeOrder order, boolean repeat, String message) {
        PayCallbackVO vo = new PayCallbackVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setRepeat(repeat);
        vo.setMessage(message);
        return vo;
    }

    private String channelOrderNoOf(String orderNo) {
        return "CH" + orderNo.substring(1);
    }


    private String payMethodName(String payMethod) {
        if ("WECHAT".equals(payMethod)) {
            return "微信支付";
        }
        if ("ALIPAY".equals(payMethod)) {
            return "支付宝";
        }
        if ("UNIONPAY".equals(payMethod)) {
            return "银联";
        }
        return payMethod == null ? "" : payMethod;
    }


}
