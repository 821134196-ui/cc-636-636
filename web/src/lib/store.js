import { writable } from 'svelte/store';

/** 任一页面发生写操作后自增，列表页订阅并重新拉取。 */
export const refreshTick = writable(0);
export function bump() {
  refreshTick.update((n) => n + 1);
}

/** 全局提示消息。 */
export const toast = writable(null);
export function notify(text, isError = false) {
  toast.set({ text, isError, ts: Date.now() });
}
