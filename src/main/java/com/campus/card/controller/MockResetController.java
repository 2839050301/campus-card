package com.campus.card.controller;

import com.campus.card.common.Result;
import com.campus.card.service.DemoDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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


    @Operation(summary = "重置演示数据")
    @PostMapping("/reset")
    public Result<Map<String,Object>> reset() {
        demoDataService.reset();
        return Result.ok(Map.of("reset",true));
    }

}
