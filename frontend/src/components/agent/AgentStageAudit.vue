<script setup lang="ts">
import { DataLine, RefreshRight } from '@element-plus/icons-vue'
import type { FundAgentStage } from '@/types'

defineProps<{
  stages: FundAgentStage[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  rerun: [stageCode: string]
}>()
</script>

<template>
  <section class="stage-audit-block">
    <div class="section-title">
      <DataLine class="title-icon" />
      <span>阶段审计</span>
    </div>
    <div v-if="stages.length > 0" class="stage-audit-list">
      <article v-for="stage in stages" :key="`${stage.stageCode}-audit`" class="stage-audit-item">
        <header>
          <strong>{{ stage.stageName }}</strong>
          <el-tooltip content="从此阶段重新执行" placement="top">
            <el-button
              text
              :icon="RefreshRight"
              :disabled="disabled"
              aria-label="从此阶段重新执行"
              @click="emit('rerun', stage.stageCode)"
            />
          </el-tooltip>
        </header>
        <small>{{ stage.stageInput || '暂无输入快照' }}</small>
        <small>{{ stage.stageOutput || '暂无输出快照' }}</small>
      </article>
    </div>
    <el-empty v-else description="暂无阶段审计" :image-size="72" />
  </section>
</template>
