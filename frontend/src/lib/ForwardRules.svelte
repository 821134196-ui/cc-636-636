<script>
  import { onMount } from 'svelte'
  import { api, fmtTime, toIso } from '../api.js'
  import { run } from './helpers.js'

  let rules = []
  let employees = []
  let historyFor = null
  let historyRows = []
  let form = {
    employeeEmail: '', recipientEmail: '', scope: 'ALL_BUSINESS', scopeKeywords: '',
    startTime: '', endDate: '', requestedBy: '主管-钱进'
  }

  const statusLabel = {
    PENDING_APPROVAL: '待审批', ACTIVE: '生效中', REVOKED: '已撤销',
    EXPIRED: '已到期', DISABLED: '已停用'
  }
  const statusClass = {
    PENDING_APPROVAL: 'orange', ACTIVE: 'green', REVOKED: 'red', EXPIRED: 'gray', DISABLED: 'gray'
  }
  const reasonLabel = {
    REVOKED: '审批撤销', EXPIRED: '授权到期', RECIPIENT_TRANSFERRED: '接收人调岗',
    SUPERSEDED: '被新申请取代', MANUAL: '手动停用'
  }

  async function load() {
    ;[rules, employees] = await Promise.all([api.forwardRules(), api.employees()])
  }

  async function submit() {
    await run(api.requestRule({
      employeeEmail: form.employeeEmail,
      recipientEmail: form.recipientEmail,
      scope: form.scope,
      scopeKeywords: form.scope === 'SUBJECT_KEYWORDS' ? form.scopeKeywords : null,
      startTime: toIso(form.startTime),
      endDate: toIso(form.endDate),
      requestedBy: form.requestedBy
    }), '转发授权申请已提交,待审批(旧版本已同时停用)')
    await load()
  }

  async function act(rule, fn, msg) {
    await run(fn, msg)
    await load()
  }

  async function showHistory(rule) {
    historyFor = rule.employee
    historyRows = await api.forwardRuleHistory(rule.employee.id)
  }

  function scopeText(r) {
    return r.scope === 'SUBJECT_KEYWORDS' ? `主题含: ${r.scopeKeywords}` : '全部业务邮件'
  }

  onMount(load)
</script>

<div class="card">
  <h2>主管申请转发授权(接收人 / 邮件范围 / 截止日期,需审批后生效)</h2>
  <div class="form-row">
    <label class="fld">离职员工邮箱
      <select bind:value={form.employeeEmail}>
        <option value="">请选择</option>
        {#each employees.filter((e) => e.status === 'DEPARTED') as e}
          <option value={e.email}>{e.name} &lt;{e.email}&gt;</option>
        {/each}
      </select>
    </label>
    <label class="fld">接收人邮箱
      <input list="staff" bind:value={form.recipientEmail} placeholder="接替人邮箱" />
      <datalist id="staff">
        {#each employees.filter((e) => e.status === 'ACTIVE') as e}
          <option value={e.email}>{e.name}</option>
        {/each}
      </datalist>
    </label>
    <label class="fld">邮件范围
      <select bind:value={form.scope}>
        <option value="ALL_BUSINESS">全部业务邮件</option>
        <option value="SUBJECT_KEYWORDS">按主题关键词</option>
      </select>
    </label>
    {#if form.scope === 'SUBJECT_KEYWORDS'}
      <label class="fld">关键词(逗号分隔)<input bind:value={form.scopeKeywords} placeholder="合同,报价" /></label>
    {/if}
    <label class="fld">生效时间<input type="datetime-local" bind:value={form.startTime} /></label>
    <label class="fld">截止日期<input type="datetime-local" bind:value={form.endDate} /></label>
    <label class="fld">申请人<input bind:value={form.requestedBy} /></label>
    <button class="primary" on:click={submit}
      disabled={!form.employeeEmail || !form.recipientEmail || !form.startTime || !form.endDate}>
      提交申请
    </button>
  </div>
  <p class="muted">注意:私人邮件与敏感主题即使授权有效也不会被转发;修改任何字段都会生成新版本并需重新审批。</p>
</div>

<div class="card">
  <h2>转发授权规则(每邮箱最新版本)</h2>
  <table>
    <thead>
      <tr><th>离职邮箱</th><th>版本</th><th>状态</th><th>接收人</th><th>范围</th><th>生效</th><th>截止</th><th>申请/审批</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each rules as r}
        <tr>
          <td>{r.employee.name}<br /><span class="muted">{r.employee.email}</span></td>
          <td>v{r.version}</td>
          <td><span class="badge {statusClass[r.status]}">{statusLabel[r.status]}</span></td>
          <td>{r.recipientEmail}</td>
          <td>{scopeText(r)}</td>
          <td class="muted">{fmtTime(r.startTime)}</td>
          <td class="muted">{fmtTime(r.endDate)}</td>
          <td class="muted">
            {r.requestedBy} {fmtTime(r.requestedAt)}<br />
            {#if r.approvedBy}✓ {r.approvedBy} {fmtTime(r.approvedAt)}{/if}
            {#if r.closeReason}<br />{reasonLabel[r.closeReason] || r.closeReason} {fmtTime(r.closedAt)}{/if}
          </td>
          <td>
            {#if r.status === 'PENDING_APPROVAL'}
              <button class="act" on:click={() => act(r, api.approveRule(r.id, '审批人'), '已批准生效')}>批准</button>
              <button class="act danger" on:click={() => act(r, api.revokeRule(r.id, '审批人'), '已撤销')}>驳回/撤销</button>
            {/if}
            {#if r.status === 'ACTIVE'}
              <button class="act danger" on:click={() => act(r, api.revokeRule(r.id, '审批人'), '授权已撤销,立即停止转发')}>撤销授权</button>
              <button class="act danger" on:click={() => act(r, api.disableRule(r.id, '管理员', '手动停用'), '已停用')}>停用</button>
            {/if}
            <button class="act" on:click={() => showHistory(r)}>历史版本</button>
          </td>
        </tr>
      {:else}
        <tr><td colspan="9" class="muted">暂无规则</td></tr>
      {/each}
    </tbody>
  </table>
</div>

{#if historyFor}
  <div class="card">
    <h2>历史版本 — {historyFor.name} &lt;{historyFor.email}&gt;</h2>
    <table>
      <thead>
        <tr><th>版本</th><th>状态</th><th>接收人</th><th>范围</th><th>生效</th><th>截止</th><th>停用时间</th><th>停用原因</th></tr>
      </thead>
      <tbody>
        {#each historyRows as h}
          <tr>
            <td>v{h.version}</td>
            <td><span class="badge {statusClass[h.status]}">{statusLabel[h.status]}</span></td>
            <td>{h.recipientEmail}</td>
            <td>{scopeText(h)}</td>
            <td class="muted">{fmtTime(h.startTime)}</td>
            <td class="muted">{fmtTime(h.endDate)}</td>
            <td class="muted">{fmtTime(h.closedAt)}</td>
            <td class="muted">{reasonLabel[h.closeReason] || h.closeReason || '—'}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}
