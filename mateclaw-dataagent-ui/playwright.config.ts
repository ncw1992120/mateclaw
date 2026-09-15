import { defineConfig, devices } from '@playwright/test'

/**
 * G3 真实验收配置：不启动或替换后端，不拦截 DataAgent API。
 * 由 docker-compose.test.yml 提供 UI/DataAgent/Runner/测试数据源，
 * 通过环境变量注入已登录 Token 和已 seed 的仪表盘 ID。
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  // E2E Compose 的 DataAgent/MinIO 使用共享 seed 与任务状态；默认单 worker
  // 避免并行页面互相关闭会话。需要隔离服务实例时可通过环境变量显式提高并发。
  workers: process.env.MATECLAW_E2E_WORKERS ? Number(process.env.MATECLAW_E2E_WORKERS) : 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['line']] : 'list',
  use: {
    baseURL: process.env.MATECLAW_UI_BASE_URL ?? 'http://127.0.0.1:5174',
    ...devices['Desktop Chrome'],
    ...(process.env.MATECLAW_E2E_BROWSER_CHANNEL
      ? { channel: process.env.MATECLAW_E2E_BROWSER_CHANNEL }
      : {}),
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
