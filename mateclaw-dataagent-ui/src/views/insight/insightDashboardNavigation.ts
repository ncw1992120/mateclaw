import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'

export function insightDashboardListLocation(query: LocationQueryRaw = {}): RouteLocationRaw {
  return {
    path: '/',
    query: { ...query, nav: 'insight' },
  }
}
