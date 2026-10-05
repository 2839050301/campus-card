# 校园一卡通充值结算系统（campus-card）

面向校园一卡通的**充值 → 支付回调 → 入账 → 对账**资金链路的后端服务。

> 个人项目，按 10 天迭代推进。
> **当前进度：Day 1–6**（工程骨架 / 登录认证 / 账户与流水查询 / 充值下单与幂等 / 支付回调入账 / 异步通知与退避重试）。
> 后续计划见 [进度与路线图](#进度与路线图)。

---

## 技术栈

| 组件 | 版本 | 用途 |
|---|---|---|
| Spring Boot | 3.5.16 | Web 骨架（`jakarta.*` 命名空间） |
| JDK | 17 | 运行环境 |
| MyBatis-Plus | 3.5.17 | ORM + 分页插件（`mybatis-plus-jsqlparser` 单独引入） |
| MySQL | 8.x | 业务数据，8 张表 |
| Redis | 6+ | 缓存 / 幂等键（使用 1 号库，与其它应用隔离） |
| Redisson | 3.52.0 | 分布式锁 |
| JJWT | 0.12.7 | JWT 签发与校验 |
| spring-security-crypto | — | 仅用 `BCryptPasswordEncoder`，不引入完整 Security 过滤器链 |
| springdoc-openapi | 2.9.1 | Swagger UI 接口文档 |
| Lombok | — | 样板代码 |

---

## 已完成的功能（Day 1–6）

### 1. 统一响应体与全局异常

- `Result<T>`：`{ success, errCode, errMsg, data }`。成功时只给 `data`，失败时只给 `errCode` / `errMsg`。
- `ErrCode` 枚举：`UNAUTHORIZED` / `FORBIDDEN` / `PARAM_ERROR` / `BIZ_ERROR` / `SYSTEM_ERROR`。
- `BizException` + `@RestControllerAdvice` 全局兜底：业务异常按错误码返回，未预期异常统一收敛为 `SYSTEM_ERROR`（堆栈只进服务端日志，不出接口）。
- 不存在的路径单独返回 **HTTP 404**，不会被上面的兜底 handler 吞成 200。

### 2. 认证

- 密码用 **BCrypt** 存储（同一明文每次哈希不同，盐写进哈希串）。
- 登录签发 **JWT**（HS384），有效期 720 分钟。
- `LoginInterceptor` 统一校验请求头 `authorization: <token>`（**不带 `Bearer ` 前缀**），校验通过后把身份放进 `UserContext`（ThreadLocal），请求结束清理。
- 白名单放行：`/api/auth/login`、`/api/ping`。
- 未登录返回 **401**，已登录但角色不符返回 **403** —— 两者语义分开，前端只在前者清 token 跳登录页。

### 3. 一卡通账户与流水

- 账户查询：余额、账户状态、单笔/单日限额、今日累计充值额。
- 流水分页查询：支持按 `flowType`（`RECHARGE` / `CONSUME` / `REFUND` / `ADJUST`）过滤。

### 4. 充值下单与幂等

- **单号规则**：`R` + `yyyyMMdd` + 3 位 Redis 自增 + 4 位随机串（字母表剔掉易混的 `I` / `O` / `0` / `1`），例 `R20261001001A7K3`。自增段保证同日不重，随机段保证单号不可预测。
- **支付方式**：`WECHAT` / `ALIPAY` / `UNIONPAY`；下单写 `t_recharge_order`，状态 `0`（待支付），**此时不碰余额**。
- **金额校验**：必须是「元」的整数倍，单笔不超过 1000 元。
- **单日限额软校验**：只统计 `status = 2`（已支付）的订单，而创建动作不改变这个聚合，所以**不加锁**（硬校验放在支付回调，见下）。
- **三层幂等**：
  1. `createOrder` **第一行**按 `requestNo` 查已存在的单，命中直接返回原单；
  2. 并发窗口内撞车由 `t_recharge_order.uk_request_no` 唯一索引兜底，捕获 `DuplicateKeyException` 后回查并返回原单；
  3. 该 Service 刻意**不加 `@Transactional`** —— 理由见「已经落地的设计决定」。

实测（10 线程同一 `requestNo` 打同一接口）：**10/10 返回成功、库里只落 1 行、单号完全一致、0 死锁**。

### 5. 支付回调入账（Day 5）

模拟渠道异步回调 → 幂等短路 → 单日限额硬校验 → 余额入账 + 写流水 + 写通知记录，**整条链路在一个事务里同生共死**。

- **回调入口**：`POST /api/mock/pay?orderNo=...&result=SUCCESS|FAIL`，落 `/api/mock/**` 白名单 —— 渠道服务器不会带我们的 JWT，这条路径上加了鉴权就永远是 401。
- **幂等（第 2 层）**：回调的幂等键是**订单号**（服务端自己生成的），不是 `requestNo`。第一行 `SELECT ... WHERE order_no = ? FOR UPDATE` 先拿记录锁，**拿到锁之后**再判 `status`：已经是 `2`（已支付）就直接返回 `repeat = true`，一分钱不碰。
- **单日限额硬校验**：锁住账户行后重算当日已付（`SUM(amount) WHERE status = 2 AND bill_date = 今天`），超限就把订单置 `4`（已关闭）+ 写 `close_time` + `remark`，然后**用 `return` 表达业务结果，绝不 `throw`** —— `rollbackFor = Exception.class` 会把这条关单 `UPDATE` 一起回滚，最后查库会发现「单子根本没关」。
- **入账**：`UPDATE t_card_account SET balance = balance + ? WHERE card_no = ?` 是**相对更新**，天然没有丢失更新问题；随后写一条 `t_account_flow`（`flowType = RECHARGE`，`balance_after` 是入账后的余额快照）。
- **三层兜底防「钱多加一次」**：① 订单行 `FOR UPDATE` 把同一个 `orderNo` 的回调串行化；② `status = 2` 幂等短路；③ `t_account_flow.uk_order_type (order_no, flow_type)` 唯一索引 —— 哪怕上面两层全写错，第二条同类型流水会被数据库直接顶掉、事务回滚。**代码会出错，数据库约束不会。**
- **通知记录**：每次成功入账写一行 `t_notify_record`（`status = 0` 待投递、`next_retry_time = now()`），Day 6 的定时任务从这张表捞活。
- **加锁顺序固定为「订单行 → 账户行」**：两个并发回调若一个先锁订单、一个先锁账户，就会交叉等待直接死锁。

`repeat` 这个字段不是可有可无的：`status` 回答「**这张单**现在怎么样」（存在库里，描述状态），`repeat` 回答「**你这次调用**干了什么」（不落库，描述事件）。10 个并发回调打同一张单，响应里的 `status` **全是 2**，光看 body 根本分不出哪一次是真的入了账 —— 只有 `repeat` 能标出来。

实测：

| 场景 | 结果 |
|---|---|
| 单次回调 | `{"status":2,"repeat":false,"message":"入账成功"}`；余额 +3000、流水 +1、通知记录 +1、订单 `status` 0 → 2 并写 `pay_time` / `channel_order_no` |
| 重复投递 | `{"status":2,"repeat":true,"message":"回调重复投递，本次未重复入账"}`；余额、流水纹丝不动 |
| **10 条并发重复回调**（同一张已付单） | 10/10 返回成功、10/10 `repeat = true`、message 完全一致、余额一分没多 |
| **10 张不同单同时回调** | 耗时 **184 ms**，10/10 `repeat = false`，余额净增**正好 10000 分**，0 死锁 |
| **超单日限额** | 第 3 笔被拦：`{"success":false,"errCode":"BIZ_ERROR","errMsg":"超出单日累计充值额度，订单已关闭，请联系管理员退款"}`；订单 `status = 4`、`close_time` 有值（**关单没被回滚**）、**没有流水、没有通知记录**，余额停在 200000 分 |
| 关单后再投同一条回调 | `{"success":false,"errCode":"BIZ_ERROR","errMsg":"订单已关闭，回调忽略"}`（终态保护） |

### 6. 异步通知与退避重试（Day 6）

入账成功之后要通知「学校一卡通系统」，但**这个 HTTP 请求绝不能放在入账事务里**。

- **为什么不能同步发**：事务里发 HTTP 最坏要等 3 秒（`RestTemplate` 3s 连接 / 3s 读超时），这 3 秒里订单行 + 账户行的锁一直不放；而且**跨系统没有分布式事务** —— HTTP 成功而事务回滚，对方以为到账、我们库里其实没有；HTTP 失败要回滚入账，用户的钱又该怎么办。唯一安全的顺序是「**先把事实落到自己库的同步事务里，再慢慢通知对方**」（outbox / 本地消息表，思路与 RocketMQ 事务消息一致，只是这个规模不必引入 MQ）。
- **通知记录在事务里，HTTP 请求在事务外**：入账事务内只多写一行 `t_notify_record`（`status = 0` 待投递、`next_retry_time = now()`），commit 之后由定时任务去投。
- **首次投递 = 第 0 次重试**：`next_retry_time` 首次也填 `NOW()`，定时任务只认「`status = 0` 且 `next_retry_time <= now`」。所以「首发」不是特殊分支，只是 `notify_times = 0` 的那一次重试 —— **一套代码零分支**。代价是首次投递最多晚一个扫描周期（5 秒）。
- **退避算法**：`next_retry_time = 本次失败时刻 + backoff[notify_times]`，档位 **30 / 60 / 120 / 240 / 480 秒**（`application.yml` 生产档；`application-dev.yml` 是 3 / 6 / 12 / 24 / 48 秒的加速档，方便验收）。判据是 `notify_times > backoff.length` —— 用 **`>` 而不是 `>=`**，所以总共投 **6 次**（1 次首发 + 5 次重试）才置 `status = 2`（通知失败、需人工介入）。
- **请求地址与请求体都是快照**：`notify_url` / `request_body` 在入账那一刻就写进通知记录，重试重发的**永远是同一个报文** —— 以后改了配置、或者订单对象早已不在内存，都不会影响已经在途的通知。
- **验签**：投递带 `X-Campus-Timestamp` + `X-Campus-Sign = HMAC-SHA256(secret, timestamp + "." + body)`，接收端用同一规则算一遍再做**恒定时间比较**（长度不等直接 `false`、逐字符异或累加，不提前 `return`，避免用响应时间把签名一位一位试出来）。
- **对方返回的成败判定**：`HTTP 200` 只说明传输层通了，业务成败看 body 里的 `code`；传输层异常走 `catch (RestClientException)` 保守当失败。模拟接收端故意用 **HTTP 200 + `{"code":500}`** 表示「对方系统繁忙」，就是为了逼出这条区分。
- **超时关单**：`OrderCloseTask` 每 60 秒扫一次 `status = 0 AND expire_time < now()`，把未支付订单关掉（`status = 4` + `close_time` + `remark`）。**幂等靠 UPDATE 的 WHERE 条件本身**，扫多少遍都不会重复关。
- **多实例下的重复投递**：`@Scheduled` 在**每个 JVM 各有一份**，两个实例会把同一条通知投两次。今天用 Redisson 一把批锁（`lock:notify:deliver`）挡住；更干净的做法是给表加「投递中」状态、用一次 `UPDATE ... WHERE id = ? AND status = 0` 原子抢占，把粒度从「一整批」缩到「一条记录」，锁寿命天然等于事务。

实测（本地 dev 档退避 3/6/12/24/48 秒）：

| 场景 | 结果 |
|---|---|
| 正常投递 | `status = 1`、`notify_times = 1`、`next_retry_time = NULL`、`response_body = {"code":0,"msg":"OK"}` |
| 对方一直失败 | 五档间隔实测 **3 / 6 / 12 / 24 / 48 秒，一档不差**；第 6 次置 `status = 2`、`notify_times = 6` |
| 超时关单 | 过期单 `status 0 → 4` 并写 `close_time`；**未过期的单原样不动**（边界用例） |
| 验签 | 伪造签名 / 长度相同的假签名 / 干脆不带签名头 → 全部 `{"code":401,"msg":"sign 校验失败"}`；用真密钥自算 HMAC → `{"code":0,"msg":"OK"}` |
| 事务边界（后端日志） | `INSERT t_notify_record` 与 `COMMIT` 在 16:53:53.675 结束，HTTP 请求出现在 **16:53:55.821** —— 通知确实在事务提交之后才发出去 |

---

## 接口一览

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/ping` | 健康检查 | 放行 |
| POST | `/api/auth/login` | 登录，返回 token + 用户信息 | 放行 |
| GET | `/api/auth/me` | 当前登录用户 | 需要 |
| GET | `/api/card/account` | 我的账户（余额 / 限额 / 今日充值） | 学生 |
| GET | `/api/card/flow` | 我的流水（分页 + `flowType` 过滤） | 学生 |
| POST | `/api/recharge/order` | 创建充值订单（按 `requestNo` 幂等） | 学生 |
| GET | `/api/recharge/order/page` | 我的充值订单（分页） | 学生 |
| GET | `/api/recharge/order/{orderNo}` | 订单详情（只能查自己的单） | 学生 |
| POST | `/api/mock/pay` | **模拟渠道异步回调**（`orderNo` + `result`） | 放行 |
| POST | `/api/mock/terminal/notify` | **模拟学校一卡通系统**接收通知（验签 + 返回 `code`） | 放行 |
| POST | `/api/mock/terminal/fail?on=true\|false` | 模拟对端故障开关（打开后通知必失败，用来看退避重试） | 放行 |

**登录**

```http
POST /api/auth/login
Content-Type: application/json

{ "account": "2023123456", "password": "123456" }
```

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "user": { "userId": 1, "account": "2023123456", "name": "张明", "role": "STUDENT", "college": "计算机学院" }
  }
}
```

**我的账户**

```http
GET /api/card/account
authorization: eyJhbGciOiJIUzM4NCJ9...
```

```json
{
  "success": true,
  "data": {
    "cardNo": "6217123456781234",
    "studentNo": "2023123456",
    "name": "张明",
    "college": "计算机学院",
    "balance": 8650,
    "status": 1,
    "singleLimit": 100000,
    "dailyLimit": 200000,
    "todayRecharged": 5000
  }
}
```

**我的流水**

```http
GET /api/card/flow?current=1&size=10&flowType=RECHARGE
authorization: eyJhbGciOiJIUzM4NCJ9...
```

```json
{
  "success": true,
  "data": {
    "records": [
      {
        "id": 1,
        "cardNo": "6217123456781234",
        "studentNo": "2023123456",
        "orderNo": "R20260929001",
        "flowType": "RECHARGE",
        "amount": 5000,
        "balanceAfter": 8650,
        "createTime": "2026-09-30 12:50:44",
        "remark": "一卡通充值 · 微信支付"
      }
    ],
    "total": 1, "size": 10, "current": 1, "pages": 1
  }
}
```

> 接口文档启动后可见：<http://127.0.0.1:18082/swagger-ui.html>

**创建充值订单（幂等）**

`requestNo` 由前端生成并在**重试时保持不变** —— 同一个 `requestNo` 无论打多少次，都只会产生一单，且每次都返回**同一张单**。

```http
POST /api/recharge/order
Content-Type: application/json
authorization: eyJhbGciOiJIUzM4NCJ9...

{
  "requestNo": "REQ-7f3c1a92-4b0e-4d18-9c55-2a6e8d013f47",
  "amount": 10000,
  "payMethod": "WECHAT"
}
```

```json
{
  "success": true,
  "data": {
    "orderNo": "R20261001001A7K3",
    "requestNo": "REQ-7f3c1a92-4b0e-4d18-9c55-2a6e8d013f47",
    "studentNo": "2023123456",
    "cardNo": "6217123456781234",
    "amount": 10000,
    "payMethod": "WECHAT",
    "status": 0,
    "expireTime": "2026-10-01 12:15:00",
    "createTime": "2026-10-01 12:00:00"
  }
}
```

> 幂等的边界：**幂等检查排在所有业务校验之前**。所以即使第二次请求带的金额非法（或当日额度已满），只要 `requestNo` 已存在，也照样返回原单 —— 否则「重试」就变成了一个会失败的操作。

**支付回调（模拟渠道）**

参数走 **query 而不是 body**（真实渠道的回调报文形式多样，这里用最简形式模拟）。`result` 不传时默认 `SUCCESS`：

```http
POST /api/mock/pay?orderNo=R20261001001A7K3&result=SUCCESS
```

```json
{
  "success": true,
  "data": {
    "orderNo": "R20261001001A7K3",
    "status": 2,
    "repeat": false,
    "message": "入账成功"
  }
}
```

同一张单再投一次（幂等命中）：

```json
{
  "success": true,
  "data": {
    "orderNo": "R20261001001A7K3",
    "status": 2,
    "repeat": true,
    "message": "回调重复投递，本次未重复入账"
  }
}
```

超单日限额时（订单被关单，HTTP 仍是 200，用业务错误码表达）：

```json
{
  "success": false,
  "errCode": "BIZ_ERROR",
  "errMsg": "超出单日累计充值额度，订单已关闭，请联系管理员退款"
}
```

---

## 数据模型（8 张表）

| 表 | 说明 |
|---|---|
| `t_user` | 用户（学生 / 管理员） |
| `t_card_account` | 一卡通账户（余额、状态、限额） |
| `t_recharge_order` | 充值订单（含 `uk_request_no` 幂等键） |
| `t_account_flow` | 账户流水（`uk_order_type` 兜底防重复入账） |
| `t_notify_record` | 支付回调通知记录（重试次数、下次重试时间） |
| `t_channel_bill` | 渠道对账单（银行/支付渠道侧数据） |
| `t_recon_task` | 对账任务 |
| `t_recon_diff` | 对账差异明细 |

建表与演示数据：`sql/campus_card.sql`

---

## 快速开始

### 1. 环境准备

- JDK 17
- MySQL 8.x
- Redis 6+（需要密码，默认示例为 `1234`）

### 2. 建库

```bash
mysql -u root -p < sql/campus_card.sql
```

脚本会创建 `campus_card` 库、8 张表，并插入演示数据。

### 3. 修改配置

`src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/campus_card?...
    username: root
    password: 1234          # ← 改成你的
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 1
```

`src/main/resources/application-dev.yml` 里是 Redis 密码：

```yaml
spring:
  data:
    redis:
      password: 1234        # ← 改成你的
```

通知相关配置（`application.yml`）：

```yaml
campus:
  pay:
    notify-url: http://127.0.0.1:18082/api/mock/terminal/notify   # 学校一卡通系统的接收地址
    notify-secret: campus-card-notify-secret-2026                 # HMAC 验签密钥，两端必须一致
    retry-backoff-seconds: 30,60,120,240,480                      # 退避档位（秒），dev 档 3,6,12,24,48
```

`retry-backoff-seconds` 是 **5 个档位 → 最多投 6 次**（首发 + 5 次重试），累计跨度 30+60+120+240+480 = **930 秒 ≈ 15.5 分钟**。

### 4. 启动

```bash
mvn spring-boot:run
```

服务监听 **18082**。健康检查：

```bash
curl http://127.0.0.1:18082/api/ping
# {"success":true,"data":{"app":"campus-card","version":"1.0.0","time":"..."}}
```

### 5. 演示账号

| 角色 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 学生 | `2023123456` | `123456` | 张明 · 计算机学院 · 卡号 `6217123456781234` · 余额 8650 分 |
| 学生 | `2023123457` | `123456` | 李思远 · 外国语学院 |
| 管理员 | `admin` | `admin123` | 王工 · 结算中心 |

---

## 项目结构

```
src/main/java/com/campus/card/
├── common/         Result / ErrCode / BizException / 全局异常处理 / UserContext
├── config/         MyBatis-Plus 分页、OpenAPI、Web（拦截器注册 + CORS）、BCrypt、Jackson、RestTemplate
├── constant/       LimitConstant（单笔 / 单日限额）、OrderStatusConstant（订单状态 0–4）、NotifyStatusConstant（通知状态 0–2）
├── controller/     PingController / AuthController / AccountController / RechargeController / MockPayController / TerminalNotifyController（模拟对端）
├── dto/            请求体
├── entity/         与表一一对应
├── interceptor/    LoginInterceptor（JWT 校验 + 身份注入）
├── mapper/         MyBatis-Plus Mapper
├── service/        业务接口与实现
├── task/           NotifyRetryTask（通知投递 + 退避重试）、OrderCloseTask（超时关单）
├── util/           JwtUtil / OrderNoGenerator / SignUtil（HMAC-SHA256 验签）
└── vo/             响应体（不含敏感字段）
```

---

## 进度与路线图

| Day | 内容 | 状态 |
|---|---|---|
| 1 | 工程骨架、统一响应体、全局异常、Swagger | ✅ 已完成 |
| 2 | 登录认证（BCrypt + JWT + 拦截器 + ThreadLocal） | ✅ 已完成 |
| 3 | 账户查询、流水分页查询 | ✅ 已完成 |
| 4 | 充值下单、幂等（`requestNo` 唯一键） | ✅ 已完成 |
| 5 | 支付回调幂等、单日限额硬校验、余额入账 + 流水 | ✅ 已完成 |
| 6 | 异步通知与重试（`t_notify_record` 定时投递、退避重试、HMAC 验签、超时关单，模拟学校一卡通系统） | ✅ 已完成 |
| 7 | 渠道对账单接入 | ⏳ 计划中 |
| 8 | 三方对账引擎（排序归并） | ⏳ 计划中 |
| 9 | 管理端接口（订单管理、差异处理） | ⏳ 计划中 |
| 10 | 压测、文档、部署 | ⏳ 计划中 |

---

## 几个已经落地的设计决定

- **金额一律用「分」+ `BIGINT`**，全链路不出现浮点数（`double` / `float` / `DECIMAL` 都不用），避免精度问题。
- **身份只能来自 token**。`/api/card/account`、`/api/card/flow` 都不接受 `studentNo` / `cardNo` 入参，查询条件由服务端从 `UserContext` 取 —— 从接口签名上就没有水平越权的口子。
- **分页响应固定四件套** `current` / `size` / `total` / `pages` + `records`，前端不用为每个列表单独适配。
- **401 与 403 语义分开**：未登录 → 401（前端清 token 跳登录页）；已登录但角色不符 → 403（只提示，不踢下线）。
- **不引入 `spring-boot-starter-security`**，只用 `spring-security-crypto` 拿 `BCryptPasswordEncoder`；鉴权用「拦截器 + ThreadLocal」手写，避免整条过滤器链带来的隐式行为。
- **幂等检查必须放在方法最前面**。`createOrder` 第一行就是按 `requestNo` 查原单并直接返回，前面不允许出现任何会抛异常的校验 —— 否则重试会先撞上金额校验或限额校验，幂等失效。
- **`createOrder` 不加 `@Transactional`**。该方法只有一条写语句，单条 `INSERT` 本身就是原子的，事务买不到额外保证，幂等的最终兜底是 `uk_request_no` 唯一索引。反过来加事务会坏事：`REPEATABLE READ` 下第一条 `SELECT` 就把快照定死了，`catch` 里回查看不到赢家刚提交的那行（第 3 层幂等失效）；锁也会跨过失败的 `INSERT` 留下共享锁，多个输家同时升级排他锁 → 直接判定死锁。
- **限额校验分软硬两次**。下单时是**软校验**（不锁）：创建动作不改变「已支付金额」这个聚合，给它加锁没有意义。真正会破坏「单日累计 ≤ 2000 元」的是**写偏斜** —— 两个待支付单各自通过校验、各自支付成功；这类「两行加起来超了」的约束唯一索引表达不了。**硬校验放在支付回调**：先锁账户行（`card_no` 唯一索引等值、行必然存在 → 记录锁，能真正排队），再在锁保护下重算当日已付，超限则关单。
- **同样是「幂等」，下单不加 `@Transactional`、回调必须加**。`requestNo` 由**客户端**给（作用域是一次提交），能防「同一个请求重复投递」；`orderNo` 由**服务端**生成（作用域是一笔支付），防的是「同一笔支付重复入账」。下单那条路只有一条 `INSERT`，唯一索引就能兜住，**而且没有余额参与**；回调要同时写 4 张表（订单 / 账户 / 流水 / 通知记录），必须同生共死 —— 唯一索引能兜住「重复下单」，**兜不住「钱加了一半」**。
- **加锁顺序固定为「订单行 → 账户行」**。两个并发回调若一个先锁订单、一个先锁账户，就会交叉等待成死锁。顺序写死是一种全局约定，所以两条 `FOR UPDATE` 并排写在 `RechargeOrderMapper` / `CardAccountMapper` 的方法名和注释里，事后一眼可查。
- **`@Transactional` 的 COMMIT 发生在 Service 方法返回那一刻，不是 Controller 返回那一刻**。`TransactionInterceptor` 挂在 Service 调用栈上，`@RestControllerAdvice` 挂在 DispatcherServlet 层、比它晚一整层 —— 所以「日志里打了异常 + 响应里带了错误信息」和「库里数据没变」可以同时成立，回滚早就做完了，兜底只是把异常翻译成响应。
- **业务结果用返回值、技术异常用抛异常**。判据只有一句话：*抛出去的那一刻，这个事务里有没有「你想留下来的写操作」*。单日限额超了是**业务结果**（关单本身就是一个要保留的写操作）→ 必须 `return`；锁不到账户、余额更新影响 0 行才是**技术异常** → 必须 `throw` 让事务回滚。
- **不信任渠道传来的金额**。回调只从渠道报文里取「付没付成功」和「渠道单号」，**金额一律以自己库里那张订单的 `amount` 为准**。（真实系统还差一步：**验签** —— 微信回调带 `sign`，要用 API 密钥按同规则算一遍比对，否则任何人 `curl` 一下就能白拿钱。）
- **通知「在事务里登记、不在事务里发送」**。这是 Day 6 存在的全部理由：入账事务只多写一行 `t_notify_record`，HTTP 请求留给事务外的定时任务。事务里发 HTTP 会同时踩三个坑 —— ① 网络耗时把订单行 + 账户行的锁一直攥在手里；② 跨系统没有分布式事务，HTTP 与 COMMIT 只能保证一个，`HTTP 成功 + 事务回滚` 就是「对方以为到账、我们库里没有」；③ 对方宕机会直接拖垮我们的充值接口。思路与 RocketMQ 事务消息一致（本地消息表 / outbox），只是这个规模不必引入 MQ。
- **首次投递 = 第 0 次重试**。`next_retry_time` 首次也填 `NOW()`，扫描条件只有一句「`status = 0` 且 `next_retry_time <= now`」。反面写法是「先同步发一次、失败再进重试队列」—— 报文拼装、签名、成功判定、日志要写两遍，两遍必然走偏，还凭空多出「首发失败」这个第三态。现在「首发」只是 `notify_times = 0` 的那一次重试，**一段代码零分支**。代价是首次投递最多晚一个扫描周期（5 秒），可以接受。
- **MyBatis-Plus 的 `updateById` 写不进 `NULL`**。默认字段策略是 `NOT_NULL`，实体字段为 `null` 时该字段**不会出现在 `SET` 子句里**。投递成功后必须把 `next_retry_time` 清空，用 `updateById` 只会让旧时间永远留在库里 —— 定时任务会把这条**已经成功**的记录反复捞出来重投。正确写法是 `LambdaUpdateWrapper.set(NotifyRecord::getNextRetryTime, null)`。实测日志里那句 `Parameters: 1, {"code":0,"msg":"OK"}, 1, null, 29(Long)` 就是 `null` 真的进了 `SET` 的证据。
- **Redisson 显式传 `leaseTime`，等于主动关掉看门狗**。看门狗只在**不传** `leaseTime` 时才启动（默认 30 秒租期、每 10 秒续一次，续期由独立的后台 Timer 线程做，跟任务线程卡不卡无关）。这里故意写死 30 秒，是为了把锁的寿命变成一个**能算的数**，反过来约束批大小 —— `BATCH × 单条超时 < 30 秒`。开着看门狗的话，锁活多久取决于 JVM 活多久，没有任何上界：一旦某个实例卡在不响应的 HTTP 上，锁被无限续期，其余实例全都抢不到，**整个集群的通知集体停摆**。权衡的结论是：**宁可重复投一次（业务有 `status` + 唯一索引兜着），不可全线卡死。**
- **`AtomicBoolean` 挡不住多实例**。`NotifyRetryTask` 里的 `running` 标志只防「上一轮还没跑完、这一轮又叠进来」，它在**单个 JVM 内**有效；两个实例各有各的 `running`，互相完全不可见。多实例的重复投递要么靠分布式锁，要么靠数据库原子抢占 —— **锁的粒度越细越并行，代价是必须处理「抢到了却没回写」**（细粒度方案要加 `claim_time` 列和超时回收，粗粒度方案不需要）。
- **接口返回假值，比不返回更危险**。`notifyStatus` / `notifyTimes` / `nextRetryTime` 曾经被硬编码成 `0 / 0 / null`，而前端三个页面其实一直在读真值 —— 结果是「通知终端失败」的黄色告警条和「重发通知」按钮**永远不显示**。前端不会报错，它只是永远走不到那个分支，所以这种 bug 只能靠端到端验收发现。修法是查询路径用 `LEFT JOIN t_notify_record` 取真值（`toVO` 只服务新建单那条路径，此刻确实还没有通知记录，那里填 0 是对的）。同时用 `n.status` 而不是 `IFNULL(n.status, 0)`：**「没有通知这件事」和「有一条等着投递的通知」是两回事**，前端把 `NULL` 渲染成 `-` 才是正确的表达。
- **验签用恒定时间比较**。`SignUtil.verify` 先判 null、再判长度，最后逐字符异或累加而不是提前 `return` —— 普通 `equals` 会在第一个不同的字符处立刻返回，攻击者靠响应时间就能一位一位把签名试出来。

---

## 说明

- 本项目为**个人学习/求职项目**，非商业系统，未接入真实微信/支付宝支付，支付相关逻辑以模拟渠道实现。
- `application*.yml` 中的数据库与 Redis 口令均为**本地开发默认值**，请按需修改。
