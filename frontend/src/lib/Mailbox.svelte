<script>
  import { onMount } from 'svelte'
  import { api, fmtTime } from '../api.js'
  import { run } from './helpers.js'

  let inbox = []
  let outbox = []
  let tasks = []
  let employees = []
  let form = { from: 'client@customer.example', to: '', subject: '', body: '', privateFlag: false }

  const taskLabel = { PENDING: '待执行', SUCCESS: '成功', FAILED: '失败', CANCELLED: '已取消' }
  const taskClass = { PENDING: 'orange', SUCCESS: 'green', FAILED: 'red', CANCELLED: 'gray' }
  const kindLabel = { NORMAL: '普通', AUTO_REPLY: '自动回复', FORWARD: '转发' }

  async function load() {
    ;[inbox, outbox, tasks, employees] = await Promise.all([
      api.inbox(), api.outbox(), api.syncTasks(), api.employees()
    ])
  }

  async function receive() {
    await run(api.receiveMail(form), '来信已投递,同步任务已生成')
    form.subject = ''
    form.body = ''
    await load()
  }

  async function syncNow() {
    await run(api.runSync(), '同步完成')
    await load()
  }

  async function retry(t) {
    await run(api.retryTask(t.id), `任务 #${t.id} 已重试(实时核对授权)`)
    await load()
  }

  onMount(load)
</script>

<div class="card">
  <h2>模拟外部来信</h2>
  <div class="form-row">
    <label class="fld">发件人<input bind:value={form.from} /></label>
    <label class="fld">收件人(离职员工)
      <select bind:value={form.to}>
        <option value="">请选择</option>
        {#each employees as e}
          <option value={e.email}>{e.name} &lt;{e.email}&gt;{e.status === 'DEPARTED' ? ' (已离职)' : ''}</option>
        {/each}
      </select>
    </label>
    <label class="fld">主题<input style="width:220px" bind:value={form.subject}
      placeholder="试试:薪资调整 / [flaky] 客户 / 正常业务" /></label>
    <label class="fld" style="flex-direction:row;align-items:center;gap:6px;">
      <input type="checkbox" bind:checked={form.privateFlag} /> 私人邮件
    </label>
  </div>
  <div class="form-row">
    <label class="fld" style="flex:1;">正文<textarea rows="2" bind:value={form.body}></textarea></label>
    <button class="primary" on:click={receive} disabled={!form.to || !form.subject}>投递</button>
    <button class="primary" on:click={syncNow}>立即同步</button>
  </div>
  <p class="muted">
    提示:主题含「薪资 / 私人 / confidential」等敏感词或勾选私人邮件的来信,即使授权有效也不会被转发;
    主题含 [flaky] 的来信首次同步会失败,可在下方任务列表重试。
  </p>
</div>

<div class="card">
  <h2>同步任务</h2>
  <table>
    <thead>
      <tr><th>#</th><th>邮箱</th><th>邮件ID</th><th>关联规则</th><th>状态</th><th>尝试次数</th><th>最近信息</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each tasks as t}
        <tr>
          <td>{t.id}</td>
          <td class="muted">{t.mailbox}</td>
          <td>{t.mailMessageId}</td>
          <td>{t.ruleId ?? '—'}</td>
          <td><span class="badge {taskClass[t.status]}">{taskLabel[t.status]}</span></td>
          <td>{t.attempts}</td>
          <td class="muted" style="max-width:260px;">{t.lastError || '—'}</td>
          <td>
            {#if t.status === 'PENDING' || t.status === 'FAILED'}
              <button class="act" on:click={() => retry(t)}>重试</button>
            {/if}
          </td>
        </tr>
      {:else}
        <tr><td colspan="8" class="muted">暂无任务</td></tr>
      {/each}
    </tbody>
  </table>
</div>

<div class="card">
  <h2>收件箱(外部来信)</h2>
  <table>
    <thead><tr><th>#</th><th>发件人</th><th>收件人</th><th>主题</th><th>标记</th><th>时间</th></tr></thead>
    <tbody>
      {#each inbox as m}
        <tr>
          <td>{m.id}</td>
          <td class="muted">{m.fromAddress}</td>
          <td class="muted">{m.toAddress}</td>
          <td>{m.subject}</td>
          <td>{#if m.privateFlag}<span class="badge red">私人</span>{/if}</td>
          <td class="muted">{fmtTime(m.receivedAt)}</td>
        </tr>
      {:else}
        <tr><td colspan="6" class="muted">暂无来信</td></tr>
      {/each}
    </tbody>
  </table>
</div>

<div class="card">
  <h2>发件箱(自动回复与转发副本)</h2>
  <table>
    <thead><tr><th>#</th><th>类型</th><th>发件人</th><th>收件人</th><th>主题</th><th>时间</th></tr></thead>
    <tbody>
      {#each outbox as m}
        <tr>
          <td>{m.id}</td>
          <td><span class="badge {m.kind === 'FORWARD' ? 'blue' : 'green'}">{kindLabel[m.kind]}</span></td>
          <td class="muted">{m.fromAddress}</td>
          <td class="muted">{m.toAddress}</td>
          <td>{m.subject}</td>
          <td class="muted">{fmtTime(m.receivedAt)}</td>
        </tr>
      {:else}
        <tr><td colspan="6" class="muted">暂无出站邮件</td></tr>
      {/each}
    </tbody>
  </table>
</div>
