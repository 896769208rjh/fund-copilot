<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatLineRound,
  Cpu,
  DataLine,
  Refresh,
  Search,
  TrendCharts,
  WarningFilled,
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { fundApi } from './api/fundApi'
import { streamFundAnalysis } from './composables/useAgentStream'
import type {
  AgentAnalysisResponse,
  AgentStep,
  AgentStreamEvent,
  FundAnalysisResult,
  FundDetail,
  FundNavPoint,
  FundSearchItem,
} from './types/fund'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const DEFAULT_FUND_CODE = '000001'
const DEFAULT_QUESTION = '分析一下这只基金的历史表现和风险点'

const searchKeyword = ref(DEFAULT_FUND_CODE)
const selectedFundCode = ref(DEFAULT_FUND_CODE)
const fundOptions = ref<FundSearchItem[]>([])
const alipayFundPool = ref<FundSearchItem[]>([])
const detail = ref<FundDetail | null>(null)
const navPoints = ref<FundNavPoint[]>([])
const analysis = ref<FundAnalysisResult | null>(null)
const agentQuestion = ref(DEFAULT_QUESTION)
const agentEvents = ref<AgentStreamEvent[]>([])
const agentAnswer = ref('')
const agentGeneratedAt = ref('')
const chartRef = ref<HTMLDivElement | null>(null)

const loading = reactive({
  search: false,
  detail: false,
  sync: false,
  agent: false,
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
  return agentEvents.value
    .filter((event) => event.type === 'AGENT_STEP')
    .map((event) => event.payload as AgentStep)
})

const progressMessages = computed<string[]>(() => {
  return agentEvents.value
    .filter((event) => event.type === 'PROGRESS')
    .map((event) => String(event.payload))
})

onMounted(async () => {
  window.addEventListener('resize', resizeChart)
  await initializeWorkbench()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  navChart?.dispose()
  navChart = null
})

watch(navPoints, () => {
  renderNavChart()
})

async function initializeWorkbench(): Promise<void> {
  await Promise.all([loadAlipayFundPool(), searchFunds(false)])
  const firstCode = alipayFundPool.value[0]?.fundCode ?? DEFAULT_FUND_CODE
  await selectFund(firstCode)
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
    const [detailResult, navResult, analysisResult] = await Promise.all([
      fundApi.detail(fundCode),
      fundApi.nav(fundCode),
      fundApi.analysis(fundCode),
    ])

    detail.value = detailResult
    navPoints.value = navResult
    analysis.value = analysisResult
    agentAnswer.value = ''
    agentEvents.value = []
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

async function runAgentAnalysis(): Promise<void> {
  if (selectedFundCode.value.trim().length === 0) {
    ElMessage.warning('请先选择基金')
    return
  }

  loading.agent = true
  agentEvents.value = []
  agentAnswer.value = ''
  agentGeneratedAt.value = ''

  const requestBody = {
    fundCode: selectedFundCode.value,
    question: agentQuestion.value.trim() || DEFAULT_QUESTION,
    includeHistory: true,
    includeRiskNotice: true,
  }

  try {
    await streamFundAnalysis(requestBody, handleAgentEvent)
  } catch (error) {
    ElMessage.warning(errorMessage(error, '流式分析失败，已切换为普通分析'))
    try {
      const response = await fundApi.analyzeWithAgent(requestBody)
      applyAgentResponse(response)
    } catch (fallbackError) {
      ElMessage.error(errorMessage(fallbackError, 'Agent 分析失败'))
    }
  } finally {
    loading.agent = false
  }
}

function handleAgentEvent(event: AgentStreamEvent): void {
  agentEvents.value.push(event)
  if (event.type === 'CARD') {
    analysis.value = event.payload as FundAnalysisResult
    return
  }
  if (event.type === 'TOKEN') {
    agentAnswer.value = String(event.payload ?? '')
    return
  }
  if (event.type === 'DONE') {
    agentGeneratedAt.value = String(event.payload ?? '')
  }
}

function applyAgentResponse(response: AgentAnalysisResponse): void {
  agentAnswer.value = response.answer
  agentGeneratedAt.value = response.generatedAt
  analysis.value = response.analysis
  agentEvents.value = [
    { type: 'PROGRESS', payload: '普通分析接口已返回' },
    ...response.steps.map((step) => ({ type: 'AGENT_STEP' as const, payload: step })),
    { type: 'CARD', payload: response.analysis },
    { type: 'TOKEN', payload: response.answer },
    { type: 'DONE', payload: response.generatedAt },
  ]
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
      color: ['#0f8b8d', '#d95f59'],
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
            color: 'rgba(15, 139, 141, 0.12)',
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

function stepType(status: string): 'success' | 'warning' | 'danger' | 'primary' {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'SKIPPED') {
    return 'warning'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'primary'
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

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.length > 0) {
    return error.message
  }
  return fallback
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

        <section class="search-block" aria-label="基金搜索">
          <div class="section-title">
            <Search class="title-icon" />
            <span>基金搜索</span>
          </div>
          <el-input
            v-model="searchKeyword"
            clearable
            placeholder="代码或名称"
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
            <el-empty v-if="fundOptions.length === 0" description="暂无搜索结果" :image-size="72" />
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

      <section class="content-panel" aria-label="基金详情">
        <header class="content-header">
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
          <div class="header-actions">
            <el-button :icon="Refresh" :loading="loading.sync" @click="syncCurrentFund">
              同步东方财富
            </el-button>
            <el-button type="primary" :icon="Cpu" :loading="loading.agent" @click="runAgentAnalysis">
              Agent 分析
            </el-button>
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

      <aside class="agent-panel" aria-label="Agent 分析">
        <header class="agent-header">
          <p class="eyebrow">AgentScope</p>
          <h2>基金分析 Agent</h2>
        </header>

        <section class="question-block">
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

        <section class="agent-progress">
          <div class="section-title">
            <Cpu class="title-icon" />
            <span>执行过程</span>
          </div>
          <div v-if="progressMessages.length > 0" class="progress-messages">
            <span v-for="message in progressMessages" :key="message">{{ message }}</span>
          </div>
          <el-timeline v-if="agentSteps.length > 0">
            <el-timeline-item
              v-for="step in agentSteps"
              :key="`${step.name}-${step.status}`"
              :type="stepType(step.status)"
              :timestamp="step.status"
            >
              <strong>{{ step.name }}</strong>
              <p>{{ step.detail }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="等待 Agent 执行" :image-size="76" />
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
      </aside>
    </main>
  </el-config-provider>
</template>
