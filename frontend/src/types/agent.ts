import type { FundAnalysisResult } from './fund'

export type AgentThinkingMode = 'FAST' | 'BALANCED' | 'DEEP'

export type AgentTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'TIMEOUT'

export type AgentStageStatus = AgentTaskStatus | 'SKIPPED'

export type AgentModelCallStatus = 'SUCCESS' | 'FALLBACK' | 'FAILED'

export interface AgentStep {
  name: string
  status: AgentStageStatus
  detail: string
}

export interface FundAnalysisRequest {
  fundCode: string
  question: string
  includeHistory: boolean
  includeRiskNotice: boolean
  thinkingMode: AgentThinkingMode
}

export interface AgentAnalysisResponse {
  agentName: string
  fundCode: string
  answer: string
  analysis: FundAnalysisResult | null
  steps: AgentStep[]
  disclaimer: string
  generatedAt: string
}

export interface FundAgentStage {
  id: number
  stageCode: string
  stageName: string
  status: AgentStageStatus
  summary: string | null
  sortOrder: number
  startedAt: string | null
  completedAt: string | null
  elapsedMs: number | null
  errorMessage: string | null
  stageInput: string | null
  stageOutput: string | null
}

export interface FundAgentReportSection {
  id: number
  stageCode: string
  sectionType: string
  title: string
  content: string
  sortOrder: number
  createdAt: string | null
}

export interface FundAgentTask {
  taskId: number
  taskNo: string
  fundCode: string
  question: string
  thinkingMode: AgentThinkingMode
  status: AgentTaskStatus
  restricted: boolean
  finalAnswer: string | null
  disclaimer: string | null
  errorMessage: string | null
  nextStageCode: string | null
  retryCount: number | null
  deadlineAt: string | null
  reportMarkdown: string | null
  analysis: FundAnalysisResult | null
  stages: FundAgentStage[]
  sections: FundAgentReportSection[]
  startedAt: string | null
  completedAt: string | null
  elapsedMs: number | null
  createdAt: string | null
}

export interface AgentModelCall {
  id: number
  taskId: number
  stageCode: string
  agentName: string
  modelName: string
  thinkingMode: AgentThinkingMode
  promptVersion: string
  outputSchema: string
  attemptNo: number
  status: AgentModelCallStatus
  inputTokens: number | null
  outputTokens: number | null
  inputChars: number
  outputChars: number
  elapsedMs: number
  fallbackReason: string | null
  errorMessage: string | null
  createdAt: string
}

export type AgentStreamEventType =
  | 'PROGRESS'
  | 'AGENT_STEP'
  | 'CARD'
  | 'TOKEN'
  | 'DONE'
  | 'TASK_CREATED'
  | 'STAGE_STARTED'
  | 'STAGE_DONE'
  | 'SECTION'
  | 'COMPLIANCE_BLOCKED'
  | 'TASK_CANCELLED'
  | 'TASK_TIMEOUT'
  | 'TASK_RERUN_STARTED'
  | 'ERROR'

export interface AgentStreamEvent<T = unknown> {
  type: AgentStreamEventType
  payload: T
}
