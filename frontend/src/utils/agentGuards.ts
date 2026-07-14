import type { FundAgentReportSection, FundAgentStage, FundAgentTask } from '@/types'

function isRecord(payload: unknown): payload is Record<string, unknown> {
  return typeof payload === 'object' && payload !== null
}

export function isFundAgentTask(payload: unknown): payload is FundAgentTask {
  return (
    isRecord(payload) && typeof payload.taskId === 'number' && typeof payload.taskNo === 'string'
  )
}

export function isFundAgentStage(payload: unknown): payload is FundAgentStage {
  return (
    isRecord(payload) &&
    typeof payload.stageCode === 'string' &&
    typeof payload.stageName === 'string'
  )
}

export function isFundAgentReportSection(payload: unknown): payload is FundAgentReportSection {
  return (
    isRecord(payload) && typeof payload.title === 'string' && typeof payload.content === 'string'
  )
}
