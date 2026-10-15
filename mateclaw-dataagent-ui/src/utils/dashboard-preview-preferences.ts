const PREVIEW_FILL_WIDTH_KEY_PREFIX = 'mateclaw:insight-preview-fill-width:'

function storageKey(dashboardId: string): string {
  return `${PREVIEW_FILL_WIDTH_KEY_PREFIX}${dashboardId}`
}

export function readDashboardPreviewFillWidth(dashboardId: string): boolean {
  if (!dashboardId || typeof localStorage === 'undefined') return false
  try {
    return localStorage.getItem(storageKey(dashboardId)) === 'true'
  } catch {
    return false
  }
}

export function saveDashboardPreviewFillWidth(dashboardId: string, enabled: boolean): void {
  if (!dashboardId || typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(storageKey(dashboardId), String(enabled))
  } catch {
    // Storage can be unavailable in restricted browser contexts; keep the in-memory toggle usable.
  }
}
