import { expect, test } from '@playwright/test'

function required(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`缺少真实 E2E 环境变量 ${name}`)
  return value
}

test('从产品入口进入数据集创建并取消返回', async ({ page }) => {
  await page.addInitScript(({ authToken, workspace }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspace))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspace: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=config')
  await page.getByRole('tab', { name: '数据配置' }).click()
  await page.getByRole('button', { name: '数据集管理' }).click()
  await expect(page).toHaveURL(/\/datasets$/)
  await page.getByRole('button', { name: '新建数据集' }).click()
  await expect(page).toHaveURL(/\/datasets\/new$/)
  await expect(page.getByText('来源类型')).toBeVisible()
  await page.getByRole('combobox').nth(1).selectOption('FILE')
  await expect(page.getByText('选择并上传文件')).toBeVisible()
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page).toHaveURL(/\/datasets$/)
})

test('智能问数模型选择器暴露可访问名称', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=smart-ask')
  await expect(page.locator('.model-select-footer input[role="combobox"]')).toHaveAccessibleName('选择模型')
})

test('问数输入工具暴露可访问名称', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=smart-ask')
  await expect(page.getByRole('button', { name: '上传附件' })).toBeVisible()
  await expect(page.getByRole('button', { name: '指定数据源' })).toBeVisible()
  await expect(page.getByRole('button', { name: '快捷提问' })).toBeVisible()
  await expect(page.getByRole('button', { name: '优化输入' })).toBeVisible()
  await expect(page.getByRole('button', { name: '发送' })).toBeVisible()
})

test('历史对话工具暴露可访问名称', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=smart-ask')
  await expect(page.getByRole('button', { name: '搜索对话' })).toBeVisible()
  await expect(page.getByRole('button', { name: '收起历史对话' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新对话' })).toBeVisible()
})

test('历史对话条目支持键盘切换', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=smart-ask')
  const item = page.locator('.history-item').first()
  await expect(item).toBeVisible()
  await expect(item).toHaveAttribute('role', 'button')
  await expect(item).toHaveAttribute('tabindex', '0')
  await expect(item).toHaveAttribute('aria-label', /对话/)
})

test('问数数据源浏览入口支持键盘打开', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=smart-ask')
  await page.getByRole('button', { name: /指定数据源/ }).click()
  const browse = page.locator('.ds-item-browse').first()
  await expect(browse).toHaveAttribute('role', 'button')
  await expect(browse).toHaveAttribute('tabindex', '0')
  await browse.focus()
  await browse.press('Enter')
  await expect(page.locator('.datasource-browse-drawer')).toBeVisible()
  const browseTabs = page.locator('.datasource-browse-drawer .browse-tabs')
  await expect(browseTabs).toHaveAttribute('role', 'tablist')
  await expect(browseTabs.locator('[role="tab"]').first()).toHaveAttribute('aria-selected', 'true')
})

test('顶部导航暴露可聚焦链接语义', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=insight')
  const links = page.locator('.nav-menu a.nav-item')
  await expect(links).toHaveCount(5)
  for (let index = 0; index < 5; index += 1) {
    await expect(links.nth(index)).toHaveAttribute('href', /\/?nav=/)
  }
  await expect(page.getByRole('button', { name: '消息通知' })).toBeVisible()
  await expect(page.getByRole('button', { name: '切换主题' })).toBeVisible()
})

test('配置中心分类暴露 Tab 语义和选中状态', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=config')
  const tablist = page.locator('.config-tabs')
  await expect(tablist).toHaveAttribute('role', 'tablist')
  const tabs = tablist.locator('[role="tab"]')
  await expect(tabs.first()).toHaveAttribute('aria-selected', 'true')
  await expect(tabs.first()).toHaveAttribute('tabindex', '0')
  if (await tabs.count() > 1) {
    await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'false')
    await tabs.first().press('ArrowRight')
    await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'true')
    await expect(tabs.nth(1)).toHaveAttribute('tabindex', '0')
  }
})

test('工作空间二级菜单支持键盘切换', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=config')
  await page.getByRole('tab', { name: '工作空间' }).click()
  const menu = page.locator('.workspace-sidebar .sub-menu-item')
  await expect(menu.first()).toHaveAttribute('role', 'tab')
  await expect(menu.first()).toHaveAttribute('tabindex', '0')
  if (await menu.count() > 1) {
    await menu.nth(1).focus()
    await menu.nth(1).press('Enter')
    await expect(menu.nth(1)).toHaveAttribute('aria-selected', 'true')
  }
})

test('帮助页图标操作暴露可访问名称', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=help')
  await expect(page.getByRole('button', { name: '展开全部' })).toBeVisible()
  await expect(page.getByRole('button', { name: '收起全部' })).toBeVisible()
  await expect(page.getByRole('button', { name: '搜索' })).toBeVisible()
})

test('指标平台分页控件暴露可访问名称', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=config')
  await page.getByRole('tab', { name: '数据配置' }).click()
  await page.getByText('E2E Aloudata Simulation', { exact: true }).click()
  await expect(page.getByRole('combobox', { name: '指标每页条数' })).toHaveCount(1)
  await expect(page.getByRole('combobox', { name: '维度每页条数' })).toHaveCount(1)
})

test('洞察列表状态标签在四主题下满足对比度', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=insight')
  await expect(page.locator('.dashboard-card').first()).toBeVisible()
  const snapshots = await page.evaluate(async () => {
    const themes = ['light', 'warm', 'eye-care', 'dark']
    const parseRgb = (value: string): [number, number, number] => {
      const match = value.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/)
      if (!match) throw new Error(`无法解析颜色 ${value}`)
      return [Number(match[1]), Number(match[2]), Number(match[3])]
    }
    const luminance = (value: string): number => parseRgb(value).map(channel => {
      const normalized = channel / 255
      return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
    }).reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0)
    const contrast = (foreground: string, background: string): number => {
      const foregroundLum = luminance(foreground)
      const backgroundLum = luminance(background)
      return (Math.max(foregroundLum, backgroundLum) + 0.05) / (Math.min(foregroundLum, backgroundLum) + 0.05)
    }
    const effectiveBackground = (element: HTMLElement): string => {
      let current: HTMLElement | null = element
      while (current) {
        const background = getComputedStyle(current).backgroundColor
        if (background !== 'rgba(0, 0, 0, 0)') return background
        current = current.parentElement
      }
      return getComputedStyle(document.documentElement).backgroundColor
    }
    const result = [] as Array<{ theme: string; ratios: number[] }>
    for (const theme of themes) {
      document.documentElement.setAttribute('data-theme', theme)
      await new Promise(resolve => requestAnimationFrame(() => resolve(undefined)))
      const template = document.querySelector<HTMLElement>('.card-status')
      if (!template) throw new Error('缺少状态标签样本')
      const synthetic = ['success', 'warning'].map(status => {
        const element = template.cloneNode(true) as HTMLElement
        element.classList.remove('el-tag--success', 'el-tag--warning')
        element.classList.add(`el-tag--${status}`)
        element.textContent = status
        document.body.append(element)
        return element
      })
      const actionTemplate = document.querySelector<HTMLElement>('.card-action-btn')
      if (!actionTemplate) throw new Error('缺少卡片操作按钮样本')
      const actionHost = document.querySelector<HTMLElement>('.dashboard-card')
      if (!actionHost) throw new Error('缺少仪表盘卡片样本')
      const actions = ['action-unpublish', 'action-delete'].map(action => {
        const element = actionTemplate.cloneNode(true) as HTMLElement
        element.classList.add(action)
        element.textContent = action
        actionHost.append(element)
        return element
      })
      result.push({
        theme,
        ratios: [...synthetic, ...actions].map(element => {
          const style = getComputedStyle(element)
          return contrast(style.color, effectiveBackground(element))
        }),
      })
      synthetic.forEach(element => element.remove())
      actions.forEach(element => element.remove())
    }
    return result
  })
  for (const snapshot of snapshots) {
    expect(snapshot.ratios.every(ratio => ratio >= 4.5), snapshot.theme).toBe(true)
  }
})

test('仪表盘预览状态圆点跟随主题状态令牌', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=insight')
  const card = page.locator('.dashboard-card').first()
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: '预览' }).click()
  await expect(page.locator('.dashboard-preview-view')).toBeVisible()
  await expect(page.locator('.toolbar-status-dot')).toBeVisible()
  const colors = await page.evaluate(() => {
    document.documentElement.setAttribute('data-theme', 'dark')
    const dot = document.querySelector<HTMLElement>('.toolbar-status-dot')
    if (!dot) throw new Error('预览页缺少状态圆点')
    const style = getComputedStyle(dot)
    const tokenProbe = document.createElement('span')
    tokenProbe.style.backgroundColor = 'var(--db-status-warning-fg)'
    document.body.append(tokenProbe)
    const expected = getComputedStyle(tokenProbe).backgroundColor
    tokenProbe.remove()
    return { actual: style.backgroundColor, expected }
  })
  expect(colors.actual).toBe(colors.expected)
})

test('洞察列表筛选和视图切换暴露当前状态', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=insight')
  await expect(page.locator('.dashboard-card').first()).toBeVisible()
  await expect(page.locator('.filter-tab.active')).toHaveAttribute('aria-pressed', 'true')
  await expect(page.locator('.filter-tab:not(.active)')).toHaveCount(2)
  await expect(page.locator('.filter-tab:not(.active)').first()).toHaveAttribute('aria-pressed', 'false')
  await page.getByRole('button', { name: /草稿/ }).first().click()
  await expect(page.locator('.filter-tab.active')).toHaveText(/草稿/)
  await expect(page.locator('.filter-tab.active')).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByTitle('网格视图')).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByTitle('列表视图')).toHaveAttribute('aria-pressed', 'false')
  await expect(page.getByRole('button', { name: '网格视图' })).toBeVisible()
  await expect(page.getByRole('button', { name: '列表视图' })).toBeVisible()
  await page.getByTitle('列表视图').click()
  await expect(page.getByTitle('列表视图')).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByTitle('网格视图')).toHaveAttribute('aria-pressed', 'false')
})

test('仪表盘卡片支持键盘打开预览', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=insight')
  const card = page.locator('.dashboard-card').first()
  await expect(card).toHaveAttribute('role', 'button')
  await expect(card).toHaveAttribute('tabindex', '0')
  await card.focus()
  await card.press('Enter')
  await expect(page.locator('.dashboard-preview-view')).toBeVisible()
})

test('仪表盘卡片内部编辑按钮的键盘操作不触发卡片预览', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=insight')
  const card = page.locator('.dashboard-card').first()
  await expect(card).toBeVisible()
  const edit = card.getByRole('button', { name: '编辑' })
  await edit.focus()
  await edit.press('Enter')
  await expect(page.locator('.editor-pages')).toBeVisible()
  await expect(page.locator('.dashboard-preview-view')).toHaveCount(0)
})

test('洞察 AI 助手关闭按钮暴露可访问名称', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=insight')
  await expect(page.locator('.dashboard-card').first()).toBeVisible()
  await page.getByRole('button', { name: /AI助手/ }).first().click()
  await expect(page.getByRole('button', { name: '关闭 AI 助手' })).toBeVisible()
  await page.getByRole('button', { name: '关闭 AI 助手' }).click()
  await expect(page.getByRole('button', { name: '关闭 AI 助手' })).toHaveCount(0)
})

test('仪表盘页面树更多操作按钮暴露可访问名称', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=insight')
  const card = page.locator('.dashboard-card').first()
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: '编辑' }).click()
  await expect(page.locator('.editor-pages')).toBeVisible()
  await expect(page.locator('.insight-editor-view > .editor-toolbar .back-btn')).toHaveAccessibleName('返回')
  await expect(page.locator('.toolbar-owner-input input')).toHaveAccessibleName('负责人')
  await expect(page.locator('.panel-collapse-btn')).toHaveAccessibleName('收起面板')
  await expect(page.locator('.palette-collapse-btn')).toHaveAccessibleName('收起面板')
  await expect(page.getByRole('button', { name: '页面操作' }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '新增页面' })).toBeVisible()
  const titleEditor = page.locator('h2.toolbar-title')
  await expect(titleEditor).toHaveAttribute('tabindex', '0')
  await titleEditor.focus()
  await titleEditor.press('Enter')
  await expect(page.locator('input.toolbar-name-input')).toBeVisible()
})

test('仪表盘预览多页面 Tab 暴露选中状态', async ({ page, request }) => {
  const token = required('MATECLAW_E2E_TOKEN')
  const workspace = required('MATECLAW_E2E_WORKSPACE_ID')
  const headers = { Authorization: `Bearer ${token}`, 'X-Workspace-Id': workspace }
  const dashboardName = `E2E A11y Tabs ${Date.now()}`
  const schemaJson = JSON.stringify({
    version: '1.1',
    pages: [
      { id: 'page-one', name: '首页', components: [] },
      { id: 'page-two', name: '明细页', components: [] },
    ],
    datasetInputs: [],
    script: '',
  })
  const create = await request.post('/dataagent/api/v1/insight/dashboards', {
    headers,
    data: { name: dashboardName, description: 'temporary tab semantics', schemaJson },
  })
  expect(create.ok()).toBeTruthy()
  const created = await create.json() as { data?: { id?: string } }
  const dashboardId = created.data?.id
  expect(dashboardId).toBeTruthy()

  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: token, workspaceId: workspace })
  try {
    await page.goto('/?nav=insight')
    const card = page.locator('.dashboard-card').filter({ hasText: dashboardName })
    await expect(card).toHaveCount(1)
    await card.getByRole('button', { name: '预览' }).click()
    await expect(page.locator('.dashboard-preview-view')).toBeVisible()
    await expect(page.locator('.page-bar').first()).toHaveAttribute('role', 'tablist')
    const tabs = page.locator('.page-bar').first().getByRole('tab')
    await expect(tabs.first()).toHaveAttribute('aria-selected', 'true')
    await tabs.first().focus()
    await tabs.first().press('ArrowRight')
    await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'true')
    await expect(tabs.nth(1)).toBeFocused()
  } finally {
    if (dashboardId) await request.delete(`/dataagent/api/v1/insight/dashboards/${dashboardId}`, { headers })
  }
})

test('仪表盘预览数据表多 Tab 支持键盘切换', async ({ page, request }) => {
  const token = required('MATECLAW_E2E_TOKEN')
  const workspace = required('MATECLAW_E2E_WORKSPACE_ID')
  const headers = { Authorization: `Bearer ${token}`, 'X-Workspace-Id': workspace }
  const dashboardName = `E2E Widget Tabs ${Date.now()}`
  const emptySource = { datasourceId: '', metrics: [], dimensions: [], filters: [], limit: 100 }
  const schemaJson = JSON.stringify({
    version: '1.1',
    pages: [{
      id: 'widget-tabs-page',
      name: '多 Tab',
      components: [{
        id: 'widget-tabs-table',
        type: 'table',
        title: '多 Tab 数据表',
        position: { x: 0, y: 0, w: 12, h: 6 },
        renderType: 'table',
        dataSource: emptySource,
        tabs: [
          { id: 'overview', title: '概览', dataSource: emptySource },
          { id: 'detail', title: '明细', dataSource: emptySource },
        ],
      }],
    }],
    datasetInputs: [],
    script: '',
  })
  const create = await request.post('/dataagent/api/v1/insight/dashboards', {
    headers,
    data: { name: dashboardName, description: 'temporary widget tab semantics', schemaJson },
  })
  expect(create.ok()).toBeTruthy()
  const created = await create.json() as { data?: { id?: string } }
  const dashboardId = created.data?.id
  expect(dashboardId).toBeTruthy()
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: token, workspaceId: workspace })
  try {
    await page.goto('/?nav=insight')
    const card = page.locator('.dashboard-card').filter({ hasText: dashboardName })
    await expect(card).toHaveCount(1)
    await card.getByRole('button', { name: '预览' }).click()
    await expect(page.locator('.dashboard-preview-view')).toBeVisible()
    const tabs = page.locator('.data-table-widget [role="tab"]')
    await expect(tabs).toHaveCount(2)
    await expect(tabs.first()).toHaveAttribute('aria-selected', 'true')
    await tabs.first().focus()
    await tabs.first().press('ArrowRight')
    await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'true')
    await expect(tabs.nth(1)).toBeFocused()
  } finally {
    if (dashboardId) await request.delete(`/dataagent/api/v1/insight/dashboards/${dashboardId}`, { headers })
  }
})

test('从产品入口创建文件数据集并进入预览', async ({ page, request }) => {
  const token = required('MATECLAW_E2E_TOKEN')
  const workspace = required('MATECLAW_E2E_WORKSPACE_ID')
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: token, workspaceId: workspace })

  await page.goto('/?nav=config')
  await page.getByRole('tab', { name: '数据配置' }).click()
  await page.getByRole('button', { name: '数据集管理' }).click()
  await page.getByRole('button', { name: '新建数据集' }).click()

  const datasetName = `E2E File Dataset ${Date.now()}`
  await page.locator('#dataset-name').fill(datasetName)
  await page.locator('#dataset-source-type').selectOption('FILE')
  await page.locator('input[type="file"]').setInputFiles({
    name: 'e2e-orders.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from('id,region,amount\n1,east,120.5\n2,west,80.0\n'),
  })
  await expect(page.getByRole('button', { name: '完成，开始数据处理' })).toBeEnabled()

  const createResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().includes('/dataagent/api/v1/datasets'))
  await page.getByRole('button', { name: '完成，开始数据处理' }).click()
  const createResponse = await createResponsePromise
  const created = await createResponse.json() as { data?: { id?: string } }
  const datasetId = created.data?.id
  await expect(page.getByText('字段大纲')).toBeVisible()
  const fieldGroup = page.locator('.group-header').first()
  await expect(fieldGroup).toHaveAttribute('role', 'button')
  await expect(fieldGroup).toHaveAttribute('aria-expanded', 'true')
  const fieldVisibilityButton = page.locator('.field-eye-btn').first()
  if (await fieldVisibilityButton.count()) {
    await expect(fieldVisibilityButton).toHaveAttribute('type', 'button')
    await expect(fieldVisibilityButton).toHaveAttribute('aria-label', /显示|隐藏/)
  }
  await fieldGroup.press('Enter')
  await expect(fieldGroup).toHaveAttribute('aria-expanded', 'false')
  await expect(page.locator('.data-table')).toContainText('120.5')
  await expect(page.locator('.data-table')).toContainText('east')

  if (datasetId) {
    await request.delete(`/dataagent/api/v1/datasets/${datasetId}`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Workspace-Id': workspace },
    })
  }
})

test('从洞察产品入口创建仪表盘并绑定脚本数据集', async ({ page, request }) => {
  const token = required('MATECLAW_E2E_TOKEN')
  const workspace = required('MATECLAW_E2E_WORKSPACE_ID')
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: token, workspaceId: workspace })

  await page.goto('/?nav=insight')
  const createResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().includes('/dataagent/api/v1/insight/dashboards'))
  await page.getByRole('button', { name: '新建仪表盘' }).click()
  const createResponse = await createResponsePromise
  const created = await createResponse.json() as { data?: { id?: string } }
  const dashboardId = created.data?.id
  expect(dashboardId).toBeTruthy()

  await page.getByRole('heading', { name: '未命名仪表盘' }).click()
  await page.locator('.toolbar-name-input').fill(`E2E UI Dashboard ${Date.now()}`)
  await page.locator('.toolbar-name-input').press('Enter')
  await page.locator('.palette-item').filter({ hasText: '数据表格' }).dragTo(page.locator('.dashboard-canvas'))
  await expect(page.getByRole('button', { name: /删除组件/ })).toBeVisible()
  await expect(page.locator('.dataset-input-panel')).toContainText('当前目标组件：')
  await expect(page.getByRole('textbox', { name: '组件标题' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: '数据源' })).toBeVisible()
  await expect(page.getByRole('spinbutton', { name: '数据行数' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: '绑定筛选器' })).toBeVisible()
  await expect(page.getByRole('switch', { name: '多 Tab 模式' })).toBeAttached()
  await expect(page.getByRole('switch', { name: '组件级时间筛选' })).toBeAttached()
  await page.locator('.dataset-input-panel').getByRole('button', { name: '添加', exact: true }).click()
  await expect(page.getByRole('combobox', { name: '选择要接收脚本结果的组件' })).toBeVisible()
  await expect(page.locator('.dataset-input-panel .dataset-input-row').last().getByRole('combobox', { name: '选择已授权数据集' })).toBeVisible()
  const datasetSelect = page.locator('.dataset-input-panel .dataset-input-row').last().locator('.el-select').first()
  await datasetSelect.click()
  await page.getByRole('option', { name: /E2E HTTP Orders Dataset/ }).click()
  const aliasInput = page.locator('.dataset-input-panel .alias-input input').last()
  await aliasInput.fill('bad alias')
  await expect(aliasInput).toHaveAttribute('aria-describedby', /dataset-alias-error-/)
  await expect(page.locator('[id^="dataset-alias-error-"]')).toBeVisible()
  await aliasInput.fill('api_orders')
  await page.locator('.dataset-input-panel textarea').fill('rows = datasets.read(input_name="api_orders")\nresult = rows.to_polars().to_dicts()')
  await expect(page.locator('.dataset-input-panel')).toContainText('结果绑定组件')

  const updateResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'PUT' && response.url().includes(`/dataagent/api/v1/insight/dashboards/${dashboardId}`))
  await page.getByRole('button', { name: '保存' }).click()
  await updateResponsePromise
  await expect(page.locator('.dataset-input-panel .alias-input input').last()).toHaveValue('api_orders')

  if (dashboardId) {
    await request.delete(`/dataagent/api/v1/insight/dashboards/${dashboardId}`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Workspace-Id': workspace },
    })
  }
})

test('数据集空态随四种主题使用主题令牌', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/datasets/new')
  await expect(page.locator('.empty-state')).toBeVisible()
  const snapshots = await page.evaluate(async () => {
    const themes = ['light', 'warm', 'eye-care', 'dark']
    const read = (selector: string) => {
      const element = document.querySelector(selector)
      if (!element) throw new Error(`missing ${selector}`)
      const style = getComputedStyle(element)
      return { background: style.backgroundColor, color: style.color }
    }
    return themes.map(theme => {
      document.documentElement.dataset.theme = theme
      const root = getComputedStyle(document.documentElement)
      const normalize = (value: string, property: 'color' | 'backgroundColor') => {
        const probe = document.createElement('span')
        probe.style[property] = value
        document.body.appendChild(probe)
        const normalized = getComputedStyle(probe)[property]
        probe.remove()
        return normalized
      }
      return {
        theme,
        empty: read('.empty-state'),
        title: read('.empty-title'),
        illustration: (() => {
          const element = document.querySelector('.empty-illustration .illustration-card') as SVGElement | null
          if (!element) throw new Error('missing illustration card')
          return { fill: getComputedStyle(element).fill, stroke: getComputedStyle(element).stroke }
        })(),
        expectedBackground: normalize(root.getPropertyValue('--theme-surface').trim(), 'backgroundColor'),
        expectedText: normalize(root.getPropertyValue('--theme-text').trim(), 'color'),
        expectedIllustrationFill: normalize(root.getPropertyValue('--theme-surface-hover').trim(), 'backgroundColor'),
        expectedIllustrationStroke: normalize(root.getPropertyValue('--theme-border-strong').trim(), 'color'),
      }
    })
  })
  for (const snapshot of snapshots) {
    expect(snapshot.empty.background, snapshot.theme).toBe(snapshot.expectedBackground)
    expect(snapshot.title.color, snapshot.theme).toBe(snapshot.expectedText)
    expect(snapshot.illustration.fill, snapshot.theme).toBe(snapshot.expectedIllustrationFill)
    expect(snapshot.illustration.stroke, snapshot.theme).toBe(snapshot.expectedIllustrationStroke)
  }
})

test('数据集列表状态和辅助文字满足主题对比度', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/datasets')
  await expect(page.locator('.dataset-card').first()).toBeVisible()
  const results = await page.evaluate(() => {
    const rgb = (value: string) => {
      const parts = value.match(/\d+(?:\.\d+)?/g)
      return parts ? parts.slice(0, 3).map(Number) : null
    }
    const luminance = (value: number[]) => {
      const channels = value.map(channel => channel / 255).map(channel => channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4)
      return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    }
    const contrast = (foreground: string, background: string) => {
      const fg = rgb(foreground)
      const bg = rgb(background)
      if (!fg || !bg) return null
      const light = Math.max(luminance(fg), luminance(bg))
      const dark = Math.min(luminance(fg), luminance(bg))
      return (light + 0.05) / (dark + 0.05)
    }
    const themes = ['light', 'warm', 'eye-care', 'dark']
    return themes.map(theme => {
      document.documentElement.dataset.theme = theme
      const muted = getComputedStyle(document.querySelector('.page-header p')!).color
      const status = getComputedStyle(document.querySelector('.status')!).color
      const surface = getComputedStyle(document.documentElement).getPropertyValue('--theme-surface').trim()
      const probe = document.createElement('span')
      probe.style.backgroundColor = surface
      document.body.appendChild(probe)
      const background = getComputedStyle(probe).backgroundColor
      probe.remove()
      return { theme, mutedContrast: contrast(muted, background), statusContrast: contrast(status, background) }
    })
  })
  for (const result of results) {
    expect(result.mutedContrast, result.theme).toBeGreaterThanOrEqual(4.5)
    expect(result.statusContrast, result.theme).toBeGreaterThanOrEqual(4.5)
  }
})

test('数据集预览工具栏和结果空态随主题使用主题令牌', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/datasets')
  const datasetCard = page.locator('.dataset-card').filter({ hasText: 'E2E File Orders Dataset' })
  await datasetCard.getByRole('button', { name: '编辑 / 预览' }).click()
  await expect(page.locator('.toolbar')).toBeVisible()
  const snapshots = await page.evaluate(() => {
    const themes = ['light', 'warm', 'eye-care', 'dark']
    const read = (selector: string) => {
      const element = document.querySelector(selector)
      if (!element) throw new Error(`missing ${selector}`)
      const style = getComputedStyle(element)
      return { background: style.backgroundColor, color: style.color }
    }
    const readOptional = (selector: string) => {
      const element = document.querySelector(selector)
      if (!element) return null
      const style = getComputedStyle(element)
      return { background: style.backgroundColor, color: style.color }
    }
    const normalize = (value: string, property: 'color' | 'backgroundColor') => {
      const probe = document.createElement('span')
      probe.style[property] = value
      document.body.appendChild(probe)
      const normalized = getComputedStyle(probe)[property]
      probe.remove()
      return normalized
    }
    return themes.map(theme => {
      document.documentElement.dataset.theme = theme
      const root = getComputedStyle(document.documentElement)
      return {
        theme,
        toolbar: read('.toolbar'),
        preview: read('.data-preview'),
        empty: readOptional('.preview-empty'),
        expectedSurface: normalize(root.getPropertyValue('--theme-surface').trim(), 'backgroundColor'),
        expectedSecondary: normalize(root.getPropertyValue('--theme-text-secondary').trim(), 'color'),
      }
    })
  })
  for (const snapshot of snapshots) {
    expect(snapshot.toolbar.background, snapshot.theme).toBe(snapshot.expectedSurface)
    expect(snapshot.preview.background, snapshot.theme).toBe(snapshot.expectedSurface)
    if (snapshot.empty) expect(snapshot.empty.color, snapshot.theme).toBe(snapshot.expectedSecondary)
  }
})

test('数据源配置表单基础容器随主题使用主题令牌', async ({ page }) => {
  await page.addInitScript(({ authToken, workspaceId }) => {
    localStorage.setItem('token', authToken)
    localStorage.setItem('workspaceId', JSON.stringify(workspaceId))
  }, { authToken: required('MATECLAW_E2E_TOKEN'), workspaceId: required('MATECLAW_E2E_WORKSPACE_ID') })

  await page.goto('/?nav=config')
  await page.getByRole('tab', { name: '数据配置' }).click()
  await page.getByRole('button', { name: '新建数据源' }).click()
  await page.getByRole('button', { name: '选择MySQL数据源' }).click()
  await expect(page.locator('.datasource-form-page')).toBeVisible()
  await expect(page.getByRole('button', { name: '关闭数据源配置' })).toBeVisible()
  await expect(page.getByRole('checkbox', { name: 'SSL' })).toHaveCount(1)
  await expect(page.getByRole('checkbox', { name: 'SSH' })).toHaveCount(1)
  await expect(page.getByRole('checkbox', { name: '跨 VPC/SQL' })).toHaveCount(1)
  await expect(page.getByRole('checkbox', { name: '开启上传文件入口' })).toHaveCount(1)
  await expect(page.getByRole('checkbox', { name: '共享元数据' })).toHaveCount(1)
  await expect(page.getByRole('textbox', { name: '端口' })).toHaveAccessibleName('端口')
  await expect(page.getByRole('textbox', { name: '密码' })).toHaveAccessibleName('密码')
  const snapshots = await page.evaluate(async () => {
    const themes = ['light', 'warm', 'eye-care', 'dark']
    const read = (selector: string) => {
      const element = document.querySelector(selector)
      if (!element) throw new Error(`missing ${selector}`)
      const style = getComputedStyle(element)
      return { background: style.backgroundColor, color: style.color }
    }
    const normalize = (value: string, property: 'color' | 'backgroundColor') => {
      const probe = document.createElement('span')
      probe.style[property] = value
      document.body.appendChild(probe)
      const normalized = getComputedStyle(probe)[property]
      probe.remove()
      return normalized
    }
    const snapshots = []
    for (const theme of themes) {
      document.documentElement.dataset.theme = theme
      // 输入控件声明了 transition: all，等待过渡完成后再读取计算样式，避免读到上一主题。
      await new Promise(resolve => setTimeout(resolve, 450))
      const root = getComputedStyle(document.documentElement)
      snapshots.push({
        theme,
        page: read('.datasource-form-page'),
        header: read('.form-header'),
        card: read('.form-card'),
        title: read('.form-title'),
        label: read('.form-card .form-label'),
        input: read('.form-card input.form-input'),
        whitelist: read('.whitelist-box-new'),
        whitelistText: read('.whitelist-text-new'),
        cancel: read('.btn-cancel'),
        submit: read('.btn-submit'),
        step: read('.step-item.active .step-num'),
        expectedBg: normalize(root.getPropertyValue('--theme-surface').trim(), 'backgroundColor'),
        expectedSurfaceHover: normalize(root.getPropertyValue('--theme-surface-hover').trim(), 'backgroundColor'),
        expectedPageBg: normalize(root.getPropertyValue('--theme-bg').trim(), 'backgroundColor'),
        expectedText: normalize(root.getPropertyValue('--theme-text').trim(), 'color'),
        expectedSecondary: normalize(root.getPropertyValue('--theme-text-secondary').trim(), 'color'),
        expectedMain: normalize(root.getPropertyValue('--main-orange').trim(), 'backgroundColor'),
      })
    }
    return snapshots
  })
  for (const snapshot of snapshots) {
    expect(snapshot.page.background, snapshot.theme).toBe(snapshot.expectedPageBg)
    expect(snapshot.header.background, snapshot.theme).toBe(snapshot.expectedBg)
    expect(snapshot.card.background, snapshot.theme).toBe(snapshot.expectedBg)
    expect(snapshot.title.color, snapshot.theme).toBe(snapshot.expectedText)
    expect(snapshot.label.color, snapshot.theme).toBe(snapshot.expectedSecondary)
    expect(snapshot.input.background, snapshot.theme).toBe(snapshot.expectedBg)
    expect(snapshot.input.color, snapshot.theme).toBe(snapshot.expectedText)
    expect(snapshot.whitelist.background, snapshot.theme).toBe(snapshot.expectedSurfaceHover)
    expect(snapshot.whitelistText.color, snapshot.theme).toBe(snapshot.expectedSecondary)
    expect(snapshot.cancel.background, snapshot.theme).toBe(snapshot.expectedBg)
    expect(snapshot.submit.background, snapshot.theme).toBe(snapshot.expectedMain)
    expect(snapshot.step.background, snapshot.theme).toBe(snapshot.expectedMain)
  }
})
