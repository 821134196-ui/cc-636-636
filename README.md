# 企业邮箱离职自动回复与转发授权系统

员工离职后：

- **人事（HR）**确认离职时间与自动回复内容，外部联系人在离职时点后收到业务衔接提示；
- **主管**单独申请转发：接替人员、**邮件范围**、**截止日期**，经**独立审批通过**后才启用；
- 接替人员**只收到获批范围内的邮件副本，从不获得离职邮箱的登录权**；
- **到期、审批撤销、接收人调岗**都会**立即停止**转发；
- **私人邮件与敏感主题硬拦截**——即使业务转发规则有效也绝不转发；
- 旧同步任务失败后重试，必须重新核对**当前**授权，**不会重新打开已停用规则**；
- 外部回执按回执号**幂等**，重复回执不改变任何状态；
- 所有配置变更与同步结果都写**审计记录**，可核对每条转发规则的接收人、邮件范围、生效与停用时间。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21、Quarkus 3.17（REST/JTA/Scheduler） |
| ORM | Hibernate ORM + Panache（IDENTITY 主键适配 SQLite） |
| 数据库 | SQLite（`data/offboarding.db`，含规则当前表 + 历史版本表） |
| 前端 | Svelte 5 + Vite 6（构建产物由 Quarkus 静态托管，单端口 8080） |
| 邮箱 | 本地模拟（收件箱 + 发件箱 + 可注入故障的投递） |
| 时间 | 独立**模拟业务时钟**，到期行为可确定性验证 |

## 一条命令启动

前置：JDK 21+、Maven 3.9+、Node 20+。

```bash
./run.sh          # 构建前端+后端并启动 -> http://localhost:8080
./run.sh test     # 同上，但先运行全部集成测试
```

页面标签页：**转发授权 / 自动回复 / 模拟邮箱 / 同步与回执 / 员工·调岗 / 审计记录**。
顶栏可**推进模拟时钟**并**手动执行一轮同步/维护**（生产中由内置调度器每 5 秒执行）。

## 页面操作剧本（对应验收点）

1. **HR 流程**：`员工/调岗` → 对在职员工点“HR 确认离职”；`自动回复` 页填写业务衔接内容并排程，到离职时间自动 `ACTIVE`，可查看历史版本、停用。
2. **主管申请 + 审批**：`转发授权` → “主管发起转发申请”，选择接替人员、邮件范围（全部业务 / 指定发件人 / 主题关键词）、起止时间 → “审批通过”（也可驳回）。
3. **到期停用**：把规则截止时间设为 1 分钟后，顶栏“推进时钟” → “立即执行同步/维护”，规则变为 `STOPPED / EXPIRED`。
4. **提前撤销**：ACTIVE 规则点“提前撤销”，立即 `STOPPED / REVOKED`，终态不可恢复。
5. **接收人调岗**：`员工/调岗` 对当前作为接收人的员工点“登记调岗”，以其为接收人的所有规则立即 `STOPPED / RECIPIENT_REASSIGNED`。
6. **旧任务失败重试**：`同步与回执` → “注入故障 N 次”，去`模拟邮箱`发信制造 FAILED 旧任务，再撤销/等到期后点“立即重试”——任务只变 `SKIPPED_NO_AUTH`，规则不复活。
7. **重复回执**：处置结论行点“发送/重发回执”两次，第二次提示“重复回执，已幂等丢弃”。
8. **私人/敏感拦截**：发信时勾选标记，或主题/正文含“私人、工资、薪资、密码、confidential”等，结论为 `BLOCKED_PRIVATE / BLOCKED_SENSITIVE`，发件箱无转发。

也可直接运行端到端脚本：

```bash
python3 smoke-test.py   # 17 项 HTTP 级断言，覆盖上述全部语义
```

## 自动化验证

```bash
mvn test
```

8 个 `@QuarkusTest` 用例（测试 profile 关闭调度器、手动驱动时钟，保证确定性）：

| 测试类 | 验证内容 |
|---|---|
| `ExpiryTest` | 到截止日期规则 `EXPIRED` 停用；到期后新任务与重试均 `SKIPPED_NO_AUTH`，无转发副本 |
| `RevokeAndStaleRetryTest` | 投递失败产生 FAILED 旧任务 → 撤销后重试不凭旧版本快照恢复授权，反复重试仍不转发 |
| `RecipientReassignTest` | 接收人调岗联动停用其 ACTIVE 与 SCHEDULED 规则，其他接收人不受影响，调岗者不得再被申请 |
| `ReceiptIdempotencyTest` | 重复回执返回 `DUPLICATE_IGNORED`，不新增投递/结论，仅追加审计；未知回执报错 |
| `ContentFilterAndScopeTest` | 私人/敏感硬拦截、发件人与主题范围生效、重复 drain 不重复转发 |
| `AutoReplyFlowTest` | HR 确认 → 按离职时间排程 → 到点生效 → 修改产生新版本 → 停用后不再回复 |

## 数据模型（SQLite）

- `employee`、`sim_clock`
- `auto_reply_rule` + `auto_reply_version`（每次创建/修改/生效/停用留版本）
- `forwarding_rule` + `forwarding_version`（接收人、范围、起止时间、状态、停用原因逐版留痕）
- `mail_message`（收件箱，含 private/sensitive 标记）、`mailbox_outbox`（自动回复与转发副本）
- `sync_task`（邮件×规则，含创建时版本快照、退避重试、回执确认）
- `forward_outcome`（邮件×规则唯一：FORWARDED / BLOCKED_* / SKIPPED_INACTIVE + 回执号）
- `audit_log`（动作、操作人、结构化明细 JSON、时间）

## 关键安全语义的实现位置

- 实时授权复核（不看任务快照）：`service/PolicyService#checkAuthorization`
- 重试时“授权失效只终结任务、不复活规则”：`service/SyncService#processOne`
- 三类停用原因与版本/审计：`service/ForwardingService`（`revoke` / `maintainSchedule` / `stopRulesForReassignedRecipient`）
- 内容硬拦截优先于范围：`service/SyncService`（先 `blockReason` 后 `inScope`）
- 回执幂等：`service/SyncService#acknowledgeReceipt`

## 主要 REST 接口

```
GET  /api/employees | POST /api/employees
POST /api/employees/{id}/offboarding | /reassign
GET  /api/auto-replies | POST /api/auto-replies/employees/{id}/confirm
POST /api/auto-replies/{id}/disable | GET /api/auto-replies/{id}/versions
GET  /api/forwarding-rules
POST /api/forwarding-rules/employees/{id}/request
POST /api/forwarding-rules/{id}/approve | /deny | /revoke
GET  /api/forwarding-rules/{id}/versions
POST /api/mail/employees/{id}/inbound | GET .../inbox | GET /api/mail/outbox
GET  /api/sync/tasks | /outcomes | POST /api/sync/drain | /tasks/{id}/retry
POST /api/sync/receipts/{receiptId}/ack
GET  /api/audit | GET/POST /api/clock
POST /api/admin/reset | /api/admin/fail-next   # 本地模拟辅助
```
