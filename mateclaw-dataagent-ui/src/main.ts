import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './assets/main.css'
import { useThemeStore } from '@/stores/useThemeStore'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.use(i18n)

// 在挂载前初始化主题，避免闪烁
const themeStore = useThemeStore()
themeStore.init()

app.mount('#app')
