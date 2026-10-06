<script>
  import { fmtTime } from '../lib/format';
  import { get, post } from '../lib/api';
  import { onMount } from 'svelte';
  import { bump, notify, refreshTick } from '../lib/store';

  let now = 0;
  let minutes = 60;

  async function load() {
    const c = await get('/api/clock');
    now = c.now;
  }
  onMount(load);
  refreshTick.subscribe(() => load());

  async function advance() {
    await post('/api/clock', { advanceMinutes: Number(minutes) });
    await load();
    bump();
    notify(`模拟时钟已推进 ${minutes} 分钟`);
  }
  async function maintain() {
    const r = await post('/api/sync/drain');
    notify(`已执行一轮维护：处理 ${r.processedTasks} 个同步任务`);
    bump();
  }
</script>

<div class="clockbar">
  <div>
    <span class="muted">模拟业务时间　</span>
    <strong>{fmtTime(now)}</strong>
  </div>
  <div class="row">
    <input type="number" bind:value={minutes} style="width:90px" /> 分钟
    <button onclick={advance}>推进时钟</button>
    <button class="primary" onclick={maintain}>立即执行同步/维护</button>
  </div>
</div>

<style>
  .clockbar {
    display: flex; justify-content: space-between; align-items: center;
    gap: 12px; flex-wrap: wrap; margin-bottom: 14px;
  }
  input { width: auto; }
</style>
