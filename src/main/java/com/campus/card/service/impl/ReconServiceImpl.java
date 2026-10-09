package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.card.common.BizException;
import com.campus.card.constant.FlowTypeConstant;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.constant.ReconConstant;
import com.campus.card.entity.*;
import com.campus.card.mapper.*;
import com.campus.card.service.ReconService;
import com.campus.card.service.bill.BillFileParser;
import com.campus.card.vo.ReconDiffVO;
import com.campus.card.vo.ReconTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.campus.card.service.bill.BillParseResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @Description
 * @Author u
 * @Date 2026/10/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconServiceImpl implements ReconService {
    private final ReconTaskMapper reconTaskMapper;
    private final ChannelBillMapper channelBillMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final AccountFlowMapper accountFlowMapper;
    private final ReconDiffMapper reconDiffMapper;
    private final BillFileParser billFileParser;

    @Override
    /**
     * 把渠道账单拉进来：读渠道给的账单文件，整份校验通过之后才入库。
     *
     * ★ 这里【故意不写】@Transactional：
     *   它在 run() 里是 this.pullChannelBill(...) 调用的，不走 Spring 代理，
     *   注解开不了新事务，留着只会让人误以为它是独立的事务边界。
     *   事务由调用方 run() 提供，两者共用同一条连接、同一个事务。
     *   （将来若要把它单独暴露出去，得从另一个 Bean 调过来，注解才会生效。）
     *
     * @param billDate 账单日期，格式 yyyy-MM-dd
     * @param channel  渠道，WECHAT / ALIPAY / UNIONPAY
     * @return 本次新入库的渠道账单条数；该日该渠道已有账单时幂等返回 0
     */
    public int pullChannelBill(String billDate, String channel) {
        LocalDate date = parseDate(billDate);
        String ch = normalizeChannel(channel);

        // 幂等闸门：该日该渠道已经拉过就不再拉（同一份文件拉两次，账目会凭空翻倍）
        Long exists = channelBillMapper.selectCount(new LambdaQueryWrapper<ChannelBill>()
                .eq(ChannelBill::getBillDate, date)
                .eq(ChannelBill::getChannel, ch));
        if (exists != null && exists > 0) {
            log.info("渠道账单已存在，跳过拉取 billDate={} channel={}", date, ch);
            return 0;
        }

        // 解析与入库分开：先让解析器把整份文件读完、校验完，拿到内存里的一份完整结果
        BillParseResult parsed = billFileParser.parse(ch, date);

        // 全部通过了，才统一入库
        for (ChannelBill b : parsed.rows()) {
            channelBillMapper.insert(b);
        }
        log.info("拉取渠道账单 file={} 解析并入账 {} 行 合计 {} 分",
                parsed.fileName(), parsed.rowCount(), parsed.totalAmount());
        return parsed.rowCount();
    }

    @Override
    /**
     * 对账任务分页：按首次对账时间倒序，最新跑的那次排最前
     *
     * @param current 第几页，从 1 开始
     * @param size    每页几条
     * @return 任务分页结果（每条已转成 ReconTaskVO，platform* 已改名成 local*）
     */ public IPage<ReconTaskVO> pageTasks(long current, long size) {
        Page<ReconTask> page = reconTaskMapper.selectPage(new Page<ReconTask>(current, size),
                new LambdaQueryWrapper<ReconTask>()
                        .orderByDesc(ReconTask::getCreateTime)
                        .orderByDesc(ReconTask::getId));
        return page.convert(this::toTaskVO);
    }

    //归并
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconTaskVO run(String billDate, String channel, String operator) {
        long start = System.currentTimeMillis();
        LocalDate date = parseDate(billDate);
        String ch = normalizeChannel(channel);

        int pulled = pullChannelBill(billDate, ch);
        log.info("对账开始 billDate={} channel={} operator={} 本次拉取账单 {} 行",
                date, ch, operator, pulled);


        //平台侧
        List<RechargeOrder> platform = rechargeOrderMapper.selectList(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getBillDate, date)
                .eq(RechargeOrder::getStatus, OrderStatusConstant.PAID)
                .orderByAsc(RechargeOrder::getChannelOrderNo));

        if (platform.isEmpty()) {
            throw new BizException(date + " 没有支付成功的充值单，无需对账");
        }

        // 渠道侧：当日该渠道的全部账单行，按同一个键排序
        List<ChannelBill> bill = channelBillMapper.selectList(new LambdaQueryWrapper<ChannelBill>()
                .eq(ChannelBill::getBillDate, date)
                .eq(ChannelBill::getChannel, ch)
                .orderByAsc(ChannelBill::getChannelOrderNo));

        // 双指针归并
        List<ReconDiff> diffs = new ArrayList<>();
        int i = 0, j = 0;
        while (i < platform.size() && j < bill.size()) {
            //平台
            RechargeOrder p = platform.get(i);

            //渠道
            ChannelBill c = bill.get(j);

            int cmp = safe(p.getChannelOrderNo()).compareTo(safe(c.getChannelOrderNo()));
            if (cmp == 0) {
                compareBoth(p, c, diffs);       // 这一笔两边都有，进去看金额/状态
                i++;
                j++;
            } else if (cmp < 0) {
                diffs.add(localOnly(p));        // 平台小 → 渠道那边没有它
                i++;
            } else {
                diffs.add(channelOnly(c));      // 渠道小 → 平台这边没有它
                j++;
            }
        }
        // 谁先走完，另一个剩下的就全是单边的
        while (i < platform.size()) {
            diffs.add(localOnly(platform.get(i++)));
        }
        while (j < bill.size()) {
            diffs.add(channelOnly(bill.get(j++)));
        }

        //已收款未入账
        List<String> orderNos = platform.stream().map(RechargeOrder::getOrderNo).toList();
        Set<String> postedOrderNos = accountFlowMapper.selectList(new LambdaQueryWrapper<AccountFlow>()
                        .select(AccountFlow::getOrderNo)
                        .eq(AccountFlow::getFlowType, FlowTypeConstant.RECHARGE)
                        .in(AccountFlow::getOrderNo, orderNos))
                .stream()
                .map(AccountFlow::getOrderNo)
                .collect(Collectors.toSet());
        for (RechargeOrder p : platform) {
            if (!postedOrderNos.contains(p.getOrderNo())) {
                diffs.add(makeDiff(p.getOrderNo(), p.getChannelOrderNo(), ReconConstant.DIFF_NOT_POSTED,
                        p.getAmount(), p.getAmount(), "渠道与充值单都有这笔钱，" +
                                "但账户流水缺失 —— 学生钱扣了余额没加只有对三方账才能发现"));
            }
        }
        //落库
        long platAmount = platform.stream().mapToLong(RechargeOrder::getAmount).sum();
        long chanAmount = bill.stream().mapToLong(ChannelBill::getAmount).sum();
        long cost = System.currentTimeMillis() - start;

        //同一天同一渠道只能有一个任务 所以找到更新找不到就insert
        ReconTask task = reconTaskMapper.selectOne(new LambdaQueryWrapper<ReconTask>()
                .eq(ReconTask::getBillDate, date)
                .eq(ReconTask::getChannel, ch));

        if (task == null) {
            task = new ReconTask();
            task.setBillDate(date);
            task.setChannel(ch);
            task.setOperator(operator);
            task.setCostMs(cost);
            task.setStatus(ReconConstant.TASK_DONE);
            task.setPlatformCount(platform.size());
            task.setPlatformAmount(platAmount);
            task.setChannelCount(bill.size());
            task.setChannelAmount(chanAmount);
            task.setDiffCount(diffs.size());
            reconTaskMapper.insert(task);
        } else {
            task.setPlatformCount(platform.size());
            task.setPlatformAmount(platAmount);
            task.setChannelCount(bill.size());
            task.setChannelAmount(chanAmount);
            task.setDiffCount(diffs.size());
            task.setCostMs(cost);
            task.setStatus(ReconConstant.TASK_DONE);
            task.setOperator(operator);
            //   这里用 updateById 是安全的：上面把九个字段全都显式 set 了，
            //   没有一个留成 null。updateById 的默认策略是 NOT_NULL（null 字段不更新），
            //   所以「忘了 set 某个字段」的表现是「那一列保持上次的值」，
            //   而不是报错 —— 这比报错更难查。九个字段一个一个数。
            reconTaskMapper.updateById(task);
        }

        //    重算之前，先把「人工已经核销过」的状态抄出来。
        //    不抄的后果：管理员昨天核销了 3 条差异，今天重跑一次，
        //    这 3 条又变回「未处理」，管理员明天再核销一遍。
        //    差异是重算出来的，但【人的处理动作】不是数据推出来的，必须继承。
        Map<String, ReconDiff> handledBefore = new HashMap<>();
        for (ReconDiff old : reconDiffMapper.selectList(new LambdaQueryWrapper<ReconDiff>()
                .eq(ReconDiff::getTaskId, task.getId())
                .eq(ReconDiff::getHandled, ReconConstant.HANDLED))) {
            handledBefore.put(keyOf(old), old);
        }

        reconDiffMapper.deleteByTaskId(task.getId());
        for (ReconDiff d : diffs) {
            ReconDiff old = handledBefore.get(keyOf(d));
            if (old != null) {
                d.setHandled(ReconConstant.HANDLED);
                d.setHandleRemark(old.getHandleRemark());
                d.setHandleTime(old.getHandleTime());
            }
            d.setTaskId(task.getId());
            reconDiffMapper.insert(d);
        }

        log.info("对账完成 taskId={} 平台 {} 笔 {} / 渠道 {} 笔 {} / 差异 {} 笔 / 耗时 {}ms",
                task.getId(), platform.size(), yuan(platAmount),
                bill.size(), yuan(chanAmount), diffs.size(), cost);
        return toTaskVO(reconTaskMapper.selectById(task.getId()));
    }

    @Override
    public IPage<ReconDiffVO> pageDiffs(long current, long size, Long taskId, String diffType) {
        IPage<ReconDiff> page = reconDiffMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ReconDiff>()
                        .eq(taskId != null, ReconDiff::getTaskId, taskId)
                        // ★ 空串也要当「没传」：前端 admin-recon.html:431-433
                        //   会把 "" 和 null 从 query 里删掉，但手敲 curl 的人可能传 ?diffType=
                        .eq(diffType != null && !diffType.isBlank(), ReconDiff::getDiffType, diffType)
                        .orderByDesc(ReconDiff::getId));
        return page.convert(this::toDiffVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconDiffVO handle(Long id, String remark, String operator) {
        ReconDiff now = reconDiffMapper.selectById(id);
        if (now == null) {
            throw new BizException("差异记录不存在");
        }

        String text = (remark == null || remark.isBlank())
                ? "已由 " + operator + " 人工核销"
                : remark.trim();

        // ★★ 条件更新：把「还没处理过」这个前提写进 WHERE 里。
        //    「先查再改」在两个人同时点的时候会双双通过；
        //    写成 WHERE handled = 0 之后，数据库保证只有一个 UPDATE 影响 1 行。
        int n = reconDiffMapper.update(null, new LambdaUpdateWrapper<ReconDiff>()
                .eq(ReconDiff::getId, id)
                .eq(ReconDiff::getHandled, ReconConstant.UNHANDLED)
                .set(ReconDiff::getHandled, ReconConstant.HANDLED)
                .set(ReconDiff::getHandleRemark, text)
                .set(ReconDiff::getHandleTime, LocalDateTime.now()));

        if (n == 0) {
            throw new BizException("该差异已被其他人处理，请刷新后查看");
        }
        log.info("对账差异已核销 diffId={} operator={} remark={}", id, operator, text);
        return toDiffVO(reconDiffMapper.selectById(id));
    }


    // VO转换
    private ReconTaskVO toTaskVO(ReconTask t) {
        ReconTaskVO vo = new ReconTaskVO();
        vo.setId(t.getId());
        vo.setBillDate(t.getBillDate());
        vo.setChannel(t.getChannel());
        vo.setLocalCount(t.getPlatformCount());
        vo.setLocalAmount(t.getPlatformAmount());
        vo.setChannelCount(t.getChannelCount());
        vo.setChannelAmount(t.getChannelAmount());
        vo.setDiffCount(t.getDiffCount());
        vo.setStatus(t.getStatus());
        vo.setCreateTime(t.getCreateTime());
        vo.setOperator(t.getOperator());
        //   表里没有 finish_time，它 = create_time + cost_ms。
        //   前端拿 finishTime - createTime 算耗时（admin-recon.html:178），
        //   两个 LocalDateTime 会被 JacksonConfig:44 序列化成毫秒数字，减法成立。
        if (t.getCreateTime() != null && t.getCostMs() != null) {
            vo.setFinishTime(t.getCreateTime().plusNanos(t.getCostMs() * 1_000_000L));
        }
        return vo;
    }

    private ReconDiffVO toDiffVO(ReconDiff d) {
        ReconDiffVO vo = new ReconDiffVO();
        vo.setId(d.getId());
        vo.setTaskId(d.getTaskId());
        vo.setOrderNo(d.getOrderNo());
        vo.setChannelOrderNo(d.getChannelOrderNo());
        vo.setDiffType(d.getDiffType());
        vo.setLocalAmount(d.getPlatformAmount());
        vo.setChannelAmount(d.getChannelAmount());
        vo.setHandled(d.getHandled());
        vo.setHandleRemark(d.getHandleRemark());
        vo.setRemark(d.getRemark());
        return vo;
    }

    //工具
    private LocalDate parseDate(String billDate) {
        if (billDate == null || billDate.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(billDate.trim());
        } catch (DateTimeParseException e) {
            throw new BizException("日期格式应为 yyyy-MM-dd，收到：" + billDate);
        }
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return ReconConstant.DEFAULT_CHANNEL;
        }
        return channel.trim().toUpperCase();
    }

    /**
     * 差异的身份：同一个单号 + 同一个类型 = 同一条差异（跨两次重跑认得出）
     */
    private String keyOf(ReconDiff d) {
        return safe(d.getOrderNo()) + "|" + safe(d.getDiffType()) + "|" + safe(d.getChannelOrderNo());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 分 → 元的展示串
     */
    private String yuan(Long fen) {
        long v = (fen == null) ? 0L : fen;
        return "¥" + BigDecimal.valueOf(v).movePointLeft(2).toPlainString();
    }


    /**
     * 通用工厂
     */
    private ReconDiff makeDiff(String orderNo, String channelOrderNo, String diffType,
                               Long platformAmount, Long channelAmount, String remark) {
        ReconDiff d = new ReconDiff();
        d.setOrderNo(orderNo == null ? "" : orderNo);
        d.setChannelOrderNo(channelOrderNo == null ? "" : channelOrderNo);
        d.setDiffType(diffType);
        d.setPlatformAmount(platformAmount == null ? 0L : platformAmount);
        d.setChannelAmount(channelAmount == null ? 0L : channelAmount);
        d.setRemark(remark);
        d.setHandled(ReconConstant.UNHANDLED);
        d.setHandleRemark("");
        return d;
    }

    /**
     * 两边都有的那一笔，进去比金额、比状态。
     * 如果渠道说这笔是 REFUND/CLOSED，平台却当成支付成功，
     * 那金额一样也没意义——先把状态这条更严重的问题报出来。
     * （一笔只报一条，避免同一笔同时冒出两条差异，让人以为有两笔账坏了。）
     */
    private void compareBoth(RechargeOrder p, ChannelBill c, List<ReconDiff> out) {
        if (!ReconConstant.TRADE_SUCCESS.equalsIgnoreCase(c.getTradeStatus())) {
            out.add(makeDiff(p.getOrderNo(), c.getChannelOrderNo(), ReconConstant.DIFF_STATUS_DIFF,
                    p.getAmount(), c.getAmount(),
                    "平台侧为支付成功，渠道侧状态为 " + c.getTradeStatus() + "，需确认是否已退款或关闭"));
            return;
        }
        if (!p.getAmount().equals(c.getAmount())) {
            out.add(makeDiff(p.getOrderNo(), c.getChannelOrderNo(), ReconConstant.DIFF_AMOUNT_DIFF,
                    p.getAmount(), c.getAmount(),
                    "两边金额不一致：平台 " + yuan(p.getAmount()) + "，渠道 " + yuan(c.getAmount())));
        }
    }

    /**
     * 短款：平台有、渠道没有 —— 钱比账少，得去追。
     */
    private ReconDiff localOnly(RechargeOrder p) {
        return makeDiff(p.getOrderNo(), "", ReconConstant.DIFF_LOCAL_ONLY,
                p.getAmount(), 0L,
                "渠道账单中未找到该笔交易，疑似渠道漏记");
    }

    /**
     * 长款：渠道有、平台没有 —— 钱比账多，补单即可。
     * ★ orderNo 填 ""，前端会把它显示成空白 + 下面一行渠道单号。
     */
    private ReconDiff channelOnly(ChannelBill c) {
        return makeDiff("", c.getChannelOrderNo(), ReconConstant.DIFF_CHANNEL_ONLY,
                0L, c.getAmount(),
                "渠道已收款但平台无对应充值单，钱进了学校账户却没记账，要立刻人工补单");
    }

}
