<script>
  import { get, post } from '../lib/api';
  import { onMount } from 'svelte';
  import { fmtTime, CHANGE_TEXT } from '../lib/format';
  import { bump, notify, refreshTick } from '../lib/store';

  let rules = [];
  let employees = [];
  let versions = [];
  let versionFor = null;
  let form = { employeeId: '', body: '', offsetMin: 0 };

  onMount(load);
  refreshTick.subscribe(() => { if (versionFor == null) load(); });

  async function load() {
    rules = await get('/api/auto-replies');
    employees = await get('/api/employees');
  }

  $: offboardedWithout = employees.filter(
    (e) => e.status === 'OFFBOARDED' && !rules.some((r) => r.employeeId === e.id));

  $: editingFor = form.employeeId
    ? employees.find((e) => e.id === Number(form.employeeId))
    : null;

  async function save() {
    if (!form.employeeId) { notify('请选择离职员工', true); return; }
    const dep = editingFor.departureAt;
    const effectiveAt = dep + Number(form.offsetMin) * 60000;
    try {
      await post(`/api/auto-replies/employees/${form.employeeId}/confirm`,
        { body: form.body, effectiveAt, actor: '陈静(HR)' });
      form = { employeeId: '', body: '', offsetMin: 0 };
      bump();
      notify('自动回复已确认并排程');
    } catch (e) { notify(e.message, true); }
  }

  async function disable(r) {
    if (!confirm('停用该自动回复？停用后不会自动恢复。')) return;
    await post(`/api/auto-replies/${r.id}/disable`, { actor: 'HR' });
    bump();
    notify('自动回复已停用');
  }

  async function openVersions(r) {
    versionFor = r;
    versions = await get(`/api/auto-replies/${r.id}/versions`);
  }
</script>

<div class="panel">
  <h2>自动回复配置（人事确认）</h2>
  <p class="muted small">人事确认离职时间与回复内容；到离职时间自动生效。接替人员不因此获得邮箱登录权。</p>
  <table>
    <thead>
      <tr><th>离职员工</th><th>回复内容</th><th>状态</th><th>版本</th>
        <th>生效时间</th><th>停用时间</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each rules as r}
        <tr>
          <td>{r.employeeName}<br /><span class="muted small mono">{r.employeeEmail}</span></td>
          <td style="max-width:340px">{r.body}</td>
          <td><span class="badge {r.status}">{r.status}</span></td>
          <td>v{r.version}</td>
          <td class="small">{fmtTime(r.effectiveAt)}</td>
          <td class="small">{fmtTime(r.disabledAt)}</td>
          <td class="row">
            <button onclick={() => openVersions(r)}>历史版本</button>
            {#if r.status !== 'DISABLED'}
              <button class="danger" onclick={() => disable(r)}>停用</button>
            {/if}
          </td>
        </tr>
      {/each}
      {#if rules.length === 0}
        <tr><td colspan="7" class="muted">暂无自动回复配置</td></tr>
      {/if}
    </tbody>
  </table>

  {#if offboardedWithout.length > 0}
    <div style="margin-top:14px">
      <h3 class="small" style="margin-bottom:8px">HR 确认新的自动回复</h3>
      <div class="field" style="margin-bottom:8px">
        <label>离职员工</label>
        <select bind:value={form.employeeId} style="max-width:360px">
          <option value="">请选择…</option>
          {#each offboardedWithout as e}
            <option value={e.id}>{e.name}（{e.email}，离职于 {fmtTime(e.departureAt)}）</option>
          {/each}
        </select>
      </div>
      <div class="field" style="margin-bottom:8px">
        <label>回复正文（业务衔接提示）</label>
        <textarea rows="3" bind:value={form.body}
          placeholder="您好，本人已离职，后续业务由 XX 衔接，请联系……"></textarea>
      </div>
      <div class="row">
        <span class="small muted">生效时间相对离职时间偏移</span>
        <input type="number" bind:value={form.offsetMin} style="width:80px" /> 分钟
        <button class="primary" onclick={save}>确认并排程</button>
      </div>
    </div>
  {/if}
</div>

{#if versionFor}
  <div class="modal-mask" onclick={() => (versionFor = null)}>
    <div class="modal" onclick={(ev) => ev.stopPropagation()}>
      <h2>自动回复历史版本 — {versionFor.employeeName}</h2>
      <table>
        <thead><tr><th>版本</th><th>变更</th><th>状态</th><th>操作人</th><th>时间</th><th>内容</th></tr></thead>
        <tbody>
          {#each versions as v}
            <tr>
              <td>v{v.version}</td>
              <td>{CHANGE_TEXT[v.changeType] ?? v.changeType}</td>
              <td><span class="badge {v.status}">{v.status}</span></td>
              <td>{v.changedBy}</td>
              <td class="small">{fmtTime(v.createdAt)}<br /><span class="muted">{v.note ?? ''}</span></td>
              <td style="max-width:300px">{v.body}</td>
            </tr>
          {/each}
        </tbody>
      </table>
      <div class="row" style="justify-content:flex-end;margin-top:10px">
        <button onclick={() => (versionFor = null)}>关闭</button>
      </div>
    </div>
  </div>
{/if}
