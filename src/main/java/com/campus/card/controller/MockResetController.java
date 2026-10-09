package com.campus.card.controller;

import com.campus.card.common.Result;
import com.campus.card.constant.ReconConstant;
import com.campus.card.service.DemoDataService;
import com.campus.card.service.bill.DemoBillFileWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * @Description
 * @Author u
 * @Date 2026/10/8
 */
@Tag(name="04 · 演示专用")
@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
@Profile("!prod")
public class MockResetController {
    private final DemoDataService demoDataService;
    private final DemoBillFileWriter demoBillFileWriter;

    @Operation(summary = "重置演示数据")
    @PostMapping("/reset")
    public Result<Map<String,Object>> reset() {
        // ★ reset() 里面是事务：这一行返回的时候，事务已经提交了
        demoDataService.reset();
        // ★ 写文件必须放在事务提交【之后】：文件系统不受事务保护，回滚不回滚它（见 2.6 / 3.6）
        String billFile = demoBillFileWriter.write(ReconConstant.DEFAULT_CHANNEL, LocalDate.now());
        return Result.ok(Map.of("reset", true, "billFile", billFile));
    }

    @Operation(summary = "生成渠道账单文件（扮演渠道：只写文件，不写库）")
    @PostMapping("/bill/generate")
    public Result<Map<String,Object>> generateBill(
            @RequestParam(required = false, defaultValue = ReconConstant.DEFAULT_CHANNEL) String channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDate) {
        LocalDate date = (billDate == null) ? LocalDate.now() : billDate;
        String billFile = demoBillFileWriter.write(channel, date);
        return Result.ok(Map.of("file", billFile));
    }
}