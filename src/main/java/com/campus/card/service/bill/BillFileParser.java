package com.campus.card.service.bill;

import com.campus.card.common.BizException;
import com.campus.card.entity.ChannelBill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @Description 渠道账单文件解析器：定位→读→解析→校验。它不碰数据库。
 * @Author u
 * @Date 2026/10/9
 */
@Slf4j
@Component
public class BillFileParser {

    /**
     * 文件名里的日期：20261010（紧凑，没有分隔符）
     */
    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * UTF-8 BOM被按UTF-8读进来之后的样子
     */
    private static final String BOM="\uFEFF";
    private static final String META_DATE = "账单日期：";
    private static final String META_CHANNEL = "渠道：";
    private static final String HEAD_FIRST = "交易时间";
    private static final String FOOT_COUNT = "总笔数：";
    private static final String FOOT_AMOUNT = "总金额：";

    private static final String COL_ORDER_NO = "渠道订单号";
    private static final String COL_AMOUNT = "交易金额(元)";
    private static final String COL_STATUS = "交易状态";

    /** 账单文件所在目录，相对工作目录 */
    @Value("${campus.recon.bill-dir:./bill}")
    private String billDir;

    /**
     * 解析一份账单文件。
     * 任何一步不过就抛业务异常，不返回半份结果。
     *
     * @param channel  渠道，已经被 normalize 过（WECHAT / ALIPAY / UNIONPAY）
     * @param billDate 账单日期
     * @return 解析结果；明细已经在内存里，还没有入库
     */
    public BillParseResult parse(String channel, LocalDate billDate) {
        //定位文件
        String fileName = channel+"_"+billDate.format(FILE_DAY)+".csv";
        Path path= Paths.get(billDir).resolve(fileName);
        if(!Files.isRegularFile(path)){
            throw new BizException("渠道账单文件不存在："+path.toAbsolutePath()
            +"（演示环境可以先调POST /api/mock/bill/generate）生成一份");
        }
        //读取
        List<String> lines=readLines(path,fileName);

        //解析 校验
        BillParseResult result=doParse(lines,channel,billDate,fileName);
        log.info("渠道账单解析成功file={}明细{}行合计{}分",
                fileName,result.rowCount(),result.totalAmount());
        return result;
    }

    /** 读文件：显式 UTF-8、剥掉第一行的 BOM、丢掉空行 */
    private List<String> readLines(Path path, String fileName) {
        List<String> raw;
        try {
            raw = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException("渠道账单文件读取失败：" + fileName + "，" + e.getMessage());
        }
        List<String> lines = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String line = raw.get(i);
            if (i == 0) {
                line = line.replace(BOM, "");   // 见 2.3：不剥掉，第一列的名字就不是你以为的那个
            }
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }
    /** 解析 + 校验：任何一处不对，整份拒收 */
    private BillParseResult doParse(List<String> lines, String channel, LocalDate billDate, String fileName) {
        // 文件头：文件自称的日期和渠道
        LocalDate fileDate = null;
        String fileChannel = null;
        int headIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(META_DATE)) {
                fileDate = toDate(line.substring(META_DATE.length()), fileName);
            } else if (line.startsWith(META_CHANNEL)) {
                fileChannel = line.substring(META_CHANNEL.length()).trim();
            } else if (line.startsWith(HEAD_FIRST)) {
                headIdx = i;
                break;
            }
        }
        if (fileDate == null || fileChannel == null) {
            throw new BizException("渠道账单文件缺少账单日期或渠道这两行：" + fileName);
        }
        if (!fileChannel.equalsIgnoreCase(channel)) {
            throw new BizException("渠道账单文件的渠道是 " + fileChannel + "，本次要拉的是 " + channel);
        }
        if (!fileDate.equals(billDate)) {
            throw new BizException("渠道账单文件的日期是 " + fileDate + "，本次要拉的是 " + billDate);
        }
        if (headIdx < 0) {
            throw new BizException("渠道账单文件没有表头行（第一列应该是「" + HEAD_FIRST + "」）：" + fileName);
        }

        // 表头：按列名建映射
        Map<String, Integer> cols = parseHead(lines.get(headIdx));

        // 明细：一行一行，遇到汇总就停
        List<ChannelBill> rows = new ArrayList<>();
        int footIdx = -1;
        for (int i = headIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(FOOT_COUNT) || line.startsWith(FOOT_AMOUNT)) {
                footIdx = i;
                break;
            }
            rows.add(toRow(line, cols, channel, billDate, i + 1, fileName));
        }
        if (footIdx < 0) {
            throw new BizException("渠道账单文件没有汇总行，无法校验完整性：" + fileName);
        }

        // 汇总：文件的校验和
        Integer fileCount = null;
        Long fileAmount = null;
        for (int i = footIdx; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(FOOT_COUNT)) {
                fileCount = toInt(line.substring(FOOT_COUNT.length()), "汇总", fileName);
            } else if (line.startsWith(FOOT_AMOUNT)) {
                fileAmount = toFen(line.substring(FOOT_AMOUNT.length()), "汇总行", fileName);
            }
        }
        if (fileCount == null || fileAmount == null) {
            throw new BizException("渠道账单文件的汇总行不完整（总笔数和总金额都要有）：" + fileName);
        }
        long sum = rows.stream().mapToLong(ChannelBill::getAmount).sum();
        if (fileCount.intValue() != rows.size() || fileAmount.longValue() != sum) {
            throw new BizException("渠道账单文件汇总对不上：文件说 " + fileCount + " 笔 / " + fileAmount
                    + " 分，实际解析出 " + rows.size() + " 笔 / " + sum + " 分");
        }
        return new BillParseResult(fileName, rows);
    }

    /** 表头 → 列名到列号的映射；顺便检查我要的三列都在（多了的列不管，那是常态） */
    private Map<String, Integer> parseHead(String headLine) {
        String[] head = headLine.split(",", -1);
        Map<String, Integer> cols = new HashMap<>();
        for (int i = 0; i < head.length; i++) {
            cols.put(head[i].trim().replace(BOM, ""), i);
        }
        for (String need : List.of(COL_ORDER_NO, COL_AMOUNT, COL_STATUS)) {
            if (!cols.containsKey(need)) {
                throw new BizException("渠道账单文件缺列「" + need + "」，实际列名：" + String.join(" / ", cols.keySet()));
            }
        }
        return cols;
    }

    /** 一行明细 → 一个账单行对象；列数不对、渠道单号是空的、金额不是数字，一律拒收 */
    private ChannelBill toRow(String line, Map<String, Integer> cols, String channel, LocalDate billDate,
                              int lineNo, String fileName) {
        String[] parts = line.split(",", -1);
        if (parts.length != cols.size()) {
            throw new BizException("渠道账单文件第 " + lineNo + " 行有 " + parts.length
                    + " 列，表头是 " + cols.size() + " 列（文件 " + fileName + "）");
        }
        String orderNo = parts[cols.get(COL_ORDER_NO)].trim();
        if (orderNo.isEmpty()) {
            throw new BizException("渠道账单文件第 " + lineNo + " 行的渠道单号是空的 —— 对账靠它把两边对上，不能空");
        }
        ChannelBill bill = new ChannelBill();
        bill.setBillDate(billDate);
        bill.setChannel(channel);
        bill.setChannelOrderNo(orderNo);
        bill.setAmount(toFen(parts[cols.get(COL_AMOUNT)], "第 " + lineNo + " 行", fileName));
        bill.setTradeStatus(parts[cols.get(COL_STATUS)].trim());
        return bill;
    }

    /** 「元」→「分」：全系统只有这里做这件事，见 2.2 */
    private long toFen(String yuan, String where, String fileName) {
        try {
            return new BigDecimal(yuan.trim()).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new BizException("渠道账单文件" + where + "的金额不是合法的元金额：「"
                    + yuan.trim() + "」（文件 " + fileName + "）");
        }
    }

    private int toInt(String text, String where, String fileName) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new BizException("渠道账单文件" + where + "的笔数不是数字：「"
                    + text.trim() + "」（文件 " + fileName + "）");
        }
    }

    private LocalDate toDate(String text, String fileName) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BizException("渠道账单文件的账单日期格式不对：「" + text.trim()
                    + "」，应该写成 2026-10-10 这样（文件 " + fileName + "）");
        }
    }

}
