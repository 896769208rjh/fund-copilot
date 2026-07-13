import type { AgentStreamEvent, FundAnalysisRequest } from '../types/fund'

const STREAM_URL = '/api/agents/fund-analysis/stream'

export async function streamFundAnalysis(
  requestBody: FundAnalysisRequest,
  onEvent: (event: AgentStreamEvent) => void,
): Promise<void> {
  const response = await fetch(STREAM_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(requestBody),
  })

  if (!response.ok || response.body === null) {
    throw new Error(`SSE request failed: ${response.status}`)
  }

  await readEventStream(response, onEvent)
}

export async function streamAnalysisTask(
  taskId: number,
  onEvent: (event: AgentStreamEvent) => void,
): Promise<void> {
  const response = await fetch(`/api/agents/fund-analysis/tasks/${taskId}/stream`, {
    headers: {
      Accept: 'text/event-stream',
    },
  })

  if (!response.ok || response.body === null) {
    throw new Error(`SSE request failed: ${response.status}`)
  }

  await readEventStream(response, onEvent)
}

export async function streamResumeAnalysisTask(
  taskId: number,
  onEvent: (event: AgentStreamEvent) => void,
): Promise<void> {
  const response = await fetch(`/api/agents/fund-analysis/tasks/${taskId}/resume/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
    },
  })

  if (!response.ok || response.body === null) {
    throw new Error(`SSE request failed: ${response.status}`)
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
    buffer += decoder.decode(value, { stream: !done })

    const chunks = buffer.split(/\r?\n\r?\n/)
    buffer = chunks.pop() ?? ''
    chunks.forEach((chunk) => emitEvent(chunk, onEvent))

    if (done) {
      break
    }
  }

  if (buffer.trim().length > 0) {
    emitEvent(buffer, onEvent)
  }
}

function emitEvent(chunk: string, onEvent: (event: AgentStreamEvent) => void): void {
  const dataLines = chunk
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())

  if (dataLines.length === 0) {
    return
  }

  const data = dataLines.join('\n')
  onEvent(JSON.parse(data) as AgentStreamEvent)
}
