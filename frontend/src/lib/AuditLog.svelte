<script>
  import { onMount } from 'svelte'
  import { api, fmtTime } from '../api.js'

  let rows = []
  let category = ''

  const categories = [
    { value: '', label: '全部' },
    { value: 'CONFIG', label: '配置变更' },
    { value: 'APPROVAL', label: '审批' },
    { value: 'SYNC', label: '同步结果' },
    { value: 'EMPLOYEE', label: '员工状态' }
  ]

  async function load() {
    rows = await api.audit(category)
  }

  onMount(load)
</script>

<div class="card">
  <h2>审计日志</h2>
  <div class="form-row">
    {#each categories as c}
      <button class="act" class:primary={category === c.value}
        on:click={() => { category = c.value; load() }}>{c.label}</button>
    {/each}
    <button class="act" on:click={load}>刷新</button>
  </div>
  <table>
    <thead><tr><th>时间</th><th>类别</th><th>动作</th><th>操作人</th><th>详情</th></tr></thead>
    <tbody>
      {#each rows as r}
        <tr>
          <td class="muted" style="white-space:nowrap;">{fmtTime(r.timestamp)}</td>
          <td><span class="badge blue">{r.category}</span></td>
          <td style="white-space:nowrap;">{r.action}</td>
          <td class="muted">{r.actor}</td>
          <td>{r.details}</td>
        </tr>
      {:else}
        <tr><td colspan="5" class="muted">暂无记录</td></tr>
      {/each}
    </tbody>
  </table>
</div>
