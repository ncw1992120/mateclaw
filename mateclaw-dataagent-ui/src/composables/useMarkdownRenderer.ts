import { marked } from 'marked'
import DOMPurify from 'dompurify'

/** DOMPurify 配置：允许 class / style 属性 */
const purifyConfig = {
  ADD_ATTR: ['class', 'style'],
}

/**
 * Markdown 渲染公共工具
 * <p>
 * 统一 Markdown 渲染逻辑（marked + DOMPurify），
 * 供 AiAnalysisWidget、DashboardPreviewView 等组件复用。
 */
export function useMarkdownRenderer() {
  /**
   * 将 Markdown 文本解析为安全 HTML
   * @param md Markdown 原文
   * @returns 安全 HTML 字符串
   */
  function renderMarkdown(md: string): string {
    if (!md) return ''
    const html = marked.parse(md, { async: false, gfm: true, breaks: true }) as string
    return DOMPurify.sanitize(html, purifyConfig)
  }

  return { renderMarkdown }
}
