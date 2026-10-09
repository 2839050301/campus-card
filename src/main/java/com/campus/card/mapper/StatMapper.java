package com.campus.card.mapper;

import com.campus.card.vo.TodayStatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @Description 首页统计。 5 个数来自同一条 SELECT —— 多个标量子查询读的是同一个读视图，
 *  *              要么都看到刚进来的那笔单，要么都看不到
 * @Author u
 * @Date 2026/10/8
 */
@Mapper
public interface StatMapper {

    @Select("select" +
            "(select count(*) from t_recharge_order where bill_date=CURDATE()) AS orderCount," +
            "(select count(*) from t_recharge_order where bill_date=CURDATE() AND status=2) AS paidCount," +
            "(select IFNULL(sum(amount),0) from t_recharge_order where " +
            "bill_date=CURDATE() AND status=2) AS paidAmount," +
            "(select count(*) from t_notify_record where status=2) AS notifyFailCount," +
            "(select IFNULL(sum(balance),0) from t_card_account) AS balanceTotal")
    TodayStatVO selectToday();
}
