package com.campus.card.service;

import com.campus.card.entity.NotifyRecord;
import com.campus.card.entity.RechargeOrder;

/**
 * @Description
 * @Author u
 * @Date 2026/10/4
 */
public interface NotifyRecordService {

    /**
     * 入账成功后调用 —— 只写记录，【不发网络请求】。
     * ★ 必须在调用方的事务里执行：通知记录要和入账同生共死（见 §3.1）。
     */
    void createPending(RechargeOrder order);

    /**
     * 投递一条记录（首次和重试都走这里），返回是否成功。
     * ★ 只管投递和回写状态，不负责选记录。
     */
    boolean deliver(NotifyRecord record);

    /**
     * 捞出到点的记录投一遍。
     *
     * @return 本轮处理的条数（0 表示没活儿）
     */
    int deliverDue(int limit);

    /**
     * 把某单的通知放回待投递队列：状态改回待通知、重试次数清零、下次投递时间 = 现在。
     * 它不投递。投递这件事全系统只有调度器一个发起者。
     *
     * @return 受影响行数，0 表示这单没有通知记录
     */
    int requeue(String orderNo);
}
