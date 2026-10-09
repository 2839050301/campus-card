package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.card.constant.NotifyStatusConstant;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.entity.NotifyRecord;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.mapper.DemoMapper;
import com.campus.card.mapper.NotifyRecordMapper;
import com.campus.card.mapper.RechargeOrderMapper;
import com.campus.card.service.DemoDataService;
import com.campus.card.service.PayCallbackService;
import com.campus.card.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @Description 演示数据重置
 * @Author u
 * @Date 2026/10/8
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DemoDataServiceImpl implements DemoDataService {
    private static final String TRADE_SUCCESS = "SUCCESS";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private static final String STU_ZHANG = "2023123456";
    private static final String CARD_ZHANG = "6217123456781234";
    private static final String STU_LI = "2023123457";
    private static final String CARD_LI = "6217123456781235";

    /** 退避表 5 档（prod 30/60/120/240/480，dev 3/6/12/24/48）→ 第 6 次失败判定最终失败 */
    private static final int FINAL_FAIL_TIMES = 6;

    /**
     * 演示脚本：{金额(分), 支付方式, 学号, 卡号, 最终状态}
     * ★ 写成固定数组而不是随机 —— 每次重置出来的单子一模一样，
     *   期望值才能写死（总 10 笔 / 成功 7 笔 / 成功金额 850 元 / 成功率 70.0%）。
     */
    private static final Object[][] SCRIPT = {
            {10000L, "WECHAT",   STU_ZHANG, CARD_ZHANG, OrderStatusConstant.PAID},
            {20000L, "ALIPAY",   STU_ZHANG, CARD_ZHANG, OrderStatusConstant.PAID},
            { 5000L, "WECHAT",   STU_LI,    CARD_LI,    OrderStatusConstant.PAID},
            {30000L, "UNIONPAY", STU_ZHANG, CARD_ZHANG, OrderStatusConstant.PAID},
            {10000L, "WECHAT",   STU_LI,    CARD_LI,    OrderStatusConstant.PAID},
            { 2000L, "ALIPAY",   STU_ZHANG, CARD_ZHANG, OrderStatusConstant.PAID},
            { 8000L, "WECHAT",   STU_LI,    CARD_LI,    OrderStatusConstant.PAID},
            {15000L, "WECHAT",   STU_ZHANG, CARD_ZHANG, OrderStatusConstant.WAITING_FOR_PAYMENT},
            { 5000L, "ALIPAY",   STU_ZHANG, CARD_ZHANG, OrderStatusConstant.WAITING_FOR_PAYMENT},
            {12000L, "WECHAT",   STU_LI,    CARD_LI,    OrderStatusConstant.CLOSED},
    };

    /**
     * 账本起点：{卡号, 期初余额(分)}。★ 和 sql/campus_card.sql 里的初始数据保持一致。
     * 这两个数字是「人给的输入」，不是算出来的 —— 所以它可以、也必须写死。
     */
    private static final Object[][] OPENING = {
            {CARD_ZHANG,  8650L},
            {CARD_LI,    12000L},
    };

    private final DemoMapper demoMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final NotifyRecordMapper notifyRecordMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final PayCallbackService payCallbackService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reset() {
        long start = System.currentTimeMillis();

        // ① 复位余额：把两个账户直接设回「账本起点」。
        //    ★ 为什么这里可以写死数字，见 2.5：期初余额是人给的输入，不是算出来的输出。
        //    ★ 为什么不能用「余额减去全部流水之和」反推，见第 7 步 DemoMapper 的注释 ——
        //      那个写法在刚建完库的时候就已经算错了。
        int resetAccounts = 0;
        for (Object[] acc : OPENING) {
            resetAccounts += demoMapper.resetBalance((String) acc[0], (Long) acc[1]);
        }
        demoMapper.deleteAllNotifyRecords();
        demoMapper.deleteAllAccountFlows();
        demoMapper.deleteAllOrders();
        demoMapper.deleteAllChannelBills();
        demoMapper.deleteAllReconDiffs();
        demoMapper.deleteAllReconTasks();
        log.info("演示数据已清空，复位余额的账户数={}", resetAccounts);

        // ② 按脚本重新造单
        LocalDate today = LocalDate.now();
        int seq = 0;
        for (Object[] row : SCRIPT) {
            seq++;
            long amount = (Long) row[0];
            String payMethod = (String) row[1];
            String studentNo = (String) row[2];
            String cardNo = (String) row[3];
            int finalStatus = (Integer) row[4];

            RechargeOrder order = new RechargeOrder();
            order.setOrderNo(orderNoGenerator.generateOrderNo("R"));
            order.setRequestNo("REQ-DEMO-" + today.format(DAY) + "-" + String.format("%02d", seq));
            order.setStudentNo(studentNo);
            order.setCardNo(cardNo);
            order.setAmount(amount);
            order.setPayMethod(payMethod);
            order.setStatus(OrderStatusConstant.WAITING_FOR_PAYMENT);   // ★ 一律先建成「待支付」
            order.setBillDate(today);
            order.setExpireTime(LocalDateTime.now().plusMinutes(15));
            order.setRemark("");
            rechargeOrderMapper.insert(order);

            if (finalStatus == OrderStatusConstant.PAID) {
                // ★ 全流程只有这一步走真实链路：余额、流水、通知记录都由它产生
                payCallbackService.handlePayCallback(order.getOrderNo(), TRADE_SUCCESS, "");
            } else if (finalStatus == OrderStatusConstant.CLOSED) {
                order.setStatus(OrderStatusConstant.CLOSED);
                order.setCloseTime(LocalDateTime.now());
                order.setRemark("演示数据：超时未支付，系统自动关单");
                rechargeOrderMapper.updateById(order);
            }
            // 待支付的单就停在上面那次 insert，不用再改
        }

        // ③ 造两条「通知终端失败」的样本 —— 首页那张「通知失败」的卡、
        //    列表里那两个「重发通知」按钮，都是冲它们去的。
        //    ★ 这是全流程里唯一一处手工改写的数据。它不违反 2.5 的原则：
        //      「通知失败」是终端那边发生的事，不是我们自己账本里的不变量 ——
        //      余额、流水、订单三方的一致性仍然完全由入账链路保证。
        List<NotifyRecord> pending = notifyRecordMapper.selectList(
                new LambdaQueryWrapper<NotifyRecord>()
                        .eq(NotifyRecord::getStatus, NotifyStatusConstant.WAIT)
                        .orderByAsc(NotifyRecord::getId));
        for (int i = 0; i < 2 && i < pending.size(); i++) {
            notifyRecordMapper.update(null, new LambdaUpdateWrapper<NotifyRecord>()
                    .eq(NotifyRecord::getId, pending.get(i).getId())
                    .set(NotifyRecord::getStatus, NotifyStatusConstant.FAIL)
                    .set(NotifyRecord::getNotifyTimes, FINAL_FAIL_TIMES)
                    .set(NotifyRecord::getNextRetryTime, null)     // ★ 这里必须能写 NULL
                    .set(NotifyRecord::getResponseBody,
                            "ConnectException: Connection refused（演示数据：模拟通知终端当时不可用）"));
        }

        log.info("演示数据重置完成 单数={} 耗时={}ms", SCRIPT.length, System.currentTimeMillis() - start);
    }
}
