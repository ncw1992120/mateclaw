/**
 * 复制文本到剪贴板，兼容非 HTTPS 环境（navigator.clipboard 不可用时回退到 execCommand）
 */
export async function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard) {
    return navigator.clipboard.writeText(text)
  }
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.left = '-9999px'
  document.body.appendChild(ta)
  ta.select()
  const ok = document.execCommand('copy')
  document.body.removeChild(ta)
  if (!ok) {
    throw new Error('Clipboard copy command failed')
  }
}
