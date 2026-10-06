package com.campus.card.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.card.vo.ReconDiffVO;
import com.campus.card.vo.ReconTaskVO;

/**
 * @Description
 * @Author u
 * @Date 2026/10/5
 */
public interface ReconService {
    /**
     * 把某天某渠道的账单弄进 t_channel_bill。
     * ★ 真实系统 = 下载渠道账单文件 + 解析 + 入库；本项目 = 演示造数。
     * ★ 幂等：这一天这个渠道已经有账单了就直接返回 0。
     *
     * @return 这次新落了几行（0 = 早就拉过了）
     */
    int pullChannelBill(String billDate, String channel);

    /**
     * 跑一次对账。同一天同一个渠道重复跑 = 覆盖上一次结果。
     *
     * @param operator 谁触发的（管理员账号，写进 t_recon_task.operator）
     */
    ReconTaskVO run(String billDate, String channel, String operator);

    IPage<ReconTaskVO> pageTasks(long current, long size);

    IPage<ReconDiffVO> pageDiffs(long current, long size, Long taskId, String diffType);

    /**
     * 人工核销一条差异。
     */
    ReconDiffVO handle(Long id, String remark, String operator);
}
