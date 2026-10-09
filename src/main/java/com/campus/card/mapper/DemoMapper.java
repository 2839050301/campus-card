package com.campus.card.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @Description 演示数据重置专用。★ 唯一调用者是 DemoDataServiceImpl，
 *              而后者唯一的使用者是 MockResetController —— 那个 Controller 带 @Profile("!prod")
 * @Author u
 * @Date 2026/10/7
 */
@Mapper
public interface DemoMapper {

    /**
     * 把某个账户的余额直接设回「账本起点」。
     * ★ 这里必须写死数字：期初余额没有来源，它是人给的输入，
     *   和 sql/campus_card.sql 里的初始数据是同一个东西。
     */
    @Update("UPDATE t_card_account SET balance = #{balance} WHERE card_no = #{cardNo}")
    int resetBalance(@Param("cardNo") String cardNo, @Param("balance") long balance);

    @Delete("DELETE FROM t_notify_record")
    int deleteAllNotifyRecords();

    @Delete("DELETE FROM t_account_flow")
    int deleteAllAccountFlows();

    @Delete("DELETE FROM t_recharge_order")
    int deleteAllOrders();

    @Delete("DELETE FROM t_channel_bill")
    int deleteAllChannelBills();

    @Delete("DELETE FROM t_recon_diff")
    int deleteAllReconDiffs();

    @Delete("DELETE FROM t_recon_task")
    int deleteAllReconTasks();
}