<script>
  import { get, post } from '../lib/api';
  import { onMount } from 'svelte';
  import { fmtTime } from '../lib/format';
  import { bump, notify, refreshTick } from '../lib/store';

  let employees = [];
  let mails = [];
  let outbox = [];
  let ownerId = '';
  let form = { fromEmail: '', fromName: '', subject: '', body: '', markPrivate: false, markSensitive: false };

  onMount(load);
  refreshTick.subscribe(load);

  async function load() {
    employees = await get('/api/employees');
    if (!ownerId) {
      const off = employees.find((e) => e.status === 'OFFBOARDED');
      if (off) ownerId = String(off.id);
    }
    if (ownerId) mails = await get(`/api/mail/employees/${ownerId}/inbox`);
    outbox = await get('/api/mail/outbox');
  }

  async function send() {
    try {
      await post(`/api/mail/employees/${ownerId}/inbound`, form);
      form = { ...form, fromEmail: '', fromName: '', subject: '', body: '' };
      bump();
      notify('外部来信已进入模拟邮箱');
    } catch (e) { notify(e.message, true); }
  }
</script>

<div class="panel">
  <h2>模拟外部来信</h2>
  <p class="muted small">本地模拟邮箱服务：外部联系人投信后，生效中的自动回复立即发出；ACTIVE 转发规则生成同步任务。</p>
  <div class="field grid2">
    <div>
      <label>投递到离职员工邮箱</label>
      <select bind:value={ownerId}>
        {#each employees.filter((e) => e.status === 'OFFBOARDED') as e}
          <option value={e.id}>{e.name}（{e.email}）</option>
        {/each}
      </select>
    </div>
    <div><label>发件人邮箱</label><input bind:value={form.fromEmail} placeholder="client@partner.example" /></div>
    <div><label>发件人称呼</label><input bind:value={form.fromName} /></div>
    <div><label>主题</label><input bind:value={form.subject} placeholder="合同/发票/……" /></div>
    <div style="grid-column:1/-1"><label>正文</label><textarea rows="2" bind:value={form.body}></textarea></div>
    <div class="row" style="grid-column:1/-1">
      <label class="row small" style="gap:6px"><input type="checkbox" bind:checked={form.markPrivate} style="width:auto" />人工标记私人邮件</label>
      <label class="row small" style="gap:6px"><input type="checkbox" bind:checked={form.markSensitive} style="width:auto" />人工标记敏感主题</label>
      <span class="muted small">（内容含“私人/工资/薪资/密码/confidential”等也会被自动识别）</span>
      <button class="primary" onclick={send}>模拟发信</button>
    </div>
  </div>
</div>

<div class="panel">
  <h2>收件箱（{employees.find((e) => e.id === Number(ownerId))?.email ?? ''}）</h2>
  <table>
    <thead><tr><th>收到时间</th><th>发件人</th><th>主题</th><th>标记</th><th>自动回复</th></tr></thead>
    <tbody>
      {#each mails as m}
        <tr>
          <td class="small">{fmtTime(m.receivedAt)}</td>
          <td class="small">{m.fromName ?? ''}<br /><span class="mono">{m.fromEmail}</span></td>
          <td>{m.subject}</td>
          <td>
            {#if m.privateFlag}<span class="badge BLOCKED_PRIVATE">私人</span>{/if}
            {#if m.sensitiveFlag}<span class="badge BLOCKED_SENSITIVE">敏感</span>{/if}
          </td>
          <td>{m.replySent ? '✓ 已回复' : '—'}</td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>

<div class="panel">
  <h2>模拟邮箱发件箱（自动回复 + 转发副本）</h2>
  <table>
    <thead><tr><th>时间</th><th>类型</th><th>目标</th><th>主题</th><th>关联规则</th></tr></thead>
    <tbody>
      {#each outbox as o}
        <tr>
          <td class="small">{fmtTime(o.sentAt)}</td>
          <td><span class="badge {o.type === 'FORWARD' ? 'FORWARDED' : 'SCHEDULED'}">{o.type}</span></td>
          <td class="mono small">{o.targetEmail}</td>
          <td class="small">{o.subject}</td>
          <td>{o.ruleId ?? '—'}</td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>
