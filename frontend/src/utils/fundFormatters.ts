import type { AgentTaskStatus } from '@/types'
import { MAX_COMPARE_FUNDS } from '@/constants/workbench'

export type ElementTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export function formatElapsed(value: number | null | undefined): string {
  return value === null || value === undefined ? '--' : `${value} ms`
}

export function formatPercent(value: number | null | undefined): string {
  return value === null || value === undefined ? '--' : `${Number(value).toFixed(2)}%`
}

export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? '--' : Number(value).toFixed(4)
}

export function isSixDigitFundCode(value: string): boolean {
  return /^\d{6}$/.test(value)
}

export function parseFundCodes(value: string, limit = MAX_COMPARE_FUNDS): string[] {
  const codes = value
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter(isSixDigitFundCode)

  return Array.from(new Set(codes)).slice(0, limit)
}

export function riskTagType(riskLevel: string | null | undefined): ElementTagType {
  if (riskLevel?.includes('高')) {
    return 'danger'
  }
  if (riskLevel?.includes('中')) {
    return 'warning'
  }
  return 'info'
}

export function taskStatusTagType(status: AgentTaskStatus): ElementTagType {
  const statusTone: Readonly<Partial<Record<AgentTaskStatus, ElementTagType>>> = {
    SUCCESS: 'success',
    FAILED: 'danger',
    TIMEOUT: 'danger',
    CANCELLED: 'warning',
    RUNNING: 'primary',
  }

  return statusTone[status] ?? 'info'
}

export function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.length > 0 ? error.message : fallback
}
