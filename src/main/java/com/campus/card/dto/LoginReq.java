package com.campus.card.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Data
public class LoginReq {
    @NotBlank(message = "账号和密码不能为空")
    private String account;
    @NotBlank(message = "账号和密码不能为空")
    private String password;
}
