<script>
  import { onMount } from 'svelte'
  import { api, fmtTime } from '../api.js'
  import { run } from './helpers.js'

  let employees = []
  let form = { email: '', name: '', department: '' }

  const statusLabel = { ACTIVE: '在职', DEPARTED: '已离职', TRANSFERRED: '已调岗' }
  const statusClass = { ACTIVE: 'green', DEPARTED: 'orange', TRANSFERRED: 'gray' }

  async function load() {
    employees = await api.employees()
  }

  async function create() {
    await run(api.createEmployee(form), '员工已创建')
    form = { email: '', name: '', department: '' }
    await load()
  }

  async function setStatus(emp, status) {
    const label = statusLabel[status]
    await run(api.setStatus(emp.id, status, '管理员'),
      status === 'TRANSFERRED'
        ? `${emp.name} 已标记调岗,其作为接收人的转发授权已立即停用`
        : `${emp.name} 状态已更新为 ${label}`)
    await load()
  }

  onMount(load)
</script>

<div class="card">
  <h2>新增员工</h2>
  <div class="form-row">
    <label class="fld">邮箱<input bind:value={form.email} placeholder="name@corp.example" /></label>
    <label class="fld">姓名<input bind:value={form.name} /></label>
    <label class="fld">部门<input bind:value={form.department} /></label>
    <button class="primary" on:click={create}>创建</button>
  </div>
</div>

<div class="card">
  <h2>员工列表</h2>
  <table>
    <thead>
      <tr><th>姓名</th><th>邮箱</th><th>部门</th><th>状态</th><th>状态变更时间</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each employees as e}
        <tr>
          <td>{e.name}</td>
          <td>{e.email}</td>
          <td>{e.department || '—'}</td>
          <td><span class="badge {statusClass[e.status]}">{statusLabel[e.status]}</span></td>
          <td class="muted">{fmtTime(e.statusChangedAt)}</td>
          <td>
            {#if e.status !== 'DEPARTED'}
              <button class="act" on:click={() => setStatus(e, 'DEPARTED')}>标记离职</button>
            {/if}
            {#if e.status !== 'TRANSFERRED'}
              <button class="act danger" on:click={() => setStatus(e, 'TRANSFERRED')}>标记调岗</button>
            {/if}
            {#if e.status !== 'ACTIVE'}
              <button class="act" on:click={() => setStatus(e, 'ACTIVE')}>恢复在职</button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
  <p class="muted">提示:员工被标记「调岗」时,所有以该员工为接收人的转发授权会立即停用并写入审计。</p>
</div>
