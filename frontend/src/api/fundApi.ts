import type {
  AgentAnalysisResponse,
  AgentModelCall,
  ApiResponse,
  FundAnalysisRequest,
  FundAnalysisResult,
  FundAgentTask,
  FundCompareResult,
  FundDetail,
  FundNavPoint,
  FundSearchItem,
} from '../types/fund'

const API_PREFIX = '/api'

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_PREFIX}${url}`, {
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    ...init,
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const body = (await response.json()) as ApiResponse<T>
  if (!body.success) {
    throw new Error(body.message || 'Request failed')
  }

  return body.data
}

export const fundApi = {
  search(keyword: string): Promise<FundSearchItem[]> {
    return request<FundSearchItem[]>(`/funds/search?keyword=${encodeURIComponent(keyword)}`)
  },

  detail(fundCode: string): Promise<FundDetail> {
    return request<FundDetail>(`/funds/${fundCode}`)
  },

  nav(fundCode: string, limit = 120): Promise<FundNavPoint[]> {
    return request<FundNavPoint[]>(`/funds/${fundCode}/nav?limit=${limit}`)
  },

  analysis(fundCode: string): Promise<FundAnalysisResult> {
    return request<FundAnalysisResult>(`/funds/${fundCode}/analysis`)
  },

  compare(fundCodes: string[]): Promise<FundCompareResult> {
    return request<FundCompareResult>(`/funds/compare?codes=${encodeURIComponent(fundCodes.join(','))}`)
  },

  sync(fundCode: string): Promise<FundDetail> {
    return request<FundDetail>(`/funds/${fundCode}/sync`, {
      method: 'POST',
    })
  },

  alipayFundPool(): Promise<FundSearchItem[]> {
    return request<FundSearchItem[]>('/alipay/fund-pool')
  },

  analyzeWithAgent(requestBody: FundAnalysisRequest): Promise<AgentAnalysisResponse> {
    return request<AgentAnalysisResponse>('/agents/fund-analysis', {
      method: 'POST',
      body: JSON.stringify(requestBody),
    })
  },

  createAnalysisTask(requestBody: FundAnalysisRequest): Promise<FundAgentTask> {
    return request<FundAgentTask>('/agents/fund-analysis/tasks', {
      method: 'POST',
      body: JSON.stringify(requestBody),
    })
  },

  getAnalysisTask(taskId: number): Promise<FundAgentTask> {
    return request<FundAgentTask>(`/agents/fund-analysis/tasks/${taskId}`)
  },

  listAgentModelCalls(taskId: number): Promise<AgentModelCall[]> {
    return request<AgentModelCall[]>(`/agents/fund-analysis/tasks/${taskId}/model-calls`)
  },

  resumeAnalysisTask(taskId: number): Promise<FundAgentTask> {
    return request<FundAgentTask>(`/agents/fund-analysis/tasks/${taskId}/resume`, {
      method: 'POST',
    })
  },

  cancelAnalysisTask(taskId: number): Promise<FundAgentTask> {
    return request<FundAgentTask>(`/agents/fund-analysis/tasks/${taskId}/cancel`, {
      method: 'POST',
    })
  },

  rerunAnalysisStage(taskId: number, stageCode: string): Promise<FundAgentTask> {
    return request<FundAgentTask>(`/agents/fund-analysis/tasks/${taskId}/stages/${stageCode}/rerun`, {
      method: 'POST',
    })
  },

  async exportAnalysisReport(taskId: number): Promise<string> {
    const response = await fetch(`${API_PREFIX}/agents/fund-analysis/tasks/${taskId}/report`, {
      headers: {
        Accept: 'text/markdown',
      },
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    return response.text()
  },

  listAnalysisTasks(fundCode?: string): Promise<FundAgentTask[]> {
    const query = fundCode === undefined || fundCode.length === 0
      ? ''
      : `?fundCode=${encodeURIComponent(fundCode)}`
    return request<FundAgentTask[]>(`/agents/fund-analysis/tasks${query}`)
  },
}
