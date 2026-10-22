/* Local-only, deterministic workbench prototype. No API calls or backend. */
(() => {
  const branches = [
    { name: '深圳', code: 'SZ', base: 1820 },
    { name: '上海', code: 'SH', base: 1560 },
    { name: '北京', code: 'BJ', base: 1320 },
    { name: '广州', code: 'GZ', base: 1080 },
    { name: '杭州', code: 'HZ', base: 870 },
    { name: '南京', code: 'NJ', base: 760 },
    { name: '成都', code: 'CD', base: 690 },
    { name: '武汉', code: 'WH', base: 620 },
    { name: '天津', code: 'TJ', base: 540 },
  ];
  const officeNames = ['福田营业部', '南山营业部', '罗湖营业部', '宝安营业部'];
  const months = Array.from({ length: 12 }, (_, i) => `${i + 1}月`);
  const periods = { '3': '近 3 个月', '6': '近 6 个月', '12': '近 12 个月' };
  const state = {
    period: '12', branch: '全部分公司', orgPath: [], page: '经营总览', pageNo: 1,
    chartType: 'line', compare: true, sort: 'desc', experience: true,
    pages: ['经营总览', '收入分析', '客户分析', '交易分析'],
    dark: false, lastSaved: '本地草稿',
  };
  const undoStack = [];
  const $ = id => document.getElementById(id);
  const fmt = n => `${Math.round(n).toLocaleString('zh-CN')} 万`;
  const esc = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const metric = (branch, month) => branch.base + month * 43 + ((month * 17 + branches.indexOf(branch) * 31) % 113);
  const branchRows = () => branches.map((b, i) => ({ ...b, value: months.slice(-Number(state.period)).reduce((s, _, j) => s + metric(b, j + 12 - Number(state.period)), 0), growth: 4.1 + (4 - i) * 1.35 }));
  const currentBranch = () => branchRows().find(b => b.name === state.orgPath[0]);
  const visibleBranches = () => state.branch === '全部分公司' ? branchRows() : branchRows().filter(b => b.name === state.branch);
  const officeRows = branch => officeNames.map((name, i) => ({
    name, branch: branch.name,
    value: Math.round(branch.value * [0.34, 0.29, 0.22, 0.15][i]),
    growth: 2.8 + ((i * 7 + branches.indexOf(branch) * 3) % 97) / 10,
  }));
  const total = () => visibleBranches().reduce((sum, row) => sum + row.value, 0);
  const rowsForTable = () => {
    let rows = state.orgPath.length ? officeRows(currentBranch()) : visibleBranches();
    if (!state.orgPath.length && state.branch !== '全部分公司') rows = officeRows(visibleBranches()[0]);
    return [...rows].sort((a, b) => state.sort === 'desc' ? b.value - a.value : a.value - b.value);
  };
  const pageTitle = () => state.page;
  const periodRows = () => months.slice(-Number(state.period)).map((month, i) => {
    const index = 12 - Number(state.period) + i;
    const group = state.orgPath.length ? officeRows(currentBranch()).filter(o => o.name === state.orgPath[1]) : visibleBranches();
    return { month, value: group.reduce((sum, row) => sum + row.value / Number(state.period) * (0.74 + index * 0.035 + (index % 3) * 0.012), 0), prior: group.reduce((sum, row) => sum + row.value / Number(state.period) * (0.68 + index * 0.032), 0) };
  });

  function filters() {
    const crumbs = state.orgPath.map((name, i) => `<button data-crumb="${i}">${esc(name)}</button>${i < state.orgPath.length - 1 ? '<span>›</span>' : ''}`).join('');
    return `<div class="app-filters">
      <label>统计周期<select id="periodFilter" aria-label="统计周期">${Object.entries(periods).map(([v, t]) => `<option value="${v}" ${state.period === v ? 'selected' : ''}>${t}</option>`).join('')}</select></label>
      <label>机构范围<select id="branchFilter" aria-label="机构范围"><option>全部分公司</option>${branches.map(b => `<option ${state.branch === b.name ? 'selected' : ''}>${b.name}</option>`).join('')}</select></label>
      <label>排名顺序<select id="sortFilter" aria-label="排名顺序"><option value="desc" ${state.sort === 'desc' ? 'selected' : ''}>收入从高到低</option><option value="asc" ${state.sort === 'asc' ? 'selected' : ''}>收入从低到高</option></select></label>
      <button data-reset-filters>重置筛选</button>
      <div class="crumbs" aria-label="组织层级"><button data-crumb="-1">全部机构</button>${crumbs}</div>
      <span class="pill amber">演示数据 · 无后端</span>
    </div>`;
  }

  function pageTabs() {
    return `<div class="app-pages" role="tablist" aria-label="经营分析页面">${state.pages.map(p => `<button role="tab" aria-selected="${state.page === p}" class="${state.page === p ? 'active' : ''}" data-page="${esc(p)}">${esc(p)}</button>`).join('')}</div>`;
  }

  function kpis() {
    const sum = total();
    const prior = sum * 0.934;
    const customers = Math.round(sum * 3.4);
    const trading = Math.round(sum * 12.8);
    const primary = state.page === '客户分析' ? ['月活客户', customers, '人', '较上期 +6.8%'] : state.page === '交易分析' ? ['交易规模', trading, '万元', '较上期 +5.2%'] : ['营业收入', sum, '万元', `较上期 +${((sum / prior - 1) * 100).toFixed(1)}%`];
    const cards = [primary, ['收入同比', 7.6 + Number(state.period) / 10, '%', '同比增长'], ['新增客户', Math.round(customers * 0.12), '人', '较上期 +3.4%']];
    return `<div class="grid3">${cards.map(([label, val, unit, delta]) => `<div class="kpi metric-card"><span>${label}</span><div class="value">${typeof val === 'number' && unit === '万元' ? fmt(val) : `${Math.round(val).toLocaleString('zh-CN')}${unit}`}</div><div class="delta">↗ ${delta}</div></div>`).join('')}</div>`;
  }

  function chartMarkup() {
    const data = periodRows();
    const max = Math.max(...data.map(x => x.value), 1) * 1.18;
    const x = i => 26 + (i * 478 / Math.max(data.length - 1, 1));
    const y = v => 155 - (v / max) * 122;
    const line = data.map((d, i) => `${i ? 'L' : 'M'}${x(i)},${y(d.value)}`).join(' ');
    const prior = data.map((d, i) => `${i ? 'L' : 'M'}${x(i)},${y(d.prior)}`).join(' ');
    const area = `${line} L${x(data.length - 1)},158 L${x(0)},158 Z`;
    const plot = state.chartType === 'area' ? `<path d="${area}" fill="#6285ef" opacity=".14"/><path d="${prior}" fill="none" stroke="#bdcbe4" stroke-width="2" stroke-dasharray="5 5"/>` : `<path d="${prior}" fill="none" stroke="#bdcbe4" stroke-width="2" stroke-dasharray="5 5"/>`;
    return `<svg class="chart" viewBox="0 0 530 185" role="img" aria-label="${esc(pageTitle())}月度模拟趋势">
      <path d="M20 30H515M20 75H515M20 120H515M20 158H515" stroke="#e9eef8" stroke-dasharray="4 5" fill="none"/>
      ${plot}<path d="${line}" fill="none" stroke="#5278ef" stroke-width="3"/>
      ${data.map((d, i) => `<circle class="data-point" data-month="${i}" tabindex="0" role="button" aria-label="查看${d.month}收入" cx="${x(i)}" cy="${y(d.value)}" r="4" fill="#5278ef"><title>${d.month} · ${fmt(d.value)}</title></circle>`).join('')}
      ${data.map((d, i) => i % Math.max(1, Math.ceil(data.length / 6)) === 0 || i === data.length - 1 ? `<text x="${x(i)}" y="178" text-anchor="middle" fill="#8796ae" font-size="10">${d.month}</text>` : '').join('')}
    </svg>`;
  }

  function rankMarkup() {
    const rows = visibleBranches();
    const max = Math.max(...rows.map(x => x.value), 1);
    return rows.map(b => `<div class="rank" role="button" tabindex="0" data-drill-branch="${esc(b.name)}" aria-label="下钻到${esc(b.name)}分公司"><span>${esc(b.name)}</span><div class="track"><div class="fill" style="width:${Math.round(b.value / max * 100)}%"></div></div><strong>${fmt(b.value)}</strong><small>›</small></div>`).join('');
  }

  function pagedTable() {
    const rows = rowsForTable();
    const pages = Math.max(1, Math.ceil(rows.length / 5));
    state.pageNo = Math.min(state.pageNo, pages);
    const chunk = rows.slice((state.pageNo - 1) * 5, state.pageNo * 5);
    const heading = state.orgPath.length ? `${state.orgPath[0]} · 营业部明细` : state.branch !== '全部分公司' ? `${state.branch} · 营业部明细` : '分公司经营明细';
    return `<div class="chart-head"><div><h3>${heading}</h3><small>点击分公司进入营业部；点击营业部查看经营明细</small></div><button class="chart-mode" data-sort-toggle>↕ ${state.sort === 'desc' ? '收入降序' : '收入升序'}</button></div>
      <table class="table"><thead><tr><th>机构</th><th>营业收入</th><th>同比</th><th>操作</th></tr></thead><tbody>${chunk.map(r => `<tr data-drill="${esc(r.name)}" data-kind="${state.orgPath.length || state.branch !== '全部分公司' ? 'office' : 'branch'}"><td>${esc(r.name)}</td><td>${fmt(r.value)}</td><td>+${r.growth.toFixed(1)}%</td><td><button data-open-detail="${esc(r.name)}">查看明细 ›</button></td></tr>`).join('')}</tbody></table>
      <div class="pager"><span>第 ${state.pageNo} / ${pages} 页 · 共 ${rows.length} 条</span><div><button data-page-step="-1" ${state.pageNo <= 1 ? 'disabled' : ''}>上一页</button> <button data-page-step="1" ${state.pageNo >= pages ? 'disabled' : ''}>下一页</button></div></div>`;
  }

  function customerPage() {
    const base = Math.round(total() * 3.4);
    return `<div class="grid2"><section class="widget"><h3>客户增长趋势</h3><small>按 ${periods[state.period]} 统计 · 演示数据</small>${chartMarkup()}</section><section class="widget"><h3>客户结构</h3>${[['新增客户',.36],['活跃客户',.72],['存量客户',.91]].map(([n,p]) => `<div class="rank"><span>${n}</span><div class="track"><div class="fill" style="width:${p * 100}%"></div></div><strong>${Math.round(base * p).toLocaleString()}</strong></div>`).join('')}</section></div>${pagedTable()}`;
  }

  function tradingPage() {
    const rows = Array.from({ length: 8 }, (_, i) => ({ id: `TX-${String(i + 1).padStart(4, '0')}`, branch: branches[i % branches.length].name, amount: 34 + ((i * 83) % 210), status: i % 4 === 0 ? '待复核' : '已完成' }));
    return `<section class="widget"><div class="chart-head"><div><h3>交易明细</h3><small>每次刷新保持确定性，仅供交互演示</small></div><button class="btn" data-export>导出当前表格 CSV</button></div><table class="table"><thead><tr><th>交易编号</th><th>分公司</th><th>交易金额（万元）</th><th>状态</th><th>操作</th></tr></thead><tbody>${rows.map(r => `<tr><td>${r.id}</td><td>${r.branch}</td><td>${r.amount}</td><td>${r.status}</td><td><button data-transaction="${r.id}">查看</button></td></tr>`).join('')}</tbody></table></section>`;
  }

  function detailPage(name) {
    const vals = Array.from({ length: 8 }, (_, i) => ({ id: `客户-${String(2101 + i).slice(-4)}`, date: `2026-10-${String(2 + i * 2).padStart(2, '0')}`, amount: `${(12.8 + i * 6.7).toFixed(1)} 万`, status: i % 3 === 0 ? '已复核' : '正常' }));
    return `<div class="notice">${esc(name)} · 经营明细是模拟记录，可继续查看行详情。</div><section class="widget"><div class="chart-head"><div><h3>${esc(name)} · 客户经营明细</h3><small>此处生成的是本地演示记录，不是真实客户信息</small></div><button class="btn" data-back-dashboard>← 返回经营总览</button></div><table class="table"><thead><tr><th>客户编号</th><th>日期</th><th>收入贡献</th><th>状态</th><th></th></tr></thead><tbody>${vals.map(r => `<tr><td>${r.id}</td><td>${r.date}</td><td>${r.amount}</td><td>${r.status}</td><td><button data-customer="${r.id}">查看记录</button></td></tr>`).join('')}</tbody></table></section>`;
  }

  function interactiveDashboard() {
    const isDetail = state.page === '机构明细';
    const heading = isDetail ? `${state.orgPath.at(-1) || state.branch}经营明细` : state.page;
    const subtitle = isDetail ? '从分公司排行下钻，可回到父级并保留筛选条件。' : '按机构和统计周期筛选；点击趋势点查看当月，点击机构逐级下钻。';
    const body = isDetail ? detailPage(state.orgPath.at(-1) || state.branch) : state.page === '客户分析' ? customerPage() : state.page === '交易分析' ? tradingPage() : state.page === '新增客户流失分析' ? customerPage() : `<div class="grid2"><section class="widget"><div class="chart-head"><div><h3>${state.page === '收入分析' ? '收入趋势' : '月度收入趋势'}</h3><small>${state.compare ? '本期与上期对比' : '仅显示本期'} · 点击数据点可查看月份</small></div><button class="chart-mode" data-chart-toggle>⌁ ${state.chartType === 'line' ? '切换面积图' : '切换折线图'}</button></div>${chartMarkup()}</section><section class="widget"><h3>分公司收入排名</h3><small>点击机构下钻到营业部</small>${rankMarkup()}</section></div>${pagedTable()}<div class="grid2"><section class="widget"><h3>经营表现说明</h3><p style="line-height:1.9;color:#7f8ea5;margin-top:12px">${state.orgPath.length ? `${esc(state.orgPath[0])}分公司收入贡献` : '收入整体稳定，深圳与上海分公司位于前列'}；近 ${state.period} 个月统计周期。</p></section><section class="widget analysis"><h3 style="color:#4d71d9">✦ AI 经营解读 · S06 可选</h3><p>当前为演示数据。真实部署后，分析须基于授权数据并附证据来源。</p></section></div>`;
    $('content').innerHTML = `${intro(heading, subtitle)}${pageTabs()}${filters()}${isDetail ? '' : kpis()}${body}${deliver()}`;
    $('context').textContent = `${stages[current].name} / ${state.orgPath.join(' / ') || state.page}`;
    bindFilters();
  }

  function bindFilters() {
    $('periodFilter').onchange = e => { state.period = e.target.value; state.pageNo = 1; refresh(true); };
    $('branchFilter').onchange = e => { state.branch = e.target.value; state.orgPath = []; state.pageNo = 1; refresh(true); };
    $('sortFilter').onchange = e => { state.sort = e.target.value; state.pageNo = 1; refresh(true); };
    document.querySelectorAll('[data-page]').forEach(btn => btn.onclick = () => { state.page = btn.dataset.page; state.pageNo = 1; refresh(true); });
  }

  function recordUndo() {
    undoStack.push(JSON.stringify({ chartType: state.chartType, pages: state.pages, dark: state.dark, page: state.page }));
    if (undoStack.length > 12) undoStack.shift();
    updateUndo();
  }
  function updateUndo() {
    const btn = $('undoChange');
    if (btn) btn.disabled = undoStack.length === 0;
  }
  function saveLocal() {
    const saved = { period: state.period, branch: state.branch, chartType: state.chartType, pages: state.pages, dark: state.dark };
    try { localStorage.setItem('mateclaw-coworking-demo-v1', JSON.stringify(saved)); state.lastSaved = '本机草稿已保存'; }
    catch { state.lastSaved = '本机存储不可用'; }
    $('footerState').textContent = `${stages[current].id} / ${state.lastSaved}`;
  }
  function refresh(persist = false) {
    if (state.page !== '需求蓝图' && state.page !== '产品整体') interactiveDashboard();
    if (persist) saveLocal();
    updateUndo();
  }

  const originalRender = render;
  render = function (...args) {
    originalRender.apply(this, args);
    if (view === 'screen' && (current === 0 || current === 1) && subtab === 0) {
      const canvasBadge = document.querySelector('#canvas .canvas-head .pill');
      if (canvasBadge) canvasBadge.textContent = '演示数据 · 可交互（无后端）';
      const demoNotice = document.querySelector('#content .notice');
      if (current === 1 && demoNotice) demoNotice.textContent = 'S02 本地交互演示：模拟数据驱动页面、筛选与逐级下钻。数据不会发送到后端。';
      $('selectMode').textContent = state.experience ? '▶ 体验模式' : '⌁ 选择模式';
      $('selectMode').classList.toggle('active', state.experience);
      interactiveDashboard();
      addUndoButton();
    }
  };

  function addUndoButton() {
    if ($('undoChange')) return;
    $('handoff').insertAdjacentHTML('beforebegin', '<button class="btn" id="undoChange" disabled>↶ 撤销 AI 变更</button>');
    $('undoChange').onclick = () => {
      const previous = undoStack.pop();
      if (!previous) return;
      Object.assign(state, JSON.parse(previous));
      document.body.classList.toggle('theme-dark', state.dark);
      refresh(true);
      toast('已撤销上一项 AI 变更');
    };
  }

  function toast(message) {
    document.querySelector('.toast')?.remove();
    document.body.insertAdjacentHTML('beforeend', `<div class="toast" role="status" aria-live="polite">${esc(message)}</div>`);
    setTimeout(() => document.querySelector('.toast')?.remove(), 2200);
  }

  function propose(text) {
    const lower = text.toLowerCase();
    let action, label, impact;
    if (/面积图|area/.test(lower)) { action = 'area'; label = '把收入趋势改为面积图'; impact = '只修改当前趋势组件的展示方式，保留筛选范围和指标。'; }
    else if (/流失|新增.*客户/.test(lower)) { action = 'addPage'; label = '新增客户流失分析页'; impact = '新增一个客户专题页面，保留现有页面与导航顺序。'; }
    else if (/删除.*交易|移除.*交易/.test(lower)) { action = 'deletePage'; label = '删除交易分析页'; impact = '从当前工作稿移除该页。此变更可通过撤销恢复。'; }
    else if (/深色|暗黑|深蓝/.test(lower)) { action = 'dark'; label = '应用深蓝色工作台主题'; impact = '仅切换当前浏览器中的展示主题。'; }
    else if (/下钻|钻取/.test(lower)) { action = 'drill'; label = '启用机构逐级下钻'; impact = '保留当前页面，允许从分公司进入营业部及模拟明细。'; }
    else { chatReply('我已经记录这条想法。试试“把收入趋势改成面积图”“新增客户流失分析”“删除交易分析页”“改成深蓝色科技风”或“增加营业部下钻”。'); return; }
    showModal('AI 变更提案 · 待确认', `<p><strong>变更</strong><br>${esc(label)}</p><p><strong>影响范围</strong><br>${esc(impact)}</p><p><strong>目标对象</strong><br>${esc(state.page)} · ${esc(state.orgPath.join(' / ') || '产品整体')}</p><div class="notice">确认后应用为一项可撤销变更；本地演示不会调用 AI 服务或后端。</div><div class="row" style="justify-content:flex-end;margin-top:20px"><button class="btn" id="rejectProposal">保留当前设计</button><button class="btn primary" id="acceptProposal">确认并应用</button></div>`);
    $('rejectProposal').onclick = () => { $('modal').close(); chatReply('已保留当前设计，没有应用这项提案。'); };
    $('acceptProposal').onclick = () => {
      recordUndo(); $('modal').close();
      if (action === 'area') { state.chartType = 'area'; state.page = '收入分析'; }
      if (action === 'addPage' && !state.pages.includes('新增客户流失分析')) state.pages.push('新增客户流失分析');
      if (action === 'deletePage') { state.pages = state.pages.filter(p => p !== '交易分析'); if (state.page === '交易分析') state.page = '经营总览'; }
      if (action === 'dark') { state.dark = true; document.body.classList.add('theme-dark'); }
      if (action === 'drill') { state.orgPath = ['深圳']; state.branch = '深圳'; }
      chatReply(`已应用：${label}。你可以点击“撤销 AI 变更”恢复。`); refresh(true); toast(`已应用：${label}`);
    };
  }

  function chatReply(text) {
    $('messages').insertAdjacentHTML('beforeend', `<div class="message"><div class="who"><span class="ai-badge">AI</span>MateClaw · ${esc(stages[current].agent)}</div><div class="bubble">${esc(text)}</div></div>`);
    $('messages').scrollTop = $('messages').scrollHeight;
  }

  function setupChat() {
    const composer = document.querySelector('.composer');
    composer.innerHTML = `<textarea id="chatInput" rows="2" placeholder="描述你想修改的内容…" aria-label="输入工作台修改要求"></textarea><div class="composer-bottom"><small>本地演示 AI · 点击确认后应用变更</small><button class="send" id="sendChat" aria-label="提交修改要求">↑</button></div>`;
    document.querySelector('.chat-note > small').innerHTML = '演示 AI 不调用模型；变更须由你确认。<br>条件筛选和图表交互均在本地运行。';
    $('sampleChange')?.remove();
    const submit = () => { const input = $('chatInput'); const value = input.value.trim(); if (!value) { input.focus(); return; } $('messages').insertAdjacentHTML('beforeend', `<div class="message user"><div class="who">你 · 刚刚</div><div class="bubble">${esc(value)}</div></div>`); input.value = ''; propose(value); };
    $('sendChat').onclick = submit;
    $('chatInput').addEventListener('keydown', e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(); } });
  }

  document.addEventListener('click', e => {
    const branch = e.target.closest('[data-drill-branch]');
    const tableRow = e.target.closest('[data-drill]');
    const crumb = e.target.closest('[data-crumb]');
    const pageStep = e.target.closest('[data-page-step]');
    const month = e.target.closest('[data-month]');
    const detail = e.target.closest('[data-open-detail]');
    if (branch) { state.orgPath = [branch.dataset.drillBranch]; state.branch = branch.dataset.drillBranch; state.pageNo = 1; refresh(true); }
    else if (crumb) { const level = Number(crumb.dataset.crumb); state.orgPath = level < 0 ? [] : state.orgPath.slice(0, level + 1); state.branch = state.orgPath[0] || '全部分公司'; state.page = '经营总览'; refresh(true); }
    else if (pageStep) { state.pageNo += Number(pageStep.dataset.pageStep); refresh(); }
    else if (e.target.closest('[data-sort-toggle]')) { state.sort = state.sort === 'desc' ? 'asc' : 'desc'; state.pageNo = 1; refresh(); }
    else if (e.target.closest('[data-reset-filters]')) { state.period = '12'; state.branch = '全部分公司'; state.orgPath = []; state.pageNo = 1; refresh(true); toast('已重置筛选条件'); }
    else if (e.target.closest('[data-chart-toggle]')) { state.chartType = state.chartType === 'line' ? 'area' : 'line'; refresh(true); }
    else if (month) { toast(`${periodRows()[Number(month.dataset.month)]?.month || ''} · ${fmt(periodRows()[Number(month.dataset.month)]?.value || 0)}（演示）`); }
    else if (detail) { state.orgPath = state.orgPath.length ? state.orgPath : [state.branch === '全部分公司' ? '深圳' : state.branch]; state.orgPath[1] = detail.dataset.openDetail; state.page = '机构明细'; refresh(); }
    else if (tableRow) {
      if (tableRow.dataset.kind === 'branch') { state.orgPath = [tableRow.dataset.drill]; state.branch = tableRow.dataset.drill; }
      else { state.orgPath = [state.orgPath[0] || state.branch, tableRow.dataset.drill]; state.page = '机构明细'; }
      state.pageNo = 1; refresh(true);
    } else if (e.target.closest('[data-back-dashboard]')) { state.page = '经营总览'; state.orgPath = state.orgPath.slice(0, 1); refresh(); }
    else if (e.target.closest('[data-export]')) exportCsv();
    else if (e.target.closest('[data-transaction]')) showModal(`交易 ${e.target.closest('[data-transaction]').dataset.transaction}`, '<p>状态：已记录（演示）</p><p>此记录来自本地确定性模拟数据。</p>');
    else if (e.target.closest('[data-customer]')) showModal(e.target.closest('[data-customer]').dataset.customer, '<p>该演示记录只包含本地生成的模拟信息。</p>');
  });

  document.addEventListener('keydown', e => {
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('[data-drill-branch], tr[data-drill], .chart .data-point')) {
      e.preventDefault(); e.target.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    }
  });

  function exportCsv() {
    const rows = [['交易编号', '分公司', '交易金额（万元）', '状态'], ...Array.from({ length: 8 }, (_, i) => [`TX-${String(i + 1).padStart(4, '0')}`, branches[i % branches.length].name, 34 + ((i * 83) % 210), i % 4 === 0 ? '待复核' : '已完成'])];
    const csv = '\ufeff' + rows.map(row => row.map(cell => `"${String(cell).replaceAll('"', '""')}"`).join(',')).join('\r\n');
    const link = document.createElement('a'); link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); link.download = '经营交易明细-演示数据.csv'; link.click(); URL.revokeObjectURL(link.href); toast('已导出当前演示表格 CSV');
  }

  function loadLocal() {
    try {
      const value = JSON.parse(localStorage.getItem('mateclaw-coworking-demo-v1') || 'null');
      if (value) { Object.assign(state, value); document.body.classList.toggle('theme-dark', state.dark); }
    } catch { /* A bad local draft never prevents the demo from opening. */ }
  }

  $('selectMode').onclick = () => { state.experience = !state.experience; $('selectMode').textContent = state.experience ? '▶ 体验模式' : '⌁ 选择模式'; toast(state.experience ? '体验模式：点击页面数据查看筛选和下钻反馈' : '选择模式：点击对象可查看属性'); };
  setupChat(); loadLocal(); render();
})();
