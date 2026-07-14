export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
}

export interface FundSearchItem {
  fundCode: string
  fundName: string
  fundType: string | null
  riskLevel: string | null
  displayTag: string | null
}

export interface FundDetail {
  fundCode: string
  fundName: string
  fundType: string | null
  fundCompany: string | null
  fundManager: string | null
  riskLevel: string | null
  purchaseStatus: string | null
  redeemStatus: string | null
  latestNav: number | null
  latestNavDate: string | null
  sourceUrl: string | null
  stale: boolean | null
  lastSyncAt: string | null
}

export interface FundNavPoint {
  navDate: string
  unitNav: number | null
  accumulatedNav: number | null
  dailyGrowthRate: number | null
}

export interface FundMetric {
  oneMonthReturn: number | null
  threeMonthReturn: number | null
  sixMonthReturn: number | null
  oneYearReturn: number | null
  maxDrawdown: number | null
  volatility: number | null
  statisticDate: string | null
}

export interface FundAnalysisResult {
  detail: FundDetail
  metrics: FundMetric
  navPoints: FundNavPoint[]
  highlights: string[]
  risks: string[]
  riskNotice: string
  dataSource: string
  generatedAt: string
}

export interface FundCompareColumn {
  fundCode: string
  fundName: string
  fundType: string | null
  riskLevel: string | null
}

export interface FundCompareRow {
  dimension: string
  values: string[]
}

export interface FundCompareResult {
  columns: FundCompareColumn[]
  rows: FundCompareRow[]
  summary: string
  generatedAt: string
}

export interface AgentStep {
  name: string
  status: string
  detail: string
}

export type AgentThinkingMode = 'FAST' | 'BALANCED' | 'DEEP'

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
  status: string
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
  status: string
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
  status: string
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
