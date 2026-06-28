import api from './index'

const BASE_URL = '/dataagent/api/v1/knowledge'

/** 知识库信息 */
export interface KnowledgeBase {
  id: string
  name: string
  description: string
  agentId: string | null
  configContent: string | null
  sourceDirectory: string | null
  status: string
  pageCount: number
  rawCount: number
  workspaceId: string
  embeddingModelId: string | null
  createTime: string
  updateTime: string
  deleted: number
}

/** 原始材料信息 */
export interface RawMaterial {
  id: string
  kbId: string
  title: string
  sourceType: string
  mimeType: string | null
  sourcePath: string | null
  originalContent: string | null
  extractedText: string | null
  contentHash: string | null
  fileSize: number | null
  processingStatus: string
  cancelRequested: boolean
  lastProcessedAt: string | null
  lastProcessedHash: string | null
  errorMessage: string | null
  progressPhase: string | null
  progressTotal: number | null
  progressDone: number | null
  pageCount: number | null
  createTime: string
  updateTime: string
  deleted: number
}

/** Wiki 页面信息 */
export interface WikiPage {
  id: string
  kbId: string
  slug: string
  title: string
  content: string | null
  summary: string | null
  outgoingLinks: string | null
  sourceRawIds: string | null
  pageType: string | null
  purposeHint: string | null
  version: number
  lastUpdatedBy: string | null
  locked: number
  archived: number
  embeddingModel: string | null
  embeddingTextVersion: string | null
  createTime: string
  updateTime: string
  deleted: number
}

/** 处理状态 */
export interface ProcessingStatus {
  status: string
  pending: number
  processing: number
  completed: number
  failed: number
  totalRaw: number
  totalPages: number
}

/** 处理任务 */
export interface WikiJob {
  id: string
  kbId: string
  rawId: string
  jobType: string
  stage: string
  status: string
  primaryModelId: string | null
  currentModelId: string | null
  currentModelName: string | null
  fallbackChainJson: string | null
  retryCount: number
  maxRetries: number
  errorCode: string | null
  errorMessage: string | null
  resumeFromStage: string | null
  metaJson: string | null
  startedAt: string | null
  finishedAt: string | null
  createTime: string
  updateTime: string
}

/** 扫描结果 */
export interface ScanResult {
  scanned: number
  added: number
  skipped: number
  errors: string[]
}

/** ==================== Knowledge Base CRUD ==================== */

export function listKBs(workspaceId?: number) {
  return api.get<KnowledgeBase[]>(`${BASE_URL}/knowledge-bases`, {
    params: { workspaceId: workspaceId || 1 },
  })
}

export function listKBsByAgent(agentId: string, workspaceId?: number) {
  return api.get<KnowledgeBase[]>(`${BASE_URL}/knowledge-bases/agent/${agentId}`, {
    params: { workspaceId: workspaceId || 1 },
  })
}

export function getKB(id: string) {
  return api.get<KnowledgeBase>(`${BASE_URL}/knowledge-bases/${id}`)
}

export function createKB(data: { name: string; description?: string; agentId?: string; workspaceId?: number }) {
  return api.post<KnowledgeBase>(`${BASE_URL}/knowledge-bases`, data)
}

export function updateKB(id: string, data: Record<string, unknown>) {
  return api.put<KnowledgeBase>(`${BASE_URL}/knowledge-bases/${id}`, data)
}

export function deleteKB(id: string) {
  return api.delete(`${BASE_URL}/knowledge-bases/${id}`)
}

export function getConfig(id: string) {
  return api.get<{ content: string }>(`${BASE_URL}/knowledge-bases/${id}/config`)
}

export function updateConfig(id: string, content: string) {
  return api.put(`${BASE_URL}/knowledge-bases/${id}/config`, { content })
}

/** ==================== Directory Scan ==================== */

export function setSourceDirectory(id: string, path: string) {
  return api.put(`${BASE_URL}/knowledge-bases/${id}/source-directory`, { path })
}

export function scanDirectory(id: string) {
  return api.post<ScanResult>(`${BASE_URL}/knowledge-bases/${id}/scan`)
}

/** ==================== Raw Materials ==================== */

export function listRaw(kbId: string) {
  return api.get<RawMaterial[]>(`${BASE_URL}/knowledge-bases/${kbId}/raw`)
}

export function addRawText(kbId: string, title: string, content: string) {
  return api.post<RawMaterial>(`${BASE_URL}/knowledge-bases/${kbId}/raw/text`, { title, content })
}

export function uploadRaw(kbId: string, file: File, onProgress?: (pct: number) => void) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post<RawMaterial>(`${BASE_URL}/knowledge-bases/${kbId}/raw/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
      ? (progressEvent) => {
          if (progressEvent.total) {
            onProgress(Math.round((progressEvent.loaded * 100) / progressEvent.total))
          }
        }
      : undefined,
  })
}

export function deleteRaw(kbId: string, rawId: string) {
  return api.delete(`${BASE_URL}/knowledge-bases/${kbId}/raw/${rawId}`)
}

export function reprocessRaw(kbId: string, rawId: string, force = false) {
  return api.post(`${BASE_URL}/knowledge-bases/${kbId}/raw/${rawId}/reprocess`, null, { params: { force } })
}

export function cancelRaw(kbId: string, rawId: string) {
  return api.post(`${BASE_URL}/knowledge-bases/${kbId}/raw/${rawId}/cancel`)
}

export function downloadRaw(kbId: string, rawId: string) {
  return api.get<Blob>(`${BASE_URL}/knowledge-bases/${kbId}/raw/${rawId}/download`, {
    responseType: 'blob',
  })
}

/** ==================== Processing ==================== */

export function processKB(kbId: string, force = false) {
  return api.post<{ queued: number; force: boolean }>(`${BASE_URL}/knowledge-bases/${kbId}/process`, null, { params: { force } })
}

export function getProcessingStatus(kbId: string) {
  return api.get<ProcessingStatus>(`${BASE_URL}/knowledge-bases/${kbId}/processing-status`)
}

/** SSE 进度订阅 — 返回原生 EventSource，由调用方管理生命周期 */
export function subscribeProgress(kbId: string): EventSource {
  // EventSource 不支持自定义 Header，通过 query param 传递 token（JwtAuthFilter 支持 ?token=xxx）
  const token = localStorage.getItem('token')
  const tokenParam = token ? `?token=${encodeURIComponent(token)}` : ''
  return new EventSource(`${BASE_URL}/knowledge-bases/${kbId}/progress${tokenParam}`)
}

/** ==================== Pages ==================== */

export function listPages(kbId: string, rawId?: string) {
  return api.get<WikiPage[]>(`${BASE_URL}/knowledge-bases/${kbId}/pages`, {
    params: rawId ? { rawId } : {},
  })
}

export function getPage(kbId: string, slug: string) {
  return api.get<WikiPage>(`${BASE_URL}/knowledge-bases/${kbId}/pages/${slug}`)
}

export function updatePage(kbId: string, slug: string, content: string, summary?: string) {
  return api.put<WikiPage>(`${BASE_URL}/knowledge-bases/${kbId}/pages/${slug}`, { content, summary })
}

export function deletePage(kbId: string, slug: string) {
  return api.delete(`${BASE_URL}/knowledge-bases/${kbId}/pages/${slug}`)
}

export function batchDeletePages(kbId: string, slugs: string[]) {
  return api.delete<number>(`${BASE_URL}/knowledge-bases/${kbId}/pages/batch`, { data: slugs })
}

export function getBacklinks(kbId: string, slug: string) {
  return api.get<WikiPage[]>(`${BASE_URL}/knowledge-bases/${kbId}/pages/${slug}/backlinks`)
}

export function listArchivedPages(kbId: string) {
  return api.get<WikiPage[]>(`${BASE_URL}/knowledge-bases/${kbId}/pages/archived`)
}

export function archivePage(kbId: string, slug: string) {
  return api.post<{ slug: string; archived: boolean; changed: boolean }>(
    `${BASE_URL}/knowledge-bases/${kbId}/pages/${slug}/archive`
  )
}

export function unarchivePage(kbId: string, slug: string) {
  return api.post<{ slug: string; archived: boolean; changed: boolean }>(
    `${BASE_URL}/knowledge-bases/${kbId}/pages/${slug}/unarchive`
  )
}

/** ==================== Jobs ==================== */

export function getWikiJobs(kbId: string, rawId?: string) {
  return api.get<WikiJob[]>(`${BASE_URL}/knowledge-bases/${kbId}/jobs`, {
    params: rawId ? { rawId } : {},
  })
}

/** ==================== Transformations ==================== */

export interface WikiTransformation {
  id: string
  kbId: string | null
  workspaceId: string
  name: string
  title: string | null
  description: string | null
  promptTemplate: string | null
  applyDefault: number
  enabled: number
  modelId: string | null
  outputTarget: string | null
  outputFormat: string | null
  outputSchema: string | null
  scope: string | null
  createTime: string
  updateTime: string
}

export interface WikiTransformationRun {
  id: string
  transformationId: string
  kbId: string
  rawId: string | null
  pageId: string | null
  status: string
  output: string | null
  error: string | null
  durationMs: number | null
  tokens: number | null
  outputPageId: string | null
  createTime: string
  updateTime: string
}

export function listTransformations(kbId: string) {
  return api.get<WikiTransformation[]>(`${BASE_URL}/knowledge-bases/${kbId}/transformations`)
}

export function createTransformation(kbId: string, data: Record<string, unknown>) {
  return api.post<WikiTransformation>(`${BASE_URL}/knowledge-bases/${kbId}/transformations`, data)
}

export function updateTransformation(id: string, data: Record<string, unknown>) {
  return api.put<WikiTransformation>(`${BASE_URL}/transformations/${id}`, data)
}

export function deleteTransformation(id: string) {
  return api.delete(`${BASE_URL}/transformations/${id}`)
}

export function applyTransformation(id: string, body: { rawId?: string; pageId?: string }, sync = false) {
  return api.post<WikiTransformationRun>(`${BASE_URL}/transformations/${id}/apply`, body, { params: { sync } })
}

export function aggregateTransformation(id: string, kbId: string) {
  return api.post<Record<string, unknown>>(`${BASE_URL}/transformations/${id}/aggregate`, null, { params: { kbId } })
}

export function listTransformationRuns(id: string) {
  return api.get<WikiTransformationRun[]>(`${BASE_URL}/transformations/${id}/runs`)
}

export function cancelTransformationRun(runId: string) {
  return api.post(`${BASE_URL}/transformation-runs/${runId}/cancel`)
}

export function saveRunAsPage(runId: string) {
  return api.post<Record<string, unknown>>(`${BASE_URL}/transformation-runs/${runId}/save-as-page`)
}

export function deleteTransformationRun(runId: string) {
  return api.delete(`${BASE_URL}/transformation-runs/${runId}`)
}

/** ==================== Hot Cache ==================== */

export interface WikiHotCache {
  id: string
  kbId: string
  content: string | null
  lastUpdated: string | null
  updateReason: string | null
  rebuildCount: number
  lastRebuildDurationMs: number | null
  lastRebuildError: string | null
  createTime: string
  updateTime: string
}

export function getHotCache(kbId: string) {
  return api.get<WikiHotCache>(`${BASE_URL}/knowledge-bases/${kbId}/hot-cache`)
}

export function regenerateHotCache(kbId: string) {
  return api.post(`${BASE_URL}/knowledge-bases/${kbId}/hot-cache/regenerate`)
}

export function resetHotCache(kbId: string) {
  return api.delete(`${BASE_URL}/knowledge-bases/${kbId}/hot-cache`)
}

/** ==================== Relations / Stats / Search ==================== */

export interface RelatedPageResult {
  slug: string
  title: string
  score: number
}

export interface PageSearchResult {
  slug: string
  title: string
  score: number
  snippet: string
}

export function getRelatedPages(kbId: string, slug: string, topK = 5) {
  return api.get<RelatedPageResult[]>(`${BASE_URL}/knowledge-bases/${kbId}/pages/${slug}/related`, { params: { topK } })
}

export function getPageCitations(kbId: string, pageId: string) {
  return api.get<Record<string, unknown>[]>(`${BASE_URL}/knowledge-bases/${kbId}/pages/${pageId}/citations`)
}

export function enrichPage(kbId: string, slug: string) {
  return api.post<Record<string, unknown>>(`${BASE_URL}/knowledge-bases/${kbId}/pages/${slug}/enrich`)
}

export function repairPage(kbId: string, slug: string) {
  return api.post<Record<string, unknown>>(`${BASE_URL}/knowledge-bases/${kbId}/pages/${slug}/repair`)
}

export function searchPreview(kbId: string, query: string, mode = 'hybrid', topK = 5) {
  return api.post<PageSearchResult[]>(`${BASE_URL}/knowledge-bases/${kbId}/search-preview`, { query, mode, topK })
}

export function getKBStats(kbId: string) {
  return api.get<Record<string, unknown>>(`${BASE_URL}/knowledge-bases/${kbId}/stats`)
}
