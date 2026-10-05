<script>
  import { onMount } from 'svelte'
  import Employees from './lib/Employees.svelte'
  import AutoReplies from './lib/AutoReplies.svelte'
  import ForwardRules from './lib/ForwardRules.svelte'
  import Mailbox from './lib/Mailbox.svelte'
  import AuditLog from './lib/AuditLog.svelte'

  const tabs = [
    { key: 'employees', label: '员工管理', comp: Employees },
    { key: 'autoreply', label: '自动回复', comp: AutoReplies },
    { key: 'forward', label: '转发授权', comp: ForwardRules },
    { key: 'mailbox', label: '邮箱模拟', comp: Mailbox },
    { key: 'audit', label: '审计日志', comp: AuditLog }
  ]
  let active = 'employees'
  let toast = null
  let toastKind = 'ok'

  function notify(e) {
    toast = e.detail.message
    toastKind = e.detail.kind || 'ok'
    clearTimeout(notify._t)
    notify._t = setTimeout(() => (toast = null), 4000)
  }

  onMount(() => {
    const h = (e) => notify(e)
    window.addEventListener('notify', h)
    return () => window.removeEventListener('notify', h)
  })
</script>

<header>
  <h1>离职邮箱自动回复与转发授权管理</h1>
  <p>人事维护自动回复 · 主管申请转发授权 · 审批后生效 · 私人与敏感邮件永不转发</p>
</header>

<nav>
  {#each tabs as t}
    <button class:active={active === t.key} on:click={() => (active = t.key)}>{t.label}</button>
  {/each}
</nav>

<main>
  {#each tabs as t}
    {#if active === t.key}
      <svelte:component this={t.comp} />
    {/if}
  {/each}
</main>

{#if toast}
  <div class="toast {toastKind}">{toast}</div>
{/if}

<style>
  :global(*) { box-sizing: border-box; }
  :global(body) {
    margin: 0;
    font-family: 'PingFang SC', 'Microsoft YaHei', 'Noto Sans CJK SC', system-ui, sans-serif;
    background: #f0f2f5;
    color: #1f2329;
  }
  header {
    background: linear-gradient(135deg, #1f3a5f, #2f5b8f);
    color: #fff;
    padding: 20px 32px;
  }
  header h1 { margin: 0 0 4px; font-size: 20px; }
  header p { margin: 0; font-size: 13px; opacity: 0.8; }
  nav {
    display: flex;
    gap: 4px;
    background: #fff;
    padding: 0 24px;
    border-bottom: 1px solid #e5e6eb;
  }
  nav button {
    border: none;
    background: none;
    padding: 12px 18px;
    font-size: 14px;
    cursor: pointer;
    color: #4e5969;
    border-bottom: 2px solid transparent;
  }
  nav button.active { color: #1f3a5f; border-bottom-color: #2f5b8f; font-weight: 600; }
  main { padding: 20px 32px; max-width: 1280px; margin: 0 auto; }
  .toast {
    position: fixed;
    top: 16px;
    left: 50%;
    transform: translateX(-50%);
    padding: 10px 20px;
    border-radius: 6px;
    color: #fff;
    font-size: 14px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
    z-index: 99;
    max-width: 70vw;
  }
  .toast.ok { background: #2f5b8f; }
  .toast.err { background: #c0392b; }

  :global(.card) {
    background: #fff;
    border-radius: 8px;
    padding: 16px 20px;
    margin-bottom: 16px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  }
  :global(.card h2) { margin: 0 0 12px; font-size: 16px; }
  :global(table) { width: 100%; border-collapse: collapse; font-size: 13px; }
  :global(th), :global(td) { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }
  :global(th) { color: #86909c; font-weight: 500; white-space: nowrap; }
  :global(button.act) {
    border: 1px solid #2f5b8f;
    background: #fff;
    color: #2f5b8f;
    border-radius: 4px;
    padding: 3px 10px;
    font-size: 12px;
    cursor: pointer;
    margin-right: 4px;
  }
  :global(button.act:hover) { background: #2f5b8f; color: #fff; }
  :global(button.act.danger) { border-color: #c0392b; color: #c0392b; }
  :global(button.act.danger:hover) { background: #c0392b; color: #fff; }
  :global(button.primary) {
    border: none;
    background: #2f5b8f;
    color: #fff;
    border-radius: 4px;
    padding: 7px 18px;
    font-size: 13px;
    cursor: pointer;
  }
  :global(input), :global(select), :global(textarea) {
    border: 1px solid #dcdfe5;
    border-radius: 4px;
    padding: 6px 8px;
    font-size: 13px;
    font-family: inherit;
  }
  :global(label.fld) { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: #4e5969; }
  :global(.badge) {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 10px;
    font-size: 12px;
    white-space: nowrap;
  }
  :global(.badge.green) { background: #e8f5e9; color: #2e7d32; }
  :global(.badge.gray) { background: #eceff1; color: #607d8b; }
  :global(.badge.orange) { background: #fff3e0; color: #e65100; }
  :global(.badge.red) { background: #ffebee; color: #c62828; }
  :global(.badge.blue) { background: #e3f2fd; color: #1565c0; }
  :global(.muted) { color: #86909c; font-size: 12px; }
  :global(.form-row) { display: flex; gap: 12px; flex-wrap: wrap; align-items: flex-end; margin-bottom: 10px; }
</style>
