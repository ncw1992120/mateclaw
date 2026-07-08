<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="glow-blob blob-1" />
      <div class="glow-blob blob-2" />
      <div class="glow-dot dot-1" />
      <div class="glow-dot dot-2" />
      <div class="glow-dot dot-3" />
    </div>

    <div class="login-card">
      <!-- 品牌区域 -->
      <div class="brand-section">
        <div class="brand-logo">
          <svg viewBox="0 0 64 64" class="logo-svg">
            <defs>
              <linearGradient id="logoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#fff" stop-opacity="0.95" />
                <stop offset="100%" stop-color="#ffe4d6" stop-opacity="0.9" />
              </linearGradient>
            </defs>
            <circle cx="28" cy="26" r="16" fill="none" stroke="url(#logoGradient)" stroke-width="3" />
            <path d="M40 38 L54 52" stroke="url(#logoGradient)" stroke-width="4" stroke-linecap="round" />
            <circle cx="28" cy="26" r="5" fill="url(#logoGradient)" />
          </svg>
        </div>
        <h1 class="brand-title">数据智能体</h1>
        <p class="brand-subtitle">数据分析 Agent 工作台</p>
      </div>

      <!-- 登录表单 -->
      <div class="form-section">
        <div class="form-header">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-desc">请登录您的账户以继续使用</p>
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
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, ArrowRight } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/useUserStore'

const router = useRouter()
const userStore = useUserStore()

const form = reactive({
  username: '',
  password: '',
})

const loading = ref(false)

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
  background: var(--theme-bg);
}

html[data-theme='dark'] .login-page {
  background:
    radial-gradient(ellipse at 15% 15%, color-mix(in srgb, var(--main-orange) 12%, transparent) 0%, transparent 45%),
    radial-gradient(ellipse at 85% 85%, color-mix(in srgb, var(--main-orange) 10%, transparent) 0%, transparent 45%),
    linear-gradient(145deg, #1a1f2e 0%, #0f1219 100%);
}

/* 背景装饰：柔和弥散光斑，避免硬边弧线 */
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.glow-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.blob-1 {
  width: 520px;
  height: 520px;
  top: -160px;
  right: -100px;
  background: color-mix(in srgb, var(--main-orange) 14%, transparent);
  animation: float 14s ease-in-out infinite;
}

.blob-2 {
  width: 420px;
  height: 420px;
  bottom: -140px;
  left: -120px;
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  animation: float 16s ease-in-out infinite reverse;
}

.glow-dot {
  position: absolute;
  border-radius: 50%;
  background: color-mix(in srgb, var(--main-orange) 28%, transparent);
  filter: blur(24px);
}

.dot-1 {
  width: 100px;
  height: 100px;
  top: 18%;
  left: 14%;
  animation: pulse 8s ease-in-out infinite;
}

.dot-2 {
  width: 70px;
  height: 70px;
  top: 62%;
  right: 12%;
  animation: pulse 10s ease-in-out infinite 1s;
}

.dot-3 {
  width: 44px;
  height: 44px;
  bottom: 28%;
  left: 38%;
  animation: pulse 7s ease-in-out infinite 2s;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-20px) scale(1.02);
  }
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.25;
    transform: scale(1);
  }
  50% {
    opacity: 0.45;
    transform: scale(1.15);
  }
}

/* 登录卡片 */
.login-card {
  position: relative;
  z-index: 1;
  display: flex;
  width: 860px;
  max-width: 92vw;
  min-height: 500px;
  background: var(--theme-surface-elevated);
  border-radius: 24px;
  box-shadow:
    0 32px 80px rgba(0, 0, 0, 0.12),
    0 0 0 1px var(--theme-border) inset;
  overflow: hidden;
  backdrop-filter: blur(20px);
}

html[data-theme='dark'] .login-card {
  background: rgba(30, 30, 30, 0.96);
  box-shadow:
    0 32px 80px rgba(0, 0, 0, 0.35),
    0 0 0 1px rgba(255, 255, 255, 0.1) inset;
}

/* 品牌区域 */
.brand-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 42%;
  padding: 56px 48px;
  background:
    linear-gradient(135deg, var(--main-orange) 0%, var(--dark-orange) 100%),
    url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.06'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
  color: #fff;
  text-align: center;
}

.brand-logo {
  width: 88px;
  height: 88px;
  margin-bottom: 28px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  backdrop-filter: blur(8px);
}

.logo-svg {
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 2px 8px rgba(0, 0, 0, 0.15));
}

.brand-title {
  font-size: 30px;
  font-weight: 700;
  margin: 0 0 10px 0;
  letter-spacing: 0.5px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.brand-subtitle {
  font-size: 14px;
  font-weight: 400;
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
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
  font-size: 26px;
  font-weight: 700;
  color: var(--theme-text);
  margin: 0 0 8px 0;
}

.form-desc {
  font-size: 14px;
  color: var(--theme-text-muted);
  margin: 0;
}

.form-field {
  margin-bottom: 24px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text-secondary);
  margin-bottom: 8px;
}

.login-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px var(--theme-border-strong) inset;
  background: var(--theme-bg);
  transition: all 0.25s ease;
}

.login-input :deep(.el-input__inner) {
  color: var(--theme-text);
}

.login-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--main-orange) inset, 0 0 0 4px color-mix(in srgb, var(--main-orange) 8%, transparent);
  background: var(--theme-surface-elevated);
}

/* 密码显示切换图标 */
.login-input :deep(.el-input__suffix-inner) {
  display: flex;
  align-items: center;
}

.login-input :deep(.el-input__password) {
  color: var(--theme-text-muted);
  font-size: 16px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.login-input :deep(.el-input__password:hover) {
  color: var(--main-orange);
}

.login-btn {
  width: 100%;
  height: 46px;
  margin-top: 12px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  background: linear-gradient(135deg, var(--main-orange) 0%, var(--dark-orange) 100%);
  border: none;
  box-shadow: 0 8px 20px color-mix(in srgb, var(--main-orange) 30%, transparent);
  transition: all 0.25s ease;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px color-mix(in srgb, var(--main-orange) 38%, transparent);
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

/* 响应式适配 */
@media (max-width: 768px) {
  .login-card {
    flex-direction: column;
    width: 92vw;
    min-height: auto;
    border-radius: 20px;
  }

  .brand-section {
    width: 100%;
    padding: 36px 24px;
  }

  .brand-logo {
    width: 64px;
    height: 64px;
    padding: 14px;
    margin-bottom: 18px;
  }

  .brand-title {
    font-size: 22px;
  }

  .form-section {
    padding: 32px 28px 40px;
  }

  .form-header {
    margin-bottom: 28px;
  }

  .form-title {
    font-size: 22px;
  }
}
</style>
