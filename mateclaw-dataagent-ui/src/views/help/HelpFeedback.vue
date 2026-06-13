<template>
  <div class="help-feedback">
    <div class="feedback-section">
      <div class="feedback-title">{{ t('helpCenter.feedbackRating') }}</div>
      <div class="feedback-desc">{{ t('helpCenter.feedbackRatingDesc') }}</div>
      <div class="feedback-rating">
        <el-rate
          v-model="rating"
          :max="5"
          show-text
          :texts="ratingTexts"
          size="large"
        />
      </div>
      <div class="feedback-summary" v-if="summary && summary.totalFeedbacks > 0">
        <span class="feedback-avg">
          {{ t('helpCenter.feedbackAverageRating') }}：
          <strong>{{ summary.averageRating }}</strong>
        </span>
        <span class="feedback-total">
          {{ t('helpCenter.feedbackTotalCount', { count: summary.totalFeedbacks }) }}
        </span>
      </div>
    </div>
    <div class="feedback-section">
      <div class="feedback-title">{{ t('helpCenter.feedbackSuggestion') }}</div>
      <el-input
        v-model="suggestion"
        type="textarea"
        :rows="3"
        :placeholder="t('helpCenter.feedbackSuggestionPlaceholder')"
      />
      <el-button
        type="primary"
        size="small"
        class="feedback-submit"
        :disabled="!rating && !suggestion"
        @click="handleSubmit"
      >
        {{ t('helpCenter.feedbackSubmit') }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { HelpFeedbackSummary } from '@/types'
import * as helpApi from '@/api/help-center'

const { t } = useI18n()

const props = defineProps<{
  documentId: string
  summary: HelpFeedbackSummary | null
}>()

const emit = defineEmits<{
  (e: 'submitted'): void
}>()

/** 评分 */
const rating = ref(0)
/** 改进建议 */
const suggestion = ref('')
/** 评分文字 */
const ratingTexts = ['很差', '较差', '一般', '有帮助', '非常有帮助']

/** 提交反馈 */
async function handleSubmit(): Promise<void> {
  if (!rating.value && !suggestion.value) {
    return
  }
  try {
    await helpApi.submitFeedback(props.documentId, {
      rating: rating.value || undefined,
      suggestion: suggestion.value || undefined,
    })
    ElMessage.success(t('helpCenter.feedbackSuccess'))
    rating.value = 0
    suggestion.value = ''
    emit('submitted')
  } catch {
    // 错误已在拦截器处理
  }
}

/** 重置表单 */
watch(() => props.documentId, () => {
  rating.value = 0
  suggestion.value = ''
})
</script>

<style scoped>
.help-feedback {
  border-top: 1px solid #f0f2f5;
  padding: 24px 0 0;
  margin-top: 32px;
}

.feedback-section {
  margin-bottom: 20px;
}

.feedback-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 8px;
}

.feedback-desc {
  font-size: 13px;
  color: #86909c;
  margin-bottom: 12px;
}

.feedback-rating {
  margin-bottom: 8px;
}

.feedback-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: #86909c;
  margin-top: 8px;
}

.feedback-avg strong {
  color: #f05a23;
  font-size: 16px;
}

.feedback-submit {
  margin-top: 12px;
}
</style>
