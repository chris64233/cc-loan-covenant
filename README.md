# cc-loan-covenant

企业授信与财务信息管理服务：管理企业授信额度、财务契约、财务快照版本与提款审批全生命周期。

当前包含可启动的服务入口、持久化依赖和应用上下文测试。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1
- Spring Data JPA + H2（内存库），金额统一使用 `BigDecimal`（精度 19，4 位小数）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 主要业务规则

### 1. 授信（Credit Facility）

- 授信记录币种、总额度和有效期（含起止日），并配置一个或多个财务契约；
  每个契约包含指标代码、比较方向（`AT_LEAST` / `AT_MOST`）和阈值。
- 授信状态：`ACTIVE`（正常）、`FROZEN`（冻结）、`CLOSED`（关闭）。
  冻结/关闭后存续提款仍可拨付、还款，但不能再批准新提款；冻结可解除。

### 2. 财务快照（只追加版本）

- 企业按报告期提交带指标值的财务快照。
- **同一报告期的新版本只能追加，不能覆盖**：版本号从 1 递增，提交新版本时
  旧版本标记为 `SUPERSEDED` 但原始记录永久保留，可随时查询历史版本。
- 不同报告期的版本号各自独立。
- 契约判断对指定快照版本逐条评估，指标缺失视为不满足；查询同时给出
  该快照是否仍为当前版本（`snapshotCurrent`）和授信在评估日是否有效。

### 3. 提款审批（原子、可冲突）

- 提款申请必须通过 `snapshotId` 绑定一个**明确的快照版本**。
- 批准必须**同时**满足三个条件：
  1. 全部财务契约通过；
  2. 授信在当天处于有效期内且状态为 `ACTIVE`；
  3. 提款金额不超过剩余额度（总额度 − 已用额度）。
- 批准在**同一事务**内一次性完成全部校验、额度占用和台账记录；任何条件不满足
  整体回滚，绝不留下部分提款。批准时写入 JSON 形式的判断依据（契约逐条结果、
  有效期、占用前后额度、绑定快照版本）。
- 业务号（`businessNo`）全局唯一，提交与批准均幂等，重复请求不会重复占用额度。

### 4. 并发与冲突

- 同一授信上的批准、取消、还款通过授信行悲观锁串行化，配合乐观锁版本号，
  **并发批准绝不突破总额度**：额度不足者收到 409 冲突。
- 审批期间若授信被冻结/关闭、绑定快照被更正（出现同报告期更新版本）、
  或额度被其他提款占用，基于旧状态的批准返回 **409 Conflict**
  （错误码分别为 `FACILITY_NOT_ACTIVE`、`SNAPSHOT_CORRECTED`、`LIMIT_EXCEEDED`）。
- 契约不满足、超出有效期属于当前数据下的确定性拒绝，返回 **422**（`DRAWDOWN_REJECTED`）。
- 快照更正与提款批准互斥（同样基于授信行锁）。

### 5. 取消、拨付与还款

- 提款状态：`PENDING → APPROVED → DISBURSED → REPAID`，`APPROVED` 可转为 `CANCELLED`。
- **已批准但尚未拨付**的提款可以取消，取消立即释放已占用额度；取消幂等。
- **已拨付**提款不能取消，只能通过还款减少已用额度；支持部分还款，还清后状态为 `REPAID`，
  单次还款不得超过未还本金。
- 拨付本身不改变已用额度（额度在批准时已占用）。

### 6. 额度台账（不可修改）

- 每次批准（占用）、取消（释放）、还款（减少）都追加一条台账，
  记录变动方向、金额和变动后的已用额度，按记账顺序排列。
- 台账条目字段在数据库层不可更新（`updatable = false`），系统不提供修改/删除入口。

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/facilities` | 创建授信（含契约） |
| GET | `/api/facilities` / `/{id}` | 授信列表/详情（含余额） |
| POST | `/api/facilities/{id}/freeze` / `unfreeze` / `close` | 冻结/解冻/关闭 |
| POST | `/api/facilities/{id}/snapshots` | 提交快照（同报告期追加新版本） |
| GET | `/api/facilities/{id}/snapshots?period=YYYY-MM-DD` | 快照列表（可按报告期过滤） |
| GET | `/api/facilities/{id}/snapshots/{sid}/covenant-check?evaluatedOn=` | 契约判断 |
| POST | `/api/facilities/{id}/drawdowns` | 提交提款（业务号幂等） |
| GET | `/api/facilities/{id}/drawdowns` / `/{businessNo}` | 提款列表/状态 |
| POST | `/api/facilities/{id}/drawdowns/{businessNo}/approve` | 批准 |
| POST | `.../{businessNo}/disburse` | 拨付 |
| POST | `.../{businessNo}/cancel` | 取消（仅已批准未拨付） |
| POST | `.../{businessNo}/repayments` | 还款（仅已拨付） |
| GET | `/api/facilities/{id}/drawdowns/ledger` | 额度台账 |

## 测试

`src/test` 下包含 26 个自动化测试，覆盖：快照版本追加与更正、契约判断、
审批条件拒绝、业务号幂等、并发批准不超额、并发还款不超本金、
冻结/更正/占用冲突、取消释放、部分还款、台账顺序与 HTTP 层行为。
