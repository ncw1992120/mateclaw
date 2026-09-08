<template>
  <!-- 历史对话搜索弹框（DeepSeek 风格：全屏遮罩 + 顶部居中面板） -->
  <Teleport to="body">
    <Transition name="search-fade">
      <div v-if="open" class="conv-search-overlay" @click.self="close">
        <div class="conv-search-panel">
          <div class="conv-search-input-row">
            <span class="conv-search-icon" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
            </span>
            <input
              ref="inputRef"
              v-model="keyword"
              class="conv-search-input"
              type="text"
              :placeholder="t('conversation.searchPlaceholder')"
              :aria-label="t('conversation.searchPlaceholder')"
              @keydown.down.prevent="moveActive(1)"
              @keydown.up.prevent="moveActive(-1)"
              @keydown.enter.prevent="selectActive"
              @keydown.esc.prevent="close"
            />
            <button class="conv-search-close" :title="t('common.close')" @click="close">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="conv-search-results">
            <template v-if="keyword.trim()">
              <div
                v-for="(conv, index) in results"
                :key="conv.conversationId"
                class="conv-search-item"
                :class="{ active: index === activeIndex }"
                @click="select(conv.conversationId)"
                @mouseenter="activeIndex = index"
              >
                <div class="conv-search-item-main">
                  <span class="conv-search-item-title" :title="conv.title || t('conversation.untitled')">
                    {{ conv.title || t('conversation.untitled') }}
                  </span>
                  <span class="conv-search-item-time">{{ formatRelativeTime(conv.lastActiveTime) }}</span>
                </div>
              </div>
              <div v-if="results.length === 0" class="conv-search-empty">
                {{ t('conversation.noSearchResult') }}
              </div>
            </template>
            <div v-else class="conv-search-empty conv-search-hint">
              {{ t('conversation.searchPlaceholder') }}
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/useChatStore'
import { formatRelativeTime } from '@/utils/time'

const props = defineProps<{
  /** 弹框是否打开 */
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  /** 用户点选或回车选中某个历史会话 */
  (e: 'select', conversationId: string): void
}>()

const { t } = useI18n()
const chatStore = useChatStore()

/** 搜索关键词 */
const keyword = ref('')

/** 键盘高亮的结果索引 */
const activeIndex = ref(0)

/** 输入框引用（打开时自动聚焦） */
const inputRef = ref<HTMLInputElement | null>(null)

/** 搜索结果：按标题关键词过滤（与侧栏列表同一匹配规则） */
const results = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return []
  return chatStore.conversations.filter(conv =>
    (conv.title || '').toLowerCase().includes(kw)
  )
})

/** 关键词变化时重置键盘高亮位置 */
watch(keyword, () => {
  activeIndex.value = 0
})

/** 打开时聚焦输入框；关闭时清空状态，保证下次打开是全新搜索 */
watch(() => props.open, (open) => {
  if (open) {
    nextTick(() => {
      inputRef.value?.focus()
    })
  } else {
    keyword.value = ''
    activeIndex.value = 0
  }
})

/** 关闭弹框 */
function close(): void {
  emit('update:open', false)
}

/** 键盘上下移动高亮项 */
function moveActive(delta: number): void {
  if (results.value.length === 0) return
  const next = activeIndex.value + delta
  activeIndex.value = Math.max(0, Math.min(next, results.value.length - 1))
}

/** Enter 选中当前高亮项 */
function selectActive(): void {
  const conv = results.value[activeIndex.value]
  if (conv) {
    select(conv.conversationId)
  }
}

/** 点击/回车选中搜索结果：通知父组件切换会话并关闭弹框 */
function select(conversationId: string): void {
  emit('select', conversationId)
  close()
}
</script>

<style scoped>
/* 全屏遮罩 + 顶部居中面板（DeepSeek 风格） */
.conv-search-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(2px);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 12vh 24px 24px;
}

.conv-search-panel {
  width: 600px;
  max-width: 100%;
  background: var(--theme-surface-elevated, #fff);
  border-radius: 14px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.18);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.conv-search-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--theme-border, #e5e7eb);
}

.conv-search-icon {
  color: var(--muted);
  flex-shrink: 0;
  display: flex;
}

.conv-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: var(--theme-text, #111);
}

.conv-search-input::placeholder {
  color: var(--muted);
}

.conv-search-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: var(--muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background var(--transition-fast, 150ms), color var(--transition-fast, 150ms);
}

.conv-search-close:hover {
  background: var(--theme-surface-hover, #f3f4f6);
  color: var(--theme-text, #111);
}

.conv-search-results {
  max-height: 340px;
  overflow-y: auto;
  padding: 6px;
}

.conv-search-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
}

.conv-search-item.active {
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
}

.conv-search-item-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.conv-search-item-title {
  font-size: 13.5px;
  color: var(--theme-text, #111);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-search-item-time {
  font-size: 12px;
  color: var(--muted);
  flex-shrink: 0;
}

.conv-search-empty {
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}

.conv-search-hint {
  padding: 18px 0 22px;
}

/* 弹框进出场动画 */
.search-fade-enter-active,
.search-fade-leave-active {
  transition: opacity 0.18s ease;
}

.search-fade-enter-from,
.search-fade-leave-to {
  opacity: 0;
}
</style>
