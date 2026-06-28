<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="glow-ring ring-1" />
      <div class="glow-ring ring-2" />
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
        <h1 class="brand-title">问数智能体</h1>
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
  background:
    radial-gradient(ellipse at 20% 20%, rgba(255, 142, 83, 0.18) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 80%, rgba(240, 90, 35, 0.16) 0%, transparent 50%),
    linear-gradient(145deg, #1a1f2e 0%, #0f1219 100%);
}

/* 背景装饰 */
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.glow-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(240, 90, 35, 0.12);
}

.ring-1 {
  width: 600px;
  height: 600px;
  top: -180px;
  right: -120px;
  background: radial-gradient(circle, rgba(240, 90, 35, 0.08) 0%, transparent 70%);
  animation: float 12s ease-in-out infinite;
}

.ring-2 {
  width: 420px;
  height: 420px;
  bottom: -100px;
  left: -80px;
  background: radial-gradient(circle, rgba(255, 142, 83, 0.06) 0%, transparent 70%);
  animation: float 14s ease-in-out infinite reverse;
}

.glow-dot {
  position: absolute;
  border-radius: 50%;
  background: rgba(240, 90, 35, 0.35);
  filter: blur(20px);
}

.dot-1 {
  width: 120px;
  height: 120px;
  top: 15%;
  left: 12%;
  animation: pulse 8s ease-in-out infinite;
}

.dot-2 {
  width: 80px;
  height: 80px;
  top: 60%;
  right: 10%;
  animation: pulse 10s ease-in-out infinite 1s;
}

.dot-3 {
  width: 50px;
  height: 50px;
  bottom: 25%;
  left: 35%;
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
  background: rgba(255, 255, 255, 0.96);
  border-radius: 24px;
  box-shadow:
    0 32px 80px rgba(0, 0, 0, 0.35),
    0 0 0 1px rgba(255, 255, 255, 0.1) inset;
  overflow: hidden;
  backdrop-filter: blur(20px);
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
    linear-gradient(135deg, rgba(240, 90, 35, 0.97) 0%, rgba(231, 92, 1, 0.98) 100%),
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
  color: #1d2129;
  margin: 0 0 8px 0;
}

.form-desc {
  font-size: 14px;
  color: #86909c;
  margin: 0;
}

.form-field {
  margin-bottom: 24px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #4e5969;
  margin-bottom: 8px;
}

.login-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e5e6eb inset;
  background: #f7f8fa;
  transition: all 0.25s ease;
}

.login-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--main-orange) inset, 0 0 0 4px rgba(240, 90, 35, 0.08);
  background: #fff;
}

/* 密码显示切换图标 */
.login-input :deep(.el-input__suffix-inner) {
  display: flex;
  align-items: center;
}

.login-input :deep(.el-input__password) {
  color: #a8abb2;
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
  box-shadow: 0 8px 20px rgba(240, 90, 35, 0.3);
  transition: all 0.25s ease;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(240, 90, 35, 0.38);
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
