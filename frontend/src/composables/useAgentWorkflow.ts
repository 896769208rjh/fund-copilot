import { computed, onBeforeUnmount, reactive, ref, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fundApi } from '@/api/fundApi'
import { DEFAULT_AGENT_QUESTION } from '@/constants/workbench'
import {
  streamAnalysisTask,
  streamFundAnalysis,
  streamResumeAnalysisTask,
} from '@/composables/useAgentStream'
import type {
  AgentAnalysisResponse,
  AgentModelCall,
  AgentStep,
  AgentStreamEvent,
  AgentTaskStatus,
  AgentThinkingMode,
  FundAgentReportSection,
  FundAgentStage,
  FundAgentTask,
  FundAnalysisRequest,
  FundAnalysisResult,
} from '@/types'
import { isFundAgentReportSection, isFundAgentStage, isFundAgentTask } from '@/utils/agentGuards'
import { downloadTextFile } from '@/utils/download'
import { getErrorMessage } from '@/utils/fundFormatters'

interface AgentWorkflowLoadingState {
  analysis: boolean
  exportReport: boolean
  taskControl: boolean
  taskHistory: boolean
}

interface UseAgentWorkflowOptions {
  fundCode: Readonly<Ref<string>>
  onAnalysisUpdated: (analysis: FundAnalysisResult) => void
}

const RESUMABLE_TASK_STATUSES: readonly AgentTaskStatus[] = ['FAILED', 'CANCELLED', 'TIMEOUT']
const CANCELLABLE_TASK_STATUSES: readonly AgentTaskStatus[] = ['PENDING', 'RUNNING']

export function useAgentWorkflow(options: UseAgentWorkflowOptions) {
  const agentQuestion = ref(DEFAULT_AGENT_QUESTION)
  const agentThinkingMode = ref<AgentThinkingMode>('BALANCED')
  const agentEvents = ref<AgentStreamEvent[]>([])
  const agentAnswer = ref('')
  const agentGeneratedAt = ref('')
  const agentTask = ref<FundAgentTask | null>(null)
  const taskHistory = ref<FundAgentTask[]>([])
  const liveStages = ref<FundAgentStage[]>([])
  const liveSections = ref<FundAgentReportSection[]>([])
  const modelCalls = ref<AgentModelCall[]>([])
  const loading = reactive<AgentWorkflowLoadingState>({
    analysis: false,
    exportReport: false,
    taskControl: false,
    taskHistory: false,
  })

  let historyRequestVersion = 0
  let activeStreamController: AbortController | null = null

  const agentSteps = computed<AgentStep[]>(() => {
    const stages = liveStages.value.length > 0 ? liveStages.value : (agentTask.value?.stages ?? [])
    if (stages.length > 0) {
      return stages.map((stage) => ({
        name: stage.stageName,
        status: stage.status,
        detail: stage.summary || stage.errorMessage || '正在执行',
      }))
    }

    return agentEvents.value
      .filter((event) => event.type === 'AGENT_STEP')
      .map((event) => event.payload as AgentStep)
  })

  const progressMessages = computed(() =>
    agentEvents.value
      .filter((event) => event.type === 'PROGRESS')
      .map((event) => String(event.payload)),
  )

  const reportSections = computed(() =>
    liveSections.value.length > 0 ? liveSections.value : (agentTask.value?.sections ?? []),
  )

  const currentTaskLabel = computed(() => {
    if (agentTask.value === null) {
      return '等待任务'
    }
    const nextStage =
      agentTask.value.nextStageCode === null ? '' : ` · 待执行 ${agentTask.value.nextStageCode}`
    return `${agentTask.value.taskNo} · ${agentTask.value.status}${nextStage}`
  })

  const canResumeTask = computed(
    () => agentTask.value !== null && RESUMABLE_TASK_STATUSES.includes(agentTask.value.status),
  )

  const canCancelTask = computed(
    () => agentTask.value !== null && CANCELLABLE_TASK_STATUSES.includes(agentTask.value.status),
  )

  function beginStream(): AbortController {
    activeStreamController?.abort()
    activeStreamController = new AbortController()
    return activeStreamController
  }

  function isCurrentStream(controller: AbortController): boolean {
    return activeStreamController === controller && !controller.signal.aborted
  }

  function resetExecutionState(): void {
    agentEvents.value = []
    agentAnswer.value = ''
    agentGeneratedAt.value = ''
    liveStages.value = []
    liveSections.value = []
    modelCalls.value = []
  }

  function resetForFundChange(): void {
    activeStreamController?.abort()
    activeStreamController = null
    resetExecutionState()
    agentTask.value = null
    taskHistory.value = []
    loading.analysis = false
    loading.taskControl = false
  }

  async function loadTaskHistory(fundCode: string): Promise<void> {
    const requestVersion = ++historyRequestVersion
    resetForFundChange()

    if (fundCode.trim().length === 0) {
      return
    }

    loading.taskHistory = true
    try {
      const tasks = await fundApi.listAnalysisTasks(fundCode)
      if (requestVersion !== historyRequestVersion || options.fundCode.value !== fundCode) {
        return
      }

      taskHistory.value = tasks
      const latestTask = tasks[0] ?? null
      applyTaskSnapshot(latestTask, true)
      if (latestTask !== null) {
        await refreshModelCalls(latestTask.taskId)
      }
    } catch (error) {
      if (requestVersion === historyRequestVersion) {
        ElMessage.error(getErrorMessage(error, '历史任务加载失败'))
      }
    } finally {
      if (requestVersion === historyRequestVersion) {
        loading.taskHistory = false
      }
    }
  }

  async function runAgentAnalysis(): Promise<void> {
    const fundCode = options.fundCode.value.trim()
    if (fundCode.length === 0) {
      ElMessage.warning('请先选择基金')
      return
    }

    const requestBody: FundAnalysisRequest = {
      fundCode,
      question: agentQuestion.value.trim() || DEFAULT_AGENT_QUESTION,
      includeHistory: true,
      includeRiskNotice: true,
      thinkingMode: agentThinkingMode.value,
    }
    const controller = beginStream()

    loading.analysis = true
    resetExecutionState()
    agentTask.value = null

    try {
      await streamFundAnalysis(
        requestBody,
        (event) => {
          if (isCurrentStream(controller) && options.fundCode.value === fundCode) {
            handleAgentEvent(event)
          }
        },
        controller.signal,
      )
    } catch (error) {
      if (controller.signal.aborted) {
        return
      }

      ElMessage.warning(getErrorMessage(error, '流式分析失败，已切换为普通分析'))
      try {
        const response = await fundApi.analyzeWithAgent(requestBody)
        if (isCurrentStream(controller)) {
          applyAgentResponse(response)
        }
      } catch (fallbackError) {
        ElMessage.error(getErrorMessage(fallbackError, 'Agent 分析失败'))
      }
    } finally {
      if (isCurrentStream(controller)) {
        loading.analysis = false
        activeStreamController = null
        await refreshTaskHistory()
      }
    }
  }

  function handleAgentEvent(event: AgentStreamEvent): void {
    agentEvents.value.push(event)

    if (event.type === 'TASK_RERUN_STARTED') {
      resetExecutionState()
      if (isFundAgentTask(event.payload)) {
        applyTaskSnapshot(event.payload)
      }
      return
    }

    if (event.type === 'TASK_CREATED' && isFundAgentTask(event.payload)) {
      applyTaskSnapshot(event.payload)
      return
    }

    if (
      (event.type === 'STAGE_STARTED' || event.type === 'STAGE_DONE') &&
      isFundAgentStage(event.payload)
    ) {
      upsertStage(event.payload)
      return
    }

    if (event.type === 'SECTION' && isFundAgentReportSection(event.payload)) {
      upsertSection(event.payload)
      return
    }

    if (event.type === 'CARD') {
      options.onAnalysisUpdated(event.payload as FundAnalysisResult)
      return
    }

    if (event.type === 'TOKEN') {
      agentAnswer.value = String(event.payload ?? '')
      return
    }

    if (event.type === 'COMPLIANCE_BLOCKED') {
      ElMessage.warning(String(event.payload ?? '问题已触发合规改写'))
      return
    }

    if (event.type === 'ERROR') {
      ElMessage.error(String(event.payload ?? 'Agent 分析失败'))
      return
    }

    if (
      (event.type === 'TASK_CANCELLED' || event.type === 'TASK_TIMEOUT') &&
      isFundAgentTask(event.payload)
    ) {
      const previousStatus = agentTask.value?.status
      applyTaskSnapshot(event.payload)
      if (previousStatus !== event.payload.status) {
        ElMessage.warning(event.type === 'TASK_CANCELLED' ? '分析任务已取消' : '分析任务执行超时')
      }
      return
    }

    if (event.type === 'DONE') {
      if (isFundAgentTask(event.payload)) {
        applyTaskSnapshot(event.payload, true)
        void refreshModelCalls(event.payload.taskId)
        return
      }
      agentGeneratedAt.value = String(event.payload ?? '')
    }
  }

  function applyAgentResponse(response: AgentAnalysisResponse): void {
    agentAnswer.value = response.answer
    agentGeneratedAt.value = response.generatedAt
    if (response.analysis !== null) {
      options.onAnalysisUpdated(response.analysis)
    }
    agentEvents.value = [
      { type: 'PROGRESS', payload: '普通分析接口已返回' },
      ...response.steps.map((step) => ({ type: 'AGENT_STEP' as const, payload: step })),
      { type: 'CARD', payload: response.analysis },
      { type: 'TOKEN', payload: response.answer },
      { type: 'DONE', payload: response.generatedAt },
    ]
  }

  async function refreshTaskHistory(): Promise<void> {
    const fundCode = options.fundCode.value.trim()
    if (fundCode.length === 0) {
      return
    }

    try {
      taskHistory.value = await fundApi.listAnalysisTasks(fundCode)
    } catch (error) {
      ElMessage.warning(getErrorMessage(error, '历史任务刷新失败'))
    }
  }

  async function refreshModelCalls(taskId: number): Promise<void> {
    try {
      const calls = await fundApi.listAgentModelCalls(taskId)
      if (agentTask.value?.taskId === taskId) {
        modelCalls.value = calls
      }
    } catch {
      if (agentTask.value?.taskId === taskId) {
        modelCalls.value = []
      }
    }
  }

  async function replayTask(taskId: number): Promise<void> {
    const controller = beginStream()
    loading.analysis = true
    resetExecutionState()

    try {
      await streamAnalysisTask(
        taskId,
        (event) => {
          if (isCurrentStream(controller)) {
            handleAgentEvent(event)
          }
        },
        controller.signal,
      )
    } catch (error) {
      if (!controller.signal.aborted) {
        ElMessage.error(getErrorMessage(error, '历史任务回放失败'))
      }
    } finally {
      if (isCurrentStream(controller)) {
        loading.analysis = false
        activeStreamController = null
      }
    }
  }

  async function resumeCurrentTask(): Promise<void> {
    if (agentTask.value === null) {
      ElMessage.warning('请先选择一个历史任务')
      return
    }

    const taskId = agentTask.value.taskId
    const controller = beginStream()
    loading.analysis = true
    resetExecutionState()

    try {
      await streamResumeAnalysisTask(
        taskId,
        (event) => {
          if (isCurrentStream(controller)) {
            handleAgentEvent(event)
          }
        },
        controller.signal,
      )
    } catch (error) {
      if (controller.signal.aborted) {
        return
      }

      ElMessage.warning(getErrorMessage(error, '流式恢复失败，已切换为普通恢复'))
      try {
        const task = await fundApi.resumeAnalysisTask(taskId)
        if (isCurrentStream(controller)) {
          applyTaskSnapshot(task, true)
          await refreshModelCalls(task.taskId)
        }
      } catch (fallbackError) {
        ElMessage.error(getErrorMessage(fallbackError, '任务恢复失败'))
      }
    } finally {
      if (isCurrentStream(controller)) {
        loading.analysis = false
        activeStreamController = null
        await refreshTaskHistory()
      }
    }
  }

  async function cancelCurrentTask(): Promise<void> {
    if (agentTask.value === null || !canCancelTask.value) {
      return
    }

    loading.taskControl = true
    try {
      const task = await fundApi.cancelAnalysisTask(agentTask.value.taskId)
      applyTaskSnapshot(task)
      ElMessage.warning('已提交任务取消请求')
      await refreshTaskHistory()
    } catch (error) {
      ElMessage.error(getErrorMessage(error, '取消任务失败'))
    } finally {
      loading.taskControl = false
    }
  }

  async function rerunStage(stageCode: string): Promise<void> {
    if (agentTask.value === null) {
      return
    }

    try {
      await ElMessageBox.confirm(
        '该阶段及其后续报告会重新生成，原始事件仍保留用于审计。',
        '重新执行阶段',
        { confirmButtonText: '重新执行', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }

    const taskId = agentTask.value.taskId
    const controller = beginStream()
    loading.taskControl = true
    loading.analysis = true
    agentEvents.value = []
    modelCalls.value = []

    try {
      const task = await fundApi.rerunAnalysisStage(taskId, stageCode)
      if (!isCurrentStream(controller)) {
        return
      }

      applyTaskSnapshot(task)
      agentAnswer.value = ''
      agentGeneratedAt.value = ''
      await streamAnalysisTask(
        task.taskId,
        (event) => {
          if (isCurrentStream(controller)) {
            handleAgentEvent(event)
          }
        },
        controller.signal,
      )
    } catch (error) {
      if (!controller.signal.aborted) {
        ElMessage.error(getErrorMessage(error, '阶段重跑失败'))
      }
    } finally {
      if (isCurrentStream(controller)) {
        loading.taskControl = false
        loading.analysis = false
        activeStreamController = null
        await refreshTaskHistory()
      }
    }
  }

  async function downloadCurrentReport(): Promise<void> {
    if (agentTask.value === null) {
      ElMessage.warning('暂无可导出的分析任务')
      return
    }

    loading.exportReport = true
    try {
      const markdown = await fundApi.exportAnalysisReport(agentTask.value.taskId)
      downloadTextFile(markdown, `${agentTask.value.taskNo}.md`, 'text/markdown;charset=utf-8')
      ElMessage.success('分析报告已导出')
    } catch (error) {
      ElMessage.error(getErrorMessage(error, '报告导出失败'))
    } finally {
      loading.exportReport = false
    }
  }

  function applyTaskSnapshot(task: FundAgentTask | null, restoreOutput = false): void {
    agentTask.value = task
    if (task !== null) {
      agentThinkingMode.value = task.thinkingMode
    }
    liveStages.value = [...(task?.stages ?? [])]
    liveSections.value = [...(task?.sections ?? [])]

    if (restoreOutput) {
      agentAnswer.value = task?.finalAnswer ?? ''
      agentGeneratedAt.value = task?.completedAt ?? ''
      if (task?.analysis !== null && task?.analysis !== undefined) {
        options.onAnalysisUpdated(task.analysis)
      }
    }
  }

  function upsertStage(stage: FundAgentStage): void {
    const stageIndex = liveStages.value.findIndex((item) => item.stageCode === stage.stageCode)
    if (stageIndex >= 0) {
      liveStages.value.splice(stageIndex, 1, stage)
    } else {
      liveStages.value.push(stage)
    }
    liveStages.value.sort((left, right) => left.sortOrder - right.sortOrder)
  }

  function upsertSection(section: FundAgentReportSection): void {
    const sectionIndex = liveSections.value.findIndex((item) => item.id === section.id)
    if (sectionIndex >= 0) {
      liveSections.value.splice(sectionIndex, 1, section)
    } else {
      liveSections.value.push(section)
    }
    liveSections.value.sort((left, right) => left.sortOrder - right.sortOrder)
  }

  onBeforeUnmount(() => {
    activeStreamController?.abort()
  })

  return {
    agentAnswer,
    agentGeneratedAt,
    agentQuestion,
    agentSteps,
    agentTask,
    agentThinkingMode,
    canCancelTask,
    canResumeTask,
    currentTaskLabel,
    liveStages,
    loading,
    modelCalls,
    progressMessages,
    reportSections,
    taskHistory,
    cancelCurrentTask,
    downloadCurrentReport,
    loadTaskHistory,
    replayTask,
    rerunStage,
    resumeCurrentTask,
    runAgentAnalysis,
  }
}
