package com.campus.card.controller;

import com.campus.card.common.Result;
import com.campus.card.common.UserContext;
import com.campus.card.dto.ConsumeReq;
import com.campus.card.service.TerminalService;
import com.campus.card.vo.ConsumeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description 消费终端（刷卡付款）
 * @Author u
 * @Date 2026/10/7
 */
@Tag(name="07 · 消费终端")
@RestController
@RequestMapping("/api/terminal")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TerminalController {
    private final TerminalService terminalService;

    @Operation(summary = "食堂刷卡消费（扣余额）")
    @PostMapping("/consume")
    public Result<ConsumeVO> consume(@RequestBody @Valid ConsumeReq req){
        UserContext.requireAdmin();
        return Result.ok(terminalService.consume(req));
    }
}
