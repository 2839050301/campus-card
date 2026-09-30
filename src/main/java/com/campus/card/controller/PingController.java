package com.campus.card.controller;

import com.campus.card.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Tag(name="00 · 健康检查")
@RestController
@RequestMapping("/api")
public class PingController {

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "campus-card");
        data.put("version", "1.0.0");
        data.put("time", LocalDateTime.now().toString());
        return Result.ok(data);
    }
}
