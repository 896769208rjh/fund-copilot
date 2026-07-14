<script setup lang="ts">
import { Cpu, Refresh } from '@element-plus/icons-vue'

defineProps<{
  title: string
  subtitle: string
  isSyncing: boolean
}>()

const emit = defineEmits<{
  openAgent: []
  syncFund: []
}>()
</script>

<template>
  <header class="workspace-header">
    <div>
      <p class="eyebrow">Workspace</p>
      <h2>{{ title }}</h2>
      <p>{{ subtitle }}</p>
    </div>
    <div class="workspace-actions">
      <el-button :icon="Refresh" :loading="isSyncing" @click="emit('syncFund')">
        同步东方财富
      </el-button>
      <el-button type="primary" :icon="Cpu" @click="emit('openAgent')"> 打开 Agent </el-button>
    </div>
  </header>
</template>
