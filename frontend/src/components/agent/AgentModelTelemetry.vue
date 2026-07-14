<script setup lang="ts">
import { Cpu } from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { AgentModelCall } from '../../types/fund'

const props = defineProps<{
  calls: AgentModelCall[]
}>()

const totalTokens = computed(() => props.calls.reduce(
  (total, call) => total + (call.inputTokens ?? 0) + (call.outputTokens ?? 0),
  0,
))

function callTone(status: string): string {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FALLBACK') {
    return 'fallback'
  }
  return 'failed'
}
</script>

<template>
  <section class="model-telemetry-block">
    <div class="section-title telemetry-title">
      <Cpu class="title-icon" />
      <span>模型调用</span>
      <small v-if="calls.length > 0">{{ calls.length }} 次 · {{ totalTokens }} Token</small>
    </div>
    <div v-if="calls.length > 0" class="telemetry-list">
      <div v-for="call in calls" :key="call.id" class="telemetry-row">
        <div>
          <strong>{{ call.stageCode }}</strong>
          <small>{{ call.modelName }} · {{ call.thinkingMode }} · 第 {{ call.attemptNo }} 次</small>
        </div>
        <div class="telemetry-values">
          <span :class="`tone-${callTone(call.status)}`">{{ call.status }}</span>
          <small>{{ (call.inputTokens ?? 0) + (call.outputTokens ?? 0) }} Token · {{ call.elapsedMs }}ms</small>
        </div>
      </div>
    </div>
    <el-empty v-else description="当前任务使用本地确定性分析" :image-size="64" />
  </section>
</template>
