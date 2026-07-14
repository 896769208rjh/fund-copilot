<script setup lang="ts">
import { computed, type Component } from 'vue'
import { Cpu, DataLine, Refresh, Search, TrendCharts } from '@element-plus/icons-vue'
import type { WorkspaceModule } from '@/constants/workbench'
import type { FundSearchItem } from '@/types'
import { isSixDigitFundCode } from '@/utils/fundFormatters'

interface ModuleNavigationItem {
  module: WorkspaceModule
  label: string
  icon: Component
}

const props = defineProps<{
  activeModule: WorkspaceModule
  selectedFundCode: string
  fundOptions: FundSearchItem[]
  alipayFundPool: FundSearchItem[]
  isSearching: boolean
  isSyncing: boolean
}>()

const emit = defineEmits<{
  navigate: [moduleName: WorkspaceModule]
  search: []
  selectFund: [fundCode: string]
  syncSearchKeyword: []
}>()

const searchKeyword = defineModel<string>('searchKeyword', { required: true })

const moduleItems: readonly ModuleNavigationItem[] = [
  { module: 'overview', label: '基金概览', icon: TrendCharts },
  { module: 'compare', label: '多基金对比', icon: DataLine },
  { module: 'agent', label: 'Agent 分析', icon: Cpu },
]

const canSyncSearchKeyword = computed(
  () => props.fundOptions.length === 0 && isSixDigitFundCode(searchKeyword.value.trim()),
)
</script>

<template>
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
        v-for="item in moduleItems"
        :key="item.module"
        class="module-button"
        :class="{ active: activeModule === item.module }"
        type="button"
        @click="emit('navigate', item.module)"
      >
        <component :is="item.icon" class="module-icon" />
        <span>{{ item.label }}</span>
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
        @keyup.enter="emit('search')"
      >
        <template #append>
          <el-button
            :icon="Search"
            :loading="isSearching"
            aria-label="搜索基金"
            @click="emit('search')"
          />
        </template>
      </el-input>
      <el-scrollbar class="result-list">
        <button
          v-for="fund in fundOptions"
          :key="fund.fundCode"
          class="fund-row"
          :class="{ active: fund.fundCode === selectedFundCode }"
          type="button"
          @click="emit('selectFund', fund.fundCode)"
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
            :loading="isSyncing"
            @click="emit('syncSearchKeyword')"
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
        @click="emit('selectFund', fund.fundCode)"
      >
        <span>{{ fund.displayTag || fund.fundName }}</span>
        <small>{{ fund.fundCode }}</small>
      </button>
    </section>
  </aside>
</template>
