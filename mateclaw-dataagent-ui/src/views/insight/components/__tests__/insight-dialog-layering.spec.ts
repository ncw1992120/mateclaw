import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const insightRoot = resolve(process.cwd(), 'src/views/insight')

function collectVueFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory()
      ? collectVueFiles(path)
      : entry.isFile() && entry.name.endsWith('.vue') ? [path] : []
  })
}

describe('洞察弹窗层级', () => {
  it('所有洞察对话框和抽屉都传送到 body，避免被画布层叠上下文覆盖', () => {
    const violations = collectVueFiles(insightRoot).flatMap((path) => {
      const source = readFileSync(path, 'utf8')
      const overlays = [...source.matchAll(/<el-(?:dialog|drawer)\b[^>]*>/g)]
      return overlays.flatMap(([tag], index) => (
        /\bappend-to-body(?:\s|=|>)/.test(tag)
          ? []
          : [`${path} 中第 ${index + 1} 个弹窗/抽屉未启用 append-to-body`]
      ))
    })

    expect(violations).toEqual([])
  })
})
