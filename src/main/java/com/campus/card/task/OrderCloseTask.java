package com.campus.card.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.mapper.RechargeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 超时未支付的充值单自动关闭。
 * @Author u
 * @Date 2026/10/5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseTask {

    private static final int BATCH = 200;

    private final RechargeOrderMapper rechargeOrderMapper;

    /**
     * 关闭过期订单 每分钟跑一次 启动后20秒才开始
     *
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void closeExpiredOrders() {
        try {
            List<RechargeOrder> expired = rechargeOrderMapper.selectList(
                    new LambdaQueryWrapper<RechargeOrder>()
                            .eq(RechargeOrder::getStatus, OrderStatusConstant.WAITING_FOR_PAYMENT)
                            .lt(RechargeOrder::getExpireTime, LocalDateTime.now())
                            .orderByAsc(RechargeOrder::getId)
                            .last("limit " + BATCH));
            if (expired.isEmpty()) {
                return;
            }
            int closed = 0;
            for (RechargeOrder order : expired) {
                order.setStatus(OrderStatusConstant.CLOSED);
                order.setCloseTime(LocalDateTime.now());
                order.setRemark("超时未支付，自动关闭");
                rechargeOrderMapper.updateById(order);
                log.info("超时关单 orderNo={} studentNo={} 创建于 {}",
                        order.getOrderNo(), order.getStudentNo(), order.getCreateTime());
                closed++;
            }
            log.info("超时关闭{}笔", closed);
        } catch (Exception e) {
            log.error("超时关单任务异常", e);
        }

    }
}
