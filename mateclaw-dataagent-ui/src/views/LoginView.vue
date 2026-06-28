<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 品牌区域 -->
      <div class="brand-section">
        <div class="brand-logo">🎯</div>
        <h1 class="brand-title">问数智能体</h1>
        <p class="brand-subtitle">数据分析 Agent 工作台</p>
      </div>

      <!-- 登录表单 -->
      <div class="form-section">
        <h2 class="form-title">用户登录</h2>

        <form @submit.prevent="handleLogin">
          <div class="form-field">
            <label class="field-label">用户名</label>
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
              clearable
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
              show-password
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
            登 录
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
import { User, Lock } from '@element-plus/icons-vue'
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
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100vh;
  background: linear-gradient(135deg, #f05a23 0%, #e75c01 50%, #c44d00 100%);
}

.login-card {
  display: flex;
  width: 800px;
  max-width: 90vw;
  height: 460px;
  max-height: 90vh;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

/* 品牌区域 */
.brand-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 45%;
  padding: 48px;
  background: linear-gradient(135deg, #f05a23 0%, #e75c01 100%);
  color: #fff;
  text-align: center;
}

.brand-logo {
  font-size: 64px;
  margin-bottom: 24px;
}

.brand-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 12px 0;
}

.brand-subtitle {
  font-size: 14px;
  opacity: 0.85;
  margin: 0;
}

/* 表单区域 */
.form-section {
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  padding: 48px;
}

.form-title {
  font-size: 22px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 32px 0;
}

.form-field {
  margin-bottom: 24px;
}

.field-label {
  display: block;
  font-size: 13px;
  color: #4e5969;
  margin-bottom: 8px;
  font-weight: 500;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
  background: var(--main-orange);
  border-color: var(--main-orange);
}

.login-btn:hover {
  background: var(--dark-orange);
  border-color: var(--dark-orange);
}

/* 响应式适配 */
@media (max-width: 768px) {
  .login-card {
    flex-direction: column;
    width: 90vw;
    height: auto;
  }

  .brand-section {
    width: 100%;
    padding: 32px;
  }

  .brand-logo {
    font-size: 48px;
    margin-bottom: 16px;
  }

  .brand-title {
    font-size: 22px;
  }

  .form-section {
    padding: 32px;
  }
}
</style>
