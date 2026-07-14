import type { AgentStreamEvent, FundAnalysisRequest } from '@/types'

const STREAM_URL = '/api/agents/fund-analysis/stream'

export async function streamFundAnalysis(
  requestBody: FundAnalysisRequest,
  onEvent: (event: AgentStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  await requestEventStream(STREAM_URL, onEvent, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(requestBody),
    signal,
  })
}

export async function streamAnalysisTask(
  taskId: number,
  onEvent: (event: AgentStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  await requestEventStream(`/api/agents/fund-analysis/tasks/${taskId}/stream`, onEvent, {
    headers: {
      Accept: 'text/event-stream',
    },
    signal,
  })
}

export async function streamResumeAnalysisTask(
  taskId: number,
  onEvent: (event: AgentStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  await requestEventStream(`/api/agents/fund-analysis/tasks/${taskId}/resume/stream`, onEvent, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
    },
    signal,
  })
}

async function requestEventStream(
  url: string,
  onEvent: (event: AgentStreamEvent) => void,
  init?: RequestInit,
): Promise<void> {
  const response = await fetch(url, init)
  if (!response.ok || response.body === null) {
    throw new Error(`SSE 请求失败（HTTP ${response.status}）`)
  }

  await readEventStream(response, onEvent)
}

async function readEventStream(
  response: Response,
  onEvent: (event: AgentStreamEvent) => void,
): Promise<void> {
  if (response.body === null) {
    throw new Error('SSE response body is empty')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (value !== undefined) {
      buffer += decoder.decode(value, { stream: true })
    }

    if (done) {
      buffer += decoder.decode()
    }

    const chunks = buffer.split(/\r?\n\r?\n/)
    buffer = chunks.pop() ?? ''
    for (const chunk of chunks) {
      emitEvent(chunk, onEvent)
    }

    if (done) {
      break
    }
  }

  if (buffer.trim().length > 0) {
    emitEvent(buffer, onEvent)
  }
}

function emitEvent(chunk: string, onEvent: (event: AgentStreamEvent) => void): void {
  const event = parseAgentStreamChunk(chunk)
  if (event !== null) {
    onEvent(event)
  }
}

export function parseAgentStreamChunk(chunk: string): AgentStreamEvent | null {
  const dataLines = chunk
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())

  if (dataLines.length === 0) {
    return null
  }

  const data = dataLines.join('\n')
  return JSON.parse(data) as AgentStreamEvent
}
