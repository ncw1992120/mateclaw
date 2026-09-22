import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig(() => ({
  // 生产部署时 SPA 由 Spring Boot fat jar 根路径承载（context-path 为 /），
  // 前后端同域同根路径，base 保持 / 即可。
  // dev server 挂在根路径，直接访问 http://localhost:5174/ 即可。
  base: '/',
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
      '/v1': {
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

