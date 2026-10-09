/** Bounded snapshot history for dashboard editor state. */
export function resolveDashboardHistoryShortcut(
  event: { key: string; ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean; altKey?: boolean; isComposing?: boolean },
  protectedFocusContext = false,
): 'undo' | 'redo' | null {
  if (protectedFocusContext || event.isComposing || event.altKey || (!event.ctrlKey && !event.metaKey)) return null
  const key = event.key.toLowerCase()
  if (key === 'z') return event.shiftKey ? 'redo' : 'undo'
  if (key === 'y' && event.ctrlKey && !event.metaKey) return 'redo'
  return null
}

export function createDashboardEditHistory<T>(maxEntries = 100) {
  const capacity = Math.max(1, Math.floor(maxEntries))
  let entries: string[] = []
  let cursor = -1

  const clone = (snapshot: string): T => JSON.parse(snapshot) as T

  function reset(state: T): void {
    entries = [JSON.stringify(state)]
    cursor = 0
  }

  function push(state: T): boolean {
    const serialized = JSON.stringify(state)
    if (cursor >= 0 && entries[cursor] === serialized) return false

    entries = entries.slice(0, cursor + 1)
    entries.push(serialized)
    cursor = entries.length - 1
    if (entries.length > capacity) {
      entries.shift()
      cursor -= 1
    }
    return true
  }

  function undo(): T | null {
    if (cursor <= 0) return null
    cursor -= 1
    return clone(entries[cursor])
  }

  function redo(): T | null {
    if (cursor < 0 || cursor >= entries.length - 1) return null
    cursor += 1
    return clone(entries[cursor])
  }

  return {
    reset,
    push,
    undo,
    redo,
    get canUndo(): boolean { return cursor > 0 },
    get canRedo(): boolean { return cursor >= 0 && cursor < entries.length - 1 },
  }
}
