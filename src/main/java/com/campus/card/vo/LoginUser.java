package com.campus.card.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginUser {
    private Long userId;
    private String account;    // 学号或工号
    private String name;
    private String role;       // STUDENT / ADMIN
    private String cardNo;     // 卡号
    private String college;    // 学员
}
