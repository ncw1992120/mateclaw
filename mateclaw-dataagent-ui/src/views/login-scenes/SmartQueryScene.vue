<template>
  <div class="smart-query-scene">
    <div class="chat-window">
      <div class="chat-header">
        <span class="chat-dot" />
        <span class="chat-title">智能助手</span>
      </div>
      <div class="chat-body">
        <div class="message user">
          <div class="bubble">近 30 天销售额趋势如何？</div>
        </div>
        <div class="message agent">
          <div class="avatar">AI</div>
          <div class="bubble">
            <div class="bubble-line" />
            <div class="bubble-line short" />
            <div class="data-badge">
              <span class="data-value">¥1,248W</span>
              <span class="data-change up">+12.5%</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'SmartQueryScene',
})
</script>

<style scoped>
.smart-query-scene {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.chat-window {
  width: 240px;
  height: 160px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  animation: float-window 5s ease-in-out infinite;
}

@keyframes float-window {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: linear-gradient(135deg, #f05a23 0%, #e75c01 100%);
}

.chat-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.3);
}

.chat-title {
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.chat-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
}

.message {
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.agent {
  align-items: flex-end;
  gap: 6px;
}

.avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #f05a23 0%, #e75c01 100%);
  flex-shrink: 0;
}

.bubble {
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 11px;
  line-height: 1.4;
  max-width: 88%;
}

.message.user .bubble {
  background: linear-gradient(135deg, #f05a23 0%, #e75c01 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message.agent .bubble {
  background: #f5f6f8;
  color: #333;
  border-bottom-left-radius: 4px;
}

.bubble-line {
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: #e0e2e6;
  margin-bottom: 4px;
}

.bubble-line.short {
  width: 55%;
}

.data-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  padding: 4px 8px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #eee;
}

.data-value {
  font-weight: 700;
  color: #333;
  font-size: 11px;
}

.data-change {
  font-size: 10px;
  font-weight: 600;
}

.data-change.up {
  color: #00b578;
}
</style>
