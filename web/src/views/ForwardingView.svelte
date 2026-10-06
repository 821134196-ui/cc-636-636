<script>
  import { get, post } from '../lib/api';
  import { onMount } from 'svelte';
  import { fmtTime, scopeLabel, STOP_REASON_TEXT, CHANGE_TEXT } from '../lib/format';
  import { bump, notify, refreshTick } from '../lib/store';

  let rules = [];
  let employees = [];
  let versions = [];
  let versionFor = null;
  let clock = 0;

  const toLocalInput = (epoch) => {
    const d = new Date(epoch);
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  };
  const fromLocalInput = (s) => new Date(s).getTime();

  let showForm = false;
  let form = {
    employeeId: '', recipientId: '', scopeType: 'ALL_BUSINESS',
    scopeCriteria: '', fromText: '', untilText: ''
  };

  onMount(load);
  refreshTick.subscribe(() => { if (versionFor == null) load(); });

  async function load() {
    rules = await get('/api/forwarding-rules');
    employees = await get('/api/employees');
    clock = (await get('/api/clock')).now;
    if (!form.fromText) {
      form.fromText = toLocalInput(clock);
      form.untilText = toLocalInput(clock + 24 * 3600_000);
    }
  }

  $: owners = employees.filter((e) => e.status === 'OFFBOARDED');
  $: recipients = employees.filter((e) => e.status === 'ACTIVE');

  function pickOwner(id) {
    form.employeeId = id;
    form.recipientId = '';
  }

  async function submit() {
    try {
      await post(`/api/forwarding-rules/employees/${form.employeeId}/request`, {
        recipientId: Number(form.recipientId),
        scopeType: form.scopeType,
        scopeCriteria: form.scopeType === 'ALL_BUSINESS' ? null : form.scopeCriteria,
        validFrom: fromLocalInput(form.fromText),
        validUntil: fromLocalInput(form.untilText),
        actor: '王强(主管)'
      });
      showForm = false;
      form.scopeCriteria = '';
      bump();
      notify('转发申请已提交，等待审批');
    } catch (e) { notify(e.message, true); }
  }

  async function act(r, action, msg) {
    if (msg && !confirm(msg)) return;
    try {
      await post(`/api/forwarding-rules/${r.id}/${action}`,
        action === 'revoke' ? { actor: '管理员', reason: '管理员提前撤销' } : { actor: '审批人', reason: '' });
      bump();
      notify('操作完成');
    } catch (e) { notify(e.message, true); }
  }

  async function openVersions(r) {
    versionFor = r;
    versions = await get(`/api/forwarding-rules/${r.id}/versions`);
  }
</script>

<div class="panel">
  <div class="row" style="justify-content:space-between">
    <h2>转发授权配置（主管申请 · 独立审批）</h2>
    <button onclick={() => (showForm = !showForm)}>主管发起转发申请</button>
  </div>
  <p class="muted small">
    接替人员仅收到获批范围内的邮件副本，<strong>不获得离职邮箱登录权</strong>。
    到期、提前撤销、接收人调岗均立即停用；私人邮件与敏感主题即使规则有效也不转发。
  </p>

  {#if showForm}
    <div class="field grid2" style="margin:10px 0">
      <div>
        <label>离职员工邮箱</label>
        <select onchange={(e) => pickOwner(e.currentTarget.value)} value={form.employeeId}>
          <option value="">请选择…</option>
          {#each owners as e}
            <option value={e.id}>{e.name}（{e.email}）</option>
          {/each}
        </select>
      </div>
      <div>
        <label>接替人员（接收人）</label>
        <select bind:value={form.recipientId}>
          <option value="">请选择…</option>
          {#each recipients as e}
            <option value={e.id}>{e.name}（{e.email}）</option>
          {/each}
        </select>
      </div>
      <div>
        <label>邮件范围</label>
        <select bind:value={form.scopeType}>
          <option value="ALL_BUSINESS">全部业务邮件（仍强制过滤私人/敏感）</option>
          <option value="SENDER_ALLOWLIST">仅指定外部发件人</option>
          <option value="SUBJECT_KEYWORDS">仅主题含关键词</option>
        </select>
      </div>
      <div>
        <label>范围条件（多个用分号分隔）</label>
        <input bind:value={form.scopeCriteria}
          placeholder={form.scopeType === 'SENDER_ALLOWLIST' ? 'a@x.com;b@y.com' : '合同;发票'}
          disabled={form.scopeType === 'ALL_BUSINESS'} />
      </div>
      <div>
        <label>生效时间</label>
        <input type="datetime-local" bind:value={form.fromText} />
      </div>
      <div>
        <label>截止日期（到期自动停用）</label>
        <input type="datetime-local" bind:value={form.untilText} />
      </div>
      <div style="grid-column:1/-1">
        <button class="primary" onclick={submit}>提交申请（待审批）</button>
      </div>
    </div>
  {/if}

  <table>
    <thead>
      <tr><th>离职员工</th><th>接收人</th><th>邮件范围</th><th>状态</th>
        <th>有效期</th><th>申请/审批</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each rules as r}
        <tr>
          <td>{r.employeeName}<br /><span class="muted small mono">{r.employeeEmail}</span></td>
          <td>
            {r.recipientName ?? '—'}<br />
            <span class="muted small mono">{r.recipientEmail}</span>
            {#if r.recipientStatus !== 'ACTIVE'}
              <div><span class="badge {r.recipientStatus}">接收人：{r.recipientStatus}</span></div>
            {/if}
          </td>
          <td class="small">{scopeLabel(r)}</td>
          <td>
            <span class="badge {r.status}">{r.status}</span>
            {#if r.stopReason}
              <div class="small muted" style="margin-top:3px">
                停用原因：{STOP_REASON_TEXT[r.stopReason] ?? r.stopReason}
              </div>
            {/if}
          </td>
          <td class="small">{fmtTime(r.validFrom)}<br />→ {fmtTime(r.validUntil)}
            {#if r.activatedAt}<div class="muted">生效于 {fmtTime(r.activatedAt)}</div>{/if}
            {#if r.stoppedAt}<div class="muted">停用于 {fmtTime(r.stoppedAt)}</div>{/if}
          </td>
          <td class="small">申请：{r.requestedBy}<br />审批：{r.approvedBy ?? '—'}</td>
          <td class="row">
            <button onclick={() => openVersions(r)}>历史版本</button>
            {#if r.status === 'PENDING_APPROVAL'}
              <button class="primary" onclick={() => act(r, 'approve')}>审批通过</button>
              <button class="danger" onclick={() => act(r, 'deny', '确认驳回该申请？')}>驳回</button>
            {:else if r.status === 'ACTIVE' || r.status === 'SCHEDULED'}
              <button class="danger" onclick={() => act(r, 'revoke', '确认提前撤销？撤销为终态，旧同步任务重试也不会恢复。')}>提前撤销</button>
            {:else}
              <span class="muted small">已终态，不可恢复</span>
            {/if}
          </td>
        </tr>
      {/each}
      {#if rules.length === 0}
        <tr><td colspan="7" class="muted">暂无转发申请</td></tr>
      {/if}
    </tbody>
  </table>
</div>

{#if versionFor}
  <div class="modal-mask" onclick={() => (versionFor = null)}>
    <div class="modal wide" onclick={(ev) => ev.stopPropagation()}>
      <h2>转发规则历史版本 — {versionFor.employeeName}</h2>
      <p class="muted small">完整记录每一次的接收人、邮件范围、生效/停用时间与操作人。</p>
      <table>
        <thead><tr><th>版本</th><th>变更</th><th>接收人</th><th>范围</th>
          <th>状态/原因</th><th>有效期</th><th>操作人/备注</th></tr></thead>
        <tbody>
          {#each versions as v}
            <tr>
              <td>v{v.version}</td>
              <td>{CHANGE_TEXT[v.changeType] ?? v.changeType}</td>
              <td class="mono small">{v.recipientEmail}</td>
              <td class="small">{v.scopeType}{v.scopeCriteria ? `：${v.scopeCriteria}` : ''}</td>
              <td>
                <span class="badge {v.status}">{v.status}</span>
                {#if v.stopReason}<div class="small muted">{STOP_REASON_TEXT[v.stopReason]}</div>{/if}
              </td>
              <td class="small">{fmtTime(v.validFrom)}<br />→ {fmtTime(v.validUntil)}
                <div class="muted">{fmtTime(v.createdAt)}</div></td>
              <td class="small">{v.changedBy}<br /><span class="muted">{v.note ?? ''}</span></td>
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
