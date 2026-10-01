package com.campus.card.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.card.vo.FlowVO;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
public interface AccountFlowService {

    /**
     * 分页查某个学生的账户流水
     * @param studentNo
     * @param flowType
     * @param current
     * @param size
     * @return
     */
    IPage<FlowVO> pageFlow(String studentNo, String flowType, long current, long size);
}
