// 全局通知与错误处理工具
export function notify(message, kind = 'ok') {
  window.dispatchEvent(new CustomEvent('notify', { detail: { message, kind } }))
}

export async function run(promise, okMessage) {
  try {
    const r = await promise
    if (okMessage) notify(okMessage)
    return r
  } catch (e) {
    notify('操作失败: ' + e.message, 'err')
    throw e
  }
}
