package com.campus.card.service;


import com.campus.card.dto.LoginReq;
import com.campus.card.vo.LoginVO;
import jakarta.validation.Valid;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
public interface AuthService{

    LoginVO login(@Valid LoginReq req);
}
