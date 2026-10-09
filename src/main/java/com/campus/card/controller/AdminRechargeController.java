package com.campus.card.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.card.common.Result;
import com.campus.card.common.UserContext;
import com.campus.card.service.AdminRechargeService;
import com.campus.card.vo.LoginUser;
import com.campus.card.vo.RechargeOrderVO;
import com.campus.card.vo.TodayStatVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Description 管理端 · 充值单
 * @Author u
 * @Date 2026/10/8
 */
@Tag(name = "05 · 管理端 · 充值单")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AdminRechargeController {

    private final AdminRechargeService adminRechargeService;

    @Operation(summary = "首页6张卡")
    @GetMapping("/stat/today")
    public Result<TodayStatVO> today(){
        UserContext.requireAdmin();
        return Result.ok(adminRechargeService.today());
    }
    @Operation(summary = "充值单分页（管理端）")
    @GetMapping("/recharge/page")
    public Result<IPage<RechargeOrderVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String payMethod,
            @RequestParam(required = false) String keyword){
        UserContext.requireAdmin();
        return Result.ok(adminRechargeService.page(current,size,status,payMethod,keyword));
    }

    @Operation(summary = "重发通知")
    @PostMapping("/recharge/notify/retry")
    public Result<RechargeOrderVO> retryNotify(@RequestParam String orderNo){
        UserContext.requireAdmin();
        return Result.ok(adminRechargeService.retryNotify(orderNo));
    }

    @Operation(summary = "人工补单")
    @PostMapping("/recharge/mock-paid")
    public Result<RechargeOrderVO> mockPaid(@RequestParam String orderNo){
        LoginUser me=UserContext.requireAdmin();
        return Result.ok(adminRechargeService.mockPaid(orderNo,me.getAccount()));
    }

}


