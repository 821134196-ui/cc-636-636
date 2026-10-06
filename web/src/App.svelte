<script>
  import { onMount } from 'svelte';
  import ClockBar from './lib/ClockBar.svelte';
  import EmployeesView from './views/EmployeesView.svelte';
  import AutoReplyView from './views/AutoReplyView.svelte';
  import ForwardingView from './views/ForwardingView.svelte';
  import MailboxView from './views/MailboxView.svelte';
  import SyncView from './views/SyncView.svelte';
  import AuditView from './views/AuditView.svelte';
  import { toast } from './lib/store';

  const TABS = [
    { key: 'forwarding', label: '转发授权' },
    { key: 'reply', label: '自动回复' },
    { key: 'mailbox', label: '模拟邮箱' },
    { key: 'sync', label: '同步与回执' },
    { key: 'employees', label: '员工/调岗' },
    { key: 'audit', label: '审计记录' }
  ];
  let tab = 'forwarding';
  let currentToast = null;
  toast.subscribe((t) => (currentToast = t));
</script>

<header>
  <h1>企业邮箱离职自动回复与转发授权系统</h1>
  <p class="subtitle">
    HR 确认离职与自动回复 · 主管单独申请转发（接收人 / 邮件范围 / 截止日期）· 审批后启用 ·
    到期、撤销、接收人调岗立即停用 · 私人与敏感邮件不转发
  </p>
</header>

<main>
  <ClockBar />
  <nav class="tabs">
    {#each TABS as t}
      <button class={tab === t.key ? 'active' : ''} onclick={() => (tab = t.key)}>
        {t.label}
      </button>
    {/each}
  </nav>

  {#if tab === 'employees'}<EmployeesView />
  {:else if tab === 'reply'}<AutoReplyView />
  {:else if tab === 'forwarding'}<ForwardingView />
  {:else if tab === 'mailbox'}<MailboxView />
  {:else if tab === 'sync'}<SyncView />
  {:else if tab === 'audit'}<AuditView />
  {/if}
</main>

{#if currentToast}
  <div class="toast {currentToast.isError ? 'error' : ''}">{currentToast.text}</div>
{/if}

<style>
  header {
    background: linear-gradient(120deg, #1e3a8a, #1d4ed8);
    color: #fff; padding: 18px 28px;
  }
  header h1 { font-size: 19px; }
  .subtitle { margin: 6px 0 0; font-size: 12.5px; opacity: .9; }
  main { max-width: 1240px; margin: 0 auto; padding: 18px 20px 60px; }
</style>
