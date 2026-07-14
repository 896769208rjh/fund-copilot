<script setup lang="ts">
import { Cpu } from '@element-plus/icons-vue'
import type { AgentStageStatus, AgentStep } from '@/types'

defineProps<{
  steps: AgentStep[]
  progressMessages: string[]
}>()

function stepType(status: AgentStageStatus): 'success' | 'warning' | 'danger' | 'primary' {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  if (status === 'TIMEOUT') {
    return 'danger'
  }
  if (status === 'CANCELLED') {
    return 'warning'
  }
  if (status === 'RUNNING') {
    return 'primary'
  }
  return 'warning'
}
</script>

<template>
  <section class="agent-progress">
    <div class="section-title">
      <Cpu class="title-icon" />
      <span>执行过程</span>
    </div>
    <div v-if="progressMessages.length > 0" class="progress-messages">
      <span v-for="(message, index) in progressMessages" :key="`${index}-${message}`">{{
        message
      }}</span>
    </div>
    <el-timeline v-if="steps.length > 0">
      <el-timeline-item
        v-for="step in steps"
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
</template>
