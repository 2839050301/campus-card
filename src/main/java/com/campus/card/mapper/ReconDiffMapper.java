package com.campus.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.card.entity.ReconDiff;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @Description
 * @Author u
 * @Date 2026/10/6
 */
@Mapper
public interface ReconDiffMapper extends BaseMapper<ReconDiff> {

    /**
     * 把某次任务扫出来的旧差异全删掉。
     * ★ 为什么必须【物理删】而不是标记删除：
     *   对账是「重算」，不是「追加」。上一次扫出来的差异如果留着，
     *   这一次再扫一遍就会变成两份，diff_count 和明细页全部翻倍。
     *   t_recon_diff 是【派生数据】——它完全由 t_channel_bill + t_recharge_order
     *   + t_account_flow 三张表推出来，随时可以重算，所以删掉不可惜。
     *   （唯一不能丢的是 handled 那三列，见第 5 步：重算前先抄出来。）
     */
    @Delete("DELETE FROM t_recon_diff WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);
}
