<script>
  import { get, post } from '../lib/api';
  import { onMount } from 'svelte';
  import { fmtTime } from '../lib/format';
  import { bump, notify, refreshTick } from '../lib/store';

  let employees = [];
  let clock = 0;
  let showCreate = false;
  let form = { name: '', email: '', department: '' };
  let depMinutes = 30;

  onMount(load);
  refreshTick.subscribe(() => load());

  async function load() {
    employees = await get('/api/employees');
    clock = (await get('/api/clock')).now;
  }

  async function createEmployee() {
    try {
      await post('/api/employees', form);
      form = { name: '', email: '', department: '' };
      showCreate = false;
      bump();
      notify('员工已创建');
    } catch (e) { notify(e.message, true); }
  }

  async function confirmDeparture(e) {
    const departure = clock - Number(depMinutes) * 60000;
    try {
      await post(`/api/employees/${e.id}/offboarding`, { departureAt: departure, actor: 'HR' });
      bump();
      notify(`已确认 ${e.name} 离职，时间 ${depMinutes} 分钟前；请到“自动回复”页由 HR 配置回复内容`);
    } catch (ex) { notify(ex.message, true); }
  }

  async function reassign(e) {
    if (!confirm(`确认将 ${e.name} 标记为调岗？以其为接收人的所有有效转发将立即停用。`)) return;
    try {
      await post(`/api/employees/${e.id}/reassign`, { actor: '人事部' });
      bump();
      notify('调岗已登记，相关转发已立即停用');
    } catch (ex) { notify(ex.message, true); }
  }
</script>

<div class="panel">
  <div class="row" style="justify-content:space-between">
    <h2>员工与邮箱账号</h2>
    <button onclick={() => (showCreate = !showCreate)}>新增员工</button>
  </div>

  {#if showCreate}
    <div class="grid2 field" style="margin:12px 0">
      <div><label>姓名</label><input bind:value={form.name} placeholder="张三" /></div>
      <div><label>邮箱</label><input bind:value={form.email} placeholder="zhangsan@corp.example" /></div>
      <div><label>部门</label><input bind:value={form.department} /></div>
      <div style="align-self:end">
        <button class="primary" onclick={createEmployee}>保存</button>
      </div>
    </div>
  {/if}

  <table>
    <thead>
      <tr><th>姓名</th><th>邮箱</th><th>部门</th><th>状态</th><th>离职时间</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each employees as e}
        <tr>
          <td>{e.name}</td>
          <td class="mono">{e.email}</td>
          <td>{e.department}</td>
          <td><span class="badge {e.status}">{e.status}</span></td>
          <td>{fmtTime(e.departureAt)}</td>
          <td class="row">
            {#if e.status === 'ACTIVE'}
              <button onclick={() => confirmDeparture(e)}>HR 确认离职</button>
              <button class="danger" onclick={() => reassign(e)}>登记调岗</button>
            {:else if e.status === 'REASSIGNED'}
              <span class="muted small">已调岗，其接收授权已停用</span>
            {:else}
              <span class="muted small">已离职</span>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
  <p class="muted small" style="margin-bottom:0">
    模拟设定：离职时间取“当前时间 − {depMinutes} 分钟”，可在下方调整后再确认。
    <input type="number" bind:value={depMinutes} style="width:70px" /> 分钟
  </p>
</div>
