<script setup lang="ts">
import { TrendCharts } from '@element-plus/icons-vue'
import type { FundCompareResult } from '@/types'

defineProps<{
  comparison: FundCompareResult | null
  isLoading: boolean
}>()

const emit = defineEmits<{
  compare: []
}>()

const compareInput = defineModel<string>('compareInput', { required: true })
</script>

<template>
  <section class="module-page compare-page">
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
          @keyup.enter="emit('compare')"
        />
        <el-button type="primary" :icon="TrendCharts" :loading="isLoading" @click="emit('compare')">
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
</template>
