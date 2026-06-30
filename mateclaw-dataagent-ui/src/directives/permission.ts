import type { Directive, DirectiveBinding } from 'vue'
import { usePermission, PERMISSION } from '@/composables/usePermission'

/**
 * v-permission 指令：根据权限点控制元素的显示/隐藏
 *
 * 用法：
 * ```html
 * <!-- 单个权限点：有权限才显示 -->
 * <button v-permission="PERMISSION.MODEL_MANAGE">配置模型</button>
 *
 * <!-- 数组：拥有任意一个权限点即显示 -->
 * <button v-permission="[PERMISSION.DATASOURCE_CREATE, PERMISSION.DATASOURCE_MANAGE]">新建数据源</button>
 * ```
 *
 * 实现方式：无权限时设置 display:none，而非移除 DOM，避免影响布局。
 */
function checkPermission(binding: DirectiveBinding): boolean {
  const { hasPermission, hasAnyPermission } = usePermission()
  const value = binding.value
  if (value == null) {
    return true
  }
  if (Array.isArray(value)) {
    return hasAnyPermission(value)
  }
  return hasPermission(value)
}

export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    if (!checkPermission(binding)) {
      el.style.display = 'none'
    }
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    const allowed = checkPermission(binding)
    if (allowed) {
      // 恢复 display：移除内联 display:none，回退到 CSS 默认值
      if (el.style.display === 'none') {
        el.style.removeProperty('display')
      }
    } else {
      el.style.display = 'none'
    }
  },
}

export { PERMISSION }
