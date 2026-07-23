<template>
  <div class="chat-view">
    <!-- 消息区域（含悬浮「回到底部」按钮），高度自适应，占满输入区上方空间 -->
    <div class="chat-main">
    <!-- Chat Area -->
    <div ref="chatAreaRef" class="chat-area">
      <!-- Empty State -->
      <div v-if="chatStore.messages.length === 0" class="empty-state">
        <div class="empty-avatar">
          <svg class="empty-avatar__icon" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
            <line x1="32" y1="12" x2="32" y2="20" stroke="white" stroke-width="3" stroke-linecap="round"/>
            <circle cx="32" cy="9" r="3.5" fill="white"/>
            <rect x="14" y="20" width="36" height="28" rx="9" fill="white"/>
            <rect x="19" y="27" width="26" height="14" rx="5" fill="var(--main-orange)"/>
            <circle cx="26" cy="34" r="2.5" fill="white"/>
            <circle cx="38" cy="34" r="2.5" fill="white"/>
          </svg>
        </div>
        <div class="empty-greeting">
          <h1 class="empty-greeting__title">{{ greetingText }}</h1>
          <p class="empty-greeting__subtitle">{{ t('chat.emptySubtitle') }}</p>
        </div>
        <!-- 智能问数快捷菜单 -->
        <div class="smart-ask-menu">
          <span
            v-for="item in smartAskMenuItems"
            :key="item.key"
            class="smart-ask-chip"
            @click="handleSmartAskMenu(item)"
          >
            <span class="chip-icon" v-html="item.icon"></span>
            <span class="chip-label">{{ t(item.label) }}</span>
          </span>
        </div>
      </div>

      <!-- Messages -->
      <template v-for="(msg, index) in chatStore.messages" :key="index">
        <!-- User Message -->
        <div v-if="msg.role === 'user'" :ref="(el) => setUserMsgRef(el as HTMLElement | null, index)" class="msg user">
          <div class="user-content-wrapper">
            <div class="bubble user-bubble">{{ msg.content }}</div>
            <!-- 附件展示 -->
            <div v-if="getUserAttachments(msg)" class="msg-attachments">
              <template v-for="(att, aIdx) in getUserAttachments(msg)" :key="aIdx">
                <span v-if="att.contentType?.startsWith('image/')" class="msg-attachment msg-attachment--image" @click="previewImage(att.url)">
                  <img :src="att.url" :alt="att.fileName" class="msg-attachment__img" />
                </span>
                <span v-else class="msg-attachment">
                  <svg class="icon-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"/></svg>
                  {{ att.fileName }}
                </span>
              </template>
            </div>
            <!-- 用户消息操作栏（气泡外左下角） -->
            <div class="msg-actions msg-actions--user">
              <span class="msg-time">{{ formatMsgTime(msg.timestamp) }}</span>
              <button
                class="action-btn"
                :class="{ copied: copyState[index] === 'copied' }"
                type="button"
                :title="copyState[index] === 'copied' ? t('chat.copied') : t('chat.copy')"
                @click="handleCopy(index, msg.content)"
              >
                <el-icon v-if="copyState[index] !== 'copied'"><CopyDocument /></el-icon>
                <el-icon v-else><Select /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <!-- AI Message -->
        <div v-else class="msg ai" :data-msg-index="index">
          <div class="ai-content-wrapper">
            <div class="bubble ai-bubble">
              <!-- Token & model info (右上角) -->
              <div v-if="getTokenInfo(msg)" class="meta-header">
                <span class="meta-token">{{ getTokenInfo(msg) }}</span>
              </div>

              <!-- Execution Process Card: aggregates thinking + tool_call segments -->
              <div
                v-if="hasExecutionProcess(msg)"
                class="seg-execution"
                :class="{ 'is-open': isExecutionProcessExpanded(index) }"
              >
                <button
                  class="seg-execution__toggle"
                  type="button"
                  @click="toggleExecutionProcessExpand(index)"
                >
                  <span class="seg-execution__label">{{ t('chat.executionProcess') }}</span>
                  <span class="seg-execution__count">{{ getExecutionProcessSummary(msg) }}</span>
                  <span
                    class="seg-execution__arrow"
                    :class="{ 'is-open': isExecutionProcessExpanded(index) }"
                  ><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 6 15 12 9 18"/></svg></span>
                </button>
                <Transition name="seg-slide">
                  <div v-if="isExecutionProcessExpanded(index)" class="seg-execution__body">
                    <template v-for="(seg, segIdx) in getExecutionProcessSegments(msg)" :key="`exec-${segIdx}`">
                      <!-- thinking 类型 -->
                      <div
                        v-if="seg.type === 'thinking'"
                        class="seg-narration seg-thinking"
                      >
                        <button
                          class="seg-narration__toggle"
                          type="button"
                          @click="toggleNarrationExpand(index, segIdx)"
                        >
                          <span class="seg-narration__label">{{ t('chat.executionStep') }}</span>
                          <span
                            class="seg-narration__arrow"
                            :class="{ 'is-open': isNarrationExpanded(index, segIdx) }"
                          ><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg></span>
                        </button>
                        <Transition name="seg-slide">
                          <div
                            v-if="isNarrationExpanded(index, segIdx)"
                            class="seg-narration__body"
                            v-html="renderMessageText((seg.thinkingText as string) || '', index)"
                          />
                        </Transition>
                      </div>

                      <!-- tool_call 类型 -->
                      <div
                        v-else-if="seg.type === 'tool_call'"
                        class="seg-tool"
                        :class="{
                          'is-running': seg.status === 'running',
                          'is-success': seg.status === 'completed' && seg.toolSuccess !== false,
                          'is-error': seg.status === 'error' || seg.toolSuccess === false,
                        }"
                      >
                        <div class="seg-tool__header" @click="toggleToolExpand(segIdx)">
                          <span class="seg-tool__status">
                            <span v-if="seg.status === 'running'" class="spin-icon">⟳</span>
                            <span v-else-if="seg.status === 'completed' && seg.toolSuccess !== false">✓</span>
                            <span v-else>✕</span>
                          </span>
                          <span class="seg-tool__name">{{ seg.toolName || seg.name }}</span>
                          <span v-if="truncateArgs(seg.toolArgs as string)" class="seg-tool__args">{{ truncateArgs(seg.toolArgs as string) }}</span>
                          <span
                            v-if="seg.toolArgs != null || seg.toolResult != null"
                            class="seg-tool__arrow"
                            :class="{ 'is-open': expandedTools.has(segIdx) }"
                          ><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg></span>
                        </div>
                        <Transition name="seg-slide">
                          <div v-if="expandedTools.has(segIdx) && (seg.toolArgs != null || seg.toolResult != null)" class="seg-tool__body">
                            <div v-if="seg.toolArgs != null" class="seg-tool__section">
                              <div class="seg-tool__section-title">{{ t('chat.toolRequestParams') }}</div>
                              <pre>{{ formatToolBody(seg.toolArgs as string) }}</pre>
                            </div>
                            <div v-if="seg.toolResult != null" class="seg-tool__section">
                              <div class="seg-tool__section-title">{{ t('chat.toolResponseParams') }}</div>
                              <pre>{{ formatToolBody(seg.toolResult as string) }}</pre>
                            </div>
                          </div>
                        </Transition>
                      </div>

                      <!-- content 类型（中间叙述） -->
                      <div
                        v-else-if="seg.type === 'content' && !isFinalContentSegment(msg, segIdx)"
                        class="seg-narration"
                      >
                        <button
                          class="seg-narration__toggle"
                          type="button"
                          @click="toggleNarrationExpand(index, segIdx)"
                        >
                          <span class="seg-narration__label">{{ t('chat.executionStep') }}</span>
                          <span
                            class="seg-narration__arrow"
                            :class="{ 'is-open': isNarrationExpanded(index, segIdx) }"
                          ><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg></span>
                        </button>
                        <Transition name="seg-slide">
                          <div
                            v-if="isNarrationExpanded(index, segIdx)"
                            class="seg-narration__body"
                            v-html="renderMessageText((seg.text as string) || '', index)"
                          />
                        </Transition>
                      </div>
                    </template>
                  </div>
                </Transition>
              </div>

              <!-- Final answer (优先使用最后一个 content segment 作为最终答案；兼容历史消息回退到 msg.content) -->
              <div v-if="getFinalAnswer(msg)" class="msg-text" v-html="renderMessageText(getFinalAnswer(msg), index)" />

              <!-- Streaming cursor -->
              <span
                v-if="chatStore.isStreaming && index === chatStore.messages.length - 1 && !msg.content"
                class="streaming-cursor"
              />
            </div>
            <!-- AI 消息操作栏（气泡外右下角） -->
            <div class="msg-actions msg-actions--ai">
              <span class="msg-time">{{ formatMsgTime(msg.timestamp) }}</span>
              <button
                class="action-btn"
                :class="{ copied: copyState[index] === 'copied' }"
                type="button"
                :title="copyState[index] === 'copied' ? t('chat.copied') : t('chat.copy')"
                @click="handleCopy(index, msg.content)"
              >
                <el-icon v-if="copyState[index] !== 'copied'"><CopyDocument /></el-icon>
                <el-icon v-else><Select /></el-icon>
              </button>
              <button
                v-if="!chatStore.isStreaming"
                class="action-btn"
                type="button"
                :title="t('chat.regenerate')"
                @click="handleRegenerate(index)"
              >
                <el-icon><RefreshRight /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <!-- Rich Cards for this message -->
        <template v-if="msg.cards && msg.cards.length">
          <template v-for="(card, cardIdx) in msg.cards" :key="`${index}-${cardIdx}`">
            <!-- QueryPlan Card -->
            <div v-if="card.type === 'queryplan'" class="qp-box">
              <div class="qp-accent"></div>
              <div class="qp-title">{{ t('chat.queryPlanTitle') }}</div>
              <div
                v-for="(val, key) in (card.data as QueryPlanData)"
                :key="key"
                class="qp-row"
              >
                <span class="qp-label">{{ queryPlanLabels[key as string] || key }}</span>
                <span class="qp-val">{{ val }}</span>
                <button class="qp-modify" @click="handleModify(key as string)">{{ t('chat.modify') }}</button>
              </div>
              <button
                class="qp-confirm"
                :class="{ confirmed: queryPlanConfirmed[`${index}-${cardIdx}`] }"
                :disabled="queryPlanConfirmed[`${index}-${cardIdx}`]"
                @click="confirmQueryPlan(index, cardIdx)"
              >
                {{ queryPlanConfirmed[`${index}-${cardIdx}`] ? `${t('chat.confirmed')} ✓` : t('chat.confirm') }}
              </button>
              <div style="clear:both"></div>
            </div>

            <!-- Insight Bar -->
            <div v-else-if="card.type === 'insight'" class="insight-bar">
              <svg class="icon-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18h6"/><path d="M10 22h4"/><path d="M15.09 14a6 6 0 0 0 1.41-8.94 6 6 0 0 0-9.5 7.94"/><path d="M9.5 14h5"/></svg>
              AI {{ t('chat.insight') }}：{{ card.data }}
            </div>

            <!-- Chart Card -->
            <div v-else-if="card.type === 'chart'" class="chart-box">
              <div class="chart-title">{{ (card.data as ChartCardData).title }}</div>
              <div :ref="(el) => setChartRef(el as HTMLElement, index, cardIdx)" class="mid-chart"></div>
            </div>

            <!-- ECharts Option Card（后端返回标准 ECharts option 时直接渲染） -->
            <div v-else-if="card.type === 'echarts'" class="echarts-box">
              <div v-if="(card.data as EChartsOptionData).title" class="echarts-title">{{ (card.data as EChartsOptionData).title }}</div>
              <div :ref="(el) => setEChartsRef(el as HTMLElement, index, cardIdx)" class="echarts-chart"></div>
            </div>

            <!-- Clarify Card -->
            <div v-else-if="card.type === 'clarify'" class="clarify-card">
              <div class="clarify-title">
                <svg class="icon-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                {{ (card.data as ClarifyData).title }}
              </div>
              <div class="clarify-desc">{{ (card.data as ClarifyData).desc }}</div>
              <div class="clarify-options">
                <label
                  v-for="(opt, optIdx) in (card.data as ClarifyData).options"
                  :key="optIdx"
                  class="clarify-opt"
                  :class="{ selected: clarifySelected[`${index}-${cardIdx}`] === optIdx }"
                  @click="clarifySelected[`${index}-${cardIdx}`] = optIdx"
                >
                  <input
                    type="radio"
                    :name="`clarify-${index}-${cardIdx}`"
                    :checked="clarifySelected[`${index}-${cardIdx}`] === optIdx"
                  />
                  {{ opt.label }}
                  <span v-if="opt.recommend" class="recommend">{{ t('chat.recommended') }}</span>
                </label>
              </div>
              <button
                class="clarify-confirm"
                :class="{ confirmed: clarifyConfirmed[`${index}-${cardIdx}`] }"
                :disabled="clarifyConfirmed[`${index}-${cardIdx}`]"
                @click="confirmClarify(index, cardIdx)"
              >
                {{ clarifyConfirmed[`${index}-${cardIdx}`] ? `${t('chat.confirmed')} ✓` : t('chat.confirmSelection') }}
              </button>
            </div>

            <!-- Dashboard Preview Card -->
            <div v-else-if="card.type === 'dashboard'" class="dash-card">
              <div class="dash-card-title">
                <svg class="icon-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="8" y1="15" x2="8" y2="17"/><line x1="12" y1="13" x2="12" y2="17"/><line x1="16" y1="11" x2="16" y2="17"/></svg>
                {{ t('chat.dashboardGenerated') }}
              </div>
              <div class="dash-kpi-row">
                <div v-for="(kpi, kpiIdx) in (card.data as DashboardCardData).kpis" :key="kpiIdx" class="dash-kpi">
                  <div class="dash-kpi-val">{{ kpi.val }}</div>
                  <div class="dash-kpi-name">
                    {{ kpi.name }}
                    <span :style="{ color: kpi.up ? 'var(--main-orange)' : 'var(--mid-grey)' }">{{ kpi.chg }}</span>
                  </div>
                </div>
              </div>
              <div class="dash-link" @click="$emit('openDashboard')">{{ t('chat.viewDashboard') }} →</div>
            </div>

            <!-- Followup Chips -->
            <div v-else-if="card.type === 'followup'" class="followup-chips">
              <span
                v-for="(chip, chipIdx) in (card.data as FollowupData)"
                :key="chipIdx"
                class="followup-chip"
                @click="handleFollowup(chip)"
              >
                {{ chip }}
              </span>
            </div>

            <!-- Recommended Questions -->
            <div v-else-if="card.type === 'recommended_questions'" class="recommended-questions" :class="{ 'is-collapsed': !isLatestRecommendedQuestions(index) && !expandedHistoryRecQuestions.has(`${index}-${cardIdx}`) }">
              <div class="recommended-questions__header" @click="!isLatestRecommendedQuestions(index) && toggleHistoryRecQuestion(`${index}-${cardIdx}`)">
                <svg class="icon-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                {{ t('chat.recommendedQuestions') }}
                <span v-if="!isLatestRecommendedQuestions(index) && !expandedHistoryRecQuestions.has(`${index}-${cardIdx}`)" class="recommended-questions__toggle">
                  {{ t('chat.expandRecQuestions', { count: (card.data as RecommendedQuestionData).questions.length }) }}
                </span>
                <span v-else-if="!isLatestRecommendedQuestions(index)" class="recommended-questions__toggle">
                  {{ t('chat.collapseRecQuestions') }}
                </span>
              </div>
              <div class="recommended-questions__list">
                <button
                  v-for="(question, qIdx) in (card.data as RecommendedQuestionData).questions"
                  :key="qIdx"
                  class="recommended-question-item"
                  @click="handleFollowup(question)"
                >
                  <svg class="recommended-question-item__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
                  <span class="recommended-question-item__text">{{ question }}</span>
                </button>
              </div>
            </div>

            <!-- Feedback -->
            <div v-else-if="card.type === 'feedback'" class="feedback">
              <span
                v-for="opt in feedbackOptions"
                :key="opt.key"
                :class="{ active: feedbackState[`${index}-${cardIdx}`] === opt.key }"
                @click="feedbackState[`${index}-${cardIdx}`] = opt.key"
              >
                <span class="feedback-icon" v-html="opt.icon"></span>{{ opt.label }}
              </span>
            </div>
          </template>
        </template>
      </template>

      <!-- Streaming cursor at end -->
      <div
        v-if="chatStore.isStreaming && chatStore.messages.length > 0 && chatStore.messages[chatStore.messages.length - 1]?.content"
        class="msg ai"
      >
        <span class="streaming-cursor-end" />
      </div>
    </div>

      <!-- 回到底部按钮 -->
      <transition name="scroll-btn-fade">
        <button
          v-if="showScrollToBottom"
          class="scroll-to-bottom"
          type="button"
          :title="t('chat.scrollToBottom')"
          :aria-label="t('chat.scrollToBottom')"
          @click="scrollToBottom(true)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14"/><path d="m19 12-7 7-7-7"/></svg>
        </button>
      </transition>

      <!-- 右侧快速导航栏：列出所有用户提问，悬浮显示内容，点击跳转到对应位置 -->
      <transition name="chat-nav-fade">
        <nav v-if="userQuestions.length > 1" class="chat-nav" :aria-label="t('chat.questionNav')">
          <button
            v-for="(q, qi) in userQuestions"
            :key="q.index"
            class="chat-nav__item"
            :class="{ 'is-active': activeQuestionIndex === q.index }"
            type="button"
            @click="scrollToMessage(q.index)"
          >
            <span class="chat-nav__label">
              <span class="chat-nav__seq">{{ qi + 1 }}</span>
              <span class="chat-nav__text">{{ q.content }}</span>
            </span>
            <span class="chat-nav__dot"></span>
          </button>
        </nav>
      </transition>
    </div>

    <!-- Datasource Selector Toolbar -->
    <div v-if="enabledDatasources.length > 0" class="ds-toolbar">
      <el-popover :width="320" trigger="click" placement="top-start" :persistent="false" :teleported="true">
        <template #reference>
          <button class="ds-trigger" :class="{ active: chatStore.selectedDatasourceIds.length > 0 }">
            <span class="ds-trigger-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h.09a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c0 .66.26 1.3.73 1.77.47.47 1.11.73 1.77.73H21a2 2 0 1 1 0 4h-.09c-.66 0-1.3.26-1.77.73-.47.47-.73 1.11-.73 1.77z"/></svg>
            </span>
            <span class="ds-trigger-text">{{ dsTriggerLabel }}</span>
            <span class="ds-trigger-arrow">▾</span>
          </button>
        </template>
        <div class="ds-popover">
          <div class="ds-popover-header">
            <span>{{ t('chat.datasourceScope') }}</span>
            <span
              v-if="chatStore.selectedDatasourceIds.length > 0"
              class="ds-popover-clear"
              @click="chatStore.selectedDatasourceIds = []"
            >{{ t('chat.clearDatasourceScope') }}</span>
          </div>
          <div class="ds-popover-list">
            <label
              v-for="ds in enabledDatasources"
              :key="ds.id"
              class="ds-popover-item"
              :class="{ checked: chatStore.selectedDatasourceIds.includes(ds.id) }"
              :title="ds.name"
            >
              <input
                type="checkbox"
                :checked="chatStore.selectedDatasourceIds.includes(ds.id)"
                @change="toggleDatasource(ds.id)"
              />
              <span class="ds-item-name">{{ ds.name }}</span>
              <span v-if="ds.sourceType" class="ds-item-type">{{ ds.sourceType }}</span>
            </label>
          </div>
        </div>
      </el-popover>
    </div>

    <!-- Input Bar -->
    <div class="input-bar">
      <!-- 附件预览区 -->
      <div v-if="pendingAttachments.length > 0" class="attachment-preview">
        <div v-for="(att, idx) in pendingAttachments" :key="idx" class="attachment-tag">
          <span v-if="att.contentType?.startsWith('image/')" class="attachment-tag__thumb">
            <img :src="att.url" :alt="att.fileName" class="attachment-tag__img" />
          </span>
          <span v-else class="attachment-tag__icon">
            <svg class="icon-inline" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"/></svg>
          </span>
          <span class="attachment-tag__name">{{ att.fileName }}</span>
          <button class="attachment-tag__remove" type="button" @click="removeAttachment(idx)">×</button>
        </div>
      </div>
      <div class="input-bar__card">
        <input ref="fileInputRef" type="file" multiple style="display:none" @change="handleFileChange" />
        <textarea
          v-model="inputMessage"
          class="chat-input"
          :placeholder="chatStore.isStreaming ? t('chat.generating') : t('chat.placeholder')"
          :disabled="chatStore.isStreaming"
          rows="1"
          @keydown="handleKeydown"
          @paste="handlePaste"
        />
        <div class="input-actions">
          <button class="btn-attach" :disabled="chatStore.isStreaming || isUploading" type="button" :title="t('chat.uploadAttachment')" @click="handleFileSelect">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"/>
            </svg>
          </button>
          <button class="btn-optimize" :disabled="!inputMessage.trim() || chatStore.isStreaming || isOptimizing" type="button" :title="t('chat.optimizePrompt')" @click="handleOptimize">
            <span v-if="isOptimizing" class="spin-icon">⟳</span>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 3l1.2 4.8L18 9l-4.8 1.2L12 15l-1.2-4.8L6 9l4.8-1.2L12 3z"/>
              <path d="M18 14l.8 1.6 1.6.8-1.6.8-.8 1.6-.8-1.6-1.6-.8 1.6-.8.8-1.6z"/>
            </svg>
          </button>
          <div class="input-actions__divider" />
          <button v-if="chatStore.isStreaming" class="btn-stop" type="button" @click="handleStop">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <rect x="6" y="6" width="12" height="12" rx="2"/>
            </svg>
          </button>
          <button v-else class="btn-send" :disabled="!canSend" type="button" :title="t('chat.send')" @click="handleSend">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/useChatStore'
import { useModelStore } from '@/stores/useModelStore'
import { Marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import * as echarts from 'echarts'
import { CopyDocument, Select, RefreshRight } from '@element-plus/icons-vue'
import { copyToClipboard } from '@/utils/clipboard'
import * as datasourceApi from '@/api/datasource'
import { uploadAttachment, optimizePrompt, resolveChartMetricMeta, interpretChart, type MessageContentPart, type ChartMetricMeta, type ChartMetricMetaPayload } from '@/api/chat'
import type { QueryPlanData, ChartCardData, EChartsOptionData, ClarifyData, DashboardCardData, FollowupData, RecommendedQuestionData, Datasource, ChatAttachment } from '@/types'

const { t } = useI18n()
const chatStore = useChatStore()
const modelStore = useModelStore()

defineEmits<{
  openDashboard: []
}>()

/** 智能问数快捷菜单项配置 */
const ICON_STROKE_ATTRS = 'viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"'
const smartAskMenuItems = [
  { key: 'interpret', label: 'smartAskMenu.interpret', icon: `<svg ${ICON_STROKE_ATTRS}><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>` },
  { key: 'report', label: 'smartAskMenu.report', icon: `<svg ${ICON_STROKE_ATTRS}><line x1="12" y1="20" x2="12" y2="10"/><line x1="18" y1="20" x2="18" y2="4"/><line x1="6" y1="20" x2="6" y2="16"/></svg>` },
  { key: 'insight', label: 'smartAskMenu.insight', icon: `<svg ${ICON_STROKE_ATTRS}><path d="M9 18h6"/><path d="M10 22h4"/><path d="M15.09 14a6 6 0 0 0 1.41-8.94 6 6 0 0 0-9.5 7.94"/><path d="M9.5 14h5"/></svg>` },
  { key: 'compare', label: 'smartAskMenu.compare', icon: `<svg ${ICON_STROKE_ATTRS}><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>` },
  { key: 'forecast', label: 'smartAskMenu.forecast', icon: `<svg ${ICON_STROKE_ATTRS}><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/></svg>` },
  { key: 'anomaly', label: 'smartAskMenu.anomaly', icon: `<svg ${ICON_STROKE_ATTRS}><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>` },
]

/** 已启用的数据源列表（用于输入框上方数据源选择器） */
const enabledDatasources = ref<Datasource[]>([])

/** 加载数据源列表 */
async function loadDatasources(): Promise<void> {
  try {
    const list = await datasourceApi.list() as unknown as Datasource[]
    enabledDatasources.value = list.filter(ds => ds.enabled)
  } catch {
    enabledDatasources.value = []
  }
}

/** 切换数据源选中状态 */
function toggleDatasource(dsId: string): void {
  const ids = chatStore.selectedDatasourceIds
  const idx = ids.indexOf(dsId)
  if (idx >= 0) {
    ids.splice(idx, 1)
  } else {
    ids.push(dsId)
  }
}

/** 数据源触发按钮文案 */
const dsTriggerLabel = computed(() => {
  const count = chatStore.selectedDatasourceIds.length
  if (count === 0) return t('chat.datasourceScope')
  return `${t('chat.datasourceScope')} (${count})`
})

/** 根据时间段生成问候语 */
const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) {
    return t('smartAskMenu.morningGreeting')
  } else if (hour < 18) {
    return t('smartAskMenu.afternoonGreeting')
  } else {
    return t('smartAskMenu.eveningGreeting')
  }
})

/** QueryPlan 字段标签映射 */
const queryPlanLabels: Record<string, string> = {
  indicator: '指标',
  dimension: '维度',
  time: '时间',
  compare: '比较',
  sort: '排序',
  limit: '条数',
}

/** 反馈选项 */
const feedbackOptions = [
  { key: 'helpful', label: '有帮助', icon: `<svg ${ICON_STROKE_ATTRS}><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>` },
  { key: 'inaccurate', label: '不准确', icon: `<svg ${ICON_STROKE_ATTRS}><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zM17 2h2a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-2"/></svg>` },
  { key: 'correct', label: '给出正确答案', icon: `<svg ${ICON_STROKE_ATTRS}><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z"/></svg>` },
]

/** 输入消息 */
const inputMessage = ref('')

/** 待发送的附件列表 */
const pendingAttachments = ref<ChatAttachment[]>([])

/** 附件上传中状态 */
const isUploading = ref(false)

/** 一键优化中状态 */
const isOptimizing = ref(false)

/** 文件选择 input 引用 */
const fileInputRef = ref<HTMLInputElement | null>(null)

/** 聊天区域容器引用 */
const chatAreaRef = ref<HTMLElement | null>(null)
/** 缓存 DOM 引用用于卸载时移除滚动监听 */
let chatAreaEl: HTMLElement | null = null

/** QueryPlan 确认状态 */
const queryPlanConfirmed = reactive<Record<string, boolean>>({})

/** 澄清卡片选中状态 */
const clarifySelected = reactive<Record<string, number>>({})

/** 澄清卡片确认状态 */
const clarifyConfirmed = reactive<Record<string, boolean>>({})

/** 反馈状态 */
const feedbackState = reactive<Record<string, string>>({})

/** 格式化消息时间戳，包含日期和时间 */
function formatMsgTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()
  const pad = (n: number): string => n.toString().padStart(2, '0')
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`

  const isSameDay = (a: Date, b: Date): boolean =>
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()

  if (isSameDay(date, now)) {
    return time
  }

  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  if (isSameDay(date, yesterday)) {
    return t('time.yesterdayAt', { time })
  }

  const ymd = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return `${ymd} ${time}`
}

/** 复制状态：按消息索引记录 */
const copyState = reactive<Record<number, 'idle' | 'copied'>>({})

/** 复制消息内容到剪贴板 */
function handleCopy(msgIndex: number, content: string): void {
  if (!content) return
  copyToClipboard(content).then(() => {
    copyState[msgIndex] = 'copied'
    setTimeout(() => {
      copyState[msgIndex] = 'idle'
    }, 2000)
  }).catch(() => {})
}

/** 重新生成 AI 消息 */
function handleRegenerate(msgIndex: number): void {
  chatStore.regenerateMessage(msgIndex)
}

/** 工具调用展开状态（按消息索引+工具索引） */
const expandedTools = reactive<Set<number>>(new Set())

/** 中间叙述（content segment）展开状态，key = `${msgIndex}-${segIdx}` */
const expandedNarrations = ref<Set<string>>(new Set())

/** "执行过程"卡片展开状态，key = `${msgIndex}` */
const expandedExecutionProcesses = ref<Set<number>>(new Set())

/** 图表实例映射 */
const chartInstances = new Map<string, echarts.ECharts>()
const chartRefs = new Map<string, HTMLElement>()

/** ECharts Option 模式图表实例映射（后端返回标准 option 时使用） */
const echartsInstances = new Map<string, echarts.ECharts>()
const echartsRefs = new Map<string, HTMLElement>()

/** 是否可以发送 */
const canSend = computed(() => (inputMessage.value.trim() || pendingAttachments.value.length > 0) && !chatStore.isStreaming && chatStore.currentAgentId)

/** Marked 自定义 renderer：将 ```echarts 代码块转为占位 div，供后续 ECharts 初始化 */
const customRenderer = {
  code({ text, lang }: { text: string; lang?: string; escaped?: boolean }): string {
    const infoStr = (lang || '').split(/\s/)[0]
    if (infoStr === 'echarts') {
      // 转成占位 div，ECharts 实例由 scanAndMountEChartsBlocks 在 nextTick 后挂载
      return `<div class="echarts-block" data-echarts-option="${encodeURIComponent(text || '')}"></div>\n`
    }
    // 非 echarts 代码块：使用 highlight.js 高亮处理
    const detectedLang = infoStr
    const hasLanguage = !!detectedLang && !!hljs.getLanguage(detectedLang)
    let highlighted: string
    try {
      highlighted = hasLanguage
        ? hljs.highlight(text, { language: detectedLang }).value
        : hljs.highlightAuto(text).value
    } catch {
      highlighted = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    }
    const langClass = hasLanguage ? ` language-${detectedLang}` : ''
    return `<pre><code class="hljs${langClass}">${highlighted}</code></pre>\n`
  },
}

/** Marked 实例（不接入 markedHighlight，由自定义 renderer 自行处理高亮） */
const markedInstance = new Marked({
  gfm: true,
  breaks: true,
  renderer: customRenderer,
})

/** DOMPurify 配置：允许 echarts 占位 div 所需的属性 */
const purifyConfig = {
  ADD_ATTR: ['data-echarts-option', 'class', 'style'],
  ADD_TAGS: ['div', 'span', 'pre', 'code'],
}

/** 渲染 Markdown */
const markdownCache = new Map<string, string>()
const MAX_MARKDOWN_CACHE = 64

/** 轻量文本转义：仅做 HTML 转义与换行处理，用于流式输出期间避免 marked.parse 阻塞主线程 */
function escapeHtmlForStreaming(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 流式输出期间的轻量渲染：转义 + 换行 + 基础代码块边界（不高亮不解析 Markdown 语法） */
function renderStreamingText(content: string): string {
  if (!content) return ''
  // 按代码围栏拆分，代码块内仅转义保留空白，块外做换行处理
  const parts = content.split(/(```[\s\S]*?```)/g)
  let html = ''
  for (const part of parts) {
    if (part.startsWith('```') && part.endsWith('```')) {
      // 代码块：去掉围栏，提取语言标识，内容转义
      const inner = part.slice(3, -3)
      const nlIdx = inner.indexOf('\n')
      const lang = nlIdx >= 0 ? inner.slice(0, nlIdx).trim() : ''
      const code = nlIdx >= 0 ? inner.slice(nlIdx + 1) : ''
      const langClass = lang ? ` language-${lang.split(/\s/)[0]}` : ''
      html += `<pre><code class="hljs${langClass}">${escapeHtmlForStreaming(code)}</code></pre>\n`
    } else {
      html += escapeHtmlForStreaming(part).replace(/\n/g, '<br>\n')
    }
  }
  return html
}

/** 判断指定索引的消息是否为正在流式输出的最后一条 */
function isStreamingLastMessage(index: number): boolean {
  return chatStore.isStreaming && index === chatStore.messages.length - 1
}

/** 根据是否流式选择渲染策略：流式时轻量渲染，非流式时完整 Markdown */
function renderMessageText(content: string, index: number): string {
  if (isStreamingLastMessage(index)) {
    return renderStreamingText(content)
  }
  return renderMarkdown(content)
}

function renderMarkdown(content: string): string {
  if (!content) return ''
  const cached = markdownCache.get(content)
  if (cached !== undefined) return cached
  const html = markedInstance.parse(content) as string
  const sanitized = DOMPurify.sanitize(html, purifyConfig)
  // 简单的 LRU：超过上限时丢弃最早插入项（Map 保留插入顺序）
  if (markdownCache.size >= MAX_MARKDOWN_CACHE) {
    const firstKey = markdownCache.keys().next().value
    if (firstKey !== undefined) {
      markdownCache.delete(firstKey)
    }
  }
  markdownCache.set(content, sanitized)
  return sanitized
}

/** 判断消息是否有可展示的 metadata */
function hasMetadata(msg: typeof chatStore.messages.value[0]): boolean {
  if (!msg.metadata || typeof msg.metadata !== 'object') return false
  const meta = msg.metadata as Record<string, unknown>
  const toolCalls = meta.toolCalls as Array<Record<string, unknown>> | undefined
  if (toolCalls && toolCalls.length > 0) return true
  const segments = meta.segments as Array<Record<string, unknown>> | undefined
  if (segments && segments.length > 0) return true
  if (meta.runtimeModel || meta.promptTokens || meta.completionTokens) return true
  return false
}

/** 判断 segment 是否为可展示内容 */
function isDisplayableSegment(seg: Record<string, unknown>): boolean {
  if (seg.type === 'tool_call') {
    return true
  }
  if (seg.type === 'thinking') {
    return !!(seg.thinkingText as string)
  }
  if (seg.type === 'content') {
    return !!(seg.text as string)
  }
  return false
}

/**
 * 提取所有 segments 数组：保留 tool_call、thinking 与 content 类型。
 * 中间 content（除最后一条持久化答案外）会被放入"执行过程"，
 * 最后一条非 segmentOnly 的 content 作为最终答案在气泡底部以正常字号展示。
 */
function getSegments(msg: typeof chatStore.messages.value[0]): Array<Record<string, unknown>> {
  if (!msg.metadata || typeof msg.metadata !== 'object') return []
  const meta = msg.metadata as Record<string, unknown>
  const segments = (meta.segments as Array<Record<string, unknown>>) || []
  return segments
    .filter(seg => seg.type === 'tool_call' || seg.type === 'thinking' || seg.type === 'content')
    .filter(isDisplayableSegment)
}

/**
 * 提取属于"执行过程"的 segments：thinking、tool_call、以及非最终答案的 content。
 */
function getExecutionProcessSegments(msg: typeof chatStore.messages.value[0]): Array<Record<string, unknown>> {
  const finalIdx = getFinalContentSegmentIndex(msg)
  return getSegments(msg).filter((seg, idx) => {
    if (seg.type === 'thinking' || seg.type === 'tool_call') return true
    if (seg.type === 'content' && idx !== finalIdx) return true
    return false
  })
}

/** 是否存在可展示的"执行过程"内容 */
function hasExecutionProcess(msg: typeof chatStore.messages.value[0]): boolean {
  return getExecutionProcessSegments(msg).length > 0
}

/** 生成"执行过程"摘要：例如"2 个思考 · 3 个工具" */
function getExecutionProcessSummary(msg: typeof chatStore.messages.value[0]): string {
  const segs = getExecutionProcessSegments(msg)
  const thinkCount = segs.filter(s => s.type === 'thinking').length
  const toolCount = segs.filter(s => s.type === 'tool_call').length
  const contentCount = segs.filter(s => s.type === 'content').length
  const parts: string[] = []
  if (thinkCount > 0) parts.push(`${thinkCount} ${t('chat.executionStep').toLowerCase()}`)
  if (toolCount > 0) parts.push(`${toolCount} ${t('chat.toolExecution').toLowerCase()}`)
  if (contentCount > 0 && parts.length === 0) parts.push(`${contentCount} 步骤`)
  return parts.length > 0 ? parts.join(' · ') : ''
}

/** 找到最终答案 content 的索引：优先选择最后一条非 segmentOnly 的 content */
function getFinalContentSegmentIndex(msg: typeof chatStore.messages.value[0]): number {
  const segs = getSegments(msg)
  for (let i = segs.length - 1; i >= 0; i--) {
    if (segs[i].type === 'content' && segs[i].segmentOnly !== true) return i
  }
  return -1
}

/** 判断给定 segment 是否为最终答案 */
function isFinalContentSegment(msg: typeof chatStore.messages.value[0], segIdx: number): boolean {
  return getFinalContentSegmentIndex(msg) === segIdx
}

/**
 * 获取最终答案文本：优先取最后一条非 segmentOnly 的 content segment；
 * 若 metadata.segments 缺失或只有执行过程，回退到 msg.content。
 */
function getFinalAnswer(msg: typeof chatStore.messages.value[0]): string {
  const segs = getSegments(msg)
  const lastIdx = getFinalContentSegmentIndex(msg)
  if (lastIdx >= 0) {
    return (segs[lastIdx].text as string) || ''
  }
  return msg.content || ''
}

/** 展开/收起"执行过程"卡片 */
function toggleExecutionProcessExpand(msgIdx: number): void {
  if (expandedExecutionProcesses.value.has(msgIdx)) {
    expandedExecutionProcesses.value.delete(msgIdx)
  } else {
    expandedExecutionProcesses.value.add(msgIdx)
  }
}

/** 判断"执行过程"卡片是否处于展开状态 */
function isExecutionProcessExpanded(msgIdx: number): boolean {
  return expandedExecutionProcesses.value.has(msgIdx)
}

/** 展开/收起某条中间叙述。key 形如 `${msgIndex}-${segIdx}`，跨消息独立。 */
function toggleNarrationExpand(msgIdx: number, segIdx: number): void {
  const key = `${msgIdx}-${segIdx}`
  if (expandedNarrations.value.has(key)) {
    expandedNarrations.value.delete(key)
  } else {
    expandedNarrations.value.add(key)
  }
}

/** 判断某条中间叙述是否处于展开状态。 */
function isNarrationExpanded(msgIdx: number, segIdx: number): boolean {
  return expandedNarrations.value.has(`${msgIdx}-${segIdx}`)
}

/** 提取工具调用列表（兜底：当 metadata.segments 不存在时使用） */
function getToolCalls(msg: typeof chatStore.messages.value[0]): Array<Record<string, unknown>> {
  if (!msg.metadata || typeof msg.metadata !== 'object') return []
  const meta = msg.metadata as Record<string, unknown>
  return (meta.toolCalls as Array<Record<string, unknown>>) || []
}

/** 格式化 token 和模型信息 */
function getTokenInfo(msg: typeof chatStore.messages.value[0]): string | null {
  if (!msg.metadata || typeof msg.metadata !== 'object') return null
  const meta = msg.metadata as Record<string, unknown>
  const parts: string[] = []
  const model = (meta.runtimeModel as string | undefined)
  const provider = (meta.runtimeProviderId as string | undefined)
  const promptTok = (meta.promptTokens as number | undefined)
  const completionTok = (meta.completionTokens as number | undefined)
  if (model) parts.push(model)
  if (provider && provider !== model) parts.push(provider)
  if (promptTok != null || completionTok != null) {
    parts.push(`${promptTok ?? 0}+${completionTok ?? 0} tokens`)
  }
  return parts.length > 0 ? parts.join(' · ') : null
}

/** 切换工具调用展开/收起 */
function toggleToolExpand(toolIdx: number): void {
  if (expandedTools.has(toolIdx)) {
    expandedTools.delete(toolIdx)
  } else {
    expandedTools.add(toolIdx)
  }
}

/** 截断工具参数（显示在 header 行） */
function truncateArgs(args: string | undefined): string {
  if (!args) return ''
  try {
    const parsed = JSON.parse(args)
    const str = JSON.stringify(parsed)
    return str.length > 60 ? str.slice(0, 60) + '…' : str
  } catch {
    return args.length > 60 ? args.slice(0, 60) + '…' : args
  }
}

/** 格式化工具返回结果预览（展开后显示） */
function formatResultPreview(result: string): string {
  if (!result) return ''
  try {
    const parsed = JSON.parse(result)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return result.length > 500 ? result.slice(0, 500) + '...' : result
  }
}

/** 格式化工具参数/结果（展开区展示完整内容，不做截断） */
function formatToolBody(value: string | undefined): string {
  if (!value) return ''
  try {
    const parsed = JSON.parse(value)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return value
  }
}

/** 设置图表容器引用 */
function setChartRef(el: HTMLElement | null, msgIndex: number, cardIndex: number): void {
  if (!el) return
  const key = `${msgIndex}-${cardIndex}`
  chartRefs.set(key, el)
  nextTick(() => initChart(key, msgIndex, cardIndex))
}

/** 初始化图表 */
function initChart(key: string, msgIndex: number, cardIndex: number): void {
  const el = chartRefs.get(key)
  if (!el) return

  const msg = chatStore.messages[msgIndex]
  if (!msg?.cards) return

  const card = msg.cards[cardIndex]
  if (card?.type !== 'chart') return

  const chartData = card.data as ChartCardData
  const instance = echarts.init(el)
  chartInstances.set(key, instance)

  const isBar = chartData.series[0]?.type === 'bar'
  instance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 20 },
    xAxis: { type: 'category', data: chartData.xData, axisLabel: { fontSize: 9, color: '#aaa' } },
    yAxis: { type: 'value', axisLabel: { fontSize: 9, color: '#aaa' }, splitLine: { lineStyle: { color: '#eee' } } },
    series: chartData.series.map(s => ({
      name: s.name,
      type: s.type || 'line',
      smooth: !isBar,
      data: s.data,
      lineStyle: { color: '#F05A23', width: 2 },
      itemStyle: { color: '#F05A23' },
      areaStyle: s.type !== 'bar' ? { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(240,90,35,0.2)' }, { offset: 1, color: 'rgba(240,90,35,0)' }] } } : undefined,
      barWidth: isBar ? 24 : undefined,
    }))
  })
}

/** 设置 ECharts Option 模式图表容器引用 */
function setEChartsRef(el: HTMLElement | null, msgIndex: number, cardIndex: number): void {
  if (!el) return
  const key = `ec-${msgIndex}-${cardIndex}`
  echartsRefs.set(key, el)
  nextTick(() => initEChartsChart(key, msgIndex, cardIndex))
}

/**
 * 初始化 ECharts Option 模式图表
 * 后端返回标准 ECharts option 时，直接透传给 echarts.setOption() 渲染
 */
function initEChartsChart(key: string, msgIndex: number, cardIndex: number): void {
  const el = echartsRefs.get(key)
  if (!el) return

  const msg = chatStore.messages[msgIndex]
  if (!msg?.cards) return

  const card = msg.cards[cardIndex]
  if (card?.type !== 'echarts') return

  const echartsData = card.data as EChartsOptionData
  const instance = echarts.init(el)
  echartsInstances.set(key, instance)

  /** 合并默认配置与后端返回的 option，确保基础体验一致 */
  const mergedOption = {
    tooltip: {
      trigger: 'axis' as const,
      ...((echartsData.option.tooltip || {}) as Record<string, unknown>),
    },
    grid: {
      left: 40,
      right: 16,
      top: echartsData.title ? 40 : 24,
      bottom: 20,
      ...((echartsData.option.grid || {}) as Record<string, unknown>),
    },
    ...echartsData.option,
  }

  instance.setOption(mergedOption)
}

/** ECharts 代码块实例映射（Markdown 内嵌的 ```echarts 占位块） */
const echartsBlockInstances = new Map<HTMLElement, echarts.ECharts>()

/** ECharts 代码块原始 option 映射，用于图表类型切换时复用数据 */
const echartsBlockOptions = new Map<HTMLElement, Record<string, any>>()

/** ECharts 代码块当前选中类型映射 */
const echartsBlockTypes = new Map<HTMLElement, string>()

/** ECharts 代码块列表明细浮层可见状态映射 */
const echartsBlockVisibleTable = new Map<HTMLElement, boolean>()

/** 每个 ECharts 块关联的 ResizeObserver 映射，用于在容器尺寸变化时自动 resize */
const echartsBlockObservers = new Map<HTMLElement, ResizeObserver>()

/** 每个 ECharts 块关联的「指标查看」请求载荷（在挂载时从所属消息的 metrics_query 工具入参解析；null 表示无指标信息，不显示按钮） */
const echartsBlockMetricPayload = new Map<HTMLElement, ChartMetricMetaPayload | null>()

/** 「指标查看」解析结果缓存（避免重复请求） */
const echartsBlockMetricMeta = new Map<HTMLElement, ChartMetricMeta>()

/** 「解读」结果缓存（避免重复请求） */
const echartsBlockInterpret = new Map<HTMLElement, string>()

/** 图表初始渲染的原生完整 option 与初始类型（AI 输出的原始样式：如自定义标记/标签/面积/坐标轴箭头等）。
 *  切换图表类型会重建为标准样式，切回初始类型时用此原生 option 还原，避免丢失 AI 原始呈现。 */
const echartsBlockPristine = new Map<HTMLElement, { option: Record<string, any>; type: ChartType }>()

/** 支持切换的图表类型（不含表格，表格由独立按钮触发的浮层展示） */
const ECHARTS_CHART_TYPES = [
  { key: 'bar', label: '柱状图' },
  { key: 'line', label: '折线图' },
  { key: 'pie', label: '饼图' },
  { key: 'scatter', label: '散点图' },
  { key: 'funnel', label: '漏斗图' },
  { key: 'gauge', label: '仪表盘/指标卡片' },
] as const

type ChartType = (typeof ECHARTS_CHART_TYPES)[number]['key']

/** ECharts option 顶层 key 白名单（安全过滤） */
const ECHARTS_ALLOWED_KEYS = new Set([
  'title', 'tooltip', 'legend', 'xAxis', 'yAxis', 'series',
  'grid', 'color', 'dataset', 'graphic', 'radar', 'polar',
  'angleAxis', 'radiusAxis', 'visualMap',
])

/** ECharts option 最大尺寸（100KB） */
const ECHARTS_MAX_OPTION_SIZE = 100 * 1024

/**
 * 递归剥离 ECharts option 中的函数值，防止 XSS
 */
function sanitizeEchartsOption(obj: Record<string, any>): void {
  for (const key of Object.keys(obj)) {
    const val = obj[key]
    if (typeof val === 'string' && val.trimStart().startsWith('function')) {
      delete obj[key]
    } else if (typeof val === 'function') {
      delete obj[key]
    } else if (val && typeof val === 'object') {
      if (Array.isArray(val)) {
        val.forEach((item: any) => {
          if (item && typeof item === 'object') {
            sanitizeEchartsOption(item)
          }
        })
      } else {
        sanitizeEchartsOption(val)
      }
    }
  }
}

/**
 * 过滤 ECharts option 顶层 key，仅保留白名单内的字段
 */
function filterEchartsTopLevelKeys(option: Record<string, any>): Record<string, any> {
  const filtered: Record<string, any> = {}
  for (const key of Object.keys(option)) {
    if (ECHARTS_ALLOWED_KEYS.has(key)) {
      filtered[key] = option[key]
    }
  }
  return filtered
}

/**
 * 提取 option 中的 xAxis/yAxis 指标名、xAxis 类别数据、series 名称与数值，
 * 供图表类型切换与列表明细复用
 */
function extractChartData(option: Record<string, any>): {
  xAxisName: string
  yAxisName: string
  categories: string[]
  seriesList: { name: string; data: number[] }[]
} {
  // 兼容 xAxis 是对象或数组（多 x 轴场景）：取第一个有数据的轴
  const xAxis: any = (() => {
    if (Array.isArray(option.xAxis)) return option.xAxis[0] || {}
    return option.xAxis || {}
  })()
  const yAxis: any = (() => {
    if (Array.isArray(option.yAxis)) return option.yAxis[0] || {}
    return option.yAxis || {}
  })()
  // 优先读取 xAxis.name，再回退到 series 第一个 name
  const xAxisName = (typeof xAxis?.name === 'string' && xAxis.name.trim())
    || (typeof xAxis?.nameTextStyle?.text === 'string' ? xAxis.nameTextStyle.text : '')
    || ''
  const yAxisName = (typeof yAxis?.name === 'string' && yAxis.name.trim())
    || (typeof yAxis?.nameTextStyle?.text === 'string' ? yAxis.nameTextStyle.text : '')
    || ''
  const categories: string[] = (xAxis && Array.isArray(xAxis.data)) ? [...xAxis.data] : []
  const seriesRaw: any[] = Array.isArray(option.series) ? option.series : []
  const seriesList = seriesRaw
    .map((s: any) => {
      // 兼容多种 series.data 形态：
      // 1. 纯数字数组 [1, 2, 3]
      // 2. 数值/字符串混合（折线图常见）
      // 3. 对象数组 [{name, value}]（饼图原始形态，不展开）
      let numericData: number[] = []
      if (Array.isArray(s?.data)) {
        numericData = s.data
          .map((v: any) => {
            if (typeof v === 'number') return v
            if (typeof v === 'string') {
              const n = Number(v)
              return Number.isFinite(n) ? n : null
            }
            if (v && typeof v === 'object' && typeof v.value === 'number') return v.value
            return null
          })
          .filter((v: number | null): v is number => v !== null && Number.isFinite(v))
      }
      return {
        name: typeof s?.name === 'string' ? s.name : '',
        data: numericData,
      }
    })
    .filter((s: { data: number[] }) => s.data.length > 0)
  return { xAxisName, yAxisName, categories, seriesList }
}

/**
 * 提取所有 series 数值（用于计算仪表盘/漏斗等汇总指标）
 */
function flattenValues(seriesList: { name: string; data: number[] }[]): number[] {
  return seriesList.flatMap((s) => s.data)
}

/**
 * 构造表格 HTML（替代 ECharts 的简单 table 渲染）
 * 表头优先展示 xAxis.name 等真实指标名，让用户能直接看懂列含义
 */
function buildTableHtml(original: Record<string, any>): string {
  const { xAxisName, yAxisName, categories, seriesList } = extractChartData(original)
  if (seriesList.length === 0) {
    return '<div class="echarts-table-empty">无数据</div>'
  }
  // 第一列是 X 轴（类别）维度：仅在有 xAxisName 时使用，否则用"日期"兜底；
  // 不要再回退到 yAxisName，否则会把"订单量"之类塞到日期列导致列含义错乱
  const firstColName = xAxisName || (categories.length > 0 ? '日期' : '序号')
  // 各 series 列名：优先 series.name，否则用 yAxis.name，再不行用"指标 N"
  const headerCells = [`<th>${escapeHtml(firstColName)}</th>`]
    .concat(seriesList.map((s, idx) => {
      const colName = s.name || yAxisName || `指标 ${idx + 1}`
      return `<th>${escapeHtml(colName)}</th>`
    }))
    .join('')
  const rows = categories.length > 0
    ? categories.map((cat, i) => {
        const cells = seriesList.map((s) => `<td>${escapeHtml(String(s.data[i] ?? '-'))}</td>`).join('')
        return `<tr><td>${escapeHtml(cat)}</td>${cells}</tr>`
      }).join('')
    : seriesList[0].data.map((v, i) => {
        const cells = seriesList.map((s) => `<td>${escapeHtml(String(s.data[i] ?? '-'))}</td>`).join('')
        return `<tr><td>第 ${i + 1} 行</td>${cells}</tr>`
      }).join('')
  return `<table class="echarts-table"><thead><tr>${headerCells}</tr></thead><tbody>${rows}</tbody></table>`
}

/** HTML 实体转义 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 数据量大时把最小的一批聚合成"其他"项，避免图表被挤成无法辨识的薄片
 * @param items 形如 [{name, value}, ...]
 * @param keepTop 保留前 N 个最大项，剩余聚合成"其他 (M 项)"
 */
function aggregateSmallItems<T extends { name: string; value: number }>(items: T[], keepTop: number): T[] {
  if (items.length <= keepTop) {
    return items
  }
  const sorted = [...items].sort((a, b) => b.value - a.value)
  const top = sorted.slice(0, keepTop)
  const rest = sorted.slice(keepTop)
  const otherValue = rest.reduce((sum, it) => sum + it.value, 0)
  return [
    ...top,
    { name: `其他 (${rest.length} 项)`, value: otherValue } as unknown as T,
  ]
}

/**
 * 通用 xAxis 配置：类别数 > 阈值时让标签自动间隔、旋转、截断，避免重叠
 */
function buildAdaptiveXAxis(categories: string[]): Record<string, any> {
  const cfg: Record<string, any> = { type: 'category', data: categories }
  if (categories.length > 8) {
    cfg.axisLabel = {
      interval: 'auto',
      rotate: categories.length > 15 ? 45 : 0,
      formatter: (val: string) => (val && val.length > 8 ? `${val.slice(0, 8)}…` : val),
    }
  }
  return cfg
}

/**
 * 根据目标图表类型重建 option（保留 title/legend/tooltip 等布局，替换 xAxis/yAxis/series）
 */
function buildEchartsOption(original: Record<string, any>, type: ChartType): Record<string, any> {
  const { categories, seriesList } = extractChartData(original)
  const base = {
    title: original.title,
    tooltip: original.tooltip,
    legend: original.legend,
    grid: original.grid,
  }

  if (type === 'pie') {
    // 饼图：只支持一个 series，使用 categories 作为外环标签
    const first = seriesList[0] || { name: '', data: [] }
    const rawData = first.data.map((v: number, i: number) => ({
      name: categories[i] ?? String(i),
      value: v,
    }))
    // 类别过多时把最小值聚合成"其他"，避免饼图被切成不可读的细条
    const pieData = aggregateSmallItems(rawData, 8)
    const dense = rawData.length > 8
    return {
      ...base,
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        name: first.name,
        type: 'pie',
        radius: ['35%', '65%'],
        center: ['50%', '55%'],
        data: pieData,
        // 类别密集时关闭引导线与重叠标签，避免视觉混乱
        label: dense
          ? { formatter: '{b}\n{d}%', edgeDistance: 10, fontSize: 11 }
          : { formatter: '{b}\n{d}%' },
        labelLine: dense ? { length: 8, length2: 8 } : { show: true },
      }],
    }
  }

  if (type === 'scatter') {
    // 散点图：每个 series 转为 [xIndex, y] 二维数组
    const maxLen = Math.max(0, ...seriesList.map((s) => s.data.length))
    const xCats = categories.length > 0
      ? categories
      : Array.from({ length: maxLen }, (_, i) => String(i + 1))
    const scatterSeries = seriesList.map((s) => ({
      name: s.name,
      type: 'scatter',
      // 类别多时缩小点尺寸，避免一片糊
      symbolSize: xCats.length > 30 ? 6 : xCats.length > 15 ? 8 : 10,
      data: s.data.map((v, i) => [i, v]),
    }))
    return {
      ...base,
      tooltip: { trigger: 'item', formatter: (p: any) => `${p.seriesName}<br/>${xCats[p.value[0]] ?? p.value[0]}: ${p.value[1]}` },
      xAxis: buildAdaptiveXAxis(xCats),
      yAxis: { type: 'value' },
      series: scatterSeries,
    }
  }

  if (type === 'funnel') {
    // 漏斗图：取首个 series，每个类别一个漏斗层
    const first = seriesList[0] || { name: '', data: [] }
    const rawData = first.data.map((v: number, i: number) => ({
      name: categories[i] ?? `第${i + 1}项`,
      value: v,
    }))
    // 漏斗图按层高切分，本质上不适合 N 较大的数据。
    // 阈值降到 6：超过 6 层就聚合成前 6 大 + "其他"，避免"11+ 层的薄片 + 重叠标签"。
    const dense = rawData.length > 6
    const funnelData = dense ? aggregateSmallItems(rawData, 6) : rawData
    const maxVal = Math.max(...funnelData.map((d) => d.value), 0)
    return {
      ...base,
      tooltip: { trigger: 'item', formatter: '{b}: {c}' },
      series: [{
        name: first.name,
        type: 'funnel',
        left: '12%',
        right: '12%',
        top: '12%',
        bottom: '8%',
        width: '76%',
        min: 0,
        max: maxVal,
        // dense 时给最薄层留 8% 高度，避免接近 0 的层被压成不可见
        minSize: dense ? '8%' : '0%',
        maxSize: '100%',
        sort: 'descending',
        // dense 时加大层间距，让标签有展示空间
        gap: dense ? 6 : 2,
        // dense 场景统一改为外侧引导线 + 文字，并允许 hideOverlap 进一步去重
        label: dense
          ? { show: true, position: 'outside', formatter: '{b}: {c}', fontSize: 11 }
          : { show: true, position: 'inside' },
        labelLine: dense ? { show: true, length: 10, length2: 10 } : { show: false },
        labelLayout: dense ? { hideOverlap: true } : undefined,
        data: funnelData,
      }],
    }
  }

  if (type === 'gauge') {
    // 仪表盘/指标卡片：取首个 series 第一项作为指标值
    const allValues = flattenValues(seriesList)
    const value = allValues[0] ?? 0
    const max = Math.max(...allValues, value * 1.2, 100)
    const titleText = (() => {
      if (base.title && typeof base.title === 'object' && typeof base.title.text === 'string') {
        return base.title.text
      }
      if (typeof base.title === 'string') {
        return base.title
      }
      return '指标'
    })()
    return {
      ...base,
      tooltip: { trigger: 'item' },
      series: [{
        name: titleText,
        type: 'gauge',
        radius: '75%',
        center: ['50%', '60%'],
        min: 0,
        max,
        progress: { show: true, width: 12 },
        axisLine: { lineStyle: { width: 12 } },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        detail: { valueAnimation: true, fontSize: 24, formatter: '{value}', offsetCenter: [0, '0%'] },
        title: { show: true, offsetCenter: [0, '70%'], fontSize: 13, color: '#666' },
        data: [{ value, name: titleText }],
      }],
    }
  }

  // 柱状图 / 折线图
  const series = seriesList.map((s: { name: string; data: number[] }) => ({
    name: s.name,
    type,
    data: s.data,
    smooth: type === 'line',
    // 数据点多时让柱变细、关掉 symbol 强调连线
    ...(type === 'bar' && categories.length > 20 ? { barMaxWidth: 12 } : {}),
    ...(type === 'line' && categories.length > 30 ? { showSymbol: false } : {}),
  }))

  return {
    ...base,
    tooltip: base.tooltip || { trigger: 'axis' },
    xAxis: buildAdaptiveXAxis(categories),
    yAxis: { type: 'value' },
    series,
  }
}

/**
 * 根据图表类型与数据规模自适应调整容器高度，避免物理挤压
 * 漏斗图按层高切分，层数多时容器需要更高
 */
function adjustContainerHeightForType(htmlEl: HTMLElement, type: ChartType, option: Record<string, any>): void {
  if (type === 'funnel' && Array.isArray(option.series) && option.series[0]) {
    const funnelData: any[] = option.series[0].data || []
    // 每层至少 32px 高度 + 上下边距 100px，封顶 700px 防超长
    const minHeight = Math.min(Math.max(350, 100 + funnelData.length * 32), 700)
    const currentHeight = parseInt(htmlEl.style.height || '350', 10)
    if (currentHeight < minHeight) {
      htmlEl.style.height = `${minHeight}px`
    }
  } else {
    htmlEl.style.height = '350px'
  }
}

/**
 * 切换指定 ECharts 代码块的图表类型
 * 注意：表格不再作为图表类型，而是通过 toggleTableOverlay 触发的浮层单独展示
 */
function switchEchartsBlockType(htmlEl: HTMLElement, type: ChartType): void {
  const original = echartsBlockOptions.get(htmlEl)
  if (!original) {
    return
  }
  if (echartsBlockTypes.get(htmlEl) === type) {
    syncEchartsToolbarActive(htmlEl, type)
    return
  }
  echartsBlockTypes.set(htmlEl, type)

  let chart = echartsBlockInstances.get(htmlEl)
  if (!chart || chart.isDisposed()) {
    chart = echarts.init(htmlEl)
    echartsBlockInstances.set(htmlEl, chart)
  }

  // 切回图表初始类型时，还原 AI 原生完整 option（保留自定义标记/标签/面积/坐标轴箭头等原始样式），
  // 而非用 buildEchartsOption 重建的标准样式
  const pristine = echartsBlockPristine.get(htmlEl)
  const newOption = pristine && pristine.type === type
    ? JSON.parse(JSON.stringify(pristine.option)) // 克隆，避免多次切换时被 ECharts 内部引用污染缓存
    : buildEchartsOption(original, type)
  chart.setOption(newOption, true)

  // 漏斗图按层高切分，层数多时容器高度需要扩展，避免物理挤压
  adjustContainerHeightForType(htmlEl, type, newOption)

  syncEchartsToolbarActive(htmlEl, type)
}

/** 同步工具栏当前选中状态 */
function syncEchartsToolbarActive(htmlEl: HTMLElement, type: ChartType): void {
  const toolbar = htmlEl.querySelector('.echarts-toolbar') as HTMLElement | null
  if (!toolbar) {
    return
  }
  const trigger = toolbar.querySelector<HTMLElement>('.echarts-toolbar-trigger-label')
  const item = ECHARTS_CHART_TYPES.find((t) => t.key === type)
  if (trigger && item) {
    trigger.textContent = item.label
  }
  toolbar.querySelectorAll<HTMLElement>('li[data-type]').forEach((li) => {
    const isActive = li.getAttribute('data-type') === type
    li.classList.toggle('is-active', isActive)
  })
}

/** 全局下拉点击外部关闭监听器引用 */
let dropdownOutsideHandler: ((e: MouseEvent) => void) | null = null

/**
 * 切换指定 ECharts 代码块的列表明细浮层
 * 浮层叠加在图表之上，不销毁 ECharts 实例，确保切换按钮始终可用
 */
function toggleTableOverlay(htmlEl: HTMLElement): void {
  const original = echartsBlockOptions.get(htmlEl)
  if (!original) {
    return
  }
  const isVisible = !!echartsBlockVisibleTable.get(htmlEl)
  if (isVisible) {
    const overlay = htmlEl.querySelector('.echarts-table-overlay')
    if (overlay) {
      overlay.remove()
    }
    echartsBlockVisibleTable.set(htmlEl, false)
  } else {
    // 同一图表内的浮层互斥：打开列表明细前先关闭「指标查看/解读」浮层
    closeAllChartOverlays(htmlEl, 'table')
    const overlay = document.createElement('div')
    overlay.className = 'echarts-table-overlay'
    overlay.setAttribute('data-kind', 'table')
    overlay.innerHTML = `
      <div class="echarts-table-overlay-header">
        <span class="echarts-table-overlay-title">列表明细</span>
        <button type="button" class="echarts-table-overlay-close" aria-label="关闭">×</button>
      </div>
      <div class="echarts-table-overlay-body">${buildTableHtml(original)}</div>
    `
    overlay.querySelector('.echarts-table-overlay-close')?.addEventListener('click', (e) => {
      e.stopPropagation()
      toggleTableOverlay(htmlEl)
    })
    htmlEl.appendChild(overlay)
    echartsBlockVisibleTable.set(htmlEl, true)
  }
  // 同步工具栏的"列表明细"按钮高亮
  const detailBtn = htmlEl.querySelector<HTMLElement>('.echarts-toolbar-detail-btn')
  detailBtn?.classList.toggle('is-active', !isVisible ? true : false)
}

/**
 * 渲染 ECharts 块顶部的下拉式类型选择器（并附加"指标查看/解读/列表明细"独立按钮）
 * @param hasMetric 该图表是否有可查看的指标信息（来自所属消息的 metrics_query 工具入参）
 */
function renderEchartsToolbar(htmlEl: HTMLElement, currentType: ChartType, hasMetric = false): void {
  // 工具栏已存在则仅更新高亮
  const existing = htmlEl.querySelector('.echarts-toolbar')
  if (existing) {
    syncEchartsToolbarActive(htmlEl, currentType)
    return
  }

  const toolbar = document.createElement('div')
  toolbar.className = 'echarts-toolbar'

  // "指标查看"按钮（仅当图表有关联指标信息时显示）
  if (hasMetric) {
    const metricBtn = document.createElement('button')
    metricBtn.type = 'button'
    metricBtn.className = 'echarts-toolbar-detail-btn echarts-toolbar-metric-btn'
    metricBtn.innerHTML = `<span class="echarts-toolbar-detail-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg></span><span>${escAuxHtml(t('chart.viewMetric'))}</span>`
    metricBtn.addEventListener('click', (e) => {
      e.stopPropagation()
      void toggleMetricOverlay(htmlEl)
    })
    toolbar.appendChild(metricBtn)
  }

  // "解读"按钮（所有图表都提供）
  const interpretBtn = document.createElement('button')
  interpretBtn.type = 'button'
  interpretBtn.className = 'echarts-toolbar-detail-btn echarts-toolbar-interpret-btn'
  interpretBtn.innerHTML = `<span class="echarts-toolbar-detail-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18h6"/><path d="M10 22h4"/><path d="M15.09 14a6 6 0 0 0 1.41-8.94 6 6 0 0 0-9.5 7.94"/><path d="M9.5 14h5"/></svg></span><span>${escAuxHtml(t('chart.interpret'))}</span>`
  interpretBtn.addEventListener('click', (e) => {
    e.stopPropagation()
    void toggleInterpretOverlay(htmlEl)
  })
  toolbar.appendChild(interpretBtn)

  // "列表明细"独立按钮（在工具栏最左侧，与下拉同级）
  const detailBtn = document.createElement('button')
  detailBtn.type = 'button'
  detailBtn.className = 'echarts-toolbar-detail-btn'
  detailBtn.innerHTML = '<span class="echarts-toolbar-detail-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg></span><span>列表明细</span>'
  detailBtn.addEventListener('click', (e) => {
    e.stopPropagation()
    toggleTableOverlay(htmlEl)
  })
  toolbar.appendChild(detailBtn)

  // 下拉触发器
  const trigger = document.createElement('div')
  trigger.className = 'echarts-toolbar-trigger'
  const currentItem = ECHARTS_CHART_TYPES.find((t) => t.key === currentType)
  trigger.innerHTML = `
    <span class="echarts-toolbar-trigger-label">${currentItem ? currentItem.label : '选择图表类型'}</span>
    <span class="echarts-toolbar-trigger-arrow">▾</span>
  `
  // 下拉面板
  const panel = document.createElement('ul')
  panel.className = 'echarts-toolbar-panel'
  ECHARTS_CHART_TYPES.forEach(({ key, label }) => {
    const li = document.createElement('li')
    li.className = 'echarts-toolbar-panel-item' + (key === currentType ? ' is-active' : '')
    li.setAttribute('data-type', key)
    li.textContent = label
    li.addEventListener('click', (e) => {
      e.stopPropagation()
      switchEchartsBlockType(htmlEl, key as ChartType)
      panel.classList.remove('is-open')
      trigger.classList.remove('is-open')
    })
    panel.appendChild(li)
  })
  trigger.addEventListener('click', (e) => {
    e.stopPropagation()
    const isOpen = panel.classList.contains('is-open')
    // 关闭所有其他下拉
    document.querySelectorAll('.echarts-toolbar-panel.is-open').forEach((p) => p.classList.remove('is-open'))
    document.querySelectorAll('.echarts-toolbar-trigger.is-open').forEach((t) => t.classList.remove('is-open'))
    panel.classList.toggle('is-open', !isOpen)
    trigger.classList.toggle('is-open', !isOpen)
  })

  toolbar.appendChild(trigger)
  toolbar.appendChild(panel)
  htmlEl.appendChild(toolbar)

  // 注册全局点击关闭监听（仅注册一次）
  if (!dropdownOutsideHandler) {
    dropdownOutsideHandler = (e: MouseEvent): void => {
      const target = e.target as HTMLElement | null
      if (!target) {
        return
      }
      if (target.closest('.echarts-toolbar')) {
        return
      }
      document.querySelectorAll('.echarts-toolbar-panel.is-open').forEach((p) => p.classList.remove('is-open'))
      document.querySelectorAll('.echarts-toolbar-trigger.is-open').forEach((t) => t.classList.remove('is-open'))
    }
    document.addEventListener('click', dropdownOutsideHandler)
  }
}

/** 转义 HTML 特殊字符，用于把后端/查询文本安全注入 innerHTML */
function escAuxHtml(text: unknown): string {
  const s = text == null ? '' : String(text)
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 把工具入参归一为对象：已是对象直接用，是 JSON 字符串则解析，失败返回 null */
function coerceArgs(raw: unknown): Record<string, any> | null {
  if (raw && typeof raw === 'object') {
    return raw as Record<string, any>
  }
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const v = JSON.parse(raw)
      return v && typeof v === 'object' ? (v as Record<string, any>) : null
    } catch {
      return null
    }
  }
  return null
}

/** 关闭同一图表内除指定 kind 外的所有浮层，并同步按钮高亮 */
function closeAllChartOverlays(htmlEl: HTMLElement, except?: 'table' | 'metric' | 'interpret'): void {
  if (except !== 'table') {
    htmlEl.querySelector('.echarts-table-overlay[data-kind="table"]')?.remove()
    echartsBlockVisibleTable.set(htmlEl, false)
    htmlEl.querySelector<HTMLElement>('.echarts-toolbar-detail-btn:not(.echarts-toolbar-metric-btn):not(.echarts-toolbar-interpret-btn)')?.classList.remove('is-active')
  }
  if (except !== 'metric') {
    htmlEl.querySelector('.echarts-aux-overlay[data-kind="metric"]')?.remove()
    htmlEl.querySelector<HTMLElement>('.echarts-toolbar-metric-btn')?.classList.remove('is-active')
  }
  if (except !== 'interpret') {
    htmlEl.querySelector('.echarts-aux-overlay[data-kind="interpret"]')?.remove()
    htmlEl.querySelector<HTMLElement>('.echarts-toolbar-interpret-btn')?.classList.remove('is-active')
  }
}

/** 从图表元素反查所属 AI 消息的索引 */
function findMessageIndexForChart(htmlEl: HTMLElement): number | null {
  const msgEl = htmlEl.closest('.msg.ai') as HTMLElement | null
  if (!msgEl) {
    return null
  }
  const idx = Number(msgEl.dataset.msgIndex)
  return Number.isNaN(idx) ? null : idx
}

/**
 * 挂载时解析图表「指标查看」请求载荷：
 * 从所属消息的 metrics_query 工具调用（toolCalls 优先，回退 segments）合并 metrics/dimensions/filters/timeConstraint/datasourceId。
 * 无 metrics_query 时返回 null（不显示「指标查看」按钮）。
 */
function buildMetricPayloadForChart(htmlEl: HTMLElement): ChartMetricMetaPayload | null {
  const idx = findMessageIndexForChart(htmlEl)
  if (idx == null) {
    return null
  }
  const msg = chatStore.messages[idx]
  const meta = (msg?.metadata || {}) as Record<string, any>

  // 收集该消息里全部工具调用的入参（toolCalls 与 segments 两处来源，入参可能是字符串或对象）
  const candidates: Record<string, any>[] = []
  const toolCalls = Array.isArray(meta.toolCalls) ? meta.toolCalls : []
  for (const tc of toolCalls) {
    const a = coerceArgs(tc?.arguments)
    if (a) candidates.push(a)
  }
  const segments = Array.isArray(meta.segments) ? meta.segments : []
  for (const seg of segments) {
    if (seg && seg.type === 'tool_call') {
      const a = coerceArgs(seg.toolArgs)
      if (a) candidates.push(a)
    }
  }

  // 结构化识别指标查询：入参含非空 metrics 数组即视为指标查询（不强依赖工具名，兼容命名/持久化差异）
  const argsList = candidates.filter((a) => Array.isArray(a.metrics) && a.metrics.length > 0)
  if (argsList.length === 0) {
    return null
  }

  const metrics: string[] = []
  const dimensions: string[] = []
  const filters: string[] = []
  let timeConstraint: string | null = null
  let datasourceId: number | null = null
  for (const a of argsList) {
    if (Array.isArray(a.metrics)) {
      for (const m of a.metrics) if (typeof m === 'string' && !metrics.includes(m)) metrics.push(m)
    }
    if (Array.isArray(a.dimensions)) {
      for (const d of a.dimensions) if (typeof d === 'string' && !dimensions.includes(d)) dimensions.push(d)
    }
    if (Array.isArray(a.filters)) {
      for (const f of a.filters) if (typeof f === 'string' && !filters.includes(f)) filters.push(f)
    }
    if (!timeConstraint && typeof a.timeConstraint === 'string' && a.timeConstraint) {
      timeConstraint = a.timeConstraint
    }
    if (datasourceId == null && a.datasourceId != null) {
      datasourceId = Number(a.datasourceId)
    }
  }
  if (metrics.length === 0) {
    return null
  }
  return { datasourceId, metrics, dimensions, filters, timeConstraint }
}

/** 反查触发该图表的最近一条用户问题（用于增强解读上下文） */
function getNearestUserQuestion(htmlEl: HTMLElement): string | undefined {
  const idx = findMessageIndexForChart(htmlEl)
  if (idx == null) {
    return undefined
  }
  for (let i = idx - 1; i >= 0; i--) {
    const m = chatStore.messages[i]
    if (m && m.role === 'user' && m.content) {
      return m.content
    }
  }
  return undefined
}

/** 构建「指标查看」面板内容 HTML（值均经转义） */
function buildMetricMetaHtml(meta: ChartMetricMeta): string {
  const dash = '—'
  const metricsHtml = (meta.metrics || []).map((m) => {
    const sub: string[] = []
    if (m.unit) sub.push(`${escAuxHtml(t('chart.fieldUnit'))}：${escAuxHtml(m.unit)}`)
    if (m.category) sub.push(`${escAuxHtml(t('chart.fieldCategory'))}：${escAuxHtml(m.category)}`)
    return `
      <div class="metric-meta__item">
        <div class="metric-meta__name">${escAuxHtml(m.displayName || m.name)}<span class="metric-meta__en">${escAuxHtml(m.name)}</span></div>
        <div class="metric-meta__caliber"><span class="metric-meta__k">${escAuxHtml(t('chart.fieldCaliber'))}</span>${m.caliber ? escAuxHtml(m.caliber) : dash}</div>
        ${sub.length ? `<div class="metric-meta__extra">${sub.join(' ｜ ')}</div>` : ''}
      </div>`
  }).join('')

  const dimsText = (meta.dimensions || []).length
    ? (meta.dimensions || []).map((d) => escAuxHtml(d.displayName || d.name)).join('、')
    : dash
  const timeText = meta.timeRange ? escAuxHtml(meta.timeRange) : dash
  const filtersText = (meta.filters || []).length
    ? (meta.filters || []).map((f) => escAuxHtml(f)).join('<br/>')
    : dash

  return `
    <div class="metric-meta">
      <div class="metric-meta__group">
        <div class="metric-meta__label">${escAuxHtml(t('chart.fieldMetric'))}</div>
        <div class="metric-meta__value">${metricsHtml || dash}</div>
      </div>
      <div class="metric-meta__group">
        <div class="metric-meta__label">${escAuxHtml(t('chart.fieldDimensions'))}</div>
        <div class="metric-meta__value">${dimsText}</div>
      </div>
      <div class="metric-meta__group">
        <div class="metric-meta__label">${escAuxHtml(t('chart.fieldTimeRange'))}</div>
        <div class="metric-meta__value">${timeText}</div>
      </div>
      <div class="metric-meta__group">
        <div class="metric-meta__label">${escAuxHtml(t('chart.fieldFilters'))}</div>
        <div class="metric-meta__value">${filtersText}</div>
      </div>
    </div>`
}

/** 构建通用浮层骨架（头部标题 + 关闭按钮 + 加载态 body），返回 overlay 元素 */
function buildAuxOverlay(kind: 'metric' | 'interpret', title: string, onClose: () => void): HTMLElement {
  const overlay = document.createElement('div')
  overlay.className = 'echarts-table-overlay echarts-aux-overlay'
  overlay.setAttribute('data-kind', kind)
  overlay.innerHTML = `
    <div class="echarts-table-overlay-header">
      <span class="echarts-table-overlay-title">${escAuxHtml(title)}</span>
      <button type="button" class="echarts-table-overlay-close" aria-label="关闭">×</button>
    </div>
    <div class="echarts-table-overlay-body echarts-aux-body"><div class="echarts-aux-loading">${escAuxHtml(t('chart.loading'))}</div></div>
  `
  overlay.querySelector('.echarts-table-overlay-close')?.addEventListener('click', (e) => {
    e.stopPropagation()
    onClose()
  })
  return overlay
}

/** 切换「指标查看」浮层 */
async function toggleMetricOverlay(htmlEl: HTMLElement): Promise<void> {
  const existing = htmlEl.querySelector('.echarts-aux-overlay[data-kind="metric"]')
  if (existing) {
    existing.remove()
    htmlEl.querySelector<HTMLElement>('.echarts-toolbar-metric-btn')?.classList.remove('is-active')
    return
  }
  closeAllChartOverlays(htmlEl, 'metric')

  const overlay = buildAuxOverlay('metric', t('chart.viewMetric'), () => { void toggleMetricOverlay(htmlEl) })
  htmlEl.appendChild(overlay)
  htmlEl.querySelector<HTMLElement>('.echarts-toolbar-metric-btn')?.classList.add('is-active')
  const body = overlay.querySelector('.echarts-aux-body') as HTMLElement

  try {
    let data = echartsBlockMetricMeta.get(htmlEl)
    if (!data) {
      const payload = echartsBlockMetricPayload.get(htmlEl)
      if (!payload) {
        body.innerHTML = `<div class="echarts-aux-empty">${escAuxHtml(t('chart.noMetric'))}</div>`
        return
      }
      data = await resolveChartMetricMeta(payload)
      echartsBlockMetricMeta.set(htmlEl, data)
    }
    if (!overlay.isConnected) {
      return
    }
    body.innerHTML = buildMetricMetaHtml(data)
  } catch (e) {
    console.error('[ChatView] 指标查看解析失败:', e)
    if (overlay.isConnected) {
      body.innerHTML = `<div class="echarts-aux-empty">${escAuxHtml(t('chart.metricError'))}</div>`
    }
  }
}

/** 切换「解读」浮层 */
async function toggleInterpretOverlay(htmlEl: HTMLElement): Promise<void> {
  const existing = htmlEl.querySelector('.echarts-aux-overlay[data-kind="interpret"]')
  if (existing) {
    existing.remove()
    htmlEl.querySelector<HTMLElement>('.echarts-toolbar-interpret-btn')?.classList.remove('is-active')
    return
  }
  closeAllChartOverlays(htmlEl, 'interpret')

  const overlay = buildAuxOverlay('interpret', t('chart.interpret'), () => { void toggleInterpretOverlay(htmlEl) })
  htmlEl.appendChild(overlay)
  htmlEl.querySelector<HTMLElement>('.echarts-toolbar-interpret-btn')?.classList.add('is-active')
  const body = overlay.querySelector('.echarts-aux-body') as HTMLElement

  try {
    let text = echartsBlockInterpret.get(htmlEl)
    if (!text) {
      const option = echartsBlockOptions.get(htmlEl)
      if (!option) {
        body.innerHTML = `<div class="echarts-aux-empty">${escAuxHtml(t('chart.noChartData'))}</div>`
        return
      }
      const agentId = chatStore.currentAgentId
      if (!agentId) {
        body.innerHTML = `<div class="echarts-aux-empty">${escAuxHtml(t('chart.interpretError'))}</div>`
        return
      }
      text = await interpretChart({
        agentId,
        conversationId: chatStore.conversationId || '',
        echartsOption: JSON.stringify(option),
        question: getNearestUserQuestion(htmlEl),
      })
      echartsBlockInterpret.set(htmlEl, text)
    }
    if (!overlay.isConnected) {
      return
    }
    body.innerHTML = `<div class="echarts-interpret">${renderMarkdown(text)}</div>`
  } catch (e) {
    console.error('[ChatView] 图表解读失败:', e)
    if (overlay.isConnected) {
      body.innerHTML = `<div class="echarts-aux-empty">${escAuxHtml(t('chart.interpretError'))}</div>`
    }
  }
}

/**
 * 扫描聊天区域中所有未初始化的 .echarts-block 占位元素并挂载 ECharts 实例
 * 参照 mateclaw-ui useEChartsRenderer 的实现模式
 */
function scanAndMountEChartsBlocks(): void {
  const container = chatAreaRef.value
  if (!container) {
    return
  }
  const blocks = container.querySelectorAll('.echarts-block:not(.echarts-error)')
  let mountedCount = 0
  blocks.forEach((el) => {
    const htmlEl = el as HTMLElement
    if (echartsBlockInstances.has(htmlEl)) {
      return
    }
    // 已有 toolbar 但未初始化（重复扫描安全兜底）

    const encoded = htmlEl.getAttribute('data-echarts-option')
    if (!encoded) {
      return
    }

    if (encoded.length > ECHARTS_MAX_OPTION_SIZE) {
      htmlEl.textContent = 'Chart option too large'
      htmlEl.classList.add('echarts-error')
      return
    }

    try {
      const raw = decodeURIComponent(encoded)
      let option = JSON.parse(raw)

      if (!option || typeof option !== 'object' || !option.series) {
        htmlEl.textContent = 'Invalid chart option'
        htmlEl.classList.add('echarts-error')
        return
      }

      option = filterEchartsTopLevelKeys(option)
      sanitizeEchartsOption(option)

      // 修正布局：后端生成的 legend/grid 间距过紧容易重叠，
      // 在前端统一给标题/图例留出合理空间，避免元素互相挤压
      const hasTitle = !!option.title
      const hasLegend = !!option.legend
      option.title = {
        left: 'center',
        top: 8,
        textStyle: { fontSize: 13, fontWeight: 600 },
        ...(typeof option.title === 'object' ? option.title : {}),
      }
      option.legend = {
        bottom: 8,
        top: hasTitle ? 32 : 8,
        type: 'scroll',
        textStyle: { fontSize: 11 },
        ...(typeof option.legend === 'object' ? option.legend : {}),
      }
      option.grid = {
        left: 40,
        right: 24,
        top: hasTitle ? (hasLegend ? 72 : 56) : (hasLegend ? 56 : 28),
        bottom: hasLegend ? 48 : 24,
        containLabel: true,
        ...(typeof option.grid === 'object' ? option.grid : {}),
      }
      // 给工具栏留 36px 空间
      if (typeof option.grid === 'object' && option.grid) {
        option.grid.top = (typeof option.grid.top === 'number' ? option.grid.top : 56) + 36
      }

      if (!htmlEl.style.height) {
        htmlEl.style.height = '350px'
      }
      if (!htmlEl.style.width) {
        htmlEl.style.width = '100%'
      }

      // 推断初始图表类型：根据 series.type 决定工具栏默认高亮项
      const initialType: ChartType = (() => {
        const firstSeries: any = Array.isArray(option.series) ? option.series[0] : null
        const t = firstSeries?.type
        if (t === 'bar' || t === 'line' || t === 'pie' || t === 'scatter' || t === 'funnel' || t === 'gauge') {
          return t
        }
        return 'bar'
      })()

      // 后端直接返回 funnel 类型时，初始挂载阶段也按层数扩展容器高度
      adjustContainerHeightForType(htmlEl, initialType, option)

      // 保存原始 option（剔除前端布局覆盖字段）以便切换时复用数据
      echartsBlockOptions.set(htmlEl, {
        title: option.title,
        tooltip: option.tooltip,
        legend: option.legend,
        xAxis: option.xAxis,
        yAxis: option.yAxis,
        series: option.series,
      })
      echartsBlockTypes.set(htmlEl, initialType)

      // 缓存 AI 原生完整 option（含布局修正后的最终形态），供切回初始类型时原样还原。
      // option 源自 JSON 解析 + 函数剥离，可安全深拷贝。
      try {
        echartsBlockPristine.set(htmlEl, {
          option: JSON.parse(JSON.stringify(option)),
          type: initialType,
        })
      } catch {
        // 深拷贝失败则跳过还原能力，不影响主流程
      }

      const chart = echarts.init(htmlEl)
      chart.setOption(option)
      echartsBlockInstances.set(htmlEl, chart)
      mountedCount += 1

      // 监听容器尺寸变化：解决列表明细浮层开关、窗口缩放、侧边栏折叠等场景下
      // 图表不跟着容器伸缩的问题；debounce 50ms 避免动画过程高频触发
      let resizeRafId: number | null = null
      const observer = new ResizeObserver(() => {
        if (resizeRafId !== null) {
          cancelAnimationFrame(resizeRafId)
        }
        resizeRafId = requestAnimationFrame(() => {
          resizeRafId = null
          if (!chart.isDisposed()) {
            chart.resize()
          }
        })
      })
      observer.observe(htmlEl)
      echartsBlockObservers.set(htmlEl, observer)

      // 解析该图表的「指标查看」载荷（来自所属消息的 metrics_query 工具入参）
      const metricPayload = buildMetricPayloadForChart(htmlEl)
      echartsBlockMetricPayload.set(htmlEl, metricPayload)

      // 渲染图表类型切换工具栏（含指标查看/解读/列表明细按钮）
      renderEchartsToolbar(htmlEl, initialType, !!metricPayload)
    } catch (e) {
      console.error('[ChatView] ECharts block mount error:', e)
      htmlEl.textContent = 'Chart render error'
      htmlEl.classList.add('echarts-error')
    }
  })
}

/** 发送消息 */
function handleSend(): void {
  const message = inputMessage.value.trim()
  if ((!message && pendingAttachments.value.length === 0) || chatStore.isStreaming || !chatStore.currentAgentId) return
  inputMessage.value = ''
  userScrolledUp.value = false
  // 将附件转为 contentParts 传给后端，同时保留附件信息用于前端展示
  const attachments = [...pendingAttachments.value]
  pendingAttachments.value = []
  const contentParts: MessageContentPart[] = []
  if (message) {
    contentParts.push({ type: 'text', text: message })
  }
  for (const att of attachments) {
    const isImage = att.contentType?.startsWith('image/')
    contentParts.push({
      type: isImage ? 'image' : 'file',
      fileName: att.fileName,
      storedName: att.storedName,
      path: att.path,
      contentType: att.contentType,
      fileSize: att.size,
    })
  }
  chatStore.sendMessage(chatStore.currentAgentId, message, contentParts)
  // 发送后将附件附加到最后一条用户消息（前端展示用）
  if (attachments.length > 0) {
    const lastMsg = chatStore.messages[chatStore.messages.length - 2]
    if (lastMsg && lastMsg.role === 'user') {
      lastMsg.attachments = attachments
    }
  }
  scrollToBottom(true)
}

/** 触发文件选择 */
function handleFileSelect(): void {
  fileInputRef.value?.click()
}

/** 处理粘贴事件，支持粘贴图片 */
function handlePaste(event: ClipboardEvent): void {
  const items = event.clipboardData?.items
  if (!items) return
  const imageFiles: File[] = []
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        imageFiles.push(file)
      }
    }
  }
  if (imageFiles.length > 0) {
    // 阻止默认粘贴行为（避免图片以文本形式插入输入框）
    event.preventDefault()
    uploadFiles(imageFiles)
  }
}

/** 上传文件列表（供粘贴和文件选择共用） */
async function uploadFiles(files: File[]): Promise<void> {
  let convId = chatStore.conversationId
  if (!convId) {
    convId = self.crypto.randomUUID ? self.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    chatStore.conversationId = convId
  }

  isUploading.value = true
  try {
    const uploadPromises = files.map(file => uploadAttachment(convId, file))
    const results = await Promise.allSettled(uploadPromises)
    for (const result of results) {
      if (result.status === 'fulfilled') {
        pendingAttachments.value.push({
          fileName: result.value.fileName,
          storedName: result.value.storedName,
          url: result.value.url,
          path: result.value.path,
          size: result.value.size,
          contentType: result.value.contentType,
        })
      } else {
        console.warn('[ChatView] Attachment upload failed:', result.reason)
      }
    }
  } finally {
    isUploading.value = false
  }
}

/** 处理文件选择并上传（支持多文件） */
async function handleFileChange(event: Event): void {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (!files || files.length === 0) return
  // 重置 input 以便再次选择同一文件
  target.value = ''

  await uploadFiles(Array.from(files))
}

/** 移除待发送附件 */
function removeAttachment(idx: number): void {
  pendingAttachments.value.splice(idx, 1)
}

/** 一键优化输入内容 */
async function handleOptimize(): Promise<void> {
  const text = inputMessage.value.trim()
  if (!text || isOptimizing.value || chatStore.isStreaming) return
  isOptimizing.value = true
  try {
    const result = await optimizePrompt(text)
    if (result.optimized) {
      inputMessage.value = result.optimized
    }
  } catch (e) {
    console.warn('[ChatView] Optimize failed:', e)
  } finally {
    isOptimizing.value = false
  }
}

/** 获取用户消息的附件列表 */
function getUserAttachments(msg: typeof chatStore.messages.value[0]): ChatAttachment[] | null {
  if (!msg.attachments || msg.attachments.length === 0) return null
  return msg.attachments
}

/** 预览图片（新窗口打开） */
function previewImage(url: string): void {
  window.open(url, '_blank')
}

/** 键盘事件处理：Enter发送，Ctrl+Enter换行 */
function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter') {
    if (event.ctrlKey || event.metaKey) {
      // Ctrl+Enter 或 Cmd+Enter 换行
      event.preventDefault()
      const target = event.target as HTMLTextAreaElement
      const start = target.selectionStart
      const end = target.selectionEnd
      const value = inputMessage.value
      inputMessage.value = value.substring(0, start) + '\n' + value.substring(end)
      // 设置光标位置到新行的开头
      nextTick(() => {
        target.selectionStart = target.selectionEnd = start + 1
      })
    } else {
      // 单独 Enter 发送消息
      event.preventDefault()
      handleSend()
    }
  }
}

/** 停止生成 */
function handleStop(): void {
  chatStore.stopChat()
}

/** 历史推荐问题展开状态 */
const expandedHistoryRecQuestions = reactive(new Set<string>())

/** 判断指定消息索引是否为最后一条 assistant 消息（即最新的推荐问题应展开显示） */
function isLatestRecommendedQuestions(msgIndex: number): boolean {
  const msgs = chatStore.messages
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (msgs[i].role === 'assistant') {
      return i === msgIndex
    }
  }
  return false
}

/** 切换历史推荐问题的展开/折叠状态 */
function toggleHistoryRecQuestion(key: string): void {
  if (expandedHistoryRecQuestions.has(key)) {
    expandedHistoryRecQuestions.delete(key)
  } else {
    expandedHistoryRecQuestions.add(key)
  }
}

/** 追问点击 */
function handleFollowup(text: string): void {
  if (chatStore.isStreaming || !chatStore.currentAgentId) return
  chatStore.sendMessage(chatStore.currentAgentId, text)
}

/** 智能问数快捷菜单点击 */
function handleSmartAskMenu(item: { key: string; label: string }): void {
  if (chatStore.isStreaming || !chatStore.currentAgentId) return
  const promptMap: Record<string, string> = {
    interpret: t('smartAskMenu.interpretPrompt'),
    report: t('smartAskMenu.reportPrompt'),
    insight: t('smartAskMenu.insightPrompt'),
    compare: t('smartAskMenu.comparePrompt'),
    forecast: t('smartAskMenu.forecastPrompt'),
    anomaly: t('smartAskMenu.anomalyPrompt'),
  }
  const prompt = promptMap[item.key] || t('smartAskMenu.defaultPrompt')
  chatStore.sendMessage(chatStore.currentAgentId, prompt)
}

/** 确认 QueryPlan */
function confirmQueryPlan(msgIndex: number, cardIndex: number): void {
  queryPlanConfirmed[`${msgIndex}-${cardIndex}`] = true
}

/** 确认澄清卡片 */
function confirmClarify(msgIndex: number, cardIndex: number): void {
  clarifyConfirmed[`${msgIndex}-${cardIndex}`] = true
}

/** 修改 QueryPlan 字段 */
function handleModify(field: string): void {
  console.log('Modify field:', field)
}

/** 用户是否已主动向上翻看历史（生成过程中不再自动下滚） */
const userScrolledUp = ref(false)

/** 是否显示「回到底部」悬浮按钮：有消息且用户已向上翻看时展示 */
const showScrollToBottom = computed(
  () => userScrolledUp.value && chatStore.messages.length > 0
)

/** 用户提问 DOM 引用映射，key = 消息索引（用于右侧导航跳转） */
const userMsgRefs = new Map<number, HTMLElement>()

/** 收集/移除用户消息 DOM 引用 */
function setUserMsgRef(el: HTMLElement | null, msgIndex: number): void {
  if (el) {
    userMsgRefs.set(msgIndex, el)
  } else {
    userMsgRefs.delete(msgIndex)
  }
}

/** 右侧导航项：所有用户提问（携带原始消息索引与内容） */
const userQuestions = computed(() =>
  chatStore.messages
    .map((msg, index) => ({ msg, index }))
    .filter(({ msg }) => msg.role === 'user')
    .map(({ msg, index }) => ({ index, content: (msg.content || '').trim() }))
)

/** 当前视口内高亮的提问消息索引 */
const activeQuestionIndex = ref<number>(-1)

/** 跳转到指定消息（右侧导航点击） */
function scrollToMessage(msgIndex: number): void {
  const container = chatAreaRef.value
  const el = userMsgRefs.get(msgIndex)
  if (!container || !el) {
    return
  }
  // 跳转到历史提问时禁用自动下滚，避免流式生成打断定位
  userScrolledUp.value = true
  const top = container.scrollTop + (el.getBoundingClientRect().top - container.getBoundingClientRect().top) - 16
  container.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
  activeQuestionIndex.value = msgIndex
}

/** rAF 节流句柄，避免滚动时高频计算高亮项 */
let activeQuestionRaf = 0

/** 根据滚动位置计算当前高亮的提问项 */
function updateActiveQuestion(): void {
  const container = chatAreaRef.value
  if (!container) {
    return
  }
  const containerTop = container.getBoundingClientRect().top
  let active = -1
  for (const { index } of userQuestions.value) {
    const el = userMsgRefs.get(index)
    if (!el) {
      continue
    }
    // 提问顶部越过容器顶部一定阈值即视为“进入该段落”
    if (el.getBoundingClientRect().top - containerTop <= 80) {
      active = index
    } else {
      break
    }
  }
  if (active === -1 && userQuestions.value.length > 0) {
    active = userQuestions.value[0].index
  }
  activeQuestionIndex.value = active
}

/** rAF 节流包装：滚动/更新时调用 */
function scheduleActiveQuestionUpdate(): void {
  if (activeQuestionRaf) {
    return
  }
  activeQuestionRaf = requestAnimationFrame(() => {
    activeQuestionRaf = 0
    updateActiveQuestion()
  })
}

/** 判断当前滚动位置是否在底部附近 */
function isNearBottom(): boolean {
  const el = chatAreaRef.value
  if (!el) {
    return true
  }
  const threshold = 50
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
}

/** 滚动到底部（仅在用户未主动上翻时执行） */
function scrollToBottom(force = false): void {
  nextTick(() => {
    if (!chatAreaRef.value) {
      return
    }
    if (force || !userScrolledUp.value) {
      chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight
    }
  })
}

/** 监听聊天区域滚动，检测用户是否主动向上翻看 */
function handleScroll(): void {
  if (isNearBottom()) {
    userScrolledUp.value = false
  } else {
    userScrolledUp.value = true
  }
  scheduleActiveQuestionUpdate()
}

/** 窗口缩放处理 */
function handleResize(): void {
  chartInstances.forEach(instance => instance.resize())
  echartsInstances.forEach(instance => instance.resize())
  echartsBlockInstances.forEach(instance => {
    if (!instance.isDisposed()) {
      instance.resize()
    }
  })
}

// 合并 messages.length 与最后一条 content 的 watch，避免同一 tick 内重复
// 触发 scanAndMountEChartsBlocks（流式期间频率极高，重复扫描显著卡顿）。
watch(
  () => [
    chatStore.messages.length,
    chatStore.messages[chatStore.messages.length - 1]?.content,
    chatStore.isStreaming,
  ],
  () => {
    scrollToBottom()
    nextTick(() => {
      scanAndMountEChartsBlocks()
      updateActiveQuestion()
    })
  }
)

onMounted(() => {
  window.addEventListener('resize', handleResize)
  chatAreaEl = chatAreaRef.value
  chatAreaEl?.addEventListener('scroll', handleScroll)
  // 加载数据源列表（用于输入框上方数据源选择器）
  loadDatasources()
  // 切回对话菜单时，强制重拉当前会话历史消息以触发 ECharts 重新挂载。
  // 但若 sessionStorage 中存在 reconnect 状态（lastEventId 已持久化），
  // 说明 MainLayout 正在/即将发起 SSE 续连，此时再 force=true 拉历史会：
  //   1) 清空 lastEventId/seenEventIds 破坏续连去重；
  //   2) 整体替换 messages 数组导致 reconnect 中 FlushBuffer 持有的 msgIndex 错位；
  //   3) 抢占浏览器并发额度延迟 SSE 重连。
  // 此场景下跳过 force 拉取，让 reconnect 流式完成；仅当无续连状态时才 force 重拉。
  const hasPendingReconnect = chatStore.hasPendingReconnect()
  if (chatStore.conversationId && !chatStore.isStreaming && !hasPendingReconnect) {
    chatStore.switchConversation(chatStore.conversationId, true).finally(() => {
      nextTick(scanAndMountEChartsBlocks)
    })
  } else {
    nextTick(scanAndMountEChartsBlocks)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chatAreaEl?.removeEventListener('scroll', handleScroll)
  if (activeQuestionRaf) {
    cancelAnimationFrame(activeQuestionRaf)
    activeQuestionRaf = 0
  }
  chartInstances.forEach(instance => instance.dispose())
  chartInstances.clear()
  echartsInstances.forEach(instance => instance.dispose())
  echartsInstances.clear()
  echartsBlockInstances.forEach(instance => {
    if (!instance.isDisposed()) {
      instance.dispose()
    }
  })
  echartsBlockInstances.clear()
  echartsBlockOptions.clear()
  echartsBlockTypes.clear()
  echartsBlockVisibleTable.clear()
  echartsBlockObservers.forEach(observer => observer.disconnect())
  echartsBlockObservers.clear()
  echartsBlockMetricPayload.clear()
  echartsBlockMetricMeta.clear()
  echartsBlockInterpret.clear()
  echartsBlockPristine.clear()
  if (dropdownOutsideHandler) {
    document.removeEventListener('click', dropdownOutsideHandler)
    dropdownOutsideHandler = null
  }
})
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--theme-bg);
}

/* 消息区容器：撑满输入区上方空间，作为悬浮按钮的定位参照 */
.chat-main {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* 回到底部悬浮按钮：锚定在消息区底部（即输入框上方）居中 */
.scroll-to-bottom {
  position: absolute;
  left: 50%;
  bottom: 16px;
  transform: translateX(-50%);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--theme-border);
  border-radius: 50%;
  background: var(--theme-surface);
  color: var(--theme-text-secondary);
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease, transform 0.15s ease;
}

.scroll-to-bottom:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 35%, transparent);
}

.scroll-to-bottom svg {
  width: 18px;
  height: 18px;
}

.scroll-btn-fade-enter-active,
.scroll-btn-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.scroll-btn-fade-enter-from,
.scroll-btn-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(8px);
}

/* 右侧快速导航栏：默认竖排圆点，悬浮整条时一次性展开为完整提问列表。
   关键：展开由容器整体 :hover 触发（而非单个标记），容器只增不减，
   指针始终落在容器内，避免逐项悬浮时因高度变化导致的抖动。 */
.chat-nav {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 9;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  max-height: 78%;
  padding: 6px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: thin;
  transition: background 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

/* 悬浮整条导航 → 变为浮层卡片 */
.chat-nav:hover {
  background: var(--theme-surface);
  border-color: var(--theme-border);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.16);
}

.chat-nav__item {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 4px 6px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  transition: background 0.12s ease;
}

.chat-nav:hover .chat-nav__item:hover {
  background: var(--theme-surface-hover);
}

.chat-nav:hover .chat-nav__item.is-active {
  background: color-mix(in srgb, var(--main-orange) 12%, transparent);
}

/* 标记点：默认短横线，激活/悬浮所在项时加粗变色 */
.chat-nav__dot {
  flex-shrink: 0;
  width: 16px;
  height: 3px;
  border-radius: 2px;
  background: var(--theme-border-strong);
  transition: width 0.15s ease, background 0.15s ease;
}

.chat-nav__item:hover .chat-nav__dot,
.chat-nav__item.is-active .chat-nav__dot {
  width: 22px;
  background: var(--main-orange);
}

/* 提问内容：默认在宽、高两个方向都收起（否则文字仍会撑开行高），
   悬浮整条导航时统一展开为列表行 */
.chat-nav__label {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 0;
  max-height: 0;
  min-width: 0;
  opacity: 0;
  overflow: hidden;
  text-align: left;
  transition: width 0.18s ease, max-height 0.18s ease, opacity 0.18s ease;
}

.chat-nav:hover .chat-nav__label {
  width: 210px;
  max-height: 44px;
  opacity: 1;
}

.chat-nav__seq {
  flex-shrink: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--main-orange) 14%, transparent);
  color: var(--main-orange);
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}

.chat-nav__item.is-active .chat-nav__seq {
  background: var(--main-orange);
  color: #fff;
}

/* 提问文本：最多显示 2 行，超出省略 */
.chat-nav__text {
  flex: 1;
  min-width: 0;
  color: var(--theme-text);
  font-size: 12px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.chat-nav-fade-enter-active,
.chat-nav-fade-leave-active {
  transition: opacity 0.2s ease;
}

.chat-nav-fade-enter-from,
.chat-nav-fade-leave-to {
  opacity: 0;
}

/* 统一的行内 SVG 图标：随文字大小缩放，替代 emoji 作为功能图标 */
.icon-inline {
  width: 1em;
  height: 1em;
  flex-shrink: 0;
  vertical-align: -0.125em;
}

.seg-tool__arrow svg,
.seg-narration__arrow svg,
.seg-execution__arrow svg {
  width: 100%;
  height: 100%;
  display: block;
}

.chat-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 40px 20px;
}

.empty-avatar {
  width: 88px;
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  margin-bottom: 24px;
  background: linear-gradient(135deg, var(--main-orange) 0%, var(--dark-orange) 100%);
  box-shadow: 0 8px 24px color-mix(in srgb, var(--main-orange) 18%, transparent);
}

.empty-avatar__icon {
  width: 48px;
  height: 48px;
}

.empty-greeting {
  text-align: center;
  margin-bottom: 32px;
}

.empty-greeting__title {
  font-size: 22px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0 0 8px;
}

.empty-greeting__subtitle {
  font-size: 14px;
  color: var(--theme-text-secondary);
  margin: 0;
}

/* 智能问数快捷菜单 */
.smart-ask-menu {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  max-width: 420px;
  margin-bottom: 32px;
}

.smart-ask-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 24px;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface);
  color: var(--theme-text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.smart-ask-chip:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 35%, transparent);
}

.chip-icon {
  display: inline-flex;
  width: 16px;
  height: 16px;
}

.chip-icon svg {
  width: 100%;
  height: 100%;
  display: block;
}

.chip-label {
  white-space: nowrap;
}

.msg {
  display: flex;
  gap: 10px;
  max-width: 86%;
}

.msg.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg.ai {
  align-self: flex-start;
}

.ai-content-wrapper {
  display: flex;
  flex-direction: column;
}

.user-content-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.avatar {
  width: 28px;
  height: 22px;
  border-radius: 6px;
  background: var(--main-orange);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.bubble {
  border-radius: 16px;
  padding: 14px 18px;
  font-size: 14px;
  line-height: 1.7;
}

.ai-bubble {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  color: var(--body-text);
}

.user-bubble {
  background: var(--main-orange);
  color: #fff;
  /* 保留用户输入中的换行（Ctrl+Enter），同时正常自动折行 */
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.msg-text {
  color: var(--body-text);
  font-size: 15px;
  line-height: 1.75;
  letter-spacing: 0.01em;
}

.msg-text :deep(h1),
.msg-text :deep(h2),
.msg-text :deep(h3),
.msg-text :deep(h4),
.msg-text :deep(h5),
.msg-text :deep(h6) {
  color: var(--theme-text);
  font-weight: 600;
  line-height: 1.4;
  margin: 1.6em 0 0.6em;
}

.msg-text :deep(h1) { font-size: 1.5em; }
.msg-text :deep(h2) { font-size: 1.35em; }
.msg-text :deep(h3) { font-size: 1.2em; }
.msg-text :deep(h4) { font-size: 1.1em; }
.msg-text :deep(h5) { font-size: 1em; }
.msg-text :deep(h6) { font-size: 0.95em; color: var(--theme-text-secondary); }

.msg-text :deep(h1:first-child),
.msg-text :deep(h2:first-child),
.msg-text :deep(h3:first-child),
.msg-text :deep(h4:first-child),
.msg-text :deep(h5:first-child),
.msg-text :deep(h6:first-child) {
  margin-top: 0;
}

.msg-text :deep(p) {
  margin: 0.75em 0;
}

.msg-text :deep(p:first-child) {
  margin-top: 0;
}

.msg-text :deep(p:last-child) {
  margin-bottom: 0;
}

.msg-text :deep(a) {
  color: var(--main-orange);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.15s;
}

.msg-text :deep(a:hover) {
  border-bottom-color: color-mix(in srgb, var(--main-orange) 40%, transparent);
}

.msg-text :deep(strong) {
  color: var(--theme-text);
  font-weight: 600;
}

.msg-text :deep(em) {
  font-style: italic;
}

.msg-text :deep(ul),
.msg-text :deep(ol) {
  padding-left: 1.6em;
  margin: 0.75em 0;
}

.msg-text :deep(li) {
  margin: 0.4em 0;
}

.msg-text :deep(li > ul),
.msg-text :deep(li > ol) {
  margin: 0.25em 0;
}

.msg-text :deep(pre) {
  background: var(--near-white);
  color: var(--body-text);
  border: 1px solid var(--theme-border);
  border-radius: 10px;
  padding: 16px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
  margin: 1em 0;
}

.msg-text :deep(pre:first-child) {
  margin-top: 0;
}

.msg-text :deep(pre:last-child) {
  margin-bottom: 0;
}

.msg-text :deep(code) {
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 0.85em;
}

.msg-text :deep(:not(pre) > code) {
  background: rgba(0, 0, 0, 0.05);
  color: var(--theme-text);
  padding: 2px 6px;
  border-radius: 5px;
  font-size: 0.82em;
}

.msg-text :deep(blockquote) {
  border-left: 3px solid var(--theme-border-strong);
  margin: 1em 0;
  padding: 8px 16px;
  color: var(--theme-text-secondary);
  border-radius: 0 8px 8px 0;
}

.msg-text :deep(blockquote p) {
  margin: 0.4em 0;
}

.msg-text :deep(blockquote p:first-child) {
  margin-top: 0;
}

.msg-text :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

.msg-text :deep(hr) {
  border: none;
  border-top: 1px solid var(--theme-border);
  margin: 1.5em 0;
}

.msg-text :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 0.5em 0;
}

.msg-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
  font-size: 13px;
  border-radius: 8px;
  overflow: hidden;
}

.msg-text :deep(th),
.msg-text :deep(td) {
  border: 1px solid var(--theme-border);
  padding: 8px 14px;
  text-align: left;
}

.msg-text :deep(th) {
  background: var(--theme-surface-elevated);
  font-weight: 600;
  color: var(--theme-text-secondary);
}

.msg-text :deep(tbody tr:nth-child(even)) {
  background: var(--theme-surface-hover);
}

.streaming-cursor {
  display: inline-block;
  width: 8px;
  height: 16px;
  background: var(--main-orange);
  border-radius: 2px;
  animation: pulse 1s ease-in-out infinite;
}

.streaming-cursor-end {
  display: inline-block;
  width: 8px;
  height: 16px;
  background: var(--main-orange);
  border-radius: 2px;
  animation: pulse 1s ease-in-out infinite;
  margin-left: 36px;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

/* QueryPlan */
.qp-box {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-left: 2px solid var(--main-orange);
  border-radius: 12px;
  padding: 16px;
  margin-left: 0;
  position: relative;
  align-self: flex-start;
  max-width: 86%;
}

.qp-accent {
  display: none;
}

.qp-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-secondary);
  margin-bottom: 10px;
}

.qp-row {
  display: flex;
  align-items: center;
  padding: 5px 0;
  font-size: 13px;
  color: var(--theme-text);
  border-radius: 6px;
  transition: background 0.15s;
}

.qp-row:hover {
  background: var(--theme-surface-hover);
}

.qp-label {
  width: 40px;
  color: var(--muted);
  font-weight: 500;
  flex-shrink: 0;
}

.qp-val {
  flex: 1;
  color: var(--theme-text);
}

.qp-modify {
  padding: 2px 10px;
  border-radius: 6px;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface-elevated);
  font-size: 11px;
  color: var(--theme-text-secondary);
  cursor: pointer;
  flex-shrink: 0;
  margin-left: 4px;
  font-family: inherit;
  transition: all 0.15s;
}

.qp-modify:hover {
  border-color: color-mix(in srgb, var(--main-orange) 35%, transparent);
  color: var(--main-orange);
}

.qp-confirm {
  float: right;
  padding: 6px 18px;
  border-radius: 8px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  margin-top: 10px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s;
}

.qp-confirm:hover:not(:disabled) {
  background: var(--dark-orange);
}

.qp-confirm.confirmed {
  background: var(--muted);
  cursor: default;
}

/* Insight Bar */
.insight-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 12px 16px;
  margin-left: 0;
  font-size: 13px;
  color: var(--theme-text-secondary);
  align-self: flex-start;
  max-width: 86%;
}

/* Chart Card */
.chart-box {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 16px;
  margin-left: 0;
  align-self: flex-start;
  max-width: 86%;
}

.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-secondary);
  margin-bottom: 10px;
}

.mid-chart {
  width: 100%;
  height: 160px;
}

/* ECharts Markdown 内嵌代码块占位元素 */
:deep(.echarts-block) {
  width: 100%;
  min-height: 350px;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  margin: 10px 0;
  position: relative;
  padding-top: 36px;
}

:deep(.echarts-block.echarts-error) {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60px;
  color: var(--muted);
  font-size: 12px;
  padding-top: 0;
}

/* ECharts 图表类型下拉选择器 */
:deep(.echarts-toolbar) {
  position: absolute;
  top: 0;
  right: 8px;
  left: 8px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  /* 提高 z-index 到 20，确保盖在 ECharts 内部 canvas/zr 之上，
     避免 ECharts 的 zr handler 拦截 toolbar 按钮的 click */
  z-index: 20;
}

:deep(.echarts-toolbar > *) {
  pointer-events: auto;
}

/* 列表明细独立按钮 */
:deep(.echarts-toolbar-detail-btn) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  background: var(--theme-surface-elevated);
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s ease;
}

:deep(.echarts-toolbar-detail-btn:hover) {
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 30%, transparent);
  background: var(--theme-surface-hover);
}

:deep(.echarts-toolbar-detail-btn.is-active) {
  color: #fff;
  background: var(--main-orange);
  border-color: var(--main-orange);
}

:deep(.echarts-toolbar-detail-icon) {
  display: inline-flex;
  width: 12px;
  height: 12px;
}

:deep(.echarts-toolbar-detail-icon svg) {
  width: 100%;
  height: 100%;
  display: block;
}

:deep(.echarts-toolbar-trigger) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  background: var(--theme-surface-elevated);
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s ease;
}

:deep(.echarts-toolbar-trigger:hover),
:deep(.echarts-toolbar-trigger.is-open) {
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 30%, transparent);
  background: var(--theme-surface-hover);
}

:deep(.echarts-toolbar-trigger-arrow) {
  font-size: 10px;
  line-height: 1;
}

:deep(.echarts-toolbar-panel) {
  position: absolute;
  top: 30px;
  right: 0;
  min-width: 130px;
  margin: 0;
  padding: 4px 0;
  list-style: none;
  background: var(--theme-surface-elevated);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  display: none;
  z-index: 10;
}

:deep(.echarts-toolbar-panel.is-open) {
  display: block;
}

:deep(.echarts-toolbar-panel-item) {
  height: 30px;
  padding: 0 12px;
  line-height: 30px;
  font-size: 12px;
  color: var(--theme-text);
  cursor: pointer;
  transition: all 0.15s ease;
}

:deep(.echarts-toolbar-panel-item:hover) {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

:deep(.echarts-toolbar-panel-item.is-active) {
  color: var(--main-orange);
  font-weight: 600;
  background: var(--theme-surface-hover);
}

/* 列表明细浮层（叠加在图表上，不销毁 ECharts 实例） */
:deep(.echarts-table-overlay) {
  position: absolute;
  top: 38px;
  left: 6px;
  right: 6px;
  bottom: 6px;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  z-index: 5;
  /* 不设置 overflow，避免破坏 thead sticky 定位上下文 */
  isolation: isolate;
}

:deep(.echarts-table-overlay-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 14px;
  border-bottom: 1px solid var(--theme-border);
  background: var(--theme-surface-elevated);
  flex-shrink: 0;
  border-top-left-radius: 10px;
  border-top-right-radius: 10px;
}

:deep(.echarts-table-overlay-title) {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
}

:deep(.echarts-table-overlay-close) {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s ease;
}

:deep(.echarts-table-overlay-close:hover) {
  background: var(--theme-surface-hover);
  color: var(--theme-text);
}

:deep(.echarts-table-overlay-body) {
  flex: 1;
  overflow: auto;
  border-bottom-left-radius: 10px;
  border-bottom-right-radius: 10px;
}

:deep(.echarts-table-overlay-body > .echarts-table-empty) {
  padding: 24px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}

:deep(.echarts-table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  color: var(--theme-text);
}

:deep(.echarts-table th),
:deep(.echarts-table td) {
  border: 1px solid var(--theme-border);
  padding: 8px 12px;
  text-align: left;
  white-space: nowrap;
}

/* 表头吸顶：相对 body 滚动容器（外层 overlay 不再设 overflow） */
:deep(.echarts-table thead th) {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--theme-surface-elevated);
  box-shadow: 0 1px 0 var(--theme-border);
}

:deep(.echarts-table tbody tr:hover) {
  background: var(--theme-surface-hover);
}

/* ===== 指标查看 / 解读 浮层内容 ===== */
:deep(.echarts-aux-body) {
  padding: 14px 16px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--theme-text);
}

:deep(.echarts-aux-loading),
:deep(.echarts-aux-empty) {
  padding: 24px 16px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}

/* 指标查看：字段定义列表 */
:deep(.metric-meta) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

:deep(.metric-meta__group) {
  display: grid;
  grid-template-columns: 68px 1fr;
  gap: 10px;
  align-items: start;
}

:deep(.metric-meta__label) {
  color: var(--muted);
  font-size: 12px;
  padding-top: 1px;
}

:deep(.metric-meta__value) {
  color: var(--theme-text);
  word-break: break-word;
}

:deep(.metric-meta__item) {
  padding: 6px 0;
}

:deep(.metric-meta__item + .metric-meta__item) {
  border-top: 1px dashed var(--theme-border);
}

:deep(.metric-meta__name) {
  font-weight: 600;
  color: var(--theme-text);
}

:deep(.metric-meta__en) {
  margin-left: 6px;
  font-weight: 400;
  font-size: 11px;
  color: var(--muted);
}

:deep(.metric-meta__caliber) {
  margin-top: 3px;
  color: var(--theme-text-secondary);
}

:deep(.metric-meta__k) {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--main-orange) 12%, transparent);
  color: var(--main-orange);
  font-size: 11px;
}

:deep(.metric-meta__extra) {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
}

/* 解读：Markdown 正文 */
:deep(.echarts-interpret) {
  font-size: 13px;
  line-height: 1.7;
  color: var(--theme-text);
}

:deep(.echarts-interpret) p {
  margin: 0 0 8px;
}

:deep(.echarts-interpret) ul,
:deep(.echarts-interpret) ol {
  margin: 4px 0 8px;
  padding-left: 20px;
}

:deep(.echarts-interpret) strong {
  color: var(--theme-text);
}

/* ECharts Option Card（后端返回标准 ECharts option 时直接渲染） */
.echarts-box {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 16px;
  margin-left: 0;
  align-self: flex-start;
  max-width: 86%;
}

.echarts-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-secondary);
  margin-bottom: 10px;
}

.echarts-chart {
  width: 100%;
  min-height: 300px;
}

/* Clarify Card */
.clarify-card {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-left: 2px solid var(--main-orange);
  border-radius: 12px;
  padding: 16px;
  margin-left: 0;
  position: relative;
  align-self: flex-start;
  max-width: 86%;
}

.clarify-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-secondary);
  margin-bottom: 6px;
}

.clarify-desc {
  font-size: 13px;
  color: var(--body-text);
  margin-bottom: 12px;
}

.clarify-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}

.clarify-opt {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--theme-border);
  cursor: pointer;
  font-size: 13px;
  color: var(--theme-text);
  transition: all 0.15s;
}

.clarify-opt:hover {
  border-color: color-mix(in srgb, var(--main-orange) 30%, transparent);
  background: var(--theme-surface-hover);
}

.clarify-opt.selected {
  border-color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 6%, transparent);
}

.clarify-opt input[type="radio"] {
  accent-color: var(--main-orange);
}

.clarify-opt .recommend {
  font-size: 10px;
  color: var(--main-orange);
  font-weight: 600;
  margin-left: 4px;
}

.clarify-confirm {
  display: block;
  margin: 0 auto;
  padding: 7px 28px;
  border-radius: 8px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s;
}

.clarify-confirm:hover:not(:disabled) {
  background: var(--dark-orange);
}

.clarify-confirm.confirmed {
  background: var(--muted);
  cursor: default;
}

/* Dashboard Preview Card */
.dash-card {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 16px;
  margin-left: 0;
  align-self: flex-start;
  max-width: 86%;
}

.dash-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-secondary);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dash-kpi-row {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.dash-kpi {
  text-align: center;
  flex: 1;
}

.dash-kpi-val {
  font-size: 18px;
  font-weight: 700;
  color: var(--theme-text);
}

.dash-kpi-name {
  font-size: 11px;
  color: var(--muted);
}

.dash-link {
  font-size: 13px;
  color: var(--main-orange);
  font-weight: 600;
  cursor: pointer;
  text-align: right;
  transition: color 0.15s;
}

.dash-link:hover {
  color: var(--dark-orange);
}

/* Followup Chips */
.followup-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 0 0;
  align-self: flex-start;
}

.followup-chip {
  padding: 7px 16px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface);
  color: var(--theme-text-secondary);
  transition: all 0.15s;
}

.followup-chip:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 30%, transparent);
}

/* Recommended Questions */
.recommended-questions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  align-self: flex-start;
  max-width: 86%;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  border-left: 2px solid var(--main-orange);
}

.recommended-questions__header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-text-secondary);
  margin-bottom: 4px;
}

.recommended-questions__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.recommended-question-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface);
  color: var(--theme-text);
  font-size: 13px;
  line-height: 1.5;
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
  width: 100%;
  font-family: inherit;
}

.recommended-question-item:hover {
  background: var(--theme-surface-hover);
  border-color: color-mix(in srgb, var(--main-orange) 30%, transparent);
  color: var(--main-orange);
}

.recommended-question-item__icon {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  margin-top: 3px;
  color: var(--muted);
  transition: color 0.15s;
}

.recommended-question-item:hover .recommended-question-item__icon {
  color: var(--main-orange);
}

.recommended-question-item__text {
  flex: 1;
  min-width: 0;
}

/* 折叠状态：隐藏问题列表 */
.recommended-questions.is-collapsed .recommended-questions__list {
  display: none;
}

.recommended-questions.is-collapsed .recommended-questions__header {
  cursor: pointer;
}

.recommended-questions.is-collapsed .recommended-questions__header:hover {
  opacity: 0.8;
}

.recommended-questions__toggle {
  font-size: 11px;
  font-weight: 400;
  color: var(--main-orange);
  margin-left: auto;
}

/* Feedback */
.feedback {
  display: flex;
  gap: 12px;
  padding: 8px 0 0;
  font-size: 12px;
  color: var(--muted);
  align-self: flex-start;
}

.feedback span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  padding: 5px 10px;
  border-radius: 8px;
  transition: background 0.15s;
}

.feedback-icon {
  display: inline-flex;
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.feedback-icon svg {
  width: 100%;
  height: 100%;
  display: block;
}

.feedback span:hover {
  background: var(--theme-surface-hover);
}

.feedback span.active {
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  color: var(--dark-orange);
}

/* 消息操作栏（位于气泡外部） */
.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  padding-top: 5px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.msg:hover .msg-actions {
  opacity: 1;
  pointer-events: auto;
}

.msg-actions--ai {
  justify-content: flex-start;
}

.msg-actions--user {
  justify-content: flex-start;
  padding-right: 4px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--muted, #94a3b8);
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-btn:hover {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

.action-btn.copied {
  color: #10b981;
}

.msg-time {
  font-size: 11px;
  color: var(--muted, #94a3b8);
  white-space: nowrap;
  line-height: 28px;
  margin-right: 4px;
}

/* Datasource Selector Toolbar */
.ds-toolbar {
  display: flex;
  align-items: center;
  padding: 6px 20px;
  border-top: 1px solid var(--theme-border);
  background: var(--theme-bg);
  flex-shrink: 0;
}

.ds-trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border-radius: 14px;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface-elevated);
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
  user-select: none;
  line-height: 1.5;
}

.ds-trigger:hover {
  border-color: color-mix(in srgb, var(--main-orange) 30%, transparent);
  color: var(--main-orange);
  background: var(--theme-surface-hover);
}

.ds-trigger.active {
  border-color: color-mix(in srgb, var(--main-orange) 35%, transparent);
  color: var(--dark-orange);
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  font-weight: 600;
}

.ds-trigger-icon {
  display: inline-flex;
  width: 12px;
  height: 12px;
}

.ds-trigger-icon svg {
  width: 100%;
  height: 100%;
  display: block;
}

.ds-trigger-text {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ds-trigger-arrow {
  font-size: 10px;
  margin-left: 2px;
  opacity: 0.6;
}

/* Popover */
.ds-popover {
  padding: 0;
}

.ds-popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
  border-bottom: 1px solid var(--theme-border);
}

.ds-popover-clear {
  font-size: 12px;
  font-weight: 400;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.15s;
}

.ds-popover-clear:hover {
  color: var(--main-orange);
}

.ds-popover-list {
  max-height: 320px;
  overflow-y: auto;
  padding: 6px 0;
}

.ds-popover-list::-webkit-scrollbar {
  width: 5px;
}

.ds-popover-list::-webkit-scrollbar-track {
  background: transparent;
}

.ds-popover-list::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.12);
  border-radius: 3px;
}

.ds-popover-list::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}

.ds-popover-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 6px;
  margin: 0 6px;
  transition: all 0.12s;
  font-size: 13px;
  color: var(--theme-text);
  line-height: 1.4;
}

.ds-popover-item:hover {
  background: var(--theme-surface-hover);
}

.ds-popover-item.checked {
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  color: var(--dark-orange);
}

.ds-popover-item input[type="checkbox"] {
  accent-color: var(--main-orange);
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  cursor: pointer;
}

.ds-item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.ds-item-type {
  font-size: 10px;
  color: var(--muted);
  background: var(--theme-surface-hover);
  padding: 2px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}

.ds-popover-item.checked .ds-item-type {
  color: var(--dark-orange);
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
}

/* Input Bar */
.input-bar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 20px 20px;
  border-top: 1px solid var(--theme-border);
  background: var(--theme-bg);
  flex-shrink: 0;
}

.input-bar__card {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  min-height: 52px;
  max-height: 180px;
  padding: 8px 12px 8px 18px;
  border-radius: 28px;
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-surface);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.input-bar__card:focus-within {
  border-color: var(--main-orange);
  box-shadow: 0 2px 16px color-mix(in srgb, var(--main-orange) 12%, transparent);
}

.chat-input {
  flex: 1;
  min-height: 34px;
  max-height: 140px;
  padding: 8px 0;
  margin: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--theme-text);
  font-size: 14px;
  line-height: 1.5;
  font-family: inherit;
  resize: none;
  outline: none;
}

.chat-input::placeholder {
  color: var(--theme-text-muted);
}

.chat-input:disabled {
  opacity: 0.7;
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding-bottom: 2px;
}

.input-actions__divider {
  width: 1px;
  height: 20px;
  background: var(--theme-border);
  margin: 0 4px;
  flex-shrink: 0;
}

.input-actions .btn-attach,
.input-actions .btn-optimize,
.input-actions .btn-stop {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--theme-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.input-actions .btn-optimize {
  width: 32px;
  height: 32px;
}

.input-actions .btn-attach svg,
.input-actions .btn-optimize svg,
.input-actions .btn-stop svg {
  width: 16px;
  height: 16px;
}

.input-actions .btn-optimize svg {
  width: 20px;
  height: 20px;
}

.input-actions .btn-attach:hover:not(:disabled),
.input-actions .btn-optimize:hover:not(:disabled),
.input-actions .btn-stop:hover:not(:disabled) {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

.input-actions .btn-attach:disabled,
.input-actions .btn-optimize:disabled {
  opacity: 0.5;
  cursor: default;
}

.btn-send {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: var(--main-orange);
  color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--main-orange) 30%, transparent);
}

.btn-send svg {
  width: 20px;
  height: 20px;
  margin-left: 1px;
}

.btn-send:hover:not(:disabled) {
  background: var(--dark-orange);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--main-orange) 40%, transparent);
}

.btn-send:disabled {
  background: var(--light-grey);
  color: var(--muted);
  cursor: default;
  box-shadow: none;
}

.btn-stop {
  color: #ef4444;
}

.btn-stop:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.08);
}

/* Metadata */
.meta-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.meta-token {
  font-size: 10px;
  color: var(--muted);
  background: var(--theme-surface-hover);
  padding: 3px 10px;
  border-radius: 10px;
  border: 1px solid var(--theme-border);
}

/* seg-tool */
.seg-tool {
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--theme-border);
  border-left: 2px solid var(--theme-border);
  border-radius: 0;
  margin-bottom: 0;
  overflow: hidden;
}

.seg-tool.is-running { border-left-color: #409eff; }
.seg-tool.is-success { border-left-color: #67c23a; }
.seg-tool.is-error   { border-left-color: #f56c6c; }

.seg-tool__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  line-height: 1.4;
  color: var(--theme-text-secondary);
}

.seg-tool__header:hover {
  color: var(--theme-text);
}

.seg-tool__status {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  font-weight: 700;
  color: #fff;
  background: #909399;
}

.is-running .seg-tool__status { background: #409eff; }
.is-success .seg-tool__status { background: #67c23a; }
.is-error .seg-tool__status   { background: #f56c6c; }

.spin-icon {
  display: inline-block;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.seg-tool__name {
  font-weight: 500;
  color: var(--theme-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
  transition: color 0.15s;
}

.seg-tool__header:hover .seg-tool__name {
  color: var(--theme-text);
}

.seg-tool__args {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.seg-tool__arrow {
  flex-shrink: 0;
  display: inline-flex;
  width: 10px;
  height: 10px;
  color: var(--muted);
  transition: transform 0.2s;
  margin-left: auto;
}

.seg-tool__arrow.is-open {
  transform: rotate(180deg);
}

.seg-tool__body {
  padding: 0 12px 12px 36px;
}

.seg-tool__section + .seg-tool__section {
  margin-top: 8px;
}

.seg-tool__section-title {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
  font-weight: 500;
}

.seg-tool__body pre {
  margin: 0;
  padding: 10px 12px;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--body-text);
  background: var(--near-white);
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  max-height: 300px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* seg-narration */
.seg-narration {
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--theme-border);
  border-left: 2px solid var(--theme-border);
  border-radius: 0;
  margin-bottom: 0;
  overflow: hidden;
}

.seg-narration__toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  background: transparent;
  border: none;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  line-height: 1.4;
  color: var(--theme-text-secondary);
  text-align: left;
  transition: color 0.15s;
}

.seg-narration__toggle:hover {
  color: var(--theme-text);
}

.seg-narration__label {
  font-weight: 500;
}

.seg-narration__arrow {
  margin-left: auto;
  display: inline-flex;
  width: 10px;
  height: 10px;
  color: var(--muted);
  transition: transform 0.2s;
}

.seg-narration__arrow.is-open {
  transform: rotate(180deg);
}

.seg-narration__body {
  padding: 0 12px 12px 36px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--theme-text-secondary);
}

.seg-narration__body :deep(p) {
  margin: 0 0 6px;
}

.seg-narration__body :deep(p:last-child) {
  margin-bottom: 0;
}

/* seg-execution */
.seg-execution {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  background: var(--theme-surface-hover);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  margin-bottom: 10px;
  overflow: hidden;
  max-width: 100%;
}

.seg-execution__toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  background: transparent;
  border: none;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  line-height: 1.4;
  color: var(--theme-text-muted);
  text-align: left;
  transition: all 0.15s;
  white-space: nowrap;
}

.seg-execution__toggle:hover {
  color: var(--theme-text-secondary);
  background: var(--theme-border);
}

.seg-execution__label {
  font-weight: 500;
}

.seg-execution__count {
  font-size: 11px;
  color: var(--muted);
  font-weight: 400;
}

.seg-execution__arrow {
  margin-left: 1px;
  display: inline-flex;
  width: 9px;
  height: 9px;
  color: var(--muted);
  transition: transform 0.2s;
}

.seg-execution__arrow.is-open {
  transform: rotate(90deg);
}

.seg-execution__body {
  width: 100%;
  min-width: 280px;
  max-width: 100%;
  padding: 0 12px 12px;
}

.seg-execution__body .seg-narration:first-child,
.seg-execution__body .seg-tool:first-child {
  border-top: 1px solid var(--theme-border);
}

/* seg-slide transition (Vue Transition) */
.seg-slide-enter-active,
.seg-slide-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}

.seg-slide-enter-from,
.seg-slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.seg-slide-enter-to,
.seg-slide-leave-from {
  opacity: 1;
  max-height: 600px;
}

/* 附件预览区 */
.attachment-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 0;
}

.attachment-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 8px;
  background: var(--theme-surface-hover);
  border: 1px solid var(--theme-border);
  font-size: 12px;
  color: var(--theme-text-secondary);
  max-width: 200px;
}

.attachment-tag__icon {
  flex-shrink: 0;
  display: inline-flex;
  width: 12px;
  height: 12px;
}

.attachment-tag__icon svg {
  width: 100%;
  height: 100%;
  display: block;
}

.attachment-tag__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.attachment-tag__remove {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  border-radius: 4px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.attachment-tag__remove:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}

/* 用户消息附件展示 */
.msg-attachments {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.msg-attachment {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  opacity: 0.85;
}

.msg-attachment--image {
  cursor: pointer;
  padding: 2px;
  border-radius: 6px;
  overflow: hidden;
}

.msg-attachment__img {
  max-width: 200px;
  max-height: 120px;
  border-radius: 4px;
  object-fit: cover;
}

.attachment-tag__thumb {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.attachment-tag__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}</style>
