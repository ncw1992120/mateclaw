import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig(({ mode }) => ({
  // 生产部署时 SPA 由 Spring Boot 服务（context-path /dataagent/api）承载，
  // 资源引用必须带该前缀，否则 /assets/** 落到根路径 404。
  // dev server 挂在根路径，直接访问 http://localhost:5174/ 即可。
  base: mode === 'production' ? '/dataagent/api/' : '/',
  plugins: [
    vue(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/dataagent': {
        target: 'http://localhost:18089',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1024,
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-echarts': ['echarts'],
          'vendor-element': ['element-plus', '@element-plus/icons-vue'],
          'vendor-markdown': ['marked', 'marked-highlight', 'highlight.js', 'dompurify'],
          'vendor-mermaid': ['mermaid'],
        },
      },
    },
  },
}))

