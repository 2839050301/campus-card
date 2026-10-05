package com.campus.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.card.entity.NotifyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 终端通知记录
 * @Author u
 * @Date 2026/10/3
 */
@Mapper
public interface NotifyRecordMapper extends BaseMapper<NotifyRecord> {

    /**
     * 捞出该投递的通知
     *
     * @param now   现在
     * @param limit 限制
     * @return {@link List }<{@link NotifyRecord }>
     */
    @Select("SELECT * FROM t_notify_record " +
            "WHERE status=0 AND next_retry_time<=#{now} " +
            "ORDER BY next_retry_time LIMIT #{limit}")
    List<NotifyRecord> selectDue(@Param("now") LocalDateTime now,
                                 @Param("limit") int limit);
}
