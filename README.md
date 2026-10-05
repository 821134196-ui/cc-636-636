# 企业邮箱离职自动回复与转发授权系统

员工离职后,外部联系人自动收到业务衔接提示;接替人接收邮件必须由主管单独申请、审批后生效,
**不交接邮箱登录权**。私人邮件与敏感主题永不转发。接收人调岗、审批撤销、授权到期都会立即停止转发,
旧同步任务重试与重复回执不会恢复失效授权。

## 技术栈

- **后端**: Java 17 + Quarkus 3(REST/Hibernate ORM/Scheduler)
- **数据库**: SQLite(本地文件 `exitmail.db`,规则与历史版本全部落库)
- **前端**: Svelte + Vite(构建后由 Quarkus 作为静态资源托管)
- **邮箱**: 本地模拟(收件箱/发件箱均存库,无外发)

## 一条命令启动

```bash
./start.sh
```

脚本依次完成:前端构建 → 静态资源拷贝 → 后端打包 → 启动,访问 <http://localhost:8080>。
(脚本优先使用系统 `java`/`mvn`,否则回退到 `/home/node/tools` 下的用户级安装。)

## 运行验证测试

```bash
export JAVA_HOME=/home/node/tools/jdk-17.0.20.1+1
export PATH=$JAVA_HOME/bin:/home/node/tools/apache-maven-3.9.9/bin:$PATH
mvn test
```

覆盖场景:到期停用、提前撤销、接收人调岗停用、旧任务重试不恢复失效授权、
重复回执不重复转发、敏感/私人邮件拦截、待审批不转发、改版需重新审批、关键词范围、
自动回复去重与窗口控制、REST 全流程冒烟。

## 核心设计

### 数据模型(全部版本化)

| 表 | 说明 |
| --- | --- |
| `employee` | 员工,状态:在职/已离职/已调岗 |
| `auto_reply_config` | 自动回复配置,每次修改生成新版本,旧版本立即停用 |
| `forward_rule` | 转发授权:接收人、范围、生效/截止日期,状态机 待审批→生效→(撤销/到期/停用) |
| `mail_message` | 本地模拟邮件(入站/出站) |
| `sync_task` | 同步任务,失败可重试 |
| `forward_receipt` / `auto_reply_receipt` | 回执,唯一去重键防止重复处理 |
| `audit_log` | 配置变更与同步结果全量审计 |

### 关键不变量

1. **授权唯一入口**:`AuthorizationService.checkForwardingAuthorized()` 实时核对
   规则状态、生效窗口、截止日期、接收人在职状态。同步执行、任务重试、全量扫描
   都必须经过它,不缓存结论。
2. **终态不可逆**:规则撤销/到期/停用后没有任何路径回到生效;变更只能产生新版本
   并重新审批。旧任务重试时核对到授权失效 → 任务取消,绝不重新启用规则。
3. **敏感过滤独立于授权**:私人邮件标记、敏感主题关键词(薪资/私人/confidential 等)
   命中即拦截,即使授权有效也不放行,拦截同样留回执与审计。
4. **幂等去重**:转发回执 `规则id:邮件id` 唯一,自动回复回执 `员工id:邮件id` 唯一,
   重复同步/重复回执不会重复发送。
5. **联动停用**:员工调岗 → 其作为接收人的所有生效/待审批规则立即停用并取消未完成任务;
   调度器每 15 秒检查授权到期与自动回复到期。

### 主要 API

```
GET/POST /api/employees            POST /api/employees/{id}/status
GET/POST /api/auto-replies         GET  /api/auto-replies/history/{employeeId}
                                   POST /api/auto-replies/{id}/disable
GET/POST /api/forward-rules        GET  /api/forward-rules/history/{employeeId}
                                   POST /api/forward-rules/{id}/approve|revoke|disable
POST     /api/mailbox/receive      GET  /api/mailbox/inbox|outbox
POST     /api/sync/run             GET  /api/sync/tasks   POST /api/sync/tasks/{id}/retry
GET      /api/audit?category=
```

## 页面说明

- **员工管理**:标记离职/调岗(调岗联动停用转发授权)
- **自动回复**:人事维护内容与生效窗口,每员工最新版本 + 完整历史版本
- **转发授权**:主管申请(接收人/范围/截止日期)→ 审批 → 生效;撤销/停用/历史版本
- **邮箱模拟**:模拟外部来信(可标记私人、注入 `[flaky]` 故障)、同步任务重试、收发件箱
- **审计日志**:配置变更、审批、同步结果、员工状态变更
