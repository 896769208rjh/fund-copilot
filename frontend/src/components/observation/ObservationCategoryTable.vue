<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDown, ArrowUp, TrendCharts } from '@element-plus/icons-vue'
import type { ObservationCategory } from '@/types'

const props = defineProps<{
  category: ObservationCategory
}>()

const emit = defineEmits<{
  openFund: [fundCode: string]
}>()

const expanded = ref(false)
const visibleFunds = computed(() =>
  expanded.value ? props.category.funds : props.category.funds.slice(0, 3),
)

function formatPercent(value: number | null): string {
  return value === null ? '--' : `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
}

function formatNumber(value: number | null): string {
  return value === null ? '--' : value.toFixed(2)
}

function formatScale(value: number | null): string {
  return value === null ? '--' : `${value.toFixed(2)} 亿`
}

function returnTone(value: number | null): string {
  if (value === null || value === 0) return 'neutral'
  return value > 0 ? 'positive' : 'negative'
}
</script>

<template>
  <section class="observation-category" :aria-label="`${category.categoryName}观察榜`">
    <header class="observation-category-header">
      <div>
        <h3>{{ category.categoryName }}</h3>
        <p>规模池 {{ category.universeSize }} 只 · 数据日 {{ category.rankDate || '--' }}</p>
      </div>
      <el-tag effect="plain" type="info">Top {{ category.funds.length }}</el-tag>
    </header>

    <div v-if="category.funds.length > 0" class="observation-table-wrap">
      <table class="observation-table">
        <thead>
          <tr>
            <th>排名</th>
            <th>基金</th>
            <th>基金规模</th>
            <th>近 1 月</th>
            <th>近 3 月</th>
            <th>近 6 月</th>
            <th>近 1 年</th>
            <th>最大回撤</th>
            <th>年化波动</th>
            <th>综合分</th>
            <th>状态</th>
            <th aria-label="操作"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="fund in visibleFunds" :key="fund.fundCode">
            <td>
              <strong class="rank-number">{{ fund.rank }}</strong>
            </td>
            <td>
              <button
                class="fund-name-button"
                type="button"
                @click="emit('openFund', fund.fundCode)"
              >
                <strong>{{ fund.fundName }}</strong>
                <small>{{ fund.fundCode }}</small>
              </button>
            </td>
            <td>{{ formatScale(fund.scaleInBillions) }}</td>
            <td :class="returnTone(fund.oneMonthReturn)">
              {{ formatPercent(fund.oneMonthReturn) }}
            </td>
            <td :class="returnTone(fund.threeMonthReturn)">
              {{ formatPercent(fund.threeMonthReturn) }}
            </td>
            <td :class="returnTone(fund.sixMonthReturn)">
              {{ formatPercent(fund.sixMonthReturn) }}
            </td>
            <td :class="returnTone(fund.oneYearReturn)">{{ formatPercent(fund.oneYearReturn) }}</td>
            <td class="negative">{{ formatPercent(fund.maxDrawdown) }}</td>
            <td>{{ formatPercent(fund.volatility) }}</td>
            <td>
              <strong>{{ formatNumber(fund.totalScore) }}</strong>
            </td>
            <td>
              <el-tag :type="fund.membershipStatus === 'NEW' ? 'success' : 'info'" effect="plain">
                {{ fund.membershipStatus === 'NEW' ? '新进榜' : '连续在榜' }}
              </el-tag>
            </td>
            <td>
              <el-button
                circle
                text
                :icon="TrendCharts"
                title="查看基金详情"
                aria-label="查看基金详情"
                @click="emit('openFund', fund.fundCode)"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <el-empty v-else description="尚未生成榜单，请先同步基金池" :image-size="70" />

    <button
      v-if="category.funds.length > 3"
      class="observation-expand-button"
      type="button"
      @click="expanded = !expanded"
    >
      <component :is="expanded ? ArrowUp : ArrowDown" />
      <span>{{ expanded ? '收起' : '查看完整 Top 10' }}</span>
    </button>
  </section>
</template>
