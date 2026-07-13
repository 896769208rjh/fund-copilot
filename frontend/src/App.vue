<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatLineRound,
  CircleClose,
  Cpu,
  DataLine,
  Download,
  Refresh,
  Search,
  TrendCharts,
  WarningFilled,
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { fundApi } from './api/fundApi'
import AgentReportSections from './components/agent/AgentReportSections.vue'
import AgentStageAudit from './components/agent/AgentStageAudit.vue'
import AgentTimeline from './components/agent/AgentTimeline.vue'
import { streamAnalysisTask, streamFundAnalysis, streamResumeAnalysisTask } from './composables/useAgentStream'
import type {
  AgentAnalysisResponse,
  AgentStep,
  AgentStreamEvent,
  AgentThinkingMode,
  FundAgentReportSection,
  FundAgentStage,
  FundAgentTask,
  FundAnalysisResult,
  FundCompareResult,
  FundDetail,
  FundNavPoint,
  FundSearchItem,
} from './types/fund'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
type WorkspaceModule = 'overview' | 'compare' | 'agent'

const DEFAULT_FUND_CODE = '000001'
const DEFAULT_QUESTION = '分析一下这只基金的历史表现和风险点'

const activeModule = ref<WorkspaceModule>('overview')
const searchKeyword = ref(DEFAULT_FUND_CODE)
const selectedFundCode = ref(DEFAULT_FUND_CODE)
const fundOptions = ref<FundSearchItem[]>([])
const alipayFundPool = ref<FundSearchItem[]>([])
const detail = ref<FundDetail | null>(null)
const navPoints = ref<FundNavPoint[]>([])
const analysis = ref<FundAnalysisResult | null>(null)
const comparison = ref<FundCompareResult | null>(null)
const compareInput = ref('000001, 110022, 161725')
const agentQuestion = ref(DEFAULT_QUESTION)
const agentThinkingMode = ref<AgentThinkingMode>('BALANCED')
const agentEvents = ref<AgentStreamEvent[]>([])
const agentAnswer = ref('')
const agentGeneratedAt = ref('')
const agentTask = ref<FundAgentTask | null>(null)
const taskHistory = ref<FundAgentTask[]>([])
const liveStages = ref<FundAgentStage[]>([])
const liveSections = ref<FundAgentReportSection[]>([])
const chartRef = ref<HTMLDivElement | null>(null)

const thinkingModeOptions = [
  { label: '快速思考', value: 'FAST' },
  { label: '适中思考', value: 'BALANCED' },
  { label: '仔细思考', value: 'DEEP' },
] satisfies Array<{ label: string; value: AgentThinkingMode }>

const loading = reactive({
  search: false,
  detail: false,
  sync: false,
  agent: false,
  compare: false,
  exportReport: false,
  taskControl: false,
})

let navChart: echarts.ECharts | null = null

const selectedFundTitle = computed(() => {
  if (detail.value !== null) {
    return `${detail.value.fundName}（${detail.value.fundCode}）`
  }
  return selectedFundCode.value
})

const metricCards = computed(() => {
  const metrics = analysis.value?.metrics
  return [
    { label: '近1月', value: formatPercent(metrics?.oneMonthReturn), tone: 'neutral' },
    { label: '近3月', value: formatPercent(metrics?.threeMonthReturn), tone: 'neutral' },
    { label: '近1年', value: formatPercent(metrics?.oneYearReturn), tone: 'accent' },
    { label: '最大回撤', value: formatPercent(metrics?.maxDrawdown), tone: 'danger' },
    { label: '年化波动', value: formatPercent(metrics?.volatility), tone: 'warning' },
  ]
})

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

const progressMessages = computed<string[]>(() => {
  return agentEvents.value
    .filter((event) => event.type === 'PROGRESS')
    .map((event) => String(event.payload))
})

const reportSections = computed<FundAgentReportSection[]>(() => {
  if (liveSections.value.length > 0) {
    return liveSections.value
  }
  return agentTask.value?.sections ?? []
})

const currentTaskLabel = computed(() => {
  if (agentTask.value === null) {
    return '等待任务'
  }
  const nextStage = agentTask.value.nextStageCode == null
    ? ''
    : ` · 待执行 ${agentTask.value.nextStageCode}`
  return `${agentTask.value.taskNo} · ${agentTask.value.status}${nextStage}`
})

const canResumeTask = computed(() => {
  return agentTask.value !== null
    && ['FAILED', 'CANCELLED', 'TIMEOUT'].includes(agentTask.value.status)
})

const canCancelTask = computed(() => {
  return agentTask.value !== null
    && ['PENDING', 'RUNNING'].includes(agentTask.value.status)
})

const canSyncSearchKeyword = computed(() => {
  return fundOptions.value.length === 0 && isSixDigitFundCode(searchKeyword.value.trim())
})

const moduleTitle = computed(() => {
  if (activeModule.value === 'compare') {
    return '多基金对比'
  }
  if (activeModule.value === 'agent') {
    return 'Agent 分析'
  }
  return '基金概览'
})

const moduleSubtitle = computed(() => {
  if (activeModule.value === 'compare') {
    return '按收益、回撤、波动、基金经理和交易状态横向比较'
  }
  if (activeModule.value === 'agent') {
    return '查看工作流阶段、结构化报告和历史任务'
  }
  return '查看基金详情、净值走势、核心指标和风险提示'
})

onMounted(async () => {
  syncModuleFromHash()
  window.addEventListener('resize', resizeChart)
  window.addEventListener('hashchange', syncModuleFromHash)
  window.addEventListener('popstate', syncModuleFromHash)
  await initializeWorkbench()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  window.removeEventListener('hashchange', syncModuleFromHash)
  window.removeEventListener('popstate', syncModuleFromHash)
  navChart?.dispose()
  navChart = null
})

watch(navPoints, () => {
  renderNavChart()
})

watch(activeModule, (moduleName) => {
  if (moduleName !== 'overview') {
    navChart?.dispose()
    navChart = null
    return
  }
  renderNavChart()
})

async function initializeWorkbench(): Promise<void> {
  await Promise.all([loadAlipayFundPool(), searchFunds(false)])
  const compareCodes = alipayFundPool.value.map((fund) => fund.fundCode).slice(0, 3)
  if (compareCodes.length > 0) {
    compareInput.value = compareCodes.join(', ')
  }
  const firstCode = alipayFundPool.value[0]?.fundCode ?? DEFAULT_FUND_CODE
  await selectFund(firstCode)
  await runFundComparison(false)
}

function setActiveModule(moduleName: WorkspaceModule): void {
  activeModule.value = moduleName
  const targetHash = `#${moduleName}`
  if (window.location.hash !== targetHash) {
    window.history.pushState(null, '', targetHash)
  }
}

function syncModuleFromHash(): void {
  const moduleName = window.location.hash.replace(/^#\/?/, '')
  if (isWorkspaceModule(moduleName)) {
    activeModule.value = moduleName
  }
}

async function loadAlipayFundPool(): Promise<void> {
  try {
    alipayFundPool.value = await fundApi.alipayFundPool()
  } catch (error) {
    ElMessage.warning(errorMessage(error, '支付宝基金池加载失败'))
  }
}

async function searchFunds(autoSelectFirst = true): Promise<void> {
  loading.search = true
  try {
    const results = await fundApi.search(searchKeyword.value.trim())
    fundOptions.value = results
    if (autoSelectFirst && results.length > 0) {
      await selectFund(results[0].fundCode)
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '基金搜索失败'))
  } finally {
    loading.search = false
  }
}

async function selectFund(fundCode: string): Promise<void> {
  selectedFundCode.value = fundCode
  await loadFundData(fundCode)
}

async function loadFundData(fundCode: string): Promise<void> {
  loading.detail = true
  try {
    const detailResult = await fundApi.detail(fundCode)
    const [navResult, analysisResult, historyResult] = await Promise.all([
      fundApi.nav(fundCode),
      fundApi.analysis(fundCode),
      fundApi.listAnalysisTasks(fundCode),
    ])

    detail.value = detailResult
    navPoints.value = navResult
    analysis.value = analysisResult
    taskHistory.value = historyResult
    agentAnswer.value = ''
    agentEvents.value = []
    agentGeneratedAt.value = ''
    applyTaskSnapshot(historyResult[0] ?? null)
  } catch (error) {
    ElMessage.error(errorMessage(error, '基金数据加载失败'))
  } finally {
    loading.detail = false
  }
}

async function syncCurrentFund(): Promise<void> {
  loading.sync = true
  try {
    await fundApi.sync(selectedFundCode.value)
    await loadFundData(selectedFundCode.value)
    ElMessage.success('基金数据已同步')
  } catch (error) {
    ElMessage.error(errorMessage(error, '基金同步失败'))
  } finally {
    loading.sync = false
  }
}

async function syncSearchKeywordFund(): Promise<void> {
  const fundCode = searchKeyword.value.trim()
  if (!isSixDigitFundCode(fundCode)) {
    ElMessage.warning('请输入6位基金代码')
    return
  }

  loading.sync = true
  try {
    await fundApi.sync(fundCode)
    await searchFunds(false)
    await selectFund(fundCode)
    ElMessage.success('已按基金代码同步东方财富数据')
  } catch (error) {
    ElMessage.error(errorMessage(error, '基金同步失败'))
  } finally {
    loading.sync = false
  }
}

async function runFundComparison(showMessage = true): Promise<void> {
  const fundCodes = parseCompareCodes(compareInput.value)
  if (fundCodes.length === 0) {
    ElMessage.warning('请输入至少一个基金代码')
    return
  }

  loading.compare = true
  try {
    comparison.value = await fundApi.compare(fundCodes)
    if (showMessage) {
      ElMessage.success('基金对比已更新')
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '基金对比失败'))
  } finally {
    loading.compare = false
  }
}

async function runAgentAnalysis(): Promise<void> {
  if (selectedFundCode.value.trim().length === 0) {
    ElMessage.warning('请先选择基金')
    return
  }

  loading.agent = true
  agentEvents.value = []
  agentAnswer.value = ''
  agentGeneratedAt.value = ''
  liveStages.value = []
  liveSections.value = []
  agentTask.value = null

  const requestBody = {
    fundCode: selectedFundCode.value,
    question: agentQuestion.value.trim() || DEFAULT_QUESTION,
    includeHistory: true,
    includeRiskNotice: true,
    thinkingMode: agentThinkingMode.value,
  }

  try {
    await streamFundAnalysis(requestBody, handleAgentEvent)
    await refreshTaskHistory()
  } catch (error) {
    ElMessage.warning(errorMessage(error, '流式分析失败，已切换为普通分析'))
    try {
      const response = await fundApi.analyzeWithAgent(requestBody)
      applyAgentResponse(response)
      await refreshTaskHistory()
    } catch (fallbackError) {
      ElMessage.error(errorMessage(fallbackError, 'Agent 分析失败'))
    }
  } finally {
    loading.agent = false
  }
}

function handleAgentEvent(event: AgentStreamEvent): void {
  agentEvents.value.push(event)
  if (event.type === 'TASK_RERUN_STARTED') {
    agentAnswer.value = ''
    agentGeneratedAt.value = ''
    liveStages.value = []
    liveSections.value = []
    if (isFundAgentTask(event.payload)) {
      applyTaskSnapshot(event.payload)
    }
    return
  }
  if (event.type === 'TASK_CREATED' && isFundAgentTask(event.payload)) {
    applyTaskSnapshot(event.payload)
    return
  }
  if ((event.type === 'STAGE_STARTED' || event.type === 'STAGE_DONE') && isFundAgentStage(event.payload)) {
    upsertStage(event.payload)
    return
  }
  if (event.type === 'SECTION' && isFundAgentReportSection(event.payload)) {
    upsertSection(event.payload)
    return
  }
  if (event.type === 'CARD') {
    analysis.value = event.payload as FundAnalysisResult
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
  if ((event.type === 'TASK_CANCELLED' || event.type === 'TASK_TIMEOUT') && isFundAgentTask(event.payload)) {
    const previousStatus = agentTask.value?.status
    applyTaskSnapshot(event.payload)
    if (previousStatus !== event.payload.status) {
      const message = event.type === 'TASK_CANCELLED' ? '分析任务已取消' : '分析任务执行超时'
      ElMessage.warning(message)
    }
    return
  }
  if (event.type === 'DONE') {
    if (isFundAgentTask(event.payload)) {
      applyTaskSnapshot(event.payload)
      agentAnswer.value = event.payload.finalAnswer ?? agentAnswer.value
      agentGeneratedAt.value = event.payload.completedAt ?? ''
      if (event.payload.analysis !== null) {
        analysis.value = event.payload.analysis
      }
      return
    }
    agentGeneratedAt.value = String(event.payload ?? '')
  }
}

function applyAgentResponse(response: AgentAnalysisResponse): void {
  agentAnswer.value = response.answer
  agentGeneratedAt.value = response.generatedAt
  if (response.analysis !== null) {
    analysis.value = response.analysis
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
  if (selectedFundCode.value.trim().length === 0) {
    return
  }
  taskHistory.value = await fundApi.listAnalysisTasks(selectedFundCode.value)
}

async function replayTask(taskId: number): Promise<void> {
  loading.agent = true
  agentEvents.value = []
  agentAnswer.value = ''
  agentGeneratedAt.value = ''
  liveStages.value = []
  liveSections.value = []
  try {
    await streamAnalysisTask(taskId, handleAgentEvent)
  } catch (error) {
    ElMessage.error(errorMessage(error, '历史任务回放失败'))
  } finally {
    loading.agent = false
  }
}

async function resumeCurrentTask(): Promise<void> {
  if (agentTask.value === null) {
    ElMessage.warning('请先选择一个历史任务')
    return
  }

  loading.agent = true
  agentEvents.value = []
  agentAnswer.value = ''
  agentGeneratedAt.value = ''
  liveStages.value = []
  liveSections.value = []
  try {
    await streamResumeAnalysisTask(agentTask.value.taskId, handleAgentEvent)
    await refreshTaskHistory()
  } catch (error) {
    ElMessage.warning(errorMessage(error, '流式恢复失败，已切换为普通恢复'))
    try {
      const task = await fundApi.resumeAnalysisTask(agentTask.value.taskId)
      applyTaskSnapshot(task)
      agentAnswer.value = task.finalAnswer ?? ''
      agentGeneratedAt.value = task.completedAt ?? ''
      await refreshTaskHistory()
    } catch (fallbackError) {
      ElMessage.error(errorMessage(fallbackError, '任务恢复失败'))
    }
  } finally {
    loading.agent = false
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
    ElMessage.error(errorMessage(error, '取消任务失败'))
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

  loading.taskControl = true
  loading.agent = true
  agentEvents.value = []
  try {
    const task = await fundApi.rerunAnalysisStage(agentTask.value.taskId, stageCode)
    applyTaskSnapshot(task)
    agentAnswer.value = ''
    agentGeneratedAt.value = ''
    await streamAnalysisTask(task.taskId, handleAgentEvent)
    await refreshTaskHistory()
  } catch (error) {
    ElMessage.error(errorMessage(error, '阶段重跑失败'))
  } finally {
    loading.taskControl = false
    loading.agent = false
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
    const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${agentTask.value.taskNo}.md`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('分析报告已导出')
  } catch (error) {
    ElMessage.error(errorMessage(error, '报告导出失败'))
  } finally {
    loading.exportReport = false
  }
}

function applyTaskSnapshot(task: FundAgentTask | null): void {
  agentTask.value = task
  if (task !== null) {
    agentThinkingMode.value = task.thinkingMode
  }
  liveStages.value = task?.stages ?? []
  liveSections.value = task?.sections ?? []
}

function upsertStage(stage: FundAgentStage): void {
  const existsIndex = liveStages.value.findIndex((item) => item.stageCode === stage.stageCode)
  if (existsIndex >= 0) {
    liveStages.value.splice(existsIndex, 1, stage)
  } else {
    liveStages.value.push(stage)
  }
  liveStages.value.sort((left, right) => left.sortOrder - right.sortOrder)
}

function upsertSection(section: FundAgentReportSection): void {
  const existsIndex = liveSections.value.findIndex((item) => item.id === section.id)
  if (existsIndex >= 0) {
    liveSections.value.splice(existsIndex, 1, section)
  } else {
    liveSections.value.push(section)
  }
  liveSections.value.sort((left, right) => left.sortOrder - right.sortOrder)
}

function renderNavChart(): void {
  void nextTick(() => {
    if (chartRef.value === null) {
      return
    }

    if (navChart === null) {
      navChart = echarts.init(chartRef.value)
    }

    navChart.setOption({
      color: ['#0f766e', '#d1495b'],
      tooltip: {
        trigger: 'axis',
        valueFormatter: (value: unknown) => String(value),
      },
      grid: {
        top: 28,
        right: 18,
        bottom: 34,
        left: 42,
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: navPoints.value.map((point) => point.navDate),
      },
      yAxis: {
        type: 'value',
        scale: true,
      },
      series: [
        {
          name: '单位净值',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: navPoints.value.map((point) => point.unitNav),
          areaStyle: {
            color: 'rgba(15, 118, 110, 0.12)',
          },
        },
        {
          name: '日增长率',
          type: 'line',
          yAxisIndex: 0,
          showSymbol: false,
          data: navPoints.value.map((point) => point.dailyGrowthRate),
        },
      ],
    })
  })
}

function resizeChart(): void {
  navChart?.resize()
}

function riskTagType(riskLevel: string | null | undefined): TagType {
  if (riskLevel?.includes('高')) {
    return 'danger'
  }
  if (riskLevel?.includes('中')) {
    return 'warning'
  }
  return 'info'
}

function statusTagType(status: string): TagType {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED' || status === 'TIMEOUT') {
    return 'danger'
  }
  if (status === 'CANCELLED') {
    return 'warning'
  }
  if (status === 'RUNNING') {
    return 'primary'
  }
  return 'info'
}

function formatElapsed(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '--'
  }
  return `${value} ms`
}

function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '--'
  }
  return `${Number(value).toFixed(2)}%`
}

function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '--'
  }
  return Number(value).toFixed(4)
}

function parseCompareCodes(value: string): string[] {
  const fundCodeSet = new Set<string>()
  value
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
    .forEach((item) => {
      if (isSixDigitFundCode(item)) {
        fundCodeSet.add(item)
      }
    })
  return Array.from(fundCodeSet).slice(0, 6)
}

function isSixDigitFundCode(value: string): boolean {
  return /^\d{6}$/.test(value)
}

function isWorkspaceModule(value: string): value is WorkspaceModule {
  return value === 'overview' || value === 'compare' || value === 'agent'
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.length > 0) {
    return error.message
  }
  return fallback
}

function isRecord(payload: unknown): payload is Record<string, unknown> {
  return typeof payload === 'object' && payload !== null
}

function isFundAgentTask(payload: unknown): payload is FundAgentTask {
  return isRecord(payload) && typeof payload.taskId === 'number' && typeof payload.taskNo === 'string'
}

function isFundAgentStage(payload: unknown): payload is FundAgentStage {
  return isRecord(payload) && typeof payload.stageCode === 'string' && typeof payload.stageName === 'string'
}

function isFundAgentReportSection(payload: unknown): payload is FundAgentReportSection {
  return isRecord(payload) && typeof payload.title === 'string' && typeof payload.content === 'string'
}
</script>

<template>
  <el-config-provider>
    <main class="fund-workbench" v-loading="loading.detail">
      <aside class="navigation-panel" aria-label="基金导航">
        <header class="brand-block">
          <div class="brand-mark">FC</div>
          <div>
            <p class="eyebrow">Fund Copilot</p>
            <h1>基金智能客服</h1>
          </div>
        </header>

        <nav class="module-nav" aria-label="工作台模块">
          <button
            class="module-button"
            :class="{ active: activeModule === 'overview' }"
            type="button"
            @click="setActiveModule('overview')"
          >
            <TrendCharts class="module-icon" />
            <span>基金概览</span>
          </button>
          <button
            class="module-button"
            :class="{ active: activeModule === 'compare' }"
            type="button"
            @click="setActiveModule('compare')"
          >
            <DataLine class="module-icon" />
            <span>多基金对比</span>
          </button>
          <button
            class="module-button"
            :class="{ active: activeModule === 'agent' }"
            type="button"
            @click="setActiveModule('agent')"
          >
            <Cpu class="module-icon" />
            <span>Agent 分析</span>
          </button>
        </nav>

        <section class="search-block" aria-label="基金搜索">
          <div class="section-title">
            <Search class="title-icon" />
            <span>基金搜索</span>
          </div>
          <el-input
            v-model="searchKeyword"
            clearable
            placeholder="代码、名称或简拼"
            @keyup.enter="searchFunds"
          >
            <template #append>
              <el-button :icon="Search" :loading="loading.search" @click="searchFunds" />
            </template>
          </el-input>
          <el-scrollbar class="result-list">
            <button
              v-for="fund in fundOptions"
              :key="fund.fundCode"
              class="fund-row"
              :class="{ active: fund.fundCode === selectedFundCode }"
              type="button"
              @click="selectFund(fund.fundCode)"
            >
              <strong>{{ fund.fundCode }}</strong>
              <span>{{ fund.fundName }}</span>
              <small>{{ fund.fundType || '未分类' }}</small>
            </button>
            <div v-if="canSyncSearchKeyword" class="empty-sync-action">
              <el-empty description="本地暂无结果" :image-size="72" />
              <el-button
                type="primary"
                :icon="Refresh"
                :loading="loading.sync"
                @click="syncSearchKeywordFund"
              >
                按代码同步
              </el-button>
            </div>
            <el-empty
              v-else-if="fundOptions.length === 0"
              description="暂无搜索结果"
              :image-size="72"
            />
          </el-scrollbar>
        </section>

        <section class="pool-block" aria-label="支付宝基金池">
          <div class="section-title">
            <DataLine class="title-icon" />
            <span>支付宝基金池</span>
          </div>
          <button
            v-for="fund in alipayFundPool"
            :key="fund.fundCode"
            class="pool-row"
            :class="{ active: fund.fundCode === selectedFundCode }"
            type="button"
            @click="selectFund(fund.fundCode)"
          >
            <span>{{ fund.displayTag || fund.fundName }}</span>
            <small>{{ fund.fundCode }}</small>
          </button>
        </section>
      </aside>

      <section class="content-panel" :aria-label="moduleTitle">
        <header class="workspace-header">
          <div>
            <p class="eyebrow">Workspace</p>
            <h2>{{ moduleTitle }}</h2>
            <p>{{ moduleSubtitle }}</p>
          </div>
          <div class="workspace-actions">
            <el-button :icon="Refresh" :loading="loading.sync" @click="syncCurrentFund">
              同步东方财富
            </el-button>
            <el-button type="primary" :icon="Cpu" :loading="loading.agent" @click="setActiveModule('agent')">
              打开 Agent
            </el-button>
          </div>
        </header>

        <section v-if="activeModule === 'overview'" class="module-page overview-page">
          <header class="fund-hero">
            <div class="title-group">
              <p class="eyebrow">公开数据分析</p>
              <h2>{{ selectedFundTitle }}</h2>
              <div class="tag-line">
                <el-tag v-if="detail?.fundType" type="info">{{ detail.fundType }}</el-tag>
                <el-tag v-if="detail?.riskLevel" :type="riskTagType(detail.riskLevel)">
                  {{ detail.riskLevel }}
                </el-tag>
                <el-tag v-if="detail?.stale" type="warning">备用数据</el-tag>
                <el-tag v-else type="success">已缓存</el-tag>
              </div>
            </div>
          </header>

          <section class="snapshot-grid">
            <div class="snapshot-item">
              <span>最新净值</span>
              <strong>{{ formatNumber(detail?.latestNav) }}</strong>
              <small>{{ detail?.latestNavDate || '--' }}</small>
            </div>
            <div class="snapshot-item">
              <span>基金公司</span>
              <strong>{{ detail?.fundCompany || '--' }}</strong>
              <small>{{ detail?.fundManager || '基金经理待同步' }}</small>
            </div>
            <div class="snapshot-item">
              <span>交易状态</span>
              <strong>{{ detail?.purchaseStatus || '--' }}</strong>
              <small>{{ detail?.redeemStatus || '--' }}</small>
            </div>
          </section>

          <section class="metric-grid" aria-label="核心指标">
            <article
              v-for="metric in metricCards"
              :key="metric.label"
              class="metric-card"
              :class="`tone-${metric.tone}`"
            >
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </article>
          </section>

          <section class="chart-panel" aria-label="净值走势">
            <div class="section-heading">
              <div>
                <p class="eyebrow">NAV Trend</p>
                <h3>净值走势</h3>
              </div>
              <el-tag type="info">{{ navPoints.length }} 条</el-tag>
            </div>
            <div ref="chartRef" class="nav-chart" />
          </section>

          <section class="analysis-panel" aria-label="分析结果">
            <div class="analysis-column">
              <div class="section-heading">
                <div>
                  <p class="eyebrow">Highlights</p>
                  <h3>分析要点</h3>
                </div>
                <TrendCharts class="heading-icon" />
              </div>
              <ul class="clean-list">
                <li v-for="item in analysis?.highlights || []" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div class="analysis-column">
              <div class="section-heading">
                <div>
                  <p class="eyebrow">Risk Notice</p>
                  <h3>风险提示</h3>
                </div>
                <WarningFilled class="heading-icon warning" />
              </div>
              <ul class="clean-list risk-list">
                <li v-for="item in analysis?.risks || []" :key="item">{{ item }}</li>
              </ul>
            </div>
          </section>
        </section>

        <section v-else-if="activeModule === 'compare'" class="module-page compare-page">
          <section class="compare-panel" aria-label="多基金对比">
            <div class="section-heading">
              <div>
                <p class="eyebrow">Fund Comparison</p>
                <h3>多基金维度对比</h3>
              </div>
              <el-tag type="info">{{ comparison?.columns.length || 0 }} 只基金</el-tag>
            </div>
            <div class="compare-toolbar">
              <el-input
                v-model="compareInput"
                class="compare-input"
                placeholder="输入多个基金代码，用逗号、空格或换行分隔"
                @keyup.enter="runFundComparison"
              />
              <el-button
                type="primary"
                :icon="TrendCharts"
                :loading="loading.compare"
                @click="runFundComparison"
              >
                更新对比
              </el-button>
            </div>
            <div v-if="comparison !== null" class="compare-table-wrap">
              <table class="compare-table">
                <thead>
                  <tr>
                    <th>对比维度</th>
                    <th v-for="column in comparison.columns" :key="column.fundCode">
                      <strong>{{ column.fundName }}</strong>
                      <small>{{ column.fundCode }} · {{ column.fundType || '未分类' }}</small>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in comparison.rows" :key="row.dimension">
                    <th>{{ row.dimension }}</th>
                    <td v-for="(value, index) in row.values" :key="`${row.dimension}-${index}`">
                      {{ value || '--' }}
                    </td>
                  </tr>
                </tbody>
              </table>
              <p class="compare-summary">{{ comparison.summary }}</p>
            </div>
            <el-empty v-else description="输入基金代码后生成对比表" :image-size="86" />
          </section>
        </section>

        <section v-else class="module-page agent-page">
          <section class="agent-board">
            <div class="agent-main">
              <header class="agent-header">
                <div>
                  <p class="eyebrow">AgentScope</p>
                  <h2>基金分析 Agent</h2>
                </div>
                <div class="agent-header-actions">
                  <el-tag class="task-status-tag" :type="statusTagType(agentTask?.status || 'PENDING')">
                    {{ currentTaskLabel }}
                  </el-tag>
                  <div class="agent-action-buttons">
                    <el-button
                      v-if="canCancelTask"
                      size="small"
                      type="danger"
                      plain
                      :icon="CircleClose"
                      :loading="loading.taskControl"
                      @click="cancelCurrentTask"
                    >
                      取消任务
                    </el-button>
                    <el-button
                      size="small"
                      :icon="Refresh"
                      :disabled="!canResumeTask"
                      :loading="loading.agent || loading.taskControl"
                      @click="resumeCurrentTask"
                    >
                      继续/重试
                    </el-button>
                    <el-button
                      size="small"
                      :icon="Download"
                      :disabled="agentTask === null"
                      :loading="loading.exportReport"
                      @click="downloadCurrentReport"
                    >
                      导出报告
                    </el-button>
                  </div>
                </div>
              </header>

              <section class="question-block">
                <div class="thinking-mode-control">
                  <span>思考强度</span>
                  <el-segmented
                    v-model="agentThinkingMode"
                    :options="thinkingModeOptions"
                    :disabled="loading.agent"
                  />
                </div>
                <el-input
                  v-model="agentQuestion"
                  type="textarea"
                  :autosize="{ minRows: 4, maxRows: 7 }"
                  resize="none"
                  placeholder="输入你的问题，例如：这只基金最近表现怎么样？"
                />
                <el-button
                  class="full-button"
                  type="primary"
                  :icon="ChatLineRound"
                  :loading="loading.agent"
                  @click="runAgentAnalysis"
                >
                  开始分析
                </el-button>
              </section>

              <section class="answer-block">
                <div class="section-title">
                  <ChatLineRound class="title-icon" />
                  <span>回答</span>
                </div>
                <p v-if="agentAnswer" class="answer-text">{{ agentAnswer }}</p>
                <el-empty v-else description="点击分析后展示结果" :image-size="76" />
                <small v-if="agentGeneratedAt">生成时间：{{ agentGeneratedAt }}</small>
              </section>

              <AgentReportSections :sections="reportSections" />
            </div>

            <div class="agent-side">
              <AgentTimeline :steps="agentSteps" :progress-messages="progressMessages" />

              <section class="task-history-block">
                <div class="section-title">
                  <DataLine class="title-icon" />
                  <span>历史任务</span>
                </div>
                <button
                  v-for="task in taskHistory"
                  :key="task.taskId"
                  class="task-row"
                  :class="{ active: task.taskId === agentTask?.taskId }"
                  type="button"
                  @click="replayTask(task.taskId)"
                >
                  <span>{{ task.taskNo }}</span>
                  <small>{{ task.status }} · {{ formatElapsed(task.elapsedMs) }}</small>
                </button>
                <el-empty v-if="taskHistory.length === 0" description="暂无历史任务" :image-size="76" />
              </section>

              <AgentStageAudit
                :stages="liveStages"
                :disabled="loading.agent || loading.taskControl"
                @rerun="rerunStage"
              />
            </div>

          </section>
        </section>
      </section>
    </main>
  </el-config-provider>
</template>
