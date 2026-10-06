#!/usr/bin/env python3
"""端到端冒烟：直接驱动 HTTP API，校验核心安全语义。"""
import json, urllib.request

BASE = "http://localhost:8080"

def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data,
                                 headers={"Content-Type": "application/json"}, method=method)
    with urllib.request.urlopen(req) as r:
        return json.load(r)

def clock_min(m):
    return call("POST", "/api/clock", {"advanceMinutes": m})

fails = []
def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ("  " + detail if detail and not cond else ""))
    if not cond:
        fails.append(name)

emps = call("GET", "/api/employees")
zhang = next(e for e in emps if "zhangwei" in e["email"])
lina = next(e for e in emps if "lina" in e["email"])
zhao = next(e for e in emps if "zhaolei" in e["email"])
now = call("GET", "/api/clock")["now"]

# 1) 主管为李娜申请全业务转发，审批通过
rule = call("POST", f"/api/forwarding-rules/employees/{zhang['id']}/request", {
    "recipientId": lina["id"], "scopeType": "ALL_BUSINESS",
    "validFrom": now, "validUntil": now + 24*3600_000, "actor": "王强(主管)"})
check("转发申请待审批", rule["status"] == "PENDING_APPROVAL", rule["status"])
rule = call("POST", f"/api/forwarding-rules/{rule['id']}/approve", {"actor": "审批人"})
check("审批后生效", rule["status"] == "ACTIVE", rule["status"])
rid = rule["id"]

# 2) 新来两封邮件：普通业务 + 敏感；drain 后普通转发、敏感拦截
m1 = call("POST", f"/api/mail/employees/{zhang['id']}/inbound", {
    "fromEmail": "buyer@partner.example", "subject": "合同盖章事宜", "body": "请回传盖章版"})
m2 = call("POST", f"/api/mail/employees/{zhang['id']}/inbound", {
    "fromEmail": "friend@private.example", "subject": "周末聚餐", "body": "私人的事"})
call("POST", "/api/sync/drain")
outcomes = call("GET", "/api/sync/outcomes")
d1 = next(o for o in outcomes if o["mailId"] == m1["id"])
d2 = next(o for o in outcomes if o["mailId"] == m2["id"])
check("业务邮件转发", d1["decision"] == "FORWARDED", str(d1))
check("私人/敏感邮件拦截", d2["decision"] in ("BLOCKED_PRIVATE", "BLOCKED_SENSITIVE"), str(d2))
outbox = call("GET", "/api/mail/outbox")
check("发件箱无敏感转发", not any(o["sourceMailId"] == m2["id"] and o["type"] == "FORWARD" for o in outbox))

# 3) 故障注入 + 新邮件 → 同步任务持续失败（注入足够次数，覆盖调度器自动重试）
call("POST", "/api/admin/fail-next", {"count": 20})
m3 = call("POST", f"/api/mail/employees/{zhang['id']}/inbound", {
    "fromEmail": "buyer@partner.example", "subject": "发货地址确认", "body": "地址如下"})
call("POST", "/api/sync/drain")
tasks = call("GET", "/api/sync/tasks?status=FAILED")
t3 = next(t for t in tasks if t["mailId"] == m3["id"])
check("失败任务待重试", t3["status"] == "FAILED" and t3["attempts"] == 1, str(t3))
call("POST", f"/api/forwarding-rules/{rid}/revoke", {"actor": "管理员", "reason": "提前撤销测试"})
rep = call("POST", f"/api/sync/tasks/{t3['id']}/retry")
check("旧任务重试不恢复授权", rep["report"] == "SKIPPED_NO_AUTH", str(rep))
rule = call("GET", "/api/forwarding-rules")
r_after = next(r for r in rule if r["id"] == rid)
check("撤销后规则保持 STOPPED", r_after["status"] == "STOPPED" and r_after["stopReason"] == "REVOKED")

# 4) 第二条规则用于到期验证
now = call("GET", "/api/clock")["now"]
r2 = call("POST", f"/api/forwarding-rules/employees/{zhang['id']}/request", {
    "recipientId": lina["id"], "scopeType": "ALL_BUSINESS",
    "validFrom": now, "validUntil": now + 60_000, "actor": "王强(主管)"})
r2 = call("POST", f"/api/forwarding-rules/{r2['id']}/approve", {"actor": "审批人"})
check("规则2生效", r2["status"] == "ACTIVE")
m4 = call("POST", f"/api/mail/employees/{zhang['id']}/inbound", {
    "fromEmail": "buyer2@partner.example", "subject": "发票信息", "body": "开发票资料"})
clock_min(2)  # 超过 1 分钟有效期
call("POST", "/api/sync/drain")
r2now = next(r for r in call("GET", "/api/forwarding-rules") if r["id"] == r2["id"])
check("到期自动停用", r2now["status"] == "STOPPED" and r2now["stopReason"] == "EXPIRED")
t4 = next(t for t in call("GET", "/api/sync/tasks") if t["mailId"] == m4["id"])
check("到期后旧任务跳过", t4["status"] == "SKIPPED_NO_AUTH", str(t4))

# 5) 第三条规则验证接收人调岗立即停用
now = call("GET", "/api/clock")["now"]
r3 = call("POST", f"/api/forwarding-rules/employees/{zhang['id']}/request", {
    "recipientId": zhao["id"], "scopeType": "ALL_BUSINESS",
    "validFrom": now, "validUntil": now + 3600_000, "actor": "王强(主管)"})
r3 = call("POST", f"/api/forwarding-rules/{r3['id']}/approve", {"actor": "审批人"})
check("规则3生效", r3["status"] == "ACTIVE")
call("POST", f"/api/employees/{zhao['id']}/reassign", {"actor": "人事部"})
r3now = next(r for r in call("GET", "/api/forwarding-rules") if r["id"] == r3["id"])
check("接收人调岗立即停用", r3now["status"] == "STOPPED"
      and r3now["stopReason"] == "RECIPIENT_REASSIGNED", str(r3now))

# 6) 重复回执幂等
receipt = d1["receiptId"]
a1 = call("POST", f"/api/sync/receipts/{receipt}/ack", {"remoteService": "GW"})
a2 = call("POST", f"/api/sync/receipts/{receipt}/ack", {"remoteService": "GW"})
check("首次回执确认", a1["first"] is True and a1["result"] == "ACKNOWLEDGED")
check("重复回执丢弃", a2["first"] is False and a2["result"] == "DUPLICATE_IGNORED")

# 7) 历史版本存在
vers = call("GET", f"/api/forwarding-rules/{rid}/versions")
check("转发历史版本齐全", {v["changeType"] for v in vers} >= {"REQUESTED", "APPROVED", "ACTIVATED", "STOPPED"})
rv = call("GET", "/api/auto-replies")
check("自动回复列表含生效状态", rv[0]["status"] == "ACTIVE")

print("\n结果:", "全部通过 ✅" if not fails else f"{len(fails)} 项失败: {fails}")
exit(1 if fails else 0)
