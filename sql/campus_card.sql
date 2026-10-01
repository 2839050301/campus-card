-- ============================================================================
--  校园一卡通充值结算系统 · 建库建表脚本
--  用法：MySQL 客户端里整份跑一遍（Navicat / DataGrip / mysql 命令行都行）
--  说明：金额一律 BIGINT 存「分」；8 张表；含 2 个演示账号 + 1 张一卡通账户
-- ============================================================================

CREATE DATABASE IF NOT EXISTS campus_card
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE campus_card;

-- ---------------------------------------------------------------------------
-- 1. t_user  用户（学生 + 管理员）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    account     VARCHAR(32)  NOT NULL COMMENT '学号或工号',
    password    VARCHAR(64)  NOT NULL COMMENT 'BCrypt 哈希，60 字符，VARCHAR(64) 刚好放得下',
    name        VARCHAR(32)  NOT NULL,
    role        VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/ADMIN',
    college     VARCHAR(64)           DEFAULT '' COMMENT '学院',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account (account)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户（学生 + 管理员）';

-- ---------------------------------------------------------------------------
-- 2. t_card_account  一卡通账户（余额在这里）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_card_account;
CREATE TABLE t_card_account (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    card_no     VARCHAR(32) NOT NULL COMMENT '卡号',
    student_no  VARCHAR(32) NOT NULL,
    balance     BIGINT      NOT NULL DEFAULT 0 COMMENT '余额，单位：分',
    version     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1正常 0冻结',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_no (card_no),
    UNIQUE KEY uk_student_no (student_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='一卡通账户';

-- ---------------------------------------------------------------------------
-- 3. t_recharge_order  充值单（核心表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_recharge_order;
CREATE TABLE t_recharge_order (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    order_no         VARCHAR(40)  NOT NULL COMMENT '平台充值单号',
    request_no       VARCHAR(64)  NOT NULL COMMENT '客户端幂等号，uk_request_no 是防重复下单第一道防线',
    student_no       VARCHAR(32)  NOT NULL,
    card_no          VARCHAR(32)  NOT NULL,
    amount           BIGINT       NOT NULL COMMENT '充值金额，单位：分',
    pay_method       VARCHAR(16)  NOT NULL COMMENT 'WECHAT/ALIPAY/UNIONPAY',
    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0待支付 1支付中 2支付成功 3支付失败 4已关闭',
    channel_order_no VARCHAR(64)           DEFAULT '' COMMENT '渠道单号',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pay_time         DATETIME              DEFAULT NULL,
    close_time       DATETIME              DEFAULT NULL,
    expire_time      DATETIME     NOT NULL COMMENT '创建后 15 分钟',
    bill_date        DATE         NOT NULL COMMENT '账单日期，对账按它切',
    remark           VARCHAR(255)          DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_status_expire (status, expire_time),
    KEY idx_student_create (student_no, create_time),
    KEY idx_bill_date (bill_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='充值单';

-- ---------------------------------------------------------------------------
-- 4. t_account_flow  账户流水（防重复加钱的最后一道防线）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_account_flow;
CREATE TABLE t_account_flow (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    flow_no       VARCHAR(40)  NOT NULL,
    card_no       VARCHAR(32)  NOT NULL,
    student_no    VARCHAR(32)  NOT NULL,
    order_no      VARCHAR(40)  NOT NULL DEFAULT '-' COMMENT '关联充值单，消费流水填 -',
    flow_type     VARCHAR(16)  NOT NULL COMMENT 'RECHARGE/CONSUME/REFUND/ADJUST',
    amount        BIGINT       NOT NULL COMMENT '有符号：充值正、消费负',
    balance_after BIGINT       NOT NULL COMMENT '入账后余额，对账要核这个',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark        VARCHAR(255)          DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_type (order_no, flow_type),
    KEY idx_card_create (card_no, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='账户流水';

-- ---------------------------------------------------------------------------
-- 5. t_notify_record  终端通知记录
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_notify_record;
CREATE TABLE t_notify_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_no        VARCHAR(40)  NOT NULL,
    notify_url      VARCHAR(255) NOT NULL COMMENT '学校一卡通系统的回调地址',
    request_body    TEXT COMMENT '发出去的报文体',
    response_body   TEXT COMMENT '对方返回',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0待通知 1成功 2失败',
    notify_times    INT          NOT NULL DEFAULT 0,
    next_retry_time DATETIME              DEFAULT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_no (order_no),
    KEY idx_retry (status, next_retry_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='终端通知记录';

-- ---------------------------------------------------------------------------
-- 6. t_channel_bill  渠道账单（对账的「另一边」）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_channel_bill;
CREATE TABLE t_channel_bill (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    bill_date        DATE        NOT NULL,
    channel          VARCHAR(16) NOT NULL COMMENT 'WECHAT/ALIPAY/UNIONPAY',
    channel_order_no VARCHAR(64) NOT NULL,
    amount           BIGINT      NOT NULL,
    trade_status     VARCHAR(16) NOT NULL COMMENT 'SUCCESS/REFUND/CLOSED',
    create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_channel_order (bill_date, channel, channel_order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='渠道对账单';

-- ---------------------------------------------------------------------------
-- 7. t_recon_task  对账任务
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_recon_task;
CREATE TABLE t_recon_task (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    bill_date       DATE        NOT NULL,
    channel         VARCHAR(16) NOT NULL,
    platform_count  INT         NOT NULL DEFAULT 0,
    platform_amount BIGINT      NOT NULL DEFAULT 0,
    channel_count   INT         NOT NULL DEFAULT 0,
    channel_amount  BIGINT      NOT NULL DEFAULT 0,
    diff_count      INT         NOT NULL DEFAULT 0,
    status          TINYINT     NOT NULL DEFAULT 0 COMMENT '0进行中 1已完成 2失败',
    cost_ms         BIGINT      NOT NULL DEFAULT 0,
    operator        VARCHAR(32)          DEFAULT '',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_channel (bill_date, channel)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='对账任务';

-- ---------------------------------------------------------------------------
-- 8. t_recon_diff  对账差异明细
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_recon_diff;
CREATE TABLE t_recon_diff (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    task_id          BIGINT       NOT NULL,
    order_no         VARCHAR(40)           DEFAULT '',
    channel_order_no VARCHAR(64)           DEFAULT '',
    diff_type        VARCHAR(20)  NOT NULL COMMENT 'LOCAL_ONLY/CHANNEL_ONLY/AMOUNT_DIFF/STATUS_DIFF/NOT_POSTED',
    platform_amount  BIGINT       NOT NULL DEFAULT 0,
    channel_amount   BIGINT       NOT NULL DEFAULT 0,
    remark           VARCHAR(255)          DEFAULT '',
    handled          TINYINT      NOT NULL DEFAULT 0 COMMENT '0未处理 1已处理',
    handle_remark    VARCHAR(255)          DEFAULT '',
    handle_time      DATETIME              DEFAULT NULL,
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task (task_id),
    KEY idx_handled (handled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='对账差异明细';

-- ===========================================================================
--  演示数据（和学生端前端 js/mock.js 里那套账号完全一致）
--  密码明文：学生 123456 / 管理员 admin123
--  密码存储：BCrypt（强度 10，60 字符）
--  下面两个学生的明文相同，但哈希不同 —— 因为 BCrypt 每次会生成随机盐并写进哈希串
--  （盐就是 $2b$10$ 后面那 22 个字符），所以彩虹表直接失效
-- ===========================================================================
INSERT INTO t_user (account, password, name, role, college)
VALUES ('2023123456', '$2b$10$ZyIXB9McsYyQd.MaPFO89utNpSO6P14wZMtZ4.qhMmZ7E3.EJQrla', '张明', 'STUDENT', '计算机学院'),
       ('2023123457', '$2b$10$3T2S07JVKVI312J0JIvOJ.Mtoazk0O6unLr4ciAKvUCRs/SRaO9bm', '李思远', 'STUDENT', '外国语学院'),
       ('admin', '$2b$10$PtP7buY5UaGPDW7HWdTLqOgxZvEyo5AOLSxKhOKehqwMSdHmsAfL2', '王工', 'ADMIN', '结算中心');

INSERT INTO t_card_account (card_no, student_no, balance, status)
VALUES ('6217123456781234', '2023123456', 8650, 1),
       ('6217123456781235', '2023123457', 12000, 1);

-- 一笔已完成的充值（含对应账户流水），方便第一天就有数据看
INSERT INTO t_recharge_order
(order_no, request_no, student_no, card_no, amount, pay_method, status,
 channel_order_no, create_time, pay_time, expire_time, bill_date, remark)
VALUES ('R20260929001', 'REQ-SEED-0001', '2023123456', '6217123456781234', 5000, 'WECHAT', 2,
        'CH20260929001', NOW(), NOW(), DATE_ADD(NOW(), INTERVAL 15 MINUTE), CURDATE(), '');

INSERT INTO t_account_flow
(flow_no, card_no, student_no, order_no, flow_type, amount, balance_after, remark)
VALUES ('F20260929001', '6217123456781234', '2023123456', 'R20260929001', 'RECHARGE', 5000, 8650, '一卡通充值 · 微信支付');

SELECT '建库建表完成' AS msg,
       (SELECT COUNT(*) FROM t_user)          AS users,
       (SELECT COUNT(*) FROM t_card_account)  AS accounts,
       (SELECT COUNT(*) FROM t_recharge_order) AS orders,
       (SELECT COUNT(*) FROM t_account_flow)  AS flows;
