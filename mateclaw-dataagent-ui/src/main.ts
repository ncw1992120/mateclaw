import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './assets/main.css'
import './assets/dashboard-design.css'
import './assets/insight-title-bar.css'
import { useThemeStore } from '@/stores/useThemeStore'
import { permissionDirective } from '@/directives/permission'
import { elementPlusConfig } from './element-plus-config'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, elementPlusConfig)
app.use(i18n)

// 注册全局权限指令 v-permission
app.directive('permission', permissionDirective)

// 在挂载前初始化主题，避免闪烁
const themeStore = useThemeStore()
themeStore.init()

app.mount('#app')
