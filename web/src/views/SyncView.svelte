<script>
  import { get, post } from '../lib/api';
  import { onMount } from 'svelte';
  import { fmtTime } from '../lib/format';
  import { bump, notify, refreshTick } from '../lib/store';

  let tasks = [];
  let outcomes = [];
  let filter = '';
  let failCount = 1;

  onMount(load);
  refreshTick.subscribe(load);

  async function load() {
    tasks = await get('/api/sync/tasks');
    outcomes = await get('/api/sync/outcomes');
  }

  $: shown = filter ? tasks.filter((t) => t.status === filter) : tasks;

  async function retry(t) {
    try {
      const r = await post(`/api/sync/tasks/${t.id}/retry`);
      bump();
      notify(`旧任务重试结果：${r.report}`);
    } catch (e) { notify(e.message, true); }
  }

  async function inject() {
    await post('/api/admin/fail-next', { count: Number(failCount) });
    notify(`已注入：接下来 ${failCount} 次转发投递将失败（模拟旧任务失败）`);
  }

  async function ack(o) {
    try {
      const r = await post(`/api/sync/receipts/${o.receiptId}/ack`, { remoteService: 'MAIL-GATEWAY' });
      bump();
      notify(r.first ? '回执已确认' : '重复回执，已幂等丢弃（授权状态不变）');
    } catch (e) { notify(e.message, true); }
  }
</script>

<div class="panel">
  <div class="row" style="justify-content:space-between">
    <h2>同步任务与处置结果</h2>
    <div class="row">
      <span class="small muted">故障注入：未来</span>
      <input type="number" bind:value={failCount} style="width:70px" />
      <span class="small muted">次投递失败</span>
      <button onclick={inject}>注入故障</button>
    </div>
  </div>
  <p class="muted small">
    每个任务对应“一封邮件 × 一条规则”。重试时重新加载规则并实时核对授权——
    到期 / 撤销 / 接收人调岗后，旧任务只会变成 SKIPPED_NO_AUTH，规则不会被重新打开。
  </p>

  <div class="row" style="margin:8px 0">
    <span class="small muted">筛选：</span>
    {#each ['', 'PENDING', 'RUNNING', 'FAILED', 'SUCCEEDED', 'SKIPPED_NO_AUTH'] as s}
      <button class={filter === s ? 'active' : ''} style={filter === s ? 'background:var(--brand);color:#fff;border-color:var(--brand)' : ''}
        onclick={() => (filter = s)}>{s || '全部'}</button>
    {/each}
  </div>

  <table>
    <thead><tr><th>#</th><th>邮件</th><th>规则/接收人</th><th>任务状态</th>
      <th>尝试</th><th>规则现状 vs 任务快照</th><th>下次重试</th><th>回执</th><th></th></tr></thead>
    <tbody>
      {#each shown as t}
        <tr>
          <td>{t.id}</td>
          <td class="small">#{t.mailId} {t.mailSubject ?? ''}</td>
          <td class="small">R{t.ruleId} → <span class="mono">{t.recipientEmail}</span></td>
          <td><span class="badge {t.status}">{t.status}</span>
            {#if t.lastError}<div class="small muted">{t.lastError}</div>{/if}</td>
          <td class="small">{t.attempts}/{t.maxAttempts}</td>
          <td class="small">当前 <strong>{t.ruleStatusNow}</strong><br />
            <span class="muted">快照 v{t.ruleVersionSnapshot} / 现 v{t.ruleVersionNow}</span></td>
          <td class="small">{t.status === 'FAILED' ? fmtTime(t.nextAttemptAt) : '—'}</td>
          <td class="small mono">{t.receiptId ?? '—'}
            {#if t.acknowledged}<div class="muted">已确认</div>{/if}</td>
          <td>
            {#if t.status === 'FAILED'}
              <button onclick={() => retry(t)}>立即重试</button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>

<div class="panel">
  <h2>逐封邮件处置结论（幂等）</h2>
  <table>
    <thead><tr><th>邮件</th><th>规则</th><th>接收人</th><th>结论</th><th>时间</th>
      <th>执行时规则状态</th><th>回执</th><th></th></tr></thead>
    <tbody>
      {#each outcomes as o}
        <tr>
          <td class="small">#{o.mailId}</td>
          <td>R{o.ruleId}</td>
          <td class="mono small">{o.recipientEmail}</td>
          <td><span class="badge {o.decision}">{o.decision}</span></td>
          <td class="small">{fmtTime(o.attemptedAt)}</td>
          <td class="small">{o.ruleStatusSnapshot}</td>
          <td class="mono small">{o.receiptId}</td>
          <td>
            {#if o.decision === 'FORWARDED'}
              <button onclick={() => ack(o)}>发送/重发回执</button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>
