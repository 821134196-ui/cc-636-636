<script>
  import { onMount } from 'svelte'
  import { api, fmtTime, toIso } from '../api.js'
  import { run } from './helpers.js'

  let configs = []
  let employees = []
  let historyFor = null
  let historyRows = []
  let form = { employeeEmail: '', content: '', startTime: '', endTime: '', createdBy: '人事-赵敏' }

  const statusLabel = { ACTIVE: '生效中', DISABLED: '已停用' }
  const reasonLabel = { SUPERSEDED: '被新版本取代', MANUAL: '手动停用', END_REACHED: '到期自动停用' }

  async function load() {
    ;[configs, employees] = await Promise.all([api.autoReplies(), api.employees()])
  }

  async function submit() {
    await run(api.upsertAutoReply({
      employeeEmail: form.employeeEmail,
      content: form.content,
      startTime: toIso(form.startTime),
      endTime: toIso(form.endTime),
      createdBy: form.createdBy
    }), '自动回复已保存为新版本')
    form.content = ''
    await load()
  }

  async function disable(cfg) {
    await run(api.disableAutoReply(cfg.id, '人事'), `已停用 ${cfg.employee.email} 的自动回复 v${cfg.version}`)
    await load()
  }

  async function showHistory(cfg) {
    historyFor = cfg.employee
    historyRows = await api.autoReplyHistory(cfg.employee.id)
  }

  onMount(load)
</script>

<div class="card">
  <h2>人事设置自动回复(每次保存生成新版本)</h2>
  <div class="form-row">
    <label class="fld">离职员工
      <select bind:value={form.employeeEmail}>
        <option value="">请选择</option>
        {#each employees.filter((e) => e.status === 'DEPARTED') as e}
          <option value={e.email}>{e.name} &lt;{e.email}&gt;</option>
        {/each}
      </select>
    </label>
    <label class="fld">生效时间<input type="datetime-local" bind:value={form.startTime} /></label>
    <label class="fld">截止时间(可空)<input type="datetime-local" bind:value={form.endTime} /></label>
    <label class="fld">操作人<input bind:value={form.createdBy} /></label>
  </div>
  <div class="form-row" style="align-items: stretch;">
    <label class="fld" style="flex:1;">回复内容(业务衔接提示)
      <textarea rows="3" bind:value={form.content}
        placeholder="例:您好,我已离职,后续业务请联系李娜 lina@corp.example"></textarea>
    </label>
  </div>
  <button class="primary" on:click={submit} disabled={!form.employeeEmail || !form.content || !form.startTime}>
    保存为新版本
  </button>
</div>

<div class="card">
  <h2>自动回复配置(每员工最新版本)</h2>
  <table>
    <thead>
      <tr><th>员工</th><th>版本</th><th>状态</th><th>生效时间</th><th>截止时间</th><th>内容</th><th>操作人</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each configs as c}
        <tr>
          <td>{c.employee.name}<br /><span class="muted">{c.employee.email}</span></td>
          <td>v{c.version}</td>
          <td><span class="badge {c.status === 'ACTIVE' ? 'green' : 'gray'}">{statusLabel[c.status]}</span></td>
          <td class="muted">{fmtTime(c.startTime)}</td>
          <td class="muted">{fmtTime(c.endTime)}</td>
          <td style="max-width:280px;">{c.content}</td>
          <td class="muted">{c.createdBy}</td>
          <td>
            <button class="act" on:click={() => showHistory(c)}>历史版本</button>
            {#if c.status === 'ACTIVE'}
              <button class="act danger" on:click={() => disable(c)}>停用</button>
            {/if}
          </td>
        </tr>
      {:else}
        <tr><td colspan="8" class="muted">暂无配置</td></tr>
      {/each}
    </tbody>
  </table>
</div>

{#if historyFor}
  <div class="card">
    <h2>历史版本 — {historyFor.name} &lt;{historyFor.email}&gt;</h2>
    <table>
      <thead>
        <tr><th>版本</th><th>状态</th><th>生效时间</th><th>截止时间</th><th>内容</th><th>创建</th><th>停用时间</th><th>停用原因</th></tr>
      </thead>
      <tbody>
        {#each historyRows as h}
          <tr>
            <td>v{h.version}</td>
            <td><span class="badge {h.status === 'ACTIVE' ? 'green' : 'gray'}">{statusLabel[h.status]}</span></td>
            <td class="muted">{fmtTime(h.startTime)}</td>
            <td class="muted">{fmtTime(h.endTime)}</td>
            <td style="max-width:260px;">{h.content}</td>
            <td class="muted">{h.createdBy}<br />{fmtTime(h.createdAt)}</td>
            <td class="muted">{fmtTime(h.disabledAt)}</td>
            <td class="muted">{reasonLabel[h.disableReason] || h.disableReason || '—'}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}
