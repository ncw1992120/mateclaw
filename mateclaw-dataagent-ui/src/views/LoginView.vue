<template>
  <div class="login-page">
    <!-- 动态背景 -->
    <div class="bg-grid" />
    <div class="bg-glow" />
    <div class="bg-orb orb-1" />
    <div class="bg-orb orb-2" />

    <div class="login-card">
      <!-- 左侧：品牌与场景展示 -->
      <div class="brand-section">
        <div class="brand-backdrop" />
        <div class="brand-content">
          <div class="brand-logo">
            <svg viewBox="0 0 100 100" class="logo-svg">
              <defs>
                <linearGradient id="logoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#fff" stop-opacity="0.95" />
                  <stop offset="100%" stop-color="#ffe4d6" stop-opacity="0.9" />
                </linearGradient>
              </defs>
              <rect x="5" y="5" width="90" height="90" rx="15" fill="none" stroke="url(#logoGradient)" stroke-width="4" />
              <circle cx="50" cy="45" r="25" fill="none" stroke="url(#logoGradient)" stroke-width="4" />
              <line x1="68" y1="62" x2="85" y2="79" stroke="url(#logoGradient)" stroke-width="6" stroke-linecap="round" />
              <polyline points="38,52 45,45 52,48 60,38" fill="none" stroke="url(#logoGradient)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
              <polygon points="60,38 57,42 60,41 58,48 62,46 65,43" fill="url(#logoGradient)" />
            </svg>
          </div>
          <h1 class="brand-title">数据智能体</h1>
          <p class="brand-subtitle">Data Agent Workbench</p>

          <!-- 场景轮播 -->
          <div class="scene-carousel">
            <button class="scene-arrow prev" @click="prevScene">
              <el-icon size="18"><ArrowLeft /></el-icon>
            </button>

            <div class="scene-stage">
              <Transition name="scene-slide" mode="out-in">
                <div :key="activeScene" class="scene-visual">
                  <component :is="scenes[activeScene].component" />
                </div>
              </Transition>
            </div>

            <button class="scene-arrow next" @click="nextScene">
              <el-icon size="18"><ArrowRight /></el-icon>
            </button>
          </div>

          <!-- 场景信息 -->
          <div class="scene-info">
            <Transition name="scene-fade" mode="out-in">
              <div :key="activeScene" class="scene-info-content">
                <div class="scene-title">
                  <el-icon size="18"><component :is="scenes[activeScene].icon" /></el-icon>
                  <span>{{ scenes[activeScene].label }}</span>
                </div>
                <p class="scene-desc">{{ scenes[activeScene].desc }}</p>
              </div>
            </Transition>
          </div>

          <!-- 场景指示器 -->
          <div class="scene-dots">
            <button
              v-for="(_, index) in scenes"
              :key="index"
              class="scene-dot"
              :class="{ active: activeScene === index }"
              @click="activeScene = index"
            />
          </div>
        </div>
      </div>

      <!-- 右侧：登录表单 -->
      <div class="form-section">
        <div class="form-header">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-desc">登录账户，开启数据智能分析之旅</p>
        </div>

        <form @submit.prevent="handleLogin">
          <div class="form-field">
            <label class="field-label">用户名</label>
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
              clearable
              class="login-input"
              @keyup.enter="handleLogin"
            />
          </div>

          <div class="form-field">
            <label class="field-label">密码</label>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              :prefix-icon="Lock"
              :show-password="true"
              class="login-input"
              @keyup.enter="handleLogin"
            />
          </div>

          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            <span class="btn-text">登 录</span>
            <el-icon class="btn-icon"><ArrowRight /></el-icon>
          </el-button>
        </form>

        <div class="form-footer">
          <span class="feature-tag">
            <el-icon><ChatDotRound /></el-icon>
            智能问数
          </span>
          <span class="feature-tag">
            <el-icon><DataLine /></el-icon>
            深度洞察
          </span>
          <span class="feature-tag">
            <el-icon><Document /></el-icon>
            报告生成
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  User,
  Lock,
  ArrowLeft,
  ArrowRight,
  ChatDotRound,
  DataLine,
  Document,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/useUserStore'
import SmartQueryScene from './login-scenes/SmartQueryScene.vue'
import InsightScene from './login-scenes/InsightScene.vue'
import ReportScene from './login-scenes/ReportScene.vue'

const router = useRouter()
const userStore = useUserStore()

const form = reactive({
  username: '',
  password: '',
})

const loading = ref(false)
const activeScene = ref(0)
let autoPlayTimer: number | null = null

const scenes = [
  {
    key: 'query',
    label: '智能问数',
    icon: ChatDotRound,
    component: SmartQueryScene,
    desc: '用自然语言提问，让 Agent 自动分析数据并返回结果',
  },
  {
    key: 'insight',
    label: '深度洞察',
    icon: DataLine,
    component: InsightScene,
    desc: '自动识别业务趋势、异常与关联，生成可视化仪表盘',
  },
  {
    key: 'report',
    label: '报告生成',
    icon: Document,
    component: ReportScene,
    desc: '一键生成专业数据分析报告，支持多格式导出与分享',
  },
]

onMounted(() => {
  startAutoPlay()
})

onUnmounted(() => {
  stopAutoPlay()
})

function nextScene(): void {
  activeScene.value = (activeScene.value + 1) % scenes.length
  resetAutoPlay()
}

function prevScene(): void {
  activeScene.value = (activeScene.value - 1 + scenes.length) % scenes.length
  resetAutoPlay()
}

function startAutoPlay(): void {
  stopAutoPlay()
  autoPlayTimer = window.setInterval(() => {
    activeScene.value = (activeScene.value + 1) % scenes.length
  }, 5000)
}

function stopAutoPlay(): void {
  if (autoPlayTimer) {
    clearInterval(autoPlayTimer)
    autoPlayTimer = null
  }
}

function resetAutoPlay(): void {
  startAutoPlay()
}

/** 处理登录 */
async function handleLogin(): Promise<void> {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    // 错误已由 axios 拦截器统一提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(240, 90, 35, 0.06) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 80%, rgba(240, 90, 35, 0.04) 0%, transparent 50%),
    #f8f9fb;
}

/* 动态背景 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(0, 0, 0, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at center, black 0%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 0%, transparent 70%);
}

.bg-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(240, 90, 35, 0.1) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 80%, rgba(240, 90, 35, 0.06) 0%, transparent 50%);
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.3;
  background: #f05a23;
  animation: float 12s ease-in-out infinite;
}

.orb-1 {
  width: 360px;
  height: 360px;
  top: -120px;
  right: -80px;
}

.orb-2 {
  width: 280px;
  height: 280px;
  bottom: -100px;
  left: -60px;
  animation-delay: -6s;
  animation-duration: 16s;
}

@keyframes float {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-24px) scale(1.05); }
}

/* 登录卡片 */
.login-card {
  position: relative;
  z-index: 1;
  display: flex;
  width: 1000px;
  max-width: 94vw;
  min-height: 620px;
  background: #ffffff;
  border-radius: 28px;
  box-shadow:
    0 40px 100px rgba(0, 0, 0, 0.1),
    0 0 0 1px rgba(0, 0, 0, 0.04) inset;
  overflow: hidden;
}

/* 品牌区域 */
.brand-section {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 48%;
  padding: 48px 40px;
  color: #fff;
  overflow: hidden;
}

.brand-backdrop {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(135deg, #f05a23 0%, #e75c01 100%),
    url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.06'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
}

.brand-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.brand-logo {
  width: 72px;
  height: 72px;
  margin-bottom: 20px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  backdrop-filter: blur(8px);
}

.logo-svg {
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 2px 8px rgba(0, 0, 0, 0.15));
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 6px 0;
  letter-spacing: 0.5px;
}

.brand-subtitle {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
  margin: 0 0 32px 0;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}

/* 场景轮播 */
.scene-carousel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 8px;
}

.scene-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  transition: all 0.25s ease;
}

.scene-arrow:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.45);
  color: #fff;
  transform: scale(1.08);
}

.scene-stage {
  width: 280px;
  height: 200px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  overflow: hidden;
}

.scene-visual {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
}

.scene-info {
  margin-top: 20px;
  min-height: 70px;
  text-align: center;
}

.scene-title {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}

.scene-desc {
  margin: 0 auto;
  max-width: 320px;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.8);
}

.scene-dots {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
}

.scene-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.35);
  cursor: pointer;
  transition: all 0.25s ease;
}

.scene-dot.active {
  width: 24px;
  border-radius: 4px;
  background: #fff;
}

/* 场景切换动画 */
.scene-slide-enter-active,
.scene-slide-leave-active {
  transition: all 0.4s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.scene-slide-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.scene-slide-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

.scene-fade-enter-active,
.scene-fade-leave-active {
  transition: all 0.3s ease;
}

.scene-fade-enter-from,
.scene-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

/* 表单区域 */
.form-section {
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  padding: 56px 56px 56px 64px;
}

.form-header {
  margin-bottom: 36px;
}

.form-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1d23;
  margin: 0 0 8px 0;
}

.form-desc {
  font-size: 14px;
  color: #888;
  margin: 0;
}

.form-field {
  margin-bottom: 24px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #555;
  margin-bottom: 8px;
}

.login-input :deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.08) inset;
  background: #f8f9fb;
  transition: all 0.25s ease;
}

.login-input :deep(.el-input__inner) {
  color: #333;
}

.login-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #f05a23 inset, 0 0 0 4px rgba(240, 90, 35, 0.08);
  background: #fff;
}

.login-input :deep(.el-input__suffix-inner) {
  display: flex;
  align-items: center;
}

.login-input :deep(.el-input__password) {
  color: #999;
  font-size: 16px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.login-input :deep(.el-input__password:hover) {
  color: #f05a23;
}

.login-btn {
  width: 100%;
  height: 48px;
  margin-top: 12px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #f05a23 0%, #e75c01 100%);
  border: none;
  box-shadow: 0 8px 24px rgba(240, 90, 35, 0.28);
  transition: all 0.25s ease;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(240, 90, 35, 0.38);
}

.login-btn:active {
  transform: translateY(0);
}

.btn-icon {
  margin-left: 6px;
  transition: transform 0.25s ease;
}

.login-btn:hover .btn-icon {
  transform: translateX(3px);
}

/* 底部特性标签 */
.form-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 36px;
  padding-top: 28px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.feature-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 20px;
  background: rgba(240, 90, 35, 0.08);
  color: #f05a23;
  font-size: 12px;
  font-weight: 500;
}

/* 响应式适配 */
@media (max-width: 860px) {
  .login-card {
    flex-direction: column;
    width: 92vw;
    min-height: auto;
    max-height: 92vh;
    overflow-y: auto;
    border-radius: 20px;
  }

  .brand-section {
    width: 100%;
    padding: 32px 24px;
  }

  .brand-logo {
    width: 56px;
    height: 56px;
    padding: 12px;
    margin-bottom: 14px;
  }

  .brand-title {
    font-size: 24px;
  }

  .brand-subtitle {
    margin-bottom: 20px;
  }

  .scene-stage {
    height: 280px;
  }

  .scene-card {
    padding: 18px;
  }

  .scene-arrow {
    display: none;
  }

  .form-section {
    padding: 32px 28px 40px;
  }

  .form-title {
    font-size: 22px;
  }

  .form-footer {
    flex-wrap: wrap;
    margin-top: 28px;
    padding-top: 20px;
  }
}
</style>
