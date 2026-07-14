import type { ApiResponse } from '@/types'

const API_BASE_PATH = '/api'

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function buildHeaders(init?: RequestInit): Headers {
  const headers = new Headers(init?.headers)
  if (init?.body !== null && init?.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return headers
}

async function parseJsonResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const responseText = await response.text()
  if (responseText.length === 0) {
    throw new ApiError('服务端返回了空响应', response.status)
  }

  try {
    return JSON.parse(responseText) as ApiResponse<T>
  } catch {
    throw new ApiError('服务端返回了无效的 JSON 响应', response.status)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_PATH}${path}`, {
    ...init,
    headers: buildHeaders(init),
  })
  const body = await parseJsonResponse<T>(response)

  if (!response.ok || !body.success) {
    throw new ApiError(body.message || `请求失败（HTTP ${response.status}）`, response.status)
  }

  return body.data
}

async function requestText(path: string, init?: RequestInit): Promise<string> {
  const response = await fetch(`${API_BASE_PATH}${path}`, {
    ...init,
    headers: buildHeaders(init),
  })

  if (!response.ok) {
    throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status)
  }

  return response.text()
}

export const httpClient = {
  request,
  requestText,
}
