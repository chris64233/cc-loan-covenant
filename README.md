# cc-loan-covenant

企业授信与财务信息管理服务：在财务契约（financial covenant）约束下完成授信管理、财务快照版本管理与提款审批。

- JDK 21
- Spring Boot 4.1.1 / Spring Data JPA / H2
- 金额一律使用 `BigDecimal`（额度精度 19,4，指标精度 19,6）

## 主要业务规则

### 1. 授信（Credit Facility）

- 授信记录币种、总额度、有效期起止日，并可配置多个财务契约。
- 契约由「指标键 + 比较方向（`GTE`/`LTE`）+ 阈值」组成，例如流动比率 ≥ 1.50、
  有息负债/EBITDA ≤ 3.00。
- 授信支持「冻结 / 解冻」，用于审批期间锁定；冻结期间的批准一律返回 409 冲突。

### 2. 财务快照（Financial Snapshot）

- 快照带报告期和一组指标值。
- **同一报告期的新版本只能追加，不能覆盖**：新版本号单调递增（v1、v2…），
  旧版本状态由 `CURRENT` 转为 `SUPERSEDED`，但实体与指标值永久保留，
  可作为历史判断依据查询。
- 提款必须绑定一个**明确的快照版本**（按快照 ID），不绑定“最新版”这种浮动引用。

### 3. 提款审批（Drawdown）

提款状态机：

```
PENDING ──批准──▶ APPROVED ──拨付──▶ DISBURSED ──还清──▶ SETTLED
   │                 │
   │拒绝             └──取消──▶ CANCELLED
   ▼
REJECTED（终态）
```

- 批准必须**同时**满足：
  1. 授信未冻结；
  2. 批准日在授信有效期内；
  3. 绑定的快照版本仍为该报告期最新（未被更正）；
  4. 全部契约逐项满足（指标缺失按违约处理）；
  5. 提款金额 ≤ 剩余额度。
- **原子性**：全部条件通过后才一次性增加已用额度、置 `APPROVED`、
  写入判断依据（含逐项契约结果、快照版本、批准前已用/可用额度）并追加台账流水；
  任何条件不满足都不会留下部分占用或半截台账。
- 两类非成功结果语义不同：
  - **409 冲突**（`FACILITY_FROZEN` / `SNAPSHOT_SUPERSEDED` / `LIMIT_EXHAUSTED`）：
    属于并发状态竞争，申请仍为 `PENDING`，调用方可基于最新状态重试。
    其中申请金额本身超过总额度属于业务拒绝（422）；仅“可用额度被其他提款占满”才是冲突。
  - **422 拒绝**（契约违约 / 过期 / 超总额度）：申请终态 `REJECTED`，不占用额度、不写台账。

### 4. 幂等与并发

- 提款业务号（`businessNo`）全局唯一，是提交与批准的幂等键：
  重复提交返回原申请（HTTP 200，新建为 201），重复批准/取消/拨付返回当前状态。
- 所有额度变动事务先对**授信行加悲观写锁**（`SELECT … FOR UPDATE`），再锁提款行、
  快照行（统一“授信行 → 提款行 → 快照行”加锁顺序），同一授信的批准、取消、还款、
  快照更正在数据库层严格串行，**并发批准（含并发批准同一笔提款）不可能重复或超额占用额度**；
  提款业务号唯一约束对幂等做最终兜底。
- 审批期间授信被冻结、绑定快照被更正、额度被其他提款占用时，
  等到锁的批准会在锁内读到最新状态并返回冲突。

### 5. 取消 / 拨付 / 还款

- `APPROVED`（已批准未拨付）可取消：一次性释放额度并写台账；`CANCELLED` 为终态。
- `APPROVED` 拨付后为 `DISBURSED`：拨付本身不产生额度流水（批准时已占用）。
- 已拨付提款**不能取消**，只能通过还款逐笔减少已用额度；
  还清时转 `SETTLED`，不允许超额还款。

### 6. 额度台账（Limit Ledger）

- 每次批准（+占用）、取消（-释放）、还款（-减少）都追加一条流水，
  记录变动类型、金额（恒正）、变动后已用额度与发生时间。
- 台账**只追加、不可修改、不可删除**，完整解释已用额度构成。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/facilities` | 建立授信（含契约列表） |
| GET  | `/api/facilities/{id}` | 授信余额：总额度/已用/可用、有效期判定、冻结标记、契约配置 |
| POST | `/api/facilities/{id}/freeze` `/unfreeze` | 冻结 / 解冻 |
| POST | `/api/facilities/{id}/snapshots` | 提交财务快照（同报告期自动追加新版本） |
| GET  | `/api/facilities/{id}/snapshots?reportingPeriod=` | 快照版本列表（可按报告期过滤） |
| GET  | `/api/snapshots/{snapshotId}/covenant-check` | 契约逐项判断 |
| POST | `/api/facilities/{id}/drawdowns` | 提交提款（body 含 `businessNo`、`snapshotId`、`amount`） |
| POST | `/api/drawdowns/{businessNo}/approve` | 批准 |
| POST | `/api/drawdowns/{businessNo}/cancel` | 取消（释放额度） |
| POST | `/api/drawdowns/{businessNo}/disburse` | 拨付 |
| POST | `/api/drawdowns/{businessNo}/repay` | 还款 |
| GET  | `/api/drawdowns/{businessNo}` | 提款状态 |
| GET  | `/api/facilities/{id}/drawdowns` | 授信下提款列表 |
| GET  | `/api/facilities/{id}/ledger` | 不可变额度台账 |

### 请求示例

建立授信：

```json
POST /api/facilities
{
  "customerName": "示例企业",
  "currency": "CNY",
  "totalLimit": 1000.0000,
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "covenants": [
    {"metricKey": "CURRENT_RATIO", "displayName": "流动比率", "operator": "GTE", "threshold": 1.50}
  ]
}
```

提交快照与提款：

```json
POST /api/facilities/1/snapshots
{"reportingPeriod": "2026-03-31", "metrics": {"CURRENT_RATIO": 1.80}}

POST /api/facilities/1/drawdowns
{"businessNo": "DD-2026-0001", "snapshotId": 1, "amount": 400.0000}
```

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 测试

- `CovenantEvaluatorTest`：GTE/LTE 数值比较、等于阈值满足、指标缺失违约。
- `DrawdownLifecycleIntegrationTest`：快照追加不覆盖、批准/取消/拨付/还款全生命周期、
  契约违约与过期拒绝且不占额度、冻结与快照更正 409、业务号幂等、台账不可变。
- `DrawdownConcurrencyIntegrationTest`：10 线程并发批准不超额、8 线程并发批准同一笔只占用一次、
  8 线程同业务号提交只建一单、批准与快照更正并发的线性化结果。
- `DrawdownApiIntegrationTest`：HTTP 全链路及 409/422/404/400 状态码映射。
