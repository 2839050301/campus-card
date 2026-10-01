package com.campus.card.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.card.common.BizException;
import com.campus.card.common.Result;
import com.campus.card.common.UserContext;
import com.campus.card.entity.CardAccount;
import com.campus.card.mapper.AccountFlowMapper;
import com.campus.card.service.AccountFlowService;
import com.campus.card.service.CardAccountService;
import com.campus.card.vo.AccountVO;
import com.campus.card.vo.FlowVO;
import com.campus.card.vo.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
@Tag(name = "02 · 学生端 · 账户")
@RequestMapping("/api/card")
@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountFlowService accountFlowService;
    private final CardAccountService cardAccountService;

    @Operation(summary = "查当前登录学生的账户与余额")
    @GetMapping("/account")
    public Result<AccountVO> getAccount() {
        LoginUser user = requireStudent();
        return Result.ok(cardAccountService.getMyAccount(user.getAccount()));
    }


    @Operation(summary = "分页查询当前登录学生的账户流水")
    @GetMapping("/flow")
    public Result<IPage<FlowVO>> flow(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String flowType){
        LoginUser user = requireStudent();
        return Result.ok(accountFlowService.pageFlow(user.getAccount(),flowType,current,size));

    }






    private LoginUser requireStudent() {
        LoginUser me = UserContext.verifyLogin();
        if (!"STUDENT".equals(me.getRole())) {
            throw new BizException("该接口仅学生可用");
        }
        return me;
    }


}
