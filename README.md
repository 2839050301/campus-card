# 校园一卡通充值结算系统（campus-card）

面向校园一卡通的**充值 → 支付回调 → 入账 → 对账**资金链路的后端服务。

> 个人项目，按 10 天迭代推进。
> **当前进度：Day 1–4**（工程骨架 / 登录认证 / 账户与流水查询 / 充值下单与幂等）。
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

## 已完成的功能（Day 1–4）

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
├── config/         MyBatis-Plus 分页、OpenAPI、Web（拦截器注册 + CORS）、BCrypt、Jackson
├── constant/       LimitConstant（单笔 / 单日限额）
├── controller/     PingController / AuthController / AccountController / RechargeController
├── dto/            请求体
├── entity/         与表一一对应
├── interceptor/    LoginInterceptor（JWT 校验 + 身份注入）
├── mapper/         MyBatis-Plus Mapper
├── service/        业务接口与实现
├── util/           JwtUtil / OrderNoGenerator
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
| 5 | 支付回调、异步通知与重试 | ⏳ 计划中 |
| 6 | 入账（CAS 余额更新 + 流水写入） | ⏳ 计划中 |
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

---

## 说明

- 本项目为**个人学习/求职项目**，非商业系统，未接入真实微信/支付宝支付，支付相关逻辑以模拟渠道实现。
- `application*.yml` 中的数据库与 Redis 口令均为**本地开发默认值**，请按需修改。
