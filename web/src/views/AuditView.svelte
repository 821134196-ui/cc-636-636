<script>
  import { get, post } from '../lib/api';
  import { onMount } from 'svelte';
  import { fmtTime } from '../lib/format';
  import { notify, refreshTick } from '../lib/store';

  let logs = [];
  let actionFilter = '';

  onMount(load);
  refreshTick.subscribe(load);

  async function load() {
    logs = await get('/api/audit?limit=300');
  }

  $: shown = actionFilter ? logs.filter((l) => l.action.includes(actionFilter)) : logs;

  async function resetAll() {
    if (!confirm('清空所有规则、邮件、任务与审计数据并恢复演示种子？')) return;
    try {
      await post('/api/admin/reset');
      refreshTick.update((n) => n + 1);
      notify('已重置为初始演示数据');
    } catch (e) { notify(e.message, true); }
  }
</script>

<div class="panel">
  <div class="row" style="justify-content:space-between">
    <h2>审计记录</h2>
    <div class="row">
      <input placeholder="按动作过滤，如 FORWARD / SYNC" bind:value={actionFilter} style="width:260px" />
      <button class="danger" onclick={resetAll}>重置演示数据</button>
    </div>
  </div>
  <p class="muted small">配置变更与同步结果全部留痕，可核对每次转发的接收人、邮件范围、生效与停用时间。</p>
  <table>
    <thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>明细</th></tr></thead>
    <tbody>
      {#each shown as l}
        <tr>
          <td class="small whitespace-nowrap">{fmtTime(l.createdAt)}</td>
          <td class="small">{l.actor}</td>
          <td><span class="action">{l.action}</span></td>
          <td class="small">{l.entityType}#{l.entityId ?? ''}</td>
          <td><pre class="detail">{JSON.stringify(JSON.parse(l.detail), null, 2)}</pre></td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>

<style>
  .action {
    font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px;
    background: var(--brand-soft); color: var(--brand);
    padding: 2px 7px; border-radius: 5px; white-space: nowrap;
  }
  .whitespace-nowrap { white-space: nowrap; }
</style>
