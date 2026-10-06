export function fmtTime(epoch) {
  if (epoch === null || epoch === undefined) return '—';
  const d = new Date(epoch);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
    `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export function scopeLabel(r) {
  if (r.scopeType === 'ALL_BUSINESS') return '全部业务邮件（强制过滤私人/敏感）';
  if (r.scopeType === 'SENDER_ALLOWLIST') return `仅发件人：${r.scopeCriteria}`;
  if (r.scopeType === 'SUBJECT_KEYWORDS') return `主题含：${r.scopeCriteria}`;
  return r.scopeType;
}

export const STOP_REASON_TEXT = {
  APPROVAL_DENIED: '审批驳回',
  REVOKED: '提前撤销',
  EXPIRED: '授权到期',
  RECIPIENT_REASSIGNED: '接收人调岗'
};

export const CHANGE_TEXT = {
  CREATED: '创建',
  UPDATED: '内容更新',
  ACTIVATED: '到点生效',
  DISABLED: '停用',
  REQUESTED: '主管申请',
  APPROVED: '审批通过',
  DENIED: '审批驳回',
  STOPPED: '停用'
};
