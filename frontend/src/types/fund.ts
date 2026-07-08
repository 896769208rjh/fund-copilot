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

export interface AgentStep {
  name: string
  status: string
  detail: string
}

export interface FundAnalysisRequest {
  fundCode: string
  question: string
  includeHistory: boolean
  includeRiskNotice: boolean
}

export interface AgentAnalysisResponse {
  agentName: string
  fundCode: string
  answer: string
  analysis: FundAnalysisResult
  steps: AgentStep[]
  disclaimer: string
  generatedAt: string
}

export type AgentStreamEventType = 'PROGRESS' | 'AGENT_STEP' | 'CARD' | 'TOKEN' | 'DONE'

export interface AgentStreamEvent<T = unknown> {
  type: AgentStreamEventType
  payload: T
}
