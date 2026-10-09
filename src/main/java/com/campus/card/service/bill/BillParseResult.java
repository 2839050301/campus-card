package com.campus.card.service.bill;

import com.campus.card.entity.ChannelBill;

import java.util.List;

/**
 * @Description 一份渠道账单文件解析出来的结果
 * @Author u
 * @Date 2026/10/9
 */
public record BillParseResult(String fileName, List<ChannelBill> rows) {
    /**
     * 行数
     *
     * @return int
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * 明细金额合计（分）
     *
     * @return long
     */
    public long totalAmount(){
        return rows.stream().mapToLong(ChannelBill::getAmount).reduce(0L, Long::sum);
    }
}
