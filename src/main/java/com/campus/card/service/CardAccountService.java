package com.campus.card.service;

import com.campus.card.vo.AccountVO;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
public interface CardAccountService {

    /**
     * 查某个学生的账户信息
     * @param studentNo 学生学号
     * @return
     */
    AccountVO getMyAccount(String studentNo);
}
