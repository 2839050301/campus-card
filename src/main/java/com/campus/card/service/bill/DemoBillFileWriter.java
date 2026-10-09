package com.campus.card.service.bill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.card.common.BizException;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.mapper.RechargeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Description 扮演渠道：按平台当天的成交写一份账单文件。
 *              ★ 它只写文件，不写数据库 —— 账单进库必须走「读文件」那条真路（见 3.5）。
 *              ★ @Profile("!prod")：一个会在生产环境写账单文件的类，是炸弹。
 * @Author u
 * @Date 2026/10/10
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DemoBillFileWriter {

    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String TRADE_SUCCESS = "SUCCESS";
    /** 演示差异：被改大的那笔加 1 元；凭空多出来的那笔是 50 元 */
    private static final long DEMO_AMOUNT_DIFF = 100L;
    private static final long DEMO_GHOST_AMOUNT = 5_000L;

    private final RechargeOrderMapper rechargeOrderMapper;

    @Value("${campus.recon.bill-dir:./bill}")
    private String billDir;

    /** 账单文件里的一行。只在这个类里用，所以写成嵌套的 record，不单独开文件 */
    private record Row(String time, String merchantNo, String channelNo, long amount, String status) {
    }

    /**
     * 写一份账单文件。
     *
     * @return 文件名（给接口回显和日志用）
     */
    public String write(String channel, LocalDate billDate) {
        String ch = channel.trim().toUpperCase();     // 渠道号的规范化只做一次，文件名要用它
        String fileName = ch + "_" + billDate.format(FILE_DAY) + ".csv";

        List<RechargeOrder> paid = rechargeOrderMapper.selectList(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getBillDate, billDate)
                .eq(RechargeOrder::getStatus, OrderStatusConstant.PAID)
                .orderByAsc(RechargeOrder::getChannelOrderNo));

        List<Row> rows = new ArrayList<>();
        for (RechargeOrder o : paid) {
            rows.add(new Row(o.getCreateTime().format(TIME), o.getOrderNo(), o.getChannelOrderNo(),
                    o.getAmount(), TRADE_SUCCESS));
        }

        // ① 金额不符：把第 2 笔改大 1 元（record 不可变，所以是替换而不是修改）
        // ★ 必须在「删第一笔」之前做：remove(0) 会把后面所有下标往前挪一位，
        //   先删再改，改到的就是原第 3 笔 —— Day 10 验收在这里抓到过一次
        String tampered = null;
        if (rows.size() > 1) {
            Row row = rows.get(1);
            row = new Row(row.time(), row.merchantNo(), row.channelNo(), row.amount() + DEMO_AMOUNT_DIFF, row.status());
            rows.set(1, row);
            tampered = row.channelNo();
        }
        // ② 短款：渠道账里漏掉一笔。列表已按渠道单号升序，漏掉的永远是第一笔，结果可复现
        String missing = null;
        if (rows.size() > 2) {
            missing = rows.remove(0).channelNo();
        }
        // ③ 长款：渠道多一笔平台根本没有的单（它只存在于这份文件里）
        String ghost = ghostChannelOrderNo(billDate);
        rows.add(new Row(LocalDateTime.now().format(TIME), "-", ghost, DEMO_GHOST_AMOUNT, TRADE_SUCCESS));

        long total = rows.stream().mapToLong(Row::amount).sum();

        List<String> lines = new ArrayList<>();
        lines.add(channelName(ch) + "账单明细");
        lines.add("账单日期：" + billDate);
        lines.add("渠道：" + ch);
        lines.add("交易时间,商户订单号,渠道订单号,交易金额(元),交易状态");
        for (Row r : rows) {
            lines.add(String.join(",", r.time(), r.merchantNo(), r.channelNo(),
                    yuan(r.amount()), r.status()));
        }
        lines.add("总笔数：" + rows.size());
        lines.add("总金额：" + yuan(total));

        Path path = Paths.get(billDir).resolve(fileName);
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            // ★ 真微信账单的最前面有三个字节 BOM，我们照抄，逼解析器真的处理它（见 2.3）
            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            try (OutputStream out = Files.newOutputStream(path)) {
                out.write(bom);
                out.write(String.join("\n", lines).concat("\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new BizException("渠道账单文件写入失败：" + path.toAbsolutePath() + "，" + e.getMessage());
        }
        log.info("已生成演示账单文件 {}：{} 行，合计 {} 分（漏掉 {}，改大 {}，多出 {}）",
                path.toAbsolutePath(), rows.size(), total, missing, tampered, ghost);
        return fileName;
    }

    /** 分 → 元，固定两位小数：10000 → 100.00 */
    private String yuan(long fen) {
        return BigDecimal.valueOf(fen).movePointLeft(2).toPlainString();
    }

    /** 文件标题里那句中文。认不出来的渠道就用渠道号本身 —— 别为了好看去猜 */
    private String channelName(String channel) {
        return switch (channel) {
            case "WECHAT" -> "微信支付";
            case "ALIPAY" -> "支付宝";
            case "UNIONPAY" -> "云闪付";
            default -> channel;
        };
    }

    /**
     * 幽灵单号：CH + 日期 + '9' + 三位随机。
     * ★ 真实单号的第 11 个字符是渠道流水序号（0~9），这里固定用 '9'，
     *   所以它一定排在所有真实单号之后，双指针扫到末尾才会遇到它，结果可复现。
     *   （这段注释是从被删掉的那个方法里搬过来的 —— 连理由一起搬。）
     */
    private String ghostChannelOrderNo(LocalDate date) {
        return "CH" + date.format(FILE_DAY) + "9" + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}