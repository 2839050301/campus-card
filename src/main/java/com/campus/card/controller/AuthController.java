package com.campus.card.controller;

import com.campus.card.common.Result;
import com.campus.card.common.UserContext;
import com.campus.card.dto.LoginReq;
import com.campus.card.service.AuthService;
import com.campus.card.vo.LoginUser;
import com.campus.card.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
@Tag(name="01 · 认证",description = "登录+获取当前登录用户")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录(学生用学号，管理端用工号)")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public Result<LoginUser> me(){
        return Result.ok(UserContext.get());
    }
}
