<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { WarningFilled, TrendCharts } from '@element-plus/icons-vue'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { init, use, type EChartsType } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { FundAnalysisResult, FundDetail, FundNavPoint } from '@/types'
import { formatNumber, formatPercent, riskTagType } from '@/utils/fundFormatters'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{
  fundCode: string
  detail: FundDetail | null
  navPoints: FundNavPoint[]
  analysis: FundAnalysisResult | null
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let navChart: EChartsType | null = null

const selectedFundTitle = computed(() => {
  if (props.detail !== null) {
    return `${props.detail.fundName}（${props.detail.fundCode}）`
  }
  return props.fundCode
})

const metricCards = computed(() => {
  const metrics = props.analysis?.metrics
  return [
    { label: '近1月', value: formatPercent(metrics?.oneMonthReturn), tone: 'neutral' },
    { label: '近3月', value: formatPercent(metrics?.threeMonthReturn), tone: 'neutral' },
    { label: '近1年', value: formatPercent(metrics?.oneYearReturn), tone: 'accent' },
    { label: '最大回撤', value: formatPercent(metrics?.maxDrawdown), tone: 'danger' },
    { label: '年化波动', value: formatPercent(metrics?.volatility), tone: 'warning' },
  ] as const
})

function renderNavChart(): void {
  void nextTick(() => {
    if (chartRef.value === null) {
      return
    }

    navChart ??= init(chartRef.value)
    navChart.setOption(
      {
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
          data: props.navPoints.map((point) => point.navDate),
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
            data: props.navPoints.map((point) => point.unitNav),
            areaStyle: {
              color: 'rgba(15, 118, 110, 0.12)',
            },
          },
          {
            name: '日增长率',
            type: 'line',
            showSymbol: false,
            data: props.navPoints.map((point) => point.dailyGrowthRate),
          },
        ],
      },
      true,
    )
  })
}

function resizeChart(): void {
  navChart?.resize()
}

watch(() => props.navPoints, renderNavChart)

onMounted(() => {
  renderNavChart()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  navChart?.dispose()
  navChart = null
})
</script>

<template>
  <section class="module-page overview-page">
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
</template>
