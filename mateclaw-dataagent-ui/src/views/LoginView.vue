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
                  <stop offset="0%" stop-color="#4176e6" stop-opacity="1" />
                  <stop offset="100%" stop-color="#4868b2" stop-opacity="1" />
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
          <!-- 登录类型页签：激活项下划线指示；切换仅变更字段文案与提交通道 -->
          <div v-if="authProvider === 'pilot'" class="auth-tabs">
            <button
              v-for="t in typeOptions"
              :key="t"
              type="button"
              class="auth-tab"
              :class="{ active: loginType === t }"
              @click="selectLoginType(t)"
            >
              {{ TYPE_META[t].label }}
            </button>
          </div>

          <div class="form-field">
            <label class="field-label">{{ meta.userLabel }}</label>
            <el-input
              v-model="form.username"
              :placeholder="meta.userPh"
              size="large"
              :prefix-icon="User"
              clearable
              class="login-input"
              @keyup.enter="handleLogin"
            />
          </div>

          <div class="form-field">
            <label class="field-label">{{ meta.pwdLabel }}</label>
            <el-input
              v-model="form.password"
              type="password"
              :placeholder="meta.pwdPh"
              size="large"
              :prefix-icon="Lock"
              :show-password="true"
              class="login-input"
              @keyup.enter="handleLogin"
            />
          </div>

          <!-- 企业认证风控触发的图形验证码（后端返回 HTTP 429 后显示） -->
          <div v-if="needCaptcha" class="form-field">
            <label class="field-label">图形验证码</label>
            <div class="captcha-row">
              <el-input
                v-model="form.validCode"
                placeholder="请输入验证码"
                size="large"
                :prefix-icon="Key"
                clearable
                class="login-input captcha-input"
                @keyup.enter="handleLogin"
              />
              <img
                v-if="captcha?.captchaImage"
                :src="`data:image/png;base64,${captcha.captchaImage}`"
                class="captcha-img"
                title="看不清？点击刷新"
                alt="图形验证码"
                @click="loadCaptcha"
              />
              <div v-else class="captcha-img captcha-placeholder" title="点击加载" @click="loadCaptcha">
                点击加载
              </div>
            </div>
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
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  User,
  Lock,
  Key,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ChatDotRound,
  DataLine,
  Document,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/useUserStore'
import { getCaptcha, getAuthMode, isNeedCaptchaError, type CaptchaInfo } from '@/api/auth'
import SmartQueryScene from './login-scenes/SmartQueryScene.vue'
import InsightScene from './login-scenes/InsightScene.vue'
import ReportScene from './login-scenes/ReportScene.vue'

const router = useRouter()
const userStore = useUserStore()

const form = reactive({
  username: '',
  password: '',
  validCode: '',
})

const loading = ref(false)
const activeScene = ref(0)
let autoPlayTimer: number | null = null

/** 企业认证风控状态：后端返回 HTTP 429 后进入验证码模式 */
const needCaptcha = ref(false)
const captcha = ref<CaptchaInfo | null>(null)

/** 认证模式：local=本地账密（隐藏企业认证选择器）；pilot=领航代验 */
const authProvider = ref<'local' | 'pilot'>('local')

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

/** 读取共享域 Cookie 值（领航 SSO 免登探测用） */
function readCookie(name: string): string | undefined {
  const prefix = name + '='
  const hit = document.cookie.split('; ').find((c) => c.startsWith(prefix))
  return hit ? decodeURIComponent(hit.slice(prefix.length)) : undefined
}

/** 企业 SSO 静默免登：持有效企业票据时无感进入系统，失败则留在登录表单 */
async function trySilentSso(ssoCookie: string): Promise<void> {
  loading.value = true
  try {
    await userStore.loginBySso(ssoCookie)
    router.push('/')
  } catch {
    // 拦截器对该接口静默处理，此处仅降级为普通登录表单
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  startAutoPlay()
  // 查询认证模式：pilot 时展示 UM/AD 选择器；配置了 SSO Cookie 名且浏览器持有票据时静默免登
  getAuthMode()
    .then(async (mode) => {
      authProvider.value = mode.provider
      if (mode.provider === 'pilot' && mode.ssoCookieName) {
        const ssoCookie = readCookie(mode.ssoCookieName)
        if (ssoCookie) {
          await trySilentSso(ssoCookie)
        }
      }
    })
    .catch(() => {
      // 后端不可达时保持 local 形态，登录请求由后端最终裁决
    })
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

/** 拉取企业认证图形验证码（authMechanism 与认证方式一致） */
async function loadCaptcha(): Promise<void> {
  try {
    captcha.value = await getCaptcha({ authnType: form.authnType })
  } catch {
    // 错误提示已由 axios 拦截器统一处理；保留占位允许用户点击重试
  }
}

/** 三种登录类型及其表单文案（对应三个登录表单形态） */
type LoginChannel = 'AD' | 'UM' | 'LOCAL'
const TYPE_META: Record<
  LoginChannel,
  { label: string; userLabel: string; pwdLabel: string; userPh: string; pwdPh: string }
> = {
  AD: {
    label: '开机账号登录',
    userLabel: '开机账号',
    pwdLabel: '开机密码',
    userPh: '请输入开机账号',
    pwdPh: '请输入开机密码',
  },
  UM: {
    label: 'UM 账号登录',
    userLabel: 'UM 账号',
    pwdLabel: 'UM 密码',
    userPh: '请输入 UM 账号',
    pwdPh: '请输入 UM 密码',
  },
  LOCAL: {
    label: '本地账号登录',
    userLabel: '账号',
    pwdLabel: '密码',
    userPh: '请输入本地账号',
    pwdPh: '请输入密码',
  },
}
const typeOptions: LoginChannel[] = ['AD', 'UM', 'LOCAL']

/** 当前登录类型：默认开机账号(AD)；LOCAL 走本地账密通道 */
const loginType = ref<LoginChannel>('AD')
const meta = computed(() => TYPE_META[loginType.value])

/** 切换登录类型：清空验证码；离开领航通道时复位验证码状态，回到领航通道且处于验证码模式时刷新图片 */
function selectLoginType(type: LoginChannel): void {
  if (loginType.value === type) {
    return
  }
  const leavingPilot = loginType.value !== 'LOCAL'
  loginType.value = type
  form.validCode = ''
  if (type === 'LOCAL') {
    needCaptcha.value = false
    captcha.value = null
  } else if (leavingPilot && needCaptcha.value) {
    loadCaptcha()
  }
}

/** 处理登录 */
async function handleLogin(): Promise<void> {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  const isPilotType = loginType.value !== 'LOCAL'
  if (isPilotType && needCaptcha.value && !captcha.value?.requestId) {
    // 首次进入验证码模式尚未拉取图片，先补拉再等用户输入
    await loadCaptcha()
    return
  }
  if (isPilotType && needCaptcha.value && !form.validCode.trim()) {
    ElMessage.warning('请输入图形验证码')
    return
  }

  loading.value = true
  try {
    let extra: {
      requestId?: string
      validCode?: string
      authnType?: 'UM' | 'AD'
      channel?: 'local'
    }
    if (loginType.value === 'LOCAL') {
      extra = { channel: 'local' }
    } else if (needCaptcha.value) {
      extra = {
        requestId: captcha.value?.requestId,
        validCode: form.validCode.trim(),
        authnType: loginType.value,
      }
    } else {
      extra = { authnType: loginType.value }
    }
    await userStore.login(form.username.trim(), form.password, extra)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    if (isPilotType && isNeedCaptchaError(e)) {
      // HTTP 429：领航风控要求验证码（错误文案已由拦截器提示），切换 UI 并拉取图片
      if (!needCaptcha.value) {
        needCaptcha.value = true
        form.validCode = ''
      }
      await loadCaptcha()
    } else if (isPilotType && needCaptcha.value) {
      // 验证码模式下登录失败（如验证码错误/密码错误）：刷新图片供重试
      form.validCode = ''
      await loadCaptcha()
    } else if (!(e as { isAxiosError?: boolean })?.isAxiosError) {
      // auth.ts 内部错误（公钥获取/加密失败等）非 axios 错误，拦截器不会提示，此处兜底
      ElMessage.error(e instanceof Error ? e.message : '登录失败，请重试')
    }
    // 其余 axios 错误由拦截器统一提示
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
    radial-gradient(ellipse at 20% 20%, rgba(65, 118, 230, 0.04) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 80%, rgba(65, 118, 230, 0.03) 0%, transparent 50%),
    #fbfcfe;
}

/* 动态背景 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(0, 0, 0, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.025) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at center, black 0%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 0%, transparent 70%);
}

.bg-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(65, 118, 230, 0.07) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 80%, rgba(65, 118, 230, 0.04) 0%, transparent 50%);
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.18;
  background: #4176E6;
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
  color: var(--theme-text);
  overflow: hidden;
}

.brand-backdrop {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(135deg, #EDF3FE 0%, #DCE8FC 100%),
    url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%234176e6' fill-opacity='0.06'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
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
  background: rgba(255, 255, 255, 0.7);
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(65, 118, 230, 0.12);
  backdrop-filter: blur(8px);
}

.logo-svg {
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 2px 8px rgba(65, 118, 230, 0.15));
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 6px 0;
  letter-spacing: 0.5px;
  color: #1a1d23;
}

.brand-subtitle {
  font-size: 13px;
  font-weight: 500;
  color: #6b84ad;
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
  border: 1px solid rgba(65, 118, 230, 0.25);
  background: rgba(255, 255, 255, 0.6);
  color: var(--theme-text-secondary);
  cursor: pointer;
  transition: all 0.25s ease;
}

.scene-arrow:hover {
  background: #fff;
  border-color: rgba(65, 118, 230, 0.5);
  color: var(--main-orange);
  transform: scale(1.08);
}

.scene-stage {
  width: 280px;
  height: 200px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(65, 118, 230, 0.12);
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
  color: #1a1d23;
}

.scene-desc {
  margin: 0 auto;
  max-width: 320px;
  font-size: 13px;
  line-height: 1.6;
  color: #5a6b8c;
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
  background: rgba(65, 118, 230, 0.3);
  cursor: pointer;
  transition: all 0.25s ease;
}

.scene-dot.active {
  width: 24px;
  border-radius: 4px;
  background: var(--main-orange);
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
  color: #8896b0;
  margin: 0;
}

.form-field {
  margin-bottom: 24px;
}

/* 登录类型页签：横排三等分，激活项主题蓝 + 下划线 */
.auth-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
}

.auth-tab {
  flex: 1;
  padding: 6px 4px 10px;
  font-size: 14px;
  font-weight: 500;
  color: #8896b0;
  background: none;
  border: none;
  border-bottom: 2px solid rgba(65, 118, 230, 0.12);
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.auth-tab:hover {
  color: #4176E6;
}

.auth-tab.active {
  color: #4176E6;
  border-bottom-color: #4176E6;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #5a6b8c;
  margin-bottom: 8px;
}

.login-input :deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px rgba(65, 118, 230, 0.14) inset;
  background: #f4f7fd;
  transition: all 0.25s ease;
}

.login-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(65, 118, 230, 0.3) inset;
  background: #f6f9fe;
}

.login-input :deep(.el-input__inner) {
  color: #333;
}

.login-input :deep(.el-input__prefix) {
  color: #8fa8d9;
}

.login-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #4176E6 inset, 0 0 0 4px rgba(65, 118, 230, 0.1);
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
  color: #4176E6;
}

/* 图形验证码 */
.captcha-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  height: 40px;
  min-width: 120px;
  border-radius: 10px;
  border: 1px solid rgba(65, 118, 230, 0.14);
  background: #f4f7fd;
  cursor: pointer;
  object-fit: cover;
  transition: border-color 0.25s ease;
}

.captcha-img:hover {
  border-color: rgba(65, 118, 230, 0.4);
}

.captcha-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #8fa8d9;
}

.login-btn {
  width: 100%;
  height: 48px;
  margin-top: 12px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #4176E6 0%, #4868B2 100%);
  border: none;
  box-shadow: 0 8px 24px rgba(65, 118, 230, 0.28);
  transition: all 0.25s ease;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(65, 118, 230, 0.38);
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
  border-top: 1px solid rgba(65, 118, 230, 0.12);
}

.feature-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 20px;
  background: rgba(65, 118, 230, 0.07);
  color: #4176E6;
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
