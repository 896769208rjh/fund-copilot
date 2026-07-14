import type {
  AgentAnalysisResponse,
  AgentModelCall,
  FundAnalysisRequest,
  FundAnalysisResult,
  FundAgentTask,
  FundCompareResult,
  FundDetail,
  FundNavPoint,
  FundSearchItem,
} from '@/types'
import { httpClient } from './httpClient'

function fundResource(fundCode: string): string {
  return `/funds/${encodeURIComponent(fundCode)}`
}

export const fundApi = {
  search(keyword: string): Promise<FundSearchItem[]> {
    return httpClient.request<FundSearchItem[]>(
      `/funds/search?keyword=${encodeURIComponent(keyword)}`,
    )
  },

  detail(fundCode: string): Promise<FundDetail> {
    return httpClient.request<FundDetail>(fundResource(fundCode))
  },

  nav(fundCode: string, limit = 120): Promise<FundNavPoint[]> {
    return httpClient.request<FundNavPoint[]>(`${fundResource(fundCode)}/nav?limit=${limit}`)
  },

  analysis(fundCode: string): Promise<FundAnalysisResult> {
    return httpClient.request<FundAnalysisResult>(`${fundResource(fundCode)}/analysis`)
  },

  compare(fundCodes: readonly string[]): Promise<FundCompareResult> {
    return httpClient.request<FundCompareResult>(
      `/funds/compare?codes=${encodeURIComponent(fundCodes.join(','))}`,
    )
  },

  sync(fundCode: string): Promise<FundDetail> {
    return httpClient.request<FundDetail>(`${fundResource(fundCode)}/sync`, {
      method: 'POST',
    })
  },

  alipayFundPool(): Promise<FundSearchItem[]> {
    return httpClient.request<FundSearchItem[]>('/alipay/fund-pool')
  },

  analyzeWithAgent(requestBody: FundAnalysisRequest): Promise<AgentAnalysisResponse> {
    return httpClient.request<AgentAnalysisResponse>('/agents/fund-analysis', {
      method: 'POST',
      body: JSON.stringify(requestBody),
    })
  },

  createAnalysisTask(requestBody: FundAnalysisRequest): Promise<FundAgentTask> {
    return httpClient.request<FundAgentTask>('/agents/fund-analysis/tasks', {
      method: 'POST',
      body: JSON.stringify(requestBody),
    })
  },

  getAnalysisTask(taskId: number): Promise<FundAgentTask> {
    return httpClient.request<FundAgentTask>(`/agents/fund-analysis/tasks/${taskId}`)
  },

  listAgentModelCalls(taskId: number): Promise<AgentModelCall[]> {
    return httpClient.request<AgentModelCall[]>(`/agents/fund-analysis/tasks/${taskId}/model-calls`)
  },

  resumeAnalysisTask(taskId: number): Promise<FundAgentTask> {
    return httpClient.request<FundAgentTask>(`/agents/fund-analysis/tasks/${taskId}/resume`, {
      method: 'POST',
    })
  },

  cancelAnalysisTask(taskId: number): Promise<FundAgentTask> {
    return httpClient.request<FundAgentTask>(`/agents/fund-analysis/tasks/${taskId}/cancel`, {
      method: 'POST',
    })
  },

  rerunAnalysisStage(taskId: number, stageCode: string): Promise<FundAgentTask> {
    return httpClient.request<FundAgentTask>(
      `/agents/fund-analysis/tasks/${taskId}/stages/${encodeURIComponent(stageCode)}/rerun`,
      { method: 'POST' },
    )
  },

  exportAnalysisReport(taskId: number): Promise<string> {
    return httpClient.requestText(`/agents/fund-analysis/tasks/${taskId}/report`, {
      headers: {
        Accept: 'text/markdown',
      },
    })
  },

  listAnalysisTasks(fundCode?: string): Promise<FundAgentTask[]> {
    const query =
      fundCode === undefined || fundCode.length === 0
        ? ''
        : `?fundCode=${encodeURIComponent(fundCode)}`
    return httpClient.request<FundAgentTask[]>(`/agents/fund-analysis/tasks${query}`)
  },
} as const
