import type {
  AgentAnalysisResponse,
  ApiResponse,
  FundAnalysisRequest,
  FundAnalysisResult,
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
}
