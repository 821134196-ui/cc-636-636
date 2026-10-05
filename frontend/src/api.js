const BASE = '/api'

async function req(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `${res.status} ${res.statusText}`)
  }
  const ct = res.headers.get('content-type') || ''
  return ct.includes('application/json') ? res.json() : res.text()
}

export const api = {
  // 员工
  employees: () => req('GET', '/employees'),
  createEmployee: (data) => req('POST', '/employees', data),
  setStatus: (id, status, actor) => req('POST', `/employees/${id}/status`, { status, actor }),

  // 自动回复
  autoReplies: () => req('GET', '/auto-replies'),
  autoReplyHistory: (employeeId) => req('GET', `/auto-replies/history/${employeeId}`),
  upsertAutoReply: (data) => req('POST', '/auto-replies', data),
  disableAutoReply: (id, actor) => req('POST', `/auto-replies/${id}/disable`, { actor }),

  // 转发规则
  forwardRules: () => req('GET', '/forward-rules'),
  forwardRuleHistory: (employeeId) => req('GET', `/forward-rules/history/${employeeId}`),
  requestRule: (data) => req('POST', '/forward-rules', data),
  approveRule: (id, actor) => req('POST', `/forward-rules/${id}/approve`, { actor }),
  revokeRule: (id, actor) => req('POST', `/forward-rules/${id}/revoke`, { actor }),
  disableRule: (id, actor, reason) => req('POST', `/forward-rules/${id}/disable`, { actor, reason }),

  // 邮箱模拟
  inbox: (address) => req('GET', '/mailbox/inbox' + (address ? `?address=${encodeURIComponent(address)}` : '')),
  outbox: () => req('GET', '/mailbox/outbox'),
  receiveMail: (data) => req('POST', '/mailbox/receive', data),

  // 同步
  runSync: () => req('POST', '/sync/run'),
  syncTasks: () => req('GET', '/sync/tasks'),
  retryTask: (id) => req('POST', `/sync/tasks/${id}/retry`),
  forwardReceipts: () => req('GET', '/sync/receipts/forward'),
  autoReplyReceipts: () => req('GET', '/sync/receipts/auto-reply'),

  // 审计
  audit: (category) => req('GET', '/audit' + (category ? `?category=${category}` : ''))
}

export function fmtTime(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** datetime-local 值 → ISO 字符串(UTC) */
export function toIso(local) {
  if (!local) return null
  return new Date(local).toISOString()
}
