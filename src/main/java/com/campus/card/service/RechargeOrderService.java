package com.campus.card.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.card.dto.RechargeCreateReq;
import com.campus.card.vo.LoginUser;
import com.campus.card.vo.RechargeOrderVO;
import jakarta.validation.Valid;

/**
 * @Description
 * @Author u
 * @Date 2026/10/2
 */
public interface RechargeOrderService {

    /** 创建充值单（三层幂等） */
    RechargeOrderVO createOrder(LoginUser user, RechargeCreateReq req);

    /** 我的充值记录分页。status 为 null 表示不过滤 */
    IPage<RechargeOrderVO> pageMyOrders(LoginUser user, Integer status, long current, long size);

    /** 充值单详情。只能看自己的单 */
    RechargeOrderVO getMyOrder(LoginUser user, String orderNo);
}
