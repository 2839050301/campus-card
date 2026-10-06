package com.campus.card.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.card.common.Result;
import com.campus.card.common.UserContext;
import com.campus.card.service.ReconService;
import com.campus.card.vo.LoginUser;
import com.campus.card.vo.ReconDiffVO;
import com.campus.card.vo.ReconTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * @Description 管理端 · 对账
 * @Author u
 * @Date 2026/10/5
 */
@Tag(name = "06 · 管理端 · 对账")
@RestController
@RequestMapping("/api/admin/recon")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AdminReconController {
    private final ReconService reconService;

    @Operation(summary = "对账任务分页")
    @GetMapping("/task")
    public Result<IPage<ReconTaskVO>> taskPage(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size) {
        UserContext.requireAdmin();
        return Result.ok(reconService.pageTasks(current, size));
    }

    @Operation(summary = "触发对账")
    @PostMapping("/run")
    public Result<ReconTaskVO> run(@RequestParam(required = false) String billDate,
                                   @RequestParam(required = false) String channel) {
        // ★ requireAdmin 的返回值就是当前登录人，直接拿来当 operator 写进任务表
        LoginUser me = UserContext.requireAdmin();
        String date = (billDate == null || billDate.isBlank())
                ? LocalDate.now().toString()
                : billDate.trim();
        return Result.ok(reconService.run(date, channel, me.getAccount()));
    }

    @Operation(summary = "差异明细分页")
    @GetMapping("/diff")
    public Result<IPage<ReconDiffVO>> diffPage(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long taskId,
                                               @RequestParam(required = false) String diffType) {
        UserContext.requireAdmin();
        return Result.ok(reconService.pageDiffs(current, size, taskId, diffType));
    }

    @Operation(summary = "人工核销差异")
    @PostMapping("/diff/handle")
    public Result<ReconDiffVO> handle(@RequestParam Long id,
                                      @RequestParam(required = false) String remark) {
        LoginUser me = UserContext.requireAdmin();
        return Result.ok(reconService.handle(id, remark, me.getAccount()));
    }

}
