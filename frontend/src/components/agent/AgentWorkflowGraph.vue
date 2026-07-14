<script setup lang="ts">
import { Connection } from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { AgentStageStatus, FundAgentStage } from '@/types'

const props = defineProps<{
  stages: FundAgentStage[]
}>()

const stageStatusMap = computed(
  () => new Map(props.stages.map((stage) => [stage.stageCode, stage.status])),
)

const graphRows = [
  [{ code: 'DATA_COLLECTION', name: '数据采集' }],
  [
    { code: 'PERFORMANCE_ANALYSIS', name: '业绩分析' },
    { code: 'RISK_ANALYSIS', name: '风险分析' },
    { code: 'PEER_COMPARISON', name: '同类比较' },
  ],
  [{ code: 'FACTOR_DEBATE', name: '因素汇聚' }],
  [{ code: 'COMPLIANCE_REVIEW', name: '合规审核' }],
  [{ code: 'ANSWER_COMPOSER', name: '回答生成' }],
]

function nodeStatus(stageCode: string): AgentStageStatus {
  return stageStatusMap.value.get(stageCode) ?? 'PENDING'
}
</script>

<template>
  <section class="workflow-graph-block">
    <div class="section-title">
      <Connection class="title-icon" />
      <span>状态图</span>
    </div>
    <div class="workflow-graph" aria-label="基金分析状态图">
      <template v-for="(row, rowIndex) in graphRows" :key="rowIndex">
        <div class="workflow-row" :class="{ branches: row.length > 1 }">
          <div
            v-for="node in row"
            :key="node.code"
            class="workflow-node"
            :class="`status-${nodeStatus(node.code).toLowerCase()}`"
          >
            <strong>{{ node.name }}</strong>
            <small>{{ nodeStatus(node.code) }}</small>
          </div>
        </div>
        <div v-if="rowIndex < graphRows.length - 1" class="workflow-connector" aria-hidden="true">
          ↓
        </div>
      </template>
    </div>
  </section>
</template>
