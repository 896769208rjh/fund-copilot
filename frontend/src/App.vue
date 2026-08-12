<script setup lang="ts">
import { onMounted, ref } from 'vue'
import FundNavigation from '@/components/layout/FundNavigation.vue'
import WorkspaceHeader from '@/components/layout/WorkspaceHeader.vue'
import { useFundWorkbench } from '@/composables/useFundWorkbench'
import { useWorkspaceNavigation } from '@/composables/useWorkspaceNavigation'
import AgentAnalysisView from '@/views/AgentAnalysisView.vue'
import FundCompareView from '@/views/FundCompareView.vue'
import FundOverviewView from '@/views/FundOverviewView.vue'
import FundObservationView from '@/views/FundObservationView.vue'

const isInitialized = ref(false)

const { activeModule, activeModuleMeta, setActiveModule } = useWorkspaceNavigation()
const {
  alipayFundPool,
  analysis,
  compareInput,
  comparison,
  detail,
  fundOptions,
  loading,
  navPoints,
  searchKeyword,
  selectedFundCode,
  initializeWorkbench,
  runFundComparison,
  searchFunds,
  selectFund,
  syncCurrentFund,
  syncSearchKeywordFund,
  updateAnalysis,
} = useFundWorkbench()

onMounted(async () => {
  try {
    await initializeWorkbench()
  } finally {
    isInitialized.value = true
  }
})

async function openFundFromObservation(fundCode: string): Promise<void> {
  await selectFund(fundCode)
  setActiveModule('overview')
}
</script>

<template>
  <el-config-provider>
    <main v-loading="loading.detail" class="fund-workbench">
      <FundNavigation
        v-model:search-keyword="searchKeyword"
        :active-module="activeModule"
        :selected-fund-code="selectedFundCode"
        :fund-options="fundOptions"
        :alipay-fund-pool="alipayFundPool"
        :is-searching="loading.search"
        :is-syncing="loading.sync"
        @navigate="setActiveModule"
        @search="searchFunds()"
        @select-fund="selectFund"
        @sync-search-keyword="syncSearchKeywordFund"
      />

      <section class="content-panel" :aria-label="activeModuleMeta.title">
        <WorkspaceHeader
          :title="activeModuleMeta.title"
          :subtitle="activeModuleMeta.subtitle"
          :is-syncing="loading.sync"
          :show-actions="activeModule !== 'home'"
          @open-agent="setActiveModule('agent')"
          @sync-fund="syncCurrentFund"
        />

        <FundObservationView v-if="activeModule === 'home'" @open-fund="openFundFromObservation" />

        <FundOverviewView
          v-else-if="activeModule === 'overview'"
          :fund-code="selectedFundCode"
          :detail="detail"
          :nav-points="navPoints"
          :analysis="analysis"
        />

        <FundCompareView
          v-else-if="activeModule === 'compare'"
          v-model:compare-input="compareInput"
          :comparison="comparison"
          :is-loading="loading.compare"
          @compare="runFundComparison()"
        />

        <AgentAnalysisView
          v-if="isInitialized"
          v-show="activeModule === 'agent'"
          :fund-code="selectedFundCode"
          @analysis-updated="updateAnalysis"
        />
      </section>
    </main>
  </el-config-provider>
</template>
