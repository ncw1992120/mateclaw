<template>
  <div class="skill-builder">
    <div class="left-header">
      <span class="icon">⚙</span>
      <span class="left-title">{{ t('skillBuilder.title') }}</span>
    </div>
    <div class="left-sub">{{ t('skillBuilder.subtitle') }}</div>

    <div class="step-list">
      <div class="step-line"></div>

      <!-- Step 1: Theme -->
      <div class="step">
        <div class="step-dot"></div>
        <div class="step-label">1. {{ t('skillBuilder.stepTheme') }}</div>
        <select v-model="selectedTheme" class="step-select" @change="onThemeChange">
          <option v-for="(theme, key) in themes" :key="key" :value="key">{{ theme.label }}</option>
        </select>
      </div>

      <!-- Step 2: Dimensions -->
      <div class="step">
        <div class="step-dot"></div>
        <div class="step-label">2. {{ t('skillBuilder.stepDimension') }}</div>
        <div class="ms-wrap">
          <div
            class="ms-trigger"
            :class="selectedDimensions.length ? 'has-value' : 'empty'"
            @click.stop="dimPanelOpen = !dimPanelOpen"
          >
            {{ selectedDimensions.length ? `${t('skillBuilder.selected')} ${selectedDimensions.length} ${t('skillBuilder.items')}` : t('skillBuilder.pleaseSelect') }}
          </div>
          <div class="ms-panel" :class="{ open: dimPanelOpen }">
            <label v-for="dim in currentThemeDimensions" :key="dim" class="ms-option">
              <input type="checkbox" :checked="selectedDimensions.includes(dim)" :value="dim" @change="toggleDimension(dim)" />
              {{ dim }}
            </label>
          </div>
          <div class="ms-selected">
            <span v-for="dim in selectedDimensions" :key="dim" class="ms-tag">
              {{ dim }}
              <span class="ms-x" @click.stop="removeDimension(dim)">&times;</span>
            </span>
          </div>
        </div>
      </div>

      <!-- Step 3: Indicators -->
      <div class="step">
        <div class="step-dot"></div>
        <div class="step-label">3. {{ t('skillBuilder.stepIndicator') }}</div>
        <div class="ms-wrap">
          <div
            class="ms-trigger"
            :class="selectedIndicators.length ? 'has-value' : 'empty'"
            @click.stop="indPanelOpen = !indPanelOpen"
          >
            {{ selectedIndicators.length ? `${t('skillBuilder.selected')} ${selectedIndicators.length} ${t('skillBuilder.items')}` : t('skillBuilder.pleaseSelect') }}
          </div>
          <div class="ms-panel" :class="{ open: indPanelOpen }">
            <label v-for="ind in currentThemeIndicators" :key="ind" class="ms-option">
              <input type="checkbox" :checked="selectedIndicators.includes(ind)" :value="ind" @change="toggleIndicator(ind)" />
              {{ ind }}
            </label>
          </div>
          <div class="ms-selected">
            <span v-for="ind in selectedIndicators" :key="ind" class="ms-tag">
              {{ ind }}
              <span class="ms-x" @click.stop="removeIndicator(ind)">&times;</span>
            </span>
          </div>
        </div>
      </div>

      <!-- Step 4: Template -->
      <div class="step">
        <div class="step-dot"></div>
        <div class="step-label">4. {{ t('skillBuilder.stepTemplate') }}</div>
        <select v-model="selectedTemplate" class="step-select">
          <option value="trend">{{ t('skillBuilder.tplTrend') }}</option>
          <option value="compare">{{ t('skillBuilder.tplCompare') }}</option>
          <option value="attribution">{{ t('skillBuilder.tplAttribution') }}</option>
          <option value="anomaly">{{ t('skillBuilder.tplAnomaly') }}</option>
        </select>
      </div>

      <!-- Step 5: Time Range -->
      <div class="step">
        <div class="step-dot"></div>
        <div class="step-label">5. {{ t('skillBuilder.stepTimeRange') }}</div>
        <div class="time-wrap">
          <div class="time-trigger" @click.stop="timeDropOpen = !timeDropOpen">
            <span>{{ timeDisplay }}</span>
            <span class="arrow">▾</span>
          </div>
          <div class="time-dropdown" :class="{ open: timeDropOpen }">
            <div
              v-for="preset in timePresets"
              :key="preset.key"
              class="time-preset"
              :class="{ active: selectedTimeKey === preset.key }"
              @click="pickTime(preset.key)"
            >
              {{ preset.label }}
              <span class="check">✓</span>
            </div>
            <div class="time-custom">
              <div style="font-size:10px;color:var(--muted);margin-bottom:4px">{{ t('skillBuilder.customDateRange') }}</div>
              <div class="time-custom-row">
                <input type="date" v-model="customDateStart" @change="pickTimeCustom" />
                <span class="sep">{{ t('skillBuilder.to') }}</span>
                <input type="date" v-model="customDateEnd" @change="pickTimeCustom" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <button class="btn-cta" @click="previewSkill">{{ t('skillBuilder.generateAndPreview') }}</button>
    <div style="text-align:center;margin-top:8px">
      <button class="collapse-link" @click="emit('collapse')">◀ {{ t('skillBuilder.collapse') }}</button>
    </div>

    <!-- Skill Preview Modal -->
    <Teleport to="body">
      <div class="skill-modal-overlay" :class="{ show: skillModalVisible }" @click.self="skillModalVisible = false">
        <div class="skill-modal">
          <div class="skill-modal-header">
            <h3>{{ t('skillBuilder.previewTitle') }}</h3>
            <button class="skill-modal-close" @click="skillModalVisible = false">&times;</button>
          </div>
          <div class="skill-modal-body">
            <pre>{{ skillPreviewContent }}</pre>
          </div>
          <div class="skill-modal-footer">
            <button class="btn-cancel" @click="skillModalVisible = false">{{ t('common.cancel') }}</button>
            <button class="btn-apply" @click="applySkill">{{ t('skillBuilder.applySkill') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SkillTheme } from '@/types'

const { t } = useI18n()
const emit = defineEmits<{
  collapse: []
}>()

/** 主题配置 */
const themes: Record<string, SkillTheme> = {
  aum: { label: 'AUM 趋势分析', indicators: ['AUM', '日均 AUM', 'AUM 环比增长率', '基金保有规模'], dimensions: ['业务条线', '产品类型', '客户分层', '渠道'], template: 'trend' },
  growth: { label: '用户增长分析', indicators: ['新增客户数', '活跃客户数', '流失客户数', '净增长率'], dimensions: ['客户分层', '渠道', '区域', '注册来源'], template: 'compare' },
  commission: { label: '佣金收入分析', indicators: ['佣金收入', '交易量', '佣金率', '客均佣金'], dimensions: ['产品类型', '业务条线', '渠道', '客户分层'], template: 'compare' },
  activity: { label: '客户活跃度分析', indicators: ['活跃客户数', '登录频次', '交易频次', '功能使用率'], dimensions: ['客户分层', '渠道', '产品类型', '区域'], template: 'attribution' },
}

/** 时间预设 */
const timePresets = [
  { key: 'today', label: '今天' },
  { key: 'd7', label: '最近 7 天' },
  { key: 'd30', label: '最近 30 天' },
  { key: 'd90', label: '最近 90 天' },
  { key: 'mtd', label: '本月至今' },
  { key: 'lm', label: '上月' },
  { key: 'q1', label: '2026 年 Q1' },
  { key: 'q2', label: '2026 年 Q2' },
]

/** 模板标签映射 */
const tplLabels: Record<string, string> = {
  trend: '基础趋势分析',
  compare: '多维对比分析',
  attribution: '归因分析',
  anomaly: '异常检测',
}

/** 选中主题 */
const selectedTheme = ref('aum')
/** 选中维度 */
const selectedDimensions = ref<string[]>(['业务条线', '产品类型'])
/** 选中指标 */
const selectedIndicators = ref<string[]>(['AUM', '日均 AUM'])
/** 选中模板 */
const selectedTemplate = ref('trend')
/** 选中时间范围 key */
const selectedTimeKey = ref('d30')
/** 自定义日期起 */
const customDateStart = ref('2026-04-22')
/** 自定义日期止 */
const customDateEnd = ref('2026-05-21')

/** 面板开关状态 */
const dimPanelOpen = ref(false)
const indPanelOpen = ref(false)
const timeDropOpen = ref(false)
const skillModalVisible = ref(false)

/** 当前主题可用维度 */
const currentThemeDimensions = computed(() => themes[selectedTheme.value]?.dimensions || [])
/** 当前主题可用指标 */
const currentThemeIndicators = computed(() => themes[selectedTheme.value]?.indicators || [])

/** 时间范围显示文本 */
const timeDisplay = computed(() => {
  if (selectedTimeKey.value === 'custom') {
    return `${customDateStart.value} ~ ${customDateEnd.value}`
  }
  const preset = timePresets.find(p => p.key === selectedTimeKey.value)
  return preset?.label || '最近 30 天'
})

/** 主题切换 */
function onThemeChange(): void {
  const theme = themes[selectedTheme.value]
  if (theme) {
    selectedDimensions.value = theme.dimensions.slice(0, 2)
    selectedIndicators.value = theme.indicators.slice(0, 2)
    selectedTemplate.value = theme.template
  }
}

/** 切换维度选中 */
function toggleDimension(dim: string): void {
  const idx = selectedDimensions.value.indexOf(dim)
  if (idx >= 0) {
    selectedDimensions.value.splice(idx, 1)
  } else {
    selectedDimensions.value.push(dim)
  }
}

/** 移除维度标签 */
function removeDimension(dim: string): void {
  const idx = selectedDimensions.value.indexOf(dim)
  if (idx >= 0) selectedDimensions.value.splice(idx, 1)
}

/** 切换指标选中 */
function toggleIndicator(ind: string): void {
  const idx = selectedIndicators.value.indexOf(ind)
  if (idx >= 0) {
    selectedIndicators.value.splice(idx, 1)
  } else {
    selectedIndicators.value.push(ind)
  }
}

/** 移除指标标签 */
function removeIndicator(ind: string): void {
  const idx = selectedIndicators.value.indexOf(ind)
  if (idx >= 0) selectedIndicators.value.splice(idx, 1)
}

/** 选择时间预设 */
function pickTime(key: string): void {
  selectedTimeKey.value = key
  timeDropOpen.value = false
}

/** 选择自定义时间 */
function pickTimeCustom(): void {
  selectedTimeKey.value = 'custom'
  timeDropOpen.value = false
}

/** 生成 Skill 预览内容 */
const skillPreviewContent = computed(() => {
  const theme = themes[selectedTheme.value]
  if (!theme) return ''
  const tpl = tplLabels[selectedTemplate.value] || '基础趋势分析'
  return `# ${t('skillBuilder.analysisTask')}：${theme.label}

## ${t('skillBuilder.indicatorSection')}
${selectedIndicators.value.map(s => `- ${s}`).join('\n') || `- (${t('skillBuilder.notSelected')})`}

## ${t('skillBuilder.dimensionSection')}
${selectedDimensions.value.map(s => `- ${s}`).join('\n') || `- (${t('skillBuilder.notSelected')})`}

## ${t('skillBuilder.caliberSection')}
- ${t('skillBuilder.excludeMoneyFund')}
- ${t('skillBuilder.momCycle30d')}

## ${t('skillBuilder.timeRangeSection')}
- ${timeDisplay.value}

## ${t('skillBuilder.templateSection')}
- ${tpl}

## ${t('skillBuilder.outputReq')}
- ${t('skillBuilder.outputReqDimTrend')}
- ${t('skillBuilder.outputReqMomHighlight')}
- ${t('skillBuilder.outputReqAnomalyMark')}`
})

/** 预览 Skill */
function previewSkill(): void {
  skillModalVisible.value = true
}

/** 应用 Skill */
function applySkill(): void {
  skillModalVisible.value = false
}
</script>

<style scoped>
.skill-builder {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.left-header {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}

.left-header .icon {
  font-size: 16px;
  color: var(--main-orange);
  margin-right: 6px;
}

.left-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--main-orange);
}

.left-sub {
  font-size: 10px;
  color: var(--muted);
  margin-bottom: 16px;
}

.step-list {
  position: relative;
  padding-left: 24px;
  flex: 1;
  overflow-y: auto;
}

.step-line {
  position: absolute;
  left: 6px;
  top: 10px;
  width: 2px;
  height: calc(100% - 20px);
  background: var(--light-orange);
}

.step {
  position: relative;
  margin-bottom: 20px;
}

.step-dot {
  position: absolute;
  left: -24px;
  top: 3px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--main-orange);
}

.step-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--blue-grey);
  margin-bottom: 6px;
}

.step-select {
  width: 100%;
  height: 34px;
  border-radius: 6px;
  border: 1px solid var(--light-grey);
  background: #fff;
  padding: 0 10px;
  font-size: 12px;
  color: var(--dark-text);
  appearance: auto;
  outline: none;
  font-family: inherit;
}

.ms-wrap {
  position: relative;
  margin-bottom: 4px;
}

.ms-trigger {
  width: 100%;
  height: 34px;
  border-radius: 6px;
  border: 1px solid var(--light-grey);
  background: #fff;
  padding: 0 28px 0 10px;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  outline: none;
  position: relative;
  font-family: inherit;
}

.ms-trigger::after {
  content: '▾';
  position: absolute;
  right: 10px;
  color: var(--muted);
  font-size: 11px;
}

.ms-trigger.has-value {
  color: var(--dark-text);
}

.ms-trigger.empty {
  color: var(--muted);
}

.ms-panel {
  display: none;
  position: absolute;
  top: 36px;
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid var(--light-grey);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  z-index: 20;
  max-height: 180px;
  overflow-y: auto;
}

.ms-panel.open {
  display: block;
}

.ms-option {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--dark-text);
  cursor: pointer;
}

.ms-option:hover {
  background: var(--very-light-orange);
}

.ms-option input[type="checkbox"] {
  accent-color: var(--main-orange);
}

.ms-selected {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.ms-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 10px;
  background: var(--light-orange);
  border: 1px solid var(--main-orange);
  font-size: 11px;
  color: var(--dark-orange);
}

.ms-x {
  cursor: pointer;
  font-size: 10px;
  color: var(--main-orange);
  font-weight: 700;
}

.ms-x:hover {
  color: #c00;
}

.time-wrap {
  position: relative;
}

.time-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid var(--light-grey);
  background: #fff;
  cursor: pointer;
  font-size: 11px;
  color: var(--dark-text);
  user-select: none;
}

.time-trigger:hover {
  border-color: var(--main-orange);
}

.time-trigger .arrow {
  font-size: 8px;
  color: var(--muted);
  margin-left: auto;
}

.time-dropdown {
  display: none;
  position: absolute;
  top: 34px;
  left: 0;
  min-width: 200px;
  background: #fff;
  border: 1px solid var(--light-grey);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 50;
  padding: 4px 0;
}

.time-dropdown.open {
  display: block;
}

.time-preset {
  padding: 6px 12px;
  font-size: 11px;
  color: var(--dark-text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.time-preset:hover {
  background: var(--light-orange);
  color: var(--dark-orange);
}

.time-preset.active {
  color: var(--main-orange);
  font-weight: 600;
}

.time-preset .check {
  font-size: 10px;
  color: var(--main-orange);
  visibility: hidden;
}

.time-preset.active .check {
  visibility: visible;
}

.time-custom {
  padding: 6px 12px;
  border-top: 1px solid var(--light-grey);
  margin-top: 4px;
}

.time-custom-row {
  display: flex;
  gap: 4px;
  align-items: center;
}

.time-custom-row input {
  flex: 1;
  height: 26px;
  border-radius: 4px;
  border: 1px solid var(--light-grey);
  padding: 0 4px;
  font-size: 10px;
  color: var(--dark-text);
  font-family: inherit;
}

.time-custom-row .sep {
  font-size: 9px;
  color: var(--muted);
}

.btn-cta {
  width: 100%;
  height: 44px;
  border-radius: 8px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  margin-top: 16px;
  box-shadow: 0 2px 8px rgba(240, 90, 35, 0.3);
  cursor: pointer;
  font-family: inherit;
  flex-shrink: 0;
}

.btn-cta:hover {
  background: var(--dark-orange);
}

.collapse-link {
  background: none;
  border: none;
  font-size: 11px;
  color: var(--muted);
  cursor: pointer;
  font-family: inherit;
}

.skill-modal-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 100;
  align-items: center;
  justify-content: center;
}

.skill-modal-overlay.show {
  display: flex;
}

.skill-modal {
  background: #fff;
  border-radius: 12px;
  width: 520px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.2);
}

.skill-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--light-grey);
}

.skill-modal-header h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--dark-orange);
  margin: 0;
}

.skill-modal-close {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: var(--lighter-grey);
  font-size: 14px;
  color: var(--muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: inherit;
}

.skill-modal-close:hover {
  background: var(--light-grey);
}

.skill-modal-body {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
}

.skill-modal-body pre {
  background: var(--near-white);
  border: 1px solid var(--light-grey);
  border-radius: 8px;
  padding: 14px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--body-text);
  font-family: "SF Mono", "Menlo", "Consolas", monospace;
  white-space: pre-wrap;
  margin: 0;
}

.skill-modal-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--light-grey);
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.skill-modal-footer button {
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  font-family: inherit;
}

.btn-cancel {
  background: var(--lighter-grey);
  color: var(--body-text);
}

.btn-apply {
  background: var(--main-orange);
  color: #fff;
}
</style>
