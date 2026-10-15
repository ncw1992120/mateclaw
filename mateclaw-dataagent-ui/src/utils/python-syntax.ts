import hljs from 'highlight.js'

/** 将 Python 源码转换为安全的高亮 HTML。 */
export function highlightPython(source: string): string {
  return hljs.highlight(source || ' ', { language: 'python' }).value
}
